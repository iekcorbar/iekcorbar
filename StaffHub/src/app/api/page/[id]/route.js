// Manual-pagina's: HTML uit de TipTap-bewerker, plus een geschoren platte tekst
// voor de zoekindex.
import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite } from '@/lib/acl';

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
  return Response.json({ node: { id: n.id, name: n.name, updated: n.updated }, html: p ? p.html : '', who: p ? p.who : n.owner });
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
