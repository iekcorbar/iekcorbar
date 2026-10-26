import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { ACHIEVEMENT_SLUGS } from '@/lib/achievements';

// Friendships are stored by email (this app's universal user key — see
// CLAUDE.md) but the public member listing deliberately never exposes raw
// emails to the client (see api/members/route.js). So this route only ever
// talks usernames with the client: requests are made by targetUsername, and
// every response below is shaped to carry usernames, never emails.
function shapeFriendships(rows, myEmail) {
  const friends = [];
  const incoming = [];
  const outgoing = [];
  for (const f of rows) {
    const iAmRequester = f.requesterEmail.toLowerCase() === myEmail.toLowerCase();
    const otherUsername = iAmRequester ? f.addresseeUsername : f.requesterUsername;
    const entry = { id: f.id, username: otherUsername, since: f.status === 'accepted' ? f.respondedAt || f.createdAt : f.createdAt };
    if (f.status === 'accepted') {
      friends.push(entry);
    } else if (iAmRequester) {
      outgoing.push(entry);
    } else {
      incoming.push(entry);
    }
  }
  return { friends, incoming, outgoing };
}

export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const email = searchParams.get('email');
    if (!email) return NextResponse.json({ error: "Missing email" }, { status: 400 });

    const sessionUser = await getSessionUser(request);
    if (!sessionUser || sessionUser.email.toLowerCase() !== email.toLowerCase()) {
      return NextResponse.json({ error: "Not authenticated for this account" }, { status: 401 });
    }

    const rows = await db.getFriendships(email);
    return NextResponse.json(shapeFriendships(rows, email));
  } catch (err) {
    console.error("Friends GET error:", err);
    return NextResponse.json({ error: "Failed to load friends" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const body = await request.json();
    const { action } = body;

    const sessionUser = await getSessionUser(request);
    if (!sessionUser) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }
    const myEmail = sessionUser.email;

    if (action === 'request') {
      const { targetUsername } = body;
      if (!targetUsername || !targetUsername.trim()) {
        return NextResponse.json({ error: "Missing target username" }, { status: 400 });
      }
      if (targetUsername.trim().toLowerCase() === (sessionUser.mcUsername || '').toLowerCase()) {
        return NextResponse.json({ error: "You can't friend yourself" }, { status: 400 });
      }

      const allUsers = await db.getUsers();
      const target = allUsers.find(u => u.mcUsername && u.mcUsername.toLowerCase() === targetUsername.trim().toLowerCase());
      if (!target) {
        return NextResponse.json({ error: "No player found with that username" }, { status: 404 });
      }

      const existingRows = await db.getFriendships(myEmail);
      const alreadyLinked = existingRows.some(f =>
        f.requesterEmail.toLowerCase() === target.email.toLowerCase() || f.addresseeEmail.toLowerCase() === target.email.toLowerCase()
      );
      if (alreadyLinked) {
        return NextResponse.json({ error: "You're already friends, or a request is already pending, with this player" }, { status: 400 });
      }

      const created = await db.sendFriendRequest(myEmail, target.email);
      if (!created) {
        return NextResponse.json({ error: "Could not send request (database unreachable). Please try again shortly." }, { status: 500 });
      }
      await db.createUserNotification({
        recipientEmail: target.email,
        type: 'friend_request',
        message: `${sessionUser.mcUsername} sent you a friend request.`,
        link: '/friends'
      });

      return NextResponse.json({ ...shapeFriendships(await db.getFriendships(myEmail), myEmail) });
    }

    if (action === 'accept' || action === 'decline' || action === 'cancel') {
      const { requestId } = body;
      if (!requestId) return NextResponse.json({ error: "Missing requestId" }, { status: 400 });

      const rows = await db.getFriendships(myEmail);
      const target = rows.find(f => f.id === requestId);
      if (!target || target.status !== 'pending') {
        return NextResponse.json({ error: "Request not found" }, { status: 404 });
      }

      const iAmAddressee = target.addresseeEmail.toLowerCase() === myEmail.toLowerCase();
      const iAmRequester = target.requesterEmail.toLowerCase() === myEmail.toLowerCase();

      if (action === 'accept') {
        if (!iAmAddressee) return NextResponse.json({ error: "Only the recipient can accept a request" }, { status: 403 });
        await db.respondToFriendRequest(requestId, true);
        await db.createUserNotification({
          recipientEmail: target.requesterEmail,
          type: 'friend_accepted',
          message: `${sessionUser.mcUsername} accepted your friend request.`,
          link: `/members/${sessionUser.mcUsername}`
        });
        // Both sides just became friends — award to whichever side actually
        // has an IGN linked (fire-and-forget, see src/lib/achievements.js).
        if (sessionUser.mcUsername) db.awardAchievement(sessionUser.mcUsername, ACHIEVEMENT_SLUGS.FIRST_FRIEND).catch(() => {});
        if (target.requesterUsername) db.awardAchievement(target.requesterUsername, ACHIEVEMENT_SLUGS.FIRST_FRIEND).catch(() => {});
      } else if (action === 'decline') {
        if (!iAmAddressee) return NextResponse.json({ error: "Only the recipient can decline a request" }, { status: 403 });
        await db.removeFriendship(requestId);
      } else {
        if (!iAmRequester) return NextResponse.json({ error: "Only the sender can cancel a request" }, { status: 403 });
        await db.removeFriendship(requestId);
      }

      return NextResponse.json({ ...shapeFriendships(await db.getFriendships(myEmail), myEmail) });
    }

    if (action === 'remove') {
      const { friendshipId } = body;
      if (!friendshipId) return NextResponse.json({ error: "Missing friendshipId" }, { status: 400 });

      const rows = await db.getFriendships(myEmail);
      const target = rows.find(f => f.id === friendshipId);
      if (!target) return NextResponse.json({ error: "Friendship not found" }, { status: 404 });

      const involvesMe = target.requesterEmail.toLowerCase() === myEmail.toLowerCase() || target.addresseeEmail.toLowerCase() === myEmail.toLowerCase();
      if (!involvesMe) return NextResponse.json({ error: "Not your friendship to remove" }, { status: 403 });

      await db.removeFriendship(friendshipId);
      return NextResponse.json({ ...shapeFriendships(await db.getFriendships(myEmail), myEmail) });
    }

    return NextResponse.json({ error: "Invalid action" }, { status: 400 });
  } catch (err) {
    console.error("Friends POST error:", err);
    return NextResponse.json({ error: "Failed to process friend action" }, { status: 500 });
  }
}
