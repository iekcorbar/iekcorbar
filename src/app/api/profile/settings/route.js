import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';
import { cleanBio, cleanBanner, cleanHidden, cleanBannerPos, safeBannerImage } from '@/lib/profile-theme';

// The logged-in player's own profile personalisation (bio, banner theme,
// which public sections are hidden). Session-verified: you can only ever read
// or change your own — there is no email/username parameter to tamper with.
export async function GET(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });

  const settings = await db.getProfileSettings(user.email);
  return NextResponse.json({
    bio: settings.bio,
    banner: cleanBanner(settings.banner),
    hidden: cleanHidden(settings.hidden),
    bannerImage: safeBannerImage(settings.bannerImage),
    bannerPos: cleanBannerPos(settings.bannerPos ?? 50)
  });
}

export async function PUT(request) {
  const user = await getSessionUser(request);
  if (!user) return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });

  try {
    const body = await request.json();
    const settings = { bio: cleanBio(body.bio), banner: cleanBanner(body.banner), hidden: cleanHidden(body.hidden) };
    if (body.bannerPos !== undefined) settings.bannerPos = cleanBannerPos(body.bannerPos);
    const saved = await db.saveProfileSettings(user.email, settings);
    if (!saved) {
      return NextResponse.json({ error: 'Could not save your profile (database unreachable). Please try again shortly.' }, { status: 500 });
    }
    return NextResponse.json({ success: true, ...settings });
  } catch {
    return NextResponse.json({ error: 'Failed to save profile settings' }, { status: 500 });
  }
}
