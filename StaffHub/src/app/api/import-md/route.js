import { getDb, clean, uniqueName, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { may, canWrite } from '@/lib/acl';
import { toText } from '@/lib/knowledge';

export const dynamic = 'force-dynamic';

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'pages')) return Response.json({ error: 'verboden' }, { status: 403 });

  const formData = await req.formData().catch(() => null);
  if (!formData) return Response.json({ error: 'no form data' }, { status: 400 });

  const file = formData.get('file');
  const parent = formData.get('parent');
  if (!file) return Response.json({ error: 'no file' }, { status: 400 });

  const text = await file.text();
  const name = (file.name || 'imported').replace(/\.md$/i, '');
  const d = getDb();

  let pid = null;
  if (parent) {
    pid = Number(parent);
    const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(pid);
    if (!p || p.type !== 'folder') return Response.json({ error: 'folder not found' }, { status: 404 });
  }

  // Convert markdown to simple HTML
  const html = mdToHtml(text);

  const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
    .run(pid, uniqueName(pid, clean(name) || 'imported'), 'page', u.id, now(), now()).lastInsertRowid);
  d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)')
    .run(id, html, u.id, now());

  return Response.json({ id, name: d.prepare('SELECT name FROM nodes WHERE id = ?').get(id).name });
}

function mdToHtml(md) {
  return String(md || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/^/, '<p>')
    .replace(/$/, '</p>');
}