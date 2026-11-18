import { me } from '@/lib/auth';

// Website-/app-analyse doorgeleid van iekcorbar.com/api/staffhub/analytics
// (het voormalige Staff Panel → Analytics-tabblad; per 18-11 leeft staff-
// gereedschap hier en niet meer op de spelerswebsite).
//   SITE_ANALYTICS_URL  - default https://iekcorbar.com
//   STAFFHUB_API_TOKEN  - gedeeld geheim, ook in de .env.local van de site
// Resultaten 5 minuten gecachet: de site-cijfers lopen per dag bij, verversen
// om de seconde heeft geen zin.
export const dynamic = 'force-dynamic';

const SITE = process.env.SITE_ANALYTICS_URL || 'https://iekcorbar.com';
const TOKEN = process.env.STAFFHUB_API_TOKEN || '';
const CACHE_MS = 5 * 60 * 1000;
const cache = new Map();

export async function GET(request) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });

  if (!TOKEN) return Response.json({ error: 'STAFFHUB_API_TOKEN is niet gezet in .env op deze server' }, { status: 503 });

  const want = new URL(request.url).searchParams.get('days');
  const days = ['7', '30', '90'].includes(want) ? want : '30';

  const hit = cache.get(days);
  if (hit && Date.now() - hit.at < CACHE_MS) return Response.json(hit.data);

  try {
    const r = await fetch(`${SITE.replace(/\/$/, '')}/api/staffhub/analytics?days=${days}`, {
      headers: { 'x-staffhub-token': TOKEN },
      signal: AbortSignal.timeout(15000)
    });
    const j = await r.json().catch(() => ({ error: 'site gaf geen geldige JSON terug' }));
    if (!r.ok) return Response.json({ error: j.error || `site gaf HTTP ${r.status}` }, { status: 502 });
    cache.set(days, { at: Date.now(), data: j });
    return Response.json(j);
  } catch (e) {
    return Response.json({ error: `site niet bereikbaar: ${e.message}` }, { status: 502 });
  }
}
