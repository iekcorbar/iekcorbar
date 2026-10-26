// Service worker — required by Chrome/Android's installability criteria for
// "Add to Home Screen" (a registered fetch handler is part of that check),
// and handles Web Push (iPhone home-screen web app + desktop browsers, see
// src/lib/push.js). Deliberately does no caching: this site is data-heavy
// and mostly dynamic (chat, moderation, live server status), so a
// cache-first strategy would risk serving stale content.
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  event.respondWith(fetch(event.request));
});

self.addEventListener('push', (event) => {
  let data = {};
  try { data = event.data ? event.data.json() : {}; } catch { data = { body: event.data ? event.data.text() : '' }; }
  const title = data.title || 'Iekcorbar';
  event.waitUntil(self.registration.showNotification(title, {
    body: data.body || '',
    icon: '/app-icons/icon-192.png',
    badge: '/app-icons/icon-192.png',
    tag: data.tag || undefined,
    renotify: !!data.tag,
    data: { link: data.link || '/' }
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL((event.notification.data && event.notification.data.link) || '/', self.location.origin).href;
  event.waitUntil((async () => {
    const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of all) {
      if (new URL(client.url).origin === self.location.origin && 'focus' in client) {
        await client.focus();
        if ('navigate' in client) return client.navigate(url);
        return;
      }
    }
    return self.clients.openWindow(url);
  })());
});
