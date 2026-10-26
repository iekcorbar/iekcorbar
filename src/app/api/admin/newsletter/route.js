import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';
import { sendMail } from '@/lib/mail';
import { newsletterEmail } from '@/lib/email-templates';
import { generateUnsubscribeToken } from '@/lib/unsubscribe-token';

import { logAudit } from '@/lib/audit';
// Deliberately admin-only via requireRole (not requirePermission on some
// new 'newsletter' tab) — user's own explicit choice: this broadcasts to
// every player at once and is effectively unrecallable once sent, the same
// bar as api/admin/rcon's free-text console or api/admin/roles.
const ADMIN_ONLY = ['admin'];

// How many individual sends run concurrently — each recipient needs their
// own personalized unsubscribe link (see email-templates.js), so this can't
// be one bcc'd blast; a small concurrency cap just keeps a large player
// base from opening hundreds of SMTP connections at once.
const SEND_CONCURRENCY = 5;

async function sendInBatches(items, worker, concurrency) {
  const results = [];
  let i = 0;
  async function next() {
    while (i < items.length) {
      const idx = i++;
      results[idx] = await worker(items[idx]).catch(err => ({ error: err }));
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, next));
  return results;
}

// GET: send history (admin-only) — lets staff see what's already gone out
// before composing another one.
export async function GET(request) {
  const auth = await requireRole(request, ADMIN_ONLY);
  if (auth.error) return auth.error;

  try {
    const history = await db.getNewsletterHistory();
    return NextResponse.json({ history });
  } catch (err) {
    console.error("Newsletter history error:", err);
    return NextResponse.json({ error: "Failed to load newsletter history" }, { status: 500 });
  }
}

// POST: compose + send to every non-opted-out player.
export async function POST(request) {
  const auth = await requireRole(request, ADMIN_ONLY);
  if (auth.error) return auth.error;

  try {
    const { subject, body } = await request.json();
    // `body` is HTML from the WYSIWYG editor — an empty editor still
    // produces something like "<p></p>", which is a non-empty *string*, so
    // a plain .trim() check would let a genuinely empty message through.
    const bodyHasContent = body && body.replace(/<[^>]+>/g, '').trim().length > 0;
    if (!subject?.trim() || !bodyHasContent) {
      return NextResponse.json({ error: "Subject and body are required" }, { status: 400 });
    }

    const users = await db.getUsers();
    const recipients = users.filter(u => u.email && !u.newsletterOptOut);
    if (recipients.length === 0) {
      return NextResponse.json({ error: "No subscribed recipients to send to" }, { status: 400 });
    }

    const siteUrl = process.env.SITE_URL || 'https://iekcorbar.com';
    // Sends from the dedicated newsletter mailbox (a real separate Plesk
    // account, not an alias — see .env.local) rather than the shared
    // bug-report one every other sendMail() caller defaults to. Falls back
    // to that default (BYPASS in local dev, or the bugs@ mailbox) if this
    // account isn't configured, so this route doesn't hard-fail just
    // because NEWSLETTER_SMTP_USER happens to be unset somewhere.
    const newsletterAccount = process.env.NEWSLETTER_SMTP_USER ? {
      user: process.env.NEWSLETTER_SMTP_USER,
      password: process.env.NEWSLETTER_SMTP_PASSWORD,
      from: `"Iekcorbar" <${process.env.NEWSLETTER_SMTP_USER}>`
    } : undefined;

    let sentCount = 0;
    let bypassCount = 0;
    const errors = [];

    await sendInBatches(recipients, async (recipient) => {
      const token = generateUnsubscribeToken(recipient.email);
      const unsubscribeUrl = `${siteUrl}/newsletter/unsubscribe?email=${encodeURIComponent(recipient.email)}&token=${token}`;
      const { subject: emailSubject, html, text } = newsletterEmail({ subject: subject.trim(), bodyHtml: body.trim(), unsubscribeUrl });
      try {
        const result = await sendMail({ to: recipient.email, subject: emailSubject, html, text, account: newsletterAccount });
        if (result === 'BYPASS') bypassCount++; else sentCount++;
      } catch (err) {
        errors.push({ email: recipient.email, error: err.message });
      }
    }, SEND_CONCURRENCY);

    await db.saveNewsletterRecord({
      subject: subject.trim(),
      body: body.trim(),
      sentBy: auth.user.mcUsername || auth.user.email,
      recipientCount: sentCount + bypassCount
    });
      await logAudit(auth.user, { area: 'newsletter', action: 'send', target: subject.trim(), detail: `${sentCount + bypassCount} recipients` });

    return NextResponse.json({
      success: true,
      sentCount,
      bypassCount,
      totalRecipients: recipients.length,
      failedCount: errors.length,
      history: await db.getNewsletterHistory()
    });
  } catch (err) {
    console.error("Newsletter send error:", err);
    return NextResponse.json({ error: "Failed to send newsletter" }, { status: 500 });
  }
}
