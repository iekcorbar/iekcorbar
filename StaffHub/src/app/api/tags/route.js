import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { may, canSee } from '@/lib/acl';

export const dynamic = 'force-dynamic';

export async function GET(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY, name TEXT UNIQUE, color TEXT DEFAULT '#gold')`).run();
  d.prepare(`CREATE TABLE IF NOT EXISTS node_tags (node INTEGER, tag INTEGER, PRIMARY KEY(node, tag))`).run();
  const nodeId = new URL(req.url).searchParams.get('node');
  if (nodeId) {
    const tags = d.prepare('SELECT t.* FROM tags t JOIN node_tags nt ON nt.tag = t.id WHERE nt.node = ?').all(Number(nodeId));
    return Response.json({ tags });
  }
  const all = d.prepare('SELECT * FROM tags ORDER BY name').all();
  return Response.json({ tags: all });
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'pages')) return Response.json({ error: 'verboden' }, { status: 403 });
  const { name, color, node, tag } = await req.json().catch(() => ({}));
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY, name TEXT UNIQUE, color TEXT DEFAULT '#gold')`).run();
  d.prepare(`CREATE TABLE IF NOT EXISTS node_tags (node INTEGER, tag INTEGER, PRIMARY KEY(node, tag))`).run();

  if (node && tag) {
    // Toggle tag on node
    const existing = d.prepare('SELECT 1 FROM node_tags WHERE node = ? AND tag = ?').get(node, tag);
    if (existing) {
      d.prepare('DELETE FROM node_tags WHERE node = ? AND tag = ?').run(node, tag);
    } else {
      d.prepare('INSERT OR IGNORE INTO node_tags (node, tag) VALUES (?, ?)').run(node, tag);
    }
    return Response.json({ ok: true });
  }
  if (name) {
    const id = d.prepare('INSERT OR IGNORE INTO tags (name, color) VALUES (?, ?)').run(name, color || '#gold').lastInsertRowid;
    return Response.json({ id, name });
  }
  return Response.json({ error: 'name or node+tag required' }, { status: 400 });
}