// Handleiding vertalen naar en/it (of opnieuw, als de Nederlandse tekst is
// gewijzigd). Alleen voor wie het document ook mag lezen; het resultaat gaat in
// `vertalingen` en wordt door de pagina-GESelf meegestuurd, zodat een vertaling
// één keer kosten heeft en daarna gewoon meeleest.
import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';
import { vertaalPagina, TALEN } from '@/lib/vertaal';

export const dynamic = 'force-dynamic';
// Vertalen is per definitie lang (meerdere model-beurten achter elkaar).
export const maxDuration = 600;

export async function POST(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const n = getDb().prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'page' || !canSee(u, n)) return Response.json({ error: 'bestaat niet' }, { status: 404 });

  const { lang, opnieuw } = await req.json().catch(() => ({}));
  if (!TALEN[String(lang || '').toLowerCase()]) return Response.json({ error: 'onbekende taal' }, { status: 400 });

  const r = await vertaalPagina(n.id, String(lang).toLowerCase(), { wie: u.id, opnieuw: Boolean(opnieuw) });
  if (!r.ok) return Response.json({ error: r.fout, stuk: r.stuk }, { status: r.fout === 'ai-uit' ? 503 : 502 });
  return Response.json({
    ok: true, lang, cached: Boolean(r.cached), stukken: r.stukken,
    controle: r.controle, html: r.html,
  });
}
