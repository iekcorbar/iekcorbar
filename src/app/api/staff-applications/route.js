import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { requirePermission, isStaffRole } from '@/lib/permissions';
import { sendMail } from '@/lib/mail';
import { staffApplicationNotificationEmail, staffApplicationDecisionEmail, staffApplicationContactEmail } from '@/lib/email-templates';

import { logAudit } from '@/lib/audit';
const MIN_AGE = 10;
const MAX_AGE = 99;
const DELIVERY_CHANNELS = ['bell', 'chat', 'email'];
const DECISIONS = ['approved', 'declined'];

// Staff-facing side effects of a new application — mirrors api/reports'
// notifyStaffOfReport, never allowed to fail the actual submission.
async function notifyStaffOfApplication({ applicantUsername, age, availability, reason }) {
  await db.createNotification({
    type: 'staff_application',
    message: `New staff application: ${applicantUsername} (age ${age})`,
    link: '/moderation?tab=applications'
  });

  const users = await db.getUsers();
  const staffFlags = await Promise.all(users.map(u => isStaffRole(u.role)));
  const staffEmails = users.filter((u, i) => staffFlags[i] && u.email).map(u => u.email);
  if (staffEmails.length === 0) return;

  const { subject, html, text } = staffApplicationNotificationEmail({
    applicantUsername, age, availability, reason,
    siteUrl: process.env.SITE_URL || 'https://iekcorbar.com'
  });
  await sendMail({ to: process.env.BUG_REPORT_EMAIL || process.env.SMTP_USER, bcc: staffEmails, subject, html, text });
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

      const { age, availability, timezone, discordUsername, experience, referenceName, reason, contribution } = body;
      const ageNum = Number(age);
      if (!Number.isInteger(ageNum) || ageNum < MIN_AGE || ageNum > MAX_AGE) {
        return NextResponse.json({ error: `Please enter a valid age (${MIN_AGE}-${MAX_AGE}).` }, { status: 400 });
      }
      if (!availability || !availability.trim()) {
        return NextResponse.json({ error: "Please describe your estimated time online / availability" }, { status: 400 });
      }
      if (!referenceName || !referenceName.trim()) {
        return NextResponse.json({ error: "Please give a reference — an existing staff member or player who can vouch for you" }, { status: 400 });
      }
      if (!reason || !reason.trim()) {
        return NextResponse.json({ error: "Please tell us why you want to become staff" }, { status: 400 });
      }
      if (!contribution || !contribution.trim()) {
        return NextResponse.json({ error: "Please tell us what you can add to iekcorbar" }, { status: 400 });
      }

      // One pending application at a time — see schema.sql's comment on why
      // this is enforced here rather than as a DB constraint.
      const existing = await db.getStaffApplicationsByApplicant(sessionUser.email);
      if (existing.some(a => a.status === 'pending')) {
        return NextResponse.json({ error: "You already have a pending staff application — please wait for staff to review it." }, { status: 409 });
      }

      const saved = await db.submitStaffApplication({
        applicantEmail: sessionUser.email,
        applicantUsername: sessionUser.mcUsername,
        age: ageNum,
        availability: availability.trim(),
        timezone: timezone && timezone.trim() ? timezone.trim() : null,
        discordUsername: discordUsername && discordUsername.trim() ? discordUsername.trim() : null,
        experience: experience && experience.trim() ? experience.trim() : null,
        referenceName: referenceName.trim(),
        reason: reason.trim(),
        contribution: contribution.trim()
      });
      if (!saved) {
        return NextResponse.json({ error: "Could not submit your application (database unreachable). Please try again shortly." }, { status: 500 });
      }

      try {
        await notifyStaffOfApplication({
          applicantUsername: sessionUser.mcUsername || sessionUser.email,
          age: ageNum,
          availability: availability.trim(),
          reason: reason.trim()
        });
      } catch (notifyErr) {
        console.error("Staff-application staff-notification error (application itself was still saved):", notifyErr);
      }

      return NextResponse.json({ success: true });
    }

    if (action === 'decide') {
      const auth = await requirePermission(request, 'applications');
      if (auth.error) return auth.error;

      const { id, status, staffNotes } = body;
      if (!id || !DECISIONS.includes(status)) {
        return NextResponse.json({ error: "Missing id or invalid decision" }, { status: 400 });
      }

      const applications = await db.getStaffApplications();
      const application = applications.find(a => a.id === id);
      if (!application) return NextResponse.json({ error: "Application not found" }, { status: 404 });

      if (staffNotes !== undefined) {
        await db.updateStaffApplicationNotes(id, (staffNotes || '').trim() || null);
      }
      const ok = await db.updateStaffApplicationStatus(id, status, auth.user.mcUsername || auth.user.email);
      if (!ok) return NextResponse.json({ error: "Could not update that application right now" }, { status: 500 });

      const trimmedNotes = staffNotes !== undefined ? ((staffNotes || '').trim() || null) : application.staffNotes;

      await db.createUserNotification({
        recipientEmail: application.applicantEmail,
        type: 'staff_application',
        message: status === 'approved'
          ? "Your Staff Application was approved — check your email for next steps!"
          : "There's an update on your Staff Application.",
        link: '/staff-application'
      });

      try {
        const { subject, html, text } = staffApplicationDecisionEmail({
          status, staffNotes: trimmedNotes, siteUrl: process.env.SITE_URL || 'https://iekcorbar.com'
        });
        await sendMail({ to: application.applicantEmail, subject, html, text });
      } catch (mailErr) {
        console.error("Failed to email staff application decision:", mailErr);
      }

      await logAudit(auth.user, { area: 'applications', action: 'decide', target: application?.applicantUsername || id, detail: status });
      return NextResponse.json({ success: true, applications: await db.getStaffApplications() });
    }

    if (action === 'update_notes') {
      const auth = await requirePermission(request, 'applications');
      if (auth.error) return auth.error;

      const { id, staffNotes } = body;
      if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

      const ok = await db.updateStaffApplicationNotes(id, (staffNotes || '').trim() || null);
      if (!ok) return NextResponse.json({ error: "Could not update that application right now" }, { status: 500 });
      await logAudit(auth.user, { area: 'applications', action: 'update notes', target: `application ${id}` });
      return NextResponse.json({ success: true, applications: await db.getStaffApplications() });
    }

    // Staff reaching out to the applicant — e.g. to set up an interview —
    // before making a final decision. Delivered immediately, no persisted
    // thread (unlike bug reports), since this is a one-shot review, not an
    // ongoing back-and-forth.
    if (action === 'contact') {
      const auth = await requirePermission(request, 'applications');
      if (auth.error) return auth.error;

      const { id, channel, message } = body;
      if (!id || !message || !message.trim()) {
        return NextResponse.json({ error: "Missing id or message" }, { status: 400 });
      }
      if (!DELIVERY_CHANNELS.includes(channel)) {
        return NextResponse.json({ error: "Invalid delivery channel" }, { status: 400 });
      }

      const applications = await db.getStaffApplications();
      const application = applications.find(a => a.id === id);
      if (!application) return NextResponse.json({ error: "Application not found" }, { status: 404 });

      const trimmed = message.trim();

      if (channel === 'bell') {
        await db.createUserNotification({
          recipientEmail: application.applicantEmail,
          type: 'staff_application',
          message: `Staff sent you a message about your Staff Application: ${trimmed}`,
          link: '/staff-application'
        });
      } else if (channel === 'chat') {
        if (!application.applicantUsername) {
          return NextResponse.json({ error: "This applicant has no in-game username on file — use Email or Bell instead." }, { status: 400 });
        }
        await db.sendMessage(auth.user.mcUsername || 'Staff', application.applicantUsername, `[Staff Application] ${trimmed}`, null);
      } else if (channel === 'email') {
        const { subject, html, text } = staffApplicationContactEmail({
          message: trimmed, siteUrl: process.env.SITE_URL || 'https://iekcorbar.com'
        });
        try {
          await sendMail({ to: application.applicantEmail, subject, html, text });
        } catch (mailErr) {
          console.error("Failed to email staff application applicant:", mailErr);
          return NextResponse.json({ error: "Could not send the email (SMTP unreachable). Try again or use a different channel." }, { status: 502 });
        }
      }

      await logAudit(auth.user, { area: 'applications', action: 'contact applicant', target: application?.applicantUsername || id, detail: channel });
      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: "Invalid action" }, { status: 400 });
  } catch (err) {
    console.error("Staff application POST error:", err);
    return NextResponse.json({ error: "Failed to process staff application" }, { status: 500 });
  }
}

export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const mine = searchParams.get('mine');

    // A member's own submitted applications — Staff Application page's
    // status view, and the duplicate-pending check on submit.
    if (mine) {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
      return NextResponse.json({ applications: await db.getStaffApplicationsByApplicant(sessionUser.email) });
    }

    // Default — full staff view (identity + staff notes included).
    const auth = await requirePermission(request, 'applications');
    if (auth.error) return auth.error;

    return NextResponse.json({ applications: await db.getStaffApplications() });
  } catch (err) {
    console.error("Staff applications GET error:", err);
    return NextResponse.json({ error: "Failed to load staff applications" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'applications');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

    const ok = await db.deleteStaffApplication(id);
    if (!ok) return NextResponse.json({ error: "Application not found (already deleted?)" }, { status: 404 });
    return NextResponse.json({ success: true, applications: await db.getStaffApplications() });
  } catch (err) {
    console.error("Staff application DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete staff application" }, { status: 500 });
  }
}
