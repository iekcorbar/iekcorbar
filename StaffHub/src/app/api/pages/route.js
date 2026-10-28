// Nieuwe handleiding-pagina aanmaken (type 'page' in de mappenboom).
import { getDb, clean, uniqueName, findChild, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite } from '@/lib/acl';

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { parent, name, html } = await req.json().catch(() => ({}));
  const d = getDb();
  const nm = clean(name) || 'Nieuwe handleiding';
  let pid = null;
  if (parent) {
    pid = Number(parent);
    const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid);
    if (!p || p.type !== 'folder' || !canSee(u, p)) return Response.json({ error: 'bestaat niet' }, { status: 404 });
  }
  if (!canWrite(u, pid ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid) : null)) return Response.json({ error: 'verboden' }, { status: 403 });
  const existing = findChild(pid, nm);
  if (existing && existing.type === 'page') return Response.json({ id: existing.id, name: existing.name, existing: true });
  const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
    .run(pid, uniqueName(pid, nm), 'page', u.id, now(), now()).lastInsertRowid);
  // Nieuw: een lege pagina óf één met meegeleverde inhoud (upload vanuit een
  // .md/.txt/-html-bestand; de client converteert en POST't die hier).
  d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)')
    .run(id, String(html || '').slice(0, 2_000_000), u.id, now());
  return Response.json({ id, name: d.prepare('SELECT name FROM nodes WHERE id = ?').get(id).name });
}
