/** Two-way interoperability with 7-Zip, the independent tool v1 promised compatibility with. */
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Vault } from "../../src/vault/vault.ts";
import { fixture, TESTPASSWORD } from "../helpers/fixtures.ts";

const SEVEN_ZIP = process.env["SEVEN_ZIP"] ?? "7z";
let dir = "";

function sevenZip(args: string[], cwd = dir): string {
  return execFileSync(SEVEN_ZIP, args, { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
}

function sevenZipExtract(zipPath: string, entry: string, password: string): string {
  return execFileSync(SEVEN_ZIP, ["x", "-so", `-p${password}`, zipPath, entry], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
}

before(() => {
  dir = mkdtempSync(join(tmpdir(), "szn-7z-"));
  try {
    sevenZip(["i"]);
  } catch {
    throw new Error(`7-Zip not found (${SEVEN_ZIP}). Install p7zip-full / 7zip.`);
  }
});

after(() => rmSync(dir, { recursive: true, force: true }));

test("7-Zip extracts a vault written by v2", async () => {
  const v = Vault.empty();
  await v.add("Note 1", "My secret note", TESTPASSWORD);
  await v.add("dir/Note 2", "Zweite Notiz: Grüße ✓\nline 2", TESTPASSWORD);
  const path = join(dir, "v2.zip");
  writeFileSync(path, v.toZip());

  const test = sevenZip(["t", `-p${TESTPASSWORD}`, path]);
  assert.match(test, /Everything is Ok/);
  const listing = sevenZip(["l", "-slt", path]);
  assert.match(listing, /Method = AES-256/);
  assert.equal(sevenZipExtract(path, "Note 1", TESTPASSWORD), "My secret note");
  assert.equal(sevenZipExtract(path, "dir/Note 2", TESTPASSWORD), "Zweite Notiz: Grüße ✓\nline 2");
});

test("7-Zip rejects the wrong password for a v2 vault", async () => {
  const v = Vault.empty();
  await v.add("Note 1", "My secret note", TESTPASSWORD);
  const path = join(dir, "v2-wrong.zip");
  writeFileSync(path, v.toZip());
  assert.throws(() => sevenZip(["t", "-pwrongpassword", path]));
});

test("v2 opens a zip created by 7-Zip with AES-256 (store and deflate)", async () => {
  const src = join(dir, "src");
  mkdirSync(join(src, "sub"), { recursive: true });
  writeFileSync(join(src, "plain.txt"), "Hello from 7-Zip");
  writeFileSync(join(src, "sub", "long.txt"), "compress me ".repeat(500));
  for (const mx of ["0", "9"]) {
    const path = join(dir, `7z-mx${mx}.zip`);
    sevenZip(["a", "-tzip", "-mem=AES256", `-mx=${mx}`, `-p${TESTPASSWORD}`, path, "plain.txt", "sub/long.txt"], src);
    const v = Vault.open(new Uint8Array(readFileSync(path)));
    assert.deepEqual(v.names().sort(), ["plain.txt", "sub/long.txt"]);
    assert.deepEqual(await v.read("plain.txt", TESTPASSWORD), { ok: true, content: "Hello from 7-Zip" });
    assert.deepEqual(await v.read("sub/long.txt", TESTPASSWORD), { ok: true, content: "compress me ".repeat(500) });
  }
});

test("v2 opens a 7-Zip AES-128 zip", async () => {
  const src = join(dir, "src128");
  mkdirSync(src, { recursive: true });
  writeFileSync(join(src, "n.txt"), "aes128");
  const path = join(dir, "7z-aes128.zip");
  sevenZip(["a", "-tzip", "-mem=AES128", `-p${TESTPASSWORD}`, path, "n.txt"], src);
  const v = Vault.open(new Uint8Array(readFileSync(path)));
  assert.deepEqual(await v.read("n.txt", TESTPASSWORD), { ok: true, content: "aes128" });
});

test("7-Zip extracts v1 fixtures that v2 re-saved", async () => {
  const v = Vault.open(fixture("4passwords_subdirs.aeszip"));
  v.rename("pw4_entry", "renamed by v2");
  const path = join(dir, "resaved.zip");
  writeFileSync(path, v.toZip());
  assert.equal(sevenZipExtract(path, "renamed by v2", "pw4"), "pw4_secret");
  assert.equal(sevenZipExtract(path, "pw1_entry/dir/pw1", "pw1"), "pw1_secret");
});
