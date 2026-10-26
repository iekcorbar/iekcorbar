import { NextResponse } from 'next/server';
import { runEventReminders } from '@/lib/reminders';

// Optional safety net for event reminders: a Plesk scheduled task ("Fetch a
// URL", every 5 minutes) calls https://iekcorbar.com/api/cron/reminders?key=<CRON_SECRET>.
// It also wakes the app up if Passenger idled it. CRON_SECRET lives in .env.local.
export async function GET(request) {
  const key = new URL(request.url).searchParams.get('key');
  if (!process.env.CRON_SECRET || key !== process.env.CRON_SECRET) {
    return NextResponse.json({ error: "Forbidden" }, { status: 403 });
  }
  const result = await runEventReminders({ force: true });
  return NextResponse.json({ success: true, ...result });
}
