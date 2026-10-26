import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

// A player's own News & Upcoming topic subscriptions — real session-verified
// (see CLAUDE.md's Auth model section), unlike some older routes that trust
// a client-sent email/role. There's no `email` parameter to spoof here: it
// always comes from the session, never the request body/query.

export async function GET(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

    const topics = await db.getFollowedNewsTopics(user.email);
    return NextResponse.json({ topics });
  } catch (err) {
    return NextResponse.json({ error: "Failed to load followed topics" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

    const { topic } = await request.json();
    if (!topic) return NextResponse.json({ error: "Missing topic" }, { status: 400 });

    await db.followNewsTopic(user.email, topic);
    return NextResponse.json({ success: true, topics: await db.getFollowedNewsTopics(user.email) });
  } catch (err) {
    return NextResponse.json({ error: "Failed to follow topic" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const user = await getSessionUser(request);
    if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });

    const { searchParams } = new URL(request.url);
    const topic = searchParams.get('topic');
    if (!topic) return NextResponse.json({ error: "Missing topic" }, { status: 400 });

    await db.unfollowNewsTopic(user.email, topic);
    return NextResponse.json({ success: true, topics: await db.getFollowedNewsTopics(user.email) });
  } catch (err) {
    return NextResponse.json({ error: "Failed to unfollow topic" }, { status: 500 });
  }
}
