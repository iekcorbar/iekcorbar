// Zoeken over namen + pagina-tekst + reacties; ACL toegepast per resultaat.
import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

export async function GET(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const q = String(new URL(req.url).searchParams.get('q') || '').trim();
  if (q.length < 2) return Response.json({ results: [] });
  const like = '%' + q.replace(/[%_]/g, (c) => '\\' + c) + '%';
  const d = getDb();
  const out = [];
  const push = (n, why) => { if (out.length < 60 && canSee(u, n)) out.push({ id: n.id, name: n.name, type: n.type, why }); };
  for (const n of d.prepare("SELECT * FROM nodes WHERE name LIKE ? ESCAPE '\\' LIMIT 40").all(like)) push(n, 'naam');
  for (const p of d.prepare("SELECT n.* FROM pages pg JOIN nodes n ON n.id = pg.node WHERE pg.html LIKE ? ESCAPE '\\' LIMIT 20").all(like)) push(p, 'inhoud');
  for (const c of d.prepare("SELECT DISTINCT n.* FROM comments cm JOIN nodes n ON n.id = cm.node WHERE cm.text LIKE ? ESCAPE '\\' LIMIT 20").all(like)) push(c, 'reactie');
  return Response.json({ results: out });
}
