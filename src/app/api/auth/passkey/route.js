import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// Self-service passkey management (list / remove your own) — see
// register-options and register-verify for adding a new one, and
// api/auth/login's passkey_auth_* actions for using one to sign in.
// Anyone with a real session can call this, but only ever sees/touches
// their own credentials; there's no admin-facing "manage someone else's
// passkeys" surface.

export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) {
    return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
  }
  const passkeys = await db.getPasskeysForUser(user.email);
  return NextResponse.json({ passkeys });
}

export async function DELETE(request) {
  const user = await getSessionUser(request);
  if (!user) {
    return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
  }
  const { searchParams } = new URL(request.url);
  const id = searchParams.get('id');
  if (!id) {
    return NextResponse.json({ error: 'Missing id' }, { status: 400 });
  }

  // Ownership check before deleting — the id is a credential ID, not scoped
  // to a user in the query itself, so this has to be verified explicitly.
  const credential = await db.getPasskeyCredentialById(id);
  if (!credential || credential.userEmail.toLowerCase() !== user.email.toLowerCase()) {
    return NextResponse.json({ error: 'Passkey not found' }, { status: 404 });
  }

  await db.deletePasskeyCredential(id);
  await logAudit(user, { area: 'security', action: 'removed a passkey', target: user.mcUsername || user.email });
  return NextResponse.json({ success: true, passkeys: await db.getPasskeysForUser(user.email) });
}
