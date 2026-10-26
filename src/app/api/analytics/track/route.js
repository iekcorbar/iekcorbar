import { NextResponse } from 'next/server';
import { recordAnalyticsEvent } from '@/lib/analytics';

// Page-view beacon from providers.js (one call per client-side navigation).
// Public on purpose; bots are filtered and only the path is stored — see
// src/lib/analytics.js for the privacy rules.
export async function POST(request) {
  const body = await request.json().catch(() => ({}));
  const path = typeof body.path === 'string' && body.path.startsWith('/') ? body.path : null;
  if (path) {
    await recordAnalyticsEvent(request, {
      type: 'pageview',
      path,
      referrer: typeof body.referrer === 'string' ? body.referrer : null,
      standalone: body.standalone === true
    });
  }
  return new NextResponse(null, { status: 204 });
}
