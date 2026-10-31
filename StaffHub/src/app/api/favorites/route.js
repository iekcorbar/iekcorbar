import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

export const dynamic = 'force-dynamic';

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS favorites (user INTEGER, node INTEGER, created INTEGER, PRIMARY KEY(user, node))`).run();
  const rows = d.prepare('SELECT n.* FROM favorites f JOIN nodes n ON n.id = f.node WHERE f.user = ? ORDER BY f.created DESC').all(u.id);
  const favs = rows.filter((n) => canSee(u, n));
  return Response.json({ favorites: favs });
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { node } = await req.json().catch(() => ({}));
  if (!node) return Response.json({ error: 'node required' }, { status: 400 });
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS favorites (user INTEGER, node INTEGER, created INTEGER, PRIMARY KEY(user, node))`).run();
  const existing = d.prepare('SELECT 1 FROM favorites WHERE user = ? AND node = ?').get(u.id, node);
  if (existing) {
    d.prepare('DELETE FROM favorites WHERE user = ? AND node = ?').run(u.id, node);
  } else {
    d.prepare('INSERT INTO favorites (user, node, created) VALUES (?,?,?)').run(u.id, node, Date.now());
  }
  return Response.json({ ok: true, favorited: !existing });
}