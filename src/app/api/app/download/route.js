import { NextResponse } from 'next/server';
import { recordAnalyticsEvent } from '@/lib/analytics';

// "Download for Android" button on /app goes through here so downloads are
// counted (Staff Panel → Analytics), then redirects to the APK itself, which
// Apache serves straight from public/downloads/.
export async function GET(request) {
  await recordAnalyticsEvent(request, { type: 'apk_download', path: '/downloads/iekcorbar.apk' });
  // Relative Location: behind Apache/Passenger request.url can be an internal host.
  return new NextResponse(null, { status: 302, headers: { Location: '/downloads/iekcorbar.apk', 'Cache-Control': 'no-store' } });
}
