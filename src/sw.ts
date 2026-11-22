/**
 * Service worker for the PWA. Precaches this version's fixed file list and serves
 * only from that cache. It never fetches anything else, never calls skipWaiting()
 * or clients.claim(), and never deletes other versions' caches.
 */
declare const self: ServiceWorkerGlobalScope;
declare const __PRECACHE__: string[];
declare const __CACHE_NAME__: string;

const CACHE = __CACHE_NAME__;
const FILES = __PRECACHE__;

function scoped(path: string): string {
  return new URL(path, self.registration.scope).href;
}

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(FILES.map(scoped))));
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  event.respondWith(
    (async () => {
      if (request.method !== "GET") return Response.error();
      const cache = await caches.open(CACHE);
      const hit = await cache.match(request, { ignoreSearch: true });
      if (hit) return hit;
      if (request.mode === "navigate") {
        const index = await cache.match(scoped("index.html"));
        if (index) return index;
      }
      return Response.error();
    })(),
  );
});
