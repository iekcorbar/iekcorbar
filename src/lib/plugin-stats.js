// Shared display helper for the generic PlaceholderAPI bridge (see
// PlaceholderSync.java in IekcorbarSiteSync + config.yml's placeholder-sync
// section) — player_stats.plugin_placeholders_json / the RCON `webstats`
// reply's `pluginStats` field is a flat object of whatever keys staff
// configured (Lands claims, ValhallaMMO skills, ValhallaRaces race/class,
// ...), synced without any website code change per new entry. This just
// gives the well-known keys a nicer label; anything else staff adds falls
// back to a humanized version of its own key so it still shows up sensibly.
const PLUGIN_STAT_LABELS = {
  lands_claims: 'Claims',
  lands_owned: 'Lands Owned',
  valhalla_race: 'Race',
  valhalla_class: 'Class',
  mmocore_class: 'MMO Class',
  mmocore_level: 'MMO Level'
};

export function humanizePluginStatKey(key) {
  if (PLUGIN_STAT_LABELS[key]) return PLUGIN_STAT_LABELS[key];
  return key.replace(/^valhalla_/, '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}
