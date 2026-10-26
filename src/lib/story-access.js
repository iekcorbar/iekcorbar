// Who may use the Story Room, and how much. Server-only (touches sessions
// and the database) — never import from a client component.
//
// Two ways in:
//   1. Staff: a real session whose role has the `story` permission (Role
//      Manager). Full access; publishing to the wiki also needs `wiki`.
//   2. Guests: people WITHOUT a website account, invited by staff through a
//      personal guest link (Story Room → Guest links). Opening the link
//      exchanges its secret for an httpOnly cookie scoped to /api/story, so
//      the guest can only ever reach Story Room endpoints — nothing else on
//      the site treats this cookie as a login. Each link has a level:
//        read    — read everything + download
//        comment — read + comments
//        edit    — read + comments + write chapters/lore/timeline
//      Guests can never delete, publish to the wiki, take over locks or
//      manage guest links; every change they make lands in the normal
//      version history under "<name> (guest)", so it's always reversible.
import crypto from 'crypto';
import { NextResponse } from 'next/server';
import { getSessionUser } from './session';
import { getRoleTabs } from './permissions';
import { db } from './db';
import { isAdminRole } from './staff-tabs';

export const STORY_GUEST_COOKIE = 'iekcorbar_story_guest';
export const STORY_GUEST_COOKIE_PATH = '/api/story';
export const STORY_GUEST_LEVELS = ['read', 'comment', 'edit'];
// Not a valid Role Manager role name (those are [a-z0-9_] only), so a guest
// can never collide with — or be granted the tabs of — a real role.
export const STORY_GUEST_ROLE = 'story-guest';

const LEVEL_RANK = { read: 0, comment: 1, edit: 2, manage: 3 };

export function hashGuestToken(token) {
  return crypto.createHash('sha256').update(String(token || '')).digest('hex');
}

export function generateGuestToken() {
  return crypto.randomBytes(32).toString('base64url');
}

export function cleanGuestName(raw) {
  return String(raw || '').replace(/[\[\]<>]/g, '').replace(/\s+/g, ' ').trim().slice(0, 60);
}

function accessFor(level) {
  const rank = LEVEL_RANK[level] ?? 0;
  return {
    level,
    canComment: rank >= LEVEL_RANK.comment,
    canEdit: rank >= LEVEL_RANK.edit,
    canManage: rank >= LEVEL_RANK.manage
  };
}

// Resolves the caller to { user, actor, isGuest, isAdmin, canPublish,
// level, canComment, canEdit, canManage, guestName? } or null.
export async function getStoryAccess(request) {
  const sessionUser = await getSessionUser(request);
  if (sessionUser) {
    const tabs = await getRoleTabs(sessionUser.role);
    if (tabs.includes('story')) {
      return {
        user: sessionUser,
        actor: sessionUser.mcUsername || sessionUser.email || 'Staff',
        isGuest: false,
        isAdmin: isAdminRole(sessionUser.role),
        canPublish: tabs.includes('wiki'),
        ...accessFor('manage')
      };
    }
  }

  const token = request.cookies.get(STORY_GUEST_COOKIE)?.value;
  if (!token) return null;
  const link = await db.getActiveStoryGuestLinkByHash(hashGuestToken(token));
  if (!link) return null;
  db.touchStoryGuestLink(link.id).catch(() => {});

  const actor = `${link.name} (guest)`;
  const level = STORY_GUEST_LEVELS.includes(link.access) ? link.access : 'read';
  return {
    // Shaped like a session user so existing code (storyActor(auth.user),
    // isAdminRole(auth.user.role), getRoleTabs(auth.user.role)) keeps
    // working unchanged and safely: the role has no tabs and isn't admin.
    user: { mcUsername: actor, email: null, role: STORY_GUEST_ROLE },
    actor,
    isGuest: true,
    isAdmin: false,
    canPublish: false,
    guestName: link.name,
    guestLinkId: link.id,
    expiresAt: link.expiresAt,
    ...accessFor(level)
  };
}

// Route guard, same { user } | { error } shape as requirePermission().
// `need` is 'read' | 'comment' | 'edit' | 'manage'.
export async function requireStoryAccess(request, need = 'read') {
  const access = await getStoryAccess(request);
  if (!access) {
    return { error: NextResponse.json({ error: 'Not authenticated — log in, or open your Story Room guest link again.' }, { status: 401 }) };
  }
  if ((LEVEL_RANK[access.level] ?? 0) < (LEVEL_RANK[need] ?? 0)) {
    const msg = need === 'manage'
      ? 'Only staff can do this.'
      : `Your guest link only allows ${access.level === 'read' ? 'reading' : 'reading and commenting'}.`;
    return { error: NextResponse.json({ error: msg }, { status: 403 }) };
  }
  return { user: access.user, access };
}

// What the client may know about its own access (drives which buttons the
// Story Room UI shows — the API enforces the same rules regardless).
export function publicStoryAccess(access) {
  if (!access) return null;
  return {
    actor: access.actor,
    isGuest: access.isGuest,
    isAdmin: access.isAdmin,
    level: access.level,
    canComment: access.canComment,
    canEdit: access.canEdit,
    canManage: access.canManage,
    canPublish: access.canPublish,
    guestName: access.guestName || null,
    expiresAt: access.expiresAt || null
  };
}

export function setStoryGuestCookie(response, token, expiresAt) {
  response.cookies.set(STORY_GUEST_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: STORY_GUEST_COOKIE_PATH,
    // Links without an expiry still get a cookie that lasts a year; opening
    // the link again simply renews it.
    expires: expiresAt ? new Date(expiresAt) : new Date(Date.now() + 365 * 24 * 3600 * 1000)
  });
  return response;
}

export function clearStoryGuestCookie(response) {
  response.cookies.set(STORY_GUEST_COOKIE, '', {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    path: STORY_GUEST_COOKIE_PATH,
    maxAge: 0
  });
  return response;
}
