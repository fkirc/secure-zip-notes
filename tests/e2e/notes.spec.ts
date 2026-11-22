/** Port of v1 ITest.kt. */
import {
  test,
  expect,
  addNote,
  createVault,
  deleteFromList,
  disableSavePicker,
  expectEditor,
  expectNotes,
  expectToast,
  openApp,
  openNote,
  renameFromList,
  vaultWithNotes,
  SECRET_NOTE,
} from "./helpers.ts";

test("add multiple notes", async ({ page }) => {
  await vaultWithNotes(page, 4);
  await expectNotes(page, ["Note 4", "Note 3", "Note 2", "Note 1"]);
  await openNote(page, "Note 3");
  await expectEditor(page, "Note 3", "3");
});

test("invalid renames from the list", async ({ page }) => {
  await vaultWithNotes(page, 4);
  await renameFromList(page, "Note 1", "Note 2");
  await expectToast(page, "Note 2 already exists");
  await renameFromList(page, "Note 2", "");
  await expectToast(page, "Empty file names are not allowed");
  await renameFromList(page, "Note 2", "directory/");
  await expectToast(page, "directory/ is an invalid entry name");
  await expectNotes(page, ["Note 4", "Note 3", "Note 2", "Note 1"]);
});

test("invalid renames from an open note", async ({ page }) => {
  await vaultWithNotes(page, 4);
  await openNote(page, "Note 1");
  for (const [to, message] of [
    ["Note 2", "Note 2 already exists"],
    ["", "Empty file names are not allowed"],
    ["\\", "\\ is an invalid entry name"],
  ] as const) {
    await page.getByRole("button", { name: "Rename" }).click();
    await page.getByRole("dialog").getByLabel("New name").fill(to);
    await page.getByRole("dialog").getByRole("button", { name: "Rename" }).click();
    await expectToast(page, message);
    await expectEditor(page, "Note 1", SECRET_NOTE);
  }
  await page.getByRole("button", { name: "Done" }).click();
  await expectNotes(page, ["Note 4", "Note 3", "Note 2", "Note 1"]);
});

test("rename a single note from the list", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await renameFromList(page, "Note 1", "Note renamed");
  await expectNotes(page, ["Note renamed"]);
  await openNote(page, "Note renamed");
  await expectEditor(page, "Note renamed", SECRET_NOTE);
});

test("rename an open note", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", SECRET_NOTE);
  await page.getByRole("button", { name: "Rename" }).click();
  await page.getByRole("dialog").getByLabel("New name").fill("Note renamed");
  await page.getByRole("dialog").getByRole("button", { name: "Rename" }).click();
  await expectEditor(page, "Note renamed", SECRET_NOTE);
  await page.getByRole("button", { name: "Done" }).click();
  await expectNotes(page, ["Note renamed"]);
});

test("edit a note", async ({ page }) => {
  await vaultWithNotes(page, 2);
  await openNote(page, "Note 1");
  await page.getByLabel("Note content").fill("changed\nsecond line");
  await page.getByRole("button", { name: "Done" }).click();
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", "changed\nsecond line");
});

test("delete multiple notes", async ({ page }) => {
  await vaultWithNotes(page, 4);
  await deleteFromList(page, "Note 2");
  await expectToast(page, "Removed Note 2");
  await deleteFromList(page, "Note 3");
  await deleteFromList(page, "Note 4");
  await expectNotes(page, ["Note 1"]);
});

test("delete an open note", async ({ page }) => {
  await vaultWithNotes(page, 2);
  await openNote(page, "Note 2");
  await page.getByRole("button", { name: "Delete" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Delete" }).click();
  await expectNotes(page, ["Note 1"]);
});

test("cancel delete keeps the note", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await page.getByRole("button", { name: "Delete Note 1", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();
  await expectNotes(page, ["Note 1"]);
});

test("add note naming conflict", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await addNote(page, "");
  await expectNotes(page, ["Note 2", "Note 1"]);
  await renameFromList(page, "Note 2", "Note 3");
  await expectNotes(page, ["Note 3", "Note 1"]);
  await addNote(page, "");
  await expectNotes(page, ["Note 4", "Note 3", "Note 1"]);
});

test("delete last note", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await deleteFromList(page, "Note 1");
  await expectNotes(page, []);
});

test("export empty vault", async ({ page }) => {
  await disableSavePicker(page);
  await openApp(page);
  await createVault(page);
  await page.getByRole("button", { name: "Save vault" }).click();
  await expectToast(page, "Notes empty, nothing to export");
});

test("unicode note names and content", async ({ page }) => {
  await vaultWithNotes(page, 1);
  await renameFromList(page, "Note 1", "Grüße/日本語 ✓");
  await openNote(page, "Grüße/日本語 ✓");
  await page.getByLabel("Note content").fill("Ünïcödé 🔐");
  await page.getByRole("button", { name: "Done" }).click();
  await openNote(page, "Grüße/日本語 ✓");
  await expectEditor(page, "Grüße/日本語 ✓", "Ünïcödé 🔐");
  await expect(page.getByRole("heading", { name: "Grüße/日本語 ✓" })).toBeVisible();
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByRole("heading", { name: "Notes" })).toBeVisible();
});
