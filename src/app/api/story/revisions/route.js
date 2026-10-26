import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { storyActor } from '@/lib/story';

// Story Room version history. GET ?nodeId= lists a chapter's revisions
// (without bodies), GET ?id= returns one revision in full. POST
// { action: 'restore', id } puts that revision's text back — it goes through
// the normal save path, so it needs the edit lock and itself becomes a new
// revision (restoring is never destructive).

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const { searchParams } = new URL(request.url);
  const id = searchParams.get('id');
  const nodeId = searchParams.get('nodeId');

  if (id) {
    const revision = await db.getStoryRevision(id);
    if (!revision) return NextResponse.json({ error: 'Revision not found.' }, { status: 404 });
    return NextResponse.json({ revision });
  }
  if (!nodeId) return NextResponse.json({ error: 'Missing nodeId' }, { status: 400 });
  const revisions = await db.getStoryRevisions(nodeId);
  if (!revisions) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ revisions });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'edit');
  if (auth.error) return auth.error;

  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }
  if (body.action !== 'restore' || !body.id) return NextResponse.json({ error: 'Unknown action' }, { status: 400 });

  const revision = await db.getStoryRevision(body.id);
  if (!revision) return NextResponse.json({ error: 'Revision not found.' }, { status: 404 });

  const result = await db.saveStoryContent(revision.nodeId, { title: revision.title, content: revision.content || '' }, storyActor(auth.user));
  if (!result.ok) {
    if (result.reason === 'lock') return NextResponse.json({ error: 'Open the chapter for editing first — restoring needs the edit lock.' }, { status: 409 });
    return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  }
  return NextResponse.json({ success: true, title: revision.title, content: revision.content || '' });
}
