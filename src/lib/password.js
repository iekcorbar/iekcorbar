// Password hashing via Node's built-in crypto (scrypt) — no extra dependency
// and no native bindings to worry about on the Plesk/Linux host (unlike
// bcrypt). Stored format is `scrypt:<saltHex>:<hashHex>`, which doubles as
// the marker `isHashedPassword` uses to tell a real hash apart from a
// legacy plaintext password still sitting in `users.password` from before
// this file existed — see the login route's lazy-migration comment for how
// those get upgraded.
import crypto from 'crypto';

const KEY_LENGTH = 64;

export function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const hash = crypto.scryptSync(password, salt, KEY_LENGTH).toString('hex');
  return `scrypt:${salt}:${hash}`;
}

export function isHashedPassword(stored) {
  return typeof stored === 'string' && stored.startsWith('scrypt:');
}

// Never throws on a malformed stored value — a corrupt/unexpected format is
// treated as "does not match" rather than crashing the login request.
export function verifyPassword(password, stored) {
  try {
    const [, salt, hashHex] = stored.split(':');
    if (!salt || !hashHex) return false;
    const candidate = crypto.scryptSync(password, salt, KEY_LENGTH);
    const actual = Buffer.from(hashHex, 'hex');
    if (candidate.length !== actual.length) return false;
    return crypto.timingSafeEqual(candidate, actual);
  } catch {
    return false;
  }
}
