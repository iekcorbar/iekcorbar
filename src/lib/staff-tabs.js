// Every tab in the Staff Panel (src/app/moderation/page.js), keyed exactly
// like that page's own activeTab/setActiveTab values. This is the single
// source of truth for both the admin-only Role Manager (which tabs an admin
// can grant a role) and the actual permission checks (see permissions.js) —
// add a tab here whenever a new one is added to the Staff Panel, or it can
// never be granted to anything but 'admin'.
//
// `label` here is deliberately the EXACT text shown on the tab's own button
// in the Staff Panel (moderation/page.js used to hardcode a second, slightly
// different label per button — e.g. Role Manager said "Moderation" while the
// real tab said "Crew & Sanctions" — which was a real source of confusion
// when an admin tried to figure out what a checkbox actually granted; the
// page now renders its tab buttons FROM this array instead of its own
// copy, so the two can't drift apart again). `description` is shown next to
// the checkbox in Role Manager so "what does granting this actually let
// someone do" has an answer right there instead of requiring guesswork.
// `group` clusters related tabs for both Role Manager's checkbox layout and
// the Staff Panel's own top-level tab bar.
//
// Settings (2026-09-13) used to be a single 'settings' permission gating 11
// unrelated tools at once (carousel, rules, tips, vote sites, project
// management, ...) with no way to grant just one of them, and no indication
// anywhere that "Settings" was a bundle at all. It's now split into one
// `settings_*` permission per tool (`parent: 'settings'`) — each is its own
// checkbox in Role Manager, under a "Settings Tools" group, and its own
// `requirePermission()` check in that tool's API route. The bare 'settings'
// entry stays as the umbrella top-level tab (what actually shows the
// "Settings" button and its sub-tab bar) but is no longer something Role
// Manager lets you toggle directly — src/lib/permissions.js's getRoleTabs()
// derives it automatically: having any settings_* permission implies
// 'settings' itself, and (for roles saved before this split) having the old
// bare 'settings' implies every settings_* permission, so no existing role
// silently lost access and no SQL migration was needed (`roles.tabs` is an
// unconstrained JSON column).
//
// Plain data only, safe to import from a client component (the achievements
// slug manifest follows the same pattern) — src/lib/permissions.js is the
// server-only half that touches sessions/the database.
export const STAFF_TABS = [
  // --- Community ---
  { key: 'moderation', label: 'Crew & Sanctions', description: 'Issue kicks, bans, and tempbans (dispatched live in-game via RCON), and add staff notes to player accounts.', group: 'Community' },
  { key: 'reports', label: 'Reports Inbox', description: 'Review and resolve anonymous player-submitted reports.', group: 'Community' },
  { key: 'bugs', label: 'Bug Reports', description: 'Triage player bug reports, message reporters back, mark fixed/duplicate/wont-fix.', group: 'Community' },

  // --- Content ---
  { key: 'events', label: 'Events Manager', description: 'Create and edit server events; see who has registered.', group: 'Content' },
  { key: 'wiki', label: 'Wiki Lore', description: 'Write and organize the public wiki (articles, categories, attachments).', group: 'Content' },
  { key: 'updates', label: 'Updates Manager', description: 'Post changelog / patch-note entries shown on the public Updates page.', group: 'Content' },
  { key: 'news', label: 'News & Upcoming', description: 'Post Minecraft news and upcoming server/website features, grouped by topic, shown on the public News & Upcoming page. Publishing pings whoever follows that topic.', group: 'Content' },
  { key: 'polls', label: 'Polls Manager', description: 'Create player polls and view their results.', group: 'Content' },
  { key: 'story', label: 'Story Room', description: 'Staff-only workspace for the iekcorbar storyline: chapters, lore library, timeline, comments and version history. Publishing a chapter to the public wiki additionally requires Wiki Lore.', group: 'Content' },

  // --- Administration ---
  { key: 'applications', label: 'Staff Applications', description: 'Review player applications for a Staff role, contact applicants, and approve or decline.', group: 'Administration' },
  { key: 'access', label: 'Access Control', description: 'View the live ban list and whitelist mirrored from the server (read-only).', group: 'Administration' },
  { key: 'analytics', label: 'Analytics', description: 'Website and app usage: visitors, page views, devices, app downloads/versions, and per-player activity and push status.', group: 'Administration' },
  { key: 'player_data', label: 'Player Data', description: 'Look up a player\'s EliteMobs combat record (kills, score, quests, dungeons, coins, weapon skill XP) and MMOCore progression (level, XP, class, points) straight from the plugin database. Nothing loads until you search a username.', group: 'Administration' },
  { key: 'console', label: 'RCON Console', description: 'Run free-text commands directly on the Minecraft server. No allowlist — this is full server access, every command is logged.', group: 'Administration' },
  { key: 'settings', label: 'Settings', description: "Umbrella tab for the site-configuration tools below — granted automatically once a role has at least one of them, not something you toggle directly.", group: 'Administration', isUmbrella: true },

  // --- Settings Tools (each its own permission, see the file comment above) ---
  { key: 'settings_general', label: 'General Settings', description: 'The Java version badge, map URL, and other misc. site-wide settings.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_carousel', label: 'Carousel Manager', description: 'Manage the homepage screenshot slideshow and its sets.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_guide', label: 'Starter Guide', description: 'Edit the new-player Starter Guide steps shown on the homepage.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_rules', label: 'Rules Manager', description: 'Edit the server rules text shown on /rules.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_tips', label: 'Tip of the Day', description: 'Manage the homepage daily-tip widget.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_vote', label: 'Vote Sites', description: 'Manage external vote-site links and their cooldowns.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_projects', label: 'Projects Manager', description: 'Manage community projects and player signups.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_content', label: 'Site Content', description: 'Edit role names and page labels shown across the site.', group: 'Settings Tools', parent: 'settings' },
  { key: 'settings_cleanup', label: 'Account Cleanup', description: 'Review and purge long-inactive player accounts.', group: 'Settings Tools', parent: 'settings' }

  // Deliberately NOT here, and NOT delegatable through Role Manager at all:
  // "Roles & Permissions" and "Newsletter". Both stay hardcoded admin-only
  // in moderation/page.js and their own API routes regardless of what a
  // role's tabs contain — Roles & Permissions so no role can ever grant
  // itself more power through it, Newsletter because a broadcast to every
  // player at once is effectively unrecallable (the user's own explicit
  // choice). If either is ever added here, that protection is gone.
];

// Every settings_* key, i.e. every individually-grantable Settings tool.
// Used by permissions.js's getRoleTabs() to derive 'settings' access, expand
// legacy roles, and by Role Manager's UI to render the "Settings Tools"
// checkbox group.
export const SETTINGS_SUBTAB_KEYS = STAFF_TABS.filter(t => t.parent === 'settings').map(t => t.key);

// The tab set every 'moderator' had before per-role permissions existed
// (console and settings were always admin-only, everything else was open to
// both staff roles) — used whenever the `roles` table has no row for
// 'moderator' yet, so a fresh install or an admin who's never opened Role
// Manager doesn't silently change any moderator's access.
export const DEFAULT_MODERATOR_TABS = ['moderation', 'reports', 'bugs', 'events', 'wiki', 'updates', 'news', 'polls', 'access'];

// 'admin' is never stored in the `roles` table and never editable through
// Role Manager — it always has every tab, hardcoded here, so an empty or
// misconfigured `roles` table can never lock every admin out at once.
export function isAdminRole(role) {
  return role === 'admin';
}

// One shared role hierarchy for every place roles are listed (Staff Panel
// filter buttons, role dropdown, member lists, public /members), so they all
// show the same order: admin → moderator → custom staff roles (most Staff
// Panel tabs first) → custom non-staff roles → player. `roles` is the Role
// Manager list ({ name, label, tabs }); a role missing from it (e.g.
// moderator before its row was ever saved) still gets its fixed place.
// Lower number = higher in the list.
export function roleRank(roleName, roles = []) {
  if (roleName === 'admin') return 0;
  if (roleName === 'moderator') return 1;
  if (roleName === 'player' || !roleName) return 1_000_000;
  if (roleName === 'pending') return 2_000_000;
  const row = roles.find(r => r.name === roleName);
  const tabCount = row && Array.isArray(row.tabs) ? row.tabs.length : 0;
  // Staff roles (≥1 tab) sort by tab count descending; non-staff after them.
  return tabCount > 0 ? 1000 - Math.min(tabCount, 999) : 100_000;
}

export function compareRoles(a, b, roles = [], labelOf = n => n) {
  const d = roleRank(a, roles) - roleRank(b, roles);
  return d !== 0 ? d : String(labelOf(a)).localeCompare(String(labelOf(b)));
}

// Badge colours per role, shared by every page that shows a role badge so a
// custom Role Manager role (e.g. "helper") is visually distinct instead of
// borrowing moderator's amber or player's copper.
//
// Custom roles pick their colour from ROLE_COLORS in Role Manager (stored as
// the key in `roles.color`). Full class strings are spelled out literally on
// purpose: Tailwind only generates classes it finds verbatim in source, so
// building them from a template string would silently render unstyled.
// Rose is deliberately left out so admin's badge stays unique.
export const ROLE_COLORS = {
  emerald: { label: 'Emerald', badge: 'bg-emerald-500/20 border border-emerald-500/40 text-emerald-300', text: 'text-emerald-300', dot: 'bg-emerald-400' },
  teal:    { label: 'Teal',    badge: 'bg-teal-500/20 border border-teal-500/40 text-teal-300',          text: 'text-teal-300',    dot: 'bg-teal-400' },
  cyan:    { label: 'Cyan',    badge: 'bg-cyan-500/20 border border-cyan-500/40 text-cyan-300',          text: 'text-cyan-300',    dot: 'bg-cyan-400' },
  sky:     { label: 'Sky',     badge: 'bg-sky-500/20 border border-sky-500/40 text-sky-300',             text: 'text-sky-300',     dot: 'bg-sky-400' },
  indigo:  { label: 'Indigo',  badge: 'bg-indigo-500/20 border border-indigo-500/40 text-indigo-300',    text: 'text-indigo-300',  dot: 'bg-indigo-400' },
  violet:  { label: 'Violet',  badge: 'bg-violet-500/20 border border-violet-500/40 text-violet-300',    text: 'text-violet-300',  dot: 'bg-violet-400' },
  fuchsia: { label: 'Fuchsia', badge: 'bg-fuchsia-500/20 border border-fuchsia-500/40 text-fuchsia-300', text: 'text-fuchsia-300', dot: 'bg-fuchsia-400' },
  lime:    { label: 'Lime',    badge: 'bg-lime-500/20 border border-lime-500/40 text-lime-300',          text: 'text-lime-300',    dot: 'bg-lime-400' },
  orange:  { label: 'Orange',  badge: 'bg-orange-500/20 border border-orange-500/40 text-orange-300',    text: 'text-orange-300',  dot: 'bg-orange-400' },
  amber:   { label: 'Amber',   badge: 'bg-amber-500/20 border border-amber-500/40 text-amber-300',       text: 'text-amber-300',   dot: 'bg-amber-400' },
  slate:   { label: 'Slate',   badge: 'bg-slate-500/20 border border-slate-400/40 text-slate-300',       text: 'text-slate-300',   dot: 'bg-slate-400' }
};
export const DEFAULT_CUSTOM_ROLE_COLOR = 'emerald';
export const DEFAULT_MODERATOR_COLOR = 'amber';

export function isValidRoleColor(color) {
  return typeof color === 'string' && Object.prototype.hasOwnProperty.call(ROLE_COLORS, color);
}

const ADMIN_STYLE = { badge: 'bg-rose-500/20 border border-rose-500/40 text-rose-300', text: 'text-sp-gold', dot: 'bg-rose-400' };
const PLAYER_STYLE = { badge: 'bg-sp-copper/20 border border-sp-copper/40 text-sp-copper', text: 'text-text-warm', dot: 'bg-sp-gold' };

// Resolves the colour set for a role. `color` is the role's stored
// `roles.color` (roleColor on member/user objects); moderator and custom
// roles honour it, admin and player are fixed.
export function roleStyle(role, color) {
  if (role === 'admin') return ADMIN_STYLE;
  if (!role || role === 'player') return PLAYER_STYLE;
  if (isValidRoleColor(color)) return ROLE_COLORS[color];
  return ROLE_COLORS[role === 'moderator' ? DEFAULT_MODERATOR_COLOR : DEFAULT_CUSTOM_ROLE_COLOR];
}

export function roleBadgeClass(role, color) {
  return roleStyle(role, color).badge;
}

export function roleTextClass(role, color) {
  return roleStyle(role, color).text;
}
