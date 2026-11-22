import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';
import { logAudit } from '@/lib/audit';
import { sendRconCommand } from '@/lib/rcon';
import {
  composeGazette, gazetteCommands, getGazetteStatus, markGazettePublished,
} from '@/lib/gazette';

// Publish the weekly Gazette. Two callers:
//  1. Staff Panel → News (session + 'news' permission, optional force: true)
//  2. The plugin's Sunday scheduler (Bearer GAZETTE_SECRET, no session)
// Both go through the same compose → queue → push → audit path. Publishing is
// idempotent per ISO week: a repeat call without force is a no-op.

function hasPluginSecret(request) {
  const secret = process.env.GAZETTE_SECRET;
  if (!secret) return false;
  return (request.headers.get('authorization') || '') === `Bearer ${secret}`;
}

async function authorize(request) {
  if (hasPluginSecret(request)) return { user: { email: 'gazette-scheduler', role: 'system' }, automatic: true };
  const auth = await requirePermission(request, 'news');
  if (auth.error) return { error: auth.error };
  return { user: auth.user, automatic: false };
}

export async function GET(request) {
  try {
    const auth = await requirePermission(request, 'news');
    if (auth.error) return auth.error;
    return NextResponse.json(await getGazetteStatus());
  } catch (err) {
    return NextResponse.json({ error: err?.message || 'Failed to load Gazette status' }, { status: 500 });
  }
}

export async function POST(request) {
  let auth;
  try {
    auth = await authorize(request);
  } catch (err) {
    return NextResponse.json({ error: 'Authentication failed' }, { status: 500 });
  }
  if (auth.error) return auth.error;

  try {
    let body = {};
    try { body = await request.json(); } catch { /* empty body is fine */ }

    const status = await getGazetteStatus();
    if (status.lastIssue === status.currentWeek && !body.force) {
      return NextResponse.json({
        success: true,
        alreadyPublished: true,
        message: `Issue ${status.currentWeek} was already published. Send force: true to replace it.`,
        ...status,
      });
    }

    const edition = await composeGazette();
    const commands = gazetteCommands(edition);

    // Sequential on purpose: the queue processes rows in id order, and the
    // book must land before the Discord announcement goes out.
    let bookReply;
    try {
      bookReply = await sendRconCommand(commands[0]);
    } catch (err) {
      return NextResponse.json({
        error: `The Gazette could not be delivered to the server: ${err?.message || err}`,
      }, { status: 502 });
    }
    let discordReply = null;
    try {
      discordReply = await sendRconCommand(commands[1]);
    } catch (err) {
      // A dead DiscordSRV must not undo an otherwise published edition.
      console.warn('[Gazette] Discord broadcast failed:', err?.message || err);
    }

    await markGazettePublished(edition);

    // Bell entry + app push for every registered user (same routine the site
    // uses for events, wiki pages, etc.). A notification hiccup is not allowed
    // to fail a publish that already reached the server.
    try {
      const users = await db.getUsers();
      const message = `The Iekcorbar Gazette #${edition.week} is out — read it at the spawn lectern or on /news`;
      await Promise.allSettled((users || [])
        .filter((u) => u?.email)
        .map((u) => db.createUserNotification({
          recipientEmail: u.email, type: 'news', message, link: '/news',
        })));
    } catch (err) {
      console.warn('[Gazette] Notification broadcast failed:', err?.message || err);
    }

    await logAudit(auth.user, {
      area: 'news',
      action: body.force ? 'republish gazette' : 'publish gazette',
      target: `Issue ${edition.week} (${edition.key})`,
      detail: `${edition.pages} pages, ${edition.postsUsed}/${edition.postsAvailable} posts`,
    });

    return NextResponse.json({
      success: true,
      alreadyPublished: false,
      issue: edition.week,
      week: edition.key,
      pages: edition.pages,
      postsUsed: edition.postsUsed,
      postsAvailable: edition.postsAvailable,
      bookReply,
      discordReply,
    });
  } catch (err) {
    console.error('[Gazette] Publish failed:', err);
    return NextResponse.json({ error: err?.message || 'Failed to publish Gazette' }, { status: 500 });
  }
}
