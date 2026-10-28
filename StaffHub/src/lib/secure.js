// Puur-platte grond van de authenticatie: geen next/headers, dus ook te
// gebruiken vanuit server.js / WebDAV.
import crypto from 'node:crypto';
import { getDb, now } from './db.js';

export function hashPass(pw) {
  const salt = crypto.randomBytes(16);
  const h = crypto.scryptSync(pw, salt, 32);
  return salt.toString('hex') + ':' + h.toString('hex');
}

export function checkPass(pw, stored) {
  try {
    const [s, h] = String(stored).split(':');
    const salt = Buffer.from(s, 'hex');
    const want = Buffer.from(h, 'hex');
    const got = crypto.scryptSync(pw, salt, want.length);
    return crypto.timingSafeEqual(want, got);
  } catch { return false; }
}

export function createSession(userId) {
  const tok = crypto.randomBytes(24).toString('hex');
  const d = getDb();
  d.prepare('INSERT INTO sessions(token,user,expires) VALUES(?,?,?)').run(tok, userId, now() + 1000 * 3600 * 24 * 30);
  d.prepare('DELETE FROM sessions WHERE expires < ?').run(now());
  return tok;
}

export function userFromSid(token) {
  if (!token) return null;
  const d = getDb();
  const s = d.prepare('SELECT * FROM sessions WHERE token = ? AND expires > ?').get(token, now());
  if (!s) return null;
  const u = d.prepare('SELECT id,username,role,team FROM users WHERE id = ?').get(s.user);
  return u || null;
}

/** Basic-auth (wachtwoord óf WebDAV-token) voor /dav. */
export function userFromBasic(header) {
  if (!header || !/^Basic /i.test(header)) return null;
  try {
    const [u, p] = Buffer.from(header.slice(6), 'base64').toString('utf8').split(':');
    const d = getDb();
    const user = d.prepare('SELECT id,username,role,team,pass FROM users WHERE username = ?').get(String(u).toLowerCase());
    if (!user) return null;
    if (checkPass(p, user.pass)) return { id: user.id, username: user.username, role: user.role, team: user.team };
    const t = d.prepare('SELECT * FROM davtokens WHERE token = ? AND user = ?').get(p, user.id);
    if (t) return { id: user.id, username: user.username, role: user.role, team: user.team };
    return null;
  } catch { return null; }
}
