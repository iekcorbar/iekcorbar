import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { sendRconCommand } from '@/lib/rcon';
import { hashPassword } from '@/lib/password';
import { createSession, attachSessionCookie } from '@/lib/session';
import { bindPushTokenFromRequest } from '@/lib/push';

// Registration (reworked 2026-09-27). The account is created right away with
// link_status 'pending' and the player is whitelisted + logged in. Linking the
// Minecraft account (in-game code) happens afterwards on /link
// (api/account/link) — they get LINK_WINDOW_MS for that. If they don't, the
// reminder runner (src/lib/reminders.js → runLinkDeadlines) removes them from
// the whitelist, kicks them and puts the account 'on_hold' until they link.
// Only affects NEW registrations: existing accounts have link_status NULL.
const LINK_WINDOW_MS = 3 * 24 * 60 * 60 * 1000; // 3 days

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const IGN_RE = /^[A-Za-z0-9_]{3,16}$/; // Minecraft Java username rules

export async function POST(request) {
  try {
    const body = await request.json();
    const { email, password, mcUsername, action = 'initiate' } = body;

    if (action !== 'initiate') {
      // The old send_code/verify steps moved to /api/account/link.
      return NextResponse.json({ error: "Please refresh the page — linking now happens on the Link page." }, { status: 400 });
    }

    if (!email || !password || !mcUsername) {
      return NextResponse.json({ error: "All fields are required" }, { status: 400 });
    }
    const ign = String(mcUsername).trim();
    if (!EMAIL_RE.test(String(email).trim())) {
      return NextResponse.json({ error: "Please enter a valid email address" }, { status: 400 });
    }
    if (!IGN_RE.test(ign)) {
      return NextResponse.json({ error: "That isn't a valid Minecraft username (3–16 letters, numbers or _)" }, { status: 400 });
    }
    if (String(password).length < 6) {
      return NextResponse.json({ error: "Password must be at least 6 characters" }, { status: 400 });
    }

    const users = await db.getUsers();
    if (users.some(u => u.email && u.email.toLowerCase() === String(email).trim().toLowerCase())) {
      return NextResponse.json({ error: "Email already registered" }, { status: 400 });
    }
    if (users.some(u => u.mcUsername && u.mcUsername.toLowerCase() === ign.toLowerCase())) {
      return NextResponse.json({ error: "Minecraft username already bound to an account" }, { status: 400 });
    }

    // Whitelist first — without it they can't join to link.
    try {
      await sendRconCommand(`whitelist add ${ign}`);
    } catch (err) {
      console.error("Whitelist command failed:", err);
      if (process.env.NODE_ENV !== 'development') {
        return NextResponse.json({
          error: "Could not reach the Minecraft server to whitelist you. Please try again in a moment or contact staff."
        }, { status: 500 });
      }
    }

    const newUser = { email: String(email).trim(), password: hashPassword(password), role: 'player', mcUsername: ign };
    const saved = await db.addUser(newUser);
    if (!saved) {
      return NextResponse.json({ error: "Could not save your account (database unreachable). Please try again shortly." }, { status: 500 });
    }
    const deadline = new Date(Date.now() + LINK_WINDOW_MS);
    await db.updateLinkState(newUser.email, { status: 'pending', deadline });

    await db.createUserNotification({
      recipientEmail: newUser.email,
      type: 'account_link',
      message: `Welcome to Iekcorbar! Link your Minecraft account "${ign}" within 3 days to keep access to the server.`,
      link: '/link',
      pushTitle: '🔗 Link your Minecraft account',
      pushBody: `Join the server as ${ign} and enter your code within 3 days.`,
      tag: 'account-link'
    });

    // Log them in straight away (same session as a normal login).
    const { token, expiresAt } = await createSession(newUser.email, { staff: false });
    await bindPushTokenFromRequest(request, newUser.email);
    const { password: _, ...safeUser } = newUser;
    const response = NextResponse.json({ success: true, user: safeUser, linkRequired: true, linkDeadline: deadline.toISOString() });
    return attachSessionCookie(response, token, expiresAt, { persistent: true });
  } catch (err) {
    console.error("Registration endpoint error:", err);
    return NextResponse.json({ error: "Server error during registration" }, { status: 500 });
  }
}
