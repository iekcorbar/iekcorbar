// Passkey (WebAuthn) helpers — a faster alternative to typing a TOTP code on
// every staff login (see src/lib/totp.js for the existing, still-required
// fallback). Thin wrapper around @simplewebauthn/server so the API routes
// don't need to know its config/shape details.
//
// RP ID / origin: WebAuthn ties every credential to the exact domain it was
// created on, so these MUST match whatever the site is actually served from.
// SITE_URL already exists for this purpose elsewhere (email links, OG-style
// URLs) — reused here rather than a separate env var. Defaults to localhost
// outside production so passkeys are actually usable in local dev without
// needing a real domain; set SITE_URL in .env.local to override either way.
import {
  generateRegistrationOptions,
  verifyRegistrationResponse,
  generateAuthenticationOptions,
  verifyAuthenticationResponse
} from '@simplewebauthn/server';
import crypto from 'crypto';

const RP_NAME = 'iekcorbar';
const SITE_URL = process.env.SITE_URL || (process.env.NODE_ENV === 'production' ? 'https://iekcorbar.com' : 'http://localhost:3000');
const RP_ID = new URL(SITE_URL).hostname;
const ORIGIN = SITE_URL;

const CHALLENGE_TTL_MS = 5 * 60 * 1000; // matches the login route's own TOTP ticket TTL

// A stable-per-account (not just per-credential) WebAuthn userID, so an
// authenticator/password manager that already has one of this account's
// passkeys correctly offers to replace/group rather than add an unrelated
// second "account". Derived, not random — never stored, cheap to recompute.
function stableUserId(email) {
  return crypto.createHash('sha256').update(String(email).toLowerCase().trim()).digest();
}

export async function buildRegistrationOptions(user, existingCredentials) {
  const options = await generateRegistrationOptions({
    rpName: RP_NAME,
    rpID: RP_ID,
    userID: stableUserId(user.email),
    userName: user.email,
    userDisplayName: user.mcUsername || user.email,
    attestationType: 'none',
    excludeCredentials: existingCredentials.map(c => ({ id: c.id, transports: c.transports })),
    authenticatorSelection: { residentKey: 'preferred', userVerification: 'preferred' }
  });
  return { options, expiresAt: Date.now() + CHALLENGE_TTL_MS };
}

export async function checkRegistrationResponse(response, expectedChallenge) {
  return verifyRegistrationResponse({
    response,
    expectedChallenge,
    expectedOrigin: ORIGIN,
    expectedRPID: RP_ID
  });
}

export async function buildAuthenticationOptions(allowCredentials) {
  const options = await generateAuthenticationOptions({
    rpID: RP_ID,
    // 2FA context, not the only factor — a plain WebAuthn login (no
    // password) would want 'preferred'/'required' here instead.
    userVerification: 'preferred',
    allowCredentials: allowCredentials.map(c => ({ id: c.id, transports: c.transports }))
  });
  return { options, expiresAt: Date.now() + CHALLENGE_TTL_MS };
}

export async function checkAuthenticationResponse(response, expectedChallenge, storedCredential) {
  return verifyAuthenticationResponse({
    response,
    expectedChallenge,
    expectedOrigin: ORIGIN,
    expectedRPID: RP_ID,
    credential: {
      id: storedCredential.id,
      publicKey: Buffer.from(storedCredential.publicKey, 'base64'),
      counter: Number(storedCredential.counter),
      transports: storedCredential.transports ? storedCredential.transports.split(',') : undefined
    }
  });
}
