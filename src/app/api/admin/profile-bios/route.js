import { NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { requirePermission } from '@/lib/permissions';
import { logAudit } from '@/lib/audit';
import { deleteBannerFile } from '@/lib/banner-files';
import { safeBannerImage } from '@/lib/profile-theme';

export const dynamic = 'force-dynamic';

// Staff review of player profile bios (Staff Panel > Moderation > Crew &
// Sanctions). Gated by the 'moderation' permission like kicks/bans: reading
// every bio and clearing one are moderation actions.

// List every bio currently set. The account email is never sent to the browser.
export async function GET(request) {
  const auth = await requirePermission(request, 'moderation');
  if (auth.error) return auth.error;

  const [bios, banners] = await Promise.all([db.getProfileBios(), db.getProfileBanners()]);
  return NextResponse.json({
    bios: bios.map(({ email, ...rest }) => rest),
    banners: banners.map(({ email, ...rest }) => ({ ...rest, bannerImage: safeBannerImage(rest.bannerImage) })).filter(b => b.bannerImage)
  });
}

// Clear one player's bio (?username=<in-game name>&reason=<optional>). The
// removed text goes into the audit log, and the player gets a bell
// notification so a bio never vanishes without explanation.
export async function DELETE(request) {
  const auth = await requirePermission(request, 'moderation');
  if (auth.error) return auth.error;

  const { searchParams } = new URL(request.url);
  const username = searchParams.get('username');
  const reason = (searchParams.get('reason') || '').trim().slice(0, 200);
  if (!username) return NextResponse.json({ error: 'Missing username' }, { status: 400 });

  // ?what=banner removes the player's uploaded banner image instead of the bio.
  if (searchParams.get('what') === 'banner') {
    const removed = await db.clearProfileBanner(username);
    if (!removed.success) {
      return NextResponse.json(
        { error: removed.notFound ? 'That player has no custom banner.' : 'Could not remove the banner (database unreachable). Please try again shortly.' },
        { status: removed.notFound ? 404 : 500 }
      );
    }
    await deleteBannerFile(removed.url);
    await logAudit(auth.user, {
      area: 'members',
      action: 'remove banner',
      target: username,
      detail: reason ? `Reason: ${reason}` : null
    });
    await db.createUserNotification({
      recipientEmail: removed.email,
      type: 'moderation',
      message: `A staff member removed your custom profile banner because it did not follow the server rules${reason ? ` (${reason})` : ''}. You can upload a different one from your profile.`,
      link: '/profile'
    });
    return NextResponse.json({ success: true });
  }

  const result = await db.clearProfileBio(username);
  if (!result.success) {
    return NextResponse.json(
      { error: result.notFound ? 'That player has no bio to clear.' : 'Could not clear the bio (database unreachable). Please try again shortly.' },
      { status: result.notFound ? 404 : 500 }
    );
  }

  await logAudit(auth.user, {
    area: 'members',
    action: 'clear bio',
    target: username,
    detail: `${reason ? `Reason: ${reason}. ` : ''}Removed text: "${String(result.bio).slice(0, 300)}"`
  });

  await db.createUserNotification({
    recipientEmail: result.email,
    type: 'moderation',
    message: `A staff member removed your profile bio because it did not follow the server rules${reason ? ` (${reason})` : ''}. You can write a new one from your profile.`,
    link: '/profile'
  });

  return NextResponse.json({ success: true });
}
