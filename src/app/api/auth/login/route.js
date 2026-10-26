import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { generateTotpSecret, buildTotpQrCode, verifyTotpToken } from '@/lib/totp';
import { createSession, attachSessionCookie, generateOpaqueToken } from '@/lib/session';
import { isStaffRole } from '@/lib/permissions';
import { bindPushTokenFromRequest } from '@/lib/push';
import { hashPassword, isHashedPassword, verifyPassword } from '@/lib/password';
import { buildAuthenticationOptions, checkAuthenticationResponse } from '@/lib/webauthn';

import { logAudit } from '@/lib/audit';
const TICKET_TTL_MS = 5 * 60 * 1000; // 5 minutes to finish the 2FA step

function stripSensitive(user) {
  const { password, totpSecret, ...safeUser } = user;
  return safeUser;
}

async function issueSession(request, email, safeUser, { staff = false, via = null } = {}) {
  const { token, expiresAt } = await createSession(email, { staff });
  // Staff sign-ins are audited (who got into the panel, and how).
  if (staff) await logAudit(safeUser, { area: 'sign-in', action: 'signed in', target: safeUser.mcUsername || safeUser.email, detail: via });
  // Android app: bind this phone's push token (sent as a cookie) to the account.
  await bindPushTokenFromRequest(request, email);
  const response = NextResponse.json({ success: true, user: safeUser });
  // Staff sessions get a browser-session-only cookie (persistent: false) —
  // see src/lib/session.js for the full "why" (auto-logout after
  // inactivity + on browser close, requested 2026-09-08).
  // Persistent for everyone since 2026-09-27 (staff auto-logout removed, see session.js).
  return attachSessionCookie(response, token, expiresAt, { persistent: true });
}

export async function POST(request) {
  try {
    const body = await request.json();
    const { action = 'login' } = body;

    // --- PHASE 1: password check. Players get a session immediately;
    // staff (admin/moderator) get routed into the TOTP setup/verify step
    // below instead, via a short-lived ticket that proves "password already
    // checked for this email" so the password doesn't need to be resent. ---
    if (action === 'login') {
      const { email, password } = body;
      if (!email || !password) {
        return NextResponse.json({ error: "Missing email or password" }, { status: 400 });
      }

      const users = await db.getUsers();
      const user = users.find(u => u.email.toLowerCase() === email.toLowerCase());

      if (!user) {
        return NextResponse.json({ error: "Invalid credentials" }, { status: 401 });
      }

      // Real hash (see src/lib/password.js) if this account has logged in
      // since hashing shipped; a legacy plaintext password otherwise — those
      // still exist for every account created before this change. Verified
      // directly against the plaintext value one last time, then upgraded
      // to a real hash immediately so it never needs checking that way
      // again. No forced mass password reset, no account left behind.
      let passwordOk;
      if (isHashedPassword(user.password)) {
        passwordOk = verifyPassword(password, user.password);
      } else {
        passwordOk = user.password === password;
        if (passwordOk) {
          await db.setUserPasswordHash(user.email, hashPassword(password));
        }
      }

      if (!passwordOk) {
        // Wrong password on a staff account is worth knowing about (throttled so
        // a guessing run can't flood the log).
        if (await isStaffRole(user.role)) {
          await logAudit(user, { area: 'sign-in', action: 'failed password', target: user.mcUsername || user.email }, { throttleMs: 10 * 60 * 1000 });
        }
        return NextResponse.json({ error: "Invalid credentials" }, { status: 401 });
      }

      // Any role with at least one Staff Panel tab (not just the built-in
      // admin/moderator) goes through the TOTP step below — a custom staff
      // role created via Role Manager gets real staff-grade access, so it
      // gets the same 2FA requirement, not just whatever "player" got.
      if (!(await isStaffRole(user.role))) {
        return await issueSession(request, user.email, stripSensitive(user));
      }

      // Local development only: skip 2FA so the Staff Panel can be tested
      // without an authenticator app. Needs BOTH `next dev` (NODE_ENV is
      // always 'production' for `next start`/the Plesk deploy, so this can
      // never fire there) AND a localhost Host header.
      if (process.env.NODE_ENV === 'development' && /^(localhost|127\.0\.0\.1)(:\d+)?$/.test(request.headers.get('host') || '')) {
        return await issueSession(request, user.email, stripSensitive(user), { staff: true, via: 'local development shortcut' });
      }

      // Staff account — needs a TOTP step before a session is issued.
      const ticket = generateOpaqueToken();
      await db.savePendingTwoFactor(ticket, {
        email: user.email,
        expiresAt: Date.now() + TICKET_TTL_MS
      });

      if (!user.totpEnabled) {
        // First staff login (or setup was never finished) — generate a
        // fresh secret and hand back a QR code to scan.
        const secret = generateTotpSecret();
        const saved = await db.setUserTotpSecret(user.email, secret);
        if (!saved) {
          return NextResponse.json({ error: "Could not start 2FA setup (database unreachable). Please try again shortly." }, { status: 500 });
        }
        const { qrCodeDataUrl } = await buildTotpQrCode(user.email, secret);
        return NextResponse.json({
          success: true,
          needsTotpSetup: true,
          ticket,
          qrCodeDataUrl,
          secretForManualEntry: secret
        });
      }

      // hasPasskeys tells the login modal whether "Use a passkey instead" is
      // worth showing at all — see api/auth/passkey/register-* for how one
      // gets added (from a logged-in session, /profile), and the two
      // passkey_auth_* actions below for what this button actually calls.
      const passkeys = await db.getPasskeysForUser(user.email);
      return NextResponse.json({ success: true, needsTotpCode: true, ticket, hasPasskeys: passkeys.length > 0 });
    }

    // --- PASSKEY LOGIN (alternative to typing a TOTP code) — reuses the
    // same ticket phase 1 issued above, so the password doesn't need
    // resending and the ticket's own TTL/expiry logic isn't duplicated. ---
    if (action === 'passkey_auth_options') {
      const { ticket } = body;
      if (!ticket) {
        return NextResponse.json({ error: 'Missing ticket' }, { status: 400 });
      }
      const pending = await db.getPendingTwoFactor(ticket);
      if (!pending || pending.expiresAt < Date.now()) {
        return NextResponse.json({ error: '2FA session expired. Please log in again.' }, { status: 400 });
      }

      const passkeys = await db.getPasskeysForUser(pending.email);
      if (passkeys.length === 0) {
        return NextResponse.json({ error: 'No passkeys registered for this account.' }, { status: 400 });
      }

      const { options, expiresAt } = await buildAuthenticationOptions(passkeys);
      // Keyed by ticket, not email — a login ticket is already the
      // single-in-flight-attempt handle for this sign-in, and reusing it
      // means the passkey challenge expires together with the ticket it
      // belongs to instead of needing its own separate cleanup.
      await db.savePasskeyChallenge(ticket, { challenge: options.challenge, expiresAt });

      return NextResponse.json({ success: true, options });
    }

    if (action === 'passkey_auth_verify') {
      const { ticket, response } = body;
      if (!ticket || !response) {
        return NextResponse.json({ error: 'Missing ticket or response' }, { status: 400 });
      }

      const pending = await db.getPendingTwoFactor(ticket);
      if (!pending || pending.expiresAt < Date.now()) {
        if (pending) await db.deletePendingTwoFactor(ticket);
        return NextResponse.json({ error: '2FA session expired. Please log in again.' }, { status: 400 });
      }

      const challenge = await db.getPasskeyChallenge(ticket);
      if (!challenge || challenge.expiresAt < Date.now()) {
        return NextResponse.json({ error: 'This passkey attempt expired. Please try again.' }, { status: 400 });
      }

      const credential = await db.getPasskeyCredentialById(response.id);
      // Both checks matter: the credential might not exist at all (stale
      // client state), or — the real security-relevant one — it could
      // belong to a completely different account than the one this ticket's
      // password check was for. Never trust `response.id` alone to imply
      // whose passkey this is.
      if (!credential || credential.userEmail.toLowerCase() !== pending.email.toLowerCase()) {
        return NextResponse.json({ error: 'This passkey is not registered to this account.' }, { status: 400 });
      }

      let verification;
      try {
        verification = await checkAuthenticationResponse(response, challenge.challenge, credential);
      } catch (err) {
        console.error('Passkey login verify threw:', err);
        return NextResponse.json({ error: 'Could not verify that passkey.' }, { status: 400 });
      }
      if (!verification.verified) {
        return NextResponse.json({ error: 'Could not verify that passkey.' }, { status: 400 });
      }

      await db.updatePasskeyCounter(credential.id, verification.authenticationInfo.newCounter);
      await db.deletePasskeyChallenge(ticket);
      await db.deletePendingTwoFactor(ticket);

      const users = await db.getUsers();
      const user = users.find(u => u.email.toLowerCase() === pending.email.toLowerCase());
      if (!user) {
        return NextResponse.json({ error: 'Account no longer exists.' }, { status: 400 });
      }
      // Same reasoning as confirm_totp_setup/verify_totp: only staff ever
      // reach this action (passkeys are only ever offered after the staff
      // TOTP branch above), so staff:true is safe to hardcode here too.
      return await issueSession(request, user.email, stripSensitive(user), { staff: true, via: 'passkey' });
    }

    // --- PHASE 2a: first-time setup — confirm the scanned code, enable
    // 2FA on the account, then issue the session. ---
    if (action === 'confirm_totp_setup' || action === 'verify_totp') {
      const { ticket, code } = body;
      if (!ticket || !code) {
        return NextResponse.json({ error: "Missing ticket or code" }, { status: 400 });
      }

      const pending = await db.getPendingTwoFactor(ticket);
      if (!pending || pending.expiresAt < Date.now()) {
        if (pending) await db.deletePendingTwoFactor(ticket);
        return NextResponse.json({ error: "2FA session expired. Please log in again." }, { status: 400 });
      }

      const users = await db.getUsers();
      const user = users.find(u => u.email.toLowerCase() === pending.email.toLowerCase());
      if (!user) {
        await db.deletePendingTwoFactor(ticket);
        return NextResponse.json({ error: "Account no longer exists." }, { status: 400 });
      }

      const valid = await verifyTotpToken(user.totpSecret, code);
      if (!valid) {
        await logAudit(user, { area: 'sign-in', action: 'failed 2FA code', target: user.mcUsername || user.email }, { throttleMs: 10 * 60 * 1000 });
        return NextResponse.json({ error: "Invalid verification code" }, { status: 400 });
      }

      if (action === 'confirm_totp_setup') {
        const enabled = await db.enableUserTotp(user.email);
        if (!enabled) {
          return NextResponse.json({ error: "Could not enable 2FA (database unreachable). Please try again shortly." }, { status: 500 });
        }
      }

      await db.deletePendingTwoFactor(ticket);
      // Every account that reaches this point went through the TOTP step,
      // which only staff (isStaffRole) are routed into — see PHASE 1 above.
      // Safe to hardcode staff:true here rather than re-checking.
      return await issueSession(request, user.email, stripSensitive({ ...user, totpEnabled: true }), { staff: true, via: 'authenticator code' });
    }

    return NextResponse.json({ error: "Invalid action" }, { status: 400 });
  } catch (err) {
    console.error("Login endpoint error:", err);
    return NextResponse.json({ error: "Server error during login" }, { status: 500 });
  }
}
