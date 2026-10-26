import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';

import { logAudit } from '@/lib/audit';
export async function GET() {
  try {
    const polls = await db.getPolls();
    return NextResponse.json({ polls });
  } catch (err) {
    return NextResponse.json({ error: "Failed to retrieve polls" }, { status: 500 });
  }
}

export async function POST(request) {
  try {
    const body = await request.json();
    const { action, pollId, optionIndex, email, id, question, options } = body;

    // Handle Vote action — identified by email only (no Minecraft IGN
    // required). Used to also fold the request's IP into the same
    // "voters" list to catch throwaway-account abuse, but that made IP the
    // sole gate: since the check was an OR across email/IP, a second real,
    // separately-registered account voting from the same network (e.g. a
    // household sharing one router) got silently blocked as "already
    // voted" the moment anyone else on that IP had voted — found 2026-09-06
    // after real households hit it on both polls and the ingredient hunt.
    // Email-only trades away IP-based multi-account throttling for correct
    // per-account voting; a real anti-abuse pass should track IP separately
    // from the block decision (e.g. flag-for-review) rather than reusing it
    // as a hard gate.
    if (action === 'vote') {
      if (!pollId || optionIndex === undefined || !email) {
        return NextResponse.json({ error: "Missing vote details" }, { status: 400 });
      }
      const result = await db.voteInPoll(pollId, optionIndex, [email]);
      if (!result.success) {
        return NextResponse.json({ error: "Could not record your vote (database unreachable, or the poll no longer exists). Please try again shortly." }, { status: 500 });
      }
      if (result.alreadyVoted) {
        return NextResponse.json({ error: "You've already voted in this poll.", polls: await db.getPolls() }, { status: 409 });
      }
      return NextResponse.json({ success: true, polls: await db.getPolls() });
    }

    // Handle Staff CRUD
    const auth = await requirePermission(request, 'polls');
    if (auth.error) return auth.error;

    if (!question || !options || !Array.isArray(options)) {
      return NextResponse.json({ error: "Missing required fields or invalid options format" }, { status: 400 });
    }

    const poll = {
      id: id || undefined,
      question,
      options
    };

    await db.savePoll(poll);
    await logAudit(auth.user, { area: 'polls', action: id ? 'update' : 'create', target: question });
    return NextResponse.json({ success: true, polls: await db.getPolls() });
  } catch (err) {
    console.error(err);
    return NextResponse.json({ error: "Failed to process request" }, { status: 500 });
  }
}

export async function DELETE(request) {
  try {
    const auth = await requirePermission(request, 'polls');
    if (auth.error) return auth.error;

    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');

    if (!id) {
      return NextResponse.json({ error: "Missing poll ID" }, { status: 400 });
    }

    await db.deletePoll(id);
    await logAudit(auth.user, { area: 'polls', action: 'delete', target: `poll ${id}` });
    return NextResponse.json({ success: true, polls: await db.getPolls() });
  } catch (err) {
    return NextResponse.json({ error: "Failed to delete poll" }, { status: 500 });
  }
}
