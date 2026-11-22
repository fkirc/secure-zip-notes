/**
 * Reproducible build. Same commit + same toolchain (Node from .nvmrc, pnpm lockfile)
 * gives byte-identical output on any machine. See SPEC.md section 8.
 *
 *   node scripts/build.ts [--out dist]
 *
 * Output:
 *   dist/pwa/                                  files deployed to GitHub Pages under /v<version>/
 *   dist/secure-zip-notes-v<version>.zip       index.html + LICENSE + README.txt
 *   dist/secure-zip-notes-v<version>-pwa.zip   the pwa/ folder
 *   dist/SHA256SUMS                            hashes of all of the above
 *   dist/RELEASE_DIGEST                        SHA-256 of SHA256SUMS (identifies the whole release)
 *   dist/APP_SCRIPT_SHA256                     SHA-256 of the inline app script (shown in Settings > About)
 */
import { build } from "esbuild";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { crc32 } from "../src/zip/crc32.ts";
import { DOS_EPOCH, METHOD_STORE, writeZip } from "../src/zip/zip.ts";

const ROOT = fileURLToPath(new URL("..", import.meta.url));
const RELEASES_URL = "https://github.com/fkirc/secure-zip-notes/releases";

function arg(name: string, fallback: string): string {
  const i = process.argv.indexOf(name);
  return i >= 0 && process.argv[i + 1] ? (process.argv[i + 1] as string) : fallback;
}

function read(path: string): Buffer {
  return readFileSync(join(ROOT, path));
}

function sha256Hex(data: Uint8Array | string): string {
  return createHash("sha256").update(data).digest("hex");
}

function sha256Base64(data: string): string {
  return createHash("sha256").update(data, "utf8").digest("base64");
}

function assertNodeVersion(): void {
  const wanted = read(".nvmrc").toString("utf8").trim();
  if (process.version !== `v${wanted}`) {
    throw new Error(`Reproducible builds require Node ${wanted} (from .nvmrc), but this is ${process.version}.`);
  }
}

async function bundle(entry: string, define: Record<string, string>): Promise<string> {
  const result = await build({
    entryPoints: [entry],
    absWorkingDir: ROOT,
    bundle: true,
    format: "iife",
    platform: "browser",
    target: "es2022",
    charset: "utf8",
    legalComments: "none",
    sourcemap: false,
    minify: false,
    write: false,
    define,
    logLevel: "warning",
  });
  const file = result.outputFiles[0];
  if (!file) throw new Error(`esbuild produced no output for ${entry}`);
  // Normalize line endings and make sure the text can be inlined safely.
  return file.text.replace(/\r\n/g, "\n");
}

function assertInlineSafe(name: string, text: string, closingTag: string): void {
  if (text.toLowerCase().includes(closingTag)) throw new Error(`${name} contains ${closingTag}`);
  const urls = text.split(RELEASES_URL).join("").match(/https?:\/\/[^\s"'`)]*/g);
  if (urls) throw new Error(`${name} contains external URLs: ${urls.join(", ")}`);
}

function storeZip(files: Array<[path: string, data: Uint8Array]>): Uint8Array {
  const sorted = [...files].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  return writeZip(
    sorted.map(([name, data]) => ({
      name,
      method: METHOD_STORE,
      flags: 0,
      ...DOS_EPOCH,
      crc: crc32(data),
      uncompressedSize: data.length,
      aes: null,
      data,
    })),
  );
}

async function main(): Promise<void> {
  assertNodeVersion();
  const out = join(ROOT, arg("--out", "dist"));
  const version = (JSON.parse(read("package.json").toString("utf8")) as { version: string }).version;

  const script = await bundle("src/main.ts", { __APP_VERSION__: JSON.stringify(version) });
  const style = read("src/ui/styles.css").toString("utf8").replace(/\r\n/g, "\n");
  assertInlineSafe("app script", script, "</script");
  assertInlineSafe("stylesheet", style, "</style");

  const csp = [
    "default-src 'none'",
    `script-src 'sha256-${sha256Base64(script)}'`,
    `style-src 'sha256-${sha256Base64(style)}'`,
    "img-src 'self' data:",
    "manifest-src 'self'",
    "worker-src 'self'",
    "connect-src 'none'",
    "object-src 'none'",
    "form-action 'none'",
    "base-uri 'none'",
    "require-trusted-types-for 'script'",
    "trusted-types szn-sw",
  ].join("; ");

  const html = read("src/index.html")
    .toString("utf8")
    .replace(/\r\n/g, "\n")
    .split("{{CSP}}").join(csp)
    .split("{{VERSION}}").join(version)
    .split("{{STYLE}}").join(style)
    .split("{{SCRIPT}}").join(script);

  const staticFiles: Array<[string, Uint8Array]> = [
    ["index.html", Buffer.from(html, "utf8")],
    ["manifest.webmanifest", read("public/manifest.webmanifest")],
    ["icons/apple-touch-icon.png", read("public/icons/apple-touch-icon.png")],
    ["icons/icon-192.png", read("public/icons/icon-192.png")],
    ["icons/icon-512.png", read("public/icons/icon-512.png")],
  ];
  const contentDigest = sha256Hex(
    staticFiles.map(([p, d]) => `${sha256Hex(d)}  ${p}\n`).join(""),
  );
  const precache = ["./", ...staticFiles.map(([p]) => p)];
  const sw = await bundle("src/sw.ts", {
    __PRECACHE__: JSON.stringify(precache),
    __CACHE_NAME__: JSON.stringify(`secure-zip-notes-v${version}-${contentDigest.slice(0, 16)}`),
  });
  assertInlineSafe("service worker", sw, "</script");
  const pwaFiles: Array<[string, Uint8Array]> = [...staticFiles, ["sw.js", Buffer.from(sw, "utf8")]];

  const base = `secure-zip-notes-v${version}`;
  const releaseZip = storeZip([
    [`${base}/index.html`, Buffer.from(html, "utf8")],
    [`${base}/LICENSE`, read("LICENSE")],
    [`${base}/README.txt`, read("public/README.txt")],
  ]);
  const pwaZip = storeZip(pwaFiles);

  rmSync(out, { recursive: true, force: true });
  const outputs: Array<[string, Uint8Array]> = [
    ...pwaFiles.map(([p, d]) => [`pwa/${p}`, d] as [string, Uint8Array]),
    [`${base}.zip`, releaseZip],
    [`${base}-pwa.zip`, pwaZip],
  ];
  for (const [p, d] of outputs) {
    mkdirSync(dirname(join(out, p)), { recursive: true });
    writeFileSync(join(out, p), d);
  }
  const sums = outputs
    .map(([p, d]) => [p, sha256Hex(d)] as const)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([p, h]) => `${h}  ${p}\n`)
    .join("");
  writeFileSync(join(out, "SHA256SUMS"), sums);
  const digest = sha256Hex(sums);
  writeFileSync(join(out, "RELEASE_DIGEST"), `${digest}\n`);
  writeFileSync(join(out, "APP_SCRIPT_SHA256"), `${sha256Hex(script)}\n`);

  console.log(sums.trimEnd());
  console.log(`\nApp script SHA-256 (shown in Settings > About): ${sha256Hex(script)}`);
  console.log(`Release digest (SHA-256 of SHA256SUMS): ${digest}`);
}

await main();
