import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
// Public feed for /news — news items and upcoming server/website features,
// grouped by topic. See src/app/api/news/topics/route.js for the topic list
// and src/app/api/news/follow/route.js for a player's own subscriptions.
export async function GET() {
  try {
    const posts = await db.getNewsPosts();
    return NextResponse.json({ posts });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve news posts" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const auth = await requirePermission(request, 'news');
    if (auth.error) return auth.error;

    const { title, type, topic, content, author, link } = await request.json();
    if (!title || !topic || !content) {
      return NextResponse.json({ error: "Missing required news post fields." }, { status: 400 });
    }
    const saved = await db.saveNewsPost({ title, type, topic, content, author: author || "Staff", link });
    if (!saved) {
      return NextResponse.json({ error: "Could not save that news post — the database may be unreachable." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'news', action: 'create', target: title });
    return NextResponse.json({ success: true, posts: await db.getNewsPosts() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to create news post" }, { status: 500 });
  }
}

export async function PUT(request) {
  try {
    const auth = await requirePermission(request, 'news');
    if (auth.error) return auth.error;

    const { id, title, type, topic, content, author, link } = await request.json();
    if (!id || !title || !topic || !content) {
      return NextResponse.json({ error: "Missing required news post fields." }, { status: 400 });
    }
    const saved = await db.saveNewsPost({ id, title, type, topic, content, author: author || "Staff", link });
    if (!saved) {
      return NextResponse.json({ error: "Could not save that news post — the database may be unreachable." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'news', action: 'update', target: title });
    return NextResponse.json({ success: true, posts: await db.getNewsPosts() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to save news post" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'news');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: "Missing news post ID parameter." }, { status: 400 });
    }
    const deleted = await db.deleteNewsPost(id);
    if (!deleted) {
      return NextResponse.json({ error: "Could not delete that news post — it may already be gone, or the database is unreachable." }, { status: 500 });
    }
    await logAudit(auth.user, { area: 'news', action: 'delete', target: `news post ${id}` });
    return NextResponse.json({ success: true, posts: await db.getNewsPosts() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete news post" }, { status: 500 });
  }
}
