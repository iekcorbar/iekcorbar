import { NextResponse } from 'next/server';
import fs from 'fs';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { STORY_FILES_DIR, storyFilePath } from '@/lib/story-file-store';
import { STORY_FILE_MAX_BYTES, isAllowedStoryFile, storyFileExt, formatBytes } from '@/lib/story-files';

import { logAudit } from '@/lib/audit';
// Story Room uploads: images, 3D models, Minecraft schematics, documents…
// attached to a chapter (nodeId), a lore entry (entityId) or neither (the
// general asset library).
//   GET                      list every file (metadata) — any access
//   POST multipart           upload one file (fields: file, nodeId?, entityId?, note?) — edit
//   POST { action:'update' } change attachment/note/name — edit
//   DELETE ?id=              staff, or the person who uploaded it (edit)
// The bytes are read back through ./raw, never from public/.

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';

async function validTarget({ nodeId, entityId }) {
  if (nodeId && entityId) return 'A file can belong to a chapter or a lore entry, not both.';
  if (nodeId && !(await db.getStoryNode(nodeId))) return 'That chapter no longer exists.';
  if (entityId) {
    const entities = (await db.getStoryEntities()) || [];
    if (!entities.some(e => e.id === entityId)) return 'That lore entry no longer exists.';
  }
  return null;
}

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;
  const files = await db.listStoryFiles();
  if (!files) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  // storedName is an internal detail — clients only ever use the id.
  return NextResponse.json({ files: files.map(({ storedName, ...f }) => ({ ...f, sizeBytes: Number(f.sizeBytes) || 0 })) });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'edit');
  if (auth.error) return auth.error;
  const actor = auth.access.actor;
  const contentType = request.headers.get('content-type') || '';

  if (contentType.includes('application/json')) {
    let body;
    try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }
    if (body.action !== 'update' || !body.id) return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
    const file = await db.getStoryFile(body.id);
    if (!file) return NextResponse.json({ error: 'File not found.' }, { status: 404 });
    const target = { nodeId: body.nodeId || null, entityId: body.entityId || null };
    const problem = await validTarget(target);
    if (problem) return NextResponse.json({ error: problem }, { status: 400 });
    let originalName = file.originalName;
    if (body.name !== undefined) {
      const name = String(body.name || '').replace(/[\\/\x00-\x1f]/g, ' ').trim().slice(0, 255);
      if (!name) return NextResponse.json({ error: 'A file name is required.' }, { status: 400 });
      // Renaming may not change the type — the extension decides how it's served.
      if (storyFileExt(name) !== storyFileExt(file.originalName)) {
        return NextResponse.json({ error: `Keep the .${storyFileExt(file.originalName)} extension when renaming.` }, { status: 400 });
      }
      originalName = name;
    }
    const note = body.note !== undefined ? String(body.note || '').trim().slice(0, 500) : file.note;
    const ok = await db.updateStoryFile(file.id, { ...target, note, originalName });
    if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true });
  }

  if (!contentType.includes('multipart/form-data')) {
    return NextResponse.json({ error: 'Expected a file upload.' }, { status: 400 });
  }

  let form;
  try { form = await request.formData(); } catch { return NextResponse.json({ error: 'The upload could not be read. Try again.' }, { status: 400 }); }
  const file = form.get('file');
  if (!file || typeof file === 'string') return NextResponse.json({ error: 'No file attached.' }, { status: 400 });

  const originalName = String(file.name || '').replace(/[\\/\x00-\x1f]/g, ' ').trim().slice(0, 255);
  if (!originalName || !isAllowedStoryFile(originalName)) {
    return NextResponse.json({ error: `"${originalName || 'This file'}" is not a file type the Story Room accepts. Images, 3D models, schematics, documents, audio/video and archives are fine — programs and web pages are not.` }, { status: 400 });
  }
  if (file.size > STORY_FILE_MAX_BYTES) {
    return NextResponse.json({ error: `"${originalName}" is ${formatBytes(file.size)} — the limit is ${formatBytes(STORY_FILE_MAX_BYTES)} per file.` }, { status: 400 });
  }
  if (file.size === 0) return NextResponse.json({ error: `"${originalName}" is empty.` }, { status: 400 });

  const target = { nodeId: String(form.get('nodeId') || '') || null, entityId: String(form.get('entityId') || '') || null };
  const problem = await validTarget(target);
  if (problem) return NextResponse.json({ error: problem }, { status: 400 });

  const id = crypto.randomUUID();
  // Stored under a generated name, never the uploaded one (no path tricks,
  // no collisions); the real name is only used for the download header.
  const storedName = `${id}.${storyFileExt(originalName)}`;
  const diskPath = storyFilePath(storedName);

  try {
    await fs.promises.mkdir(STORY_FILES_DIR, { recursive: true });
    await fs.promises.writeFile(diskPath, Buffer.from(await file.arrayBuffer()));
  } catch (err) {
    console.error('Story file write failed:', err);
    return NextResponse.json({ error: 'The file could not be stored on the server. Nothing was saved.' }, { status: 500 });
  }

  const saved = await db.addStoryFile({
    id, ...target, storedName, originalName,
    mime: file.type || null,
    sizeBytes: file.size,
    note: String(form.get('note') || '').trim().slice(0, 500) || null,
    actor
  });
  if (!saved) {
    // Don't leave bytes behind that nothing can list or delete.
    await fs.promises.unlink(diskPath).catch(() => {});
    return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  }
  return NextResponse.json({ success: true, id });
}

export async function DELETE(request) {
  const auth = await requireStoryAccess(request, 'edit');
  if (auth.error) return auth.error;
  const id = new URL(request.url).searchParams.get('id');
  if (!id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });

  const file = await db.getStoryFile(id);
  if (!file) return NextResponse.json({ error: 'File not found.' }, { status: 404 });
  if (!auth.access.canManage && file.uploadedBy !== auth.access.actor) {
    return NextResponse.json({ error: 'You can only delete files you uploaded yourself.' }, { status: 403 });
  }

  const ok = await db.deleteStoryFile(id);
  await logAudit({ mcUsername: auth.access.actor, role: auth.access.isGuest ? 'story-guest' : auth.user?.role }, { area: 'story', action: 'delete file', target: file.originalName });
  if (!ok) return NextResponse.json({ error: 'Could not delete the file (it may already be gone, or the database is unreachable).' }, { status: 500 });
  const p = storyFilePath(file.storedName);
  if (p) await fs.promises.unlink(p).catch(() => {});
  return NextResponse.json({ success: true });
}
