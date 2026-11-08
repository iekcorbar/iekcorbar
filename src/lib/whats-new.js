// Player-facing "what's new" entries for /whats-new. Newest first. Written in plain player
// language: what you can do now, not how it works inside. Only things that are live on the
// server belong here - add an entry when a batch ships, not before.
// tag = which guide to point to.
export const WHATS_NEW = [
  {
    date: '2026-11-07',
    title: 'New spirits in the wild',
    tag: 'Wildlife',
    link: { href: '/wildlife', label: 'Wildlife guide' },
    items: [
      'Five new Will-o\'wisps now drift through the night: green (draws monsters), red (hunts you), gold (leaves a gift), white (heals) and purple (speed and jump).',
      'A Kappa waits in rivers and swamps, tugging at swimmers.',
      'A Tengu rides the wind above the mountain peaks and lifts those below.',
      'A clockwork Automaton patrols the caves on heavy, ticking steps.',
      'A Kitsune with three glowing tails watches from the tree line of old forests.',
      'All of these are rare sights placed by staff - keep your eyes open at night.',
    ],
  },
  {
    date: '2026-11-01',
    title: 'More guides, a calendar and a live garden season',
    tag: 'Website',
    link: { href: '/calendar', label: 'Open the calendar' },
    items: [
      'New guides for Kitchen (farming and cooking), Relics (props and holiday sets) and the everyday tools: Zukan, backpacks and Kura storage.',
      'The calendar shows events, the next holidays and which season the Sakagura garden is in right now, with a countdown to the next one.',
    ],
  },
  {
    date: '2026-11-01',
    title: 'Dragons have their own guide - and their own nests',
    tag: 'Wildlife',
    link: { href: '/dragons', label: 'Dragon Guide' },
    items: [
      'Your tamed dragon can now claim a Dragon Nest (a new craftable prop) and rests on it when you stand still. Eggs are laid in your own nest.',
      'Name tags above tamed dragons, and a look-at readout of hunger and nest in the action bar.',
      'Coal now also heals a hurt dragon.',
      'Riding is smoother: you sit on a proper saddle spot and dragons fly around obstacles instead of sliding sideways.',
      'The Dragon Whistle calls all your dragons to you. Saddlebags, dyes and the pet menu are all described in the new guide.',
    ],
  },
  {
    date: '2026-11-01',
    title: 'Three new guides and a search bar',
    tag: 'Website',
    link: { href: '/search', label: 'Try the search' },
    items: [
      'A new-player route, "Your first hours", with the shortest way to get settled.',
      'A Gouw guide for claiming land, trusting friends and the limits per rank.',
      'A site-wide search over guides, wiki, news and recipes.',
    ],
  },
  {
    date: '2026-10-30',
    title: 'More to brew, more to grow',
    tag: 'Sakagura',
    link: { href: '/sakagura', label: 'Sakagura guide' },
    items: [
      'The garden grew to 48 crops, plus orchard trees you plant as saplings.',
      'Seasons change the garden every seven real days, and two brewers at one cauldron work twice as fast.',
      'Brewing competitions: submit your best bottle and compete for titles and prizes.',
      'A seasonal shelf of holiday drinks sits next to the regular menu.',
    ],
  },
  {
    date: '2026-10-30',
    title: 'Quests with real teachers',
    tag: 'Quests',
    link: { href: '/start', label: 'Where to begin' },
    items: [
      'Several quest lines now teach whole systems step by step, from Steamworks gears to cooking, brewing and storage.',
      'Open your quest book with /questbook. Merchants offer repeatable contracts that pay money and coins.',
    ],
  },
  {
    date: '2026-10-26',
    title: 'Gouw replaces Lands',
    tag: 'Land claims',
    link: { href: '/gouw', label: 'Gouw guide' },
    items: [
      'Land claims now run on Gouw. Every old claim was carried over automatically.',
      'Type /gouw for a menu: claim, trust friends, and set what members may do.',
    ],
  },
  {
    date: '2026-10-24',
    title: 'Steamworks, Kitchen, Wildlife and more',
    tag: 'Plugins',
    link: { href: '/steamworks', label: 'Steamworks guide' },
    items: [
      'Steamworks: gears, steam, trains, ores, oil and the Karakuri Arm.',
      'Kitchen: ten crops, cooking pots and shared meals.',
      'Wildlife: dozens of animals, a Field Journal and a zoo to build.',
      'Satchel backpacks, Kura storage networks and the Zukan recipe browser (/zukan).',
    ],
  },
];
