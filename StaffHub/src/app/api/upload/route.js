// Upload: multipart met een of meer files + optionele "pad" (mapjes gescheiden
// door '/') zodat drag & drop van hele mappenboomkes werkt.
import fs from 'node:fs';
import { getDb, clean, uniqueName, findChild, storeBlob, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite, may } from '@/lib/acl';

const MAX = 512 * 1024 * 1024;

async function ensurePath(u, base, parts) {
  let cur = base;
  const d = getDb();
  for (const raw of parts) {
    const nm = clean(raw);
    if (!nm) continue;
    let kid = findChild(cur, nm);
    if (!kid || kid.type !== 'folder') {
      const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
        .run(cur, uniqueName(cur, nm), 'folder', u.id, now(), now()).lastInsertRowid);
      kid = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
    }
    cur = kid.id;
  }
  return cur;
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'upload')) return Response.json({ error: 'verboden' }, { status: 403 });   // cap 'upload' (lib/caps)
  const form = await req.formData().catch(() => null);
  if (!form) return Response.json({ error: 'verwacht multipart/form-data' }, { status: 400 });
  const parentRaw = form.get('parent');
  const folderPath = String(form.get('path') || '');
  const d = getDb();
  let parentId = parentRaw ? Number(parentRaw) : null;
  if (parentId) {
    const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(parentId);
    if (!p || !canSee(u, p) || !canWrite(u, p)) return Response.json({ error: 'verboden' }, { status: 403 });
  }
  const made = [];
  for (const [key, val] of form.entries()) {
    if (key !== 'files' || !(val instanceof File)) continue;
    if (val.size > MAX) { made.push({ name: val.name, error: 'te groot (max 512 MB)' }); continue; }
    // het browser-DnM-API geeft webkitRelativePath; knip de bestandsnaam eraf
    const rel = String(val.webkitrelativepath || val.name).split('/').filter(Boolean);
    const name = clean(rel[rel.length - 1]) || 'naamloos';
    let folder = parentId;
    if (rel.length > 1 || folderPath) folder = await ensurePath(u, parentId, rel.length > 1 ? rel.slice(0, -1) : folderPath.split('/'));
    if (!canWrite(u, folder ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(folder) : null)) { made.push({ name, error: 'verboden' }); continue; }

    const buf = Buffer.from(await val.arrayBuffer());
    const sha = storeBlob(buf);
    const mime = val.type || 'application/octet-stream';
    let node = findChild(folder, name);
    if (node && node.type === 'file') {                       // zelfde naam = nieuwe versie, nooit overschrijven
      const last = d.prepare('SELECT * FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(node.id);
      if (last && last.sha === sha) { made.push({ id: node.id, name, unchanged: true }); continue; }
      d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)').run(node.id, sha, buf.length, mime, u.id, now());
      d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), node.id);
      made.push({ id: node.id, name, versioned: true });
      continue;
    }
    if (node) { made.push({ name, error: 'er bestaat al een map met die naam' }); continue; }
    const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
      .run(folder, uniqueName(folder, name), 'file', u.id, now(), now()).lastInsertRowid);
    d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)').run(id, sha, buf.length, mime, u.id, now());
    made.push({ id, name });
  }
  return Response.json({ ok: true, made });
}
