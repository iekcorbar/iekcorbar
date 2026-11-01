import { NextResponse } from 'next/server';
import { db } from '@/lib/db';

// The Sakagura garden season, written into the settings table by the SiteSync plugin
// (SeasonSync, key sakagura_season). Public and read-only; null when the plugin has not
// written it yet, so the calendar simply hides the live line.
export async function GET() {
  try {
    const settings = await db.getSettings();
    let season = null;
    if (settings.sakagura_season) {
      try {
        const s = JSON.parse(settings.sakagura_season);
        if (['SPRING', 'SUMMER', 'AUTUMN', 'WINTER'].includes(s.season) && Number.isFinite(s.next)) season = s;
      } catch { /* malformed value - treat as unknown */ }
    }
    return NextResponse.json({ season });
  } catch {
    return NextResponse.json({ season: null });
  }
}
