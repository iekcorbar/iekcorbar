import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// GET /api/carousel               -> public homepage view: a random sample
//                                     (max 10) of the currently active set's images.
// GET /api/carousel?setId=<id>    -> staff CRUD view (Carousel Manager): every
//                                     image in that one set, unfiltered.
// GET /api/carousel?itemId=<id>   -> which sets one slide currently belongs to
//                                     (feeds the "also show in..." checklist on
//                                     the edit form — see db.getCarouselItemSetIds).
export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const setId = searchParams.get('setId');
    const itemId = searchParams.get('itemId');

    if (itemId) {
      return NextResponse.json({ setIds: await db.getCarouselItemSetIds(itemId) });
    }

    const items = setId ? await db.getCarouselItemsBySet(setId) : await db.getCarouselDisplay();
    return NextResponse.json({ items });
  } catch (err) {
    return NextResponse.json({ error: "Failed to load carousel items" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'settings_carousel');
    if (auth.error) return auth.error;

    const body = await request.json();

    // Drag-reorder within one set (Carousel Manager) — same shape as the
    // wiki index's 'reorder' action, see db.reorderCarouselItems(). Order
    // is scoped to one set at a time — a shared slide's position in other
    // sets is untouched.
    if (body.action === 'reorder') {
      const { orderedIds, setId: reorderSetId } = body;
      if (!Array.isArray(orderedIds) || orderedIds.length === 0 || !reorderSetId) {
        return NextResponse.json({ error: "Missing orderedIds or setId" }, { status: 400 });
      }
      const saved = await db.reorderCarouselItems(orderedIds, reorderSetId);
      if (!saved) {
        return NextResponse.json({ error: "Could not save the new order (database unreachable). Please try again shortly." }, { status: 500 });
      }
      await logAudit(auth.user, { area: 'settings_carousel', action: 'reorder', target: 'Carousel slides' });
      return NextResponse.json({ success: true, items: await db.getCarouselItemsBySet(reorderSetId) });
    }

    // `setIds` is the full desired membership list for this slide — a
    // single upload can belong to multiple sets at once (checkboxes on the
    // edit form) rather than needing to be re-uploaded into each one.
    const { id, title, desc, src, setIds, setId } = body;
    const resolvedSetIds = Array.isArray(setIds) ? setIds : (setId ? [setId] : []);

    if (!title || !desc || !src || resolvedSetIds.length === 0) {
      return NextResponse.json({ error: "Title, description, image, and at least one set are required." }, { status: 400 });
    }

    const newItem = {
      id: id || undefined,
      title,
      desc,
      src,
      setIds: resolvedSetIds
    };

    await db.saveCarouselItem(newItem);
    // Refresh every set this slide touches (old + new memberships) so a
    // staff member looking at any of them sees the change immediately —
    // the caller only actually renders whichever set is currently selected.
    await logAudit(auth.user, { area: 'settings_carousel', action: id ? 'update' : 'create', target: title });
    const refreshSetId = resolvedSetIds[0];
    return NextResponse.json({ success: true, item: newItem, items: await db.getCarouselItemsBySet(refreshSetId) });
  } catch (err) {
    return NextResponse.json({ error: "Failed to save carousel item" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_carousel');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const setId = searchParams.get('setId');

    if (!id) {
      return NextResponse.json({ error: "Missing slide ID" }, { status: 400 });
    }

    // Removes the slide from this one set — it may still be shared with
    // others, in which case only the membership is dropped (the underlying
    // image is only actually deleted once this was its last one).
    if (setId) {
      await db.removeCarouselItemFromSet(id, setId);
    } else {
      await db.deleteCarouselItem(id);
    }
    await logAudit(auth.user, { area: 'settings_carousel', action: setId ? 'remove from set' : 'delete', target: `slide ${id}` });
    return NextResponse.json({ success: true, items: setId ? await db.getCarouselItemsBySet(setId) : [] });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete carousel item" }, { status: 500 });
  }
}
