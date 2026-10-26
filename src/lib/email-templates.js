// Reusable HTML email templates. First one of these in the codebase —
// api/bug-reports has been sending plain-text-only mail via src/lib/mail.js
// since it was built. Table-based layout with all styles inlined (no
// external stylesheet, no web fonts) since that's what actually renders
// consistently across real email clients (Outlook especially ignores most
// modern CSS) — this is the email-specific reason it looks different from
// how the rest of this site is styled.
const BRAND_GOLD = '#d4af37';
const BRAND_DARK = '#1a1512';
const BRAND_PANEL = '#241d18';
const BRAND_TEXT = '#e8ddc9';
const BRAND_MUTED = '#a8998a';

function wrapEmail({ preheader, title, bodyHtml, ctaLabel, ctaUrl, footerHtml }) {
  return `<!doctype html>
<html>
  <body style="margin:0;padding:0;background:${BRAND_DARK};font-family:Georgia,'Times New Roman',serif;">
    <span style="display:none;font-size:1px;color:${BRAND_DARK};line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">${preheader || ''}</span>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:${BRAND_DARK};padding:32px 16px;">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;background:${BRAND_PANEL};border:1px solid #3a2f22;border-radius:12px;overflow:hidden;">
            <tr>
              <td style="padding:20px 28px;border-bottom:1px solid #3a2f22;">
                <span style="font-size:13px;letter-spacing:2px;text-transform:uppercase;color:${BRAND_GOLD};font-weight:bold;">iekcorbar</span>
              </td>
            </tr>
            <tr>
              <td style="padding:28px;">
                <h1 style="margin:0 0 16px;font-size:20px;color:${BRAND_GOLD};text-transform:uppercase;letter-spacing:1px;">${title}</h1>
                <div style="font-size:14px;line-height:1.6;color:${BRAND_TEXT};">${bodyHtml}</div>
                ${ctaUrl ? `
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin-top:24px;">
                  <tr>
                    <td style="border-radius:8px;background:${BRAND_GOLD};">
                      <a href="${ctaUrl}" style="display:inline-block;padding:11px 22px;font-size:12px;font-weight:bold;letter-spacing:1px;text-transform:uppercase;color:#1a1512;text-decoration:none;">${ctaLabel}</a>
                    </td>
                  </tr>
                </table>` : ''}
              </td>
            </tr>
            <tr>
              <td style="padding:16px 28px;border-top:1px solid #3a2f22;">
                <span style="font-size:11px;color:${BRAND_MUTED};">${footerHtml || `You're receiving this because you're staff on iekcorbar. Manage this from the Staff Panel.`}</span>
              </td>
            </tr>
          </table>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}

function escapeHtml(str) {
  return String(str ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// New anonymous player report — see api/reports' POST.
export function reportNotificationEmail({ targetUsername, category, details, siteUrl = 'https://iekcorbar.com' }) {
  const subject = `New Player Report: ${targetUsername} (${category})`;
  const ctaUrl = `${siteUrl}/moderation?tab=reports`;

  const bodyHtml = `
    <p style="margin:0 0 12px;">A new anonymous player report just came in.</p>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#1a1512;border-radius:8px;margin:0 0 4px;">
      <tr><td style="padding:10px 14px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Reported Player</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:15px;color:${BRAND_GOLD};font-weight:bold;">${escapeHtml(targetUsername)}</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Category</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:14px;color:${BRAND_TEXT};">${escapeHtml(category)}</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Details</td></tr>
      <tr><td style="padding:0 14px 14px;font-size:14px;color:${BRAND_TEXT};white-space:pre-wrap;">${escapeHtml(details)}</td></tr>
    </table>
    <p style="margin:14px 0 0;font-size:12px;color:${BRAND_MUTED};">No reporter identity is stored — this report is fully anonymous, same as it appears in the Staff Panel.</p>
  `;

  const text = `New Player Report: ${targetUsername} (${category})\n\nDetails:\n${details}\n\nView in the Staff Panel: ${ctaUrl}`;

  return {
    subject,
    html: wrapEmail({ preheader: `New report against ${targetUsername}`, title: 'New Player Report', bodyHtml, ctaLabel: 'Open Reports Inbox', ctaUrl }),
    text
  };
}

// Staff replying to a bug report via the "Contact Reporter" action's Email
// channel — see api/bug-reports' POST action 'contact'. `message` is the
// staff member's own free-text reply.
export function bugReportReplyEmail({ subject, message, siteUrl = 'https://iekcorbar.com' }) {
  const emailSubject = `Re: your bug report "${subject}"`;
  const ctaUrl = `${siteUrl}/profile`;

  const bodyHtml = `
    <p style="margin:0 0 12px;">Staff replied to your bug report:</p>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#1a1512;border-radius:8px;margin:0 0 4px;">
      <tr><td style="padding:10px 14px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">${escapeHtml(subject)}</td></tr>
      <tr><td style="padding:0 14px 14px;font-size:14px;color:${BRAND_TEXT};white-space:pre-wrap;">${escapeHtml(message)}</td></tr>
    </table>
    <p style="margin:14px 0 0;font-size:12px;color:${BRAND_MUTED};">You can reply from your own Profile page — see "My Bug Reports".</p>
  `;

  const text = `Staff replied to your bug report "${subject}":\n\n${message}\n\nReply from your Profile page: ${ctaUrl}`;

  return {
    subject: emailSubject,
    html: wrapEmail({ preheader: `Staff replied to "${subject}"`, title: 'Bug Report Update', bodyHtml, ctaLabel: 'Open My Profile', ctaUrl }),
    text
  };
}

// New Staff Application submission — see api/staff-applications' POST
// action 'submit'. BCC'd to every staff email, same pattern as
// reportNotificationEmail above.
export function staffApplicationNotificationEmail({ applicantUsername, age, availability, reason, siteUrl = 'https://iekcorbar.com' }) {
  const subject = `New Staff Application: ${applicantUsername}`;
  const ctaUrl = `${siteUrl}/moderation?tab=applications`;

  const bodyHtml = `
    <p style="margin:0 0 12px;">A player just applied for a Staff role.</p>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#1a1512;border-radius:8px;margin:0 0 4px;">
      <tr><td style="padding:10px 14px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Applicant</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:15px;color:${BRAND_GOLD};font-weight:bold;">${escapeHtml(applicantUsername)} (age ${escapeHtml(age)})</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Availability</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:14px;color:${BRAND_TEXT};">${escapeHtml(availability)}</td></tr>
      <tr><td style="padding:0 14px 10px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Why they want to join staff</td></tr>
      <tr><td style="padding:0 14px 14px;font-size:14px;color:${BRAND_TEXT};white-space:pre-wrap;">${escapeHtml(reason)}</td></tr>
    </table>
    <p style="margin:14px 0 0;font-size:12px;color:${BRAND_MUTED};">Full details, including their in-game experience and what they think they can add, are in the Staff Panel.</p>
  `;

  const text = `New Staff Application: ${applicantUsername} (age ${age})\n\nAvailability: ${availability}\n\nReason:\n${reason}\n\nReview in the Staff Panel: ${ctaUrl}`;

  return {
    subject,
    html: wrapEmail({ preheader: `New staff application from ${applicantUsername}`, title: 'New Staff Application', bodyHtml, ctaLabel: 'Open Staff Applications', ctaUrl }),
    text
  };
}

// Staff approving/declining an application — see api/staff-applications'
// POST action 'decide'. `staffNotes`, if given, is only ever a short
// explanation staff chose to share, never the internal-only notes field.
export function staffApplicationDecisionEmail({ status, staffNotes, siteUrl = 'https://iekcorbar.com' }) {
  const approved = status === 'approved';
  const subject = approved ? 'Your iekcorbar Staff Application was approved!' : 'Update on your iekcorbar Staff Application';
  const ctaUrl = `${siteUrl}/staff-application`;

  const bodyHtml = `
    <p style="margin:0 0 12px;">${approved
      ? "Congratulations — your application for a Staff role has been <strong style=\"color:" + BRAND_GOLD + ";\">approved</strong>! A member of the team will reach out with next steps."
      : "Thanks for applying — after review, your application for a Staff role was not accepted this time."}</p>
    ${staffNotes ? `
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#1a1512;border-radius:8px;margin:8px 0 4px;">
      <tr><td style="padding:10px 14px;font-size:12px;color:${BRAND_MUTED};text-transform:uppercase;letter-spacing:0.5px;">Note from staff</td></tr>
      <tr><td style="padding:0 14px 14px;font-size:14px;color:${BRAND_TEXT};white-space:pre-wrap;">${escapeHtml(staffNotes)}</td></tr>
    </table>` : ''}
    ${!approved ? `<p style="margin:14px 0 0;font-size:12px;color:${BRAND_MUTED};">You're welcome to apply again in the future.</p>` : ''}
  `;

  const text = `${approved ? 'Your Staff Application was approved!' : 'Your Staff Application was not accepted this time.'}${staffNotes ? `\n\nNote from staff:\n${staffNotes}` : ''}`;

  return {
    subject,
    html: wrapEmail({ preheader: subject, title: approved ? 'Application Approved' : 'Application Update', bodyHtml, ctaLabel: 'Open My Profile', ctaUrl: `${siteUrl}/profile` }),
    text
  };
}

// Staff reaching out to an applicant via the "Contact Applicant" action's
// Email channel — see api/staff-applications' POST action 'contact'.
export function staffApplicationContactEmail({ message, siteUrl = 'https://iekcorbar.com' }) {
  const subject = 'A message about your iekcorbar Staff Application';
  const ctaUrl = `${siteUrl}/profile`;

  const bodyHtml = `
    <p style="margin:0 0 12px;">Staff sent you a message about your Staff Application:</p>
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#1a1512;border-radius:8px;margin:0 0 4px;">
      <tr><td style="padding:14px;font-size:14px;color:${BRAND_TEXT};white-space:pre-wrap;">${escapeHtml(message)}</td></tr>
    </table>
  `;

  const text = `Staff sent you a message about your Staff Application:\n\n${message}`;

  return {
    subject,
    html: wrapEmail({ preheader: 'A message about your Staff Application', title: 'Staff Application', bodyHtml, ctaLabel: 'Open My Profile', ctaUrl }),
    text
  };
}

// Real email clients (Outlook especially) ignore a <style> block, so the
// only reliable way to style tags is inline. `bodyHtml` here always comes
// from RichTextEditor's Tiptap instance (StarterKit + a Link extension
// only — see that component's comment), which only ever emits this fixed,
// known set of tags, never arbitrary/injected markup — so a plain
// string-replace pass is safe and doesn't need a real HTML parser.
function inlineEmailStyles(html) {
  return html
    .replace(/<p>/g, `<p style="margin:0 0 12px;color:${BRAND_TEXT};">`)
    .replace(/<h2>/g, `<h2 style="margin:16px 0 8px;font-size:17px;color:${BRAND_GOLD};">`)
    .replace(/<h3>/g, `<h3 style="margin:14px 0 6px;font-size:15px;color:${BRAND_GOLD};">`)
    .replace(/<ul>/g, `<ul style="margin:0 0 12px;padding-left:20px;color:${BRAND_TEXT};">`)
    .replace(/<ol>/g, `<ol style="margin:0 0 12px;padding-left:20px;color:${BRAND_TEXT};">`)
    .replace(/<li>/g, `<li style="margin:0 0 4px;">`)
    .replace(/<a /g, `<a style="color:${BRAND_GOLD};text-decoration:underline;" `)
    .replace(/<strong>/g, `<strong style="color:${BRAND_GOLD};">`);
}

// Strips tags for the plain-text fallback nodemailer sends alongside the
// HTML version (some clients/spam filters prefer it). Not meant to be
// pretty, just readable — <br>/<p> become real line breaks first so
// paragraphs don't run together.
function htmlToPlainText(html) {
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

// "Forgot password?" reset link — see api/auth/forgot-password's 'request'
// action. `resetUrl` embeds the single-use token (src/lib/db.js's
// password_resets table), not the token alone, so the email just needs one
// clickable link.
export function passwordResetEmail({ resetUrl, expiresInMinutes = 30, siteUrl = 'https://iekcorbar.com' }) {
  const subject = 'Reset your iekcorbar password';

  const bodyHtml = `
    <p style="margin:0 0 12px;">Someone (hopefully you) asked to reset the password on this iekcorbar account.</p>
    <p style="margin:0 0 12px;">Click the button below to choose a new password. This link works once and expires in ${expiresInMinutes} minutes.</p>
    <p style="margin:14px 0 0;font-size:12px;color:${BRAND_MUTED};">Didn't request this? You can safely ignore this email — your password won't change unless the link above is used.</p>
  `;

  const text = `Someone (hopefully you) asked to reset the password on this iekcorbar account.\n\nOpen this link to choose a new password (works once, expires in ${expiresInMinutes} minutes):\n${resetUrl}\n\nDidn't request this? You can safely ignore this email — your password won't change unless the link above is used.`;

  return {
    subject,
    html: wrapEmail({ preheader: 'Reset your iekcorbar password', title: 'Password Reset', bodyHtml, ctaLabel: 'Choose New Password', ctaUrl: resetUrl, footerHtml: `Sent because a password reset was requested for this email address on ${siteUrl.replace(/^https?:\/\//, '')}.` }),
    text
  };
}

// Admin-composed newsletter broadcast — see api/admin/newsletter's POST.
// `bodyHtml` is rich HTML from the WYSIWYG compose form (RichTextEditor /
// Tiptap), not plain text. `unsubscribeUrl` is per-recipient (embeds that
// one player's email + a signed token, see src/lib/unsubscribe-token.js),
// which is why this gets called once per recipient rather than sent as a
// single bcc'd blast — a shared unsubscribe link can't identify who
// clicked it.
export function newsletterEmail({ subject, bodyHtml, unsubscribeUrl }) {
  const footerHtml = `You're receiving this because you're a registered iekcorbar player. <a href="${unsubscribeUrl}" style="color:${BRAND_MUTED};text-decoration:underline;">Unsubscribe from these emails</a>.`;

  const text = `${htmlToPlainText(bodyHtml)}\n\n---\nUnsubscribe: ${unsubscribeUrl}`;

  return {
    subject,
    html: wrapEmail({ preheader: subject, title: subject, bodyHtml: inlineEmailStyles(bodyHtml), footerHtml }),
    text
  };
}
