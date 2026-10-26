// Generic outbound email helper. Mirrors src/lib/rcon.js's "BYPASS" pattern:
// if SMTP isn't configured (no mailbox exists yet, or it's local dev),
// sendMail() no-ops and returns "BYPASS" instead of throwing, so callers can
// still save whatever they were emailing about (e.g. a bug report) and just
// skip the notification step rather than failing the whole request.
//
// Default mailbox (bug reports, staff report notifications) via:
//   SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, SMTP_FROM (optional,
//   defaults to SMTP_USER)
//
// A caller can send through a *different* real mailbox on the same server
// by passing `account: { user, password, from? }` — used by the newsletter
// (NEWSLETTER_SMTP_USER/PASSWORD, a genuinely separate Plesk account, not
// an alias) so newsletters don't appear to come from the bug-report inbox.
// Host/port are assumed shared (same mail server) since that's what every
// mailbox on this Plesk instance actually is; if a future account needs a
// different host, extend `account` to include one instead of assuming.
import nodemailer from 'nodemailer';

const _transporters = new Map();

function _getTransporter({ host, port, user, password }) {
  const key = `${host}:${port}:${user}`;
  if (_transporters.has(key)) return _transporters.get(key);
  const transporter = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user, pass: password },
  });
  _transporters.set(key, transporter);
  return transporter;
}

/**
 * Sends an email — plain-text only if `html` is omitted (all existing
 * callers, e.g. bug reports), or a real HTML email alongside a plain-text
 * fallback when it's given (nodemailer picks whichever the recipient's
 * client prefers). `bcc` is for broadcasting to a list of staff addresses
 * without exposing each recipient's address to the others. `account`
 * overrides which real mailbox this send authenticates as and appears
 * from — omit it to use the default (bug-report) mailbox. Returns "BYPASS"
 * (no-op) if the resolved account isn't configured, "OK" on success, or
 * throws if it's configured but sending actually failed (a real error the
 * caller should surface, unlike the not-configured case).
 */
export async function sendMail({ to, bcc, subject, text, html, account }) {
  const host = process.env.SMTP_HOST;
  const port = parseInt(process.env.SMTP_PORT) || 587;
  const user = account?.user || process.env.SMTP_USER;
  const password = account?.password || process.env.SMTP_PASSWORD;
  const from = account?.from || process.env.SMTP_FROM || user;

  if (!host || !user || !password) {
    console.warn("SMTP not configured for this account (host/user/password missing). Skipping email send.");
    return "BYPASS";
  }

  const transporter = _getTransporter({ host, port, user, password });
  await transporter.sendMail({ from, to, bcc, subject, text, html });
  return "OK";
}
