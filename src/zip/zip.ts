/**
 * Minimal Zip reader and writer (PKWARE APPNOTE), scoped to what Secure Zip Notes needs:
 * reading entries via the central directory and writing STORE entries with fixed sizes
 * (no data descriptors, no Zip64).
 */
import { concatBytes, utf8Decode, utf8Encode, type Bytes } from "../bytes.ts";

const SIG_LOCAL = 0x04034b50;
const SIG_CENTRAL = 0x02014b50;
const SIG_EOCD = 0x06054b50;
const SIG_ZIP64_EOCD_LOCATOR = 0x07064b50;

export const METHOD_STORE = 0;
export const METHOD_DEFLATE = 8;
export const METHOD_AES = 99;
export const EXTRA_AES = 0x9901;

export const FLAG_ENCRYPTED = 0x0001;
export const FLAG_DATA_DESCRIPTOR = 0x0008;
export const FLAG_UTF8 = 0x0800;

export class ZipFormatError extends Error {}

export interface AesExtra {
  vendorVersion: number; // 1 = AE-1 (CRC checked), 2 = AE-2 (CRC is 0)
  strength: number; // 1, 2, 3 = AES-128, -192, -256
  actualMethod: number; // compression method applied before encryption
}

export interface ZipEntry {
  name: string;
  flags: number;
  method: number;
  dosTime: number;
  dosDate: number;
  crc: number;
  compressedSize: number;
  uncompressedSize: number;
  aes: AesExtra | null;
  /** Raw (possibly encrypted/compressed) entry data from the local file. */
  data: Bytes;
}

function view(bytes: Uint8Array): DataView {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

export function parseAesExtra(extra: Uint8Array): AesExtra | null {
  const dv = view(extra);
  let p = 0;
  while (p + 4 <= extra.length) {
    const id = dv.getUint16(p, true);
    const size = dv.getUint16(p + 2, true);
    if (p + 4 + size > extra.length) break;
    if (id === EXTRA_AES && size >= 7) {
      return {
        vendorVersion: dv.getUint16(p + 4, true),
        strength: dv.getUint8(p + 8),
        actualMethod: dv.getUint16(p + 9, true),
      };
    }
    p += 4 + size;
  }
  return null;
}

export function encodeAesExtra(aes: AesExtra): Bytes {
  const out = new Uint8Array(11);
  const dv = view(out);
  dv.setUint16(0, EXTRA_AES, true);
  dv.setUint16(2, 7, true);
  dv.setUint16(4, aes.vendorVersion, true);
  out[6] = 0x41; // 'A'
  out[7] = 0x45; // 'E'
  out[8] = aes.strength;
  dv.setUint16(9, aes.actualMethod, true);
  return out;
}

function findEocd(bytes: Uint8Array): number {
  const dv = view(bytes);
  const min = Math.max(0, bytes.length - 22 - 0xffff);
  for (let p = bytes.length - 22; p >= min; p--) {
    if (dv.getUint32(p, true) === SIG_EOCD && p + 22 + dv.getUint16(p + 20, true) === bytes.length) return p;
  }
  // Tolerate trailing garbage: accept any signature match.
  for (let p = bytes.length - 22; p >= min; p--) {
    if (dv.getUint32(p, true) === SIG_EOCD) return p;
  }
  throw new ZipFormatError("End of central directory not found");
}

/** Parses a Zip archive. Throws ZipFormatError for anything that isn't a readable Zip. */
export function readZip(bytes: Bytes): ZipEntry[] {
  if (bytes.length < 22) throw new ZipFormatError("File too small");
  const dv = view(bytes);
  const eocd = findEocd(bytes);
  if (eocd >= 20 && dv.getUint32(eocd - 20, true) === SIG_ZIP64_EOCD_LOCATOR) {
    throw new ZipFormatError("Zip64 is not supported");
  }
  const count = dv.getUint16(eocd + 10, true);
  const cdSize = dv.getUint32(eocd + 12, true);
  const cdOffset = dv.getUint32(eocd + 16, true);
  if (count === 0xffff || cdOffset === 0xffffffff) throw new ZipFormatError("Zip64 is not supported");
  if (cdOffset + cdSize > eocd) throw new ZipFormatError("Corrupt central directory");

  const entries: ZipEntry[] = [];
  let p = cdOffset;
  for (let i = 0; i < count; i++) {
    if (p + 46 > bytes.length || dv.getUint32(p, true) !== SIG_CENTRAL) {
      throw new ZipFormatError("Corrupt central directory entry");
    }
    const flags = dv.getUint16(p + 8, true);
    const method = dv.getUint16(p + 10, true);
    const dosTime = dv.getUint16(p + 12, true);
    const dosDate = dv.getUint16(p + 14, true);
    const crc = dv.getUint32(p + 16, true);
    const compressedSize = dv.getUint32(p + 20, true);
    const uncompressedSize = dv.getUint32(p + 24, true);
    const nameLen = dv.getUint16(p + 28, true);
    const extraLen = dv.getUint16(p + 30, true);
    const commentLen = dv.getUint16(p + 32, true);
    const localOffset = dv.getUint32(p + 42, true);
    if (compressedSize === 0xffffffff || uncompressedSize === 0xffffffff || localOffset === 0xffffffff) {
      throw new ZipFormatError("Zip64 is not supported");
    }
    const nameBytes = bytes.subarray(p + 46, p + 46 + nameLen);
    const extra = bytes.subarray(p + 46 + nameLen, p + 46 + nameLen + extraLen);
    p += 46 + nameLen + extraLen + commentLen;

    if (localOffset + 30 > bytes.length || dv.getUint32(localOffset, true) !== SIG_LOCAL) {
      throw new ZipFormatError("Corrupt local file header");
    }
    const localNameLen = dv.getUint16(localOffset + 26, true);
    const localExtraLen = dv.getUint16(localOffset + 28, true);
    const dataStart = localOffset + 30 + localNameLen + localExtraLen;
    if (dataStart + compressedSize > bytes.length) throw new ZipFormatError("Truncated entry data");
    const localExtra = bytes.subarray(localOffset + 30 + localNameLen, dataStart);

    entries.push({
      name: utf8Decode(nameBytes),
      flags,
      method,
      dosTime,
      dosDate,
      crc,
      compressedSize,
      uncompressedSize,
      aes: parseAesExtra(extra) ?? parseAesExtra(localExtra),
      data: bytes.slice(dataStart, dataStart + compressedSize),
    });
  }
  return entries;
}

export interface WriteEntry {
  name: string;
  method: number;
  flags: number;
  dosTime: number;
  dosDate: number;
  crc: number;
  uncompressedSize: number;
  aes: AesExtra | null;
  data: Uint8Array;
}

/** Serializes entries into a Zip archive. Output depends only on the input (deterministic). */
export function writeZip(entries: readonly WriteEntry[]): Bytes {
  const locals: Uint8Array[] = [];
  const centrals: Uint8Array[] = [];
  let offset = 0;
  for (const e of entries) {
    const nameBytes = utf8Encode(e.name);
    const extra = e.aes ? encodeAesExtra(e.aes) : new Uint8Array(0);
    // Sizes are always known, so never use a data descriptor; always mark names as UTF-8.
    const flags = (e.flags & ~FLAG_DATA_DESCRIPTOR) | FLAG_UTF8;
    const versionNeeded = e.aes ? 51 : e.method === METHOD_DEFLATE ? 20 : 10;

    const local = new Uint8Array(30 + nameBytes.length + extra.length);
    const lv = view(local);
    lv.setUint32(0, SIG_LOCAL, true);
    lv.setUint16(4, versionNeeded, true);
    lv.setUint16(6, flags, true);
    lv.setUint16(8, e.method, true);
    lv.setUint16(10, e.dosTime, true);
    lv.setUint16(12, e.dosDate, true);
    lv.setUint32(14, e.crc, true);
    lv.setUint32(18, e.data.length, true);
    lv.setUint32(22, e.uncompressedSize, true);
    lv.setUint16(26, nameBytes.length, true);
    lv.setUint16(28, extra.length, true);
    local.set(nameBytes, 30);
    local.set(extra, 30 + nameBytes.length);

    const central = new Uint8Array(46 + nameBytes.length + extra.length);
    const cv = view(central);
    cv.setUint32(0, SIG_CENTRAL, true);
    cv.setUint16(4, 51, true); // version made by: 5.1, MS-DOS host (fixed for determinism)
    cv.setUint16(6, versionNeeded, true);
    cv.setUint16(8, flags, true);
    cv.setUint16(10, e.method, true);
    cv.setUint16(12, e.dosTime, true);
    cv.setUint16(14, e.dosDate, true);
    cv.setUint32(16, e.crc, true);
    cv.setUint32(20, e.data.length, true);
    cv.setUint32(24, e.uncompressedSize, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint16(30, extra.length, true);
    // comment length, disk number, internal and external attributes stay 0
    cv.setUint32(42, offset, true);
    central.set(nameBytes, 46);
    central.set(extra, 46 + nameBytes.length);

    locals.push(local, e.data);
    centrals.push(central);
    offset += local.length + e.data.length;
  }
  const cd = concatBytes(centrals);
  const eocd = new Uint8Array(22);
  const ev = view(eocd);
  ev.setUint32(0, SIG_EOCD, true);
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, cd.length, true);
  ev.setUint32(16, offset, true);
  return concatBytes([...locals, cd, eocd]);
}

/** DOS date/time for a JS Date (local time, as Zip tools expect). */
export function toDosDateTime(date: Date): { dosDate: number; dosTime: number } {
  const year = Math.min(Math.max(date.getFullYear(), 1980), 2107);
  return {
    dosDate: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate(),
    dosTime: (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2),
  };
}

/** 1980-01-01 00:00:00, the fixed timestamp used for reproducible release archives. */
export const DOS_EPOCH = { dosDate: (1 << 5) | 1, dosTime: 0 } as const;
