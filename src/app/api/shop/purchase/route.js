import { NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { sendRconCommand } from '@/lib/rcon';
import { ACHIEVEMENT_SLUGS } from '@/lib/achievements';

// This route moves real in-game currency, so — unlike several older routes
// in this codebase — it never trusts a client-sent role/username. The buyer
// is always sessionUser.mcUsername from the real session cookie (see
// src/lib/session.js), never anything the request body could claim to be.

function fillTemplate(template, vars) {
  let out = template;
  for (const [k, v] of Object.entries(vars)) {
    out = out.split(`{${k}}`).join(String(v));
  }
  return out;
}

// Avoids floating-point noise (e.g. "4.999999999999997") in the RCON
// command text without forcing a fake ".00" onto whole-number prices most
// economy commands won't expect.
function formatAmount(n) {
  return Number(n.toFixed(2)).toString();
}

// Live, uncached balance read — deliberately bypasses /api/player-stats'
// 60s cache, since a spend decision can't use a value that stale. Mirrors
// that route's own RCON-then-MySQL-fallback shape, except BYPASS (RCON not
// configured, local dev only) falls back to the DB mirror instead of being
// treated as unreachable, and any real RCON failure is a hard error rather
// than silently trusting a possibly-stale number for a money transaction.
async function getLiveBalance(username) {
  let result;
  try {
    result = await sendRconCommand(`webstats ${username}`);
  } catch (err) {
    return { error: `Could not reach the Minecraft server (${err.message || 'offline'}).` };
  }
  if (result === 'BYPASS') {
    const stats = await db.getPlayerStats(username);
    if (!stats) return { error: 'No player stats found for this account.' };
    return { balance: stats.balance || 0 };
  }
  try {
    const stats = JSON.parse(result);
    if (stats && !stats.error && typeof stats.balance === 'number') {
      return { balance: stats.balance };
    }
  } catch { /* fall through */ }
  return { error: 'Could not read a live balance from the server.' };
}

async function isPlayerOnline(username) {
  let result;
  try {
    result = await sendRconCommand('list');
  } catch (err) {
    return { error: `Could not reach the Minecraft server (${err.message || 'offline'}).` };
  }
  if (result === 'BYPASS') return { online: true };
  const online = !!(result && result.toLowerCase().includes(username.toLowerCase()));
  return { online };
}

export async function POST(request) {
  try {
    const sessionUser = await getSessionUser(request);
    if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    if (!sessionUser.mcUsername) {
      return NextResponse.json({ error: "Link your Minecraft account (IGN) on your profile before using the shop." }, { status: 400 });
    }
    const username = sessionUser.mcUsername;

    const settings = await db.getShopSettings();
    if (!settings.enabled) {
      return NextResponse.json({ error: "The shop is currently closed." }, { status: 403 });
    }

    const { itemId } = await request.json();
    if (!itemId) return NextResponse.json({ error: "Missing itemId" }, { status: 400 });

    const items = await db.getShopItems();
    const item = items.find(i => i.id === itemId);
    if (!item || !item.active) {
      return NextResponse.json({ error: "This item is not available." }, { status: 404 });
    }

    // 'website'-currency items (see shop_items.currency) spend the
    // website-only "Gears" balance (users.website_currency) instead of the
    // real in-game Vault balance — entirely decoupled from the Minecraft
    // server, so unlike everything below this branch, none of it touches
    // RCON: no online check, no live-balance read, no take command, no give
    // command. Added 2026-09-07 after Glass of Milk (a purely cosmetic
    // website effect) turned out to be unbuyable whenever the server's real
    // economy command was misconfigured or unreachable — a website-only
    // cosmetic shouldn't depend on either.
    if (item.currency === 'website') {
      const currentCurrency = await db.getUserWebsiteCurrency(sessionUser.email);
      if (currentCurrency < item.price) {
        return NextResponse.json({ error: `Not enough Gears. You have ${currentCurrency}, this costs ${item.price}.` }, { status: 402 });
      }
      const spent = await db.spendWebsiteCurrency(sessionUser.email, item.price);
      if (!spent) {
        return NextResponse.json({ error: "Could not spend Gears — your balance may have just changed. Please refresh and try again." }, { status: 409 });
      }

      const purchaseId = crypto.randomUUID();
      const takeMessage = '(paid with website Gears — no RCON involved)';
      const giveMessage = '(website-only item — nothing delivered in-game)';
      const saved = await db.createShopPurchase({
        id: purchaseId, username, itemId: item.id, itemName: item.name, pricePaid: item.price,
        status: 'delivered', takeMessage, giveMessage
      });
      if (!saved) {
        // Gears were already spent above — same "log loudly, don't reverse
        // a confirmed spend" stance as the real-money path below takes when
        // its own createShopPurchase call fails.
        console.error('[Shop] Website-currency purchase succeeded but could not be recorded in the database:', { purchaseId, username, itemId: item.id });
      }

      db.awardAchievement(username, ACHIEVEMENT_SLUGS.FIRST_SHOP_PURCHASE).catch(() => {});

      return NextResponse.json({
        success: true,
        purchaseId,
        status: 'delivered',
        willAutoDeliverOnJoin: false,
        takeMessage,
        giveMessage
      });
    }

    // Balance reads/writes go through Vault, which resolves an OfflinePlayer
    // fine (see WebStatsCommand.java's own balance lookup) — only the actual
    // item *delivery* (a `give`-style command) genuinely needs the player
    // online/loaded. So the purchase itself is no longer blocked by presence;
    // being offline just defers delivery (see below) instead of refusing the
    // sale outright. 2026-09-06: this used to hard-block the whole purchase
    // whenever the buyer was offline, which made a website-only item like
    // Glass of Milk impossible to buy unless you happened to be logged in.
    const onlineCheck = await isPlayerOnline(username);
    if (onlineCheck.error) return NextResponse.json({ error: onlineCheck.error }, { status: 502 });

    const before = await getLiveBalance(username);
    if (before.error) return NextResponse.json({ error: before.error }, { status: 502 });
    if (before.balance < item.price) {
      return NextResponse.json({ error: `Insufficient balance. You have ${before.balance}, this costs ${item.price}.` }, { status: 402 });
    }

    const takeCommand = fillTemplate(settings.takeCommand, { player: username, amount: formatAmount(item.price) });

    let takeMessage;
    try {
      const takeResult = await sendRconCommand(takeCommand);
      takeMessage = takeResult === 'BYPASS' ? '(RCON not configured — dev bypass)' : (takeResult || '(empty response)');
      console.log(`[Shop RCON take] "${takeCommand}" -> "${takeMessage}"`);
    } catch (err) {
      console.error('[Shop RCON take] failed:', err);
      return NextResponse.json({ error: `Could not reach the Minecraft server to charge you — nothing was taken. (${err.message || 'offline'})` }, { status: 502 });
    }

    // The take command's own template is a staff-editable best guess (see
    // shop_settings — the real syntax isn't confirmed anywhere in this
    // codebase), so its reply text alone isn't trustworthy. The only real
    // signal is the balance having actually dropped.
    const after = await getLiveBalance(username);
    const moneyConfirmedTaken = !after.error && after.balance <= before.balance - item.price + 0.0001;
    const purchaseId = crypto.randomUUID();

    if (!moneyConfirmedTaken) {
      await db.createShopPurchase({
        id: purchaseId, username, itemId: item.id, itemName: item.name, pricePaid: item.price,
        status: 'failed', takeMessage, giveMessage: null
      });
      return NextResponse.json({
        error: `Your balance doesn't appear to have changed, so nothing was delivered. Server replied: "${takeMessage}". Please report this to staff — the shop's money-take command may need adjusting in its Settings.`,
        takeMessage
      }, { status: 502 });
    }

    // Money confirmed taken — deliver the item. If the buyer is offline,
    // don't even attempt the give: a vanilla `give` against an offline name
    // typically still gets an RCON *response* (just an unhelpful in-game
    // failure message), it doesn't throw — so the try/catch below can't
    // reliably detect "failed because offline" from the response text alone.
    // Skip straight to 'queued' instead, same status as a give that failed
    // for some other reason; IekcorbarSiteSync's join listener
    // (processQueuedShopPurchases) delivers it automatically once they're
    // next online, no manual retry needed for this specific case.
    const giveCommand = fillTemplate(item.giveCommand, { player: username });
    let giveMessage;
    let status = 'delivered';
    if (!onlineCheck.online) {
      giveMessage = 'Player offline — will be delivered automatically the next time they join the server.';
      status = 'queued';
    } else {
      try {
        const giveResult = await sendRconCommand(giveCommand);
        giveMessage = giveResult === 'BYPASS' ? '(RCON not configured — dev bypass)' : (giveResult || '(empty response)');
        console.log(`[Shop RCON give] "${giveCommand}" -> "${giveMessage}"`);
      } catch (err) {
        console.error('[Shop RCON give] failed:', err);
        giveMessage = err.message || 'Minecraft server offline';
        status = 'queued';
      }
    }

    const saved = await db.createShopPurchase({
      id: purchaseId, username, itemId: item.id, itemName: item.name, pricePaid: item.price,
      status, takeMessage, giveMessage
    });
    if (!saved) {
      // Money was already taken and (probably) delivered in-game at this
      // point — losing the record is a bookkeeping problem, not a refund
      // situation, so this is logged loudly rather than surfaced as a user
      // facing error that might prompt a confusing repeat purchase attempt.
      console.error('[Shop] Purchase succeeded in-game but could not be recorded in the database:', { purchaseId, username, itemId: item.id, status });
    }

    // Money was confirmed taken above regardless of delivery status, so this
    // is a real purchase either way — award now rather than waiting on
    // eventual delivery (see src/lib/achievements.js).
    db.awardAchievement(username, ACHIEVEMENT_SLUGS.FIRST_SHOP_PURCHASE).catch(() => {});

    return NextResponse.json({
      success: true,
      purchaseId,
      status,
      // Distinguishes "queued because you were offline" (expected, delivers
      // itself automatically on your next join — see
      // MySQLSync.processQueuedShopPurchases) from "queued because the give
      // command itself failed while you were online" (a real problem, needs
      // the manual Retry Delivery button) — same `status` value for both, so
      // the frontend can't tell them apart from that alone.
      willAutoDeliverOnJoin: status === 'queued' && !onlineCheck.online,
      takeMessage,
      giveMessage,
      newBalance: after.error ? null : after.balance
    });
  } catch (err) {
    console.error("Shop purchase POST error:", err);
    return NextResponse.json({ error: "Failed to process purchase" }, { status: 500 });
  }
}
