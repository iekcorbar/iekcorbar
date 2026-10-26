import { db, eventStartUtcMs } from '@/lib/db';
import { pushBellNotification } from '@/lib/push';
import { sendRconCommand } from '@/lib/rcon';
import crypto from 'crypto';

// Event reminders via push (requested 2026-09-27):
//  - '24h': signed-up players get "tomorrow" (bell + push); everyone else gets
//    a push-only "still time to sign up" invite with a Sign up button.
//  - '1h' : signed-up players get "starts in 1 hour" (bell + push).
// Runs every few minutes from src/instrumentation.js, from GET /api/events,
// and from /api/cron/reminders (Plesk scheduled task, optional safety net
// for when Passenger has idled the app). Each (event, kind) is claimed in the
// event_reminders_sent table first, so multiple Passenger processes or
// triggers never send the same reminder twice.
// Push categories: type 'event_reminder' → the "events" switch on the Profile page.

const HOUR = 60 * 60 * 1000;
const REMINDERS = [
  // kind, send when start - before <= now, but only if at least `minLead` before start
  { kind: '24h', before: 24 * HOUR, minLead: 3 * HOUR },
  { kind: '1h', before: 1 * HOUR, minLead: -10 * 60 * 1000 } // up to 10 min after start (late trigger)
];
const RUN_INTERVAL_MS = 2 * 60 * 1000;
let lastRun = 0;
let running = false;

// --- Daily "Tip of the day" push -----------------------------------------
// Once a day at DAILY_TIP_HOUR (Europe/Amsterdam), everyone gets today's tip
// — the same one the homepage shows (api/tips: day-of-year modulo active
// tips). Push only, no bell entry (the bell would fill up daily). If the
// server was idle at 10:00, it still goes out on the first check before
// DAILY_TIP_LAST_HOUR. Claimed per date in event_reminders_sent
// ('tip:YYYY-MM-DD'), so it's sent exactly once per day.
// Push category 'tips' → its own "Daily tip of the day" switch on the Profile page.
const DAILY_TIP_HOUR = 10;
const DAILY_TIP_LAST_HOUR = 20;

function amsterdamNow() {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Amsterdam', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23'
  }).formatToParts(new Date()).map(p => [p.type, p.value]));
  return { date: `${parts.year}-${parts.month}-${parts.day}`, hour: Number(parts.hour) };
}

function dayOfYear(date) {
  const start = new Date(date.getFullYear(), 0, 0);
  return Math.floor((date - start) / (1000 * 60 * 60 * 24));
}

async function runDailyTip() {
  const { date, hour } = amsterdamNow();
  if (hour < DAILY_TIP_HOUR || hour >= DAILY_TIP_LAST_HOUR) return;
  const tips = (await db.getTips()).filter(t => t.active);
  if (tips.length === 0) return;
  if (!(await db.claimEventReminder(`tip:${date}`, 'tip'))) return;
  const tip = tips[dayOfYear(new Date()) % tips.length];
  const users = await db.getUsers();
  const text = String(tip.content || '').replace(/\s+/g, ' ').trim();
  await pushBellNotification({
    recipients: users.map(u => u.email),
    type: 'daily_tip',
    message: text,
    link: '/',
    pushTitle: '💡 Tip of the day',
    pushBody: text.length > 240 ? `${text.slice(0, 239)}…` : text,
    tag: 'daily-tip'
  });
  console.log(`[Reminders] Sent daily tip for ${date}`);
}

// --- Account linking deadlines (new registrations, see api/auth/register) --
//  - ~24h before the deadline: one reminder (bell + push).
//  - deadline passed: removed from the whitelist, kicked if online, account
//    'on_hold' (bell + push). If RCON fails the status is left alone and it's
//    retried on the next run, so nobody is put on hold while still whitelisted.
// Short reactivation windows (1 hour, api/account/link) get no reminder.
const LINK_WARN_BEFORE_MS = 24 * 60 * 60 * 1000;
const HOLD_KICK_MESSAGE = 'Your Iekcorbar account is on hold - link it at iekcorbar.com/link';

async function runLinkDeadlines() {
  await db.prunePendingRegistrations();
  const states = await db.getLinkStates();
  for (const s of states || []) {
    if (s.status !== 'pending' || !s.deadline || !s.mcUsername) continue;
    const left = new Date(s.deadline).getTime() - Date.now();

    if (left <= 0) {
      try {
        await sendRconCommand(`whitelist remove ${s.mcUsername}`);
      } catch (err) {
        console.error(`[Link] Could not remove ${s.mcUsername} from whitelist, retrying later:`, err.message);
        continue;
      }
      try { await sendRconCommand(`kick ${s.mcUsername} ${HOLD_KICK_MESSAGE}`); } catch { /* not online */ }
      await db.updateLinkState(s.email, { status: 'on_hold', code: null, codeExpires: null });
      await db.createUserNotification({
        recipientEmail: s.email,
        type: 'account_link',
        message: `Your account is on hold because "${s.mcUsername}" wasn't linked in time. Link it to get back on the server.`,
        link: '/link',
        pushTitle: '⏸️ Your account is on hold',
        pushBody: "You didn't link your Minecraft account in time. Tap to link it and get back on the server.",
        tag: 'account-link'
      });
      console.log(`[Link] ${s.mcUsername} put on hold (not linked before deadline)`);
    } else if (left <= LINK_WARN_BEFORE_MS && left > 60 * 60 * 1000) {
      const key = 'lw:' + crypto.createHash('sha1').update(`${s.email}|${new Date(s.deadline).toISOString()}`).digest('hex').slice(0, 40);
      if (!(await db.claimEventReminder(key, 'warn'))) continue;
      const hours = Math.max(1, Math.round(left / (60 * 60 * 1000)));
      await db.createUserNotification({
        recipientEmail: s.email,
        type: 'account_link',
        message: `Only ${hours} hours left to link your Minecraft account "${s.mcUsername}" — after that you'll be removed from the whitelist.`,
        link: '/link',
        pushTitle: '⏳ Link your account today',
        pushBody: `${hours} hours left to link "${s.mcUsername}" — it takes 1 minute.`,
        tag: 'account-link'
      });
    }
  }
}

function whenText(event) {
  return `${event.time || ''}${event.timezone ? ` (${event.timezone})` : ''}`.trim();
}

export async function runEventReminders({ force = false } = {}) {
  if (running || (!force && Date.now() - lastRun < RUN_INTERVAL_MS)) return { skipped: true };
  running = true;
  lastRun = Date.now();
  const sent = [];
  try {
    const events = await db.getEvents();
    let users = null;
    let registrations = null;

    for (const event of events || []) {
      const start = eventStartUtcMs(event.date, event.time, event.timezone);
      if (start === null) continue;
      const lead = start - Date.now();

      for (const r of REMINDERS) {
        if (lead > r.before || lead < r.minLead) continue;
        // Atomic claim: only the first process/trigger gets true.
        if (!(await db.claimEventReminder(event.id, r.kind))) continue;

        users = users || await db.getUsers();
        registrations = registrations || await db.getRegistrations();
        const regNames = new Set((registrations || [])
          .filter(x => String(x.eventId) === String(event.id))
          .map(x => String(x.username).toLowerCase()));
        const registered = users.filter(u => u.mcUsername && regNames.has(u.mcUsername.toLowerCase()));
        const others = users.filter(u => !(u.mcUsername && regNames.has(u.mcUsername.toLowerCase())));
        const link = `/events?event=${encodeURIComponent(event.id)}`;
        const tag = `event-reminder-${event.id}`;

        if (r.kind === '24h') {
          for (const u of registered) {
            await db.createUserNotification({
              recipientEmail: u.email,
              type: 'event_reminder',
              message: `Reminder: "${event.title}" is tomorrow at ${whenText(event)}.`,
              link,
              pushTitle: `⏰ Tomorrow: ${event.title}`,
              pushBody: `Starts at ${whenText(event)}. You're signed up — see you there!`,
              tag,
              actions: [{ label: 'View event', link }]
            });
          }
          if (others.length > 0) {
            // Push only (no bell entry for everyone) — an invite, not a reminder.
            await pushBellNotification({
              recipients: others.map(u => u.email),
              type: 'event_reminder',
              message: `"${event.title}" is tomorrow — still time to sign up!`,
              link,
              pushTitle: `📅 ${event.title} is tomorrow!`,
              pushBody: `Starts at ${whenText(event)}. There's still time to join — sign up now!`,
              tag,
              actions: [{ label: 'Sign up', link: `${link}&signup=1` }, { label: 'View', link }]
            });
          }
        } else {
          for (const u of registered) {
            await db.createUserNotification({
              recipientEmail: u.email,
              type: 'event_reminder',
              message: `"${event.title}" starts in 1 hour.`,
              link,
              pushTitle: `🔔 ${event.title} starts in 1 hour`,
              pushBody: 'Get ready and hop on the server — see you there!',
              tag,
              actions: [{ label: 'View event', link }]
            });
          }
        }
        sent.push(`${event.title}:${r.kind}`);
        console.log(`[Reminders] Sent ${r.kind} reminder for "${event.title}" (${registered.length} signed up)`);
      }
    }
    await runDailyTip();
    await runLinkDeadlines();
  } catch (err) {
    console.error('[Reminders] Failed to run event reminders:', err);
  } finally {
    running = false;
  }
  return { sent };
}
