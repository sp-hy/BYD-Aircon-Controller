const CACHE = 'byd-pwa-v7';
const SHELL = ['./index.html', './manifest.json'];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);

  // Cache-first for CartoDB dark map tiles — makes previously viewed maps work offline
  if (url.hostname.endsWith('.basemaps.cartocdn.com') || url.hostname === 'basemaps.cartocdn.com') {
    e.respondWith(
      caches.match(e.request).then(hit => hit || fetch(e.request).then(res => {
        if (res.ok) caches.open(CACHE).then(c => c.put(e.request, res.clone()));
        return res;
      }))
    );
    return;
  }

  // Network-first for CDN libs (sql.js, Chart.js, Leaflet) — cache on success
  if (['cdnjs.cloudflare.com', 'cdn.jsdelivr.net', 'unpkg.com'].some(h => url.hostname === h)) {
    e.respondWith(
      fetch(e.request)
        .then(res => {
          if (res.ok) caches.open(CACHE).then(c => c.put(e.request, res.clone()));
          return res;
        })
        .catch(() => caches.match(e.request))
    );
    return;
  }

  // Cache-first for app shell
  e.respondWith(caches.match(e.request).then(hit => hit || fetch(e.request)));
});
