import { test as base, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";

export const ROOT = fileURLToPath(new URL("../../", import.meta.url));
export const VERSION = (JSON.parse(readFileSync(ROOT + "package.json", "utf8")) as { version: string }).version;
export const APP_FILE = ROOT + "dist/pwa/index.html";
export const APP_URL = pathToFileURL(APP_FILE).href;
export const PWA_URL = `http://localhost:4173/secure-zip-notes/v${VERSION}/`;
export const FIXTURES = ROOT + "tests/fixtures/";
export const TESTPASSWORD = "testpassword";
export const SECRET_NOTE = "My secret note";

/**
 * Every test records all requests the page makes. In the zip variant the only allowed
 * request is the initial file:// load of index.html (plus blob: URLs for downloads).
 */
export const test = base.extend<{ requests: string[]; allowedOrigins: string[] }>({
  allowedOrigins: [[], { option: true }],
  requests: [
    async ({ page, allowedOrigins }, use) => {
      // A request counts if it reached the network: it got a response, or it failed for a
      // reason other than the CSP. Requests the CSP refused never leave the browser
      // (Chromium reports them as failed with "csp"; WebKit reports no outcome at all).
      const urls: string[] = [];
      page.on("response", (r) => urls.push(r.url()));
      page.on("requestfailed", (r) => {
        if (!/^csp$|BLOCKED_BY_CSP|Content Security Policy/i.test(r.failure()?.errorText ?? "")) urls.push(r.url());
      });
      await use(urls);
      const unexpected = urls.filter(
        (u) => u !== APP_URL && !u.startsWith("blob:") && !u.startsWith("data:") && !allowedOrigins.some((o) => u.startsWith(o)),
      );
      expect(unexpected, "unexpected network requests").toEqual([]);
    },
    { auto: true },
  ],
});
export { expect };

export async function openApp(page: Page): Promise<void> {
  await page.goto(APP_URL);
  await expect(page.getByRole("button", { name: "Create new vault" })).toBeVisible();
}

export async function createVault(page: Page, password = TESTPASSWORD): Promise<void> {
  await page.getByRole("button", { name: "Create new vault" }).click();
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByLabel("Confirm password").fill(password);
  await page.getByRole("button", { name: "Create vault" }).click();
  await expect(page.getByRole("heading", { name: "Notes" })).toBeVisible();
}

export function noteList(page: Page) {
  return page.locator(".notes .note");
}

export async function expectNotes(page: Page, names: string[]): Promise<void> {
  if (names.length === 0) await expect(page.getByText("No notes yet")).toBeVisible();
  else await expect(noteList(page)).toHaveText(names);
}

/** Adds a note via "Add note", types the text and closes the editor with "Done". */
export async function addNote(page: Page, text: string): Promise<void> {
  await page.getByRole("button", { name: "Add note" }).click();
  await expect(page.getByLabel("Note content")).toBeVisible();
  if (text) await page.getByLabel("Note content").fill(text);
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByRole("heading", { name: "Notes" })).toBeVisible();
}

export async function openNote(page: Page, name: string): Promise<void> {
  await noteList(page).getByText(name, { exact: true }).click();
  // Either the editor opens or the note asks for its own password.
  await expect(page.getByLabel("Note content").or(page.getByRole("dialog"))).toBeVisible();
}

export async function expectEditor(page: Page, name: string, content: string): Promise<void> {
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
  await expect(page.getByLabel("Note content")).toHaveValue(content);
}

export async function renameFromList(page: Page, from: string, to: string): Promise<void> {
  await page.getByRole("button", { name: `Rename ${from}`, exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("New name").fill(to);
  await dialog.getByRole("button", { name: "Rename" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

export async function deleteFromList(page: Page, name: string): Promise<void> {
  await page.getByRole("button", { name: `Delete ${name}`, exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Delete" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

export async function expectToast(page: Page, text: string): Promise<void> {
  await expect(page.getByRole("status").filter({ hasText: text })).toBeVisible();
}

export async function openVaultFile(page: Page, path: string, label = "Open vault"): Promise<void> {
  await page.getByLabel(label).setInputFiles(path);
}

export async function unlock(page: Page, password = TESTPASSWORD): Promise<void> {
  await expect(page.getByRole("heading", { name: "Unlock vault" })).toBeVisible();
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Unlock" }).click();
  await expect(page.getByRole("heading", { name: "Notes" })).toBeVisible();
}

/** Creates a vault with notes "Note 1" (SECRET_NOTE) .. "Note n" (text "n"), like v1's preconditions. */
export async function vaultWithNotes(page: Page, n: number): Promise<void> {
  await openApp(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  for (let i = 2; i <= n; i++) await addNote(page, String(i));
}

/** In Chromium, removes the File System Access API so saving falls back to a download. */
export async function disableSavePicker(page: Page): Promise<void> {
  await page.addInitScript(() => {
    delete (window as unknown as { showSaveFilePicker?: unknown }).showSaveFilePicker;
  });
}
