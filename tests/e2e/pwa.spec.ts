/** PWA variant served over http://localhost (a secure context), like GitHub Pages. Chromium only. */
import { readFileSync } from "node:fs";
import {
  test,
  expect,
  addNote,
  createVault,
  expectEditor,
  expectNotes,
  expectToast,
  openNote,
  unlock,
  PWA_URL,
  ROOT,
  SECRET_NOTE,
  TESTPASSWORD,
} from "./helpers.ts";
import { Vault } from "../../src/vault/vault.ts";

test.use({ allowedOrigins: ["http://localhost:4173/secure-zip-notes/"] });

async function openPwa(page: import("@playwright/test").Page): Promise<void> {
  await page.goto(PWA_URL);
  await expect(page.getByRole("button", { name: "Create new vault" }).or(page.getByRole("heading", { name: "Unlock vault" }))).toBeVisible();
}

async function waitForServiceWorker(page: import("@playwright/test").Page): Promise<void> {
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
}

test("installable: manifest, icons and service worker", async ({ page }) => {
  await openPwa(page);
  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute("href", "./manifest.webmanifest");
  await waitForServiceWorker(page);
  const manifest = JSON.parse(readFileSync(ROOT + "dist/pwa/manifest.webmanifest", "utf8")) as Record<string, unknown>;
  expect(manifest["display"]).toBe("standalone");
  expect(manifest["start_url"]).toBe("./");
  expect(manifest["scope"]).toBe("./");
  for (const path of ["manifest.webmanifest", "icons/icon-192.png", "icons/icon-512.png", "icons/apple-touch-icon.png", "sw.js"]) {
    const res = await page.request.get(PWA_URL + path);
    expect(res.status(), path).toBe(200);
  }
  const scope = await page.evaluate(async () => (await navigator.serviceWorker.ready).scope);
  expect(scope).toBe(PWA_URL);
});

test("works offline after the first visit, and keeps the encrypted vault", async ({ page, context }) => {
  await openPwa(page);
  await waitForServiceWorker(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  await expect(page.getByText("Changes not exported yet")).toBeVisible();

  await context.setOffline(true);
  const failed: string[] = [];
  page.on("requestfailed", (r) => failed.push(r.url()));
  const fromNetwork: string[] = [];
  page.on("response", (r) => {
    if (!r.fromServiceWorker()) fromNetwork.push(r.url());
  });
  await page.reload();
  await unlock(page);
  await expectNotes(page, ["Note 1"]);
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", SECRET_NOTE);
  expect(failed).toEqual([]);
  expect(fromNetwork).toEqual([]);
});

test("only ciphertext is stored in the browser", async ({ page }) => {
  await openPwa(page);
  await createVault(page);
  await addNote(page, "plaintext must not be stored");
  const stored = await page.evaluate(
    () =>
      new Promise<number[]>((resolve, reject) => {
        const req = indexedDB.open("secure-zip-notes", 1);
        req.onsuccess = () => {
          const get = req.result.transaction("vault").objectStore("vault").get("current");
          get.onsuccess = () => resolve(Array.from(new Uint8Array((get.result as { bytes: Uint8Array }).bytes)));
          get.onerror = () => reject(get.error);
        };
        req.onerror = () => reject(req.error);
      }),
  );
  const bytes = new Uint8Array(stored);
  expect(Buffer.from(bytes).toString("latin1")).not.toContain("plaintext must not be stored");
  expect(Buffer.from(bytes).toString("latin1")).not.toContain(TESTPASSWORD);
  const vault = Vault.open(bytes);
  expect(await vault.read("Note 1", TESTPASSWORD)).toEqual({ ok: true, content: "plaintext must not be stored" });
});

test("export downloads the vault and clears the reminder", async ({ page }, testInfo) => {
  await openPwa(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export vault" }).click();
  const download = await downloadPromise;
  const path = testInfo.outputPath("export.zip");
  await download.saveAs(path);
  await expectToast(page, "Exported secure-zip-notes.zip");
  await expect(page.getByText("Changes not exported yet")).toHaveCount(0);
  const vault = Vault.open(new Uint8Array(readFileSync(path)));
  expect(await vault.read("Note 1", TESTPASSWORD)).toEqual({ ok: true, content: SECRET_NOTE });
});

test("remove the vault from this device", async ({ page }) => {
  await openPwa(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  await page.getByRole("button", { name: "Remove from this device" }).click();
  await expect(page.getByRole("dialog")).toContainText("never exported");
  await page.getByRole("dialog").getByRole("button", { name: "Remove" }).click();
  await expect(page.getByRole("button", { name: "Create new vault" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: "Create new vault" })).toBeVisible();
});

test("the service worker never updates or claims clients", async () => {
  const sw = readFileSync(ROOT + "dist/pwa/sw.js", "utf8");
  expect(sw).not.toContain("skipWaiting");
  expect(sw).not.toContain("clients.claim");
  expect(sw).not.toMatch(/\bfetch\(/);
  expect(sw).not.toContain("caches.delete");
});

test("refuses to run inside a frame", async ({ page }) => {
  await page.goto(PWA_URL + "manifest.webmanifest");
  await page.setContent(`<iframe src="${PWA_URL}"></iframe>`);
  const frame = page.frameLocator("iframe");
  await expect(frame.getByText("refuses to run inside a frame")).toBeVisible();
});
