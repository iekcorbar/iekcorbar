import { NextResponse } from 'next/server';
import { getSessionUser } from '@/lib/session';
import { withRoleInfo } from '@/lib/permissions';

// Returns the live user row for the current session. The client keeps the
// login response in localStorage, which froze fields like `role` at login
// time — providers.js calls this on load to refresh them, so a role change
// made by an admin shows up without the player having to log out and in.
export async function GET(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
    const [withInfo] = await withRoleInfo([user]);
    return NextResponse.json({ user: withInfo });
  } catch (err) {
    console.error('auth/me error:', err);
    return NextResponse.json({ error: 'Failed to load user' }, { status: 500 });
  }
}
