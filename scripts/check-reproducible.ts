/**
 * Builds twice with different time zones and locales and checks that every output
 * byte is identical. CI additionally compares builds across Linux, macOS and Windows.
 *
 *   node scripts/check-reproducible.ts [--expect path/to/SHA256SUMS]
 */
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("..", import.meta.url));

function buildInto(dir: string, env: Record<string, string>): string {
  const res = spawnSync(process.execPath, ["scripts/build.ts", "--out", dir], {
    cwd: ROOT,
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
  if (res.status !== 0) throw new Error(`build failed:\n${res.stdout}\n${res.stderr}`);
  return readFileSync(join(ROOT, dir, "SHA256SUMS"), "utf8");
}

const a = buildInto(".tmp/repro-a", { TZ: "UTC", LC_ALL: "C", LANG: "C" });
const b = buildInto(".tmp/repro-b", { TZ: "Pacific/Kiritimati", LC_ALL: "de_AT.UTF-8", LANG: "de_AT.UTF-8" });

let ok = a === b;
for (const line of a.trim().split("\n")) {
  const path = line.slice(66);
  const x = readFileSync(join(ROOT, ".tmp/repro-a", path));
  const y = readFileSync(join(ROOT, ".tmp/repro-b", path));
  if (!x.equals(y)) {
    console.error(`DIFFERENT: ${path}`);
    ok = false;
  }
}

const i = process.argv.indexOf("--expect");
const expectedPath = i >= 0 ? process.argv[i + 1] : undefined;
if (expectedPath) {
  const expected = readFileSync(expectedPath, "utf8");
  if (expected !== a) {
    console.error(`Build does not match ${expectedPath}`);
    ok = false;
  }
}

if (!ok) {
  console.error("Build is NOT reproducible.");
  process.exit(1);
}
console.log(a.trimEnd());
console.log("\nReproducible: two builds with different TZ and locale are byte-identical.");
