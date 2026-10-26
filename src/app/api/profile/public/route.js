import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { cleanBanner, cleanHidden, cleanBannerPos, safeBannerImage } from '@/lib/profile-theme';

// What a visitor sees of someone's personalisation, looked up by in-game
// name. Public by design (same stance as /api/members and the public
// /members/[username] page) but never includes the email or anything else
// from the account row — only bio, banner theme, hidden sections, join date.
export async function GET(request) {
  const username = new URL(request.url).searchParams.get('username');
  if (!username) return NextResponse.json({ error: 'Missing username' }, { status: 400 });

  const p = await db.getPublicProfileSettings(username);
  return NextResponse.json({
    bio: p.bio || '',
    banner: cleanBanner(p.banner),
    hidden: cleanHidden(p.hidden),
    bannerImage: safeBannerImage(p.bannerImage),
    bannerPos: cleanBannerPos(p.bannerPos ?? 50),
    createdAt: p.createdAt || null
  });
}
