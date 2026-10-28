// PATCH: hernoemen / verplaatsen / zichtbaarheid; DELETE: rekursief.
// GET: één node opvragen - nodig voor een gedeelde link (?open=<id>), want die
// begint buiten elke map en de hub moet dan weten wát het is en waar het woont.
import { getDb, clean, uniqueName, findChild, now, deleteNodeTree, gcBlobs, latestVersion } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite, effectiveVis } from '@/lib/acl';

export const dynamic = 'force-dynamic';

/**
 * Eén node, zelfde vorm als een regel uit /api/tree, dus de deeplink-links
 * dezelfde selecties als een klik in de lijst. Rechten: canSee, verder niets.
 */
export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const n = getDb().prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const out = { id: n.id, name: n.name, type: n.type, parent: n.parent, vis: n.vis, team: n.team, owner: n.owner, updated: n.updated };
  if (n.type === 'file') { const v = latestVersion(n.id); if (v) out.size = v.size; }
  return Response.json({ node: out });
}

export async function PATCH(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || !canSee(u, n)) return Response.json({ error: 'bestaat niet' }, { status: 404 });
  if (!canWrite(u, n)) return Response.json({ error: 'verboden' }, { status: 403 });
  const body = await req.json().catch(() => ({}));

  if (body.name != null) {
    const nm = clean(body.name);
    if (!nm) return Response.json({ error: 'lege naam' }, { status: 400 });
    if (nm !== n.name) {
      if (findChild(n.parent, nm)) return Response.json({ error: 'die naam bestaat hier al' }, { status: 409 });
      d.prepare('UPDATE nodes SET name = ?, updated = ? WHERE id = ?').run(nm, now(), n.id);
    }
  }
  if (body.parent !== undefined) {
    const pid = body.parent === null ? null : Number(body.parent);
    if (pid != null) {
      const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid);
      if (!p || p.type !== 'folder' || !canSee(u, p) || !canWrite(u, p)) return Response.json({ error: 'ouder niet ok' }, { status: 403 });
      if (p.id === n.id || d.prepare('WITH RECURSIVE s(id) AS (SELECT ? UNION SELECT parent FROM nodes WHERE id IN s) SELECT 1 FROM s WHERE id = ?').get(n.id, pid)) {
        return Response.json({ error: 'je kunt een map niet in zijn eigen kind stoppen' }, { status: 400 });
      }
    } else if (u.role !== 'owner' && effectiveVis(n).vis !== 'all') {
      return Response.json({ error: 'naar root mag niet' }, { status: 403 });
    }
    const cur = findChild(pid, n.name);
    const nm = cur && cur.id !== n.id ? uniqueName(pid, n.name) : n.name;
    d.prepare('UPDATE nodes SET parent = ?, name = ?, updated = ? WHERE id = ?').run(pid, nm, now(), n.id);
    if (n.type === 'folder') d.prepare('UPDATE nodes SET vis = NULL, team = NULL WHERE parent = ?').run(n.id); // kinderen erven opnieuw
  }
  if (body.vis !== undefined) {
    if (u.role !== 'owner' && body.vis === 'owner') return Response.json({ error: 'verboden' }, { status: 403 });
    const vis = ['all', 'team', 'owner', null].includes(body.vis) ? body.vis : null;
    d.prepare('UPDATE nodes SET vis = ?, team = ?, updated = ? WHERE id = ?').run(vis, vis === 'team' ? (body.team || u.team || null) : null, now(), n.id);
  }
  return Response.json({ ok: true });
}

export async function DELETE(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const n = getDb().prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n) return Response.json({ ok: true });
  if (!canWrite(u, n)) return Response.json({ error: 'verboden' }, { status: 403 });
  deleteNodeTree(n.id);
  gcBlobs();
  return Response.json({ ok: true });
}
