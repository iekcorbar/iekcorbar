import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';
import { sendRconCommand } from '@/lib/rcon';

import { logAudit } from '@/lib/audit';
// 3 months, matching the retention policy this route enforces.
const INACTIVITY_DAYS = 90;

function cutoffDate() {
  const d = new Date();
  d.setDate(d.getDate() - INACTIVITY_DAYS);
  return d;
}

// GET: staff-only preview — lists candidates, deletes nothing. See the
// "Account Cleanup" tab in the moderation panel, which drives this.
export async function GET(request) {
  const auth = await requirePermission(request, 'settings_cleanup');
  if (auth.error) return auth.error;

  try {
    const candidates = await db.getInactiveAccountCandidates(cutoffDate());
    return NextResponse.json({ candidates, inactivityDays: INACTIVITY_DAYS });
  } catch (err) {
    return NextResponse.json({ error: "Failed to load inactive account candidates" }, { status: 500 });
  }
}

// POST: actually purges the accounts a staff member selected. Every
// requested email is re-checked against a fresh candidate query (not just
// trusted from the request body) so a stale preview, or a tampered
// request, can't be used to delete an account that has since logged back
// in or that belongs to staff.
export async function POST(request) {
  const auth = await requirePermission(request, 'settings_cleanup');
  if (auth.error) return auth.error;

  try {
    const { emails } = await request.json();
    if (!Array.isArray(emails) || emails.length === 0) {
      return NextResponse.json({ error: "No accounts selected" }, { status: 400 });
    }

    const candidates = await db.getInactiveAccountCandidates(cutoffDate());
    const candidateMap = new Map(candidates.map(c => [c.email.toLowerCase(), c]));

    const results = [];
    for (const rawEmail of emails) {
      const email = String(rawEmail || '').toLowerCase();
      const candidate = candidateMap.get(email);
      if (!candidate) {
        results.push({ email: rawEmail, success: false, error: "No longer an inactivity candidate (skipped)" });
        continue;
      }

      let rconBypassed = false;
      if (candidate.mcUsername) {
        try {
          const result = await sendRconCommand(`whitelist remove ${candidate.mcUsername}`);
          rconBypassed = result === 'BYPASS';
        } catch (rconErr) {
          console.error(`Failed to remove ${candidate.mcUsername} from whitelist:`, rconErr);
          await logAudit(auth.user, { area: 'settings_cleanup', action: 'purge inactive account', target: candidate.mcUsername || 'player account' });
          results.push({ email: candidate.email, mcUsername: candidate.mcUsername, success: false, error: "Could not reach the Minecraft server to update the whitelist — account was not deleted." });
          continue;
        }
      }

      const deleted = await db.deleteUserAccount(candidate.email);
      if (!deleted) {
        results.push({ email: candidate.email, mcUsername: candidate.mcUsername, success: false, error: "Removed from whitelist, but failed to delete the account record." });
        continue;
      }

      await db.addModerationLog({
        username: candidate.mcUsername || candidate.email,
        type: 'account_purge',
        reason: `Inactive ${INACTIVITY_DAYS}+ days (auto-purge). Last activity: ${candidate.lastActive || 'never recorded, account created ' + candidate.createdAt}.`,
        staffMember: auth.user.mcUsername || auth.user.email
      });

      results.push({ email: candidate.email, mcUsername: candidate.mcUsername, success: true, rconBypassed });
    }

    return NextResponse.json({ success: true, results });
  } catch (err) {
    return NextResponse.json({ error: "Failed to purge inactive accounts" }, { status: 500 });
  }
}
