import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// Matches VOTE_COOLDOWN_MS in vote/page.js — that one is client-side only
// (for the countdown display), this is the real, enforced check.
const VOTE_COOLDOWN_MS = 24 * 60 * 60 * 1000;

// 2026-08-13: this route used to ALSO dispatch our own separate RCON reward
// (give diamond 3, configurable via vote_reward_config) the instant a player
// clicked "Vote Now" here — on top of whatever VoteSpeed itself grants
// natively the moment it receives the external site's own real Votifier
// vote (see VoteSpeed's rewards.yml, `default` reward, chance:100, always
// fires). That meant every real external vote was being double-rewarded
// (once by VoteSpeed, once by us, unverified, just for clicking), and a
// player could get *our* reward by clicking without ever actually voting.
// Removed by explicit user decision — VoteSpeed's own reward is the single,
// real, verified source of truth now. This route's only remaining job is
// logging the click so the per-site cooldown countdown on /vote has
// something to read (informational only, no reward attached to it).
function userVotes(allVotes, username) {
  return allVotes.filter(v => String(v.username).toLowerCase() === String(username).toLowerCase());
}

// GET: a user's own vote-click history, for the cooldown display.
export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    const username = searchParams.get('username');
    if (!username) return NextResponse.json({ log: [] });

    const mine = userVotes(await db.getVoteLog(), username);
    return NextResponse.json({ log: mine });
  } catch (err) {
    console.error("Vote log GET error:", err);
    return NextResponse.json({ error: "Failed to load vote log" }, { status: 500 });
  }
}

// POST: record that a player clicked through to an external vote site.
// Purely informational (cooldown display) — grants no reward.
export async function POST(request) {
  try {
    const { username, siteId } = await request.json();
    if (!username || !siteId) {
      return NextResponse.json({ error: "username and siteId are required" }, { status: 400 });
    }

    const sites = await db.getVoteSites();
    const site = sites.find(s => String(s.id) === String(siteId));
    if (!site) {
      return NextResponse.json({ error: "Unknown vote site" }, { status: 404 });
    }

    const mine = userVotes(await db.getVoteLog(), username);
    const lastForSite = mine
      .filter(v => v.siteName === site.name)
      .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))[0];
    if (lastForSite && Date.now() - new Date(lastForSite.timestamp).getTime() < VOTE_COOLDOWN_MS) {
      return NextResponse.json({ error: "You've already voted on this site in the last 24 hours." }, { status: 429 });
    }

    const saved = await db.logVote({ username, siteName: site.name });
    if (!saved) {
      return NextResponse.json({ error: "Failed to record vote (database unreachable). Please try again shortly." }, { status: 500 });
    }

    const updated = userVotes(await db.getVoteLog(), username);
    return NextResponse.json({ success: true, log: updated });
  } catch (err) {
    console.error("Vote log POST error:", err);
    return NextResponse.json({ error: "Failed to record vote" }, { status: 500 });
  }
}
