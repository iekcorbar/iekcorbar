// Cookie-laag bovenop secure.js (alleen voor de Next-routes).
import { cookies } from 'next/headers';
import { createSession, userFromSid } from './secure.js';
import { getDb } from './db.js';

export { hashPass, checkPass, createSession, userFromBasic } from './secure.js';

export const SID = 'sh_sid';

export async function setSessionCookie(token) {
  const c = await cookies();
  c.set(SID, token, { httpOnly: true, sameSite: 'lax', path: '/', maxAge: 60 * 60 * 24 * 30, secure: process.env.COOKIE_SECURE === '1' });
}

export async function clearSessionCookie() {
  const c = await cookies();
  c.delete(SID);
}

/** Huidige gebruiker uit de sessie-cookie, of null. */
export async function me() {
  const c = await cookies();
  return userFromSid(c.get(SID)?.value);
}

export function needUser(user) {
  if (!user) { const e = new Error('login'); e.status = 401; throw e; }
  return user;
}

export function dropSessions(userId) {
  getDb().prepare('DELETE FROM sessions WHERE user = ?').run(userId);
}
