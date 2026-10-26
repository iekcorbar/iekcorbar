import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
export async function GET() {
  const settings = await db.getSettings();
  return NextResponse.json({ settings });
}

// Was gated by trusting a client-sent `role` field (the old pattern CLAUDE.md
// describes) — migrated to a real session check so Role Manager's dedicated
// 'General Settings' toggle actually controls who can write here, not just
// who the Staff Panel shows the tab to. ('settings_general' specifically,
// not the old single 'settings' — see src/lib/staff-tabs.js's 2026-09-13
// comment on why Settings was split into one permission per tool.)
export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'settings_general');
    if (auth.error) return auth.error;

    const { role, ...settings } = await request.json();
    await db.saveSettings(settings);
    await logAudit(auth.user, { area: 'settings_general', action: 'update', target: 'General settings', detail: Object.keys(settings).join(', ') });
    return NextResponse.json({ success: true, settings: await db.getSettings() });
  } catch {
    return NextResponse.json({ error: "Failed to save settings" }, { status: 500 });
  }
}
