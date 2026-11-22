/** Entry point. Bundled into a single inline classic script in index.html. */
import { App } from "./app.ts";
import { toBase64, toHex, sha256, utf8Encode } from "./bytes.ts";
import { detectMode } from "./platform.ts";
import { addManifestLinks, registerServiceWorker } from "./pwa/register.ts";

// Captured synchronously: the exact script text whose hash is pinned in the CSP.
const scriptText = document.currentScript?.textContent ?? "";

async function main(): Promise<void> {
  const root = document.getElementById("app");
  if (!root) return;
  // Refuse to run inside a frame (GitHub Pages can't send frame-ancestors).
  if (window.top !== window.self) {
    root.textContent = "Secure Zip Notes refuses to run inside a frame.";
    return;
  }
  const mode = detectMode();
  const digest = await sha256(utf8Encode(scriptText));
  const app = new App({ root, mode, scriptHash: `${toHex(digest)} (CSP sha256-${toBase64(digest)})` });
  if (mode === "pwa") {
    addManifestLinks();
    registerServiceWorker().catch((e: unknown) => console.error("Service worker registration failed", e));
  }
  await app.start();
}

void main();
