// Kinderen van een map (parent = id, of weg voor root) + info om te tekenen.
import { getDb, latestVersion } from '@/lib/db';
import { me } from '@/lib/auth';
import { visibleChildren } from '@/lib/acl';

export async function GET(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const parent = new URL(req.url).searchParams.get('parent');
  const pid = parent && /^\d+$/.test(parent) ? Number(parent) : null;
  const d = getDb();
  const kids = visibleChildren(u, pid).map((n) => {
    const v = n.type === 'file' ? latestVersion(n.id) : null;
    const out = { id: n.id, name: n.name, type: n.type, parent: n.parent, vis: n.vis, team: n.team, owner: n.owner, updated: n.updated, children: n.type === 'folder' && !!d.prepare('SELECT 1 AS x FROM nodes WHERE parent = ? LIMIT 1').get(n.id) };
    if (v) out.size = v.size;
    return out;
  });
  // broodkruimel naar de parent
  const trail = [];
  let cur = pid ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid) : null;
  while (cur) { trail.unshift({ id: cur.id, name: cur.name }); cur = cur.parent ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(cur.parent) : null; }
  return Response.json({ parent: pid, trail, kids });
}
