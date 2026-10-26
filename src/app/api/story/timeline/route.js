import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { storyActor } from '@/lib/story';

import { logAudit } from '@/lib/audit';
// Story Room world timeline. Events are ordered by `sortKey` (any whole
// number — e.g. an in-world year), with a free-text `eraLabel` for display
// ("Year 312, Spring") and an optional link to a chapter.

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const events = await db.getStoryTimeline();
  if (!events) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ events });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'edit');
  if (auth.error) return auth.error;

  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }

  const title = String(body.title || '').trim().slice(0, 255);
  if (!title) return NextResponse.json({ error: 'A title is required.' }, { status: 400 });
  const sortKey = parseInt(body.sortKey, 10);

  const id = await db.saveStoryTimelineEvent({
    id: body.id || null,
    sortKey: Number.isFinite(sortKey) ? sortKey : 0,
    eraLabel: String(body.eraLabel || '').trim().slice(0, 100),
    title,
    description: String(body.description || '').trim().slice(0, 5000),
    nodeId: body.nodeId || null
  }, storyActor(auth.user));
  if (!id) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ success: true, id });
}

export async function DELETE(request) {
  const auth = await requireStoryAccess(request, 'manage');
  if (auth.error) return auth.error;

  const id = new URL(request.url).searchParams.get('id');
  if (!id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
  const ok = await db.deleteStoryTimelineEvent(id);
  if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  await logAudit({ mcUsername: auth.access.actor, role: auth.access.isGuest ? 'story-guest' : auth.user?.role }, { area: 'story', action: 'delete timeline event', target: `event ${id}` });
  return NextResponse.json({ success: true });
}
