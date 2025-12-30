const CACHE = "thynkah-v3";
const SHELL = [
    "/",
    "/browse",
    "/add",
    "/askui",
    "/css/style.css",
    "/manifest.json",
    "/icons/icon-192.png",
    "/icons/icon-512.png"
];

self.addEventListener("install", (event) => {
    self.skipWaiting();
    event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
});

self.addEventListener("activate", (event) => {
    event.waitUntil((async () => {
        const keys = await caches.keys();
        await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)));
        await self.clients.claim();
    })());
});

self.addEventListener("fetch", (event) => {
    const req = event.request;
    const url = new URL(req.url);

    // Non-GET: network only, graceful offline error
    if (req.method !== "GET") {
        event.respondWith(
            fetch(req).catch(() =>
                new Response(JSON.stringify({ error: "OFFLINE" }), {
                    status: 503,
                    headers: { "Content-Type": "application/json" }
                })
            )
        );
        return;
    }

    // ✅ HTML navigations: NETWORK FIRST (prevents stale /browse after saving)
    if (req.mode === "navigate") {
        event.respondWith(
            fetch(req)
                .then((res) => {
                    const copy = res.clone();
                    caches.open(CACHE).then((cache) => cache.put(req, copy));
                    return res;
                })
                .catch(() => caches.match(req).then((c) => c || caches.match("/browse") || caches.match("/")))
        );
        return;
    }

    // Static assets: cache-first with background update
    if (
        url.pathname.startsWith("/css/") ||
        url.pathname.startsWith("/icons/") ||
        url.pathname === "/manifest.json"
    ) {
        event.respondWith(
            caches.match(req).then((cached) => {
                const fetchPromise = fetch(req)
                    .then((res) => {
                        caches.open(CACHE).then((cache) => cache.put(req, res.clone()));
                        return res;
                    })
                    .catch(() => cached);
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
