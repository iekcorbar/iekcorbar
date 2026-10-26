import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { requirePermission } from '@/lib/permissions';
import { sendMail } from '@/lib/mail';
import { bugReportReplyEmail } from '@/lib/email-templates';
import { BUG_CATEGORY_VALUES, BUG_STATUSES } from '@/lib/bug-categories';

import { logAudit } from '@/lib/audit';
const BUG_REPORT_EMAIL = process.env.BUG_REPORT_EMAIL || 'bugs@iekcorbar.com';
const DELIVERY_CHANNELS = ['bell', 'chat', 'email'];

// True if `user` (the session row) is allowed to see/act on `report` as its
// owner — case-insensitive, same reasoning as the journal Share/Edit bug
// CLAUDE.md documents (email comparisons in this codebase must never be
// case-sensitive).
function isOwnReport(user, report) {
  return !!user && !!report && report.reporterEmail &&
    report.reporterEmail.toLowerCase() === user.email.toLowerCase();
}

export async function POST(request) {
  try {
    const body = await request.json();
    const { action = 'submit' } = body;

    if (action === 'submit') {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) {
        return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
      }

      const { subject, description, category, customCategory } = body;
      if (!subject || !subject.trim() || !description || !description.trim()) {
        return NextResponse.json({ error: "Subject and description are required" }, { status: 400 });
      }
      if (!category || !BUG_CATEGORY_VALUES.includes(category)) {
        return NextResponse.json({ error: "Please pick a category" }, { status: 400 });
      }
      if (category === 'other' && (!customCategory || !customCategory.trim())) {
        return NextResponse.json({ error: "Please describe the category when picking \"Other\"" }, { status: 400 });
      }

      const saved = await db.submitBugReport({
        reporterEmail: sessionUser.email,
        reporterUsername: sessionUser.mcUsername,
        subject: subject.trim(),
        description: description.trim(),
        category,
        customCategory: category === 'other' ? customCategory.trim() : null
      });
      if (!saved) {
        return NextResponse.json({ error: "Could not submit report (database unreachable). Please try again shortly." }, { status: 500 });
      }

      // Staff bell — same pattern as api/reports' notifyStaffOfReport, first
      // wired up 2026-09-08 (see CLAUDE.md's "plumbing exists, no trigger"
      // note this was fixing). Best-effort, never blocks the submission.
      db.createNotification({
        type: 'bug_report',
        message: `New bug report: ${subject.trim()} (${sessionUser.mcUsername || sessionUser.email})`,
        link: '/moderation?tab=bugs'
      }).catch(() => {});

      // Best-effort — the report is already saved either way, so a mail
      // failure (or SMTP not being configured yet) shouldn't fail this
      // request. See src/lib/mail.js.
      try {
        await sendMail({
          to: BUG_REPORT_EMAIL,
          subject: `[Iekcorbar Bug Report] ${subject.trim()}`,
          text: `From: ${sessionUser.mcUsername} (${sessionUser.email})\nCategory: ${category === 'other' ? customCategory.trim() : category}\n\n${description.trim()}`
        });
      } catch (mailErr) {
        console.error("Failed to email bug report:", mailErr);
      }

      return NextResponse.json({ success: true });
    }

    if (action === 'update_status') {
      const auth = await requirePermission(request, 'bugs');
      if (auth.error) return auth.error;

      const { id, status, duplicateOf } = body;
      if (!id || !BUG_STATUSES.includes(status)) {
        return NextResponse.json({ error: "Missing id or invalid status" }, { status: 400 });
      }
      if (status === 'duplicate' && !duplicateOf) {
        return NextResponse.json({ error: "Pick which report this is a duplicate of" }, { status: 400 });
      }

      // Snapshot the previous status so the reporter only gets pinged on
      // the actual open -> fixed transition, not every time staff re-saves
      // an already-fixed report (e.g. editing notes triggers no status
      // change, but re-clicking "Mark fixed" shouldn't re-notify either).
      const before = (await db.getBugReports()).find(r => r.id === id);
      const wasAlreadyFixed = before?.status === 'fixed';

      await db.updateBugReportStatus(id, status, auth.user.mcUsername, duplicateOf);

      if (status === 'fixed' && !wasAlreadyFixed) {
        if (before?.reporterEmail) {
          const notifyMessage = `Good news — your bug report "${before.subject}" has been fixed!`;
          await db.createUserNotification({
            recipientEmail: before.reporterEmail,
            type: 'bug_report',
            message: notifyMessage,
            link: '/profile#bug-reports'
          });
          // Also drop it into the report's own thread so the "why/what
          // changed" context lives in one place alongside any other replies,
          // same as a manual Contact Reporter message would.
          await db.addBugReportMessage({
            bugReportId: id,
            senderType: 'staff',
            senderName: auth.user.mcUsername || 'Staff',
            body: notifyMessage,
            deliveryChannel: 'bell'
          });
        }

        // Everyone who confirmed "I have this too" while composing their
        // own report gets a bell too — they never filed this report
        // themselves, so there's no thread of theirs to post into (and no
        // /profile#bug-reports entry to link to), just the bell + a link
        // to where it now shows as Fixed.
        const watcherEmails = await db.getBugReportWatcherEmails(id);
        for (const watcherEmail of watcherEmails) {
          await db.createUserNotification({
            recipientEmail: watcherEmail,
            type: 'bug_report',
            message: `Good news — a bug you also reported, "${before?.subject}", has been fixed!`,
            link: '/known-bugs'
          });
        }
      }

      await logAudit(auth.user, { area: 'bugs', action: 'set status', target: before?.subject || id, detail: status });
      return NextResponse.json({ success: true, reports: await db.getBugReports() });
    }

    if (action === 'update_notes') {
      const auth = await requirePermission(request, 'bugs');
      if (auth.error) return auth.error;

      const { id, staffNotes } = body;
      if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

      await db.updateBugReportNotes(id, (staffNotes || '').trim() || null);
      await logAudit(auth.user, { area: 'bugs', action: 'update notes', target: `bug ${id}` });
      return NextResponse.json({ success: true, reports: await db.getBugReports() });
    }

    // Staff re-categorizing a report — mainly for reports filed as "Other"
    // before a matching predefined category existed (see bug-categories.js).
    if (action === 'update_category') {
      const auth = await requirePermission(request, 'bugs');
      if (auth.error) return auth.error;

      const { id, category, customCategory } = body;
      if (!id || !category || !BUG_CATEGORY_VALUES.includes(category)) {
        return NextResponse.json({ error: "Missing id or invalid category" }, { status: 400 });
      }
      if (category === 'other' && (!customCategory || !customCategory.trim())) {
        return NextResponse.json({ error: "Please describe the category when picking \"Other\"" }, { status: 400 });
      }

      const ok = await db.updateBugReportCategory(id, category, category === 'other' ? customCategory.trim() : null);
      if (!ok) return NextResponse.json({ error: "Could not update that report's category right now" }, { status: 500 });
      await logAudit(auth.user, { area: 'bugs', action: 'set category', target: `bug ${id}`, detail: category });
      return NextResponse.json({ success: true, reports: await db.getBugReports() });
    }

    // "I have this too" while composing a new report — see the duplicate
    // check in Profile's bug report form. Any logged-in member can bump an
    // existing OPEN report's counter instead of filing a fresh duplicate.
    if (action === 'bump_duplicate') {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

      const { id } = body;
      if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

      const reports = await db.getBugReports();
      const target = reports.find(r => r.id === id);
      if (!target || target.status !== 'open') {
        return NextResponse.json({ error: "That report is no longer open" }, { status: 400 });
      }

      const ok = await db.bumpBugReportAffectedCount(id, sessionUser.email);
      if (!ok) return NextResponse.json({ error: "Could not update that report right now" }, { status: 500 });
      return NextResponse.json({ success: true });
    }

    // Staff reaching out to the reporter — they pick which channel actually
    // delivers it; either way the message is also saved to the thread so
    // there's one durable record of the conversation regardless of channel.
    if (action === 'contact') {
      const auth = await requirePermission(request, 'bugs');
      if (auth.error) return auth.error;

      const { id, channel, message } = body;
      if (!id || !message || !message.trim()) {
        return NextResponse.json({ error: "Missing id or message" }, { status: 400 });
      }
      if (!DELIVERY_CHANNELS.includes(channel)) {
        return NextResponse.json({ error: "Invalid delivery channel" }, { status: 400 });
      }

      const reports = await db.getBugReports();
      const report = reports.find(r => r.id === id);
      if (!report) return NextResponse.json({ error: "Report not found" }, { status: 404 });

      const trimmed = message.trim();

      if (channel === 'bell') {
        await db.createUserNotification({
          recipientEmail: report.reporterEmail,
          type: 'bug_report',
          message: `Staff replied to your bug report "${report.subject}": ${trimmed}`,
          link: '/profile#bug-reports'
        });
      } else if (channel === 'chat') {
        if (!report.reporterUsername) {
          return NextResponse.json({ error: "This reporter has no in-game username on file — use Email or Bell instead." }, { status: 400 });
        }
        // Prefixed with which report this is about — a bare chat message
        // wouldn't otherwise connect back to it for the reporter, unlike
        // the bell (whose text names the report) or email (subject line).
        await db.sendMessage(auth.user.mcUsername || 'Staff', report.reporterUsername, `[Bug report "${report.subject}"] ${trimmed}`, null);
      } else if (channel === 'email') {
        const { subject, html, text } = bugReportReplyEmail({
          subject: report.subject, message: trimmed, siteUrl: process.env.SITE_URL || 'https://iekcorbar.com'
        });
        try {
          await sendMail({ to: report.reporterEmail, subject, html, text });
        } catch (mailErr) {
          console.error("Failed to email bug report reply:", mailErr);
          return NextResponse.json({ error: "Could not send the email (SMTP unreachable) — the message was not recorded. Try again or use a different channel." }, { status: 502 });
        }
      }

      await db.addBugReportMessage({
        bugReportId: id,
        senderType: 'staff',
        senderName: auth.user.mcUsername || 'Staff',
        body: trimmed,
        deliveryChannel: channel
      });

      await logAudit(auth.user, { area: 'bugs', action: 'contact reporter', target: report.subject || `bug ${id}`, detail: channel });
      return NextResponse.json({ success: true, messages: await db.getBugReportMessages(id) });
    }

    // The reporter following up on their own report from their Profile
    // page — always visible on-site already, so no delivery channel to
    // pick; staff just get a bell so they know to check back.
    if (action === 'reply') {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

      const { id, message } = body;
      if (!id || !message || !message.trim()) {
        return NextResponse.json({ error: "Missing id or message" }, { status: 400 });
      }

      const reports = await db.getBugReports();
      const report = reports.find(r => r.id === id);
      if (!report || !isOwnReport(sessionUser, report)) {
        return NextResponse.json({ error: "Report not found" }, { status: 404 });
      }

      await db.addBugReportMessage({
        bugReportId: id,
        senderType: 'reporter',
        senderName: sessionUser.mcUsername || sessionUser.email,
        body: message.trim()
      });

      db.createNotification({
        type: 'bug_report',
        message: `${sessionUser.mcUsername || sessionUser.email} replied on bug report "${report.subject}"`,
        link: '/moderation?tab=bugs'
      }).catch(() => {});

      return NextResponse.json({ success: true, messages: await db.getBugReportMessages(id) });
    }

    return NextResponse.json({ error: "Invalid action" }, { status: 400 });
  } catch (err) {
    console.error("Bug report POST error:", err);
    return NextResponse.json({ error: "Failed to process bug report" }, { status: 500 });
  }
}

export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const isPublic = searchParams.get('public');
    const mine = searchParams.get('mine');
    const messagesFor = searchParams.get('messagesFor');

    // Members-only view (any logged-in account, not just staff) — the
    // /known-bugs page and the duplicate-check panel while composing a new
    // report. Despite the name, "public" here means "no staff permission
    // required", not "no login required" — descriptions can carry detail
    // staff don't want handed to people with no account here at all, on
    // top of reporter identity/staff notes/the reply thread already being
    // excluded (see db.getPublicBugReports). Real fix for a live incident
    // 2026-09-09 where this was reachable with no session at all.
    if (isPublic) {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
      const category = searchParams.get('category') || undefined;
      return NextResponse.json({ reports: await db.getPublicBugReports(category) });
    }

    // A member's own submitted reports — Profile page's "My Bug Reports".
    if (mine) {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
      return NextResponse.json({ reports: await db.getBugReportsByReporter(sessionUser.email) });
    }

    // The conversation thread on one report — either staff (any role with
    // the 'bugs' tab) or the reporter themselves.
    if (messagesFor) {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

      const reports = await db.getBugReports();
      const report = reports.find(r => r.id === messagesFor);
      if (!report) return NextResponse.json({ error: "Report not found" }, { status: 404 });

      if (!isOwnReport(sessionUser, report)) {
        const auth = await requirePermission(request, 'bugs');
        if (auth.error) return auth.error;
      }

      return NextResponse.json({ messages: await db.getBugReportMessages(messagesFor) });
    }

    // Default — full staff view (identity + staff notes included).
    const auth = await requirePermission(request, 'bugs');
    if (auth.error) return auth.error;

    return NextResponse.json({ reports: await db.getBugReports() });
  } catch (err) {
    console.error("Bug reports GET error:", err);
    return NextResponse.json({ error: "Failed to load bug reports" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'bugs');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

    await db.deleteBugReport(id);
    return NextResponse.json({ success: true, reports: await db.getBugReports() });
  } catch (err) {
    console.error("Bug report DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete bug report" }, { status: 500 });
  }
}
