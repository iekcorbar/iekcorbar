// Versiegeschiedenis: lijst + terugdraaien (terugdraaien = oude versie als nieuwste bovenop).
import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite } from '@/lib/acl';

export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const vs = d.prepare('SELECT v.id, v.size, v.mime, v.created, u.username AS who FROM versions v JOIN users u ON u.id = v.who WHERE v.node = ? ORDER BY v.id DESC').all(n.id);
  return Response.json({ node: n.name, versions: vs });
}

export async function POST(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const { version } = await req.json().catch(() => ({}));
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'file' || !canWrite(u, n)) return Response.json({ error: 'verboden' }, { status: 403 });
  const v = d.prepare('SELECT * FROM versions WHERE id = ? AND node = ?').get(Number(version), n.id);
  if (!v) return Response.json({ error: 'versie niet gevonden' }, { status: 404 });
  const last = d.prepare('SELECT sha FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(n.id);
  if (last && last.sha === v.sha) return Response.json({ ok: true, unchanged: true });
  d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)').run(n.id, v.sha, v.size, v.mime, u.id, now());
  d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), n.id);
  return Response.json({ ok: true });
}
