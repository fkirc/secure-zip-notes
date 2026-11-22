/**
 * One-off generator for the app icons in public/icons (a padlock on a blue tile).
 * The PNGs are committed; the build never runs this. Run: node scripts/gen-icons.ts
 */
import { writeFileSync, mkdirSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { crc32 } from "../src/zip/crc32.ts";

function chunk(type: string, data: Uint8Array): Buffer {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

function png(size: number, pixel: (x: number, y: number) => [number, number, number, number]): Buffer {
  const raw = Buffer.alloc((size * 4 + 1) * size);
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0;
    for (let x = 0; x < size; x++) {
      const [r, g, b, a] = pixel(x, y);
      const o = y * (size * 4 + 1) + 1 + x * 4;
      raw[o] = r; raw[o + 1] = g; raw[o + 2] = b; raw[o + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", new Uint8Array(0)),
  ]);
}

/** Shape in unit coordinates (0..1): 0 = background, 1 = lock (white), 2 = keyhole. */
function shape(u: number, v: number, full: boolean): number {
  if (!full) {
    // rounded tile for "any" icons
    const r = 0.18, cx = Math.min(Math.max(u, r), 1 - r), cy = Math.min(Math.max(v, r), 1 - r);
    if ((u - cx) ** 2 + (v - cy) ** 2 > r * r) return -1;
  }
  const inBody = u >= 0.27 && u <= 0.73 && v >= 0.46 && v <= 0.78;
  const dx = u - 0.5, dy = v - 0.46, d = Math.sqrt(dx * dx + dy * dy);
  const inShackle = v <= 0.46 && d <= 0.17 && d >= 0.1;
  const inLegs = v > 0.4 && v <= 0.47 && ((u >= 0.33 && u <= 0.4) || (u >= 0.6 && u <= 0.67));
  const kd = Math.sqrt((u - 0.5) ** 2 + (v - 0.58) ** 2);
  const inKey = kd <= 0.045 || (Math.abs(u - 0.5) <= 0.02 && v >= 0.58 && v <= 0.69);
  if (inBody && inKey) return 2;
  if (inBody || inShackle || inLegs) return 1;
  return 0;
}

function icon(size: number, full: boolean): Buffer {
  const S = 4;
  const bg = [0x1f, 0x5f, 0x8b], fg = [0xff, 0xff, 0xff];
  return png(size, (x, y) => {
    let r = 0, g = 0, b = 0, a = 0;
    for (let sy = 0; sy < S; sy++) for (let sx = 0; sx < S; sx++) {
      const k = shape((x + (sx + 0.5) / S) / size, (y + (sy + 0.5) / S) / size, full);
      if (k < 0) continue;
      const c = k === 1 ? fg : bg;
      r += c[0] as number; g += c[1] as number; b += c[2] as number; a += 255;
    }
    const n = S * S, cov = a / 255;
    return cov === 0 ? [0, 0, 0, 0] : [Math.round(r / cov), Math.round(g / cov), Math.round(b / cov), Math.round(a / n)];
  });
}

mkdirSync("public/icons", { recursive: true });
writeFileSync("public/icons/icon-192.png", icon(192, false));
writeFileSync("public/icons/icon-512.png", icon(512, true));
writeFileSync("public/icons/apple-touch-icon.png", icon(180, true));
console.log("icons written");
