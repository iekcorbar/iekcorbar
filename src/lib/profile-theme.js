// Shared (client + server) definitions for profile personalisation: which
// banner themes exist, which public sections a player may hide, and the
// sanitising helpers the API uses so only these known values are ever stored.
// Plain data/functions only, safe to import from a client component.

export const BIO_MAX_LENGTH = 280;

// Custom banner images (player uploads; AI-generated art and animated GIF/WebP
// are allowed). Enforced server-side in /api/profile/banner from the file's own
// bytes — these numbers are mirrored client-side only to fail fast.
export const BANNER_MAX_STILL_BYTES = 2 * 1024 * 1024;     // still PNG / JPEG / WebP
export const BANNER_MAX_ANIMATED_BYTES = 4 * 1024 * 1024;  // animated GIF / WebP / APNG
export const BANNER_MIN_WIDTH = 600;
export const BANNER_MIN_HEIGHT = 150;
export const BANNER_MAX_DIMENSION = 4096;
export const BANNER_IMAGE_URL = /^\/uploads\/banners\/[A-Za-z0-9._-]+$/;

// Vertical focus (0 = top, 100 = bottom) for how a tall image is cropped into the banner strip.
export function cleanBannerPos(value) {
  const n = Math.round(Number(value));
  return Number.isFinite(n) ? Math.min(100, Math.max(0, n)) : 50;
}

// Only ever render an image URL we produced ourselves.
export function safeBannerImage(url) {
  return typeof url === 'string' && BANNER_IMAGE_URL.test(url) ? url : null;
}

// Preset banners only (no uploads): nothing to moderate or host, and every
// theme is guaranteed to look right with the profile's text colours.
export const PROFILE_BANNERS = [
  { key: 'gears', label: 'Brass & Gears', css: 'linear-gradient(135deg, #2a1608 0%, #7a4a1e 55%, #3a1f0c 100%)', accent: '#e0b25a' },
  { key: 'sakura', label: 'Sakura', css: 'linear-gradient(135deg, #1b0a12 0%, #8a2f55 55%, #3a1020 100%)', accent: '#f4a6c4' },
  { key: 'night', label: 'Midnight', css: 'linear-gradient(135deg, #0b1020 0%, #2c3a7a 55%, #141a38 100%)', accent: '#9db4ff' },
  { key: 'forest', label: 'Mossy Forest', css: 'linear-gradient(135deg, #0a1a10 0%, #2f6a3c 55%, #12281a 100%)', accent: '#8fe0a0' },
  { key: 'ember', label: 'Ember', css: 'linear-gradient(135deg, #1a0a06 0%, #a8381a 55%, #3a140a 100%)', accent: '#ffb27a' },
  { key: 'steel', label: 'Steel', css: 'linear-gradient(135deg, #101418 0%, #46525e 55%, #1c2229 100%)', accent: '#c4d2de' }
];
export const DEFAULT_BANNER = 'gears';

// Public sections a player can hide from visitors. Their own view always
// shows everything. Logbook is not listed: it is already friends-only.
export const HIDEABLE_SECTIONS = [
  { key: 'combat', label: 'Combat stats' },
  { key: 'jobs', label: 'Jobs' },
  { key: 'economy', label: 'Balance & economy' },
  { key: 'claims', label: 'Claims & mining' },
  { key: 'achievements', label: 'Achievements' }
];

const BANNER_KEYS = new Set(PROFILE_BANNERS.map(b => b.key));
const SECTION_KEYS = new Set(HIDEABLE_SECTIONS.map(s => s.key));

export function getBanner(key) {
  return PROFILE_BANNERS.find(b => b.key === key) || PROFILE_BANNERS.find(b => b.key === DEFAULT_BANNER);
}

export function cleanBio(value) {
  return String(value ?? '').replace(/\r\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim().slice(0, BIO_MAX_LENGTH);
}

export function cleanBanner(value) {
  return BANNER_KEYS.has(value) ? value : DEFAULT_BANNER;
}

// Accepts an array or the stored comma-separated string.
export function cleanHidden(value) {
  const list = Array.isArray(value) ? value : String(value || '').split(',');
  return [...new Set(list.map(s => String(s).trim()).filter(s => SECTION_KEYS.has(s)))];
}
