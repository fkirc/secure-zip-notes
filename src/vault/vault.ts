/**
 * The vault: an AES-encrypted Zip file holding text notes.
 *
 * Entries stay encrypted in memory at all times. Plaintext only exists while a note
 * is being read or written. Unchanged entries are written back byte-for-byte.
 */
import { utf8Decode, utf8Encode, type Bytes } from "../bytes.ts";
import { AES_256, decryptPayload, encryptPayload, isAesStrength } from "../crypto/winzip-aes.ts";
import { crc32 } from "../zip/crc32.ts";
import { inflateRaw } from "../zip/inflate.ts";
import {
  FLAG_ENCRYPTED,
  METHOD_AES,
  METHOD_DEFLATE,
  METHOD_STORE,
  readZip,
  toDosDateTime,
  writeZip,
  ZipFormatError,
  type AesExtra,
  type ZipEntry,
} from "../zip/zip.ts";

/** Import errors with the exact messages of v1 (NotesImport.kt). */
export class ImportError extends Error {}

export const MSG_INVALID_ZIP = "Import failed. Probably this is not a valid Zip file.";
export const MSG_DIRECTORY = "Import failed. Zip files with pure directory entries are not supported.";
export const MSG_UNENCRYPTED = "Import failed. Zip files with non-encrypted entries are not supported.";
export const MSG_NOT_AES = "Unsupported encryption algorithm. This app only supports Zip files with AES encryption.";
export const MSG_EMPTY_NAME = "File names must not have zero lengths.";
export const MSG_NO_AES_RECORD = "Import failed. Could not find AES data record.";
export const MSG_EMPTY_EXPORT = "Notes empty, nothing to export";

function compressionError(method: number): string | null {
  switch (method) {
    case METHOD_STORE:
    case METHOD_DEFLATE:
      return null;
    case 12:
      return "Import failed: This app does not support BZIP2 compression.";
    case 9:
      return "Import failed: This app does not support DEFLATE64 compression.";
    case 14:
      return "Import failed: This app does not support LZMA compression.";
    case 98:
      return "Import failed: This app does not support PPMD compression.";
    default:
      return `Import failed: Unsupported compression method (${method}).`;
  }
}

export interface NoteEntry {
  name: string;
  aes: AesExtra;
  flags: number;
  dosTime: number;
  dosDate: number;
  crc: number;
  uncompressedSize: number;
  /** salt | verifier | ciphertext | mac */
  payload: Bytes;
}

/** Validates entry names like v1 (ZipUtil.kt). Returns an error message, or null if valid. */
export function entryNameError(name: string): string | null {
  if (name.length === 0) return "Empty file names are not allowed";
  if (name.startsWith("/") || name.startsWith("\\") || name.endsWith("/") || name.endsWith("\\")) {
    return `${name} is an invalid entry name`;
  }
  return null;
}

export type ReadResult =
  | { ok: true; content: string }
  | { ok: false; reason: "wrong-password" | "corrupt" };

export class Vault {
  private entries: NoteEntry[];

  private constructor(entries: NoteEntry[]) {
    this.entries = entries;
  }

  static empty(): Vault {
    return new Vault([]);
  }

  /** Opens a Zip file with the same validation rules as v1's import. */
  static open(bytes: Bytes): Vault {
    let raw: ZipEntry[];
    try {
      raw = readZip(bytes);
    } catch (e) {
      if (e instanceof ZipFormatError) throw new ImportError(MSG_INVALID_ZIP);
      throw e;
    }
    if (raw.length === 0) throw new ImportError(MSG_INVALID_ZIP);
    if (raw.some((e) => e.name.endsWith("/"))) throw new ImportError(MSG_DIRECTORY);
    if (raw.some((e) => (e.flags & FLAG_ENCRYPTED) === 0)) throw new ImportError(MSG_UNENCRYPTED);
    if (raw.some((e) => e.method !== METHOD_AES)) throw new ImportError(MSG_NOT_AES);
    if (raw.some((e) => e.name.length === 0)) throw new ImportError(MSG_EMPTY_NAME);
    for (const e of raw) {
      if (!e.aes || !isAesStrength(e.aes.strength)) throw new ImportError(MSG_NO_AES_RECORD);
      const err = compressionError(e.aes.actualMethod);
      if (err) throw new ImportError(err);
    }
    const names = new Set<string>();
    for (const e of raw) {
      if (names.has(e.name)) throw new ImportError(MSG_INVALID_ZIP);
      names.add(e.name);
    }
    return new Vault(
      raw.map((e) => ({
        name: e.name,
        aes: e.aes as AesExtra,
        flags: e.flags,
        dosTime: e.dosTime,
        dosDate: e.dosDate,
        crc: e.crc,
        uncompressedSize: e.uncompressedSize,
        payload: e.data,
      })),
    );
  }

  get size(): number {
    return this.entries.length;
  }

  /** Names in Zip order. */
  names(): string[] {
    return this.entries.map((e) => e.name);
  }

  /** Names as listed in the UI: newest first, like v1. */
  displayNames(): string[] {
    return this.names().reverse();
  }

  has(name: string): boolean {
    return this.entries.some((e) => e.name === name);
  }

  /** "Note N" that doesn't exist yet (same scheme as v1's generateUnusedFileName). */
  unusedName(): string {
    let up = 1;
    let name: string;
    do {
      name = `Note ${up++ + this.entries.length}`;
    } while (this.has(name));
    return name;
  }

  /** Error message for renaming `from` to `to`, or null if allowed. */
  renameError(from: string, to: string): string | null {
    if (from === to) return null;
    if (this.has(to)) return `${to} already exists`;
    return entryNameError(to);
  }

  private index(name: string): number {
    const i = this.entries.findIndex((e) => e.name === name);
    if (i < 0) throw new Error(`No such note: ${name}`);
    return i;
  }

  /** Decrypts a note. */
  async read(name: string, password: string): Promise<ReadResult> {
    const e = this.entries[this.index(name)] as NoteEntry;
    if (!isAesStrength(e.aes.strength)) return { ok: false, reason: "corrupt" };
    const res = await decryptPayload(e.payload, password, e.aes.strength);
    if (!res.ok) return res;
    let data = res.plaintext;
    if (e.aes.actualMethod === METHOD_DEFLATE) {
      try {
        data = await inflateRaw(data);
      } catch {
        return { ok: false, reason: "corrupt" };
      }
    }
    if (e.aes.vendorVersion === 1 && crc32(data) !== e.crc) return { ok: false, reason: "corrupt" };
    return { ok: true, content: utf8Decode(data) };
  }

  /** True if the password decrypts at least one entry. */
  async unlocks(password: string): Promise<boolean> {
    for (const name of this.displayNames()) {
      if ((await this.read(name, password)).ok) return true;
    }
    return false;
  }

  private async encrypt(name: string, content: string, password: string, now: Date): Promise<NoteEntry> {
    const plain = utf8Encode(content);
    const payload = await encryptPayload(plain, password);
    const { dosDate, dosTime } = toDosDateTime(now);
    return {
      name,
      // AE-2, AES-256, STORE: exactly what v1 writes. AE-2 stores CRC 0.
      aes: { vendorVersion: 2, strength: AES_256, actualMethod: METHOD_STORE },
      flags: FLAG_ENCRYPTED,
      dosTime,
      dosDate,
      crc: 0,
      uncompressedSize: plain.length,
      payload,
    };
  }

  /** Adds a new note at the end (shown first in the list). */
  async add(name: string, content: string, password: string, now: Date = new Date()): Promise<void> {
    const err = this.has(name) ? `${name} already exists` : entryNameError(name);
    if (err) throw new Error(err);
    this.entries.push(await this.encrypt(name, content, password, now));
  }

  /** Re-encrypts a note with new content (and optionally a new name), keeping its position. */
  async update(name: string, newName: string, content: string, password: string, now: Date = new Date()): Promise<void> {
    const err = this.renameError(name, newName);
    if (err) throw new Error(err);
    const i = this.index(name);
    this.entries[i] = await this.encrypt(newName, content, password, now);
  }

  /** Renames without re-encrypting (the name is not part of the encrypted data). */
  rename(from: string, to: string): void {
    const err = this.renameError(from, to);
    if (err) throw new Error(err);
    const i = this.index(from);
    this.entries[i] = { ...(this.entries[i] as NoteEntry), name: to };
  }

  delete(name: string): void {
    this.entries.splice(this.index(name), 1);
  }

  toZip(): Bytes {
    return writeZip(
      this.entries.map((e) => ({
        name: e.name,
        method: METHOD_AES,
        flags: e.flags | FLAG_ENCRYPTED,
        dosTime: e.dosTime,
        dosDate: e.dosDate,
        crc: e.crc,
        uncompressedSize: e.uncompressedSize,
        aes: e.aes,
        data: e.payload,
      })),
    );
  }
}
