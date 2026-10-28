// Manual-pagina's: HTML uit de TipTap-bewerker, plus een geschoren platte tekst
// voor de zoekindex.
import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite } from '@/lib/acl';
import { htmlToMd } from '@/lib/docconv';

export function stripHtml(html) {
  return String(html || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').slice(0, 20000);
}

export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'page' || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const p = d.prepare('SELECT * FROM pages WHERE node = ?').get(n.id);
  let html = p ? p.html : '';

  // ?lang=it = de VERTALING van dit document opvragen, niet het origineel: de
  // brontaal is de bron van waarheid en staat als één rij in `pages`. Zonder deze
  // regel was een gemaakte vertaling alleen in het AI-bord te zien, en verdween
  // hij uit het oog zodra je de handleiding zelf opende.
  const gevraagd = String(new URL(req.url).searchParams.get('lang') || '').toLowerCase();
  let gekozen = null;
  if (gevraagd && gevraagd !== 'nl') {
    const v = d.prepare('SELECT html, updated FROM vertalingen WHERE node = ? AND lang = ?').get(n.id, gevraagd);
    if (!v) return Response.json({ error: 'nog niet vertaald' }, { status: 404 });
    html = v.html; gekozen = gevraagd;
  }

  // ?fmt=md of ?fmt=html = DOWNLOADEN: een handleiding is niet alleen iets om in
  // te kijken maar ook iets om mee te nemen (in de bus, zonder bereik, of in een
  // andere editor). Zonder deze regel was een pagina de enige soort in de hub die
  // je niet als bestand kon krijgen. Met ?lang mee: dan gaat de VERTALING mee.
  const fmt = String(new URL(req.url).searchParams.get('fmt') || '').toLowerCase();
  if (fmt === 'md' || fmt === 'html') {
    const schoon = String(n.name).replace(/[\\/:*?"<>|\u0000-\u001f]/g, '').trim() || 'handleiding';
    const bestandsnaam = schoon + (gekozen ? '-' + gekozen : '') + (fmt === 'md' ? '.md' : '.html');
    const body = fmt === 'md'
      ? htmlToMd(html)
      : `<!doctype html><html lang="${gekozen || 'nl'}"><head><meta charset="utf-8"><title>${encodeURIComponent(n.name)}</title>` +
        `<style>body{font:15px/1.6 system-ui;max-width:46rem;margin:3rem auto;padding:0 1rem;color:#1b1b1b}img{max-width:100%}</style>` +
        `</head><body>${html}</body></html>`;
    return new Response(body, {
      headers: {
        'content-type': fmt === 'md' ? 'text/markdown; charset=utf-8' : 'text/html; charset=utf-8',
        'content-disposition': `attachment; filename*=UTF-8''${encodeURIComponent(bestandsnaam)}`,
        'cache-control': 'no-store',
      },
    });
  }
  // `varianten`: wat er al klaarligt, zodat de editor er tabbladen van kan maken.
  const varianten = d.prepare('SELECT lang, updated FROM vertalingen WHERE node = ?').all(n.id)
    .map((v) => ({ lang: v.lang, updated: v.updated, verouderd: v.updated < n.updated }));
  return Response.json({
    node: { id: n.id, name: n.name, updated: n.updated }, html, wie: p ? p.who : n.owner,
    gekozen, varianten,
  });
}

export async function PUT(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const { html } = await req.json().catch(() => ({}));
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'page' || !canWrite(u, n)) return Response.json({ error: 'verboden' }, { status: 403 });
  const safe = String(html || '').slice(0, 2_000_000);
  const cur = d.prepare('SELECT node FROM pages WHERE node = ?').get(n.id);
  if (cur) d.prepare('UPDATE pages SET html = ?, who = ?, updated = ? WHERE node = ?').run(safe, u.id, now(), n.id);
  else d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)').run(n.id, safe, u.id, now());
  d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), n.id);
  return Response.json({ ok: true });
}
