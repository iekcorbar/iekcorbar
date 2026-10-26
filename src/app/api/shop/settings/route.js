import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// The single global "how do we take a player's money" RCON template — see
// the design note on shop_items in schema.sql. Kept admin-only (not
// moderator), same precedent as api/site-content/route.js's PUT: a wrong
// edit here silently breaks every purchase on the shop at once (money not
// actually deducted, or deducted with no confirmation), unlike a wrong edit
// to a single item's own give command, so it gets the stricter gate.
export async function GET(request) {
  try {
    const auth = await requireRole(request, ['admin', 'moderator']);
    if (auth.error) return auth.error;
    const settings = await db.getShopSettings();
    return NextResponse.json({ settings });
  } catch (err) {
    console.error("Shop settings GET error:", err);
    return NextResponse.json({ error: "Failed to load shop settings" }, { status: 500 });
  }
}

// Accepts either/both fields — takeCommand and the on/off `enabled` toggle
// are edited from separate controls in the staff panel, and saveShopSettings
// merges rather than overwrites so setting one never blanks the other.
export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const { takeCommand, enabled } = await request.json();
    if (takeCommand !== undefined && (!takeCommand || !takeCommand.includes('{player}') || !takeCommand.includes('{amount}'))) {
      return NextResponse.json({ error: "takeCommand must include both {player} and {amount} placeholders" }, { status: 400 });
    }
    if (takeCommand === undefined && enabled === undefined) {
      return NextResponse.json({ error: "Nothing to save" }, { status: 400 });
    }

    const saved = await db.saveShopSettings({ takeCommand, enabled });
    if (!saved) {
      return NextResponse.json({ error: "Could not save settings (database unreachable). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'shop', action: 'update settings', target: 'Shop settings', detail: enabled !== undefined ? `enabled=${enabled}` : 'take command' });
    const settings = await db.getShopSettings();
    return NextResponse.json({ success: true, settings });
  } catch (err) {
    console.error("Shop settings POST error:", err);
    return NextResponse.json({ error: "Failed to save shop settings" }, { status: 500 });
  }
}
