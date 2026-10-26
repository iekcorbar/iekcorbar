import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission, isStaffRole } from '@/lib/permissions';
import { getSessionUser } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// /updates is the changelog — staff-only reading (any staff role, not just
// those with the 'updates' Staff Panel tab itself) per an explicit 2026-09-16
// request, unlike every other read-only feed in this codebase which is
// public or just login-gated.
export async function GET(request) {
  try {
    const user = await getSessionUser(request);
    if (!user || !(await isStaffRole(user.role))) {
      return NextResponse.json({ error: 'Staff only' }, { status: user ? 403 : 401 });
    }
    const updates = await db.getUpdates();
    return NextResponse.json({ updates });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve updates" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'updates');
    if (auth.error) return auth.error;

    const { title, category, content, author } = await request.json();
    if (!title || !category || !content) {
      return NextResponse.json({ error: "Missing required update fields." }, { status: 400 });
    }
    await db.saveUpdate({ title, category, content, author: author || "Staff" });
    await logAudit(auth.user, { area: 'updates', action: 'create', target: title });
    return NextResponse.json({ success: true, updates: await db.getUpdates() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to create update" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'updates');
    if (auth.error) return auth.error;

    const { id, title, category, content, author } = await request.json();
    if (!id || !title || !category || !content) {
      return NextResponse.json({ error: "Missing required update fields." }, { status: 400 });
    }
    await db.saveUpdate({ id, title, category, content, author: author || "Staff" });
    await logAudit(auth.user, { area: 'updates', action: 'update', target: title });
    return NextResponse.json({ success: true, updates: await db.getUpdates() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to save update" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'updates');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: "Missing update ID parameter." }, { status: 400 });
    }
    const deleted = await db.deleteUpdate(id);
    if (!deleted) {
      return NextResponse.json({ error: "Could not delete that update — it may already be gone, or the database is unreachable." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'updates', action: 'delete', target: `update ${id}` });
    return NextResponse.json({ success: true, updates: await db.getUpdates() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete update" }, { status: 500 });
  }
}
