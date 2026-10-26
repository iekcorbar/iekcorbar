import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireRole, getSessionUser } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// Catalog CRUD, colocated with the public /achievements page — same pattern
// as shop items (see api/shop/items/route.js).
function isStaff(user) {
  return !!user && (user.role === 'admin' || user.role === 'moderator');
}

// GET is public — the achievement gallery needs to be browsable while
// logged out, showing what exists even if nothing's unlocked yet. Inactive
// (in-progress/retired) achievements are staff-only, same rule as shop items.
export async function GET(request) {
  try {
    // A missing/invalid session just means "treat as a regular visitor"
    // here (unlike POST/DELETE below) — this route never rejects an
    // anonymous caller, it only uses the session to decide whether to also
    // include inactive rows.
    const sessionUser = await getSessionUser(request);
    const achievements = await db.getAchievements();
    const visible = isStaff(sessionUser) ? achievements : achievements.filter(a => a.active);
    return NextResponse.json({ achievements: visible });
  } catch (err) {
    console.error("Achievements GET error:", err);
    return NextResponse.json({ error: "Failed to load achievements" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin', 'moderator']);
    if (auth.error) return auth.error;

    const body = await request.json();
    const { id, slug, title, description, icon, active, currencyReward } = body;

    if (!slug || !title) {
      return NextResponse.json({ error: "slug and title are required" }, { status: 400 });
    }
    if (currencyReward !== undefined && (!Number.isFinite(Number(currencyReward)) || Number(currencyReward) < 0)) {
      return NextResponse.json({ error: "currencyReward must be a non-negative number" }, { status: 400 });
    }

    const saved = await db.saveAchievement({ id, slug: slug.trim(), title: title.trim(), description, icon, active, currencyReward });
    if (!saved) {
      return NextResponse.json({ error: "Could not save achievement (database unreachable). Please try again shortly." }, { status: 500 });
    }

    await logAudit(auth.user, { area: 'achievements', action: id ? 'update' : 'create', target: title.trim() });
    const achievements = await db.getAchievements();
    return NextResponse.json({ success: true, achievements });
  } catch (err) {
    console.error("Achievements POST error:", err);
    return NextResponse.json({ error: "Failed to save achievement" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requireRole(request, ['admin', 'moderator']);
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    if (!id) return NextResponse.json({ error: "Missing id" }, { status: 400 });

    await db.deleteAchievement(id);
    await logAudit(auth.user, { area: 'achievements', action: 'delete', target: `achievement ${id}` });
    const achievements = await db.getAchievements();
    return NextResponse.json({ success: true, achievements });
  } catch (err) {
    console.error("Achievements DELETE error:", err);
    return NextResponse.json({ error: "Failed to delete achievement" }, { status: 500 });
  }
}
