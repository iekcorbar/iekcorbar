// Download van een bestand (latest of een bepaalde versie): /api/file/<id>[?v=<versie-id>]
import fs from 'node:fs';
import { getDb, blobPath } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

export async function GET(req, { params }) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { id } = await params;
  const d = getDb();
  const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));
  if (!n || n.type !== 'file' || !canSee(u, n)) return Response.json({ error: 'not found' }, { status: 404 });
  const vq = new URL(req.url).searchParams.get('v');
  const v = vq
    ? d.prepare('SELECT * FROM versions WHERE id = ? AND node = ?').get(Number(vq), n.id)
    : d.prepare('SELECT * FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(n.id);
  if (!v || !fs.existsSync(blobPath(v.sha))) return Response.json({ error: 'not found' }, { status: 404 });
  const buf = fs.readFileSync(blobPath(v.sha));
  return new Response(buf, {
    headers: {
      'Content-Type': v.mime || 'application/octet-stream',
      'Content-Length': String(buf.length),
      'Content-Disposition': `inline; filename*=UTF-8''${encodeURIComponent(n.name)}`,
      'Cache-Control': 'private, max-age=60',
    },
  });
}
