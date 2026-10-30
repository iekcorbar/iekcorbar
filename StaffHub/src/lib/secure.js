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

/**
 * Een sessie openen. `agent` is de user-agent van de browser die inlogde: hij
 * gaat MEET in de sessierij, zodat de owner bij "wie is er ingelogd" niet naar
 * een anoniem rijtje tokens kijkt maar naar "Windows / Firefox".
 */
export function createSession(userId, agent = '') {
  const tok = crypto.randomBytes(24).toString('hex');
  const d = getDb();
  d.prepare('INSERT INTO sessions(token,user,expires,agent) VALUES(?,?,?,?)')
    .run(tok, userId, now() + 1000 * 3600 * 24 * 30, String(agent || '').slice(0, 120));
  d.prepare('DELETE FROM sessions WHERE expires < ?').run(now());
  return tok;
}

export function userFromSid(token) {
  if (!token) return null;
  const d = getDb();
  const s = d.prepare('SELECT * FROM sessions WHERE token = ? AND expires > ?').get(token, now());
  if (!s) return null;
  const u = d.prepare('SELECT id,username,role,team,prefs,perms FROM users WHERE id = ?').get(s.user);
  if (!u) return null;
  let prefs = null;
  try { prefs = u.prefs ? JSON.parse(u.prefs) : null; } catch { prefs = null; }
  let perms = null;                            // uitgereikte bekwaamheden (lib/caps)
  try { perms = u.perms ? JSON.parse(u.perms) : null; } catch { perms = null; }
  const { prefs: _weg, perms: _perms, ...rest } = u;
  return { ...rest, prefs, perms };
}

/** Basic-auth (wachtwoord óf WebDAV-token) voor /dav. */
export function userFromBasic(header) {
  if (!header || !/^Basic /i.test(header)) return null;
  try {
    const [u, p] = Buffer.from(header.slice(6), 'base64').toString('utf8').split(':');
    const d = getDb();
    const user = d.prepare('SELECT id,username,role,team,pass,perms FROM users WHERE username = ?').get(String(u).toLowerCase());
    if (!user) return null;
    let perms = null;                          // meenemen: /dav checkt dezelfde caps als de web-UI
    try { perms = user.perms ? JSON.parse(user.perms) : null; } catch { perms = null; }
    const basis = { id: user.id, username: user.username, role: user.role, team: user.team, perms };
    if (checkPass(p, user.pass)) return basis;
    const t = d.prepare('SELECT * FROM davtokens WHERE token = ? AND user = ?').get(p, user.id);
    if (t) return basis;
    return null;
  } catch { return null; }
}
