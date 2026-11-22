import { test } from "node:test";
import assert from "node:assert/strict";
import { crc32 } from "../../src/zip/crc32.ts";
import { utf8Encode } from "../../src/bytes.ts";

test("crc32 of empty input is 0", () => {
  assert.equal(crc32(new Uint8Array(0)), 0);
});

test("crc32 check value '123456789'", () => {
  assert.equal(crc32(utf8Encode("123456789")), 0xcbf43926);
});

test("crc32 of 'The quick brown fox jumps over the lazy dog'", () => {
  assert.equal(crc32(utf8Encode("The quick brown fox jumps over the lazy dog")), 0x414fa339);
});
