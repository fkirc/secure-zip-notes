import { test } from "node:test";
import assert from "node:assert/strict";
import { DOS_EPOCH, METHOD_STORE, readZip, toDosDateTime, writeZip, ZipFormatError } from "../../src/zip/zip.ts";
import { crc32 } from "../../src/zip/crc32.ts";
import { utf8Encode } from "../../src/bytes.ts";

function plainEntry(name: string, content: string) {
  const data = utf8Encode(content);
  return { name, method: METHOD_STORE, flags: 0, ...DOS_EPOCH, crc: crc32(data), uncompressedSize: data.length, aes: null, data };
}

test("write then read round trip preserves names, data and order", () => {
  const entries = [plainEntry("a.txt", "A"), plainEntry("dir/b.txt", "Bee"), plainEntry("ünïcödé.txt", "✓"), plainEntry("empty", "")];
  const zip = writeZip(entries);
  const read = readZip(zip);
  assert.deepEqual(read.map((e) => e.name), entries.map((e) => e.name));
  read.forEach((e, i) => {
    assert.deepEqual(e.data, entries[i]?.data);
    assert.equal(e.crc, entries[i]?.crc);
    assert.equal(e.method, METHOD_STORE);
  });
});

test("writer output is deterministic", () => {
  const a = writeZip([plainEntry("x", "1"), plainEntry("y", "2")]);
  const b = writeZip([plainEntry("x", "1"), plainEntry("y", "2")]);
  assert.deepEqual(a, b);
});

test("empty archive is a 22-byte end record", () => {
  const zip = writeZip([]);
  assert.equal(zip.length, 22);
  assert.deepEqual(readZip(zip), []);
});

test("garbage is rejected", () => {
  assert.throws(() => readZip(utf8Encode("definitely not a zip file, just text")), ZipFormatError);
  assert.throws(() => readZip(new Uint8Array(5)), ZipFormatError);
});

test("truncated archive is rejected", () => {
  const zip = writeZip([plainEntry("a.txt", "hello world")]);
  assert.throws(() => readZip(zip.slice(10)), ZipFormatError);
});

test("DOS date/time encoding", () => {
  const { dosDate, dosTime } = toDosDateTime(new Date(2026, 10, 22, 18, 30, 15));
  assert.equal(dosDate, ((2026 - 1980) << 9) | (11 << 5) | 22);
  assert.equal(dosTime, (18 << 11) | (30 << 5) | 7);
  assert.deepEqual(DOS_EPOCH, { dosDate: 0x21, dosTime: 0 });
});
