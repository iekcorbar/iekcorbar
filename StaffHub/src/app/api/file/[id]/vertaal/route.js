// Vertalen van geuploade tekstbestanden (md/txt/html/log) vanuit de document-popup.
// Zelfde motor als de pagina-vertaling (lib/vertaal); bron is de laatste versie-blob.
import fs from 'node:fs';
import { getDb, blobPath, latestVersion } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';
import { htmlToMd } from '@/lib/docconv';
import { pdfNaarMd } from '@/lib/pdftext';
import { vertaalBestand, TALEN } from '@/lib/vertaal';

export const dynamic = 'force-dynamic';
export const maxDuration = 600;

const EXT = ['md', 'txt', 'html', 'htm', 'log', 'pdf'];
const MAX_BYTES = 200_000;
const MAX_PDF = 20 * 1024 * 1024;

export async function POST(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const n = getDb().prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'file' || !canSee(u, n)) return Response.json({ error: 'bestaat niet' }, { status: 404 });

  const ext = String(n.name || '').split('.').pop().toLowerCase();
  if (!EXT.includes(ext)) return Response.json({ error: 'dit bestandstype kan niet vertaald worden' }, { status: 415 });

  const { lang, opnieuw } = await req.json().catch(() => ({}));
  const taal = String(lang || '').toLowerCase();
  if (!TALEN[taal]) return Response.json({ error: 'onbekende taal' }, { status: 400 });

  const v = latestVersion(n.id);
  if (!v || !fs.existsSync(blobPath(v.sha))) return Response.json({ error: 'bestand ontbreekt' }, { status: 404 });
  const grootte = fs.statSync(blobPath(v.sha)).size;
  if (grootte > (ext === 'pdf' ? MAX_PDF : MAX_BYTES)) return Response.json({ error: 'bestand te groot om te vertalen' }, { status: 413 });
  let tekst;
  if (ext === 'pdf') {
    try { tekst = await pdfNaarMd(fs.readFileSync(blobPath(v.sha))); }
    catch { return Response.json({ error: 'deze pdf kon niet gelezen worden' }, { status: 422 }); }
    if (tekst.trim().length < 40) return Response.json({ error: 'geen tekst in deze pdf (een scan?)' }, { status: 422 });
  } else {
    tekst = fs.readFileSync(blobPath(v.sha), 'utf8');
    if (ext === 'html' || ext === 'htm') tekst = htmlToMd(tekst);
  }

  const r = await vertaalBestand(n, tekst, taal, { wie: u.id, opnieuw: Boolean(opnieuw) });
  if (!r.ok) return Response.json({ error: r.fout, stuk: r.stuk }, { status: r.fout === 'ai-uit' ? 503 : 502 });
  return Response.json({ ok: true, lang: taal, cached: Boolean(r.cached), stukken: r.stukken, controle: r.controle, html: r.html });
}
