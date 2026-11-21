import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

// FriendInvite (Minecraft plugin) derives its 6-character code from the
// player's UUID: uuid.toString().replace('-','').substring(0,6).toUpperCase().
// The website can therefore show the SAME code without any server round-trip
// and without a database migration. If the UUID is missing/stale, the client
// falls back to "/friend code" in-game (see src/app/invite/page.js).
const codeForUuid = (uuid) => String(uuid || '').replace(/-/g, '').slice(0, 6).toUpperCase();

export async function GET(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });

    const mcUsername = user.mcUsername;
    if (!mcUsername) return NextResponse.json({ code: null, reason: 'no-minecraft-name' });

    const bundle = await db.getPlayerDataBundle(mcUsername);
    const uuid = bundle && bundle.uuid;
    if (!uuid) return NextResponse.json({ code: null, reason: 'no-uuid' });

    const code = codeForUuid(uuid);
    const origin = new URL(request.url).origin;
    return NextResponse.json({
      code,
      mcUsername,
      shareUrl: `${origin}/welcome?ref=${code}`,
    });
  } catch (err) {
    console.error('invite GET error:', err);
    return NextResponse.json({ error: 'Failed to load your invite code' }, { status: 500 });
  }
}
