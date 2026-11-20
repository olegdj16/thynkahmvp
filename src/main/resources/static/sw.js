// Thynkah Service Worker
// Versioned + cleanup + navigation fallback

const CACHE_VERSION = "thynkah-v2026-02-14-01";
const STATIC_CACHE = `${CACHE_VERSION}-static`;
const PAGE_CACHE = `${CACHE_VERSION}-pages`;

// Precache critical shell files
const PRECACHE = [
    "/offline.html",
    "/css/style.css",
    "/manifest.json",
    "/icons/icon-192.png",
    "/icons/icon-512.png"
];

// INSTALL
self.addEventListener("install", event => {
    event.waitUntil(
        caches.open(STATIC_CACHE)
            .then(cache => cache.addAll(PRECACHE))
            .then(() => self.skipWaiting())
    );
});

// ACTIVATE (delete old caches)
self.addEventListener("activate", event => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(
                keys.map(key => {
                    if (key.startsWith("thynkah-") &&
                        key !== STATIC_CACHE &&
                        key !== PAGE_CACHE) {
                        return caches.delete(key);
                    }
                })
            )
        ).then(() => self.clients.claim())
    );
});

// FETCH
self.addEventListener("fetch", event => {
    const request = event.request;
    const url = new URL(request.url);

    // Only handle same-origin
    if (url.origin !== self.location.origin) return;

    // API calls → network only
    if (url.pathname.startsWith("/api/")) {
        event.respondWith(fetch(request));
        return;
    }

    // Navigation (HTML pages)
    if (request.mode === "navigate") {
        event.respondWith(
            fetch(request)
                .then(response => {
                    const clone = response.clone();
                    caches.open(PAGE_CACHE)
                        .then(cache => cache.put(request, clone));
                    return response;
                })
                .catch(() =>
                    caches.match(request)
                        .then(resp => resp || caches.match("/offline.html"))
                )
        );
        return;
    }

    // Static assets (CSS, JS)
    event.respondWith(
        caches.match(request)
            .then(cached => {
                if (cached) return cached;
                return fetch(request).then(response => {
                    if (
                        request.method === "GET" &&
                        (
                            url.pathname.startsWith("/css/") ||
                            url.pathname.startsWith("/js/") ||
                            url.pathname.startsWith("/icons/") ||
                            url.pathname.endsWith(".png") ||
                            url.pathname.endsWith(".jpg") ||
                            url.pathname.endsWith(".svg")
                        )
                    ) {
                        caches.open(STATIC_CACHE)
                            .then(cache => cache.put(request, response.clone()));
                    }
                    return response;
                });
            })
    );
});
