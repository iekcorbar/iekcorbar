import crypto from 'crypto';
import { db } from '@/lib/db';
import { getSessionUser } from '@/lib/session';

// First-party, cookie-free usage analytics (Staff Panel → Analytics).
// Requested 2026-09-27: insight into website + app usage. Privacy choices:
//  - no cookies, no third-party tracker; everything stays in our own DB;
//  - anonymous visitors are only countable per day: visitor_hash is a hash of
//    IP + user agent + a salt that changes daily, so it can't be reversed to
//    an IP or linked across days;
//  - logged-in players are linked to their account (shown per player);
//  - only the path is stored, never the query string (it can hold tokens);
//  - bots/crawlers/link previews are ignored;
//  - rows older than 90 days are deleted automatically (db.js cleanup).
// Documented on /privacy — keep that page in sync if this changes.

const BOT_RE = /bot|crawl|spider|slurp|facebookexternalhit|embedly|preview|headless|lighthouse|pingdom|uptime|curl|wget|python-requests|axios|node-fetch/i;

export function parseUserAgent(ua = '', { standalone = false } = {}) {
  const appMatch = ua.match(/IekcorbarApp\/([\w.-]+)/);
  const isIos = /iPhone|iPad|iPod/.test(ua);
  const isAndroid = /Android/.test(ua);
  const isMobile = isIos || isAndroid || /Mobile/.test(ua);

  let platform;
  if (appMatch) platform = 'android-app';
  else if (standalone && isIos) platform = 'iphone-homescreen';
  else if (standalone) platform = 'homescreen-other';
  else if (isMobile) platform = 'mobile-browser';
  else platform = 'desktop';

  const browser = appMatch ? 'Iekcorbar app'
    : /Edg\//.test(ua) ? 'Edge'
    : /OPR\/|Opera/.test(ua) ? 'Opera'
    : /SamsungBrowser/.test(ua) ? 'Samsung Internet'
    : /Firefox\/|FxiOS/.test(ua) ? 'Firefox'
    : /CriOS|Chrome\//.test(ua) ? 'Chrome'
    : /Safari\//.test(ua) ? 'Safari'
    : 'Other';
  const os = isIos ? 'iOS' : isAndroid ? 'Android' : /Windows/.test(ua) ? 'Windows'
    : /Mac OS X|Macintosh/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'Other';

  return { platform, browser, os, appVersion: appMatch ? appMatch[1].slice(0, 20) : null };
}

function clientIp(request) {
  const fwd = request.headers.get('x-forwarded-for');
  return (fwd ? fwd.split(',')[0] : request.headers.get('x-real-ip')) || 'unknown';
}

function dailyVisitorHash(request, ua) {
  const day = new Date().toISOString().slice(0, 10);
  const salt = process.env.ANALYTICS_SALT || process.env.CRON_SECRET || 'iekcorbar';
  return crypto.createHash('sha256').update(`${day}|${salt}|${clientIp(request)}|${ua}`).digest('hex');
}

function referrerHost(ref, request) {
  try {
    if (!ref) return null;
    const host = new URL(ref).hostname.replace(/^www\./, '');
    const own = (request.headers.get('host') || 'iekcorbar.com').split(':')[0].replace(/^www\./, '');
    return host && host !== own ? host.slice(0, 255) : null;
  } catch {
    return null;
  }
}

// type: 'pageview' | 'apk_download'
export async function recordAnalyticsEvent(request, { type = 'pageview', path = null, referrer = null, standalone = false } = {}) {
  try {
    const ua = request.headers.get('user-agent') || '';
    if (!ua || BOT_RE.test(ua)) return;
    const { platform, browser, os, appVersion } = parseUserAgent(ua, { standalone });
    const user = await getSessionUser(request).catch(() => null);
    const cleanPath = path ? String(path).split('?')[0].split('#')[0].slice(0, 255) : null;
    await db.insertAnalyticsEvent({
      type,
      path: cleanPath,
      visitorHash: dailyVisitorHash(request, ua),
      userEmail: user?.email || null,
      platform,
      appVersion,
      browser,
      os,
      referrerHost: referrerHost(referrer, request)
    });
  } catch (err) {
    console.error('[Analytics] Failed to record event:', err.message);
  }
}
