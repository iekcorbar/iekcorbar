import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

// Self-service newsletter opt-out toggle, for any logged-in user — the
// same underlying flag (users.newsletter_opt_out) that the one-click email
// unsubscribe link flips, just reachable from the Profile page too, for
// anyone who'd rather manage it there than dig up an old email.
export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

  const users = await db.getUsers();
  const me = users.find(u => u.email.toLowerCase() === user.email.toLowerCase());
  return NextResponse.json({ optedOut: !!me?.newsletterOptOut });
}

export async function PUT(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

  const { optedOut } = await request.json();
  const saved = await db.setNewsletterOptOut(user.email, !!optedOut);
  if (!saved) {
    return NextResponse.json({ error: "Could not save preference (database unreachable). Please try again shortly." }, { status: 500 });
  }
  return NextResponse.json({ success: true, optedOut: !!optedOut });
}
