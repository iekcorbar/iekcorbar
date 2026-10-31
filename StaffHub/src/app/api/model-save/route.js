// Bewerkt .bbmodel vanuit de 3D-viewer opslaan: wordt een nieuwe versie van dezelfde node
// (de oude blijft in de versiegeschiedenis) en komt in model_edits, zodat de
// pull-script op de pc van de eigenaar weet wat er in de projectmap bijgewerkt moet worden.
import { getDb, storeBlob, ensureEdits, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite, may } from '@/lib/acl';

export const dynamic = 'force-dynamic';
const MAX = 32 * 1024 * 1024;

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'upload')) return Response.json({ error: 'verboden' }, { status: 403 });
  const body = await req.json().catch(() => null);
  const id = Number(body?.id);
  const tekst = typeof body?.json === 'string' ? body.json : null;
  if (!id || !tekst || tekst.length > MAX) return Response.json({ error: 'id en json verwacht' }, { status: 400 });
  let parsed;
  try { parsed = JSON.parse(tekst); } catch { return Response.json({ error: 'geen geldige json' }, { status: 400 }); }
  if (!Array.isArray(parsed?.elements) || !Array.isArray(parsed?.outliner)) return Response.json({ error: 'geen bbmodel' }, { status: 400 });

  const d = getDb();
  const node = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
  if (!node || node.type !== 'file' || !/\.bbmodel$/i.test(node.name)) return Response.json({ error: 'geen bbmodel-bestand' }, { status: 404 });
  if (!canSee(u, node) || !canWrite(u, node)) return Response.json({ error: 'verboden' }, { status: 403 });

  const buf = Buffer.from(tekst, 'utf8');
  const sha = storeBlob(buf);
  const last = d.prepare('SELECT * FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(id);
  if (last && last.sha === sha) return Response.json({ ok: true, unchanged: true });
  ensureEdits(d);
  const vid = Number(d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)')
    .run(id, sha, buf.length, last?.mime || 'application/json', u.id, now()).lastInsertRowid);
  d.prepare('INSERT INTO model_edits(node,version,who,created) VALUES(?,?,?,?)').run(id, vid, u.id, now());
  d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), id);
  return Response.json({ ok: true, version: vid });
}
