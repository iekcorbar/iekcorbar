import { NextResponse } from 'next/server';
import fs from 'fs';
import { Readable } from 'stream';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { storyFilePath } from '@/lib/story-file-store';
import { storyFileInlineType } from '@/lib/story-files';
import { attachmentHeader } from '@/lib/story-export';

// Serves the bytes of a Story Room upload — only to someone with Story Room
// access. ?inline=1 shows images/audio/video in the page (thumbnails,
// preview); every other type, and SVG, is always a download. The sandbox
// CSP + nosniff stop an uploaded file from ever running as a page here.

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const { searchParams } = new URL(request.url);
  const file = await db.getStoryFile(searchParams.get('id') || '');
  if (!file) return NextResponse.json({ error: 'File not found.' }, { status: 404 });
  const p = storyFilePath(file.storedName);
  let stat;
  try { stat = p ? await fs.promises.stat(p) : null; } catch { stat = null; }
  if (!stat) return NextResponse.json({ error: 'This file is missing on the server.' }, { status: 404 });

  const inlineType = searchParams.get('inline') ? storyFileInlineType(file.originalName) : null;
  const stream = Readable.toWeb(fs.createReadStream(p));
  return new NextResponse(stream, {
    headers: {
      'Content-Type': inlineType || 'application/octet-stream',
      'Content-Length': String(stat.size),
      'Content-Disposition': inlineType ? 'inline' : attachmentHeader(file.originalName),
      'Content-Security-Policy': "default-src 'none'; img-src 'self'; media-src 'self'; sandbox",
      'X-Content-Type-Options': 'nosniff',
      'Cache-Control': 'private, max-age=3600'
    }
  });
}
