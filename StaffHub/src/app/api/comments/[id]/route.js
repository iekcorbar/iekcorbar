// Reacties op een map, bestand of pagina.
import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';
import { notify, mentionedUsers } from '@/lib/notify';

export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const list = d.prepare('SELECT c.id, c.text, c.created, u.username AS who, u.team AS team FROM comments c JOIN users u ON u.id = c.who WHERE c.node = ? ORDER BY c.id').all(n.id);
  return Response.json({ comments: list });
}

export async function POST(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const { text } = await req.json().catch(() => ({}));
  const t = String(text || '').trim().slice(0, 2000);
  if (!t) return Response.json({ error: 'leeg' }, { status: 400 });
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const cid = Number(d.prepare('INSERT INTO comments(node,who,text,created) VALUES(?,?,?,?)').run(n.id, u.id, t, now()).lastInsertRowid);
  // Melden: de eigenaar van het document en wie er eerder op reageerde (zonder jezelf),
  // plus iedereen die met @naam wordt aangehaald (die krijgt de zwaardere 'mention').
  const genoemd = mentionedUsers(t, n, u.id);
  const eerder = d.prepare('SELECT DISTINCT who FROM comments WHERE node = ?').all(n.id).map((r) => r.who);
  const volgers = [n.owner, ...eerder].filter((x) => x !== u.id && !genoemd.includes(x));
  const kort = t.length > 80 ? t.slice(0, 80) + '…' : t;
  notify(genoemd, { kind: 'mention', text: `${u.username} mentioned you on "${n.name}": ${kort}`, node: n.id, from: u.id });
  notify(volgers, { kind: 'comment', text: `${u.username} commented on "${n.name}": ${kort}`, node: n.id, from: u.id });
  return Response.json({ id: cid });
}

export async function DELETE(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const cid = Number(new URL(req.url).searchParams.get('cid'));
  const d = getDb();
  const c = d.prepare('SELECT * FROM comments WHERE id = ? AND node = ?').get(cid, Number(id));
  if (!c) return Response.json({ ok: true });
  if (c.who !== u.id && u.role !== 'owner') return Response.json({ error: 'verboden' }, { status: 403 });
  d.prepare('DELETE FROM comments WHERE id = ?').run(c.id);
  return Response.json({ ok: true });
}
