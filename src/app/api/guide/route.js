import { NextResponse } from 'next/server';
import fs from 'fs';
import path from 'path';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024; // 20MB — comfortably fits a manual PDF
const ALLOWED_TYPES = ['application/pdf'];

function isAllowedFile(file) {
  return file.type?.startsWith('image/') || ALLOWED_TYPES.includes(file.type);
}

async function saveAttachment(file, stepId, request) {
  const bytes = await file.arrayBuffer();
  const buffer = Buffer.from(bytes);

  const uploadDir = path.join(process.cwd(), 'public', 'uploads', 'guide');
  await fs.promises.mkdir(uploadDir, { recursive: true });

  const ext = path.extname(file.name || '') || (file.type === 'application/pdf' ? '.pdf' : '');
  const safeStepId = String(stepId || Date.now()).replace(/[^a-zA-Z0-9_]/g, '');
  const filename = `${safeStepId}-${Date.now()}${ext}`;
  await fs.promises.writeFile(path.join(uploadDir, filename), buffer);

  const host = request.headers.get('host') || 'localhost:3000';
  const protocol = request.headers.get('x-forwarded-proto') || 'http';
  return {
    attachmentUrl: `${protocol}://${host}/uploads/guide/${filename}`,
    attachmentName: file.name || filename
  };
}

// Parses either JSON or multipart/form-data into a common shape, and — for
// an edit that doesn't touch the attachment — carries the existing
// attachment forward so it isn't wiped by a plain text-only save.
async function parseStepRequest(request) {
  const auth = await requirePermission(request, 'settings_guide');
  if (auth.error) return { error: auth.error };

  const contentType = request.headers.get('content-type') || '';
  let id, title, content, file, removeAttachment;

  if (contentType.includes('multipart/form-data')) {
    const formData = await request.formData();
    id = formData.get('id') || undefined;
    title = formData.get('title');
    content = formData.get('content');
    file = formData.get('file');
    if (file && typeof file === 'string') file = null; // no file actually attached
    removeAttachment = formData.get('removeAttachment') === 'true';
  } else {
    const body = await request.json();
    ({ id, title, content, removeAttachment } = body);
  }

  let attachmentUrl, attachmentName;
  if (file && isAllowedFile(file) && file.size > 0) {
    if (file.size > MAX_ATTACHMENT_BYTES) {
      return { error: NextResponse.json({ error: "Attachment must be smaller than 20MB" }, { status: 400 }) };
    }
    ({ attachmentUrl, attachmentName } = await saveAttachment(file, id, request));
  } else if (file && !isAllowedFile(file)) {
    return { error: NextResponse.json({ error: "Only images and PDF files are allowed" }, { status: 400 }) };
  } else if (removeAttachment) {
    attachmentUrl = null;
    attachmentName = null;
  } else if (id) {
    // Editing without touching the attachment — keep whatever it had.
    const existing = (await db.getGuide()).find(s => String(s.id) === String(id));
    attachmentUrl = existing ? existing.attachmentUrl : undefined;
    attachmentName = existing ? existing.attachmentName : undefined;
  }

  return { user: auth.user, step: { id, title, content, attachmentUrl, attachmentName } };
}

export async function GET() {
  return NextResponse.json({ guide: await db.getGuide() });
}

export async function POST(request) {
  try {
    const parsed = await parseStepRequest(request);
    if (parsed.error) return parsed.error;
    const { step } = parsed;
    if (!step.title || !step.content) return NextResponse.json({ error: "Missing fields" }, { status: 400 });
    await db.saveGuideStep(step);
    await logAudit(parsed.user, { area: 'settings_guide', action: 'create', target: step.title });
    return NextResponse.json({ success: true, guide: await db.getGuide() });
  } catch (err) {
    console.error("Guide POST error:", err);
    return NextResponse.json({ error: "Failed to save guide step" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const parsed = await parseStepRequest(request);
    if (parsed.error) return parsed.error;
    await db.saveGuideStep(parsed.step);
    await logAudit(parsed.user, { area: 'settings_guide', action: 'update', target: parsed.step.title });
    return NextResponse.json({ success: true, guide: await db.getGuide() });
  } catch (err) {
    console.error("Guide PUT error:", err);
    return NextResponse.json({ error: "Failed to update guide step" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_guide');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    await db.deleteGuideStep(id);
    await logAudit(auth.user, { area: 'settings_guide', action: 'delete', target: `guide step ${id}` });
    return NextResponse.json({ success: true, guide: await db.getGuide() });
  } catch {
    return NextResponse.json({ error: "Failed to delete guide step" }, { status: 500 });
  }
}
