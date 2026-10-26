// Server-side half of the per-role Staff Panel permission system — see
// src/lib/staff-tabs.js for the shared tab manifest and defaults. This file
// touches real sessions and the database, so it must never be imported from
// a client component (import staff-tabs.js there instead).
import { NextResponse } from 'next/server';
import { getSessionUser } from './session';
import { db } from './db';
import { STAFF_TABS, SETTINGS_SUBTAB_KEYS, DEFAULT_MODERATOR_TABS, isAdminRole, roleRank } from './staff-tabs';

// Resolves which Staff Panel tabs `role` can access right now. Admin always
// gets every tab (see isAdminRole's own comment for why that's hardcoded,
// not stored). Anything else is looked up in the `roles` table, falling
// back to the legacy default set for 'moderator' specifically if no row
// exists for it yet, and to "nothing" for any other unknown role.
//
// Settings permission derivation (2026-09-13, see staff-tabs.js's comment
// for the full story): the raw `tabs` array stored on a role never needs to
// contain the bare 'settings' key going forward, and a role saved before
// Settings was split into per-tool permissions might still ONLY contain it.
// Both directions are resolved here, once, so every caller (requirePermission
// and /api/permissions/me alike) sees a consistent, already-expanded list
// without needing to know this history:
//   - bare 'settings' present  -> every settings_* key is implied (legacy
//     roles keep full access instead of silently losing all 9 tools)
//   - any settings_* key present -> bare 'settings' is implied (that's the
//     only way the umbrella tab/sub-tab bar becomes reachable at all)
export async function getRoleTabs(role) {
  if (isAdminRole(role)) return STAFF_TABS.map(t => t.key);

  const roles = await db.getRoles();
  const found = roles.find(r => r.name === role);
  let tabs = found
    ? (Array.isArray(found.tabs) ? found.tabs : [])
    : (role === 'moderator' ? DEFAULT_MODERATOR_TABS : []);

  if (tabs.includes('settings')) {
    tabs = [...new Set([...tabs, ...SETTINGS_SUBTAB_KEYS])];
  } else if (SETTINGS_SUBTAB_KEYS.some(k => tabs.includes(k))) {
    tabs = [...tabs, 'settings'];
  }
  return tabs;
}

// True if `role` gets into the Staff Panel at all (has at least one tab, or
// is admin) — used by the login route to decide who goes through the 2FA
// step, and could gate the Staff Panel's own top-level access guard.
export async function isStaffRole(role) {
  if (isAdminRole(role)) return true;
  const tabs = await getRoleTabs(role);
  return tabs.length > 0;
}

// Real, server-verified permission check — mirrors requireRole()'s shape
// ({ user } or { error }) so it drops into an existing route handler the
// same way, but checks the caller's *granted tabs* (from the `roles` table)
// instead of a hardcoded role list. Use this instead of requireRole() for
// any route backing a Staff Panel tab that should respect Role Manager's
// per-role toggles rather than being permanently admin/moderator-only.
export async function requirePermission(request, tabKey) {
  const user = await getSessionUser(request);
  if (!user) {
    return { error: NextResponse.json({ error: 'Not authenticated' }, { status: 401 }) };
  }
  const tabs = await getRoleTabs(user.role);
  if (!tabs.includes(tabKey)) {
    return { error: NextResponse.json({ error: 'Forbidden' }, { status: 403 }) };
  }
  return { user };
}

// Adds display info for each user's role — `roleLabel` (the Role Manager
// label for custom roles like "Helper", null for admin/moderator/player,
// which use the admin-editable site-content labels instead) and
// `roleIsStaff` — so public pages (members list, member profile, own
// profile) show a custom role as itself instead of falling back to
// "player" just because they only knew the hardcoded admin/moderator names.
export async function withRoleInfo(users) {
  const roles = await db.getRoles();
  const byName = new Map(roles.map(r => [r.name, r]));
  const staffCache = new Map();
  return Promise.all(users.map(async u => {
    const role = u.role || 'player';
    if (!staffCache.has(role)) staffCache.set(role, await isStaffRole(role));
    const row = byName.get(role);
    const isBuiltIn = role === 'admin' || role === 'moderator' || role === 'player';
    return { ...u, roleLabel: !isBuiltIn && row ? (row.label || row.name) : (isBuiltIn ? null : role), roleIsStaff: staffCache.get(role), roleRank: roleRank(role, roles), roleColor: row?.color || null };
  }));
}
