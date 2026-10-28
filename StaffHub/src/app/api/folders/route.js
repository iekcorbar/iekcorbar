import { getDb, clean, uniqueName, findChild, now } from '@/lib/db';
import { me, needUser } from '@/lib/auth';
import { canWrite, canSee } from '@/lib/acl';

export async function POST(req) {
  const u = await me();
  try { needUser(u); } catch { return Response.json({ error: 'login' }, { status: 401 }); }
  const { parent, name, vis, team } = await req.json().catch(() => ({}));
  const d = getDb();
  const nm = clean(name);
  if (!nm) return Response.json({ error: 'Geen naam' }, { status: 400 });
  let pid = null;
  if (parent) {
    pid = Number(parent);
    const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid);
    if (!p || p.type !== 'folder') return Response.json({ error: 'Ouder bestaat niet' }, { status: 400 });
    if (!canSee(u, p) || !canWrite(u, p)) return Response.json({ error: 'Verboden' }, { status: 403 });
  } else if (u.role !== 'owner' && (vis === 'owner')) {
    return Response.json({ error: 'Alleen owners maken besloten mappen' }, { status: 403 });
  }
  const dup = findChild(pid, nm);
  if (dup) return Response.json({ id: dup.id, name: dup.name, existing: true });
  const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,vis,team,owner,created,updated) VALUES(?,?,?,?,?,?,?,?)')
    .run(pid, uniqueName(pid, nm), 'folder', ['team', 'owner'].includes(vis) ? vis : (vis === 'all' ? 'all' : null), team || null, u.id, now(), now()).lastInsertRowid);
  return Response.json({ id, name: d.prepare('SELECT name FROM nodes WHERE id = ?').get(id).name });
}
