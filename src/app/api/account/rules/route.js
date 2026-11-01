import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { currentRulesVersion } from '@/lib/rules-version';

// Rules acceptance for the logged-in player (onboarding, see api/auth/register and /welcome).
//  GET            -> { accepted, at, version, current }
//  POST { accept:true } -> stores acceptance of the current rules version
export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  const row = await db.getRulesAcceptance(user.email);
  const current = await currentRulesVersion();
  return NextResponse.json({ accepted: !!row?.at, at: row?.at || null, version: row?.version || null, current });
}

export async function POST(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  const body = await request.json().catch(() => ({}));
  if (body.accept !== true) return NextResponse.json({ error: "You need to agree to the rules to continue." }, { status: 400 });
  const ok = await db.acceptRules(user.email, await currentRulesVersion());
  if (!ok) return NextResponse.json({ error: "Could not save your answer. Please try again." }, { status: 500 });
  return NextResponse.json({ success: true });
}
