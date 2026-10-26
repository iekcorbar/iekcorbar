import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// Was previously an unwritten, de-facto "1 event at a time" limit caused by
// a registerForEvent bug (see db.js) rather than a real business rule. Now
// a real, deliberate cap enforced here.
const MAX_EVENTS_PER_PLAYER = 3;

export async function POST(request) {
  try {
    const { eventId, username, action } = await request.json();

    if (!eventId || !username || !action) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
    }

    if (action === 'register') {
      const allRegistrations = await db.getRegistrations();
      const mine = allRegistrations.filter(r => String(r.username).toLowerCase() === String(username).toLowerCase());
      const alreadyInThisEvent = mine.some(r => String(r.eventId) === String(eventId));

      if (!alreadyInThisEvent && mine.length >= MAX_EVENTS_PER_PLAYER) {
        return NextResponse.json({
          error: `You're already signed up for ${MAX_EVENTS_PER_PLAYER} events — that's the max at once. Leave one first to join another.`
        }, { status: 400 });
      }

      const ok = await db.registerForEvent(eventId, username);
      if (!ok) {
        return NextResponse.json({ error: "Failed to register (database unreachable). Please try again shortly." }, { status: 500 });
      }
    } else if (action === 'unregister') {
      await db.unregisterFromEvent(eventId, username);
    } else {
      return NextResponse.json({ error: "Invalid action" }, { status: 400 });
    }

    return NextResponse.json({ success: true, registrations: await db.getRegistrations() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to process registration" }, { status: 500 });
  }
}
