import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// GET /api/carousel/sets -> list every set (id, name, isActive, itemCount).
export async function GET() {
  try {
    const sets = await db.getCarouselSets();
    return NextResponse.json({ sets });
  } catch (err) {
    return NextResponse.json({ error: "Failed to load carousel sets" }, { status: 500 });
  }
}

// POST body shapes (session-authenticated, 'settings' permission required):
//   { name }                -> create a new set
//   { id, name }             -> rename an existing set
//   { id, activate: true }   -> make this the one active set
export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'settings_carousel');
    if (auth.error) return auth.error;

    const { id, name, activate } = await request.json();

    if (activate) {
      if (!id) {
        return NextResponse.json({ error: "Missing set ID" }, { status: 400 });
      }
      await db.setActiveCarouselSet(id);
      await logAudit(auth.user, { area: 'settings_carousel', action: 'activate set', target: `set ${id}` });
      return NextResponse.json({ success: true, sets: await db.getCarouselSets() });
    }

    if (!name || !name.trim()) {
      return NextResponse.json({ error: "Set name is required." }, { status: 400 });
    }

    const saved = await db.saveCarouselSet({ id: id || undefined, name: name.trim() });
    if (!saved) {
      return NextResponse.json({ error: "Failed to save set (database unreachable). Please try again shortly." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'settings_carousel', action: id ? 'rename set' : 'create set', target: name.trim() });
    return NextResponse.json({ success: true, sets: await db.getCarouselSets() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to save carousel set" }, { status: 500 });
  }
}

// DELETE ?id=&role= -> deletes the set and every image inside it.
export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_carousel');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: "Missing set ID" }, { status: 400 });
    }

    await db.deleteCarouselSet(id);
    await logAudit(auth.user, { area: 'settings_carousel', action: 'delete set', target: `set ${id}` });
    return NextResponse.json({ success: true, sets: await db.getCarouselSets() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete carousel set" }, { status: 500 });
  }
}
