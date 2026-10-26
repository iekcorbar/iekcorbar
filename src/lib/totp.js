// TOTP (RFC 6238) helpers for staff 2FA. Thin wrapper around otplib's
// functional API (v13) so callers don't need to know about its
// secret/URI/verify shapes, and around `qrcode` for rendering the
// authenticator-app QR code as a data URI (no file writes, nothing to clean
// up — fits the same "no server-side temp state beyond what's persisted"
// spirit as the rest of the auth flow).
import { generateSecret, generate, verify, generateURI } from 'otplib';
import QRCode from 'qrcode';

const ISSUER = 'Iekcorbar';

// Base32 secret, stored on users.totp_secret once confirmed.
export function generateTotpSecret() {
  return generateSecret();
}

// otpauth:// URI + rendered QR code (data URI) for the one-time "scan this"
// setup screen. `label` is the user's email so it shows up nicely inside
// the authenticator app next to other accounts.
export async function buildTotpQrCode(email, secret) {
  const uri = generateURI({ issuer: ISSUER, label: email, secret });
  const qrCodeDataUrl = await QRCode.toDataURL(uri);
  return { uri, qrCodeDataUrl };
}

// Checks a 6-digit code against a secret. otplib already tolerates the
// standard +/-1 time-step drift by default.
export async function verifyTotpToken(secret, token) {
  if (!secret || !token) return false;
  try {
    const result = await verify({ secret, token: String(token).trim() });
    return !!result.valid;
  } catch (err) {
    console.error('TOTP verify failed:', err);
    return false;
  }
}
