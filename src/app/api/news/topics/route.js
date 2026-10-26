import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// Staff-managed topic list news posts are grouped under — same shape as
// /api/updates/categories, see that route for the pattern this mirrors.
export async function GET() {
  try {
    const topics = await db.getNewsTopics();
    return NextResponse.json({ topics });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve news topics" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'news');
    if (auth.error) return auth.error;

    const { topic } = await request.json();
    if (!topic || !topic.trim()) {
      return NextResponse.json({ error: "Topic name cannot be empty." }, { status: 400 });
    }

    await db.addNewsTopic(topic.trim());
    await logAudit(auth.user, { area: 'news', action: 'add topic', target: topic.trim() });
    return NextResponse.json({ success: true, topics: await db.getNewsTopics() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to create topic" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'news');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const topic = searchParams.get('topic');

    if (!topic) {
      return NextResponse.json({ error: "Missing topic parameter." }, { status: 400 });
    }

    await db.deleteNewsTopic(topic);
    await logAudit(auth.user, { area: 'news', action: 'delete topic', target: topic });
    return NextResponse.json({ success: true, topics: await db.getNewsTopics(), posts: await db.getNewsPosts() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete topic" }, { status: 500 });
  }
}
