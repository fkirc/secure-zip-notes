import { test } from "node:test";
import assert from "node:assert/strict";
import { entryNameError, ImportError, MSG_INVALID_ZIP, Vault } from "../../src/vault/vault.ts";

const PW = "testpassword";

async function vaultWith(...names: string[]): Promise<Vault> {
  const v = Vault.empty();
  for (const n of names) await v.add(n, `content of ${n}`, PW);
  return v;
}

test("unused names follow v1's 'Note N' scheme", async () => {
  const v = Vault.empty();
  assert.equal(v.unusedName(), "Note 1");
  await v.add("Note 1", "", PW);
  assert.equal(v.unusedName(), "Note 2");
  await v.add("Note 2", "", PW);
  v.rename("Note 2", "Note 3");
  assert.equal(v.unusedName(), "Note 4");
});

test("list is newest first, like v1", async () => {
  const v = await vaultWith("Note 1", "Note 2", "Note 3");
  assert.deepEqual(v.displayNames(), ["Note 3", "Note 2", "Note 1"]);
});

test("rename validation matches v1 messages", async () => {
  const v = await vaultWith("Note 1", "Note 2");
  assert.equal(v.renameError("Note 1", "Note 2"), "Note 2 already exists");
  assert.equal(v.renameError("Note 1", ""), "Empty file names are not allowed");
  assert.equal(v.renameError("Note 1", "directory/"), "directory/ is an invalid entry name");
  assert.equal(v.renameError("Note 1", "\\"), "\\ is an invalid entry name");
  assert.equal(v.renameError("Note 1", "/abs"), "/abs is an invalid entry name");
  assert.equal(v.renameError("Note 1", "sub/dir/ok"), null);
  assert.equal(v.renameError("Note 1", "Note 1"), null);
  assert.equal(entryNameError("ok"), null);
});

test("add rejects duplicates and invalid names", async () => {
  const v = await vaultWith("Note 1");
  await assert.rejects(v.add("Note 1", "", PW), /already exists/);
  await assert.rejects(v.add("", "", PW), /Empty file names/);
});

test("rename keeps content and does not need the password", async () => {
  const v = await vaultWith("Note 1");
  v.rename("Note 1", "Note renamed");
  assert.deepEqual(v.names(), ["Note renamed"]);
  assert.deepEqual(await v.read("Note renamed", PW), { ok: true, content: "content of Note 1" });
});

test("update re-encrypts in place, optionally renaming", async () => {
  const v = await vaultWith("Note 1", "Note 2", "Note 3");
  await v.update("Note 2", "Changed", "new content", PW);
  assert.deepEqual(v.names(), ["Note 1", "Changed", "Note 3"]);
  assert.deepEqual(await v.read("Changed", PW), { ok: true, content: "new content" });
  await assert.rejects(v.update("Changed", "Note 1", "x", PW), /already exists/);
});

test("delete, including the last note", async () => {
  const v = await vaultWith("Note 1", "Note 2", "Note 3", "Note 4");
  v.delete("Note 2");
  v.delete("Note 3");
  v.delete("Note 4");
  assert.deepEqual(v.names(), ["Note 1"]);
  v.delete("Note 1");
  assert.equal(v.size, 0);
});

test("save and reopen gives identical notes", async () => {
  const v = await vaultWith("Note 1", "dir/Note 2", "Ümlaut ✓");
  await v.update("Note 1", "Note 1", "line 1\nline 2\n", PW);
  const reopened = Vault.open(v.toZip());
  assert.deepEqual(reopened.names(), v.names());
  for (const n of v.names()) assert.deepEqual(await reopened.read(n, PW), await v.read(n, PW));
  assert.deepEqual(reopened.toZip(), v.toZip(), "unchanged entries are written back byte-for-byte");
});

test("unlocks() checks the password against the entries", async () => {
  const v = await vaultWith("Note 1");
  assert.equal(await v.unlocks(PW), true);
  assert.equal(await v.unlocks("nope nope"), false);
});

test("empty vault cannot be opened as a file (same as v1)", () => {
  assert.throws(() => Vault.open(Vault.empty().toZip()), (e: unknown) => e instanceof ImportError && e.message === MSG_INVALID_ZIP);
});

test("large note (1 MB) round trip", async () => {
  const big = "x".repeat(1024 * 1024);
  const v = Vault.empty();
  await v.add("big", big, PW);
  const res = await Vault.open(v.toZip()).read("big", PW);
  assert.ok(res.ok && res.content === big);
});
