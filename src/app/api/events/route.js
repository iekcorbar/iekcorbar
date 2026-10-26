import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { db } from '@/lib/db';
import { runEventReminders } from '@/lib/reminders';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
const MAX_POSTER_BYTES = 8 * 1024 * 1024; // 8MB

async function savePoster(file, eventId, request) {
  const bytes = await file.arrayBuffer();
  const buffer = Buffer.from(bytes);

  const uploadDir = path.join(process.cwd(), 'public', 'uploads', 'events');
  await fs.promises.mkdir(uploadDir, { recursive: true });

  const ext = path.extname(file.name || '') || '.png';
  const safeId = String(eventId || Date.now()).replace(/[^a-zA-Z0-9_]/g, '');
  const filename = `${safeId}-${Date.now()}${ext}`;
  await fs.promises.writeFile(path.join(uploadDir, filename), buffer);

  const host = request.headers.get('host') || 'localhost:3000';
  const protocol = request.headers.get('x-forwarded-proto') || 'http';
  return `${protocol}://${host}/uploads/events/${filename}`;
}

// Parses either multipart/form-data (poster upload) or plain JSON into a
// common shape. Replaces the old "Poster Theme" dropdown (image: 'derby' |
// 'boss', mapped to two hardcoded static images) with a real uploaded
// image — same runtime-upload pattern as guide attachments (see
// api/guide/route.js and CLAUDE.md "Runtime file uploads"). An edit that
// doesn't touch the poster carries the existing one forward rather than
// wiping it.
async function parseEventRequest(request) {
  const auth = await requirePermission(request, 'events');
  if (auth.error) return { error: auth.error };

  const contentType = request.headers.get('content-type') || '';
  let id, title, description, date, time, timezone, file, removeImage;

  if (contentType.includes('multipart/form-data')) {
    const formData = await request.formData();
    id = formData.get('id') || undefined;
    title = formData.get('title');
    description = formData.get('description');
    date = formData.get('date');
    time = formData.get('time');
    timezone = formData.get('timezone');
    file = formData.get('file');
    if (file && typeof file === 'string') file = null; // no file actually attached
    removeImage = formData.get('removeImage') === 'true';
  } else {
    const body = await request.json();
    ({ id, title, description, date, time, timezone, removeImage } = body);
  }

  if (!title || !description || !date || !time) {
    return { error: NextResponse.json({ error: "Missing required fields" }, { status: 400 }) };
  }

  let image;
  if (file && file.size > 0) {
    if (!file.type?.startsWith('image/')) {
      return { error: NextResponse.json({ error: "Poster must be an image file" }, { status: 400 }) };
    }
    if (file.size > MAX_POSTER_BYTES) {
      return { error: NextResponse.json({ error: "Poster image must be smaller than 8MB" }, { status: 400 }) };
    }
    image = await savePoster(file, id, request);
  } else if (removeImage) {
    image = null;
  } else if (id) {
    // Editing without touching the poster — keep whatever it had (including
    // a legacy 'derby'/'boss' theme sentinel, which the frontend still maps
    // to its static image for events created before this change).
    const existing = (await db.getEvents()).find(e => String(e.id) === String(id));
    image = existing ? existing.image : null;
  } else {
    image = null;
  }

  return { user: auth.user, event: { id, title, description, date, time, timezone: timezone || 'UTC+2', image } };
}

export async function GET() {
  try {
    // Opportunistic reminder check (throttled inside; see src/lib/reminders.js).
    runEventReminders().catch(() => {});
    const events = await db.getEvents();
    const registrations = await db.getRegistrations();
    return NextResponse.json({ events, registrations });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve events" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const parsed = await parseEventRequest(request);
    if (parsed.error) return parsed.error;

    await db.saveEvent(parsed.event);
    await logAudit(parsed.user, { area: 'events', action: parsed.event.id ? 'update' : 'create', target: parsed.event.title });
    return NextResponse.json({ success: true, event: parsed.event });
  } catch (err) {
    console.error("Events POST error:", err);
    return NextResponse.json({ error: "Failed to save event" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'events');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: "Missing event ID" }, { status: 400 });
    }

    await db.deleteEvent(id);
    await logAudit(auth.user, { area: 'events', action: 'delete', target: `event ${id}` });
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete event" }, { status: 500 });
  }
}
