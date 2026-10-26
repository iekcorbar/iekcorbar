import crypto from 'crypto';
import fs from 'fs';
import { db } from '@/lib/db';
import { isStaffRole } from '@/lib/permissions';
import webpush from 'web-push';

const EXPO_PUSH_ENDPOINT = 'https://exp.host/--/api/v2/push/send';

// Platform value stored in device_tokens.platform for the native Android app
// (the WebView wrapper around this site, a separate Android Studio/Gradle
// project). Its tokens are raw FCM registration tokens, not Expo tokens, and
// go through FCM's HTTP v1 API directly below. The token itself still lives
// in the `expo_push_token` column — historical name only, renaming it would
// need a migration for no functional gain.
export const FCM_PLATFORM = 'android-fcm';

// Cookie the Android app sets on iekcorbar.com containing its own FCM token,
// so the login/logout routes can (un)bind the device without the WebView
// needing custom headers — see bindPushTokenFromRequest below.
export const PUSH_TOKEN_COOKIE = 'iekcorbar_push_token';

// --- FCM HTTP v1 auth -------------------------------------------------------
// Credentials come from a Firebase service account (Firebase console →
// Project settings → Service accounts → Generate new private key), either as
// a path to that JSON file (FCM_SERVICE_ACCOUNT_FILE) or as its three
// relevant fields in .env.local (FCM_PROJECT_ID / FCM_CLIENT_EMAIL /
// FCM_PRIVATE_KEY, the key with literal "\n" escapes). Without either, FCM
// sending no-ops with a one-time warning instead of failing.
let serviceAccount;
function getServiceAccount() {
  if (serviceAccount !== undefined) return serviceAccount;
  serviceAccount = null;
  try {
    if (process.env.FCM_SERVICE_ACCOUNT_FILE) {
      const json = JSON.parse(fs.readFileSync(process.env.FCM_SERVICE_ACCOUNT_FILE, 'utf8'));
      serviceAccount = { projectId: json.project_id, clientEmail: json.client_email, privateKey: json.private_key };
    } else if (process.env.FCM_PROJECT_ID && process.env.FCM_CLIENT_EMAIL && process.env.FCM_PRIVATE_KEY) {
      serviceAccount = {
        projectId: process.env.FCM_PROJECT_ID,
        clientEmail: process.env.FCM_CLIENT_EMAIL,
        privateKey: process.env.FCM_PRIVATE_KEY.replace(/\\n/g, '\n')
      };
    }
  } catch (err) {
    console.error('[Push] Failed to load FCM service account:', err);
  }
  if (!serviceAccount) console.warn('[Push] FCM not configured (FCM_SERVICE_ACCOUNT_FILE or FCM_PROJECT_ID/FCM_CLIENT_EMAIL/FCM_PRIVATE_KEY) — Android push disabled.');
  return serviceAccount;
}

let cachedAccessToken = null; // { token, expiresAt }
async function getFcmAccessToken(sa) {
  if (cachedAccessToken && cachedAccessToken.expiresAt > Date.now() + 60_000) return cachedAccessToken.token;

  const now = Math.floor(Date.now() / 1000);
  const b64 = obj => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const unsigned = `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64({
    iss: sa.clientEmail,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600
  })}`;
  const signature = crypto.createSign('RSA-SHA256').update(unsigned).sign(sa.privateKey, 'base64url');

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${unsigned}.${signature}`
    })
  });
  const data = await res.json();
  if (!res.ok || !data.access_token) throw new Error(`FCM auth failed: ${JSON.stringify(data)}`);
  cachedAccessToken = { token: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 };
  return cachedAccessToken.token;
}

// Data-only message: the app builds the notification itself (PushService.java
// in the Android project), so it looks the same whether the app is in the
// foreground, background or killed, and tapping it opens `link`.
async function sendFcm(tokens, { title, body, data }) {
  if (tokens.length === 0) return;
  const sa = getServiceAccount();
  if (!sa) return;
  const accessToken = await getFcmAccessToken(sa);
  const payload = { title: title || 'Iekcorbar', body: body || '' };
  for (const [k, v] of Object.entries(data || {})) {
    if (v !== undefined && v !== null) payload[k] = String(v);
  }

  await Promise.all(tokens.map(async token => {
    try {
      const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.projectId}/messages:send`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: { token, data: payload, android: { priority: 'high' } } })
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        const code = err?.error?.details?.find(d => d.errorCode)?.errorCode || err?.error?.status;
        // App uninstalled / token rotated — drop it so we stop sending to it.
        if (res.status === 404 || code === 'UNREGISTERED') {
          await db.deleteDeviceToken(token);
        } else {
          console.error('[Push] FCM send failed:', res.status, JSON.stringify(err));
        }
      }
    } catch (err) {
      console.error('[Push] FCM send error:', err);
    }
  }));
}

// --- Web Push (iPhone home-screen web app + desktop browsers) ---------------
// VAPID keys in .env.local (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY /
// VAPID_SUBJECT). The browser's service worker (public/sw.js) shows the
// notification and opens `link` on tap. No action buttons: iOS doesn't
// support them for web push.
let vapidReady;
function ensureVapid() {
  if (vapidReady !== undefined) return vapidReady;
  vapidReady = false;
  const { VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT } = process.env;
  if (VAPID_PUBLIC_KEY && VAPID_PRIVATE_KEY) {
    webpush.setVapidDetails(VAPID_SUBJECT || 'https://iekcorbar.com', VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY);
    vapidReady = true;
  } else {
    console.warn('[Push] VAPID keys not configured — web push disabled.');
  }
  return vapidReady;
}

export function getVapidPublicKey() {
  return process.env.VAPID_PUBLIC_KEY || null;
}

async function sendWebPush(subscriptions, { title, body, data }) {
  if (subscriptions.length === 0 || !ensureVapid()) return;
  const payload = JSON.stringify({
    title: title || 'Iekcorbar',
    body: body || '',
    link: data?.link || '/',
    tag: data?.tag || data?.type || undefined
  });
  await Promise.all(subscriptions.map(async sub => {
    try {
      await webpush.sendNotification(sub, payload, { TTL: 24 * 60 * 60, urgency: 'high' });
    } catch (err) {
      // 404/410: the browser unsubscribed or the home-screen app was removed.
      if (err.statusCode === 404 || err.statusCode === 410) {
        await db.deleteWebPushSubscription(sub.endpoint);
      } else {
        console.error('[Push] Web push send failed:', err.statusCode || '', err.body || err.message);
      }
    }
  }));
}

async function sendExpo(tokens, { title, body, data }) {
  if (tokens.length === 0) return;
  await fetch(EXPO_PUSH_ENDPOINT, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Accept-Encoding': 'gzip, deflate',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(tokens.map(to => ({ to, sound: 'default', title, body, data: data || {} })))
  });
}

// Sends a push notification to every device registered to a user (keyed by email —
// see the note on device_tokens in db.js for why this isn't a numeric user id).
// Android-app devices go through FCM, Expo-app devices through Expo's push API.
export async function sendPushToUser(userEmail, message = {}) {
  try {
    const [tokens, webSubs] = await Promise.all([
      db.getDeviceTokens(userEmail).catch(() => []),
      db.getWebPushSubscriptions(userEmail).catch(() => [])
    ]);
    const fcm = (tokens || []).filter(t => t.platform === FCM_PLATFORM).map(t => t.token);
    const expo = (tokens || []).filter(t => t.platform !== FCM_PLATFORM).map(t => t.token);
    await Promise.all([sendFcm(fcm, message), sendExpo(expo, message), sendWebPush(webSubs || [], message)]);
  } catch (err) {
    console.error('Failed to send push notification:', err);
  }
}

// Push categories the user can switch off on the Profile page (plus a master
// "enabled" switch). Every notification type maps to one category; anything
// sent to the staff feed (staff: true) is always the 'staff' category.
export const PUSH_CATEGORIES = ['chat', 'personal', 'events', 'tips', 'community', 'staff'];
export const DEFAULT_PUSH_PREFERENCES = { enabled: true, chat: true, personal: true, events: true, tips: true, community: true, staff: true };
const TYPE_CATEGORY = {
  chat: 'chat',
  friend_request: 'personal', friend_accepted: 'personal', journal_share: 'personal', bug_report: 'personal', account_purge: 'personal',
  account_link: 'personal',
  event: 'events', project: 'events', poll: 'events', event_reminder: 'events',
  daily_tip: 'tips'
  // everything else (updates, news, wiki, rules, tips, shop, ...) → 'community'
};
export function pushCategoryFor(type, staff = false) {
  if (staff) return 'staff';
  return TYPE_CATEGORY[type] || 'community';
}
export function normalizePushPreferences(prefs) {
  const out = { ...DEFAULT_PUSH_PREFERENCES };
  if (prefs && typeof prefs === 'object') {
    for (const key of ['enabled', ...PUSH_CATEGORIES]) {
      if (typeof prefs[key] === 'boolean') out[key] = prefs[key];
    }
  }
  return out;
}

const TITLES = {
  chat: 'New message',
  friend_request: 'Friend request',
  friend_accepted: 'Friend request accepted',
  journal_share: 'Journal shared with you',
  bug_report: 'Bug report',
  report: 'New report',
  staff_application: 'Staff application',
  account_purge: 'Account',
  updates: 'Website update',
  event: 'New event',
  poll: 'New poll',
  project: 'New community project',
  news: 'News',
  wiki: 'Wiki',
  rules: 'Rules',
  wiki_category: 'Wiki',
  tip: 'Tip of the day',
  carousel: 'Iekcorbar',
  vote_site: 'Vote',
  shop: 'Shop',
  carousel_set: 'Iekcorbar',
  achievement: 'Achievement',
  brewing: 'Brewery',
  guide: 'Starter guide',
  site_content: 'Iekcorbar'
};

// Push counterpart of every notification-bell entry — called fire-and-forget
// from db.js wherever a user_notifications / notifications row is created, so
// every bell notification also reaches the Android app, chat messages
// included (api/chat creates a 'chat' bell entry per message). `staff: true`
// targets everyone whose role can open the Staff Panel (the staff bell
// feed); otherwise `recipients` is a list of emails.
export async function pushBellNotification({ recipients = [], staff = false, type, message, link, pushTitle, pushBody, tag, actions }) {
  try {
    let emails = recipients;
    if (staff) {
      const users = await db.getUsers();
      const roles = [...new Set(users.map(u => u.role))];
      const staffRoles = new Set();
      await Promise.all(roles.map(async r => { if (await isStaffRole(r).catch(() => false)) staffRoles.add(r); }));
      emails = users.filter(u => staffRoles.has(u.role)).map(u => u.email);
    }
    let unique = [...new Set(emails.filter(Boolean))];
    // Respect each recipient's own push settings (Profile page).
    const category = pushCategoryFor(type, staff);
    const prefsMap = await db.getPushPreferencesFor(unique);
    unique = unique.filter(e => {
      const p = normalizePushPreferences(prefsMap.get(e.toLowerCase()));
      return p.enabled && p[category];
    });
    const msg = {
      title: pushTitle || TITLES[type] || 'Iekcorbar',
      body: pushBody || message,
      // actions: up to two notification buttons [{ label, link }] (JSON string — FCM data values must be strings)
      data: { type, link: link || '/', tag: tag || type, actions: Array.isArray(actions) && actions.length ? JSON.stringify(actions.slice(0, 2)) : undefined }
    };
    await Promise.all(unique.map(e => sendPushToUser(e, msg)));
  } catch (err) {
    console.error('Failed to push bell notification:', err);
  }
}

// Called from the login route once a session is issued: if the request came
// from the Android app (it sets PUSH_TOKEN_COOKIE), bind that device to the
// user who just logged in.
export async function bindPushTokenFromRequest(request, email) {
  try {
    const token = request.cookies.get(PUSH_TOKEN_COOKIE)?.value;
    if (token && email) await db.saveDeviceToken({ userEmail: email, platform: FCM_PLATFORM, token });
  } catch (err) {
    console.error('Failed to bind push token on login:', err);
  }
}

// Logout counterpart — a logged-out phone must stop receiving that user's
// chat messages and notifications.
export async function unbindPushTokenFromRequest(request) {
  try {
    const token = request.cookies.get(PUSH_TOKEN_COOKIE)?.value;
    if (token) await db.deleteDeviceToken(token);
  } catch (err) {
    console.error('Failed to unbind push token on logout:', err);
  }
}
