self.addEventListener("install", e => {
    e.waitUntil(
        caches.open("thynkah-v1").then(cache =>
            cache.addAll([
                "/",
                "/add",
                "/browse",
                "/askui",
                "/css/style.css"
            ])
        )
    );
});

self.addEventListener("fetch", e => {
    e.respondWith(
        caches.match(e.request).then(r => r || fetch(e.request))
    );
});

const CACHE = "thynkah-v1";
const ASSETS = ["/", "/add", "/browse", "/askui", "/css/style.css"];

self.addEventListener("install", (e) => {
    e.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)));
});

self.addEventListener("activate", (e) => {
    e.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
        )
    );
});

self.addEventListener("fetch", (e) => {
    e.respondWith(caches.match(e.request).then((r) => r || fetch(e.request)));
});
