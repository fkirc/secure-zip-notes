/**
 * WinZip AES encryption (AE-1 / AE-2), as written by v1 (Zip4j) and 7-Zip.
 *
 * Per entry: PBKDF2-HMAC-SHA1 with 1000 iterations derives an AES key, an HMAC key
 * and a 2-byte password verifier from the password and a random salt. The data is
 * encrypted with AES-CTR using a little-endian counter that starts at 1, and
 * authenticated with HMAC-SHA1 over the ciphertext, truncated to 10 bytes.
 *
 * Entry payload layout: salt | verifier (2) | ciphertext | authentication code (10).
 *
 * Only WebCrypto primitives are used; there is no third-party crypto code.
 */
import { bytesEqual, concatBytes, utf8Encode, type Bytes } from "../bytes.ts";

const subtle = globalThis.crypto.subtle;

export const PBKDF2_ITERATIONS = 1000;
export const VERIFIER_LENGTH = 2;
export const MAC_LENGTH = 10;
const BLOCK = 16;

/** AES strength byte from the 0x9901 extra field. 3 = AES-256 (the only one we write). */
export type AesStrength = 1 | 2 | 3;
export const AES_256: AesStrength = 3;

export function saltLength(strength: AesStrength): number {
  return 4 + 4 * strength; // 8, 12, 16
}

export function keyLength(strength: AesStrength): number {
  return 8 + 8 * strength; // 16, 24, 32
}

export function isAesStrength(n: number): n is AesStrength {
  return n === 1 || n === 2 || n === 3;
}

/** Standard password encoding (7-Zip, WinZip): UTF-8. */
export function passwordBytesUtf8(password: string): Bytes {
  return utf8Encode(password);
}

/**
 * v1 (Zip4j) encoding: the low byte of every UTF-16 code unit. Identical to UTF-8
 * for ASCII passwords; only differs for non-ASCII characters.
 */
export function passwordBytesV1(password: string): Bytes {
  const out = new Uint8Array(password.length);
  for (let i = 0; i < password.length; i++) out[i] = password.charCodeAt(i) & 0xff;
  return out;
}

/** Encodings to try when decrypting: UTF-8 first, then v1's legacy encoding if different. */
export function passwordCandidates(password: string): Bytes[] {
  const utf8 = passwordBytesUtf8(password);
  const v1 = passwordBytesV1(password);
  return bytesEqual(utf8, v1) ? [utf8] : [utf8, v1];
}

interface DerivedKeys {
  aesKey: CryptoKey;
  hmacKey: CryptoKey;
  verifier: Bytes;
}

async function deriveKeys(passwordBytes: Bytes, salt: Bytes, strength: AesStrength): Promise<DerivedKeys> {
  const keyLen = keyLength(strength);
  const base = await subtle.importKey("raw", passwordBytes, "PBKDF2", false, ["deriveBits"]);
  const bits = new Uint8Array(
    await subtle.deriveBits(
      { name: "PBKDF2", hash: "SHA-1", salt, iterations: PBKDF2_ITERATIONS },
      base,
      (2 * keyLen + VERIFIER_LENGTH) * 8,
    ),
  );
  const aesKey = await subtle.importKey("raw", bits.slice(0, keyLen), { name: "AES-CTR" }, false, ["encrypt"]);
  const hmacKey = await subtle.importKey(
    "raw",
    bits.slice(keyLen, 2 * keyLen),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  return { aesKey, hmacKey, verifier: bits.slice(2 * keyLen) };
}

/** Little-endian 128-bit counter block for block index i (counter value i + 1). */
export function counterBlock(blockIndex: number): Bytes {
  const block = new Uint8Array(BLOCK);
  let n = blockIndex + 1;
  for (let b = 0; b < BLOCK && n > 0; b++) {
    block[b] = n % 256;
    n = Math.floor(n / 256);
  }
  return block;
}

/**
 * AES-CTR with WinZip's little-endian counter. WebCrypto increments counters
 * big-endian, so each 16-byte block is processed with its own counter block.
 * Encryption and decryption are the same operation.
 */
export async function aesCtrLittleEndian(key: CryptoKey, data: Bytes): Promise<Bytes> {
  const out = new Uint8Array(data.length);
  const blocks = Math.ceil(data.length / BLOCK);
  const BATCH = 2048;
  for (let start = 0; start < blocks; start += BATCH) {
    const end = Math.min(blocks, start + BATCH);
    const pending: Promise<void>[] = [];
    for (let i = start; i < end; i++) {
      const chunk = data.subarray(i * BLOCK, Math.min(data.length, (i + 1) * BLOCK));
      pending.push(
        subtle
          .encrypt({ name: "AES-CTR", counter: counterBlock(i), length: 128 }, key, chunk)
          .then((ct) => out.set(new Uint8Array(ct), i * BLOCK)),
      );
    }
    await Promise.all(pending);
  }
  return out;
}

async function mac(hmacKey: CryptoKey, ciphertext: Bytes): Promise<Bytes> {
  const full = new Uint8Array(await subtle.sign("HMAC", hmacKey, ciphertext));
  return full.slice(0, MAC_LENGTH);
}

/** Encrypts plaintext into a WinZip AES-256 payload with UTF-8 password encoding. */
export async function encryptPayload(plaintext: Bytes, password: string, salt?: Bytes): Promise<Bytes> {
  const s = salt ?? globalThis.crypto.getRandomValues(new Uint8Array(saltLength(AES_256)));
  if (s.length !== saltLength(AES_256)) throw new Error("Invalid salt length");
  const keys = await deriveKeys(passwordBytesUtf8(password), s, AES_256);
  const ciphertext = await aesCtrLittleEndian(keys.aesKey, plaintext);
  return concatBytes([s, keys.verifier, ciphertext, await mac(keys.hmacKey, ciphertext)]);
}

export type DecryptResult =
  | { ok: true; plaintext: Bytes }
  | { ok: false; reason: "wrong-password" | "corrupt" };

/** Decrypts a WinZip AES payload. Never returns unauthenticated plaintext. */
export async function decryptPayload(payload: Bytes, password: string, strength: AesStrength): Promise<DecryptResult> {
  const sLen = saltLength(strength);
  if (payload.length < sLen + VERIFIER_LENGTH + MAC_LENGTH) return { ok: false, reason: "corrupt" };
  const salt = payload.slice(0, sLen);
  const verifier = payload.subarray(sLen, sLen + VERIFIER_LENGTH);
  const ciphertext = payload.slice(sLen + VERIFIER_LENGTH, payload.length - MAC_LENGTH);
  const storedMac = payload.subarray(payload.length - MAC_LENGTH);
  let verifierMatched = false;
  for (const pw of passwordCandidates(password)) {
    if (pw.length === 0) continue;
    const keys = await deriveKeys(pw, salt, strength);
    if (!bytesEqual(keys.verifier, verifier)) continue;
    verifierMatched = true;
    if (!bytesEqual(await mac(keys.hmacKey, ciphertext), storedMac)) continue;
    return { ok: true, plaintext: await aesCtrLittleEndian(keys.aesKey, ciphertext) };
  }
  return { ok: false, reason: verifierMatched ? "corrupt" : "wrong-password" };
}
