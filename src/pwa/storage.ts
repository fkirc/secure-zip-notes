/**
 * PWA vault persistence in IndexedDB. Only the encrypted zip bytes are stored,
 * exactly what would be in the exported file. Never passwords or plaintext.
 */
import type { Bytes } from "../bytes.ts";

const DB_NAME = "secure-zip-notes";
const STORE = "vault";
const KEY = "current";

export interface StoredVault {
  bytes: Bytes;
  fileName: string;
  changedAt: number;
  exportedAt: number;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function run<T>(mode: IDBTransactionMode, op: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb();
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const req = op(tx.objectStore(STORE));
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

export async function loadVault(): Promise<StoredVault | null> {
  const v = (await run("readonly", (s) => s.get(KEY))) as StoredVault | undefined;
  if (!v) return null;
  return { ...v, bytes: new Uint8Array(v.bytes) };
}

export async function storeVault(v: StoredVault): Promise<void> {
  await run("readwrite", (s) => s.put(v, KEY));
}

export async function clearVault(): Promise<void> {
  await run("readwrite", (s) => s.delete(KEY));
}

/** Asks the browser not to evict our storage. Best effort. */
export async function requestPersistence(): Promise<void> {
  try {
    await navigator.storage?.persist?.();
  } catch {
    // ignore
  }
}
