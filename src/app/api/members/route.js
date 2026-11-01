import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { withRoleInfo } from '@/lib/permissions';
import { getCmiRanks } from '@/lib/cmi-db';
import { rankLabel } from '@/lib/ranks';

export async function GET() {
  try {
    const allUsers = await db.getUsers();
    // New accounts that haven't linked their Minecraft account yet (pending /
    // on hold) aren't members yet — hide them from the public list.
    const unlinked = new Set((await db.getLinkStates()).map(x => String(x.email).toLowerCase()));
    const users = (Array.isArray(allUsers) ? allUsers : []).filter(u => !unlinked.has(String(u.email).toLowerCase())).map(({ password, email, totpSecret, ...u }) => u); // never expose secrets (was leaking TOTP secrets publicly)
    // In-game rank from the CMI database (non-fatal: no rank shown if it is unreachable).
    const ranks = await getCmiRanks(users.map(u => u.mcUsername));
    const withRank = users.map(u => ({ ...u, gameRank: rankLabel(ranks[String(u.mcUsername || '').toLowerCase()]) }));
    return NextResponse.json({ users: await withRoleInfo(withRank) });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve members data" }, { status: 500 });
  }
}
