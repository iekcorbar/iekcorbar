import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// 2026-08-13: this GET used to optionally bundle vote_reward_config
// (?includeRewardConfig=true) for the /vote page's "Reward Info Banner",
// and POST had an update_reward_config action to edit it — both removed
// along with our own separate reward-dispatch in api/vote/log (see that
// file's comment), since no staff UI ever actually existed to edit this
// config anyway and VoteSpeed's own reward is now the only source of truth.
export async function GET() {
  return NextResponse.json({ sites: await db.getVoteSites() });
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'settings_vote');
    if (auth.error) return auth.error;

    const body = await request.json();
    const { role, action, ...site } = body;
    if (!site.name || !site.url) return NextResponse.json({ error: "Name and URL are required" }, { status: 400 });
    const saved = await db.saveVoteSite(site);
    if (!saved) {
      return NextResponse.json({ error: "Could not save vote site (database unreachable). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'settings_vote', action: 'create', target: site.name });
    return NextResponse.json({ success: true, sites: await db.getVoteSites() });
  } catch {
    return NextResponse.json({ error: "Failed to save vote site" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'settings_vote');
    if (auth.error) return auth.error;

    const { role, ...site } = await request.json();
    const saved = await db.saveVoteSite(site);
    if (!saved) {
      return NextResponse.json({ error: "Could not update vote site (database unreachable). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'settings_vote', action: 'update', target: site.name || site.id });
    return NextResponse.json({ success: true, sites: await db.getVoteSites() });
  } catch {
    return NextResponse.json({ error: "Failed to update vote site" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_vote');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const deleted = await db.deleteVoteSite(id);
    if (!deleted) {
      return NextResponse.json({ error: "Could not delete vote site (database unreachable). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'settings_vote', action: 'delete', target: `vote site ${id}` });
    return NextResponse.json({ success: true, sites: await db.getVoteSites() });
  } catch {
    return NextResponse.json({ error: "Failed to delete vote site" }, { status: 500 });
  }
}
