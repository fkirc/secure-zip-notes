import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import type { Bytes } from "../../src/bytes.ts";

export const FIXTURES_DIR = fileURLToPath(new URL("../fixtures/", import.meta.url));
export const TESTPASSWORD = "testpassword";

export function fixture(path: string): Bytes {
  return new Uint8Array(readFileSync(FIXTURES_DIR + path));
}
