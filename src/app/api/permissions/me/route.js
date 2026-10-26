import { NextResponse } from 'next/server';
import { getSessionUser } from '@/lib/session';
import { getRoleTabs, isStaffRole } from '@/lib/permissions';

// What the *currently logged-in* caller's own role can access — unlike
// /api/admin/roles (admin-only, manages every role), this is safe for any
// authenticated user to call and never exposes another role's permissions.
// The Staff Panel (src/app/moderation/page.js) uses this both for its own
// access guard and to decide which tabs to render for a non-admin role.
export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ tabs: [], isStaff: false, isAdmin: false });

  const [tabs, staff] = await Promise.all([getRoleTabs(user.role), isStaffRole(user.role)]);
  return NextResponse.json({ tabs, isStaff: staff, isAdmin: user.role === 'admin' });
}
