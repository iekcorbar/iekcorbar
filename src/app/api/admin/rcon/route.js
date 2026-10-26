import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';
import { sendRconCommand } from '@/lib/rcon';

import { logAudit } from '@/lib/audit';
// Raw RCON terminal (Staff Panel > Console tab). Unlike the templated
// kick/ban/tempban/unban in api/moderation (restricted to four fixed
// commands), this executes *any* string the caller sends verbatim against
// the live Minecraft server console — gated to whichever roles have the
// 'console' tab granted via Role Manager (admin always does, see
// src/lib/permissions.js), and every command + response is logged to
// rcon_console_log for accountability regardless of outcome.
export async function GET(request) {
  try {
    const auth = await requirePermission(request, 'console');
    if (auth.error) return auth.error;

    const log = await db.getRconConsoleLog();
    return NextResponse.json({ log });
  } catch (err) {
    return NextResponse.json({ error: 'Failed to retrieve console log' }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'console');
    if (auth.error) return auth.error;
    const { user } = auth;

    const { command } = await request.json();
    if (!command || typeof command !== 'string' || !command.trim()) {
      return NextResponse.json({ error: 'Missing command' }, { status: 400 });
    }
    const trimmed = command.trim();
    const staffMember = user.mcUsername || user.email;

    let response = null;
    let bypassed = false;
    try {
      const result = await sendRconCommand(trimmed);
      if (result === 'BYPASS') {
        bypassed = true;
      } else {
        response = result;
      }
    } catch (rconErr) {
      // Still log the attempt even though it failed to reach the server —
      // knowing staff *tried* to run something is part of the audit trail.
      await db.addRconConsoleLog({ command: trimmed, response: `[connection failed] ${rconErr.message}`, staffMember });
      return NextResponse.json({
        error: 'Could not reach the Minecraft server. The command was not executed.'
      }, { status: 502 });
    }

    await db.addRconConsoleLog({
      command: trimmed,
      response: bypassed ? '[RCON not configured on this environment — command not sent]' : (response || '(empty response)'),
      staffMember
    });

    await logAudit(auth.user, { area: 'console', action: 'run command', target: trimmed.slice(0, 200) });
    const log = await db.getRconConsoleLog();
    return NextResponse.json({ success: true, response, bypassed, log });
  } catch (err) {
    return NextResponse.json({ error: 'Failed to execute command' }, { status: 500 });
  }
}
