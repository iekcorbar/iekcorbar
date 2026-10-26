import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { logAudit } from '@/lib/audit';
import {
  requireStoryAccess, generateGuestToken, hashGuestToken, cleanGuestName, STORY_GUEST_LEVELS
} from '@/lib/story-access';

// Story Room → Guest links (staff only — guests can never reach this, see
// requireStoryAccess's 'manage' level). The secret is returned exactly once,
// at creation; only its hash is stored, so a lost link can't be shown again
// — switch it off and make a new one instead.

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';
const EXPIRY_DAYS = { '7': 7, '30': 30, '90': 90, never: null };

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'manage');
  if (auth.error) return auth.error;
  const links = await db.listStoryGuestLinks();
  if (!links) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ links: links.map(l => ({ ...l, revoked: !!l.revoked })) });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'manage');
  if (auth.error) return auth.error;

  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }
  const name = cleanGuestName(body.name);
  if (!name) return NextResponse.json({ error: 'Enter the name of the person this link is for.' }, { status: 400 });
  const access = STORY_GUEST_LEVELS.includes(body.access) ? body.access : 'edit';
  const days = Object.prototype.hasOwnProperty.call(EXPIRY_DAYS, String(body.expiry)) ? EXPIRY_DAYS[String(body.expiry)] : 30;
  const expiresAt = days ? new Date(Date.now() + days * 24 * 3600 * 1000) : null;

  const token = generateGuestToken();
  const id = await db.createStoryGuestLink({ tokenHash: hashGuestToken(token), name, access, expiresAt, actor: auth.access.actor });
  if (!id) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  await logAudit({ mcUsername: auth.access.actor, role: auth.access.isGuest ? 'story-guest' : auth.user?.role }, { area: 'story', action: 'create guest link', target: name, detail: `access: ${access}, expires: ${expiresAt ? expiresAt.toISOString().slice(0, 10) : 'never'}` });
  return NextResponse.json({ success: true, id, token, name, access, expiresAt });
}

export async function DELETE(request) {
  const auth = await requireStoryAccess(request, 'manage');
  if (auth.error) return auth.error;
  const id = new URL(request.url).searchParams.get('id');
  if (!id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
  const ok = await db.revokeStoryGuestLink(id);
  if (!ok) return NextResponse.json({ error: 'Could not switch this link off (not found, or the database is unreachable).' }, { status: 500 });
  await logAudit({ mcUsername: auth.access.actor, role: auth.access.isGuest ? 'story-guest' : auth.user?.role }, { area: 'story', action: 'revoke guest link', target: `link ${id}` });
  return NextResponse.json({ success: true });
}
