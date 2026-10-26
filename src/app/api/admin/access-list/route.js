import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

// Staff-only view of the server's real ban list and whitelist (mirrored
// from banned-players.json/whitelist.json by AccessListSync.java) — never
// exposed to the public /members page, which only ever lists real website
// accounts. Gated by the 'access' tab (see Role Manager), real session, not
// client-asserted.
export async function GET(request) {
  try {
    const auth = await requirePermission(request, 'access');
    if (auth.error) return auth.error;

    const [banned, whitelistedUnregistered, whitelistedAll] = await Promise.all([
      db.getBannedPlayers(),
      db.getUnregisteredWhitelistedPlayers(),
      db.getWhitelistedPlayers()
    ]);

    return NextResponse.json({
      banned,
      whitelistedUnregistered,
      whitelistedTotal: whitelistedAll.length
    });
  } catch (err) {
    console.error("Access list GET error:", err);
    return NextResponse.json({ error: "Failed to load access list" }, { status: 500 });
  }
}
