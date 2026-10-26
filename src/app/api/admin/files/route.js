import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// Admin-only shared file drop (Staff Panel > Settings > Shared Files) —
// deliberately requireRole(['admin']) rather than requirePermission on some
// new STAFF_TABS entry, same bar as api/admin/newsletter and api/admin/roles:
// no role should ever be able to grant itself (or be granted) access here.
//
// Files are written OUTSIDE public/ on purpose. Every other upload feature
// in this codebase (chat images, custom skins, wiki attachments, event
// posters) lives under public/uploads/**, served back out through the
// unauthenticated /uploads/* passthrough app.js/server.js add in front of
// Next's own handler (see CLAUDE.md "Runtime file uploads") — fine for
// player-facing content that's meant to be publicly reachable by URL, wrong
// for files admins are explicitly sharing only with each other. Storing them
// in a private directory below the project root means the only way to read
// one back is through GET /api/admin/files/download, which re-checks
// requireRole(['admin']) itself.
const ADMIN_ONLY = ['admin'];
const MAX_FILE_BYTES = 100 * 1024 * 1024; // 100MB — comfortably covers plugin jars
const PRIVATE_DIR = path.join(process.cwd(), 'private-uploads', 'admin-files');

export async function GET(request) {
  const auth = await requireRole(request, ADMIN_ONLY);
  if (auth.error) return auth.error;

  try {
    const files = await db.getAdminFiles();
    return NextResponse.json({ files });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve shared files" }, { status: 500 });
  }
}

export async function POST(request) {
  const auth = await requireRole(request, ADMIN_ONLY);
  if (auth.error) return auth.error;

  try {
    const contentType = request.headers.get('content-type') || '';
    if (!contentType.includes('multipart/form-data')) {
      return NextResponse.json({ error: "Expected a multipart/form-data upload" }, { status: 400 });
    }

    const formData = await request.formData();
    const file = formData.get('file');
    const description = formData.get('description');

    if (!file || typeof file === 'string') {
      return NextResponse.json({ error: "No file attached" }, { status: 400 });
    }
    if (file.size > MAX_FILE_BYTES) {
      return NextResponse.json({ error: `File must be smaller than ${MAX_FILE_BYTES / (1024 * 1024)}MB` }, { status: 400 });
    }

    await fs.promises.mkdir(PRIVATE_DIR, { recursive: true });

    const id = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}`;
    // Stored filename is a generated id, never the original name — sidesteps
    // path traversal / collisions entirely; the human-readable name is kept
    // separately (original_name) and only used for the download's
    // Content-Disposition header.
    const ext = path.extname(file.name || '');
    const storedFilename = `${id}${ext}`;

    const bytes = Buffer.from(await file.arrayBuffer());
    await fs.promises.writeFile(path.join(PRIVATE_DIR, storedFilename), bytes);

    const saved = await db.saveAdminFile({
      id,
      filename: storedFilename,
      originalName: file.name || storedFilename,
      sizeBytes: file.size,
      description: description ? String(description).trim() || null : null,
      uploadedBy: auth.user.mcUsername || auth.user.email
    });

    if (!saved) {
      // Metadata write failed even after the db.json fallback — clean up the
      // orphaned file rather than leaving a byte blob nothing can ever list
      // or delete through the UI.
      await fs.promises.unlink(path.join(PRIVATE_DIR, storedFilename)).catch(() => {});
      return NextResponse.json({ error: "Could not save that file's record — the database may be unreachable." }, { status: 500 });
    }

    await logAudit(auth.user, { area: 'files', action: 'upload', target: file.name || storedFilename });
    return NextResponse.json({ success: true, files: await db.getAdminFiles() });
  } catch (err) {
    console.error("Admin files POST error:", err);
    return NextResponse.json({ error: "Failed to upload file" }, { status: 500 });
  }
}

export async function DELETE(request) {
  const auth = await requireRole(request, ADMIN_ONLY);
  if (auth.error) return auth.error;

  try {
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) {
      return NextResponse.json({ error: "Missing file ID" }, { status: 400 });
    }

    const filename = await db.deleteAdminFile(id);
    if (!filename) {
      return NextResponse.json({ error: "Could not delete that file — it may already be gone, or the database is unreachable." }, { status: 500 });
    }

    await fs.promises.unlink(path.join(PRIVATE_DIR, filename)).catch(() => {});

    await logAudit(auth.user, { area: 'files', action: 'delete', target: filename });
    return NextResponse.json({ success: true, files: await db.getAdminFiles() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete file" }, { status: 500 });
  }
}
