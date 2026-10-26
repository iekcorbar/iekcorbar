import { NextResponse } from 'next/server';
import { requireRole } from '@/lib/session';
import { sendRconCommand } from '@/lib/rcon';

import { logAudit } from '@/lib/audit';
// Real remote read/write of VoteSpeed's own reward config (rewards.yml's
// "default" reward + config.yml's vote-count milestones) via the plugin's
// new /webrewards RCON command — see WebRewardsCommand.java for the actual
// file I/O and its important caveat (saving strips YAML comments, so it
// backs up each file first). Admin-only, same precedent as the shop's
// take-money command and site-content: this can silently break every
// future vote reward at once if edited carelessly.

export async function GET(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const result = await sendRconCommand('webrewards get');
    if (result === 'BYPASS') {
      return NextResponse.json({ error: "RCON isn't configured in this environment." }, { status: 503 });
    }
    let data;
    try {
      data = JSON.parse(result);
    } catch {
      return NextResponse.json({ error: `Unexpected server reply: "${result}"` }, { status: 502 });
    }
    if (!data.success) {
      return NextResponse.json({ error: data.error || 'Failed to load rewards' }, { status: 502 });
    }
    return NextResponse.json(data);
  } catch (err) {
    console.error("Vote rewards GET error:", err);
    return NextResponse.json({ error: `Could not reach the Minecraft server: ${err.message}` }, { status: 502 });
  }
}

export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin']);
    if (auth.error) return auth.error;

    const body = await request.json();
    if (!body.default || !Array.isArray(body.milestones)) {
      return NextResponse.json({ error: "Missing default reward or milestones" }, { status: 400 });
    }

    // Compact (no extra whitespace) — the plugin reconstructs this from
    // Bukkit's space-split command args, so anything beyond single spaces
    // inside string values could get mangled.
    const payload = JSON.stringify(body);
    const result = await sendRconCommand(`webrewards set ${payload}`);
    if (result === 'BYPASS') {
      return NextResponse.json({ error: "RCON isn't configured in this environment." }, { status: 503 });
    }
    let data;
    try {
      data = JSON.parse(result);
    } catch {
      return NextResponse.json({ error: `Unexpected server reply: "${result}"` }, { status: 502 });
    }
    if (!data.success) {
      return NextResponse.json({ error: data.error || 'Failed to save rewards' }, { status: 502 });
    }
    await logAudit(auth.user, { area: 'vote_rewards', action: 'update', target: 'VoteSpeed rewards' });
    return NextResponse.json(data);
  } catch (err) {
    console.error("Vote rewards POST error:", err);
    return NextResponse.json({ error: `Could not reach the Minecraft server: ${err.message}` }, { status: 502 });
  }
}
