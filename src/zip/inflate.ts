/** Raw DEFLATE decompression via the browser's built-in DecompressionStream. */
import type { Bytes } from "../bytes.ts";

export async function inflateRaw(data: Bytes): Promise<Bytes> {
  const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}
