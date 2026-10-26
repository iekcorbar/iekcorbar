import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { FCM_PLATFORM } from '@/lib/push';

// POST: Register/update a device's push token for the logged-in user
export async function POST(request) {
  try {
    const { email, platform, token } = await request.json();

    // Android app (FCM): called from inside the app's WebView on every page
    // load, so the device is identified by the real session cookie rather
    // than a client-sent email. No session (logged out / expired) unbinds
    // the device instead, so a logged-out phone stops getting that user's
    // notifications.
    if (platform === FCM_PLATFORM) {
      if (!token || typeof token !== 'string' || token.length > 255) {
        return NextResponse.json({ error: "Missing or invalid token" }, { status: 400 });
      }
      const user = await getSessionUser(request);
      if (!user) {
        await db.deleteDeviceToken(token);
        return NextResponse.json({ success: true, registered: false });
      }
      await db.saveDeviceToken({ userEmail: user.email, platform: FCM_PLATFORM, token });
      return NextResponse.json({ success: true, registered: true });
    }

    if (!email || !platform || !token) {
      return NextResponse.json({ error: "Missing email, platform or token" }, { status: 400 });
    }

    if (!token.startsWith('ExponentPushToken[') && !token.startsWith('ExpoPushToken[')) {
      return NextResponse.json({ error: "Not a valid Expo push token" }, { status: 400 });
    }

    await db.saveDeviceToken({ userEmail: email, platform, token });
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to register device token" }, { status: 500 });
  }
}

// DELETE: Unregister a device's push token (e.g. on logout)
export async function DELETE(request) {
  try {
    const { searchParams } = new URL(request.url);
    const token = searchParams.get('token');

    if (!token) {
      return NextResponse.json({ error: "Missing token" }, { status: 400 });
    }

    await db.deleteDeviceToken(token);
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to unregister device token" }, { status: 500 });
  }
}
