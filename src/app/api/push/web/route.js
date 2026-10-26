import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { getVapidPublicKey } from '@/lib/push';

// Web Push subscriptions for this browser / iPhone home-screen web app.
// GET: the public VAPID key the browser needs to subscribe.
// POST { subscription }: bind this browser's subscription to the logged-in
//   user (called on "Enable notifications" and on every page load while
//   notifications are allowed, so switching accounts re-binds it).
// DELETE { endpoint }: unbind (logout / "Disable" on the Profile page).
export async function GET() {
  const publicKey = getVapidPublicKey();
  if (!publicKey) return NextResponse.json({ error: "Web push is not configured" }, { status: 503 });
  return NextResponse.json({ publicKey });
}

export async function POST(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

  const { subscription } = await request.json().catch(() => ({}));
  const endpoint = subscription?.endpoint;
  if (!endpoint || typeof endpoint !== 'string' || !endpoint.startsWith('https://') || endpoint.length > 2000
      || !subscription.keys?.p256dh || !subscription.keys?.auth) {
    return NextResponse.json({ error: "Invalid subscription" }, { status: 400 });
  }
  const clean = { endpoint, expirationTime: subscription.expirationTime ?? null, keys: { p256dh: String(subscription.keys.p256dh), auth: String(subscription.keys.auth) } };
  const saved = await db.saveWebPushSubscription({ userEmail: user.email, subscription: clean, userAgent: request.headers.get('user-agent') });
  if (!saved) return NextResponse.json({ error: "Could not save subscription" }, { status: 500 });
  return NextResponse.json({ success: true });
}

export async function DELETE(request) {
  const { endpoint } = await request.json().catch(() => ({}));
  if (!endpoint || typeof endpoint !== 'string') return NextResponse.json({ error: "Missing endpoint" }, { status: 400 });
  await db.deleteWebPushSubscription(endpoint);
  return NextResponse.json({ success: true });
}
