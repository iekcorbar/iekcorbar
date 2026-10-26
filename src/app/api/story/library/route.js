import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requireStoryAccess } from '@/lib/story-access';
import { storyActor, STORY_ENTITY_KIND_KEYS } from '@/lib/story';

import { logAudit } from '@/lib/audit';
// Story Room lore library: characters, places, factions, items. GET lists
// every entry; GET ?mentions=<name> lists the chapters linking to it via
// [[Name]]. POST saves (create when no id), DELETE ?id= removes an entry.

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const mentions = new URL(request.url).searchParams.get('mentions');
  if (mentions) {
    const docs = await db.getStoryMentions(mentions);
    if (!docs) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ docs });
  }

  const entities = await db.getStoryEntities();
  if (!entities) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ entities });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'edit');
  if (auth.error) return auth.error;

  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }

  const name = String(body.name || '').trim().slice(0, 150);
  if (!name) return NextResponse.json({ error: 'A name is required.' }, { status: 400 });
  if (/[\[\]]/.test(name)) return NextResponse.json({ error: 'Names cannot contain [ or ] (they are used for lore links).' }, { status: 400 });
  const kind = STORY_ENTITY_KIND_KEYS.includes(body.kind) ? body.kind : 'other';

  const imageUrl = String(body.imageUrl || '').trim().slice(0, 500);
  if (imageUrl && !/^https?:\/\//i.test(imageUrl) && !imageUrl.startsWith('/')) {
    return NextResponse.json({ error: 'The image must be a web link (https://...).' }, { status: 400 });
  }

  const existing = (await db.getStoryEntities()) || [];
  if (existing.some(e => e.name.toLowerCase() === name.toLowerCase() && e.id !== body.id)) {
    return NextResponse.json({ error: `There is already a lore entry called "${name}".` }, { status: 400 });
  }

  const id = await db.saveStoryEntity({
    id: body.id || null,
    kind,
    name,
    summary: String(body.summary || '').trim().slice(0, 500),
    content: String(body.content || ''),
    imageUrl
  }, storyActor(auth.user));
  if (!id) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ success: true, id });
}

export async function DELETE(request) {
  const auth = await requireStoryAccess(request, 'manage');
  if (auth.error) return auth.error;

  const id = new URL(request.url).searchParams.get('id');
  if (!id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
  const ok = await db.deleteStoryEntity(id);
  if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  await logAudit({ mcUsername: auth.access.actor, role: auth.access.isGuest ? 'story-guest' : auth.user?.role }, { area: 'story', action: 'delete lore entry', target: `entry ${id}` });
  return NextResponse.json({ success: true });
}
