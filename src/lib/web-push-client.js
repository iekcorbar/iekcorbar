// Browser-side Web Push helpers (Profile page + providers.js). Web Push works
// in desktop browsers, Android Chrome and — since iOS 16.4 — in the iPhone
// web app once it's added to the home screen (not in plain Safari tabs).
// The native Android app (user agent "IekcorbarApp/") has its own FCM push,
// so web push is never offered there.

export function isNativeApp() {
  return typeof navigator !== 'undefined' && navigator.userAgent.includes('IekcorbarApp/');
}

export function isIos() {
  if (typeof navigator === 'undefined') return false;
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

export function isStandalone() {
  if (typeof window === 'undefined') return false;
  return window.matchMedia?.('(display-mode: standalone)').matches || window.navigator.standalone === true;
}

export function webPushSupported() {
  return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

// 'native-app' | 'ios-needs-home-screen' | 'unsupported' | 'denied' | 'enabled' | 'available'
export async function getWebPushStatus() {
  if (isNativeApp()) return 'native-app';
  if (isIos() && !isStandalone()) return 'ios-needs-home-screen';
  if (!webPushSupported()) return 'unsupported';
  if (Notification.permission === 'denied') return 'denied';
  const reg = await navigator.serviceWorker.getRegistration();
  const sub = reg ? await reg.pushManager.getSubscription() : null;
  return sub && Notification.permission === 'granted' ? 'enabled' : 'available';
}

function urlBase64ToUint8Array(base64) {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  const raw = atob((base64 + padding).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from([...raw].map(c => c.charCodeAt(0)));
}

async function postSubscription(sub) {
  const res = await fetch('/api/push/web', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ subscription: sub.toJSON() })
  });
  if (!res.ok) throw new Error('Could not register this device');
}

// Must be called from a click/tap (iOS only allows the permission prompt then).
export async function enableWebPush() {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error(permission === 'denied' ? 'denied' : 'dismissed');
  const reg = await navigator.serviceWorker.register('/sw.js');
  await navigator.serviceWorker.ready;
  let sub = await reg.pushManager.getSubscription();
  if (!sub) {
    const { publicKey } = await (await fetch('/api/push/web')).json();
    if (!publicKey) throw new Error('Web push is not configured on the server');
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(publicKey) });
  }
  await postSubscription(sub);
}

export async function disableWebPush() {
  const reg = await navigator.serviceWorker.getRegistration();
  const sub = reg ? await reg.pushManager.getSubscription() : null;
  if (!sub) return;
  await fetch('/api/push/web', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ endpoint: sub.endpoint })
  }).catch(() => {});
  await sub.unsubscribe().catch(() => {});
}

// On page load for a logged-in user: if this browser is already subscribed,
// (re)bind it to whoever is logged in now. Never prompts.
export async function syncWebPush() {
  try {
    if (isNativeApp() || !webPushSupported() || Notification.permission !== 'granted') return;
    const reg = await navigator.serviceWorker.getRegistration();
    const sub = reg ? await reg.pushManager.getSubscription() : null;
    if (sub) await postSubscription(sub);
  } catch { /* best effort */ }
}

// On logout: unbind this browser from the account, but keep the browser
// subscription itself so logging back in re-binds it automatically.
export async function unbindWebPush() {
  try {
    if (!webPushSupported()) return;
    const reg = await navigator.serviceWorker.getRegistration();
    const sub = reg ? await reg.pushManager.getSubscription() : null;
    if (!sub) return;
    await fetch('/api/push/web', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpoint: sub.endpoint }),
      keepalive: true
    });
  } catch { /* best effort */ }
}
