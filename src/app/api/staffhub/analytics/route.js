import { NextResponse } from 'next/server';
import crypto from 'node:crypto';
import { db } from '@/lib/db';
import { normalizePushPreferences } from '@/lib/push';
import { pluginDataQuery } from '@/lib/plugin-data-db';

// Machine-to-machine feed for StaffHub's "Website-analyse" screen (the old
// Staff Panel → Analytics tab, moved to StaffHub per owner 18-11: the public
// website is for players, staff tooling lives in StaffHub).
//
// Auth: header `x-staffhub-token` must match the shared secret, looked up in
// two places, first hit wins:
//   1. env STAFFHUB_API_TOKEN (site .env.local on Plesk) - optional
//   2. plugin MySQL table staffhub_shared, row k='analytics_token' - the
//      practical half (owner "fix 2", 19-11): StaffHub already carries its
//      token in /opt/staffhub/.env, and this site reaches that same DB via
//      the existing PLUGIN_DB_* credentials, so no Plesk env edit is needed.
//      The plugin DB stays read-only from the site, as everywhere else - the
//      row itself was written once by the agent over SSH on the VPS.
// No secret anywhere => 503; wrong token => 401. Payload is email-free.
export const dynamic = 'force-dynamic';

async function authorized(request) {
  let want = process.env.STAFFHUB_API_TOKEN || '';
  if (!want) {
    try {
      const rows = await pluginDataQuery("SELECT v FROM staffhub_shared WHERE k = 'analytics_token' LIMIT 1");
      want = rows?.[0]?.v || '';
    } catch { return 'unconfigured'; }
  }
  if (!want) return 'unconfigured';
  const got = request.headers.get('x-staffhub-token') || '';
  try {
    const a = Buffer.from(String(want));
    const b = Buffer.from(got);
    return a.length === b.length && crypto.timingSafeEqual(a, b) ? 'ok' : 'denied';
  } catch {
    return 'denied';
  }
}

export async function GET(request) {
  const auth = await authorized(request);
  if (auth === 'unconfigured') return NextResponse.json({ error: 'shared analytics token is not configured (env or staffhub_shared)' }, { status: 503 });
  if (auth !== 'ok') return NextResponse.json({ error: 'unauthorized' }, { status: 401 });

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
      const { email, ...rest } = r; // never send emails over this wire either
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
    console.error('StaffHub analytics API error:', err);
    return NextResponse.json({ error: 'Could not load analytics (is the analytics_events table created?)' }, { status: 500 });
  }
}
