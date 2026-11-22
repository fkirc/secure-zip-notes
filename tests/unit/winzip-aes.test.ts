import { test } from "node:test";
import assert from "node:assert/strict";
import {
  AES_256,
  counterBlock,
  decryptPayload,
  encryptPayload,
  MAC_LENGTH,
  passwordBytesV1,
  passwordCandidates,
  saltLength,
  keyLength,
} from "../../src/crypto/winzip-aes.ts";
import { toHex, utf8Decode, utf8Encode } from "../../src/bytes.ts";

const subtle = globalThis.crypto.subtle;

test("PBKDF2-HMAC-SHA1 matches RFC 6070 test vectors (via WebCrypto)", async () => {
  const vectors = [
    { p: "password", s: "salt", c: 1, dk: "0c60c80f961f0e71f3a9b524af6012062fe037a6" },
    { p: "password", s: "salt", c: 2, dk: "ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957" },
    { p: "password", s: "salt", c: 4096, dk: "4b007901b765489abead49d926f721d065a429c1" },
    {
      p: "passwordPASSWORDpassword",
      s: "saltSALTsaltSALTsaltSALTsaltSALTsalt",
      c: 4096,
      dk: "3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038",
    },
  ];
  for (const v of vectors) {
    const key = await subtle.importKey("raw", utf8Encode(v.p), "PBKDF2", false, ["deriveBits"]);
    const bits = await subtle.deriveBits(
      { name: "PBKDF2", hash: "SHA-1", salt: utf8Encode(v.s), iterations: v.c },
      key,
      (v.dk.length / 2) * 8,
    );
    assert.equal(toHex(new Uint8Array(bits)), v.dk);
  }
});

test("counter blocks are little-endian and start at 1", () => {
  assert.equal(toHex(counterBlock(0)), "01000000000000000000000000000000");
  assert.equal(toHex(counterBlock(254)), "ff000000000000000000000000000000");
  assert.equal(toHex(counterBlock(255)), "00010000000000000000000000000000");
  assert.equal(toHex(counterBlock(65535)), "00000100000000000000000000000000");
});

test("salt and key lengths per AES strength", () => {
  assert.deepEqual([saltLength(1), saltLength(2), saltLength(3)], [8, 12, 16]);
  assert.deepEqual([keyLength(1), keyLength(2), keyLength(3)], [16, 24, 32]);
});

test("encrypt then decrypt round trip, including multi-block and partial blocks", async () => {
  for (const len of [0, 1, 15, 16, 17, 255 * 16 + 5, 300 * 16]) {
    const plain = new Uint8Array(len);
    for (let i = 0; i < len; i++) plain[i] = (i * 31 + 7) & 0xff;
    const payload = await encryptPayload(plain, "testpassword");
    assert.equal(payload.length, 16 + 2 + len + MAC_LENGTH);
    const res = await decryptPayload(payload, "testpassword", AES_256);
    assert.ok(res.ok);
    if (res.ok) assert.deepEqual(res.plaintext, plain);
  }
});

test("ciphertext differs from plaintext and between encryptions (random salt)", async () => {
  const plain = utf8Encode("My secret note");
  const a = await encryptPayload(plain, "testpassword");
  const b = await encryptPayload(plain, "testpassword");
  assert.notDeepEqual(a, b);
  assert.ok(!toHex(a).includes(toHex(plain)));
});

test("deterministic with a fixed salt", async () => {
  const salt = new Uint8Array(16).fill(7);
  const a = await encryptPayload(utf8Encode("hello"), "pw123456", salt);
  const b = await encryptPayload(utf8Encode("hello"), "pw123456", salt);
  assert.deepEqual(a, b);
});

test("wrong password is reported as wrong-password", async () => {
  const payload = await encryptPayload(utf8Encode("secret"), "right password");
  const res = await decryptPayload(payload, "wrong password", AES_256);
  assert.deepEqual(res, { ok: false, reason: "wrong-password" });
});

test("tampered ciphertext fails authentication and returns no plaintext", async () => {
  const payload = await encryptPayload(utf8Encode("secret content"), "testpassword");
  const tampered = payload.slice();
  tampered[20] = (tampered[20] ?? 0) ^ 1;
  const res = await decryptPayload(tampered, "testpassword", AES_256);
  assert.deepEqual(res, { ok: false, reason: "corrupt" });
});

test("tampered MAC fails authentication", async () => {
  const payload = await encryptPayload(utf8Encode("secret content"), "testpassword");
  const tampered = payload.slice();
  tampered[tampered.length - 1] = (tampered[tampered.length - 1] ?? 0) ^ 0x80;
  assert.deepEqual(await decryptPayload(tampered, "testpassword", AES_256), { ok: false, reason: "corrupt" });
});

test("truncated payload is corrupt", async () => {
  assert.deepEqual(await decryptPayload(new Uint8Array(10), "testpassword", AES_256), {
    ok: false,
    reason: "corrupt",
  });
});

test("UTF-8 content and password round trip", async () => {
  const text = "Grüße, 日本語, emoji 🔐";
  const payload = await encryptPayload(utf8Encode(text), "pässwörd-🔑");
  const res = await decryptPayload(payload, "pässwörd-🔑", AES_256);
  assert.ok(res.ok);
  if (res.ok) assert.equal(utf8Decode(res.plaintext), text);
});

test("v1 password encoding (low byte per UTF-16 unit) is tried as fallback", async () => {
  assert.equal(passwordCandidates("ascii only").length, 1);
  assert.equal(passwordCandidates("pässwört").length, 2);
  assert.deepEqual([...passwordBytesV1("ä")], [0xe4]);
});
