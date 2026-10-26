import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser, requireRole } from '@/lib/session';

import { logAudit } from '@/lib/audit';
// Public by username — earned achievements are meant to be shown off (same
// bragging-rights stance as the public /members/[username] profile), no
// session required to view someone else's. Falls back to "my own" (via the
// real session cookie) when no username is given, since /achievements shows
// the logged-in viewer's own progress by default.
export async function GET(request) {
  try {
    const { searchParams } = new URL(request.url);
    let username = searchParams.get('username');

    if (!username) {
      const sessionUser = await getSessionUser(request);
      if (!sessionUser || !sessionUser.mcUsername) {
        return NextResponse.json({ earned: [] });
      }
      username = sessionUser.mcUsername;
    }

    const earned = await db.getPlayerAchievements(username);
    return NextResponse.json({ earned });
  } catch (err) {
    console.error("Achievements earned GET error:", err);
    return NextResponse.json({ error: "Failed to load earned achievements" }, { status: 500 });
  }
}

// Staff-only manual grant — the only way a "custom" achievement (one with no
// automatic trigger wired into the code, see src/lib/achievements.js) is
// ever earned, and also useful to backfill a triggered one for a player who
// missed it. Reuses db.awardAchievement, the exact same path every
// automatic trigger site calls, so this pays out currency and flips the
// same in-game-toast-pending flag identically.
export async function POST(request) {
  try {
    const auth = await requireRole(request, ['admin', 'moderator']);
    if (auth.error) return auth.error;

    const { achievementId, username } = await request.json();
    if (!achievementId || !username || !String(username).trim()) {
      return NextResponse.json({ error: "achievementId and username are required" }, { status: 400 });
    }

    const achievements = await db.getAchievements();
    const achievement = achievements.find(a => a.id === achievementId);
    if (!achievement) {
      return NextResponse.json({ error: "Unknown achievement" }, { status: 404 });
    }

    const result = await db.awardAchievement(String(username).trim(), achievement.slug);
    if (!result.success) {
      return NextResponse.json({ error: "Could not grant achievement — it may be inactive, or the database is unreachable." }, { status: 400 });
    }
    await logAudit(auth.user, { area: 'achievements', action: 'grant', target: `${String(username).trim()}: ${achievement.title}` });
    return NextResponse.json({ success: true, alreadyEarned: result.alreadyEarned });
  } catch (err) {
    console.error("Achievements manual grant error:", err);
    return NextResponse.json({ error: "Failed to grant achievement" }, { status: 500 });
  }
}
