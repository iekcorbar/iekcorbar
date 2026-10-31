import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { sendRconCommand } from '@/lib/rcon';

function isStaff(user) {
  return !!user && (user.role === 'admin' || user.role === 'moderator');
}

// GET: the caller's own purchase history, or — for staff — every purchase
// on the shop (?all=true), so staff can spot 'queued' deliveries across all
// players without needing each affected player to report it themselves.
export async function GET(request) {
  try {
    const sessionUser = await getSessionUser(request);
    if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

    const { searchParams } = new URL(request.url);
    if (searchParams.get('all') === 'true') {
      if (!isStaff(sessionUser)) return NextResponse.json({ error: "Forbidden" }, { status: 403 });
      const purchases = await db.getAllShopPurchases();
      return NextResponse.json({ purchases });
    }

    if (!sessionUser.mcUsername) return NextResponse.json({ purchases: [] });
    const purchases = await db.getShopPurchasesByUsername(sessionUser.mcUsername);
    return NextResponse.json({ purchases });
  } catch (err) {
    console.error("Shop purchases GET error:", err);
    return NextResponse.json({ error: "Failed to load purchase history" }, { status: 500 });
  }
}

// PATCH: retry delivery for a 'queued' purchase (money already taken, the
// give command failed the first time), same shape as the vote-log PATCH
// claim endpoint; also the default for callers that don't send `action`.
// The buyer can retry their own; staff can retry anyone's. Never re-charges
// — only re-issues the give command.
export async function PATCH(request) {
  try {
    const sessionUser = await getSessionUser(request);
    if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

    const { purchaseId } = await request.json();
    if (!purchaseId) return NextResponse.json({ error: "Missing purchaseId" }, { status: 400 });

    const staff = isStaff(sessionUser);
    const purchases = staff ? await db.getAllShopPurchases() : await db.getShopPurchasesByUsername(sessionUser.mcUsername || '');
    const purchase = purchases.find(p => p.id === purchaseId);
    if (!purchase) return NextResponse.json({ error: "Purchase not found" }, { status: 404 });
    if (purchase.status !== 'queued') {
      return NextResponse.json({ error: `Nothing to retry — this purchase is '${purchase.status}'.` }, { status: 400 });
    }

    const items = await db.getShopItems();
    const item = items.find(i => i.id === purchase.itemId);
    if (!item) {
      return NextResponse.json({ error: "This item no longer exists in the shop, so it can't be re-delivered automatically. Please handle it manually." }, { status: 404 });
    }

    let listResult;
    try {
      listResult = await sendRconCommand('list');
    } catch (err) {
      return NextResponse.json({ error: `Could not reach the Minecraft server (${err.message || 'offline'}).` }, { status: 502 });
    }
    const online = listResult === 'BYPASS' || (listResult && listResult.toLowerCase().includes(purchase.username.toLowerCase()));
    if (!online) {
      return NextResponse.json({ error: `${purchase.username} needs to be online to retry delivery.` }, { status: 409 });
    }

    const giveCommand = item.giveCommand.split('{player}').join(purchase.username);
    let giveMessage;
    let status = 'delivered';
    try {
      const giveResult = await sendRconCommand(giveCommand);
      giveMessage = giveResult === 'BYPASS' ? '(RCON not configured — dev bypass)' : (giveResult || '(empty response)');
      console.log(`[Shop RCON retry] "${giveCommand}" -> "${giveMessage}"`);
    } catch (err) {
      console.error('[Shop RCON retry] failed:', err);
      giveMessage = err.message || 'Minecraft server offline';
      status = 'queued';
    }

    await db.updateShopPurchaseStatus(purchaseId, status, giveMessage);
    return NextResponse.json({ success: true, status, giveMessage });
  } catch (err) {
    console.error("Shop purchases PATCH error:", err);
    return NextResponse.json({ error: "Failed to retry delivery" }, { status: 500 });
  }
}
