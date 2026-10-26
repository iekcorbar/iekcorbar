import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import {
  getStoryAccess, publicStoryAccess, hashGuestToken, setStoryGuestCookie, clearStoryGuestCookie
} from '@/lib/story-access';

// Story Room guest sign-in (see src/lib/story-access.js).
//   POST { key }  — exchange the secret from a guest link for the guest
//                   cookie; the /story-room page calls this once and then
//                   strips the key from the address bar.
//   GET           — who am I here? Works for staff sessions too, so staff
//                   can use the /story-room page as well.
//   DELETE        — "Leave": forget this browser's guest access.

export async function GET(request) {
  const access = await getStoryAccess(request);
  if (!access) return NextResponse.json({ error: 'No Story Room access.' }, { status: 401 });
  return NextResponse.json({ access: publicStoryAccess(access) });
}

export async function POST(request) {
  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }
  const key = String(body.key || '').trim();
  if (!key || key.length > 200) return NextResponse.json({ error: 'This link is not valid.' }, { status: 400 });

  const link = await db.getActiveStoryGuestLinkByHash(hashGuestToken(key));
  if (!link) {
    return NextResponse.json({ error: 'This guest link is no longer valid — it may have expired or been switched off. Ask the iekcorbar staff for a new one.' }, { status: 401 });
  }
  const response = NextResponse.json({ success: true, name: link.name, access: link.access });
  return setStoryGuestCookie(response, key, link.expiresAt);
}

export async function DELETE() {
  return clearStoryGuestCookie(NextResponse.json({ success: true }));
}
