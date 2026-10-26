// Runs once when the Next.js server starts (Node runtime only). Starts the
// event-reminder timer (src/lib/reminders.js) — there's no cron on the Plesk
// host, so the server checks every few minutes itself. If Passenger idles the
// app, the optional Plesk scheduled task on /api/cron/reminders covers it.
export async function register() {
  if (process.env.NEXT_RUNTIME !== 'nodejs') return;
  if (global.__iekcorbar_reminder_timer) return;
  const { runEventReminders } = await import('./lib/reminders');
  global.__iekcorbar_reminder_timer = setInterval(() => {
    runEventReminders().catch(() => {});
  }, 5 * 60 * 1000);
  console.log('[Reminders] Event reminder timer started (every 5 minutes)');
  // First check shortly after startup.
  setTimeout(() => runEventReminders().catch(() => {}), 30 * 1000);
}
