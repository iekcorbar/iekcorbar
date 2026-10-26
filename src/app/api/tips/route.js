import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// "Tip of the Day" — GET is fully public (homepage widget). Picks one
// active tip deterministically per calendar day (day-of-year modulo active
// count) rather than truly at random, so every visitor sees the same tip
// all day and it rotates the next — matches the "of the day" framing
// instead of reshuffling on every page load/refresh.
function dayOfYear(date) {
  const start = new Date(date.getFullYear(), 0, 0);
  const diff = date - start;
  return Math.floor(diff / (1000 * 60 * 60 * 24));
}

export async function GET() {
  const tips = await db.getTips();
  const active = tips.filter(t => t.active);
  const todaysTip = active.length > 0 ? active[dayOfYear(new Date()) % active.length] : null;
  return NextResponse.json({ tips, todaysTip });
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'settings_tips');
    if (auth.error) return auth.error;

    const { role, ...tip } = await request.json();
    if (!tip.content || !tip.content.trim()) return NextResponse.json({ error: "Tip content is required" }, { status: 400 });
    const saved = await db.saveTip(tip);
    if (!saved) return NextResponse.json({ error: "Could not save tip (database unreachable). Please try again shortly." }, { status: 500 });
    await logAudit(auth.user, { area: 'settings_tips', action: 'create', target: String(tip.content).slice(0, 80) });
    return NextResponse.json({ success: true, tips: await db.getTips() });
  } catch {
    return NextResponse.json({ error: "Failed to save tip" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'settings_tips');
    if (auth.error) return auth.error;

    const { role, ...tip } = await request.json();
    const saved = await db.saveTip(tip);
    if (!saved) return NextResponse.json({ error: "Could not update tip (database unreachable). Please try again shortly." }, { status: 500 });
    await logAudit(auth.user, { area: 'settings_tips', action: 'update', target: tip.id || String(tip.content || '').slice(0, 80) });
    return NextResponse.json({ success: true, tips: await db.getTips() });
  } catch {
    return NextResponse.json({ error: "Failed to update tip" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_tips');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const deleted = await db.deleteTip(id);
    if (!deleted) return NextResponse.json({ error: "Could not delete tip (database unreachable). Please try again shortly." }, { status: 500 });
    await logAudit(auth.user, { area: 'settings_tips', action: 'delete', target: `tip ${id}` });
    return NextResponse.json({ success: true, tips: await db.getTips() });
  } catch {
    return NextResponse.json({ error: "Failed to delete tip" }, { status: 500 });
  }
}
