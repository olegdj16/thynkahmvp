const CACHE = "thynkah-v2";
const SHELL = [
    "/",          // if you have a home route
    "/browse",
    "/add",
    "/askui",
    "/css/style.css",
    "/manifest.json",
    "/icons/icon-192.png",
    "/icons/icon-512.png"
];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE).then((cache) => cache.addAll(SHELL))
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
        )
    );
});

self.addEventListener("fetch", (event) => {
    const req = event.request;

    // Never cache/handle non-GET beyond graceful failure
    if (req.method !== "GET") {
        event.respondWith(
            fetch(req).catch(() =>
                new Response(
                    JSON.stringify({ error: "OFFLINE" }),
                    { status: 503, headers: { "Content-Type": "application/json" } }
                )
            )
        );
        return;
    }

    const url = new URL(req.url);

    // HTML navigation: cache-first, fallback to cached Browse
    if (req.mode === "navigate") {
        event.respondWith(
            caches.match(req).then((cached) => {
                if (cached) return cached;
                return fetch(req)
                    .then((res) => {
                        const copy = res.clone();
                        caches.open(CACHE).then((cache) => cache.put(req, copy));
                        return res;
                    })
                    .catch(() => caches.match("/browse") || caches.match("/"));
            })
        );
        return;
    }

    // Static assets: cache-first, update cache in background
    if (
        url.pathname.startsWith("/css/") ||
        url.pathname.startsWith("/icons/") ||
        url.pathname === "/manifest.json"
    ) {
        event.respondWith(
            caches.match(req).then((cached) => {
                const fetchPromise = fetch(req).then((res) => {
                    caches.open(CACHE).then((cache) => cache.put(req, res.clone()));
                    return res;
                }).catch(() => cached);
                return cached || fetchPromise;
            })
        );
        return;
    }

    // Default GET: network-first with cache fallback
    event.respondWith(
        fetch(req)
            .then((res) => {
                caches.open(CACHE).then((cache) => cache.put(req, res.clone()));
                return res;
            })
            .catch(() => caches.match(req))
    );
});
