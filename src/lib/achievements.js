// Known achievement slugs, used by every trigger site (chat, journal,
// friends, shop, ...) so a typo in one route doesn't silently
// create an achievement nobody can ever earn because the CRUD-created slug
// doesn't match. Staff still create the actual achievement rows (title,
// description, icon) through /achievements — this file only fixes the
// *identifier* each trigger awards, not the content.
//
// Adding a new triggerable achievement: pick a slug here, add a matching
// entry to ACHIEVEMENT_SLUG_INFO below (shown in the /achievements slug
// picker so staff don't have to read this file), call
// `db.awardAchievement(username, ACHIEVEMENT_SLUGS.WHATEVER)` from the
// relevant route (fire-and-forget, never let it block the real action —
// see awardAchievement's own doc comment in db.js), then create the matching
// row in the /achievements staff UI with that exact slug.
export const ACHIEVEMENT_SLUGS = {
  FIRST_CHAT_MESSAGE: 'first-chat-message',
  FIRST_CHAT_IMAGE: 'first-chat-image',
  FIRST_JOURNAL_ENTRY: 'first-journal-entry',
  FIRST_FRIEND: 'first-friend',
  FIRST_SHOP_PURCHASE: 'first-shop-purchase'
};

// Plain-language explainer for each slug above, shown directly in the
// /achievements staff editor's slug picker. This exists so staff pick a
// trigger from a described list instead of having to go read this file (or
// every route that calls awardAchievement) just to find out what a slug
// actually does. Keep in sync with ACHIEVEMENT_SLUGS above.
export const ACHIEVEMENT_SLUG_INFO = {
  [ACHIEVEMENT_SLUGS.FIRST_CHAT_MESSAGE]: 'Fires automatically the first time a player sends a message in /chat.',
  [ACHIEVEMENT_SLUGS.FIRST_CHAT_IMAGE]: 'Fires automatically the first time a player attaches an image in /chat.',
  [ACHIEVEMENT_SLUGS.FIRST_JOURNAL_ENTRY]: 'Fires automatically the first time a player saves a profile journal entry.',
  [ACHIEVEMENT_SLUGS.FIRST_FRIEND]: "Fires automatically the first time a player's friend request is accepted (both players earn it).",
  [ACHIEVEMENT_SLUGS.FIRST_SHOP_PURCHASE]: 'Fires automatically the first time a player buys anything from /shop.'
};

// A slug outside ACHIEVEMENT_SLUGS/_INFO above has no code path that ever
// calls awardAchievement for it, so nothing earns it automatically — that's
// the definition of a "custom" achievement in the /achievements editor:
// staff grant it to specific players by hand (see the Grant control there
// and the POST handler on /api/achievements/earned) instead of a developer
// wiring up a new trigger for it.
export function isKnownAchievementSlug(slug) {
  return Object.values(ACHIEVEMENT_SLUGS).includes(slug);
}

// Turns a title into a URL/slug-safe identifier for a custom achievement —
// staff creating one never have to invent or type a slug by hand.
export function slugify(text) {
  return (
    String(text || '')
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 80) || 'custom'
  );
}
