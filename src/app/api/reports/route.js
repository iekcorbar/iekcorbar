import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission, isStaffRole } from '@/lib/permissions';
import { sendMail } from '@/lib/mail';
import { reportNotificationEmail } from '@/lib/email-templates';

import { logAudit } from '@/lib/audit';
// Staff-facing side effects of a new report — never allowed to fail the
// actual submission (the report is already saved by the time this runs).
// Two independent notices: a bell entry in the Staff Panel (works even with
// no SMTP configured at all) and an HTML email BCC'd to every staff email
// (any role with Staff Panel access, not just literal admin/moderator —
// same isStaffRole() boundary as 2FA/session-timeout).
async function notifyStaffOfReport({ targetUsername, category, details }) {
  await db.createNotification({
    type: 'report',
    message: `New report: ${targetUsername} (${category})`,
    link: '/moderation?tab=reports'
  });

  const users = await db.getUsers();
  const staffFlags = await Promise.all(users.map(u => isStaffRole(u.role)));
  const staffEmails = users.filter((u, i) => staffFlags[i] && u.email).map(u => u.email);
  if (staffEmails.length === 0) return;

  const { subject, html, text } = reportNotificationEmail({
    targetUsername, category, details,
    siteUrl: process.env.SITE_URL || 'https://iekcorbar.com'
  });
  // "to" is a harmless self-addressed placeholder — some SMTP relays reject
  // a message with only a bcc and no to at all. Every real staff address
  // goes in bcc only, so they never see each other's addresses.
  await sendMail({ to: process.env.BUG_REPORT_EMAIL || process.env.SMTP_USER, bcc: staffEmails, subject, html, text });
}

// POST: Submit an anonymous report — no reporter identity stored, no auth
export async function POST(request) {
  try {
    const { targetUsername, category, details } = await request.json();
    if (!targetUsername || !category || !details) {
      return NextResponse.json({ error: "All fields are required" }, { status: 400 });
    }
    const saved = await db.submitReport({ targetUsername, category, details });
    if (!saved) {
      return NextResponse.json({ error: "Could not submit report (database unreachable). Please try again shortly." }, { status: 500 });
    }

    try {
      await notifyStaffOfReport({ targetUsername, category, details });
    } catch (notifyErr) {
      console.error("Report staff-notification error (report itself was still saved):", notifyErr);
    }

    return NextResponse.json({ success: true });
  } catch (err) {
    console.error("Report submit error:", err);
    return NextResponse.json({ error: "Failed to submit report" }, { status: 500 });
  }
}

// GET: staff with the 'reports' tab granted — fetch all reports
export async function GET(request) {
  try {
    const auth = await requirePermission(request, 'reports');
    if (auth.error) return auth.error;
    // Context for staff: how many players have blocked the reported member (count only).
    const [reports, blockCounts] = await Promise.all([db.getReports(), db.getChatBlockCounts().catch(() => ({}))]);
    return NextResponse.json({
      reports: reports.map(r => ({ ...r, blockedByCount: blockCounts[String(r.targetUsername).toLowerCase()] || 0 }))
    });
  } catch (err) {
    console.error("Reports GET error:", err);
    return NextResponse.json({ error: "Failed to load reports" }, { status: 500 });
  }
}

// DELETE: staff with the 'reports' tab granted — dismiss a report
export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'reports');
    if (auth.error) return auth.error;
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) {
      return NextResponse.json({ error: "Missing report ID" }, { status: 400 });
    }
    const deleted = await db.deleteReport(id);
    if (!deleted) {
      return NextResponse.json({ error: "Report not found (already deleted?)" }, { status: 404 });
    }
    await logAudit(auth.user, { area: 'reports', action: 'delete', target: `report ${id}` });
    return NextResponse.json({ success: true, reports: await db.getReports() });
  } catch (err) {
    console.error("Reports DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete report" }, { status: 500 });
  }
}
