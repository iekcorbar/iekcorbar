import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

export const dynamic = 'force-dynamic';

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS recent_views (user INTEGER, node INTEGER, viewed_at INTEGER, PRIMARY KEY(user, node))`).run();
  const rows = d.prepare('SELECT n.* FROM recent_views r JOIN nodes n ON n.id = r.node WHERE r.user = ? ORDER BY r.viewed_at DESC LIMIT 10').all(u.id);
  const recents = rows.filter((n) => canSee(u, n));
  return Response.json({ recent: recents });
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { node } = await req.json().catch(() => ({}));
  if (!node) return Response.json({ error: 'node required' }, { status: 400 });
  const d = getDb();
  d.prepare(`CREATE TABLE IF NOT EXISTS recent_views (user INTEGER, node INTEGER, viewed_at INTEGER, PRIMARY KEY(user, node))`).run();
  d.prepare('INSERT OR REPLACE INTO recent_views (user, node, viewed_at) VALUES (?,?,?)').run(u.id, node, Date.now());
  // Keep only 20 most recent
  d.prepare('DELETE FROM recent_views WHERE user = ? AND node NOT IN (SELECT node FROM recent_views WHERE user = ? ORDER BY viewed_at DESC LIMIT 20)').run(u.id, u.id);
  return Response.json({ ok: true });
}