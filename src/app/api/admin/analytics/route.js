import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';
import { normalizePushPreferences } from '@/lib/push';

// Staff Panel → Analytics. Permission 'analytics' (admins always; other roles
// only if granted in Roles & Permissions). Players are shown by Minecraft
// name, never by email.
export async function GET(request) {
  const auth = await requirePermission(request, 'analytics');
  if (auth.error) return auth.error;

  const days = new URL(request.url).searchParams.get('days') || 30;
  try {
    const a = await db.getAnalytics(days);
    const users = await db.getUsers();
    const byEmail = new Map(users.map(u => [u.email.toLowerCase(), u]));
    const nameOf = (email) => byEmail.get(String(email).toLowerCase())?.mcUsername || '(unknown)';
    const num = (v) => Number(v) || 0;

    // One row per player: website/app activity in the range + push devices.
    const rows = new Map();
    const row = (email) => {
      const key = String(email).toLowerCase();
      if (!rows.has(key)) rows.set(key, { email: key, player: nameOf(email), views: 0, platforms: [], appVersion: null, firstSeen: null, lastSeen: null, androidPush: false, webPush: [] });
      return rows.get(key);
    };
    for (const p of a.players || []) {
      const r = row(p.email);
      r.views = num(p.views);
      r.platforms = String(p.platforms || '').split(',').filter(Boolean);
      r.appVersion = p.appVersion || null;
      r.firstSeen = p.firstSeen;
      r.lastSeen = p.lastSeen;
    }
    for (const dvc of a.androidDevices || []) row(dvc.email).androidPush = true;
    for (const w of a.webPush || []) {
      const ua = w.userAgent || '';
      row(w.email).webPush.push(/iPhone|iPad/.test(ua) ? 'iPhone' : /Android/.test(ua) ? 'Android browser' : 'Desktop');
    }
    const prefs = await db.getPushPreferencesFor([...rows.keys()]);
    const players = [...rows.values()].map(r => {
      const p = normalizePushPreferences(prefs.get(r.email));
      const { email, ...rest } = r; // never send emails to the client
      return { ...rest, notificationsOff: !p.enabled };
    }).sort((x, y) => new Date(y.lastSeen || 0) - new Date(x.lastSeen || 0));

    const list = (rowsIn, fields = ['views', 'visits']) => (rowsIn || []).map(r => {
      const o = { label: r.label };
      for (const f of fields) o[f] = num(r[f]);
      return o;
    });

    return NextResponse.json({
      days: a.days,
      totals: {
        pageviews: num(a.totals?.pageviews),
        visits: num(a.totals?.visits),
        activePlayers: num(a.totals?.activePlayers),
        appUsers: num(a.totals?.appUsers),
        downloads: num(a.totals?.downloads),
        visitorsToday: num(a.today?.visitors),
        pageviewsToday: num(a.today?.pageviews),
        androidPushPlayers: (a.androidDevices || []).length,
        webPushDevices: (a.webPush || []).length
      },
      daily: (a.daily || []).map(d => ({ day: d.day, pageviews: num(d.pageviews), visitors: num(d.visitors), downloads: num(d.downloads) })),
      topPages: list(a.topPages),
      platforms: list(a.platforms),
      browsers: list(a.browsers),
      oses: list(a.oses),
      referrers: list(a.referrers),
      appVersions: list(a.appVersions, ['users']),
      players
    });
  } catch (err) {
    console.error('Analytics API error:', err);
    return NextResponse.json({ error: 'Could not load analytics (is the analytics_events table created?)' }, { status: 500 });
  }
}
