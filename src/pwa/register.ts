/** PWA wiring: manifest and service worker, only when served over https. */

interface TrustedTypePolicyLike {
  createScriptURL(url: string): string;
}
interface TrustedTypesWindow {
  trustedTypes?: { createPolicy(name: string, rules: { createScriptURL(url: string): string }): TrustedTypePolicyLike };
}

const SW_URL = "./sw.js";

export function addManifestLinks(): void {
  const manifest = document.createElement("link");
  manifest.rel = "manifest";
  manifest.href = "./manifest.webmanifest";
  const touchIcon = document.createElement("link");
  touchIcon.rel = "apple-touch-icon";
  touchIcon.href = "./icons/apple-touch-icon.png";
  document.head.append(manifest, touchIcon);
}

/**
 * Registers the service worker. It never calls skipWaiting() and this version's
 * files never change, so the app never updates itself.
 */
export async function registerServiceWorker(): Promise<void> {
  const tt = (window as unknown as TrustedTypesWindow).trustedTypes;
  // The CSP enforces Trusted Types; this policy only ever allows the one service worker URL.
  const url = tt
    ? tt.createPolicy("szn-sw", {
        createScriptURL: (u: string) => {
          if (u !== SW_URL) throw new Error("Blocked script URL");
          return u;
        },
      }).createScriptURL(SW_URL)
    : SW_URL;
  await navigator.serviceWorker.register(url as string, { scope: "./", updateViaCache: "none" });
}
