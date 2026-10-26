import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';
import { STAFF_TABS, isValidRoleColor } from '@/lib/staff-tabs';

import { logAudit } from '@/lib/audit';
const VALID_TAB_KEYS = new Set(STAFF_TABS.map(t => t.key));

// Role & permission management (Staff Panel > Settings > Roles &
// Permissions). Deliberately gated with requireRole(['admin']) — the fixed
// role list, not requirePermission()/a toggleable tab — so no role, however
// it's configured, can ever grant itself the power to grant more power.
// 'admin' itself is never a row in the `roles` table (see schema.sql); it's
// rejected below rather than silently accepted.
const PROTECTED_NAME = 'admin';

function cleanRoleName(raw) {
  return String(raw || '').trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
}

export async function GET(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const roles = await db.getRoles();
    return NextResponse.json({ roles });
  } catch (err) {
    console.error('Roles GET error:', err);
    return NextResponse.json({ error: 'Failed to load roles' }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const body = await request.json();
    const name = cleanRoleName(body.name);
    const label = String(body.label || '').trim();
    // Whitelisted against the real tab manifest rather than trusting
    // whatever the client sends verbatim — harmless today (only admin can
    // reach this route), but a stray/typo'd key would otherwise sit in the
    // role forever, granted but matching nothing any route actually checks.
    const tabs = (Array.isArray(body.tabs) ? body.tabs : []).filter(t => VALID_TAB_KEYS.has(t));
    // Only palette keys are stored; anything else falls back to the default.
    const color = isValidRoleColor(body.color) ? body.color : null;

    if (!name || !label) {
      return NextResponse.json({ error: 'name and label are required' }, { status: 400 });
    }
    if (name === PROTECTED_NAME) {
      return NextResponse.json({ error: "'admin' is a built-in role and always has every permission — it can't be edited here." }, { status: 400 });
    }

    const saved = await db.saveRole({ name, label, isStaff: true, tabs, color });
    if (!saved) {
      return NextResponse.json({ error: 'Could not save role (database unreachable). Please try again shortly.' }, { status: 500 });
    }

    const roles = await db.getRoles();
    await logAudit(auth.user, { area: 'roles', action: 'save role', target: name, detail: `tabs: ${tabs.join(', ')}` });
    return NextResponse.json({ success: true, roles });
  } catch (err) {
    console.error('Roles POST error:', err);
    return NextResponse.json({ error: 'Failed to save role' }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const name = cleanRoleName(searchParams.get('name'));
    if (!name) return NextResponse.json({ error: 'Missing name' }, { status: 400 });
    if (name === PROTECTED_NAME) {
      return NextResponse.json({ error: "'admin' is a built-in role and can't be deleted." }, { status: 400 });
    }

    const deleted = await db.deleteRole(name);
    if (!deleted) return NextResponse.json({ error: 'Could not delete role (database unreachable). Please try again shortly.' }, { status: 500 });

    const roles = await db.getRoles();
    await logAudit(auth.user, { area: 'roles', action: 'delete role', target: name });
    return NextResponse.json({ success: true, roles });
  } catch (err) {
    console.error('Roles DELETE error:', err);
    return NextResponse.json({ error: 'Failed to delete role' }, { status: 500 });
  }
}
