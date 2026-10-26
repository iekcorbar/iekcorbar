import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { sendRconCommand } from '@/lib/rcon';

// Linking a new account to Minecraft (the /link page). See api/auth/register
// for the overall flow. Session-verified: only ever acts on the caller's own
// account.
//  GET                      -> { status: 'linked'|'pending'|'on_hold', deadline, mcUsername, serverAddress }
//  POST { action:'reactivate' }  -> on_hold: back on the whitelist for REACTIVATE_WINDOW_MS
//  POST { action:'send_code' }   -> tellraw a 6-digit code to the (online) player
//  POST { action:'verify', code } -> linked: status cleared, stays whitelisted
const REACTIVATE_WINDOW_MS = 60 * 60 * 1000; // 1 hour
const CODE_TTL_MS = 30 * 60 * 1000;          // 30 minutes
const MAX_ATTEMPTS = 10;                      // wrong codes before the code is voided

if (!global.__link_attempts) global.__link_attempts = new Map();

async function serverAddress() {
  try {
    const s = await db.getSettings();
    return s.server_address || null;
  } catch {
    return null;
  }
}

export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  const state = await db.getLinkState(user.email);
  return NextResponse.json({
    status: state?.status || 'linked',
    deadline: state?.deadline || null,
    mcUsername: state?.mcUsername || user.mcUsername || null,
    serverAddress: await serverAddress()
  });
}

export async function POST(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

  const { action, code } = await request.json().catch(() => ({}));
  const state = await db.getLinkState(user.email);
  const status = state?.status || 'linked';
  const ign = state?.mcUsername;
  if (status === 'linked') return NextResponse.json({ success: true, status: 'linked' });
  if (!ign) return NextResponse.json({ error: "No Minecraft username on this account — please contact staff." }, { status: 400 });

  if (action === 'reactivate') {
    if (status !== 'on_hold') return NextResponse.json({ success: true, status });
    try {
      await sendRconCommand(`whitelist add ${ign}`);
    } catch (err) {
      console.error('Reactivate whitelist failed:', err);
      return NextResponse.json({ error: "Could not reach the Minecraft server. Please try again in a moment." }, { status: 500 });
    }
    const deadline = new Date(Date.now() + REACTIVATE_WINDOW_MS);
    await db.updateLinkState(user.email, { status: 'pending', deadline, code: null, codeExpires: null });
    return NextResponse.json({ success: true, status: 'pending', deadline: deadline.toISOString() });
  }

  if (action === 'send_code') {
    if (status !== 'pending') return NextResponse.json({ error: "Your account is on hold — tap Reactivate first." }, { status: 400 });
    const newCode = String(Math.floor(100000 + Math.random() * 900000));
    await db.updateLinkState(user.email, { code: newCode, codeExpires: new Date(Date.now() + CODE_TTL_MS) });
    global.__link_attempts.delete(user.email.toLowerCase());
    try {
      const result = await sendRconCommand(
        `tellraw ${ign} [{"text":"[Iekcorbar] ","color":"gold","bold":true},{"text":"Your website link code is: ","color":"white"},{"text":"${newCode}","color":"yellow","bold":true}]`
      );
      if (result === 'BYPASS') {
        // RCON not configured (local dev) — show the code on the page instead.
        return NextResponse.json({ success: true, devCode: newCode });
      }
      if (/no player was found|not found|unknown/i.test(String(result || ''))) {
        return NextResponse.json({ error: `You're not online yet. Join the server as "${ign}" first, then tap Send code again.` }, { status: 400 });
      }
      return NextResponse.json({ success: true });
    } catch (err) {
      console.error('Send link code failed:', err);
      if (process.env.NODE_ENV === 'development') return NextResponse.json({ success: true, devCode: newCode });
      return NextResponse.json({ error: `Couldn't reach you in-game. Make sure you're online as "${ign}" and try again.` }, { status: 400 });
    }
  }

  if (action === 'verify') {
    if (status !== 'pending') return NextResponse.json({ error: "Your account is on hold — tap Reactivate first." }, { status: 400 });
    const key = user.email.toLowerCase();
    const attempts = (global.__link_attempts.get(key) || 0) + 1;
    global.__link_attempts.set(key, attempts);
    const expired = !state.code || !state.codeExpires || new Date(state.codeExpires).getTime() < Date.now();
    if (expired || attempts > MAX_ATTEMPTS) {
      if (attempts > MAX_ATTEMPTS) await db.updateLinkState(user.email, { code: null, codeExpires: null });
      return NextResponse.json({ error: "This code has expired. Tap Send code to get a new one." }, { status: 400 });
    }
    if (String(code || '').trim() !== String(state.code)) {
      return NextResponse.json({ error: "That code isn't right — check your in-game chat and try again." }, { status: 400 });
    }
    global.__link_attempts.delete(key);
    try { await sendRconCommand(`whitelist add ${ign}`); } catch { /* already whitelisted from registration */ }
    await db.updateLinkState(user.email, { status: null, deadline: null, code: null, codeExpires: null });
    await db.createUserNotification({
      recipientEmail: user.email,
      type: 'account_link',
      message: `Your Minecraft account "${ign}" is linked. Welcome aboard!`,
      link: '/profile',
      pushTitle: '✅ Account linked',
      pushBody: `"${ign}" is linked — have fun on Iekcorbar!`,
      tag: 'account-link'
    });
    return NextResponse.json({ success: true, status: 'linked' });
  }

  return NextResponse.json({ error: "Invalid action" }, { status: 400 });
}
