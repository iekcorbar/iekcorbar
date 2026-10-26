import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { generateOpaqueToken } from '@/lib/session';
import { hashPassword } from '@/lib/password';
import { sendMail } from '@/lib/mail';
import { passwordResetEmail } from '@/lib/email-templates';

const MIN_PASSWORD_LENGTH = 8;
const RESET_TTL_MS = 30 * 60 * 1000; // 30 minutes

// "Forgot password?" — for players who no longer remember their old
// password (the only self-service password change before this, api/account's
// PUT, requires knowing the current one, so it can't help here). Two
// actions, mirroring the shape of api/auth/login's phased POST:
//   'request' — given an email, mails a single-use reset link if an account
//               exists for it (see src/lib/db.js's password_resets table).
//   'reset'   — given that link's token + a new password, sets it.
//
// Deliberately never reveals whether an email address has an account (the
// 'request' branch always returns the same generic success message) so this
// can't be used to enumerate registered emails.
export async function POST(request) {
  try {
    const body = await request.json();
    const { action } = body;

    if (action === 'request') {
      const { email } = body;
      if (!email) {
        return NextResponse.json({ error: 'Missing email' }, { status: 400 });
      }

      const genericResponse = NextResponse.json({
        success: true,
        message: "If an account exists for that email, we've sent a password reset link."
      });

      const users = await db.getUsers();
      const user = users.find(u => u.email.toLowerCase() === String(email).toLowerCase());
      // Same "don't leak which emails exist" response either way — the only
      // difference is whether we actually send anything.
      if (!user) return genericResponse;

      // Drop any earlier outstanding link for this account first, so only
      // the newest email's link ever works.
      await db.deletePasswordResetsForEmail(user.email);

      const token = generateOpaqueToken();
      const expiresAt = new Date(Date.now() + RESET_TTL_MS);
      const saved = await db.createPasswordReset(token, user.email, expiresAt);
      if (!saved) {
        // Database unreachable — still don't reveal account existence, but
        // this is a real failure worth logging (see CLAUDE.md's note on
        // treating a flaky MySQL connection as a bug, not a shrug).
        console.error('[forgot-password] Could not persist reset token (database unreachable) for', user.email);
        return genericResponse;
      }

      const siteUrl = process.env.SITE_URL || 'https://iekcorbar.com';
      const resetUrl = `${siteUrl}/reset-password?token=${token}`;
      const { subject, html, text } = passwordResetEmail({ resetUrl, expiresInMinutes: RESET_TTL_MS / 60000, siteUrl });

      // Sends from the dedicated noreply@ mailbox (same real separate Plesk
      // account the newsletter uses — see .env.local's NEWSLETTER_SMTP_USER)
      // rather than the shared bugs@ inbox sendMail() defaults to — a
      // password reset is a system/security email, not a bug report. Falls
      // back to that default if the account isn't configured, same as the
      // newsletter route.
      const account = process.env.NEWSLETTER_SMTP_USER ? {
        user: process.env.NEWSLETTER_SMTP_USER,
        password: process.env.NEWSLETTER_SMTP_PASSWORD,
        from: `"Iekcorbar" <${process.env.NEWSLETTER_SMTP_USER}>`
      } : undefined;

      try {
        await sendMail({ to: user.email, subject, html, text, account });
      } catch (mailErr) {
        // sendMail only throws when SMTP *is* configured but the send
        // itself failed (see src/lib/mail.js) — a real delivery failure,
        // not the "not configured" no-op. Still don't leak this to the
        // client; log it so it's diagnosable the same way other silent
        // mail failures in this codebase are.
        console.error('[forgot-password] Failed to send reset email to', user.email, mailErr);
      }

      return genericResponse;
    }

    if (action === 'reset') {
      const { token, newPassword } = body;
      if (!token || !newPassword) {
        return NextResponse.json({ error: 'Missing token or new password' }, { status: 400 });
      }
      if (newPassword.length < MIN_PASSWORD_LENGTH) {
        return NextResponse.json({ error: `New password must be at least ${MIN_PASSWORD_LENGTH} characters` }, { status: 400 });
      }

      const reset = await db.getPasswordResetByToken(token);
      if (!reset) {
        return NextResponse.json({ error: 'This reset link is invalid, expired, or already used. Request a new one.' }, { status: 400 });
      }

      const saved = await db.setUserPasswordHash(reset.userEmail, hashPassword(newPassword));
      if (!saved) {
        return NextResponse.json({ error: 'Could not save the new password (database unreachable). Please try again shortly.' }, { status: 500 });
      }

      await db.markPasswordResetUsed(token);
      await db.deletePasswordResetsForEmail(reset.userEmail);
      // Password just changed — anything logged in from before (a shared
      // device, or whoever needed this reset in the first place) shouldn't
      // stay logged in on the old password's session.
      await db.invalidateSessionsForEmail(reset.userEmail);

      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: 'Invalid action' }, { status: 400 });
  } catch (err) {
    console.error('Forgot-password endpoint error:', err);
    return NextResponse.json({ error: 'Server error' }, { status: 500 });
  }
}
