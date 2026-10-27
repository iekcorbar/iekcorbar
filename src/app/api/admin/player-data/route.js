import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

// Staff Panel > Player Data (see STAFF_TABS key 'player_data'): one combat
// profile lookup — EliteMobs' PlayerData row + MMOCore's progression row for
// a single username, read straight from the plugin DB (src/lib/plugin-data-db.js,
// read-only). Nothing is returned until staff explicitly searches a name, and
// the whole route is permission-gated, so the tables are never reachable
// without a granted 'player_data' tab.
export async function GET(request) {
  try {
    const auth = await requirePermission(request, 'player_data');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const name = String(searchParams.get('name') || '').trim();
    if (!/^[A-Za-z0-9_]{2,16}$/.test(name)) {
      return NextResponse.json({ error: 'Provide a valid Minecraft username.' }, { status: 400 });
    }

    const profile = await db.getCombatProfile(name);
    if (profile.error === 'db-unreachable') {
      return NextResponse.json({ error: 'The plugin database is unreachable right now.' }, { status: 503 });
    }
    return NextResponse.json(profile);
  } catch (err) {
    console.error('Player data GET error:', err);
    return NextResponse.json({ error: 'Failed to load player data' }, { status: 500 });
  }
}
