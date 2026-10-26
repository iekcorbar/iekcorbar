import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// GET is public — this text is rendered on public pages (nav, members list,
// friends page, etc.), so anyone needs to be able to read it. Only editing
// is gated, via the same 'settings' Staff Panel permission as this feature's
// sibling sub-tabs (carousel/guide/rules/tips/vote sites/projects) — 'admin'
// is the only role with 'settings' by default, but Role Manager can now
// delegate it to 'moderator' or a custom role like any other tab.
export async function GET() {
  try {
    const content = await db.getSiteContent();
    return NextResponse.json({ content });
  } catch (err) {
    console.error("Site content GET error:", err);
    return NextResponse.json({ error: "Failed to load site content" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'settings_content');
    if (auth.error) return auth.error;

    const { entries } = await request.json();
    if (!entries || typeof entries !== 'object') {
      return NextResponse.json({ error: "Missing entries" }, { status: 400 });
    }

    const saved = await db.saveSiteContent(entries);
    if (!saved) {
      return NextResponse.json({ error: "Could not save (database unreachable). Please try again shortly." }, { status: 500 });
    }

    await logAudit(auth.user, { area: 'settings_content', action: 'update', target: 'Site content', detail: Object.keys(entries).join(', ') });
    const content = await db.getSiteContent();
    return NextResponse.json({ success: true, content });
  } catch (err) {
    console.error("Site content PUT error:", err);
    return NextResponse.json({ error: "Failed to save site content" }, { status: 500 });
  }
}
