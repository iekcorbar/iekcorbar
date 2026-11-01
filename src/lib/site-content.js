// Registry of admin-editable text labels shown across the site (role display
// names, a couple of page titles) — the "Site Content" tab in the staff
// panel edits these, backed by the `site_content` table (src/lib/db.js:
// getSiteContent/saveSiteContent). This file is the single source of truth
// for which keys exist, their defaults, and how they're grouped/labelled in
// the admin form — both the server (default-merging) and the client
// (SiteContentContext in providers.js) import it.
//
// Role labels here are purely *display* text — the underlying `role` field
// on `users` (admin/moderator/player) stays fixed and keeps driving
// authorization everywhere; only what a role is *called* on-screen is
// dynamic. This lets staff rename these to match whatever their LuckPerms
// group names actually are without a code change/deploy.
export const SITE_CONTENT_DEFAULTS = [
  { key: 'role_label_admin', label: 'Display name for the Admin role', category: 'roles', value: 'Admin' },
  { key: 'role_label_moderator', label: 'Display name for the Moderator role', category: 'roles', value: 'Engineer' },
  { key: 'role_label_player', label: 'Display name for the Player role', category: 'roles', value: 'Player' },
  { key: 'role_description_admin', label: 'Blurb shown under Admins on the Members page', category: 'roles', value: 'Strategic server architect & infrastructure head.' },
  { key: 'role_description_moderator', label: 'Blurb shown under Moderators on the Members page', category: 'roles', value: 'Moderator keeping server order and supporting operations.' },
  { key: 'title_friends_page', label: 'Title of the Friends page', category: 'titles', value: 'Friends' },
  { key: 'title_staff_panel', label: 'Title of the Staff Command Station page', category: 'titles', value: 'Staff Command Station' },
  { key: 'title_members_page', label: 'Title of the Members page', category: 'titles', value: 'Members' },
];

export const SITE_CONTENT_CATEGORY_LABELS = {
  roles: 'Role Names',
  titles: 'Page Titles',
};

export function siteContentDefaultsMap() {
  const map = {};
  for (const item of SITE_CONTENT_DEFAULTS) map[item.key] = item.value;
  return map;
}

export function siteContentMetaFor(key) {
  return SITE_CONTENT_DEFAULTS.find(item => item.key === key) || null;
}
