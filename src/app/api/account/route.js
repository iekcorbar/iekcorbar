import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser, SESSION_COOKIE, destroySession, clearSessionCookie } from '@/lib/session';
import { hashPassword, isHashedPassword, verifyPassword } from '@/lib/password';
import { sendRconCommand } from '@/lib/rcon';

import { logAudit } from '@/lib/audit';
import { isStaffRole } from '@/lib/permissions';
const MIN_PASSWORD_LENGTH = 8;

// Verifies `candidate` against a stored password value the same way the
// login route does — a real scrypt hash if this account has logged in since
// hashing shipped (src/lib/password.js), or a straight string compare
// against a legacy plaintext password otherwise. Unlike login, a correct
// legacy match here does NOT upgrade the hash — the password PUT below
// replaces it with a real hash anyway, and DELETE has nothing left to
// upgrade once the account is gone.
function checkPassword(candidate, stored) {
  return isHashedPassword(stored) ? verifyPassword(candidate, stored) : stored === candidate;
}

// Self-service password change. Previously nobody — player or staff — had
// any way to change their own password after registration; the only path
// that ever touched `users.password` was the one-time legacy-plaintext
// upgrade baked into login. Requires the current password, same as any
// other "change password" form.
export async function PUT(request) {
  try {
    const sessionUser = await getSessionUser(request);
    if (!sessionUser) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }

    const { currentPassword, newPassword } = await request.json();
    if (!currentPassword || !newPassword) {
      return NextResponse.json({ error: "Missing current or new password" }, { status: 400 });
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      return NextResponse.json({ error: `New password must be at least ${MIN_PASSWORD_LENGTH} characters` }, { status: 400 });
    }

    const users = await db.getUsers();
    const user = users.find(u => u.email.toLowerCase() === sessionUser.email.toLowerCase());
    if (!user) {
      return NextResponse.json({ error: "Account no longer exists" }, { status: 404 });
    }

    if (!checkPassword(currentPassword, user.password)) {
      return NextResponse.json({ error: "Current password is incorrect" }, { status: 401 });
    }

    const saved = await db.setUserPasswordHash(user.email, hashPassword(newPassword));
    if (!saved) {
      return NextResponse.json({ error: "Could not save the new password (database unreachable). Please try again shortly." }, { status: 500 });
    }

    if (await isStaffRole(user.role)) await logAudit(user, { area: 'security', action: 'changed own password', target: user.mcUsername || user.email });
    return NextResponse.json({ success: true });
  } catch (err) {
    console.error("Change password error:", err);
    return NextResponse.json({ error: "Failed to change password" }, { status: 500 });
  }
}

// Self-service "Right to be Forgotten" — the Privacy Policy has always
// promised this (see providers.js's GDPR modal), but the only mechanism
// behind it was api/admin/gdpr-delete: staff-initiated, admin-only, and
// explicitly refuses to target the acting admin's own account or any other
// admin account. That left every user, staff included, with no way to
// delete their own account without asking someone else to do it via
// Discord. This lets anyone do it themselves, gated only by re-entering
// their password (irreversible action, same confirmation shape as the
// password change above). Mirrors gdpr-delete's own delete sequence
// (whitelist removal first, then the DB row, aborting rather than leaving
// them out of sync if RCON fails) and cascade (see db.deleteUserAccount).
export async function DELETE(request) {
  try {
    const sessionUser = await getSessionUser(request);
    if (!sessionUser) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }

    const { password } = await request.json();
    if (!password) {
      return NextResponse.json({ error: "Password confirmation required" }, { status: 400 });
    }

    const users = await db.getUsers();
    const user = users.find(u => u.email.toLowerCase() === sessionUser.email.toLowerCase());
    if (!user) {
      return NextResponse.json({ error: "Account no longer exists" }, { status: 404 });
    }

    if (!checkPassword(password, user.password)) {
      return NextResponse.json({ error: "Incorrect password" }, { status: 401 });
    }

    // Not something the user asked to be blocked specifically, but letting
    // the very last admin delete themselves would leave the site with zero
    // admin accounts and no way to grant one back except by hand in the
    // database — refuse rather than risk that.
    if (user.role === 'admin') {
      const otherAdmins = users.filter(u => u.role === 'admin' && u.email.toLowerCase() !== user.email.toLowerCase());
      if (otherAdmins.length === 0) {
        return NextResponse.json({ error: "You're the only admin account — promote another account to admin first, then you can delete this one." }, { status: 400 });
      }
    }

    let rconBypassed = false;
    if (user.mcUsername) {
      try {
        const result = await sendRconCommand(`whitelist remove ${user.mcUsername}`);
        rconBypassed = result === 'BYPASS';
      } catch (rconErr) {
        console.error(`Failed to remove ${user.mcUsername} from whitelist during self-delete:`, rconErr);
        return NextResponse.json({
          error: "Could not reach the Minecraft server to update the whitelist — account was not deleted."
        }, { status: 502 });
      }
    }

    const deleted = await db.deleteUserAccount(user.email);
    if (!deleted) {
      return NextResponse.json({
        error: "Removed from whitelist, but failed to delete the account record. Please try again — the whitelist removal won't be repeated if you retry."
      }, { status: 500 });
    }

    await db.addModerationLog({
      username: user.mcUsername || user.email,
      type: 'account_purge',
      reason: 'Account permanently deleted by the player themself (self-service GDPR erasure).',
      staffMember: user.mcUsername || user.email
    });
      if (await isStaffRole(user.role)) await logAudit(user, { area: 'security', action: 'deleted own account', target: user.mcUsername || 'staff account' });

    const token = request.cookies.get(SESSION_COOKIE)?.value;
    await destroySession(token);

    const response = NextResponse.json({ success: true, rconBypassed });
    return clearSessionCookie(response);
  } catch (err) {
    console.error("Self-service account delete error:", err);
    return NextResponse.json({ error: "Failed to delete account" }, { status: 500 });
  }
}
