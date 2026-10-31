import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite } from '@/lib/acl';

export const dynamic = 'force-dynamic';

export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'page' || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });

  d.prepare(`CREATE TABLE IF NOT EXISTS page_versions (
    id INTEGER PRIMARY KEY,
    node INTEGER NOT NULL,
    html TEXT NOT NULL,
    who INTEGER,
    created INTEGER NOT NULL,
    size INTEGER
  )`).run();

  const vs = d.prepare('SELECT v.id, v.size, v.created, u.username AS who FROM page_versions v LEFT JOIN users u ON u.id = v.who WHERE v.node = ? ORDER BY v.id DESC LIMIT 50').all(n.id);
  return Response.json({ node: n.name, versions: vs });
}

export async function POST(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'page' || !canWrite(u, n)) return Response.json({ error: 'verboden' }, { status: 403 });

  d.prepare(`CREATE TABLE IF NOT EXISTS page_versions (
    id INTEGER PRIMARY KEY,
    node INTEGER NOT NULL,
    html TEXT NOT NULL,
    who INTEGER,
    created INTEGER NOT NULL,
    size INTEGER
  )`).run();

  const p = d.prepare('SELECT html FROM pages WHERE node = ?').get(n.id);
  const html = p ? p.html : '';
  d.prepare('INSERT INTO page_versions (node, html, who, created, size) VALUES (?,?,?,?,?)')
    .run(n.id, html, u.id, now(), html.length);

  return Response.json({ ok: true });
}