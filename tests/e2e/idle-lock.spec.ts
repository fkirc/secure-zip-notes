/** Idle auto-lock: default 60 minutes, user-configurable, never loses unsaved edits. */
import { test, expect, createVault, expectEditor, expectToast, openApp, openNote, unlock, addNote, SECRET_NOTE } from "./helpers.ts";

test("locks after 60 idle minutes and keeps the unsaved edit", async ({ page }) => {
  await page.clock.install();
  await openApp(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  await openNote(page, "Note 1");
  await page.getByLabel("Note content").fill("typed but not closed");

  await page.clock.fastForward("59:00");
  await expect(page.getByLabel("Note content")).toBeVisible();

  await page.clock.fastForward("01:01");
  await expect(page.getByRole("heading", { name: "Unlock vault" })).toBeVisible();
  await expectToast(page, "Locked after 60 minutes of inactivity");
  await expect(page.getByLabel("Note content")).toHaveCount(0);

  await unlock(page);
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", "typed but not closed");
});

test("activity resets the idle timer", async ({ page }) => {
  await page.clock.install();
  await openApp(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  await page.clock.fastForward("50:00");
  await openNote(page, "Note 1");
  await page.clock.fastForward("50:00");
  await expect(page.getByLabel("Note content")).toBeVisible();
  await page.clock.fastForward("10:01");
  await expect(page.getByRole("heading", { name: "Unlock vault" })).toBeVisible();
});

test("idle timeout is configurable", async ({ page }) => {
  await page.clock.install();
  await openApp(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  await page.getByRole("button", { name: "Settings" }).click();
  await page.getByLabel("Auto-lock after inactivity (minutes)").fill("5");
  await page.getByRole("button", { name: "Save setting" }).click();
  await expectToast(page, "Auto-lock after 5 minutes");
  await page.getByRole("button", { name: "Back" }).click();
  await page.clock.fastForward("04:30");
  await expect(page.getByRole("heading", { name: "Notes" })).toBeVisible();
  await page.clock.fastForward("00:31");
  await expect(page.getByRole("heading", { name: "Unlock vault" })).toBeVisible();
  await expectToast(page, "Locked after 5 minutes of inactivity");
});

test("manual lock clears the session; wrong password stays locked", async ({ page }) => {
  await openApp(page);
  await createVault(page);
  await addNote(page, SECRET_NOTE);
  await page.getByRole("button", { name: "Lock" }).click();
  await page.getByLabel("Password").fill("not the password");
  await page.getByRole("button", { name: "Unlock" }).click();
  await expect(page.getByRole("alert")).toHaveText("Wrong password");
  await unlock(page);
  await openNote(page, "Note 1");
  await expectEditor(page, "Note 1", SECRET_NOTE);
});
