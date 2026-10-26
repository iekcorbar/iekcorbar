import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getRoleTabs } from '@/lib/permissions';
import { requireStoryAccess, publicStoryAccess } from '@/lib/story-access';
import { isAdminRole } from '@/lib/staff-tabs';
import { STORY_STATUS_KEYS, storyHtmlToWikiMarkdown, storyActor } from '@/lib/story';

import { logAudit } from '@/lib/audit';
// Staff Panel → Story Room: the chapter/folder tree, chapter bodies, edit
// locks and "Publish to wiki". Access is staff with the `story` permission
// (Role Manager) or a Story Room guest link — see src/lib/story-access.js:
// reading needs any access, every POST action needs edit rights, deleting is
// staff-only, and publishing additionally needs `wiki` (guests never have
// it). See src/components/StoryRoomTab.js for the UI side.

const DB_ERROR = 'Could not reach the database. Nothing was changed — please try again shortly.';
const MAX_CONTENT_CHARS = 2_000_000;

function subtreeIds(tree, rootId) {
  const ids = [rootId];
  for (let i = 0; i < ids.length; i++) {
    for (const node of tree) {
      if (node.parentId === ids[i]) ids.push(node.id);
    }
  }
  return ids;
}

export async function GET(request) {
  const auth = await requireStoryAccess(request, 'read');
  if (auth.error) return auth.error;

  const { searchParams } = new URL(request.url);
  const id = searchParams.get('id');

  if (id) {
    const node = await db.getStoryNode(id);
    if (!node) return NextResponse.json({ error: 'Not found (or the database is unreachable).' }, { status: 404 });
    return NextResponse.json({ node });
  }

  const tree = await db.getStoryTree();
  if (!tree) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  return NextResponse.json({ tree, me: storyActor(auth.user), access: publicStoryAccess(auth.access) });
}

export async function POST(request) {
  const auth = await requireStoryAccess(request, 'edit');
  if (auth.error) return auth.error;
  const actor = storyActor(auth.user);

  let body;
  try { body = await request.json(); } catch { return NextResponse.json({ error: 'Invalid request' }, { status: 400 }); }
  const { action } = body;

  if (action === 'create') {
    const type = body.type === 'folder' ? 'folder' : 'doc';
    const title = String(body.title || '').trim().slice(0, 255);
    if (!title) return NextResponse.json({ error: 'A title is required.' }, { status: 400 });
    if (body.parentId) {
      const parent = await db.getStoryNode(body.parentId);
      if (!parent || parent.type !== 'folder') return NextResponse.json({ error: 'Items can only be created inside a folder.' }, { status: 400 });
    }
    const id = await db.createStoryNode({ parentId: body.parentId || null, type, title, actor });
    if (!id) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true, id });
  }

  if (action === 'save') {
    const title = String(body.title || '').trim().slice(0, 255);
    const content = String(body.content ?? '');
    if (!body.id || !title) return NextResponse.json({ error: 'A title is required.' }, { status: 400 });
    if (content.length > MAX_CONTENT_CHARS) return NextResponse.json({ error: 'This chapter is too long to save. Split it into several chapters.' }, { status: 400 });
    const result = await db.saveStoryContent(body.id, { title, content }, actor);
    if (!result.ok) {
      if (result.reason === 'lock') {
        const node = await db.getStoryNode(body.id);
        return NextResponse.json({ error: 'You no longer hold the edit lock for this chapter, so it was not saved.', lockUser: node?.lockUser || null }, { status: 409 });
      }
      return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    }
    return NextResponse.json({ success: true, savedAt: new Date().toISOString() });
  }

  if (action === 'meta') {
    if (!body.id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
    const patch = {};
    if (body.title !== undefined) {
      patch.title = String(body.title || '').trim().slice(0, 255);
      if (!patch.title) return NextResponse.json({ error: 'A title is required.' }, { status: 400 });
    }
    if (body.status !== undefined) {
      if (!STORY_STATUS_KEYS.includes(body.status)) return NextResponse.json({ error: 'Unknown status' }, { status: 400 });
      patch.status = body.status;
    }
    const ok = await db.updateStoryMeta(body.id, patch, actor);
    if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true });
  }

  if (action === 'move') {
    if (!body.id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
    const tree = await db.getStoryTree();
    if (!tree) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    const target = body.parentId || null;
    if (target) {
      const parent = tree.find(n => n.id === target);
      if (!parent || parent.type !== 'folder') return NextResponse.json({ error: 'You can only move items into a folder.' }, { status: 400 });
      if (subtreeIds(tree, body.id).includes(target)) return NextResponse.json({ error: 'A folder cannot be moved into itself.' }, { status: 400 });
    }
    const ok = await db.moveStoryNode(body.id, target);
    if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true });
  }

  if (action === 'reorder') {
    if (!Array.isArray(body.orderedIds) || body.orderedIds.length === 0) return NextResponse.json({ error: 'Missing orderedIds' }, { status: 400 });
    const ok = await db.reorderStoryNodes(body.orderedIds.map(String));
    if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: true });
  }

  // Also serves as the heartbeat: the editor calls this every 30s while open.
  if (action === 'lock') {
    if (!body.id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
    const force = !!body.force && isAdminRole(auth.user.role);
    const result = await db.acquireStoryLock(body.id, actor, force);
    if (result.error) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    return NextResponse.json({ success: result.ok, lockUser: result.lockUser });
  }

  if (action === 'unlock') {
    if (!body.id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });
    await db.releaseStoryLock(body.id, actor);
    return NextResponse.json({ success: true });
  }

  if (action === 'publish') {
    const tabs = await getRoleTabs(auth.user.role);
    if (!tabs.includes('wiki')) return NextResponse.json({ error: 'Publishing to the wiki also requires the Wiki Lore permission.' }, { status: 403 });
    const node = body.id ? await db.getStoryNode(body.id) : null;
    if (!node || node.type !== 'doc') return NextResponse.json({ error: 'Chapter not found.' }, { status: 404 });
    const category = String(body.category || '').trim().slice(0, 100) || 'Lore';
    const markdown = storyHtmlToWikiMarkdown(node.content);
    if (!markdown) return NextResponse.json({ error: 'This chapter is empty.' }, { status: 400 });
    const wikiId = node.publishedWikiId || `story-${node.id}`.slice(0, 50);
    const slug = node.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || wikiId;
    await db.saveWikiPage({ id: wikiId, slug, title: node.title, category, content: markdown, parentId: null, updatedBy: actor });
    const pages = await db.getWikiPages();
    const page = (pages || []).find(p => String(p.id) === wikiId);
    if (!page) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
    await db.setStoryPublished(node.id, wikiId);
    await logAudit({ mcUsername: actor, role: auth.user?.role }, { area: 'story', action: 'publish to wiki', target: node.title, detail: `category: ${category}` });
    return NextResponse.json({ success: true, wikiId, slug: page.slug || slug });
  }

  return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
}

export async function DELETE(request) {
  const auth = await requireStoryAccess(request, 'manage');
  if (auth.error) return auth.error;
  const actor = storyActor(auth.user);

  const { searchParams } = new URL(request.url);
  const id = searchParams.get('id');
  if (!id) return NextResponse.json({ error: 'Missing id' }, { status: 400 });

  const tree = await db.getStoryTree();
  if (!tree) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  if (!tree.some(n => n.id === id)) return NextResponse.json({ error: 'Not found' }, { status: 404 });

  const ids = subtreeIds(tree, id);
  const lockedByOther = tree.find(n => ids.includes(n.id) && n.lockUser && n.lockUser !== actor);
  if (lockedByOther && !isAdminRole(auth.user.role)) {
    return NextResponse.json({ error: `"${lockedByOther.title}" is being edited by ${lockedByOther.lockUser} right now.` }, { status: 409 });
  }

  const ok = await db.deleteStoryNodes(ids);
  if (!ok) return NextResponse.json({ error: DB_ERROR }, { status: 500 });
  await logAudit({ mcUsername: actor, role: auth.user?.role }, { area: 'story', action: 'delete chapter', target: tree.find(n => n.id === id)?.title || id, detail: `${ids.length} item(s) removed` });
  return NextResponse.json({ success: true, deleted: ids.length });
}
