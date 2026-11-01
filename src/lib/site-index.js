// Hand-kept list of the site's own pages for /search. Wiki pages, news and recipes are
// added at search time from their APIs / data file, so only real pages live here.
// kw = extra words people would type that the title does not contain.
export const SITE_PAGES = [
  { href: '/start', title: 'Your first hours', text: 'New player route: claim land, recipes, quests, pick a path.', kw: 'beginner new start tutorial getting started help' },
  { href: '/welcome', title: 'Welcome', text: 'Create your account, accept the rules and link your Minecraft name.', kw: 'register account link whitelist join' },
  { href: '/gouw', title: 'Gouw guide - land claims', text: 'Claim chunks, trust friends, protection, limits and commands.', kw: 'claim land chunk protect trust lands plot base' },
  { href: '/dragons', title: 'Dragon guide', text: 'Find, tame and ride a Steam Dragon: whistle, saddlebags, dyes, eggs and nests.', kw: 'dragon tame ride whistle nest egg saddle coal' },
  { href: '/wildlife', title: 'Wildlife guide', text: 'New animals, the Field Journal, breeding, zoo and the Wraith.', kw: 'animals journal spyglass zoo tanuki crane elephant' },
  { href: '/sakagura', title: 'Sakagura guide - brewing', text: 'Cauldrons, the Steam Still, casks, quality stars, garden and orchard.', kw: 'brew drink sake whisky wine garden crops still cask alcohol' },
  { href: '/steamworks', title: 'Steamworks guide', text: 'Gears, stress, machines, trains, ores and oil.', kw: 'machines gears steam train create rotation stress oil ore' },
  { href: '/steamworks/arm', title: 'Karakuri Arm programming guide', text: 'Program the robot arm with a small language and a checker.', kw: 'arm robot program karakuri automation' },
  { href: '/kitchen', title: 'Kitchen guide - farming & cooking', text: 'Ten crops, cutting board, cooking pot, kamado stove and shared meals.', kw: 'cook food rice tea ramen sushi farm crops knife pot meal' },
  { href: '/relics', title: 'Relics guide - props & holidays', text: 'Placeable props and seasonal collections: Halloween, Xmas Town, New Year, Valentine, Easter, St. Patrick.', kw: 'relic prop decoration christmas gift box advent music gramophone countdown' },
  { href: '/tools', title: 'Storage, backpacks & Zukan', text: 'Kura storage networks, Satchel backpacks and the Zukan recipe browser.', kw: 'kura storage chest terminal backpack satchel zukan jei recipe book inventory' },
  { href: '/recipes', title: 'Recipes', text: 'Every custom recipe of the server plugins.', kw: 'craft crafting recipe ingredients' },
  { href: '/wiki', title: 'Wiki', text: 'The reference for every plugin and system.', kw: 'wiki reference docs help' },
  { href: '/map', title: 'Map', text: 'The live world map.', kw: 'bluemap world location' },
  { href: '/vote', title: 'Vote', text: 'Vote for the server and earn rewards.', kw: 'voting reward' },
  { href: '/shop', title: 'Shop', text: 'Spend yen and coins on kits and items.', kw: 'buy kit crate yen coins gears currency' },
  { href: '/calendar', title: 'Calendar', text: 'Events, holidays and the seasons of the garden.', kw: 'schedule when date christmas halloween easter season' },
  { href: '/whats-new', title: "What's new", text: 'The latest changes in plain language.', kw: 'changelog update patch new release' },
  { href: '/events', title: 'Events', text: 'Upcoming events and sign-ups.', kw: 'event signup competition' },
  { href: '/news', title: 'News', text: 'Announcements and what is coming.', kw: 'announcement update upcoming' },
  { href: '/updates', title: 'Updates', text: 'What changed on the server.', kw: 'changelog patch' },
  { href: '/rules', title: 'Rules', text: 'The server rules.', kw: 'rule ban allowed' },
  { href: '/achievements', title: 'Achievements', text: 'Website achievements and progress.', kw: 'advancement badge' },
  { href: '/members', title: 'Members', text: 'Player profiles.', kw: 'players profile community' },
  { href: '/projects', title: 'Community projects', text: 'Builds the community works on together.', kw: 'build project signup' },
  { href: '/journal', title: 'Journal', text: 'Player journal posts.', kw: 'blog story' },
  { href: '/friends', title: 'Friends', text: 'Your friends list.', kw: 'friend' },
  { href: '/report-bug', title: 'Report a bug', text: 'Tell us when something is broken.', kw: 'bug issue problem' },
  { href: '/donate', title: 'Support the server', text: 'Voluntary donations.', kw: 'donate paypal support' },
  { href: '/staff-application', title: 'Staff application', text: 'Apply to join the staff team.', kw: 'staff apply moderator' },
  { href: '/behind-the-scenes', title: 'Behind the scenes', text: 'How the server and site are made.', kw: 'about team' },
  { href: '/terms', title: 'Terms', text: 'Terms of service.', kw: 'legal' },
  { href: '/privacy', title: 'Privacy', text: 'Privacy policy.', kw: 'gdpr data legal' },
];

const norm = (s) => (s || '').toString().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

export function stripMarkdown(md) {
  return (md || '')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*_`|]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

// Score one entry against the query words: all words must appear somewhere; title hits
// count most, then keywords, then body text.
export function scoreEntry(entry, words) {
  const title = norm(entry.title);
  const kw = norm(entry.kw);
  const text = norm(entry.text);
  let total = 0;
  for (const w of words) {
    let s = 0;
    if (title.startsWith(w)) s += 12;
    else if (title.includes(w)) s += 8;
    if (kw.includes(w)) s += 5;
    if (text.includes(w)) s += 2;
    if (s === 0) return 0;
    total += s;
  }
  return total;
}

export function snippet(text, words, len = 150) {
  const t = text || '';
  const low = norm(t);
  let at = -1;
  for (const w of words) { at = low.indexOf(w); if (at >= 0) break; }
  if (at < 0) return t.slice(0, len) + (t.length > len ? '…' : '');
  const start = Math.max(0, at - 50);
  return (start > 0 ? '…' : '') + t.slice(start, start + len) + (start + len < t.length ? '…' : '');
}

export { norm };
