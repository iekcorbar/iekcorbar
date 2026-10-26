import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { withRoleInfo } from '@/lib/permissions';
import { ACHIEVEMENT_SLUGS } from '@/lib/achievements';
import fs from 'fs';
import path from 'path';

// Private messages between members. Everything runs as the logged-in
// session user — the old version took `username`/`sender` from the client,
// so anyone could read or send as anyone. The Expo app (mobile/) still
// sends those fields and has no session, so its chat tab gets a 401 now.
//
// GET  ?overview=1                 conversations + everyone you can message
// GET  ?contact=X[&before=][&markRead=1] one conversation (newest 100, or older)
// GET  ?unread=1                   unread count for the nav badge
// POST { action: 'send', receiver, message } (or multipart with `image`)
// POST { action: 'read', contact } | { action: 'delete', id }
// POST { action: 'block' | 'unblock', username }

// "Who's looking at which conversation right now" — the chat page polls
// GET ?contact= every few seconds while a conversation is open, each poll
// stamps it here, and send skips the bell/push for a recipient who's
// already reading this exact conversation.
const ACTIVE_VIEW_WINDOW_MS = 12_000;
if (!global.__chat_active_viewers) global.__chat_active_viewers = new Map();
const viewKey = (viewer, contact) => `${String(viewer).toLowerCase()}:${String(contact).toLowerCase()}`;
const isActivelyViewing = (viewer, contact) => {
  const lastSeen = global.__chat_active_viewers.get(viewKey(viewer, contact));
  return typeof lastSeen === 'number' && Date.now() - lastSeen < ACTIVE_VIEW_WINDOW_MS;
};

const MAX_MESSAGE_LENGTH = 2000;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5MB
const IMAGE_TYPES = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/webp': 'webp' };
const CHAT_UPLOAD_DIR = path.join(process.cwd(), 'public', 'uploads', 'chat');

const lower = (s) => String(s || '').toLowerCase();

async function requireChatUser(request) {
  const user = await getSessionUser(request);
  if (!user) return { error: NextResponse.json({ error: 'Please log in to use chat.' }, { status: 401 }) };
  if (!user.mcUsername) return { error: NextResponse.json({ error: 'Link your Minecraft account first to use chat.' }, { status: 403 }) };
  return { user };
}

// Members you can message: linked accounts with a Minecraft name, not you.
async function getChatMembers(me) {
  const allUsers = (await db.getUsers()) || [];
  const unlinked = new Set(((await db.getLinkStates()) || []).map(x => lower(x.email)));
  const members = allUsers
    .filter(u => u.mcUsername && lower(u.mcUsername) !== lower(me.mcUsername) && !unlinked.has(lower(u.email)) && u.role !== 'pending')
    .map(({ mcUsername, role, customSkinUrl, email }) => ({ mcUsername, role, customSkinUrl: customSkinUrl || null, email }));
  return withRoleInfo(members);
}

function blockState(blocks, me, other) {
  return {
    byMe: blocks.some(b => lower(b.blocker) === lower(me) && lower(b.blocked) === lower(other)),
    byThem: blocks.some(b => lower(b.blocker) === lower(other) && lower(b.blocked) === lower(me))
  };
}

const parseDate = (v) => {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
};

export async function GET(request) {
  try {
    const auth = await requireChatUser(request);
    if (auth.error) return auth.error;
    const me = auth.user;
    const { searchParams } = new URL(request.url);

    if (searchParams.get('unread')) {
      return NextResponse.json({ success: true, unread: await db.getUnreadChatCount(me.mcUsername) });
    }

    // Everyone I blocked + every conversation I muted (Profile > Account card).
    if (searchParams.get('blocks')) {
      const [blocks, muted] = await Promise.all([db.getChatBlocks(me.mcUsername), db.getChatMutes(me.mcUsername)]);
      return NextResponse.json({
        success: true,
        blocked: blocks.filter(b => lower(b.blocker) === lower(me.mcUsername)).map(b => ({ mcUsername: b.blocked })),
        muted: muted.map(n => ({ mcUsername: n }))
      });
    }

    if (searchParams.get('overview')) {
      const [chats, members, blocks, friendships, mutes] = await Promise.all([
        db.getRecentChats(me.mcUsername),
        getChatMembers(me),
        db.getChatBlocks(me.mcUsername),
        db.getFriendships(me.email).catch(() => []),
        db.getChatMutes(me.mcUsername).catch(() => [])
      ]);
      const mutedNames = new Set(mutes.map(lower));
      const friendNames = new Set((friendships || [])
        .filter(f => f.status === 'accepted')
        .map(f => lower(lower(f.requesterEmail) === lower(me.email) ? f.addresseeUsername : f.requesterUsername)));
      const people = members.map(({ email, ...m }) => ({
        ...m,
        isFriend: friendNames.has(lower(m.mcUsername)),
        blockedByMe: blockState(blocks, me.mcUsername, m.mcUsername).byMe
      }));
      return NextResponse.json({ success: true, me: me.mcUsername, conversations: (chats || []).map(c => ({ ...c, muted: mutedNames.has(lower(c.contact)) })), people });
    }

    const contact = searchParams.get('contact');
    if (contact) {
      global.__chat_active_viewers.set(viewKey(me.mcUsername, contact), Date.now());
      const before = parseDate(searchParams.get('before'));
      const [{ messages, hasMore }, blocks] = await Promise.all([
        db.getMessages(me.mcUsername, contact, { before }),
        db.getChatBlocks(me.mcUsername)
      ]);
      // The page asks for markRead while the tab is visible, so messages
      // you're actually looking at stop counting as unread straight away.
      // Opening a conversation (`initial=1`) always clears EVERYTHING from this
      // contact, not just what falls inside the newest-100 window above: unread
      // rows older than that window (legacy messages from before read-tracking
      // existed) would otherwise keep a badge on the sidebar forever even though
      // there is nothing new to read. Later polls only write when something new
      // actually arrived.
      if (searchParams.get('markRead') && (searchParams.get('initial') || messages.some(m => !m.read && lower(m.sender) === lower(contact)))) {
        await db.markChatAsRead(contact, me.mcUsername);
      }
      const mutedNow = (await db.getChatMutes(me.mcUsername).catch(() => [])).some(n => lower(n) === lower(contact));
      return NextResponse.json({ success: true, messages, hasMore, block: blockState(blocks, me.mcUsername, contact), muted: mutedNow });
    }

    return NextResponse.json({ error: 'Invalid request parameters' }, { status: 400 });
  } catch (err) {
    console.error('Chat GET error:', err);
    return NextResponse.json({ error: 'Failed to retrieve chat data' }, { status: 500 });
  }
}

async function saveChatImage(imageFile, sender) {
  const ext = IMAGE_TYPES[imageFile.type];
  if (!ext) return { error: 'Only PNG, JPG, GIF or WebP images are allowed.' };
  if (imageFile.size > MAX_IMAGE_BYTES) return { error: 'Image must be smaller than 5MB.' };
  await fs.promises.mkdir(CHAT_UPLOAD_DIR, { recursive: true });
  const safeSender = lower(sender).replace(/[^a-z0-9_]/g, '') || 'user';
  const filename = `${safeSender}-${Date.now()}-${Math.floor(Math.random() * 1e6)}.${ext}`;
  await fs.promises.writeFile(path.join(CHAT_UPLOAD_DIR, filename), Buffer.from(await imageFile.arrayBuffer()));
  // Relative URL: works on any host/protocol (the old absolute
  // http://host/... URL broke as mixed content behind the HTTPS proxy).
  return { url: `/uploads/chat/${filename}` };
}

async function removeChatImage(imageUrl) {
  try {
    const pathname = new URL(String(imageUrl || ''), 'https://iekcorbar.com').pathname;
    if (!pathname.startsWith('/uploads/chat/')) return;
    await fs.promises.unlink(path.join(CHAT_UPLOAD_DIR, path.basename(pathname)));
  } catch { /* already gone */ }
}

export async function POST(request) {
  try {
    const auth = await requireChatUser(request);
    if (auth.error) return auth.error;
    const me = auth.user;

    let body;
    let imageFile = null;
    if ((request.headers.get('content-type') || '').includes('multipart/form-data')) {
      const formData = await request.formData();
      body = { action: formData.get('action') || 'send', receiver: formData.get('receiver'), message: formData.get('message') || '' };
      imageFile = formData.get('image');
      if (!imageFile || typeof imageFile === 'string' || !imageFile.size) imageFile = null;
    } else {
      body = await request.json();
    }
    const { action } = body;

    if (action === 'send') {
      const members = await getChatMembers(me);
      const receiver = members.find(m => lower(m.mcUsername) === lower(body.receiver));
      if (!receiver) return NextResponse.json({ error: 'That member could not be found.' }, { status: 404 });

      const block = blockState(await db.getChatBlocks(me.mcUsername), me.mcUsername, receiver.mcUsername);
      if (block.byMe) return NextResponse.json({ error: 'You blocked this member. Unblock them to send a message.' }, { status: 403 });
      if (block.byThem) return NextResponse.json({ error: 'This member is not accepting messages from you.' }, { status: 403 });

      const text = String(body.message || '').trim();
      if (!text && !imageFile) return NextResponse.json({ error: 'Type a message or add an image.' }, { status: 400 });
      if (text.length > MAX_MESSAGE_LENGTH) return NextResponse.json({ error: `Messages can be at most ${MAX_MESSAGE_LENGTH} characters.` }, { status: 400 });

      let imageUrl = null;
      if (imageFile) {
        const saved = await saveChatImage(imageFile, me.mcUsername);
        if (saved.error) return NextResponse.json({ error: saved.error }, { status: 400 });
        imageUrl = saved.url;
      }

      const newMsg = await db.sendMessage(me.mcUsername, receiver.mcUsername, text, imageUrl);

      // If they are reading this conversation right now, the message is read the
      // moment it arrives — otherwise it would count as unread (sidebar + nav
      // badge) until their next poll, which looks like a "new messages" bug.
      if (isActivelyViewing(receiver.mcUsername, me.mcUsername)) {
        await db.markChatAsRead(me.mcUsername, receiver.mcUsername).catch(() => {});
      }

      // Fire-and-forget achievements — never block a message that already sent.
      db.awardAchievement(me.mcUsername, ACHIEVEMENT_SLUGS.FIRST_CHAT_MESSAGE).catch(() => {});
      if (imageUrl) db.awardAchievement(me.mcUsername, ACHIEVEMENT_SLUGS.FIRST_CHAT_IMAGE).catch(() => {});

      // Bell + push, unless they're already reading this conversation.
      const mutedByReceiver = (await db.getChatMutes(receiver.mcUsername).catch(() => [])).some(n => lower(n) === lower(me.mcUsername));
      if (!mutedByReceiver && !isActivelyViewing(receiver.mcUsername, me.mcUsername)) {
        try {
          await db.createUserNotification({
            recipientEmail: receiver.email,
            type: 'chat',
            message: `${me.mcUsername} sent you a message.`,
            link: `/chat?user=${encodeURIComponent(me.mcUsername)}`,
            pushTitle: String(me.mcUsername),
            pushBody: text ? text.slice(0, 180) : '📷 Image',
            tag: `chat-${lower(me.mcUsername)}`
          });
        } catch (notifErr) {
          console.error('Failed to create chat notification:', notifErr);
        }
      }

      return NextResponse.json({ success: true, message: newMsg });
    }

    if (action === 'read') {
      if (!body.contact) return NextResponse.json({ error: 'Missing contact' }, { status: 400 });
      await db.markChatAsRead(body.contact, me.mcUsername);
      return NextResponse.json({ success: true });
    }

    if (action === 'delete') {
      const msg = await db.getChatMessage(body.id);
      if (!msg) return NextResponse.json({ error: 'Message not found.' }, { status: 404 });
      if (lower(msg.sender) !== lower(me.mcUsername)) return NextResponse.json({ error: 'You can only delete your own messages.' }, { status: 403 });
      if (!(await db.deleteChatMessage(msg.id))) return NextResponse.json({ error: 'Could not delete the message.' }, { status: 500 });
      if (msg.imageUrl) await removeChatImage(msg.imageUrl);
      return NextResponse.json({ success: true });
    }

    if (action === 'mute' || action === 'unmute') {
      const contact = String(body.username || '').trim().slice(0, 100);
      if (!contact) return NextResponse.json({ error: 'Missing username' }, { status: 400 });
      const ok = await db.setChatMute(me.mcUsername, contact, action === 'mute');
      if (!ok) return NextResponse.json({ error: 'Could not save that right now — please try again.' }, { status: 500 });
      return NextResponse.json({ success: true });
    }

    if (action === 'block' || action === 'unblock') {
      const members = await getChatMembers(me);
      const target = members.find(m => lower(m.mcUsername) === lower(body.username));
      if (!target) return NextResponse.json({ error: 'That member could not be found.' }, { status: 404 });
      if (action === 'block' && target.roleIsStaff) {
        return NextResponse.json({ error: 'Staff members cannot be blocked. Use Report if something is wrong.' }, { status: 400 });
      }
      const ok = await db.setChatBlock(me.mcUsername, target.mcUsername, action === 'block');
      if (!ok) return NextResponse.json({ error: 'Could not save that right now — please try again.' }, { status: 500 });
      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: 'Invalid action' }, { status: 400 });
  } catch (err) {
    console.error('Chat endpoint error:', err);
    return NextResponse.json({ error: 'Failed to process chat action' }, { status: 500 });
  }
}
