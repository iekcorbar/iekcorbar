// Download/stream van een bestand (latest of een bepaalde versie): /api/file/<id>[?v=<versie-id>]
//
// Sinds de mediaspeler streamt deze route in plaats van alles in één buffer te
// lezen: een video van 400 MB hoort niet in één Admission in het geheugen.
// De Range-header wordt ondersteund zoals een mediaspeler dat eis: zonder
// 206 Partial Content kan de browser niet spoileren (seek) en pakt Safari
// het bestand helemaal niet. Byte-feilers: start-end, start-, of suffix -N.
import fs from 'node:fs';
import { Readable } from 'node:stream';
import { getDb, blobPath } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

export const dynamic = 'force-dynamic';

function baseHeaders(naam, mime, total) {
  return {
    'Content-Type': mime,
    'Accept-Ranges': 'bytes',
    'Content-Disposition': `inline; filename*=UTF-8''${encodeURIComponent(naam)}`,
    'Cache-Control': 'private, max-age=60',
    'X-Content-Type-Options': 'nosniff',
  };
}

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
  const pad = v && blobPath(v.sha);
  if (!pad || !fs.existsSync(pad)) return Response.json({ error: 'not found' }, { status: 404 });

  const size = fs.statSync(pad).size;
  const mime = v.mime || 'application/octet-stream';
  const range = req.headers.get('range');
  let start = 0, end = size - 1, part = false;
  if (range) {
    const m = /^bytes=(\d*)-(\d*)$/.exec(range.trim());
    if (m && (m[1] || m[2])) {
      part = true;
      if (m[1]) { start = Math.min(Number(m[1]), Math.max(0, size - 1)); if (m[2]) end = Math.min(Number(m[2]), size - 1); }
      else { start = Math.max(0, size - Math.min(Number(m[2]), size)); }   // suffix "-N"
      if (end < start) {                                                    // onsinnig bereik
        return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${size}` } });
      }
    }
  }

  const stream = fs.createReadStream(pad, { start, end });
  const headers = { ...baseHeaders(n.name, mime, size), 'Content-Length': String(end - start + 1) };
  if (part) headers['Content-Range'] = `bytes ${start}-${end}/${size}`;
  return new Response(Readable.toWeb(stream), { status: part ? 206 : 200, headers });
}
