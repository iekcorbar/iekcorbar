import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

export const dynamic = 'force-dynamic';

export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id, vid } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'page' || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const v = d.prepare('SELECT * FROM page_versions WHERE id = ? AND node = ?').get(Number(vid), n.id);
  if (!v) return Response.json({ error: 'versie niet gevonden' }, { status: 404 });
  return Response.json({ id: v.id, html: v.html, who: v.who, created: v.created, size: v.size });
}