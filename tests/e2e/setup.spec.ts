/** Port of v1 SetupTests.kt. */
import { test, expect, openApp, createVault, addNote, expectNotes, openNote, expectEditor, SECRET_NOTE } from "./helpers.ts";

test("create new password, add a note, read it back", async ({ page }) => {
  await openApp(page);
  await createVault(page);
  await expectNotes(page, []);
  await addNote(page, SECRET_NOTE);
  await expectNotes(page, ["Note 1"]);
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", SECRET_NOTE);
});

test("password too short", async ({ page }) => {
  await openApp(page);
  await page.getByRole("button", { name: "Create new vault" }).click();
  await page.getByLabel("Password", { exact: true }).fill("short");
  await page.getByLabel("Confirm password").fill("short");
  await page.getByRole("button", { name: "Create vault" }).click();
  await expect(page.getByRole("alert")).toHaveText("Minimum length: 8 characters");
  await expect(page.getByRole("heading", { name: "New master password" })).toBeVisible();
});

test("password mismatch", async ({ page }) => {
  await openApp(page);
  await page.getByRole("button", { name: "Create new vault" }).click();
  await page.getByLabel("Password", { exact: true }).fill("testpassword");
  await page.getByLabel("Confirm password").fill("testpassworx");
  await page.getByRole("button", { name: "Create vault" }).click();
  await expect(page.getByRole("alert")).toHaveText("Passwords do not match");
});

test("generate random password", async ({ page }) => {
  await openApp(page);
  await page.getByRole("button", { name: "Create new vault" }).click();
  await page.getByRole("button", { name: "Generate random password" }).click();
  const pw = page.getByLabel("Password", { exact: true });
  const confirm = page.getByLabel("Confirm password");
  await expect(pw).toHaveAttribute("type", "text");
  const value = await pw.inputValue();
  expect(value).toMatch(/^[0-9A-Za-z]{20}$/);
  await expect(confirm).toHaveValue(value);
  await page.getByRole("button", { name: "Create vault" }).click();
  await expect(page.getByRole("heading", { name: "Notes" })).toBeVisible();
});
