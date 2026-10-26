// Predefined bug-report categories — shown in the Profile page's submission
// form and used as filters on the public /known-bugs page. 'other' is
// always selectable too; when picked, the reporter's own free-text label is
// stored in bug_reports.custom_category and shown instead everywhere below.
// Plain data only, safe to import from client components — see db.js/the
// bug-reports API route for the server-side half.
//
// Deliberately split into website features and actual in-game/server
// topics — the original list was website-only, which meant most real
// Minecraft-server bugs (economy, land claims, PvP, general gameplay) had
// nowhere to go but "Other". That mattered beyond just mislabeling: the
// duplicate-check panel and "I have this too" only match within the same
// *predefined* category (see below and getPublicBugReports) — "Other"
// is deliberately excluded from that matching since its free-text label
// varies per reporter, so anything filed as Other could never actually
// surface as a known duplicate to confirm. Real in-game categories fix
// that for the topics they cover.
export const BUG_CATEGORIES = [
  // Website features
  { value: 'account_login', label: 'Account & Login' },
  { value: 'website_ui', label: 'Website / UI' },
  { value: 'chat_friends', label: 'Chat & Friends' },
  { value: 'wiki', label: 'Wiki' },
  { value: 'shop', label: 'Shop' },
  { value: 'events_voting', label: 'Events & Voting' },
  { value: 'mobile_app', label: 'Mobile App' },
  // In-game / Minecraft server
  { value: 'ingame_general', label: 'In-Game / Server' },
  { value: 'economy_jobs', label: 'Economy & Jobs' },
  { value: 'land_claims', label: 'Land Claims' },
  { value: 'pvp_combat', label: 'PvP & Combat' },
  { value: 'plugins_commands', label: 'Plugins & Commands' },
  { value: 'other', label: 'Other' },
];

export const BUG_CATEGORY_VALUES = BUG_CATEGORIES.map(c => c.value);

// Resolves what to actually display for a report's category — its
// predefined label, or the reporter's own text when they picked "Other".
export function bugCategoryLabel(category, customCategory) {
  if (category === 'other' && customCategory && customCategory.trim()) return customCategory.trim();
  return BUG_CATEGORIES.find(c => c.value === category)?.label || category;
}

export const BUG_STATUSES = ['open', 'fixed', 'wont_fix', 'duplicate'];

export const BUG_STATUS_LABELS = {
  open: 'Open',
  fixed: 'Fixed',
  wont_fix: "Won't Fix",
  duplicate: 'Duplicate',
};

// Resolved reports (fixed / wont_fix / duplicate) drop out of the default
// staff and public views this many days after resolved_at — computed
// lazily off that timestamp wherever a report list is read (db.js), not a
// scheduled job. Mirrors the brewing minigame's ready_at clock-comparison
// pattern elsewhere in this codebase.
export const BUG_ARCHIVE_DAYS = 14;

export function isBugReportArchived(report) {
  if (!report.resolvedAt) return false;
  if (report.status === 'open') return false;
  const ageMs = Date.now() - new Date(report.resolvedAt).getTime();
  return ageMs > BUG_ARCHIVE_DAYS * 24 * 60 * 60 * 1000;
}
