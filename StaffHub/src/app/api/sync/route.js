import fs from 'node:fs';
import path from 'node:path';
import { getDb, clean, uniqueName, findChild, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { may } from '@/lib/acl';

export const dynamic = 'force-dynamic';

// Directory to sync from (configured via env or default)
const SYNC_DIR = process.env.SYNC_DIR || '/app/staffhub-quests';

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'admin')) return Response.json({ error: 'verboden' }, { status: 403 });

  let files = [];
  try {
    files = fs.readdirSync(SYNC_DIR).filter((f) => f.endsWith('.md'));
  } catch {
    return Response.json({ error: 'sync dir not found: ' + SYNC_DIR }, { status: 404 });
  }

  const d = getDb();
  const result = [];
  for (const f of files) {
    const content = fs.readFileSync(path.join(SYNC_DIR, f), 'utf-8');
    const name = f.replace('.md', '').replace(/-/g, ' ');
    // find existing page with this name
    const existing = d.prepare("SELECT id FROM nodes WHERE type='page' AND name LIKE ?").get('%' + name.split(' ')[0] + '%');
    result.push({ file: f, name, size: content.length, synced: !!existing, id: existing?.id || null });
  }
  return Response.json({ dir: SYNC_DIR, files: result, count: files.length });
}

export async function POST() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'admin')) return Response.json({ error: 'verboden' }, { status: 403 });

  let files = [];
  try {
    files = fs.readdirSync(SYNC_DIR).filter((f) => f.endsWith('.md'));
  } catch {
    return Response.json({ error: 'sync dir not found: ' + SYNC_DIR }, { status: 404 });
  }

  const d = getDb();
  const results = [];
  for (const f of files) {
    const content = fs.readFileSync(path.join(SYNC_DIR, f), 'utf-8');
    const name = clean(f.replace('.md', ''));
    // simple markdown to HTML (basic)
    const html = '<pre>' + content.replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</pre>';

    // find existing
    const existing = d.prepare("SELECT id FROM nodes WHERE type='page' AND name = ?").get(name);
    if (existing) {
      d.prepare('UPDATE pages SET html=?, who=?, updated=? WHERE node=?').run(html, u.id, now(), existing.id);
      d.prepare('UPDATE nodes SET updated=? WHERE id=?').run(now(), existing.id);
      results.push({ file: f, action: 'updated', id: existing.id });
    } else {
      const pid = null;
      const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
        .run(pid, uniqueName(pid, name), 'page', u.id, now(), now()).lastInsertRowid);
      d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)')
        .run(id, html, u.id, now());
      results.push({ file: f, action: 'created', id });
    }
  }
  return Response.json({ synced: results.length, results });
}