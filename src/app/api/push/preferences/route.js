import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { isStaffRole } from '@/lib/permissions';
import { normalizePushPreferences } from '@/lib/push';

// The logged-in user's own push notification settings (Profile page →
// "Push Notifications"). Session-verified; only ever reads/writes the
// caller's own row.
export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

  const map = await db.getPushPreferencesFor([user.email]);
  const staff = await isStaffRole(user.role).catch(() => false);
  return NextResponse.json({ prefs: normalizePushPreferences(map.get(user.email.toLowerCase())), isStaff: staff });
}

export async function PUT(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

  const body = await request.json().catch(() => ({}));
  const prefs = normalizePushPreferences(body.prefs);
  const saved = await db.savePushPreferences(user.email, prefs);
  if (!saved) {
    return NextResponse.json({ error: "Could not save preferences. Please try again shortly." }, { status: 500 });
  }
  return NextResponse.json({ success: true, prefs });
}
