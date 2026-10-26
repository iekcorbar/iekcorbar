import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { isAdminRole } from '@/lib/staff-tabs';
import { storyActor } from '@/lib/story';

// Story Room margin comments on a chapter. Staff and guests with comment or
// edit rights can comment and resolve/reopen; only the author (or an admin)
// can delete a comment.

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const nodeId = new URL(request.url).searchParams.get('nodeId');
  if (!nodeId) return NextResponse.json({ error: 'Missing nodeId' }, { status: 400 });
  const comments = await db.getStoryComments(nodeId);
  if (!comments) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ comments });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'comment');
  if (auth.error) return auth.error;

  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }

  if (body.action === 'add') {
    const text = String(body.body || '').trim().slice(0, 4000);
    if (!body.nodeId || !text) return NextResponse.json({ error: 'Write a comment first.' }, { status: 400 });
    const quote = body.quote ? String(body.quote).trim().slice(0, 500) : null;
    const ok = await db.addStoryComment({ nodeId: body.nodeId, author: storyActor(auth.user), body: text, quote });
    if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true });
  }

  if (body.action === 'resolve') {
    if (!body.id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
    const ok = await db.setStoryCommentResolved(body.id, !!body.resolved);
    if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true });
  }

  return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
}

export async function DELETE(request) {
  const auth = await requireStoryAccess(request, 'comment');
  if (auth.error) return auth.error;

  const id = new URL(request.url).searchParams.get('id');
  if (!id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
  const comment = await db.getStoryComment(id);
  if (!comment) return NextResponse.json({ error: 'Comment not found.' }, { status: 404 });
  if (comment.author !== storyActor(auth.user) && !isAdminRole(auth.user.role)) {
    return NextResponse.json({ error: 'You can only delete your own comments.' }, { status: 403 });
  }
  const ok = await db.deleteStoryComment(id);
  if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ success: true });
}
