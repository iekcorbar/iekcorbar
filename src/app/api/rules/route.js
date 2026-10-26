import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
export async function GET() {
  return NextResponse.json({ rules: await db.getRules() });
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'settings_rules');
    if (auth.error) return auth.error;

    const { role, ...rule } = await request.json();
    if (!rule.title || !rule.content) return NextResponse.json({ error: "Missing fields" }, { status: 400 });
    await db.saveRule(rule);
    await logAudit(auth.user, { area: 'settings_rules', action: 'create', target: rule.title });
    return NextResponse.json({ success: true, rules: await db.getRules() });
  } catch {
    return NextResponse.json({ error: "Failed to save rule" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'settings_rules');
    if (auth.error) return auth.error;

    const { role, ...rule } = await request.json();
    await db.saveRule(rule);
    await logAudit(auth.user, { area: 'settings_rules', action: 'update', target: rule.title || rule.id });
    return NextResponse.json({ success: true, rules: await db.getRules() });
  } catch {
    return NextResponse.json({ error: "Failed to update rule" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'settings_rules');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    await db.deleteRule(id);
    await logAudit(auth.user, { area: 'settings_rules', action: 'delete', target: `rule ${id}` });
    return NextResponse.json({ success: true, rules: await db.getRules() });
  } catch {
    return NextResponse.json({ error: "Failed to delete rule" }, { status: 500 });
  }
}
