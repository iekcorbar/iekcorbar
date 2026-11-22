import { db } from '@/lib/db';
import { pluginDataQuery } from '@/lib/plugin-data-db';

// The Iekcorbar Gazette — site side.
//
// Composes one weekly edition from the site's own database (news_posts, events,
// users, plus a couple of read-only plugin-DB stats) and hands the plugin two
// commands through the existing site_command_queue. We deliberately do NOT try
// to read site tables from the plugin: the two databases are different servers
// (see docs/gazette-design.md).

const MAX_BODY_CHARS = 42_000;   // base64 stays under the queue's TEXT column (65 KB)
const MAX_PAGE_CHARS = 256;      // what fits on a written-book page in Minecraft

const SITE_URL = 'https://iekcorbar.com';
const DISCORD_INVITE = 'discord.gg/G9P2khU7F4';

// A rotating one-liner from the server's own inside jokes. No DB table, no
// config — the week number picks the line, so it changes every edition.
const DID_YOU_KNOW = [
  'A clockwork skeleton once won the annual slowest-crank contest.',
  'Somewhere in the Garden, a tanuki is still waiting for a wink.',
  'The first steam hammer was stopped by a very determined duck.',
  'A courier golem named Dobby once delivered a single cookie, very seriously.',
  'Three cranks in ten seconds is called a tune. Do not ask the engineer.',
  'A capped capybara is a bus. This is official zoo policy.',
  'The Museum of Failed Contraptions accepts donations of jammed gearboxes.',
  'A maneki-neko has been quietly rigging the luck dice since October.',
  'One brewer labelled a batch "Definitely Fine". It was not.',
  'The gramophone in Xmas Town knows more carols than the whole staff team.',
  'A spyglass is the accepted way to stare politely at a crane.',
  'The Guild bank once accepted 10,000 zinc ingots in a single afternoon.',
  'A wandering trader still sells journals for a spyglass and twelve emeralds.',
  'The slowest water wheel in the network is still technically a water wheel.',
  'A book has been read in a lectern, and the achievement was worth it.',
  "The year's most dangerous machine is a pot of only water, heated for five minutes.",
  'A gift box addressed to the wrong player cannot be opened before Christmas. Even by staff.',
  'A search for "cookie" in a Kura terminal has never once produced a cookie.',
  'The field journal records rare koi. It does not record the fishing rod used.',
  'A shelf of unread manuals is the truest steampunk decoration.',
];

function pad(n) { return String(n).padStart(2, '0'); }

export function weekInfo(date = new Date()) {
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  // ISO 8601: Thursday determines the week's year; Monday is day 1.
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - day);
  const year = d.getUTCFullYear();
  const yearStart = new Date(Date.UTC(year, 0, 1));
  const week = Math.ceil((((d - yearStart) / 86_400_000) + 1) / 7);
  return { key: `${year}-W${pad(week)}`, year, week };
}

export function weekStart(date = new Date()) {
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() - day + 1);
  return d;
}

function mysqlDate(date) {
  return date.toISOString().slice(0, 19).replace('T', ' ');
}

function visibleLength(text) {
  return String(text).replace(/&(#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])/g, '').length;
}

// news_posts content is markdown-ish. A written book has no markdown renderer,
// so flatten it to plain text (keeping list bullets as a readable character).
function plain(text = '') {
  return String(text)
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s*/gm, '')
    .replace(/\*\*|__|~~/g, '')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '• ')
    .replace(/^\s*>\s?/gm, '')
    .replace(/\|/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

// Same rule as BookManager.addPage: pack whole lines, only split a single too
// long line on word boundaries.
function paginate(block) {
  const pages = [];
  let page = '';
  for (const line of String(block).split('\n')) {
    const candidate = page ? `${page}\n${line}` : line;
    if (visibleLength(candidate) <= MAX_PAGE_CHARS) { page = candidate; continue; }
    if (page) { pages.push(page.trim()); page = ''; }
    if (visibleLength(line) <= MAX_PAGE_CHARS) { page = line; continue; }
    let wordPage = '';
    for (const word of line.split(/\s+/).filter(Boolean)) {
      const next = wordPage ? `${wordPage} ${word}` : word;
      if (wordPage && visibleLength(next) > MAX_PAGE_CHARS) {
        pages.push(wordPage.trim());
        wordPage = word;
      } else {
        wordPage = next;
      }
    }
    page = wordPage;
  }
  if (page.trim()) pages.push(page.trim());
  return pages.length ? pages : [''];
}

async function optional(label, fn, fallback) {
  try {
    return await fn();
  } catch (err) {
    console.warn(`[Gazette] ${label} unavailable:`, err?.message || err);
    return fallback;
  }
}

async function collect(limit) {
  const info = weekInfo();
  const since = weekStart();
  const sinceSql = mysqlDate(since);

  // The one hard requirement: if the site's own DB is down/falling back to
  // db.json, publishing an empty paper would be worse than failing loudly.
  const posts = await db.getNewsPosts();
  if (!Array.isArray(posts)) throw new Error('Could not read news posts — refusing to publish an empty edition.');

  const weekPosts = posts
    .filter((p) => p && p.createdAt && new Date(p.createdAt) >= since)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const chosen = weekPosts.slice(0, limit);

  const [events, users, claims, jobs, votes, top] = await Promise.all([
    optional('events', () => db.getEvents(), []),
    optional('users', () => db.getUsers(), []),
    optional('claims', () => db.getTotalLandsClaimed(), null),
    optional('jobs', () => db.getTotalActiveJobs(), null),
    optional('votes', () => pluginDataQuery(
      'SELECT COUNT(*) AS c FROM votifier_events WHERE received_at >= ?', [sinceSql]
    ), null),
    optional('top player', () => pluginDataQuery(
      'SELECT username, playtime_hours AS hours FROM player_stats ORDER BY playtime_hours DESC LIMIT 1'
    ), null),
  ]);

  return { info, since, chosen, weekPosts, events, users, claims, jobs, votes, top };
}

function buildPages(d, issueDate) {
  const { info, chosen, weekPosts, events, users, claims, jobs, votes, top } = d;
  const dateLabel = issueDate.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const pages = [];

  pages.push(...paginate(
    `&0&8────────────────────────────\n` +
    `&6The Iekcorbar Gazette\n` +
    `&8Issue &e${info.week}&8 / &e${info.year}   ·   ${dateLabel}\n` +
    `&7From the Guild Press\n` +
    `&8────────────────────────────\n\n` +
    `&7News from the server, the village and everyone in it.`
  ));

  if (chosen.length) {
    let block = '&lTHIS WEEK&r\n\n';
    for (const post of chosen) {
      const title = plain(post.title) || 'Untitled';
      const body = plain(post.content).replace(/\s+/g, ' ').slice(0, 320);
      block += `&6${title}&r\n&7${body}&r\n\n`;
    }
    pages.push(...paginate(block.trim()));
  } else {
    pages.push(...paginate('&lTHIS WEEK&r\n\n&7A quiet week in the Guild. No new dispatches reached the press.\n\n&7Send in something worth printing!'));
  }

  const upcoming = (Array.isArray(events) ? events : [])
    .filter((e) => e && (e.date || e.eventDate || e.start) && new Date(e.date || e.eventDate || e.start) >= new Date())
    .sort((a, b) => new Date(a.date || a.eventDate || a.start) - new Date(b.date || b.eventDate || b.start))
    .slice(0, 4);
  if (upcoming.length) {
    let block = '&lCOMING UP&r\n\n';
    for (const e of upcoming) {
      const when = new Date(e.date || e.eventDate || e.start).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
      block += `&6${plain(e.title || e.name || 'Event')}&r  &8(${when})\n`;
    }
    pages.push(...paginate(block.trim()));
  }

  const numbers = [];
  if (Array.isArray(users)) numbers.push(`&f${users.length}&7  registered members`);
  if (claims != null) numbers.push(`&f${Number(claims).toLocaleString('en-GB')}&7  chunks claimed`);
  if (jobs != null) numbers.push(`&f${Number(jobs).toLocaleString('en-GB')}&7  active jobs`);
  if (votes?.[0]?.c != null) numbers.push(`&f${Number(votes[0].c).toLocaleString('en-GB')}&7  votes this week`);
  if (top?.[0]?.username) numbers.push(`&f${plain(top[0].username)}&7  most hours in-game`);
  if (numbers.length) {
    pages.push(...paginate(`&lBY THE NUMBERS&r\n\n${numbers.map((n) => `•  ${n}`).join('\n')}`));
  }

  if (weekPosts.length > chosen.length) {
    pages.push(...paginate(
      `&lAND ELSEWHERE&r\n\n&7${weekPosts.length - chosen.length} more dispatch(es) this week — see &o${SITE_URL}/news&r&7.`
    ));
  }

  const joke = DID_YOU_KNOW[(info.week + info.year) % DID_YOU_KNOW.length];
  pages.push(...paginate(`&lDID YOU KNOW&r\n\n&7${joke}`));
  pages.push(...paginate(
    `&0&8────────────────────────────\n` +
    `&7More news: &o${SITE_URL}/news&r\n` +
    `&7Chat with us: &o${DISCORD_INVITE}&r\n` +
    `&8Printed by the Guild Press · ${info.year}`
  ));

  return pages;
}

function buildDiscord(d, issueDate) {
  const dateLabel = issueDate.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const headlines = d.chosen.slice(0, 5).map((p) => plain(p.title) || 'Untitled');
  const parts = [
    `📰 The Iekcorbar Gazette — Issue ${d.info.week} · ${dateLabel}`,
    ...headlines,
    `Read it at the spawn lectern or ${SITE_URL}/news`,
  ];
  return parts.join(' • ').slice(0, 1900);
}

/** Compose an edition. `limit` shrinks the post count until the payload fits. */
export async function composeGazette(limit = 40) {
  let d = await collect(limit);
  let pages = buildPages(d, new Date());
  let body = pages.join('\n---\n');
  while (Buffer.byteLength(body, 'utf8') > MAX_BODY_CHARS && limit > 1) {
    limit = Math.max(1, Math.floor(limit / 2));
    d = await collect(limit);
    pages = buildPages(d, new Date());
    body = pages.join('\n---\n');
  }
  if (Buffer.byteLength(body, 'utf8') > MAX_BODY_CHARS) {
    throw new Error('Gazette edition is too large for a written book even after trimming.');
  }
  const payload = Buffer.from(body, 'utf8').toString('base64');
  return {
    key: d.info.key,
    year: d.info.year,
    week: d.info.week,
    pages: pages.length,
    payload,
    discord: buildDiscord(d, new Date()),
    postsUsed: d.chosen.length,
    postsAvailable: d.weekPosts.length,
  };
}

export function gazetteCommands(edition) {
  return [
    `gazette publish ${edition.week} ${edition.payload}`,
    `discord broadcast ${edition.discord}`,
  ];
}

export async function getGazetteStatus() {
  const settings = await db.getSettings();
  return {
    lastIssue: settings?.gazette_last_issue || null,
    lastPublishedAt: settings?.gazette_last_published_at || null,
    currentWeek: weekInfo().key,
  };
}

export async function markGazettePublished(edition) {
  await db.saveSettings({
    gazette_last_issue: edition.key,
    gazette_last_published_at: new Date().toISOString(),
  });
}
