/** Saving the vault (zip variant): download, in-place save, and reopening. */
import { readFileSync } from "node:fs";
import {
  test,
  expect,
  addNote,
  createVault,
  disableSavePicker,
  expectEditor,
  expectNotes,
  expectToast,
  FIXTURES,
  openApp,
  openNote,
  openVaultFile,
  unlock,
  vaultWithNotes,
  SECRET_NOTE,
  TESTPASSWORD,
} from "./helpers.ts";
import { Vault } from "../../src/vault/vault.ts";

test("save via download, reopen, identical notes", async ({ page }, testInfo) => {
  await disableSavePicker(page);
  await vaultWithNotes(page, 3);
  await expect(page.getByText("Unsaved changes")).toBeVisible();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Save vault" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("secure-zip-notes.zip");
  const path = testInfo.outputPath("saved.zip");
  await download.saveAs(path);
  await expectToast(page, "Saved secure-zip-notes.zip");
  await expect(page.getByText("Unsaved changes")).toHaveCount(0);

  // The file is a real AES zip that the core library (and so 7-Zip, see integration tests) can read.
  const vault = Vault.open(new Uint8Array(readFileSync(path)));
  expect(vault.displayNames()).toEqual(["Note 3", "Note 2", "Note 1"]);
  expect(await vault.read("Note 1", TESTPASSWORD)).toEqual({ ok: true, content: SECRET_NOTE });

  await page.reload();
  await openVaultFile(page, path);
  await unlock(page);
  await expectNotes(page, ["Note 3", "Note 2", "Note 1"]);
  await openNote(page, "Note 2");
  await expectEditor(page, "Note 2", "2");
});

test("edit a v1 vault and save it, keeping the file name", async ({ page }, testInfo) => {
  await disableSavePicker(page);
  await openApp(page);
  await openVaultFile(page, FIXTURES + "4notes.aeszip");
  await unlock(page);
  await openNote(page, "Note 2");
  await page.getByLabel("Note content").fill("edited in v2");
  await page.getByRole("button", { name: "Done" }).click();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Save vault" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("4notes.aeszip");
  const path = testInfo.outputPath("4notes.aeszip");
  await download.saveAs(path);
  const vault = Vault.open(new Uint8Array(readFileSync(path)));
  expect(await vault.read("Note 2", TESTPASSWORD)).toEqual({ ok: true, content: "edited in v2" });
  expect(await vault.read("Note 1", TESTPASSWORD)).toEqual({ ok: true, content: SECRET_NOTE });
});

test("in-place save with the File System Access API asks for a location only once", async ({ page, browserName }) => {
  test.skip(browserName !== "chromium", "File System Access API is Chromium-only");
  await page.addInitScript(() => {
    const w = window as unknown as Record<string, unknown>;
    w["__pickerCalls"] = 0;
    w["__writes"] = [] as number[][];
    w["showSaveFilePicker"] = async (opts: { suggestedName: string }) => {
      w["__pickerCalls"] = (w["__pickerCalls"] as number) + 1;
      return {
        name: opts.suggestedName.replace(".zip", "-picked.zip"),
        createWritable: async () => ({
          write: async (blob: Blob) => {
            (w["__writes"] as number[][]).push(Array.from(new Uint8Array(await blob.arrayBuffer())));
          },
          close: async () => {},
        }),
      };
    };
  });
  await vaultWithNotes(page, 1);
  await page.getByRole("button", { name: "Save vault" }).click();
  await expectToast(page, "Saved secure-zip-notes-picked.zip");
  await addNote(page, "second");
  await page.getByRole("button", { name: "Save vault" }).click();
  await expect.poll(() => page.evaluate(() => (window as unknown as { __writes: number[][] }).__writes.length)).toBe(2);
  expect(await page.evaluate(() => (window as unknown as { __pickerCalls: number }).__pickerCalls)).toBe(1);
  const last = await page.evaluate(() => (window as unknown as { __writes: number[][] }).__writes[1] ?? []);
  const vault = Vault.open(new Uint8Array(last));
  expect(vault.displayNames()).toEqual(["Note 2", "Note 1"]);
  expect(await vault.read("Note 2", TESTPASSWORD)).toEqual({ ok: true, content: "second" });
});

test("closing with unsaved changes asks for confirmation", async ({ page }) => {
  await openApp(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  let beforeUnload = false;
  page.on("dialog", async (d) => {
    if (d.type() === "beforeunload") beforeUnload = true;
    await d.accept();
  });
  await page.close({ runBeforeUnload: true });
  await expect.poll(() => beforeUnload).toBe(true);
});

test("closing the vault with unsaved changes warns", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await page.getByRole("button", { name: "Close vault" }).click();
  await expect(page.getByRole("dialog")).toContainText("unsaved changes will be lost");
  await page.getByRole("button", { name: "Cancel" }).click();
  await expectNotes(page, ["Note 1"]);
  await page.getByRole("button", { name: "Close vault" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Close" }).click();
  await expect(page.getByRole("button", { name: "Create new vault" })).toBeVisible();
});
