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
  const kidsRaw = visibleChildren(u, pid);
  // Aantallen per map in ENE query: de tegels in de nieuwe hub tonen "7 items"
  // en dat mag niet per tegel een losse up-vraag worden.
  const mapIds = kidsRaw.filter((n) => n.type === 'folder').map((n) => n.id);
  const teller = new Map();
  if (mapIds.length) {
    for (const r of d.prepare(`SELECT parent, COUNT(*) AS c FROM nodes WHERE parent IN (${mapIds.map(() => '?').join(',')}) GROUP BY parent`).all(...mapIds))
      teller.set(r.parent, r.c);
  }
  const kids = kidsRaw.map((n) => {
    const v = n.type === 'file' ? latestVersion(n.id) : null;
    const out = { id: n.id, name: n.name, type: n.type, parent: n.parent, vis: n.vis, team: n.team, owner: n.owner, updated: n.updated, children: n.type === 'folder' && (teller.get(n.id) || 0) > 0, items: n.type === 'folder' ? (teller.get(n.id) || 0) : null };
    if (v) out.size = v.size;
    return out;
  });
  // broodkruimel naar de parent
  const trail = [];
  let cur = pid ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid) : null;
  while (cur) { trail.unshift({ id: cur.id, name: cur.name }); cur = cur.parent ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(cur.parent) : null; }
  return Response.json({ parent: pid, trail, kids });
}
