/**
 * Secure Zip Notes v2 application: state, actions and screens.
 *
 * Security model: the vault stays encrypted in memory. Plaintext of a note exists only
 * while it is open in the editor. The password lives in memory until the app is locked
 * (idle timeout or "Lock") or the tab is closed.
 */
import { generatePassword, validateNewPassword } from "./crypto/password.ts";
import {
  canSaveInPlace,
  clampIdleMinutes,
  download,
  loadSettings,
  MAX_IDLE_MINUTES,
  MIN_IDLE_MINUTES,
  pickSaveFile,
  readFile,
  saveSettings,
  shareFile,
  writeToHandle,
  type Settings,
  type WritableFileHandle,
} from "./platform.ts";
import { clearVault, loadVault, requestPersistence, storeVault } from "./pwa/storage.ts";
import { h, mount, type Child } from "./ui/h.ts";
import { ImportError, MSG_EMPTY_EXPORT, Vault } from "./vault/vault.ts";

declare const __APP_VERSION__: string;
export const APP_VERSION: string = typeof __APP_VERSION__ === "string" ? __APP_VERSION__ : "dev";
export const RELEASES_URL = "https://github.com/fkirc/secure-zip-notes/releases";
const DEFAULT_FILE_NAME = "secure-zip-notes.zip";

type Screen = "start" | "setup" | "unlock" | "list" | "edit" | "settings";

type Dialog =
  | { kind: "alert"; title: string; message: string }
  | { kind: "rename"; name: string }
  | { kind: "delete"; name: string }
  | { kind: "entry-password"; name: string; error: string | null }
  | { kind: "confirm"; title: string; message: string; confirmLabel: string; onConfirm: () => Promise<void> | void };

interface Editing {
  name: string;
  original: string;
  content: string;
}

export interface AppOptions {
  root: HTMLElement;
  mode: "file" | "pwa";
  scriptHash: string;
}

export class App {
  private readonly view: HTMLElement;
  private readonly toastEl: HTMLElement;
  private readonly mode: "file" | "pwa";
  private readonly scriptHash: string;
  private settings: Settings = loadSettings();

  private screen: Screen = "start";
  private vault: Vault | null = null;
  private password: string | null = null;
  private readonly entryPasswords = new Map<string, string>();
  private fileName = DEFAULT_FILE_NAME;
  private fileHandle: WritableFileHandle | null = null;
  private dirty = false;
  private changedAt = 0;
  private exportedAt = 0;
  private editing: Editing | null = null;
  private dialog: Dialog | null = null;
  private toastTimer: ReturnType<typeof setTimeout> | null = null;
  private idleTimer: ReturnType<typeof setTimeout> | null = null;
  private formError: string | null = null;
  private busy = false;
  private setupPassword = "";
  private setupConfirm = "";
  private setupVisible = false;

  constructor(opts: AppOptions) {
    this.view = h("div", { class: "view" });
    // The toast lives outside the re-rendered view, so showing or hiding it never
    // re-creates the editor (which would move the caret while typing).
    this.toastEl = h("div", { class: "toast", role: "status", "aria-live": "polite" });
    opts.root.replaceChildren(this.view, this.toastEl);
    this.mode = opts.mode;
    this.scriptHash = opts.scriptHash;
  }

  async start(): Promise<void> {
    for (const type of ["pointerdown", "keydown", "input", "wheel", "touchstart"]) {
      document.addEventListener(type, () => this.onActivity(), { capture: true, passive: true });
    }
    window.addEventListener("beforeunload", (e) => {
      if (this.hasUnsavedWork()) {
        e.preventDefault();
        e.returnValue = "";
      }
    });
    if (this.mode === "pwa") {
      document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "hidden") void this.run(() => this.commitEditor());
      });
      try {
        const stored = await loadVault();
        if (stored) {
          this.vault = Vault.open(stored.bytes);
          this.fileName = stored.fileName;
          this.changedAt = stored.changedAt;
          this.exportedAt = stored.exportedAt;
          this.screen = "unlock";
        }
      } catch (e) {
        this.dialog = { kind: "alert", title: "Could not load the stored vault", message: errorMessage(e) };
      }
    }
    this.render();
  }

  // ---------------------------------------------------------------- state helpers

  private hasUnsavedWork(): boolean {
    const editorChanged = this.editing !== null && this.editing.content !== this.editing.original;
    return editorChanged || (this.mode === "file" && this.dirty);
  }

  private get notExported(): boolean {
    return this.mode === "pwa" && this.vault !== null && this.vault.size > 0 && this.changedAt > this.exportedAt;
  }

  private async run(action: () => Promise<void> | void): Promise<void> {
    if (this.busy) return;
    this.busy = true;
    try {
      await action();
    } catch (e) {
      this.dialog = { kind: "alert", title: "Error", message: errorMessage(e) };
    } finally {
      this.busy = false;
      this.render();
    }
  }

  private toast(message: string): void {
    this.toastEl.textContent = message;
    this.toastEl.classList.add("visible");
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => {
      this.toastEl.textContent = "";
      this.toastEl.classList.remove("visible");
      this.toastTimer = null;
    }, 4000);
  }

  private onActivity(): void {
    if (this.password !== null) this.scheduleIdleLock();
  }

  private scheduleIdleLock(): void {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = setTimeout(() => void this.run(() => this.lock(true)), this.settings.idleMinutes * 60_000);
  }

  private async persist(): Promise<void> {
    if (this.mode !== "pwa" || !this.vault) return;
    if (this.vault.size === 0) {
      await clearVault();
      return;
    }
    await storeVault({
      bytes: this.vault.toZip(),
      fileName: this.fileName,
      changedAt: this.changedAt,
      exportedAt: this.exportedAt,
    });
  }

  private async changed(): Promise<void> {
    this.dirty = true;
    this.changedAt = Date.now();
    await this.persist();
  }

  private requirePassword(): string {
    if (this.password === null) throw new Error("Vault is locked");
    return this.password;
  }

  private requireVault(): Vault {
    if (!this.vault) throw new Error("No vault open");
    return this.vault;
  }

  // ---------------------------------------------------------------- actions

  private async createVault(): Promise<void> {
    const err = validateNewPassword(this.setupPassword, this.setupConfirm);
    if (err) {
      this.formError = err;
      return;
    }
    this.vault = Vault.empty();
    this.password = this.setupPassword;
    this.fileName = DEFAULT_FILE_NAME;
    this.fileHandle = null;
    this.dirty = false;
    this.changedAt = 0;
    this.exportedAt = 0;
    this.resetSetup();
    this.screen = "list";
    if (this.mode === "pwa") await requestPersistence();
    this.scheduleIdleLock();
  }

  private resetSetup(): void {
    this.setupPassword = "";
    this.setupConfirm = "";
    this.setupVisible = false;
    this.formError = null;
  }

  private async openFile(file: File): Promise<void> {
    const bytes = await readFile(file);
    let vault: Vault;
    try {
      vault = Vault.open(bytes);
    } catch (e) {
      if (e instanceof ImportError) {
        this.dialog = { kind: "alert", title: "Cannot open this file", message: e.message };
        return;
      }
      throw e;
    }
    const replace = async () => {
      this.clearSession();
      this.vault = vault;
      this.fileName = file.name || DEFAULT_FILE_NAME;
      this.fileHandle = null;
      this.dirty = false;
      this.changedAt = Date.now();
      this.exportedAt = this.changedAt; // it came from a file, so a copy exists outside the browser
      this.screen = "unlock";
      if (this.mode === "pwa") {
        await requestPersistence();
        await this.persist();
      }
    };
    if (this.vault && (this.dirty || this.notExported)) {
      this.dialog = {
        kind: "confirm",
        title: "Replace the current vault?",
        message: "The current vault has changes that are not saved to a file. They will be lost.",
        confirmLabel: "Replace",
        onConfirm: replace,
      };
      return;
    }
    await replace();
  }

  private async unlock(password: string): Promise<void> {
    const vault = this.requireVault();
    if (password.length === 0 || !(await vault.unlocks(password))) {
      this.formError = "Wrong password";
      return;
    }
    this.password = password;
    this.formError = null;
    this.screen = "list";
    this.scheduleIdleLock();
  }

  private async addNote(): Promise<void> {
    const vault = this.requireVault();
    const name = vault.unusedName();
    await vault.add(name, "", this.requirePassword());
    await this.changed();
    this.editing = { name, original: "", content: "" };
    this.screen = "edit";
  }

  private async openNote(name: string): Promise<void> {
    const vault = this.requireVault();
    const res = await vault.read(name, this.entryPasswords.get(name) ?? this.requirePassword());
    if (res.ok) {
      this.editing = { name, original: res.content, content: res.content };
      this.screen = "edit";
    } else if (res.reason === "wrong-password") {
      this.dialog = { kind: "entry-password", name, error: null };
    } else {
      this.dialog = { kind: "alert", title: "Cannot open note", message: corruptMessage(name) };
    }
  }

  private async submitEntryPassword(name: string, password: string): Promise<void> {
    const res = await this.requireVault().read(name, password);
    if (res.ok) {
      this.entryPasswords.set(name, password);
      this.dialog = null;
      this.editing = { name, original: res.content, content: res.content };
      this.screen = "edit";
    } else if (res.reason === "wrong-password") {
      this.dialog = { kind: "entry-password", name, error: "Wrong password" };
    } else {
      this.dialog = { kind: "alert", title: "Cannot open note", message: corruptMessage(name) };
    }
  }

  /** Encrypts the open note into the vault if it changed. Always uses the master password. */
  private async commitEditor(): Promise<void> {
    const e = this.editing;
    if (!e || e.content === e.original || this.password === null) return;
    const content = e.content;
    await this.requireVault().update(e.name, e.name, content, this.password);
    this.entryPasswords.delete(e.name);
    e.original = content;
    await this.changed();
  }

  private async closeEditor(): Promise<void> {
    await this.commitEditor();
    this.editing = null;
    this.screen = "list";
  }

  private async rename(from: string, to: string): Promise<void> {
    const vault = this.requireVault();
    this.dialog = null;
    if (from === to) return;
    const err = vault.renameError(from, to);
    if (err) {
      this.toast(err);
      return;
    }
    vault.rename(from, to);
    const pw = this.entryPasswords.get(from);
    if (pw !== undefined) {
      this.entryPasswords.delete(from);
      this.entryPasswords.set(to, pw);
    }
    if (this.editing?.name === from) this.editing.name = to;
    await this.changed();
  }

  private async deleteNote(name: string): Promise<void> {
    this.requireVault().delete(name);
    this.entryPasswords.delete(name);
    this.dialog = null;
    if (this.editing?.name === name) {
      this.editing = null;
      this.screen = "list";
    }
    await this.changed();
    this.toast(`Removed ${name}`);
  }

  /** Zip variant: write the vault to a file. */
  private async saveVault(): Promise<void> {
    await this.commitEditor();
    const vault = this.requireVault();
    if (vault.size === 0) {
      this.toast(MSG_EMPTY_EXPORT);
      return;
    }
    const bytes = vault.toZip();
    if (!this.fileHandle && canSaveInPlace()) {
      this.fileHandle = await pickSaveFile(this.fileName);
      if (!this.fileHandle) return; // cancelled
      this.fileName = this.fileHandle.name;
    }
    if (this.fileHandle) await writeToHandle(this.fileHandle, bytes);
    else download(bytes, this.fileName);
    this.dirty = false;
    this.exportedAt = Date.now();
    await this.persist();
    this.toast(`Saved ${this.fileName}`);
  }

  /** PWA: get a copy of the encrypted vault out of browser storage. */
  private async exportVault(share: boolean): Promise<void> {
    await this.commitEditor();
    const vault = this.requireVault();
    if (vault.size === 0) {
      this.toast(MSG_EMPTY_EXPORT);
      return;
    }
    const bytes = vault.toZip();
    if (!(share && (await shareFile(bytes, this.fileName)))) download(bytes, this.fileName);
    this.dirty = false;
    this.exportedAt = Date.now();
    await this.persist();
    this.toast(`Exported ${this.fileName}`);
  }

  private clearSession(): void {
    this.password = null;
    this.entryPasswords.clear();
    this.editing = null;
    this.dialog = null;
    this.formError = null;
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = null;
  }

  private async lock(idle: boolean): Promise<void> {
    if (this.password === null) return;
    await this.commitEditor(); // unsaved edits are encrypted, never discarded
    this.clearSession();
    if (!this.vault || this.vault.size === 0) {
      this.vault = null;
      this.screen = "start";
    } else {
      this.screen = "unlock";
    }
    if (idle) this.toast(`Locked after ${this.settings.idleMinutes} minutes of inactivity`);
  }

  private closeVault(): void {
    const close = async () => {
      this.clearSession();
      this.vault = null;
      this.fileHandle = null;
      this.dirty = false;
      this.screen = "start";
      if (this.mode === "pwa") await clearVault();
    };
    const pwa = this.mode === "pwa";
    this.dialog = {
      kind: "confirm",
      title: pwa ? "Remove the vault from this device?" : "Close the vault?",
      message: pwa
        ? this.notExported
          ? "Warning: the latest changes were never exported. Removing the vault deletes them permanently."
          : "The vault is deleted from this browser's storage. Your exported files are not affected."
        : this.dirty
          ? "Warning: unsaved changes will be lost."
          : "You can open it again from the file.",
      confirmLabel: pwa ? "Remove" : "Close",
      onConfirm: close,
    };
  }

  private saveIdleMinutes(value: string): void {
    this.settings = { idleMinutes: clampIdleMinutes(Number(value)) };
    saveSettings(this.settings);
    if (this.password !== null) this.scheduleIdleLock();
    this.toast(`Auto-lock after ${this.settings.idleMinutes} minutes`);
  }

  // ---------------------------------------------------------------- rendering

  render(): void {
    mount(this.view, this.renderScreen(), this.renderDialog());
  }

  private renderScreen(): Child {
    switch (this.screen) {
      case "start":
        return this.startScreen();
      case "setup":
        return this.setupScreen();
      case "unlock":
        return this.unlockScreen();
      case "list":
        return this.listScreen();
      case "edit":
        return this.editScreen();
      case "settings":
        return this.settingsScreen();
    }
  }

  private header(title: string, ...actions: Child[]): HTMLElement {
    return h("header", { class: "bar" }, h("h1", null, title), h("div", { class: "actions" }, ...actions));
  }

  private button(label: string, onClick: () => Promise<void> | void, extra: Record<string, unknown> = {}): HTMLElement {
    return h("button", { type: "button", ...extra, onclick: () => void this.run(onClick) }, label);
  }

  private openFileInput(label: string): HTMLElement {
    const input = h("input", {
      type: "file",
      class: "visually-hidden",
      accept: ".zip,.aeszip,application/zip",
      "aria-label": label,
      onchange: (e: Event) => {
        const file = (e.target as HTMLInputElement).files?.[0];
        if (file) void this.run(() => this.openFile(file));
      },
    }) as HTMLInputElement;
    return h("label", { class: "button" }, label, input);
  }

  private startScreen(): HTMLElement {
    return h(
      "main",
      { class: "screen center" },
      h("h1", null, "Secure Zip Notes"),
      h("p", { class: "muted" }, "Encrypted notes in a standard AES-256 Zip file. Works offline; nothing leaves this device."),
      h(
        "div",
        { class: "stack" },
        this.button("Create new vault", () => {
          this.resetSetup();
          this.screen = "setup";
        }, { class: "primary" }),
        this.openFileInput("Open vault"),
      ),
      h(
        "p",
        { class: "muted small" },
        this.mode === "pwa"
          ? "The encrypted vault is kept in this browser. Browser storage is not a backup: export your vault regularly."
          : "Your vault is a normal Zip file that 7-Zip and other tools can open with your password. Backups are up to you.",
      ),
      this.versionFooter(),
    );
  }

  private versionFooter(): HTMLElement {
    return h("p", { class: "muted small" }, `Version ${APP_VERSION}`);
  }

  private setupScreen(): HTMLElement {
    const type = this.setupVisible ? "text" : "password";
    return h(
      "main",
      { class: "screen center" },
      h("h1", null, "New master password"),
      h(
        "form",
        {
          class: "stack",
          onsubmit: (e: Event) => {
            e.preventDefault();
            void this.run(() => this.createVault());
          },
        },
        h(
          "label",
          null,
          "Password",
          h("input", {
            type,
            name: "new-password",
            autocomplete: "new-password",
            value: this.setupPassword,
            "data-autofocus": true,
            oninput: (e: Event) => (this.setupPassword = (e.target as HTMLInputElement).value),
          }),
        ),
        h(
          "label",
          null,
          "Confirm password",
          h("input", {
            type,
            name: "confirm-password",
            autocomplete: "new-password",
            value: this.setupConfirm,
            oninput: (e: Event) => (this.setupConfirm = (e.target as HTMLInputElement).value),
          }),
        ),
        this.formError ? h("p", { class: "error", role: "alert" }, this.formError) : null,
        h(
          "div",
          { class: "row" },
          h("button", { type: "submit", class: "primary" }, "Create vault"),
          this.button("Generate random password", () => {
            this.setupPassword = generatePassword();
            this.setupConfirm = this.setupPassword;
            this.setupVisible = true;
            this.formError = null;
          }),
          this.button("Cancel", () => {
            this.resetSetup();
            this.screen = "start";
          }),
        ),
        h(
          "p",
          { class: "muted small" },
          "There is no password recovery. Use a long password and write it down somewhere safe.",
        ),
      ),
    );
  }

  private unlockScreen(): HTMLElement {
    return h(
      "main",
      { class: "screen center" },
      h("h1", null, "Unlock vault"),
      h("p", { class: "muted" }, this.fileName),
      this.dirty && this.mode === "file" ? h("p", { class: "warning" }, "Unsaved changes. Unlock to save them.") : null,
      h(
        "form",
        {
          class: "stack",
          onsubmit: (e: Event) => {
            e.preventDefault();
            const input = (e.target as HTMLFormElement).elements.namedItem("password") as HTMLInputElement;
            const pw = input.value;
            void this.run(() => this.unlock(pw));
          },
        },
        h(
          "label",
          null,
          "Password",
          h("input", { type: "password", name: "password", autocomplete: "current-password", "data-autofocus": true }),
        ),
        this.formError ? h("p", { class: "error", role: "alert" }, this.formError) : null,
        h(
          "div",
          { class: "row" },
          h("button", { type: "submit", class: "primary" }, "Unlock"),
          this.openFileInput("Open another vault"),
          this.mode === "pwa" ? this.button("Remove from this device", () => this.closeVault()) : null,
        ),
      ),
    );
  }

  private listScreen(): HTMLElement {
    const vault = this.requireVault();
    const names = vault.displayNames();
    const pwa = this.mode === "pwa";
    return h(
      "main",
      { class: "screen" },
      this.header(
        "Notes",
        this.button("Add note", () => this.addNote(), { class: "primary" }),
        pwa ? this.button("Export vault", () => this.exportVault(false)) : this.button("Save vault", () => this.saveVault()),
        pwa && typeof navigator.canShare === "function" ? this.button("Share vault", () => this.exportVault(true)) : null,
        this.button("Lock", () => this.lock(false)),
        this.button("Settings", () => {
          this.screen = "settings";
        }),
      ),
      h("p", { class: "muted small" }, this.fileName),
      !pwa && this.dirty ? h("p", { class: "warning", role: "status" }, "Unsaved changes") : null,
      this.notExported
        ? h("p", { class: "warning" }, "Changes not exported yet. Browser storage is not a backup.")
        : null,
      names.length === 0
        ? h("p", { class: "muted empty" }, "No notes yet")
        : h(
            "ul",
            { class: "notes", "aria-label": "Notes" },
            names.map((name) =>
              h(
                "li",
                null,
                h("button", { type: "button", class: "note", onclick: () => void this.run(() => this.openNote(name)) }, name),
                this.button("Rename", () => {
                  this.dialog = { kind: "rename", name };
                }, { "aria-label": `Rename ${name}`, class: "small" }),
                this.button("Delete", () => {
                  this.dialog = { kind: "delete", name };
                }, { "aria-label": `Delete ${name}`, class: "small danger" }),
              ),
            ),
          ),
      h("div", { class: "row footer" }, this.button(pwa ? "Remove from this device" : "Close vault", () => this.closeVault())),
    );
  }

  private editScreen(): HTMLElement {
    const e = this.editing;
    if (!e) throw new Error("No note open");
    return h(
      "main",
      { class: "screen editor" },
      this.header(
        e.name,
        this.button("Done", () => this.closeEditor(), { class: "primary" }),
        this.button("Rename", () => {
          this.dialog = { kind: "rename", name: e.name };
        }),
        this.button("Delete", () => {
          this.dialog = { kind: "delete", name: e.name };
        }, { class: "danger" }),
      ),
      h("textarea", {
        "aria-label": "Note content",
        spellcheck: "false",
        autocomplete: "off",
        value: e.content,
        "data-autofocus": true,
        oninput: (ev: Event) => (e.content = (ev.target as HTMLTextAreaElement).value),
      }),
    );
  }

  private settingsScreen(): HTMLElement {
    return h(
      "main",
      { class: "screen" },
      this.header("Settings", this.button("Back", () => {
        this.screen = this.vault ? (this.password !== null ? "list" : "unlock") : "start";
      }, { class: "primary" })),
      h(
        "form",
        {
          class: "stack",
          onsubmit: (ev: Event) => {
            ev.preventDefault();
            const input = (ev.target as HTMLFormElement).elements.namedItem("idle") as HTMLInputElement;
            const value = input.value;
            void this.run(() => this.saveIdleMinutes(value));
          },
        },
        h(
          "label",
          null,
          "Auto-lock after inactivity (minutes)",
          h("input", {
            type: "number",
            name: "idle",
            min: MIN_IDLE_MINUTES,
            max: MAX_IDLE_MINUTES,
            step: 1,
            value: String(this.settings.idleMinutes),
          }),
        ),
        h("div", { class: "row" }, h("button", { type: "submit" }, "Save setting")),
      ),
      h("h2", null, "About"),
      h(
        "dl",
        { class: "about" },
        h("dt", null, "Version"),
        h("dd", null, APP_VERSION),
        h("dt", null, "Mode"),
        h("dd", null, this.mode === "pwa" ? "Installed web app (PWA)" : "Local file"),
        h("dt", null, "App script SHA-256"),
        h("dd", { class: "mono", "data-testid": "script-hash" }, this.scriptHash),
        h("dt", null, "Releases and hashes"),
        h("dd", { class: "mono" }, RELEASES_URL),
      ),
      h(
        "p",
        { class: "muted small" },
        "This app never updates itself and never connects to the internet. To update, install a new version manually.",
      ),
    );
  }

  private renderDialog(): Child {
    const d = this.dialog;
    if (!d) return null;
    const close = () => {
      this.dialog = null;
    };
    const frame = (title: string, ...body: Child[]) =>
      h(
        "div",
        { class: "overlay" },
        h("div", { class: "dialog", role: "dialog", "aria-modal": "true", "aria-label": title }, h("h2", null, title), ...body),
      );
    switch (d.kind) {
      case "alert":
        return frame(
          d.title,
          h("p", { role: "alert" }, d.message),
          h("div", { class: "row" }, this.button("OK", close, { class: "primary", "data-autofocus": true })),
        );
      case "delete":
        return frame(
          `Delete ${d.name}?`,
          h("p", null, "This cannot be undone."),
          h(
            "div",
            { class: "row" },
            this.button("Delete", () => this.deleteNote(d.name), { class: "danger" }),
            this.button("Cancel", close, { "data-autofocus": true }),
          ),
        );
      case "confirm":
        return frame(
          d.title,
          h("p", null, d.message),
          h(
            "div",
            { class: "row" },
            this.button(d.confirmLabel, async () => {
              this.dialog = null;
              await d.onConfirm();
            }, { class: "danger" }),
            this.button("Cancel", close, { "data-autofocus": true }),
          ),
        );
      case "rename":
        return frame(
          `Rename ${d.name}`,
          h(
            "form",
            {
              class: "stack",
              onsubmit: (ev: Event) => {
                ev.preventDefault();
                const input = (ev.target as HTMLFormElement).elements.namedItem("name") as HTMLInputElement;
                const to = input.value;
                void this.run(() => this.rename(d.name, to));
              },
            },
            h("label", null, "New name", h("input", { type: "text", name: "name", value: d.name, "data-autofocus": true })),
            h("div", { class: "row" }, h("button", { type: "submit", class: "primary" }, "Rename"), this.button("Cancel", close)),
          ),
        );
      case "entry-password":
        return frame(
          `Password for ${d.name}`,
          h("p", { class: "muted" }, "This note is encrypted with a different password."),
          h(
            "form",
            {
              class: "stack",
              onsubmit: (ev: Event) => {
                ev.preventDefault();
                const input = (ev.target as HTMLFormElement).elements.namedItem("entry-password") as HTMLInputElement;
                const pw = input.value;
                void this.run(() => this.submitEntryPassword(d.name, pw));
              },
            },
            h(
              "label",
              null,
              "Note password",
              h("input", { type: "password", name: "entry-password", autocomplete: "off", "data-autofocus": true }),
            ),
            d.error ? h("p", { class: "error", role: "alert" }, d.error) : null,
            h("div", { class: "row" }, h("button", { type: "submit", class: "primary" }, "Open note"), this.button("Cancel", close)),
          ),
        );
    }
  }
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function corruptMessage(name: string): string {
  return `${name} could not be decrypted: the file is corrupted.`;
}
