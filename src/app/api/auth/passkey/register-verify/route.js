import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { isStaffRole } from '@/lib/permissions';
import { checkRegistrationResponse } from '@/lib/webauthn';

import { logAudit } from '@/lib/audit';
const MAX_NAME_LENGTH = 60;

// Phase 2: verifies the attestation the browser produced against the
// challenge register-options just issued, then saves the credential.
export async function POST(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) {
      return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
    }
    if (!(await isStaffRole(user.role))) {
      return NextResponse.json({ error: 'Passkeys are only available for staff accounts.' }, { status: 403 });
    }

    const { name, response } = await request.json();
    if (!response) {
      return NextResponse.json({ error: 'Missing registration response' }, { status: 400 });
    }

    const pending = await db.getPasskeyChallenge(user.email);
    if (!pending || pending.expiresAt < Date.now()) {
      if (pending) await db.deletePasskeyChallenge(user.email);
      return NextResponse.json({ error: 'This registration attempt expired. Please try again.' }, { status: 400 });
    }

    let verification;
    try {
      verification = await checkRegistrationResponse(response, pending.challenge);
    } catch (err) {
      // The library throws (rather than returning verified:false) on a
      // malformed/tampered response — treated the same as "didn't verify".
      console.error('Passkey registration verify threw:', err);
      return NextResponse.json({ error: 'Could not verify that passkey. Please try again.' }, { status: 400 });
    }

    if (!verification.verified || !verification.registrationInfo) {
      return NextResponse.json({ error: 'Could not verify that passkey. Please try again.' }, { status: 400 });
    }

    await db.deletePasskeyChallenge(user.email);

    const { credential, credentialDeviceType, credentialBackedUp } = verification.registrationInfo;
    const saved = await db.savePasskeyCredential({
      id: credential.id,
      userEmail: user.email,
      publicKey: Buffer.from(credential.publicKey).toString('base64'),
      counter: credential.counter,
      deviceType: credentialDeviceType,
      backedUp: credentialBackedUp,
      transports: credential.transports,
      name: (name || '').trim().slice(0, MAX_NAME_LENGTH) || null
    });
    if (!saved) {
      return NextResponse.json({ error: 'Could not save the passkey (database unreachable). Please try again shortly.' }, { status: 500 });
    }

    await logAudit(user, { area: 'security', action: 'added a passkey', target: user.mcUsername || user.email });
    return NextResponse.json({ success: true, passkeys: await db.getPasskeysForUser(user.email) });
  } catch (err) {
    console.error('Passkey register-verify error:', err);
    return NextResponse.json({ error: 'Could not save the passkey' }, { status: 500 });
  }
}
