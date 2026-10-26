// Real, server-verified sessions — the auth model described in this repo's
// CLAUDE.md ("Auth model — client-asserted roles, no server sessions") has a
// gap: every privileged API request just trusts whatever `role` the client
// sends. This file adds an actual session (random token in an httpOnly
// cookie, checked server-side against the `sessions` table on every call)
// so routes that opt into `requireRole()` verify the caller for real instead
// of trusting the request body/query string.
//
// This coexists with the old pattern rather than replacing it everywhere at
// once — only routes explicitly migrated to requireRole() get the real
// check; the rest still trust the client-sent role until they're migrated
// too (see the moderation route for the first one, and the iekcorbar
// project-context memory for the rest of the migration).
import { NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';

export const SESSION_COOKIE = 'iekcorbar_session';
const PLAYER_SESSION_DURATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 days, unchanged

// Staff (admin/moderator/any custom role with Staff Panel access — same
// "isStaffRole" boundary the 2FA requirement uses) get a much shorter,
// *sliding* expiry instead: getSessionUser() below bumps it back up to this
// full duration on every authenticated request, so an actively-working
// staff member is never logged out mid-task — only a session left open and
// genuinely unused for this long expires. Combined with a browser-session
// (non-persistent) cookie in attachSessionCookie() so it's also cleared the
// moment the browser itself closes, not just after this timer. Requested by
// the user 2026-09-08 ("admin sessies moeten automatisch afgesloten worden
// ... na sluiten browser") after being asked to pick something workable
// between 2-8 hours.
// 2026-09-27: the short 3-hour staff expiry + browser-session-only cookie was
// removed at the operator's request ("irritant en niet nodig") — staff now
// stay logged in as long as players do (still sliding: every request
// extends it again). Staff 2FA at login is unchanged.
const STAFF_SESSION_DURATION_MS = PLAYER_SESSION_DURATION_MS;

// 64 hex chars — matches `sessions.token CHAR(64)` in schema.sql.
export function generateOpaqueToken() {
  return crypto.randomBytes(32).toString('hex');
}

// Creates the session row. Caller still needs to attach the cookie to a
// NextResponse via attachSessionCookie() — kept separate because the two
// often happen at different points in a route handler (e.g. after building
// the response body). Pass { staff: true } for any staff login (mirrors
// isStaffRole() — see the login route) to get the shorter sliding duration
// and a session-only cookie instead of the player default.
export async function createSession(email, { staff = false } = {}) {
  const token = generateOpaqueToken();
  const durationMs = staff ? STAFF_SESSION_DURATION_MS : PLAYER_SESSION_DURATION_MS;
  const expiresAt = new Date(Date.now() + durationMs);
  await db.createSession(token, email, expiresAt, staff);
  return { token, expiresAt, staff };
}

// `persistent: false` omits the cookie's own expires/max-age entirely,
// which makes it a true browser-session cookie — the browser discards it
// when it closes, independently of the server-side expiresAt above.
export function attachSessionCookie(response, token, expiresAt, { persistent = true } = {}) {
  const cookieOptions = {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/'
  };
  if (persistent) cookieOptions.expires = expiresAt;
  response.cookies.set(SESSION_COOKIE, token, cookieOptions);
  return response;
}

export function clearSessionCookie(response) {
  response.cookies.set(SESSION_COOKIE, '', {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: '/',
    maxAge: 0
  });
  return response;
}

export async function destroySession(token) {
  if (!token) return;
  await db.deleteSession(token);
}

// Resolves the incoming request's session cookie to a *live* user row (the
// role is re-read from `users` on every call, not frozen at login time) or
// null if there's no valid session. Password/TOTP secret are stripped.
export async function getSessionUser(request) {
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) return null;

  const session = await db.getSessionByToken(token);
  if (!session) return null;

  const users = await db.getUsers();
  const user = users.find(u => u.email && u.email.toLowerCase() === session.userEmail.toLowerCase());
  if (!user) return null;

  // Sliding expiry — see STAFF_SESSION_DURATION_MS above. Whether a session
  // is "staff" is decided once, at login (createSession's `staff` flag,
  // stored on the row), not re-derived from the user's *current* role here
  // — re-deriving it would need permissions.js, which itself imports this
  // file, so doing it here would be a circular import. A role change only
  // takes effect for sessions created after it.
  if (session.isStaff) {
    db.updateSessionExpiry(token, new Date(Date.now() + STAFF_SESSION_DURATION_MS)).catch(() => {});
  }

  const { password, totpSecret, ...safeUser } = user;
  return safeUser;
}

// Guard for API routes: verifies a real session belongs to a user with one
// of `roles`. Usage:
//   const auth = await requireRole(request, ['admin', 'moderator']);
//   if (auth.error) return auth.error;
//   const { user } = auth;
export async function requireRole(request, roles) {
  const user = await getSessionUser(request);
  if (!user) {
    return { error: NextResponse.json({ error: 'Not authenticated' }, { status: 401 }) };
  }
  if (!roles.includes(user.role)) {
    return { error: NextResponse.json({ error: 'Forbidden' }, { status: 403 }) };
  }
  return { user };
}
