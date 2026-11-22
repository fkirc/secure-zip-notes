/** Compatibility with zip files from v1 (legacy/app/src/androidTest/assets), mirroring ImportTests.kt and MultiPasswordTests.kt. */
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fixture, FIXTURES_DIR, TESTPASSWORD } from "../helpers/fixtures.ts";
import { ImportError, Vault } from "../../src/vault/vault.ts";

async function expectNotes(path: string, expected: Array<[name: string, password: string, content: string]>) {
  const v = Vault.open(fixture(path));
  assert.deepEqual(v.displayNames(), expected.map(([n]) => n));
  for (const [name, pw, content] of expected) {
    assert.deepEqual(await v.read(name, pw), { ok: true, content }, `${path}: ${name}`);
  }
  return v;
}

test("singlenote.aeszip", async () => {
  await expectNotes("singlenote.aeszip", [["Note 1", TESTPASSWORD, "My secret note"]]);
});

test("4notes.aeszip", async () => {
  await expectNotes("4notes.aeszip", [
    ["Note 4", TESTPASSWORD, "4"],
    ["Note 3", TESTPASSWORD, "3"],
    ["Note 2", TESTPASSWORD, "2"],
    ["Note 1", TESTPASSWORD, "My secret note"],
  ]);
});

test("twopasswords.ZIP: entries with different passwords", async () => {
  const v = await expectNotes("twopasswords.ZIP", [
    ["pw2_entry", "pw2", "secret"],
    ["testpassword_entry", TESTPASSWORD, "My secret note"],
  ]);
  assert.deepEqual(await v.read("pw2_entry", TESTPASSWORD), { ok: false, reason: "wrong-password" });
});

test("4passwords_subdirs.aeszip: four passwords and subdirectory names", async () => {
  await expectNotes("4passwords_subdirs.aeszip", [
    ["pw4_entry", "pw4", "pw4_secret"],
    ["pw3_entry/dir/dir/pw2", "pw3", "pw3_secret"],
    ["pw2_entry", "pw2", "pw2_secret"],
    ["pw1_entry/dir/pw1", "pw1", "pw1_secret"],
  ]);
});

const hundredA = readFileSync(FIXTURES_DIR + "compression/100a.txt", "utf8");

for (const f of ["100a_flat", "100a_deflate", "100a_deflate_fast", "100a_deflate_ultra"]) {
  test(`compression/${f}.aeszip decodes to 100a.txt`, async () => {
    assert.equal(hundredA, "a".repeat(100));
    await expectNotes(`compression/${f}.aeszip`, [["100a.txt", TESTPASSWORD, hundredA]]);
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

for (const [path, message] of failures) {
  test(`${path} is rejected with v1's message`, () => {
    assert.throws(() => Vault.open(fixture(path)), (e: unknown) => e instanceof ImportError && e.message === message);
  });
}

test("v1 vault: edit with v2, re-save, other entries still decrypt", async () => {
  const original = fixture("4notes.aeszip");
  const v = Vault.open(original);
  await v.update("Note 2", "Note 2", "edited in v2", TESTPASSWORD);
  const reopened = Vault.open(v.toZip());
  assert.deepEqual(await reopened.read("Note 2", TESTPASSWORD), { ok: true, content: "edited in v2" });
  assert.deepEqual(await reopened.read("Note 1", TESTPASSWORD), { ok: true, content: "My secret note" });
  assert.deepEqual(await reopened.read("Note 4", TESTPASSWORD), { ok: true, content: "4" });
});
