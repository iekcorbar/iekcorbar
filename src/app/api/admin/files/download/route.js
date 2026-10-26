import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { db } from '@/lib/db';
import { requireRole } from '@/lib/session';

// Matches PRIVATE_DIR in ../route.js — kept as its own literal here rather
// than a shared import since it's one line and this is the only other file
// that ever needs it.
const PRIVATE_DIR = path.join(process.cwd(), 'private-uploads', 'admin-files');
const ADMIN_ONLY = ['admin'];

// The only way an uploaded file's bytes are ever served back out — re-checks
// requireRole(['admin']) itself rather than trusting a guessable URL, unlike
// the public /uploads/* passthrough this deliberately avoids (see
// ../route.js's file comment).
export async function GET(request) {
  const auth = await requireRole(request, ADMIN_ONLY);
  if (auth.error) return auth.error;

  try {
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) {
      return NextResponse.json({ error: "Missing file ID" }, { status: 400 });
    }

    const files = await db.getAdminFiles();
    const record = files.find(f => String(f.id) === id);
    if (!record) {
      return NextResponse.json({ error: "File not found" }, { status: 404 });
    }

    const filePath = path.join(PRIVATE_DIR, record.filename);
    // Guard against a stored filename ever resolving outside PRIVATE_DIR —
    // it's always a server-generated id (see POST in ../route.js), never
    // user input, but this is the last line of defense before touching disk.
    if (!filePath.startsWith(PRIVATE_DIR + path.sep)) {
      return NextResponse.json({ error: "Invalid file reference" }, { status: 400 });
    }

    const data = await fs.promises.readFile(filePath);
    const asciiName = (record.originalName || record.filename).replace(/[^\x20-\x7E]/g, '_');
    const encodedName = encodeURIComponent(record.originalName || record.filename);

    return new NextResponse(data, {
      headers: {
        'Content-Type': 'application/octet-stream',
        'Content-Disposition': `attachment; filename="${asciiName}"; filename*=UTF-8''${encodedName}`,
        'Content-Length': String(data.length)
      }
    });
  } catch (err) {
    console.error("Admin files download error:", err);
    return NextResponse.json({ error: "Failed to download file" }, { status: 500 });
  }
}
