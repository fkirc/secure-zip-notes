/**
 * Tiny static file server (node:http, no dependencies) for PWA end-to-end tests and
 * local previews. Serves a directory laid out like GitHub Pages:
 *
 *   node scripts/serve.ts [--port 4173] [--site <dir>]
 *
 * Without --site it serves dist/pwa at /secure-zip-notes/v<version>/.
 */
import { createServer } from "node:http";
import { existsSync, readFileSync, statSync } from "node:fs";
import { extname, join, normalize, sep } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("..", import.meta.url));
const BASE = "/secure-zip-notes/";

function arg(name: string): string | undefined {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

const port = Number(arg("--port") ?? 4173);
const site = arg("--site");
const version = (JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8")) as { version: string }).version;

const TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".webmanifest": "application/manifest+json",
  ".png": "image/png",
  ".zip": "application/zip",
  ".txt": "text/plain; charset=utf-8",
};

/** Maps a URL path to a file on disk, or null. */
function resolve(urlPath: string): string | null {
  if (!urlPath.startsWith(BASE)) return null;
  let rel = decodeURIComponent(urlPath.slice(BASE.length));
  let dir: string;
  if (site) {
    dir = site;
  } else {
    const prefix = `v${version}/`;
    if (rel === "" ) return null;
    if (!rel.startsWith(prefix)) return null;
    rel = rel.slice(prefix.length);
    dir = join(ROOT, "dist", "pwa");
  }
  const file = normalize(join(dir, rel));
  if (!file.startsWith(normalize(dir + sep)) && file !== normalize(dir)) return null;
  if (existsSync(file) && statSync(file).isDirectory()) return join(file, "index.html");
  return file;
}

createServer((req, res) => {
  const url = new URL(req.url ?? "/", "http://localhost");
  if (url.pathname === BASE.slice(0, -1)) {
    res.writeHead(301, { location: BASE }).end();
    return;
  }
  const file = resolve(url.pathname);
  if (!file || !existsSync(file)) {
    res.writeHead(404, { "content-type": "text/plain" }).end("Not found");
    return;
  }
  res.writeHead(200, {
    "content-type": TYPES[extname(file)] ?? "application/octet-stream",
    "cache-control": "no-cache",
  });
  res.end(readFileSync(file));
}).listen(port, "127.0.0.1", () => {
  console.log(`Serving on http://localhost:${port}${BASE}${site ? "" : `v${version}/`}`);
});
