import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { isStaffRole } from '@/lib/permissions';
import { buildRegistrationOptions } from '@/lib/webauthn';

// Phase 1 of adding a passkey (Profile > Account Security > Manage
// Passkeys): generates a WebAuthn challenge for the browser's
// navigator.credentials.create() call. Staff-only — passkeys exist purely
// to speed up the TOTP step (see src/lib/totp.js), which only staff ever
// go through, so offering this to a plain player would be a dead control.
export async function POST(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) {
      return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
    }
    if (!(await isStaffRole(user.role))) {
      return NextResponse.json({ error: 'Passkeys are only available for staff accounts.' }, { status: 403 });
    }

    const existing = await db.getPasskeysForUser(user.email);
    const { options, expiresAt } = await buildRegistrationOptions(user, existing);
    await db.savePasskeyChallenge(user.email, { challenge: options.challenge, expiresAt });

    return NextResponse.json({ options });
  } catch (err) {
    console.error('Passkey register-options error:', err);
    return NextResponse.json({ error: 'Could not start passkey registration' }, { status: 500 });
  }
}
