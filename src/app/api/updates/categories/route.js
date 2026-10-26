import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission, isStaffRole } from '@/lib/permissions';
import { getSessionUser } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// Same staff-only gate as GET /api/updates — the /updates page fetches both.
export async function GET(request) {
  try {
    const user = await getSessionUser(request);
    if (!user || !(await isStaffRole(user.role))) {
      return NextResponse.json({ error: 'Staff only' }, { status: user ? 403 : 401 });
    }
    const categories = await db.getUpdateCategories();
    return NextResponse.json({ categories });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve update categories" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'updates');
    if (auth.error) return auth.error;

    const { category } = await request.json();
    if (!category || !category.trim()) {
      return NextResponse.json({ error: "Category name cannot be empty." }, { status: 400 });
    }

    await db.addUpdateCategory(category.trim());
    await logAudit(auth.user, { area: 'updates', action: 'add category', target: category.trim() });
    return NextResponse.json({ success: true, categories: await db.getUpdateCategories() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to create category" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'updates');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const category = searchParams.get('category');

    if (!category) {
      return NextResponse.json({ error: "Missing category parameter." }, { status: 400 });
    }

    await db.deleteUpdateCategory(category);
    await logAudit(auth.user, { area: 'updates', action: 'delete category', target: category });
    return NextResponse.json({ success: true, categories: await db.getUpdateCategories(), updates: await db.getUpdates() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete category" }, { status: 500 });
  }
}
