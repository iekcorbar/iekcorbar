import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

// Personal notifications (friend requests/accepts, journal shares) — see
// db.js's user_notifications methods. Distinct from the staff-only
// /api/notifications feed, and session-verified like the journal/friends
// routes rather than trusting a client-sent email.
async function requireOwner(request, email) {
  if (!email) {
    return { error: NextResponse.json({ error: "Missing email" }, { status: 400 }) };
  }
  const sessionUser = await getSessionUser(request);
  if (!sessionUser || sessionUser.email.toLowerCase() !== email.toLowerCase()) {
    return { error: NextResponse.json({ error: "Not authenticated for this account" }, { status: 401 }) };
  }
  return { user: sessionUser };
}

export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const email = searchParams.get('email');
    const auth = await requireOwner(request, email);
    if (auth.error) return auth.error;

    const notifications = await db.getUserNotifications(email);
    return NextResponse.json({ notifications });
  } catch (err) {
    console.error("Profile notifications GET error:", err);
    return NextResponse.json({ error: "Failed to load notifications" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const { email, action, id } = await request.json();
    const auth = await requireOwner(request, email);
    if (auth.error) return auth.error;

    if (action === 'mark_read') {
      if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });
      await db.markUserNotificationRead(id, email);
    } else if (action === 'clear_all') {
      await db.clearUserNotifications(email);
    } else {
      return NextResponse.json({ error: "Invalid action" }, { status: 400 });
    }

    const notifications = await db.getUserNotifications(email);
    return NextResponse.json({ success: true, notifications });
  } catch (err) {
    console.error("Profile notifications POST error:", err);
    return NextResponse.json({ error: "Failed to update notifications" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const email = searchParams.get('email');
    const auth = await requireOwner(request, email);
    if (auth.error) return auth.error;

    if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });
    await db.deleteUserNotification(id, email);

    const notifications = await db.getUserNotifications(email);
    return NextResponse.json({ success: true, notifications });
  } catch (err) {
    console.error("Profile notifications DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete notification" }, { status: 500 });
  }
}
