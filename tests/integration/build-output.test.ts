/** Checks the release artifacts produced by `pnpm build` (run it first). */
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync, mkdirSync, copyFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { readZip } from "../../src/zip/zip.ts";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const DIST = join(ROOT, "dist");
const version = (JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8")) as { version: string }).version;
const sha256 = (d: Uint8Array | string) => createHash("sha256").update(d).digest("hex");

function sums(): Map<string, string> {
  const m = new Map<string, string>();
  for (const line of readFileSync(join(DIST, "SHA256SUMS"), "utf8").trim().split("\n")) m.set(line.slice(66), line.slice(0, 64));
  return m;
}

test("dist exists (run pnpm build first)", () => {
  assert.ok(existsSync(join(DIST, "SHA256SUMS")), "dist/SHA256SUMS missing; run pnpm build");
});

test("SHA256SUMS matches every output file and is sorted", () => {
  const text = readFileSync(join(DIST, "SHA256SUMS"), "utf8");
  const paths = [...sums().keys()];
  assert.deepEqual(paths, [...paths].sort());
  for (const [path, hash] of sums()) assert.equal(sha256(readFileSync(join(DIST, path))), hash, path);
  assert.equal(readFileSync(join(DIST, "RELEASE_DIGEST"), "utf8").trim(), sha256(text));
});

test("release zip contains exactly index.html, LICENSE and README.txt with fixed timestamps", () => {
  const zip = readZip(new Uint8Array(readFileSync(join(DIST, `secure-zip-notes-v${version}.zip`))));
  const base = `secure-zip-notes-v${version}/`;
  assert.deepEqual(zip.map((e) => e.name), [`${base}LICENSE`, `${base}README.txt`, `${base}index.html`]);
  for (const e of zip) assert.deepEqual([e.dosDate, e.dosTime, e.method], [0x21, 0, 0]);
  const index = zip.find((e) => e.name.endsWith("index.html"));
  assert.ok(index);
  assert.deepEqual(Buffer.from(index.data), readFileSync(join(DIST, "pwa", "index.html")), "zip and PWA serve the same index.html");
});

test("7-Zip extracts the release zip", () => {
  const dir = mkdtempSync(join(tmpdir(), "szn-rel-"));
  try {
    execFileSync(process.env["SEVEN_ZIP"] ?? "7z", ["x", `-o${dir}`, join(DIST, `secure-zip-notes-v${version}.zip`)], { stdio: "ignore" });
    const extracted = readFileSync(join(dir, `secure-zip-notes-v${version}`, "index.html"));
    assert.deepEqual(extracted, readFileSync(join(DIST, "pwa", "index.html")));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("PWA zip matches the pwa/ folder", () => {
  const zip = readZip(new Uint8Array(readFileSync(join(DIST, `secure-zip-notes-v${version}-pwa.zip`))));
  const s = sums();
  for (const e of zip) assert.equal(sha256(e.data), s.get(`pwa/${e.name}`), e.name);
  assert.equal(zip.length, [...s.keys()].filter((p) => p.startsWith("pwa/")).length);
});

test("shipped code contains no external URLs except the releases link, and no eval", () => {
  const html = readFileSync(join(DIST, "pwa", "index.html"), "utf8");
  const sw = readFileSync(join(DIST, "pwa", "sw.js"), "utf8");
  for (const text of [html, sw]) {
    const urls = text.match(/https?:\/\/[^\s"'`<)]+/g) ?? [];
    assert.deepEqual(urls.filter((u) => u !== "https://github.com/fkirc/secure-zip-notes/releases"), []);
    assert.ok(!/\beval\(|new Function\(/.test(text));
  }
});

test("service worker never self-updates, claims clients, or fetches", () => {
  const sw = readFileSync(join(DIST, "pwa", "sw.js"), "utf8");
  assert.ok(!sw.includes("skipWaiting"));
  assert.ok(!sw.includes("clients.claim"));
  assert.ok(!/\bfetch\(/.test(sw));
  assert.ok(!sw.includes("caches.delete"));
});

test("assemble-pages verifies releases and lays out immutable version paths", () => {
  const tmp = mkdtempSync(join(tmpdir(), "szn-pages-"));
  try {
    const tag = `v${version}`;
    mkdirSync(join(tmp, "releases", tag), { recursive: true });
    copyFileSync(join(DIST, "SHA256SUMS"), join(tmp, "releases", tag, "SHA256SUMS"));
    copyFileSync(join(DIST, `secure-zip-notes-${tag}-pwa.zip`), join(tmp, "releases", tag, `secure-zip-notes-${tag}-pwa.zip`));
    execFileSync(process.execPath, [join(ROOT, "scripts/assemble-pages.ts"), "--releases", join(tmp, "releases"), "--out", join(tmp, "site")], { stdio: "ignore" });
    for (const [path, hash] of sums()) {
      if (!path.startsWith("pwa/")) continue;
      assert.equal(sha256(readFileSync(join(tmp, "site", tag, path.slice(4)))), hash, path);
    }
    assert.match(readFileSync(join(tmp, "site", "index.html"), "utf8"), new RegExp(`\\./${tag}/`));

    // A tampered release must be rejected.
    const sumsPath = join(tmp, "releases", tag, "SHA256SUMS");
    const tampered = readFileSync(sumsPath, "utf8").replace(/^[0-9a-f]{64}(?= {2}pwa\/index\.html)/m, "0".repeat(64));
    writeFileSync(sumsPath, tampered);
    assert.throws(() =>
      execFileSync(process.execPath, [join(ROOT, "scripts/assemble-pages.ts"), "--releases", join(tmp, "releases"), "--out", join(tmp, "site2")], { stdio: "ignore" }),
    );
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

