import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser, requireRole } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// Catalog CRUD, colocated with the public /shop page like the other site
// feature APIs. Unlike the /projects API (which still trusts a client-sent
// `role` field), this uses the real session check throughout since it gates
// real in-game item delivery.
function isStaff(user) {
  return !!user && (user.role === 'admin' || user.role === 'moderator');
}

// GET is public (the shop needs to be browsable while logged out), but only
// ever returns inactive items to staff — a logged-out visitor or player
// should never see an item mid-edit/disabled by staff.
// `enabled` is included here (not just the staff-only settings route) since
// every visitor — logged in or not — needs to know whether the shop is
// currently open to show the right UI, not just staff.
export async function GET(request) {
  try {
    const sessionUser = await getSessionUser(request);
    const items = await db.getShopItems();
    const visible = isStaff(sessionUser) ? items : items.filter(i => i.active);
    const settings = await db.getShopSettings();
    return NextResponse.json({ items: visible, enabled: settings.enabled });
  } catch (err) {
    console.error("Shop items GET error:", err);
    return NextResponse.json({ error: "Failed to load shop items" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin', 'moderator']);
    if (auth.error) return auth.error;

    const body = await request.json();
    const { id, slug, name, description, price, giveCommand, imageUrl, active, currency } = body;
    const isWebsiteCurrency = currency === 'website';

    if (!slug || !name || price === undefined || price === null) {
      return NextResponse.json({ error: "slug, name and price are required" }, { status: 400 });
    }
    // A 'website' (Gears) item is never delivered in-game — no RCON contact
    // at all, see api/shop/purchase — so it has no give command to require.
    // A 'balance' item still spends real Vault currency and needs one.
    if (!isWebsiteCurrency && !giveCommand) {
      return NextResponse.json({ error: "giveCommand is required for items priced in real balance" }, { status: 400 });
    }
    const numericPrice = Number(price);
    if (!Number.isFinite(numericPrice) || numericPrice < 0) {
      return NextResponse.json({ error: "price must be a non-negative number" }, { status: 400 });
    }
    if (!isWebsiteCurrency && !giveCommand.includes('{player}')) {
      return NextResponse.json({ error: "giveCommand must include a {player} placeholder" }, { status: 400 });
    }

    const saved = await db.saveShopItem({
      id, slug, name, description, price: numericPrice, giveCommand: isWebsiteCurrency ? null : giveCommand, imageUrl,
      active: active !== undefined ? !!active : true,
      currency: isWebsiteCurrency ? 'website' : 'balance'
    });
    if (!saved) {
      return NextResponse.json({ error: "Could not save item (database unreachable). Please try again shortly." }, { status: 500 });
    }

    await logAudit(auth.user, { area: 'shop', action: id ? 'update item' : 'create item', target: name });
    const items = await db.getShopItems();
    return NextResponse.json({ success: true, items });
  } catch (err) {
    console.error("Shop items POST error:", err);
    return NextResponse.json({ error: "Failed to save shop item" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requireRole(request, ['admin', 'moderator']);
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

    await db.deleteShopItem(id);
    await logAudit(auth.user, { area: 'shop', action: 'delete item', target: `item ${id}` });
    const items = await db.getShopItems();
    return NextResponse.json({ success: true, items });
  } catch (err) {
    console.error("Shop items DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete shop item" }, { status: 500 });
  }
}
