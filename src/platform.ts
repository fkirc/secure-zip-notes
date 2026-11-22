/** Browser integration: file open/save, settings, environment detection. */
import type { Bytes } from "./bytes.ts";

/** "pwa" when served over https (or localhost) with service workers, "file" otherwise. */
export function detectMode(): "file" | "pwa" {
  if (location.protocol === "file:") return "file";
  return "serviceWorker" in navigator && window.isSecureContext ? "pwa" : "file";
}

export async function readFile(file: File): Promise<Bytes> {
  return new Uint8Array(await file.arrayBuffer());
}

/** Triggers a download of the bytes. Works in every browser, including on file://. */
export function download(bytes: Bytes, fileName: string): void {
  const url = URL.createObjectURL(new Blob([bytes], { type: "application/zip" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  a.rel = "noopener";
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Minimal typing for the File System Access API (Chromium only). */
export interface WritableFileHandle {
  name: string;
  createWritable(): Promise<{ write(data: Blob): Promise<void>; close(): Promise<void> }>;
}

interface SaveFilePickerWindow {
  showSaveFilePicker?: (options: {
    suggestedName: string;
    types: Array<{ description: string; accept: Record<string, string[]> }>;
  }) => Promise<WritableFileHandle>;
}

export function canSaveInPlace(): boolean {
  return typeof (window as unknown as SaveFilePickerWindow).showSaveFilePicker === "function";
}

/** Asks the user where to save (Chromium). Returns null if the user cancelled. */
export async function pickSaveFile(suggestedName: string): Promise<WritableFileHandle | null> {
  const picker = (window as unknown as SaveFilePickerWindow).showSaveFilePicker;
  if (!picker) return null;
  try {
    return await picker({
      suggestedName,
      types: [{ description: "Encrypted Zip", accept: { "application/zip": [".zip", ".aeszip"] } }],
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") return null;
    throw e;
  }
}

export async function writeToHandle(handle: WritableFileHandle, bytes: Bytes): Promise<void> {
  const w = await handle.createWritable();
  await w.write(new Blob([bytes], { type: "application/zip" }));
  await w.close();
}

/** Shares the vault via the OS share sheet where supported (mobile). Returns false if unavailable. */
export async function shareFile(bytes: Bytes, fileName: string): Promise<boolean> {
  const file = new File([bytes], fileName, { type: "application/zip" });
  if (typeof navigator.canShare !== "function" || !navigator.canShare({ files: [file] })) return false;
  try {
    await navigator.share({ files: [file] });
    return true;
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") return true;
    return false;
  }
}

const SETTINGS_KEY = "secure-zip-notes.settings";
export const DEFAULT_IDLE_MINUTES = 60;
export const MIN_IDLE_MINUTES = 1;
export const MAX_IDLE_MINUTES = 1440;

export interface Settings {
  idleMinutes: number;
}

export function clampIdleMinutes(n: number): number {
  if (!Number.isFinite(n)) return DEFAULT_IDLE_MINUTES;
  return Math.min(MAX_IDLE_MINUTES, Math.max(MIN_IDLE_MINUTES, Math.round(n)));
}

/** Settings are not secret. Stored best effort; defaults apply if storage is unavailable. */
export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<Settings>;
      return { idleMinutes: clampIdleMinutes(Number(parsed.idleMinutes)) };
    }
  } catch {
    // storage unavailable (e.g. some browsers on file://)
  }
  return { idleMinutes: DEFAULT_IDLE_MINUTES };
}

export function saveSettings(s: Settings): void {
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(s));
  } catch {
    // ignore
  }
}
