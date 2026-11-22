/**
 * Assembles the GitHub Pages site from published releases. Every version lives at its
 * own immutable path /secure-zip-notes/<tag>/, so installed PWAs never change.
 *
 *   node scripts/assemble-pages.ts --releases <dir> --out <site>
 *
 * <dir>/<tag>/ must contain that release's SHA256SUMS and secure-zip-notes-<tag>-pwa.zip.
 * Every file is checked against the release's SHA256SUMS; any mismatch aborts.
 */
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { readZip } from "../src/zip/zip.ts";

function arg(name: string): string {
  const i = process.argv.indexOf(name);
  const v = i >= 0 ? process.argv[i + 1] : undefined;
  if (!v) throw new Error(`missing ${name}`);
  return v;
}

const sha256 = (d: Uint8Array | string) => createHash("sha256").update(d).digest("hex");

function parseSums(text: string): Map<string, string> {
  const m = new Map<string, string>();
  for (const line of text.split("\n")) {
    const match = /^([0-9a-f]{64}) {2}(.+)$/.exec(line);
    if (match) m.set(match[2] as string, match[1] as string);
  }
  return m;
}

function compareTags(a: string, b: string): number {
  const pa = a.slice(1).split(".").map(Number);
  const pb = b.slice(1).split(".").map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

const releasesDir = arg("--releases");
const out = arg("--out");
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });

const tags = readdirSync(releasesDir)
  .filter((t) => /^v\d+\.\d+\.\d+$/.test(t) && existsSync(join(releasesDir, t, `secure-zip-notes-${t}-pwa.zip`)))
  .sort(compareTags);
if (tags.length === 0) throw new Error("no releases with a PWA found");

const rows: string[] = [];
for (const tag of tags) {
  const sumsText = readFileSync(join(releasesDir, tag, "SHA256SUMS"), "utf8");
  const sums = parseSums(sumsText);
  const zipName = `secure-zip-notes-${tag}-pwa.zip`;
  const zip = new Uint8Array(readFileSync(join(releasesDir, tag, zipName)));
  if (sums.get(zipName) !== sha256(zip)) throw new Error(`${tag}: ${zipName} does not match SHA256SUMS`);
  const expected = [...sums.keys()].filter((p) => p.startsWith("pwa/")).sort();
  const entries = readZip(zip);
  const names = entries.map((e) => `pwa/${e.name}`).sort();
  if (JSON.stringify(names) !== JSON.stringify(expected)) throw new Error(`${tag}: PWA file list differs from SHA256SUMS`);
  for (const e of entries) {
    if (sums.get(`pwa/${e.name}`) !== sha256(e.data)) throw new Error(`${tag}: ${e.name} does not match SHA256SUMS`);
    if (e.name.includes("..") || e.name.startsWith("/")) throw new Error(`${tag}: bad path ${e.name}`);
    const target = join(out, tag, e.name);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, e.data);
  }
  rows.unshift(
    `<li><a href="./${escapeHtml(tag)}/">${escapeHtml(tag)}</a> <a href="https://github.com/fkirc/secure-zip-notes/releases/tag/${escapeHtml(tag)}">release notes and hashes</a><br><code>${sha256(sumsText)}</code></li>`,
  );
  console.log(`${tag}: ${entries.length} files verified`);
}

const style = `body{font-family:system-ui,sans-serif;max-width:720px;margin:40px auto;padding:0 16px;line-height:1.5}code{font-size:.8rem;word-break:break-all}li{margin:12px 0}`;
const csp = `default-src 'none'; style-src 'sha256-${createHash("sha256").update(style).digest("base64")}'; base-uri 'none'; form-action 'none'`;
const latest = tags[tags.length - 1] as string;
const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="${csp}">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>Secure Zip Notes</title>
<link rel="icon" href="data:,">
<style>${style}</style>
</head>
<body>
<h1>Secure Zip Notes</h1>
<p>Encrypted notes in a standard AES-256 Zip file. Every version below is a separate, immutable web app: once installed, it never updates itself. To update, export your vault, open the newer version, install it, and open the vault there.</p>
<p><strong>Latest: <a href="./${escapeHtml(latest)}/">${escapeHtml(latest)}</a></strong></p>
<h2>All versions</h2>
<p>The code under each link is the release digest (SHA-256 of the release's SHA256SUMS).</p>
<ul>
${rows.join("\n")}
</ul>
<p><a href="https://github.com/fkirc/secure-zip-notes">Source code and reproducible build instructions</a></p>
</body>
</html>
`;
writeFileSync(join(out, "index.html"), html);
console.log(`Site assembled in ${out} (${tags.length} versions, latest ${latest})`);
