import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';
import { sendRconCommand } from '@/lib/rcon';

import { logAudit } from '@/lib/audit';
// Staff-initiated account erasure — one of two mechanisms behind the
// Privacy Policy's "Right to be Forgotten" promise (see providers.js's GDPR
// modal); the other is self-service, see api/account's DELETE, which anyone
// (staff included) can use on their own account without asking staff to run
// this route for them. Before either existed, that promise had no real
// action behind it for an *active* account at all — only
// api/admin/inactive-accounts could delete a user, and only ones already
// flagged inactive.
//
// This one stays deliberately admin-only via requireRole (not
// requirePermission on the 'moderation' tab) since it targets *other*
// people's accounts and is irreversible — broader than anything a moderator
// otherwise gets to do. Mirrors inactive-accounts' delete sequence
// (whitelist removal first, then the DB row, aborting rather than leaving
// them out of sync if RCON fails).
export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const { targetEmail } = await request.json();
    if (!targetEmail) {
      return NextResponse.json({ error: "Missing targetEmail" }, { status: 400 });
    }

    const users = await db.getUsers();
    const target = users.find(u => u.email.toLowerCase() === targetEmail.toLowerCase());
    if (!target) {
      return NextResponse.json({ error: "No such account" }, { status: 404 });
    }
    if (target.email.toLowerCase() === auth.user.email.toLowerCase()) {
      return NextResponse.json({ error: "You can't delete your own account this way — use Delete Account on your own Profile page instead." }, { status: 400 });
    }
    if (target.role === 'admin') {
      return NextResponse.json({ error: "Admin accounts can't be deleted from here." }, { status: 400 });
    }

    let rconBypassed = false;
    if (target.mcUsername) {
      try {
        const result = await sendRconCommand(`whitelist remove ${target.mcUsername}`);
        rconBypassed = result === 'BYPASS';
      } catch (rconErr) {
        console.error(`Failed to remove ${target.mcUsername} from whitelist:`, rconErr);
        return NextResponse.json({
          error: "Could not reach the Minecraft server to update the whitelist — account was not deleted."
        }, { status: 502 });
      }
    }

    const deleted = await db.deleteUserAccount(target.email);
    if (!deleted) {
      return NextResponse.json({
        error: "Removed from whitelist, but failed to delete the account record. Please try again — the whitelist removal won't be repeated if you retry."
      }, { status: 500 });
    }

    await db.addModerationLog({
      username: target.mcUsername || target.email,
      type: 'account_purge',
      reason: `Account permanently deleted at the player's request (GDPR erasure).`,
      staffMember: auth.user.mcUsername || auth.user.email
    });

    await logAudit(auth.user, { area: 'gdpr', action: 'delete account', target: target.mcUsername || 'player account' });
    return NextResponse.json({ success: true, rconBypassed });
  } catch (err) {
    console.error('GDPR delete error:', err);
    return NextResponse.json({ error: "Failed to delete account" }, { status: 500 });
  }
}
