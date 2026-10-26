import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// GET: Fetch staff notifications
export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const role = searchParams.get('role');

    if (role !== 'admin' && role !== 'moderator') {
      return NextResponse.json({ error: "Unauthorized" }, { status: 403 });
    }

    return NextResponse.json({ notifications: await db.getNotifications() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve notifications" }, { status: 500 });
  }
}

// DELETE: Dismiss a notification
export async function DELETE(request) {
  try {
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const role = searchParams.get('role');

    if (role !== 'admin' && role !== 'moderator') {
      return NextResponse.json({ error: "Unauthorized" }, { status: 403 });
    }

    if (!id) {
      return NextResponse.json({ error: "Missing notification ID" }, { status: 400 });
    }

    await db.dismissNotification(id);
    return NextResponse.json({ success: true, notifications: await db.getNotifications() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to dismiss notification" }, { status: 500 });
  }
}

// POST: Actions on notifications (e.g. clear all)
export async function POST(request) {
  try {
    const { role, action } = await request.json();

    if (role !== 'admin' && role !== 'moderator') {
      return NextResponse.json({ error: "Unauthorized" }, { status: 403 });
    }

    if (action === 'clear_all') {
      await db.clearAllNotifications();
      return NextResponse.json({ success: true, notifications: [] });
    }

    return NextResponse.json({ error: "Invalid action" }, { status: 400 });
  } catch (err) {
    return NextResponse.json({ error: "Failed to process request" }, { status: 500 });
  }
}
