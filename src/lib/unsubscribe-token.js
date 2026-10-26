// Signed, stateless unsubscribe tokens — no DB storage or per-user token
// column needed. The token is an HMAC of the email under a server secret,
// so a one-click unsubscribe link (?email=...&token=...) can be verified
// with no lookup, and works even if MySQL is unreachable at that moment.
//
// Worst case if this secret ever leaked or the fallback below is left in
// place on a real deployment: someone could forge an unsubscribe link for
// another player's email and opt them out of the newsletter without
// permission — annoying, but it doesn't expose any data or grant account
// access, unlike a session/password secret. Still, set UNSUBSCRIBE_SECRET
// in .env.local for production rather than relying on the fallback below.
import crypto from 'crypto';

const SECRET = process.env.UNSUBSCRIBE_SECRET || 'iekcorbar-newsletter-unsubscribe-dev-fallback-secret';

export function generateUnsubscribeToken(email) {
  return crypto.createHmac('sha256', SECRET).update(String(email).toLowerCase().trim()).digest('hex').slice(0, 32);
}

export function verifyUnsubscribeToken(email, token) {
  if (!email || !token) return false;
  const expected = generateUnsubscribeToken(email);
  const expectedBuf = Buffer.from(expected);
  const givenBuf = Buffer.from(String(token));
  if (expectedBuf.length !== givenBuf.length) return false;
  return crypto.timingSafeEqual(expectedBuf, givenBuf);
}
