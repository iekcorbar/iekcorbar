import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { readStoryFile } from '@/lib/story-file-store';
import {
  STORY_EXPORT_FORMATS, renderChapter, buildFolderZip, buildFullZip, safeFileName, attachmentHeader
} from '@/lib/story-export';

// Story Room downloads — anyone who can read the Story Room (staff or any
// guest link) can download, since it's the same content they can already
// see on screen.
//   ?id=<chapter>&format=docx|md|txt|html  -> that one chapter as a file
//   ?id=<folder>&format=...                -> that folder as a ZIP
//   ?all=1&format=...                      -> everything as a ZIP (chapters,
//                                             lore library, timeline,
//                                             uploaded files + a JSON backup)

const DB_ERROR = 'Could not reach the database — please try again shortly.';
// The ZIP is built in memory, so uploaded files are included up to this
// total; anything beyond is listed in the README as "download separately".
const MAX_UPLOAD_BYTES_IN_ZIP = 400 * 1024 * 1024;

async function loadUploads(rows) {
  const uploads = [];
  const skipped = [];
  let total = 0;
  // Smallest first, so one huge model doesn't push out many small images.
  for (const row of [...rows].sort((a, b) => Number(a.sizeBytes) - Number(b.sizeBytes))) {
    if (total + Number(row.sizeBytes) > MAX_UPLOAD_BYTES_IN_ZIP) { skipped.push(row.originalName); uploads.push(row); continue; }
    const data = await readStoryFile(row.storedName);
    if (data) total += data.length;
    else skipped.push(row.originalName);
    uploads.push({ ...row, data });
  }
  return { uploads, skipped };
}

function fileResponse(data, filename, mime) {
  return new NextResponse(data, {
    headers: {
      'Content-Type': mime,
      'Content-Disposition': attachmentHeader(filename),
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff'
    }
  });
}

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const { searchParams } = new URL(request.url);
  const format = Object.prototype.hasOwnProperty.call(STORY_EXPORT_FORMATS, searchParams.get('format')) ? searchParams.get('format') : 'docx';
  const opts = { origin: new URL(request.url).origin };
  const exportedBy = auth.access.actor;

  const nodes = await db.getStoryNodesWithContent();
  if (!nodes) return NextResponse.json({ error: DB_ERROR }, { status: 500 });

  if (searchParams.get('all')) {
    const [entities, timeline, fileRows] = await Promise.all([db.getStoryEntities(), db.getStoryTimeline(), db.listStoryFiles()]);
    if (!entities || !timeline || !fileRows) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    const { uploads, skipped } = await loadUploads(fileRows);
    const zip = buildFullZip({ nodes, entities, timeline, format, exportedBy, opts, uploads, skipped });
    return fileResponse(zip, `iekcorbar-story-${new Date().toISOString().slice(0, 10)}.zip`, 'application/zip');
  }

  const id = searchParams.get('id');
  const node = id ? nodes.find(n => n.id === id) : null;
  if (!node) return NextResponse.json({ error: 'Chapter or folder not found.' }, { status: 404 });

  if (node.type === 'folder') {
    const inFolder = new Set([node.id]);
    for (let grew = true; grew;) {
      grew = false;
      for (const n of nodes) if (n.parentId && inFolder.has(n.parentId) && !inFolder.has(n.id)) { inFolder.add(n.id); grew = true; }
    }
    const fileRows = (await db.listStoryFiles()) || [];
    const { uploads, skipped } = await loadUploads(fileRows.filter(f => f.nodeId && inFolder.has(f.nodeId)));
    const zip = buildFolderZip({ nodes, folder: node, format, exportedBy, opts, uploads, skipped });
    return fileResponse(zip, `${safeFileName(node.title, 'Folder')}.zip`, 'application/zip');
  }

  const f = STORY_EXPORT_FORMATS[format];
  return fileResponse(renderChapter(node, format, opts), `${safeFileName(node.title)}.${f.ext}`, f.mime);
}
