import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

// The website-only "Gears" currency (users.website_currency) — see
// schema.sql's comment on that column and api/shop/purchase's 'website'
// branch. Top-level (not nested under /api/shop) since it's earned via
// achievements and may grow other sources later, not just spent in the
// shop. Read-only from here: the only ways a balance actually changes are
// db.awardAchievement (earn) and db.spendWebsiteCurrency (spend, via a
// 'website'-currency shop purchase) — never a direct client-set amount.
export async function GET(request) {
  try {
    const sessionUser = await getSessionUser(request);
    if (!sessionUser) return NextResponse.json({ currency: 0 });
    const currency = await db.getUserWebsiteCurrency(sessionUser.email);
    return NextResponse.json({ currency });
  } catch (err) {
    console.error("Currency GET error:", err);
    return NextResponse.json({ error: "Failed to load currency balance" }, { status: 500 });
  }
}
