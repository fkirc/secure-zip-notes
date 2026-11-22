/** Port of v1 ImportTests.kt and MultiPasswordTests.kt, using the v1 fixture files. */
import { readFileSync } from "node:fs";
import {
  test,
  expect,
  expectEditor,
  expectNotes,
  FIXTURES,
  openApp,
  openNote,
  openVaultFile,
  unlock,
  SECRET_NOTE,
  TESTPASSWORD,
} from "./helpers.ts";

test("import single note", async ({ page }) => {
  await openApp(page);
  await openVaultFile(page, FIXTURES + "singlenote.aeszip");
  await unlock(page);
  await expectNotes(page, ["Note 1"]);
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", SECRET_NOTE);
});

test("import 4 notes", async ({ page }) => {
  await openApp(page);
  await openVaultFile(page, FIXTURES + "4notes.aeszip");
  await unlock(page);
  await expectNotes(page, ["Note 4", "Note 3", "Note 2", "Note 1"]);
  await openNote(page, "Note 4");
  await expectEditor(page, "Note 4", "4");
});

test("wrong password is rejected", async ({ page }) => {
  await openApp(page);
  await openVaultFile(page, FIXTURES + "singlenote.aeszip");
  await page.getByLabel("Password").fill("wrongpassword");
  await page.getByRole("button", { name: "Unlock" }).click();
  await expect(page.getByRole("alert")).toHaveText("Wrong password");
  await unlock(page);
});

test("import subdirectory names with 4 passwords", async ({ page }) => {
  await openApp(page);
  await openVaultFile(page, FIXTURES + "4passwords_subdirs.aeszip");
  await unlock(page, "pw1");
  await expectNotes(page, ["pw4_entry", "pw3_entry/dir/dir/pw2", "pw2_entry", "pw1_entry/dir/pw1"]);
});

const hundredA = readFileSync(FIXTURES + "compression/100a.txt", "utf8");
for (const f of ["100a_flat", "100a_deflate", "100a_deflate_fast", "100a_deflate_ultra"]) {
  test(`import compression ${f}`, async ({ page }) => {
    await openApp(page);
    await openVaultFile(page, FIXTURES + `compression/${f}.aeszip`);
    await unlock(page);
    await expectNotes(page, ["100a.txt"]);
    await openNote(page, "100a.txt");
    await expectEditor(page, "100a.txt", hundredA);
  });
}

const failures: Array<[string, string]> = [
  ["subdirs.aeszip", "Import failed. Zip files with pure directory entries are not supported."],
  ["invalid/emptynote.7z", "Import failed. Probably this is not a valid Zip file."],
  ["invalid/emptyzip.zip", "Import failed. Probably this is not a valid Zip file."],
  ["invalid/unencrypted_note.zip", "Import failed. Zip files with non-encrypted entries are not supported."],
  ["invalid/broken_zipcrypto.zip", "Unsupported encryption algorithm. This app only supports Zip files with AES encryption."],
  ["compression/100a_bzip2.aeszip", "Import failed: This app does not support BZIP2 compression."],
  ["compression/100a_deflate64.aeszip", "Import failed: This app does not support DEFLATE64 compression."],
  ["compression/100a_lzma.aeszip", "Import failed: This app does not support LZMA compression."],
  ["compression/100a_ppmd.aeszip", "Import failed: This app does not support PPMD compression."],
];
for (const [file, message] of failures) {
  test(`import rejects ${file}`, async ({ page }) => {
    await openApp(page);
    await openVaultFile(page, FIXTURES + file);
    await expect(page.getByRole("dialog").getByRole("alert")).toHaveText(message);
    await page.getByRole("button", { name: "OK" }).click();
    await expect(page.getByRole("button", { name: "Create new vault" })).toBeVisible();
  });
}

test("two passwords (MultiPasswordTests.twoPasswords)", async ({ page }) => {
  await openApp(page);
  await openVaultFile(page, FIXTURES + "twopasswords.ZIP");
  await unlock(page, TESTPASSWORD);
  await expectNotes(page, ["pw2_entry", "testpassword_entry"]);

  await openNote(page, "testpassword_entry");
  await expectEditor(page, "testpassword_entry", SECRET_NOTE);
  await page.getByRole("button", { name: "Done" }).click();

  await openNote(page, "pw2_entry");
  const dialog = page.getByRole("dialog", { name: "Password for pw2_entry" });
  await dialog.getByLabel("Note password").fill("wrong");
  await dialog.getByRole("button", { name: "Open note" }).click();
  await expect(dialog.getByRole("alert")).toHaveText("Wrong password");
  await dialog.getByLabel("Note password").fill("pw2");
  await dialog.getByRole("button", { name: "Open note" }).click();
  await expectEditor(page, "pw2_entry", "secret");
  await page.getByRole("button", { name: "Done" }).click();

  // The entry password is remembered for the session.
  await openNote(page, "pw2_entry");
  await expectEditor(page, "pw2_entry", "secret");
});

test("four passwords (MultiPasswordTests.test4Passwords)", async ({ page }) => {
  await openApp(page);
  await openVaultFile(page, FIXTURES + "4passwords_subdirs.aeszip");
  await unlock(page, "pw4");
  for (const [name, pw, secret] of [
    ["pw4_entry", null, "pw4_secret"],
    ["pw3_entry/dir/dir/pw2", "pw3", "pw3_secret"],
    ["pw2_entry", "pw2", "pw2_secret"],
    ["pw1_entry/dir/pw1", "pw1", "pw1_secret"],
  ] as const) {
    await openNote(page, name);
    if (pw) {
      const dialog = page.getByRole("dialog", { name: `Password for ${name}` });
      await dialog.getByLabel("Note password").fill(pw);
      await dialog.getByRole("button", { name: "Open note" }).click();
    }
    await expectEditor(page, name, secret);
    await page.getByRole("button", { name: "Done" }).click();
  }
});
