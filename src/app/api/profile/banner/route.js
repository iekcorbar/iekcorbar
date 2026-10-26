import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { sniffImage, stripJpegMetadata } from '@/lib/image-sniff';
import { BANNER_UPLOAD_DIR as UPLOAD_DIR, deleteBannerFile } from '@/lib/banner-files';
import {
  BANNER_MAX_STILL_BYTES, BANNER_MAX_ANIMATED_BYTES, BANNER_MIN_WIDTH, BANNER_MIN_HEIGHT, BANNER_MAX_DIMENSION
} from '@/lib/profile-theme';

// Player-uploaded profile banner. AI-generated art and animated GIF / WebP /
// APNG are allowed. Nothing the browser says about the file is trusted: format,
// dimensions and the animated flag are read from the bytes (lib/image-sniff.js),
// SVG/HTML/anything unreadable is rejected, and JPEG EXIF/GPS metadata is
// stripped before the file is written. One banner per player: a new upload
// replaces (and deletes) the old file. Served by the generic /uploads/*
// handler in app.js/server.js. Staff can remove a banner from the Staff Panel
// (api/admin/profile-bios, ?what=banner).

export const dynamic = 'force-dynamic';

const COOLDOWN_MS = 20 * 1000;
if (!global.__banner_uploads) global.__banner_uploads = new Map();

const fileNameFor = (email, ext) => `${crypto.createHash('sha1').update(String(email).toLowerCase()).digest('hex').slice(0, 12)}-${Date.now()}.${ext}`;

export async function POST(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
  if (!user.mcUsername) return NextResponse.json({ error: 'Link your Minecraft account first — banners belong to your public profile.' }, { status: 400 });

  const last = global.__banner_uploads.get(user.email.toLowerCase()) || 0;
  if (Date.now() - last < COOLDOWN_MS) {
    return NextResponse.json({ error: 'Please wait a few seconds before uploading again.' }, { status: 429 });
  }

  try {
    const form = await request.formData();
    const file = form.get('file');
    if (!file || typeof file === 'string' || !file.size) return NextResponse.json({ error: 'No image received.' }, { status: 400 });
    if (file.size > BANNER_MAX_ANIMATED_BYTES) {
      return NextResponse.json({ error: `That file is too large (max ${BANNER_MAX_ANIMATED_BYTES / 1024 / 1024} MB, and ${BANNER_MAX_STILL_BYTES / 1024 / 1024} MB for a still image).` }, { status: 413 });
    }

    let buffer = Buffer.from(await file.arrayBuffer());
    const info = sniffImage(buffer);
    if (!info) return NextResponse.json({ error: 'That is not a PNG, JPEG, GIF or WebP image.' }, { status: 400 });

    const limit = info.animated ? BANNER_MAX_ANIMATED_BYTES : BANNER_MAX_STILL_BYTES;
    if (buffer.length > limit) {
      return NextResponse.json({ error: info.animated
        ? `Animated banners can be at most ${limit / 1024 / 1024} MB.`
        : `Still banners can be at most ${limit / 1024 / 1024} MB (animated GIF/WebP may be up to ${BANNER_MAX_ANIMATED_BYTES / 1024 / 1024} MB).` }, { status: 413 });
    }
    if (!info.width || !info.height || info.width > BANNER_MAX_DIMENSION || info.height > BANNER_MAX_DIMENSION) {
      return NextResponse.json({ error: `The image is too large — keep it under ${BANNER_MAX_DIMENSION} × ${BANNER_MAX_DIMENSION} pixels.` }, { status: 400 });
    }
    if (info.width < BANNER_MIN_WIDTH || info.height < BANNER_MIN_HEIGHT) {
      return NextResponse.json({ error: `The image is too small — at least ${BANNER_MIN_WIDTH} × ${BANNER_MIN_HEIGHT} pixels.` }, { status: 400 });
    }

    if (info.type === 'jpeg') buffer = stripJpegMetadata(buffer); // no GPS / camera data in public files

    await fs.promises.mkdir(UPLOAD_DIR, { recursive: true });
    const name = fileNameFor(user.email, info.ext);
    await fs.promises.writeFile(path.join(UPLOAD_DIR, name), buffer);
    const url = `/uploads/banners/${name}`;

    const result = await db.setProfileBannerImage(user.email, url);
    if (!result.success) {
      await deleteBannerFile(url);
      return NextResponse.json({ error: 'Could not save your banner (database unreachable). Please try again shortly.' }, { status: 500 });
    }
    await deleteBannerFile(result.previous); // replaced banner no longer needed

    global.__banner_uploads.set(user.email.toLowerCase(), Date.now());
    return NextResponse.json({ success: true, bannerImage: url, animated: info.animated });
  } catch (err) {
    console.error('Banner upload error:', err);
    return NextResponse.json({ error: 'Could not upload the banner.' }, { status: 500 });
  }
}

// Remove my own banner (back to the preset theme).
export async function DELETE(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
  const result = await db.setProfileBannerImage(user.email, null);
  if (!result.success) return NextResponse.json({ error: 'Could not remove your banner right now.' }, { status: 500 });
  await deleteBannerFile(result.previous);
  return NextResponse.json({ success: true });
}
