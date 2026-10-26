import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';
import { sendRconCommand } from '@/lib/rcon';

import { logAudit } from '@/lib/audit';
// The server runs CMI, which registers its own kick/ban/tempban/unban
// commands under the `cmi <subcommand>` router rather than overriding the
// vanilla `/kick` and `/ban` commands directly — plain `kick <player>
// <reason>` over RCON connects and "succeeds" (empty response, no error)
// but silently does nothing, confirmed by cross-checking the live server
// console log against an RCON round-trip during initial wiring.
const RCON_TEMPBAN_DURATION = '7d';
const RCON_PROTECTED_TYPES = ['kick', 'ban', 'tempban'];

function buildRconCommand(type, targetUsername, reason) {
  switch (type) {
    case 'kick': return `cmi kick ${targetUsername} ${reason}`;
    case 'ban': return `cmi ban ${targetUsername} ${reason}`;
    case 'tempban': return `cmi tempban ${targetUsername} ${RCON_TEMPBAN_DURATION} ${reason}`;
    case 'unban': return `cmi unban ${targetUsername}`;
    default: return null;
  }
}

// This route is the first one migrated off the client-asserted `role`
// pattern (see CLAUDE.md "Auth model") onto a real, server-side session
// check instead of trusting whatever `role` the client sent in the query
// string/body — now via requirePermission('moderation'), so Role Manager's
// per-role tab toggle (see src/lib/permissions.js) actually controls who
// can call this, not just whichever roles were hardcoded here. The other
// ~16 role-gated API routes still use the old client-asserted pattern —
// see the iekcorbar-project-context memory for the rest of that migration.
export async function GET(request) {
  try {
    const auth = await requirePermission(request, 'moderation');
    if (auth.error) return auth.error;

    const rawUsers = await db.getUsers();
    // Account-link state for new registrations (pending / on_hold + deadline).
    const linkStates = new Map((await db.getLinkStates()).map(x => [String(x.email).toLowerCase(), x]));
    const users = rawUsers.map(({ password, totpSecret, ...u }) => {
      const ls = linkStates.get(String(u.email).toLowerCase());
      return ls ? { ...u, linkState: ls.status, linkDeadline: ls.deadline } : u;
    }); // omit secrets
    const logs = await db.getModerationLogs();
    // Registrations that whitelisted + got a code sent but haven't verified
    // it in-game yet — not real accounts, but staff want to see them as
    // "pending members" in the same list rather than invisible until they
    // either finish or silently expire. See CLAUDE.md registration handshake.
    const pendingRegistrations = await db.getAllPendingRegistrations();

    return NextResponse.json({ users, logs, pendingRegistrations });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve moderation data" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'moderation');
    if (auth.error) return auth.error;
    const { role } = auth.user;

    const { targetUsername, type, reason, staffMember, action, targetEmail, newRole, newIGN } = await request.json();

    // Check if we are updating a user profile
    if (action === 'update_profile') {
      if (!targetEmail) {
        return NextResponse.json({ error: "Missing target user email" }, { status: 400 });
      }
      if (newRole) {
        // Only admin can change roles
        if (role !== 'admin') {
          return NextResponse.json({ error: "Only admins can change roles" }, { status: 403 });
        }
        await db.updateUserRole(targetEmail, newRole);
        await logAudit(auth.user, { area: 'members', action: 'change role', target: targetEmail, detail: newRole });
      }
      if (newIGN !== undefined) {
        await db.updateUserIGN(targetEmail, newIGN);
        await logAudit(auth.user, { area: 'members', action: 'change in-game name', target: targetEmail, detail: newIGN });
      }
      const updatedUsers = await db.getUsers();
      return NextResponse.json({ success: true, users: updatedUsers.map(({ password, totpSecret, ...u }) => u) });
    }

    // Otherwise, we are writing a moderation log (kick/ban/tempban/unban/note)
    if (!targetUsername || !type || !reason) {
      return NextResponse.json({ error: "Missing log details" }, { status: 400 });
    }

    // kick/ban/tempban/unban actually dispatch a CMI RCON command instead of
    // just writing a log entry — see src/lib/rcon.js. If the Minecraft
    // server is unreachable, the action is refused rather than silently
    // logged as if it happened (staff would otherwise see a log entry for
    // something that never actually occurred in-game).
    let rconMessage = null;
    let rconBypassed = false;
    const rconCommand = buildRconCommand(type, targetUsername, reason);

    if (rconCommand) {
      if (RCON_PROTECTED_TYPES.includes(type)) {
        const allUsers = await db.getUsers();
        const targetUser = allUsers.find(u => u.mcUsername && u.mcUsername.toLowerCase() === targetUsername.toLowerCase());
        if (targetUser && (targetUser.role === 'admin' || targetUser.role === 'moderator')) {
          return NextResponse.json({ error: "Staff members are protected and cannot be kicked or banned." }, { status: 403 });
        }
      }

      try {
        const result = await sendRconCommand(rconCommand);
        if (result === 'BYPASS') {
          rconBypassed = true; // RCON not configured (local dev) — logged only, nothing sent in-game
        } else {
          rconMessage = result;
        }
      } catch (rconErr) {
        console.error(`RCON ${type} command failed:`, rconErr);
        return NextResponse.json({
          error: `Could not reach the Minecraft server to execute the ${type}. The action was not applied or logged — please try again shortly.`
        }, { status: 502 });
      }
    }

    const log = {
      username: targetUsername,
      type,
      reason,
      staffMember: staffMember || "Staff"
    };

    await db.addModerationLog(log);
    await logAudit(auth.user, { area: 'moderation', action: type, target: targetUsername, detail: reason });
    const updatedLogs = await db.getModerationLogs();

    return NextResponse.json({ success: true, logs: updatedLogs, rconMessage, rconBypassed });
  } catch (err) {
    return NextResponse.json({ error: "Failed to submit moderation action" }, { status: 500 });
  }
}
