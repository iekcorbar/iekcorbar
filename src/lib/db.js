import fs from 'fs';
import crypto from 'crypto';
import path from 'path';
import mysql from 'mysql2/promise';
import { siteContentDefaultsMap, siteContentMetaFor } from './site-content';
import { pluginDataQuery } from './plugin-data-db';
import { isBugReportArchived } from './bug-categories';
import { createChatsPreparer } from './chat-store';

const DB_FILE = path.join(process.cwd(), 'src', 'lib', 'db.json');

let pool = null;

function getPool() {
  if (!pool) {
    pool = mysql.createPool({
      host: process.env.DB_HOST || '127.0.0.1',
      port: parseInt(process.env.DB_PORT) || 3306,
      user: process.env.DB_USER || 'iekjeBR36',
      // 2026-10-26: hier stond het echte wachtwoord als fallback - bewust verwijderd,
      // want deze bron gaat naar git. De echte waarde staat in .env.local van de
      // dev-machine én van de server (beide buiten git gehouden).
      password: process.env.DB_PASSWORD || 'TODO-in-env-local',
      database: process.env.DB_NAME || 'iekcorbar',
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0,
      connectTimeout: 3000
    });
  }
  return pool;
}

// Fallback JSON reader if database is unreachable
function readFallbackJson() {
  try {
    if (fs.existsSync(DB_FILE)) {
      return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
    }
  } catch (err) {
    console.error('Failed to read db.json fallback:', err);
  }
  return {};
}

function writeFallbackJson(data) {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('Failed to write db.json fallback:', err);
  }
}

// Execute query with automatic MySQL connection and error fallback
async function query(sql, params = []) {
  try {
    const p = getPool();
    const queryPromise = p.execute(sql, params);
    const timeoutPromise = new Promise((_, reject) => 
      setTimeout(() => reject(new Error('MySQL Query Timeout')), 2000)
    );
    const [rows] = await Promise.race([queryPromise, timeoutPromise]);
    return rows;
  } catch (err) {
    console.warn(`[MySQL DB Warning] ${err.message}. Using fallback execution.`);
    throw err;
  }
}

// Web Push subscriptions (iPhone home-screen web app + desktop browsers).
// A subscription is JSON with a long endpoint URL, too long for the
// device_tokens.expo_push_token VARCHAR(255) unique key, hence its own table
// keyed by a SHA-256 of the endpoint. Created lazily like push_preferences
// (and listed in schema.sql / webpush-migration.sql).
let webPushTableReady = false;
async function ensureWebPushTable() {
  if (webPushTableReady) return;
  await query(`CREATE TABLE IF NOT EXISTS web_push_subscriptions (
    id CHAR(64) PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    subscription_json TEXT NOT NULL,
    user_agent VARCHAR(255) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_web_push_user (user_email)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  webPushTableReady = true;
}
function webPushId(endpoint) {
  return crypto.createHash('sha256').update(String(endpoint)).digest('hex');
}

let eventRemindersTableReady = false;
async function ensureEventRemindersTable() {
  if (eventRemindersTableReady) return;
  await query(`CREATE TABLE IF NOT EXISTS event_reminders_sent (
    event_id VARCHAR(50) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, kind)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  eventRemindersTableReady = true;
}

let analyticsTableReady = false;
async function ensureAnalyticsTable() {
  if (analyticsTableReady) return;
  await query(`CREATE TABLE IF NOT EXISTS analytics_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  type VARCHAR(20) NOT NULL DEFAULT 'pageview',
  path VARCHAR(255) DEFAULT NULL,
  visitor_hash CHAR(64) NOT NULL,
  user_email VARCHAR(255) DEFAULT NULL,
  platform VARCHAR(30) NOT NULL,
  app_version VARCHAR(20) DEFAULT NULL,
  browser VARCHAR(30) DEFAULT NULL,
  os VARCHAR(30) DEFAULT NULL,
  referrer_host VARCHAR(255) DEFAULT NULL,
  INDEX idx_analytics_created (created_at),
  INDEX idx_analytics_type_created (type, created_at),
  INDEX idx_analytics_user (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  analyticsTableReady = true;
}

// Story Room (Staff Panel → Story Room, see src/components/StoryRoomTab.js):
// the staff storyline workspace. Created lazily like the tables above (and
// listed in schema.sql / story-room-migration.sql). Deliberately MySQL-only,
// no db.json fallback: this is multi-person collaborative writing, and a
// save that silently lands in one server's local fallback file is exactly
// the kind of invisible data loss CLAUDE.md warns about — failing loudly
// ("couldn't save, try again") is the honest behaviour here.
let storyTablesReady = false;
async function ensureStoryTables() {
  if (storyTablesReady) return;
  await query(`CREATE TABLE IF NOT EXISTS story_nodes (
    id CHAR(36) PRIMARY KEY,
    parent_id CHAR(36) DEFAULT NULL,
    type VARCHAR(10) NOT NULL DEFAULT 'doc',
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'idea',
    content LONGTEXT DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(100) DEFAULT NULL,
    updated_by VARCHAR(100) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    lock_user VARCHAR(100) DEFAULT NULL,
    lock_until DATETIME DEFAULT NULL,
    published_wiki_id VARCHAR(50) DEFAULT NULL,
    INDEX idx_story_nodes_parent (parent_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  await query(`CREATE TABLE IF NOT EXISTS story_revisions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    node_id CHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT DEFAULT NULL,
    saved_by VARCHAR(100) DEFAULT NULL,
    saved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_story_revisions_node (node_id, saved_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  await query(`CREATE TABLE IF NOT EXISTS story_comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    node_id CHAR(36) NOT NULL,
    author VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    quote TEXT DEFAULT NULL,
    resolved TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_story_comments_node (node_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  await query(`CREATE TABLE IF NOT EXISTS story_entities (
    id CHAR(36) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL DEFAULT 'character',
    name VARCHAR(150) NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    content LONGTEXT DEFAULT NULL,
    image_url VARCHAR(500) DEFAULT NULL,
    updated_by VARCHAR(100) DEFAULT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  await query(`CREATE TABLE IF NOT EXISTS story_timeline (
    id CHAR(36) PRIMARY KEY,
    sort_key INT NOT NULL DEFAULT 0,
    era_label VARCHAR(100) DEFAULT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    node_id CHAR(36) DEFAULT NULL,
    updated_by VARCHAR(100) DEFAULT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  // Guest links (2026-10-22): lets someone without a website account work in
  // the Story Room through a personal link (see src/lib/story-access.js).
  // Only a SHA-256 hash of the link's secret is stored, never the secret.
  // Uploaded files (2026-10-22): images, 3D models, schematics, documents…
  // attached to a chapter, a lore entry, or neither (general asset library).
  // Bytes live in private-uploads/story-files/ (see api/story/files), never
  // under public/, so only Story Room access can read them back.
  await query(`CREATE TABLE IF NOT EXISTS story_files (
    id CHAR(36) PRIMARY KEY,
    node_id CHAR(36) DEFAULT NULL,
    entity_id CHAR(36) DEFAULT NULL,
    stored_name VARCHAR(80) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime VARCHAR(120) DEFAULT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    note VARCHAR(500) DEFAULT NULL,
    uploaded_by VARCHAR(100) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_story_files_node (node_id),
    INDEX idx_story_files_entity (entity_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  await query(`CREATE TABLE IF NOT EXISTS story_guest_links (
    id CHAR(36) PRIMARY KEY,
    token_hash CHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    access VARCHAR(10) NOT NULL DEFAULT 'edit',
    expires_at DATETIME DEFAULT NULL,
    revoked TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(100) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME DEFAULT NULL,
    UNIQUE KEY uq_story_guest_links_token (token_hash)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  storyTablesReady = true;
}

// Chat blocks (api/chat). Created on first use, same as the Story Room
// tables above, so a deploy doesn't need a manual SQL step first.
let chatTablesReady = false;
async function ensureChatTables() {
  if (chatTablesReady) return;
  await query(`CREATE TABLE IF NOT EXISTS chat_blocks (
    blocker VARCHAR(100) NOT NULL,
    blocked VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker, blocked),
    KEY idx_chat_blocks_blocked (blocked)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  // Muted conversations: no unread badge, no bell/push for that contact.
  await query(`CREATE TABLE IF NOT EXISTS chat_mutes (
    user_key VARCHAR(100) NOT NULL,
    muted VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_key, muted)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  chatTablesReady = true;
}

// Keeps the `chats` table complete (adds a missing image_url column and moves
// messages stranded in db.json into MySQL). Every chat method below calls it
// first so they all use the SAME store — see chat-store.js for the incident.
const ensureChatsTable = createChatsPreparer({ query, readFallbackJson, writeFallbackJson, dbFile: DB_FILE });

// Staff audit trail (who changed what, via the Staff Panel). Self-creating
// like chat_blocks so no manual SQL is needed; also in schema.sql.
let auditTableReady = false;
async function ensureAuditTable() {
  if (auditTableReady) return;
  await query(`CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(50) PRIMARY KEY,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(100) NOT NULL,
    actor_email VARCHAR(255) DEFAULT NULL,
    actor_role VARCHAR(50) DEFAULT NULL,
    area VARCHAR(40) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target VARCHAR(255) DEFAULT NULL,
    detail TEXT DEFAULT NULL,
    KEY idx_audit_created (created_at),
    KEY idx_audit_area (area)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  auditTableReady = true;
}

// How long a Story Room edit lock lives without a heartbeat. The editor
// renews it every 30s while open, so a closed tab / crashed browser frees
// the document again within this window instead of locking it forever.
const STORY_LOCK_SECONDS = 90;
// Autosave fires every few seconds while typing; consecutive saves by the
// same person within this window update one revision instead of creating
// hundreds, so the history stays readable ("one row per writing session").
const STORY_REVISION_MERGE_MINUTES = 10;

let linkColumnsReady = false;
async function ensureLinkColumns() {
  if (linkColumnsReady) return;
  const existing = await query("SHOW COLUMNS FROM users LIKE 'link\\_%'");
  const have = new Set((existing || []).map(c => c.Field));
  const defs = {
    link_status: 'VARCHAR(20) NULL DEFAULT NULL',
    link_deadline: 'DATETIME NULL DEFAULT NULL',
    link_code: 'VARCHAR(10) NULL DEFAULT NULL',
    link_code_expires: 'DATETIME NULL DEFAULT NULL'
  };
  for (const [col, def] of Object.entries(defs)) {
    if (!have.has(col)) await query(`ALTER TABLE users ADD COLUMN ${col} ${def}`);
  }
  linkColumnsReady = true;
}

// Profile personalisation columns on users (bio, banner theme, hidden public
// sections). Added on first use so no manual SQL is needed. Kept out of
// getUsers() on purpose — see profile-theme.js for the allowed values.
let profileColumnsReady = false;
async function ensureProfileColumns() {
  if (profileColumnsReady) return;
  const existing = await query("SHOW COLUMNS FROM users LIKE 'profile\\_%'");
  const have = new Set((existing || []).map(c => c.Field));
  const defs = {
    profile_bio: 'TEXT NULL',
    profile_banner: 'VARCHAR(30) NULL DEFAULT NULL',
    profile_hidden: 'VARCHAR(255) NULL DEFAULT NULL',
    profile_banner_img: 'VARCHAR(255) NULL DEFAULT NULL',
    profile_banner_pos: 'TINYINT UNSIGNED NULL DEFAULT NULL'
  };
  for (const [col, def] of Object.entries(defs)) {
    if (!have.has(col)) await query(`ALTER TABLE users ADD COLUMN ${col} ${def}`);
  }
  profileColumnsReady = true;
}

let pushPreferencesTableReady = false;
async function ensurePushPreferencesTable() {
  if (pushPreferencesTableReady) return;
  await query(`CREATE TABLE IF NOT EXISTS push_preferences (
    user_email VARCHAR(255) PRIMARY KEY,
    prefs_json TEXT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
  pushPreferencesTableReady = true;
}

// Every notification-bell entry (personal user_notifications and the staff
// notifications feed) is also sent as a push notification to the user's
// Android app — see pushBellNotification in push.js. Fire-and-forget and
// dynamically imported: push.js imports this file, and a slow/failed push
// must never delay or fail the save that created the bell entry.
function queueBellPush(opts) {
  import('./push.js')
    .then(m => m.pushBellNotification(opts))
    .catch(err => console.error('Failed to queue bell push:', err));
}

// --- Player vs. staff notifications ---------------------------------------
// Two strictly separate routines:
//  - PLAYER routine (this helper): create/update/delete of player-facing
//    content (wiki pages, events, polls, community projects, rules) goes to
//    every registered user's personal bell (user_notifications) + app push.
//  - STAFF routine (createNotification / the `notifications` table): staff-
//    only matters (player reports, staff applications, bug reports) — only
//    fetched by the bell for staff roles and only pushed to isStaffRole()
//    users, never to players.
// Wiki edits used to go to the staff feed; they're player content, so they
// now use the player routine (staff get them too, being users themselves).
//
// Repeated edits to the same item within CONTENT_UPDATE_COOLDOWN_MS only
// notify once, so a staffer saving a wiki page five times in a row doesn't
// send every player five pushes. Creates and deletes always notify.
const CONTENT_LABELS = { wiki: 'Wiki page', wiki_category: 'Wiki category', event: 'Event', poll: 'Poll', project: 'Community project', rules: 'Rule', tip: 'Tip of the day', carousel: 'Homepage slide', carousel_set: 'Homepage slideshow', vote_site: 'Vote site', shop: 'Shop item', achievement: 'Achievement', brewing: 'Brewing recipe', guide: 'Starter guide step', site_content: 'Website text' };
const CONTENT_UPDATE_COOLDOWN_MS = 15 * 60 * 1000;
const recentContentNotifications = new Map();

function recipeDisplayName(recipe, slug) {
  const raw = (Array.isArray(recipe?.names) && recipe.names[0]) || slug || 'recipe';
  return String(raw).replace(/[&§][0-9a-fk-orx]/gi, '').trim();
}

function shortText(text, max = 60) {
  const t = String(text || '').replace(/\s+/g, ' ').trim();
  return t.length > max ? `${t.slice(0, max - 1)}…` : t;
}

// Push copy per content type: an inviting title/body plus up to two action
// buttons shown on the Android notification (see PushService.java in the app
// project). "signup=1" deep links make the events/projects pages open the
// sign-up flow straight away (EventsClient / ProjectsClient).
function buildContentPush(type, action, { id, title, link, extra }) {
  const t = title || '';
  const view = (label) => [{ label, link }];
  if (action === 'deleted') {
    if (type === 'event') return { pushTitle: `Event cancelled: ${t}`, pushBody: "This event won't take place. Keep an eye out for the next one!" };
    return {};
  }
  const created = action === 'created';
  switch (type) {
    case 'event':
      return created
        ? { pushTitle: `📅 New event: ${t}`, pushBody: `${extra ? `${extra.charAt(0).toUpperCase()}${extra.slice(1)} — ` : ''}sign up now and join the fun!`,
            actions: [{ label: 'Sign up', link: `/events?event=${encodeURIComponent(id)}&signup=1` }, { label: 'View', link }] }
        : { pushTitle: `📅 Event updated: ${t}`, pushBody: "The details changed — check what's new and make sure you're signed up!",
            actions: [{ label: 'Sign up', link: `/events?event=${encodeURIComponent(id)}&signup=1` }, { label: 'View', link }] };
    case 'project':
      return created
        ? { pushTitle: `🔨 New community project: ${t}`, pushBody: 'Builders wanted! Join the crew and help build it together.',
            actions: [{ label: 'Join project', link: `/projects?project=${encodeURIComponent(id)}&signup=1` }, { label: 'View', link }] }
        : { pushTitle: `🔨 Project updated: ${t}`, pushBody: 'Check the latest plans — there may still be a spot for you!',
            actions: [{ label: 'Join project', link: `/projects?project=${encodeURIComponent(id)}&signup=1` }, { label: 'View', link }] };
    case 'poll':
      return { pushTitle: created ? '🗳️ New poll' : '🗳️ Poll updated', pushBody: `${t} — cast your vote now!`, actions: view('Vote now') };
    case 'wiki':
      return created
        ? { pushTitle: `📖 New wiki page: ${t}`, pushBody: 'Fresh knowledge for your adventures — give it a read!', actions: view('Read now') }
        : { pushTitle: `📖 Wiki updated: ${t}`, pushBody: "See what's new on this page.", actions: view('Read now') };
    case 'rules':
      return { pushTitle: '📜 Server rules updated', pushBody: `"${t}" — take a quick look so you stay up to date.`, actions: view('Read rules') };
    case 'tip':
      return { pushTitle: '💡 Tip of the day', pushBody: t };
    case 'vote_site':
      return { pushTitle: `🗳️ Vote for Iekcorbar on ${t}`, pushBody: 'Every vote helps the server grow — and earns you rewards!', actions: view('Vote now') };
    case 'shop':
      return { pushTitle: created ? `🛒 New in the shop: ${t}` : `🛒 Shop update: ${t}`, pushBody: 'Take a look before someone else grabs it!', actions: view('Open shop') };
    case 'achievement':
      return { pushTitle: `🏆 ${created ? 'New achievement' : 'Achievement updated'}: ${t}`, pushBody: 'Can you be the one to unlock it first?', actions: view('View achievements') };
    case 'brewing':
      return { pushTitle: `🍶 ${created ? 'New brewing recipe' : 'Recipe updated'}: ${t}`, pushBody: 'Fire up the brewery and give it a try!', actions: view('Start brewing') };
    case 'guide':
      return { pushTitle: `🧭 Starter guide ${created ? 'extended' : 'updated'}`, pushBody: `"${t}" — handy for new and returning players.`, actions: view('Open guide') };
    default:
      return {};
  }
}

async function notifyPlayersOfContent(type, action, { id, title, link, extra }) {
  try {
    const key = `${type}:${id}`;
    const last = recentContentNotifications.get(key);
    if (action === 'updated' && last && Date.now() - last < CONTENT_UPDATE_COOLDOWN_MS) return;
    recentContentNotifications.set(key, Date.now());

    const label = CONTENT_LABELS[type] || 'Item';
    const name = title ? `"${title}"` : '';
    const message = action === 'created'
      ? `New ${label.toLowerCase()}: ${name}${extra ? ` ${extra}` : ''}`
      : `${label} ${action === 'deleted' ? 'removed' : 'updated'}: ${name}`;
    await broadcastUserNotification(type, message.trim(), link, { tag: `${type}-${id}`, ...buildContentPush(type, action, { id, title, link, extra }) });
  } catch (err) {
    console.error(`Failed to notify players of ${type} ${action}:`, err);
  }
}

// --- Automatic cleanup of finished events / completed projects -----------
// Requested 2026-09-27: events and projects disappear by themselves once
// they're over. There's no cron on the Plesk host, so this runs lazily from
// getEvents()/getCommunityProjects() — at most once per CLEANUP_INTERVAL_MS.
// Deliberately SILENT (no notifyPlayersOfContent): a finished event isn't
// "cancelled", so players must not get a removal push for it.
//  - Events have no end time, only a start (date + time + "UTC+x"), so an
//    event is removed EVENT_CLEANUP_AFTER_MS after it started (grace so an
//    evening event doesn't vanish mid-party), with its registrations and its
//    uploaded image file.
//  - Projects have no end date; one marked 'completed' is removed
//    PROJECT_CLEANUP_AFTER_MS after its last update, with its sign-ups.
const EVENT_CLEANUP_AFTER_MS = 12 * 60 * 60 * 1000;       // 12 hours after start
const PROJECT_CLEANUP_AFTER_MS = 7 * 24 * 60 * 60 * 1000; // 7 days after "completed"
const CLEANUP_INTERVAL_MS = 10 * 60 * 1000;
let lastContentCleanup = 0;

// "2026-10-30" + "19:30" + "UTC+1" -> epoch ms (null if unparseable).
// Missing timezone = the server's UTC+2 default (see the Staff Panel form).
export function eventStartUtcMs(date, time, timezone) {
  const d = String(date || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!d) return null;
  const t = String(time || '00:00').match(/^(\d{1,2}):(\d{2})/) || [null, '0', '0'];
  const tz = String(timezone || 'UTC+2').replace(/\s/g, '').match(/^(?:UTC|GMT)?([+-])(\d{1,2})(?::?(\d{2}))?$/i);
  const offsetMin = tz ? (tz[1] === '-' ? -1 : 1) * (Number(tz[2]) * 60 + Number(tz[3] || 0)) : 120;
  return Date.UTC(Number(d[1]), Number(d[2]) - 1, Number(d[3]), Number(t[1]), Number(t[2])) - offsetMin * 60 * 1000;
}

async function cleanupFinishedContent() {
  if (Date.now() - lastContentCleanup < CLEANUP_INTERVAL_MS) return;
  lastContentCleanup = Date.now();
  try {
    const events = await query('SELECT id, title, date, time, timezone, image FROM events');
    for (const e of events || []) {
      const start = eventStartUtcMs(e.date, e.time, e.timezone);
      if (start === null || Date.now() < start + EVENT_CLEANUP_AFTER_MS) continue;
      await query('DELETE FROM event_registrations WHERE event_id = ?', [e.id]);
      await query('DELETE FROM events WHERE id = ?', [e.id]);
      try { await query('DELETE FROM event_reminders_sent WHERE event_id = ?', [String(e.id)]); } catch { /* table may not exist yet */ }
      // Remove the uploaded image too (only our own /uploads/events/ files).
      try {
        const pathname = new URL(String(e.image || ''), 'https://iekcorbar.com').pathname;
        if (pathname.startsWith('/uploads/events/')) {
          await fs.promises.unlink(path.join(process.cwd(), 'public', 'uploads', 'events', path.basename(pathname)));
        }
      } catch { /* no image / already gone */ }
      console.log(`[Cleanup] Removed finished event "${e.title}" (${e.date} ${e.time} ${e.timezone || ''})`);
    }

    // Analytics retention (see src/lib/analytics.js): keep 90 days.
    try { await query('DELETE FROM analytics_events WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)'); } catch { /* table may not exist yet */ }

    const projects = await query("SELECT id, title, updated_at as updatedAt FROM community_projects WHERE status = 'completed'");
    for (const p of projects || []) {
      const updated = new Date(p.updatedAt).getTime();
      if (!Number.isFinite(updated) || Date.now() < updated + PROJECT_CLEANUP_AFTER_MS) continue;
      await query('DELETE FROM project_signups WHERE project_id = ?', [p.id]);
      await query('DELETE FROM community_projects WHERE id = ?', [p.id]);
      console.log(`[Cleanup] Removed completed project "${p.title}"`);
    }
  } catch (err) {
    console.error('Automatic cleanup of finished events/projects failed:', err);
  }
}

// Broadcasts one personal notification row (see `user_notifications`) to
// every registered user — used for player-facing announcements (currently
// just "a website update was published/revised") where the whole point is
// that regular players see it, unlike the staff-only `notifications` table
// (wiki edits stay there — that's a content-management concern for staff,
// not news for players). Deliberately its own try/catch: a broadcast
// failure must never make the real save it's attached to (e.g. saveUpdate)
// report as failed when the update itself already saved fine.
async function broadcastUserNotification(type, message, link, { tag, pushTitle, pushBody, actions } = {}) {
  try {
    const users = await query('SELECT email FROM users');
    if (!users || users.length === 0) return;
    const now = Date.now();
    const placeholders = [];
    const params = [];
    users.forEach((u, i) => {
      placeholders.push('(?, ?, ?, ?, ?)');
      params.push(`${now}-${i}-${Math.random().toString(36).slice(2, 8)}`, u.email, type, message, link);
    });
    await query(
      `INSERT INTO user_notifications (id, recipient_email, type, message, link) VALUES ${placeholders.join(', ')}`,
      params
    );
    queueBellPush({ recipients: users.map(u => u.email), type, message, link, tag, pushTitle, pushBody, actions });
  } catch (err) {
    console.error(`Failed to broadcast "${type}" notification to all users:`, err);
  }
}

// Same idea as broadcastUserNotification just above, but scoped to whoever
// follows `topic` (see the `news_topic_follows` table) instead of every
// registered user — backs the News & Upcoming feature's per-topic
// subscriptions (see db.saveNewsPost) so a player only gets pinged for
// topics they actually opted into, unlike the Updates feed's all-users
// blast. Deliberately its own try/catch for the same reason as that one: a
// notification failure must never make the post save itself report as
// failed.
async function broadcastTopicFollowersNotification(topic, message, link) {
  try {
    const followers = await query('SELECT user_email FROM news_topic_follows WHERE topic = ?', [topic]);
    if (!followers || followers.length === 0) return;
    const now = Date.now();
    const placeholders = [];
    const params = [];
    followers.forEach((f, i) => {
      placeholders.push('(?, ?, ?, ?, ?)');
      params.push(`${now}-${i}-${Math.random().toString(36).slice(2, 8)}`, f.user_email, 'news', message, link);
    });
    await query(
      `INSERT INTO user_notifications (id, recipient_email, type, message, link) VALUES ${placeholders.join(', ')}`,
      params
    );
    queueBellPush({ recipients: followers.map(f => f.user_email), type: 'news', message, link });
  } catch (err) {
    console.error(`Failed to broadcast news notification for topic "${topic}":`, err);
  }
}

// Lazily migrates db.json's old one-set-per-slide shape (carousel_items[]
// each carrying its own setId/sortOrder) into the new many-to-many
// carousel_item_sets array, the very first time the fallback path needs
// it, and persists the result so it only ever runs once. The real MySQL
// side of this same migration is a one-off SQL statement (see schema.sql /
// the SQL notes given alongside this feature) since production data
// already lives there, not in db.json.
function migrateFallbackCarouselMemberships(fb) {
  if (!fb.carousel_item_sets) {
    fb.carousel_item_sets = (fb.carousel_items || [])
      .filter(c => c.setId)
      .map(c => ({ itemId: c.id, setId: c.setId, sortOrder: c.sortOrder ?? 0 }));
    writeFallbackJson(fb);
  }
  return fb.carousel_item_sets;
}

// Fisher-Yates shuffle + cap, used by the carousel's public display query so
// a set holding an unbounded number of screenshots only ever hands the
// homepage a random sample instead of the whole set.
function shuffleAndCap(items, limit) {
  const arr = [...items];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr.slice(0, limit);
}

export const db = {
  // PLAYER HISTORY
  getPlayerHistory: async () => {
    try {
      const rows = await query('SELECT day, players, peak_time as peakTime, last_updated as lastUpdated FROM player_history');
      if (rows && rows.length > 0) return rows;
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.player_history || [];
  },

  recordDailyPlayerPeak: async (count) => {
    try {
      const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
      const currentDay = days[new Date().getDay()];
      const todayStr = new Date().toISOString().split('T')[0];
      const now = new Date();
      const currentHourStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

      const rows = await query('SELECT * FROM player_history WHERE day = ?', [currentDay]);
      if (rows && rows.length > 0) {
        const record = rows[0];
        if (record.last_updated !== todayStr) {
          await query('UPDATE player_history SET players = ?, peak_time = ?, last_updated = ? WHERE day = ?', [count, currentHourStr, todayStr, currentDay]);
        } else if (count > record.players) {
          await query('UPDATE player_history SET players = ?, peak_time = ? WHERE day = ?', [count, currentHourStr, currentDay]);
        }
      } else {
        await query('INSERT INTO player_history (day, players, peak_time, last_updated) VALUES (?, ?, ?, ?)', [currentDay, count, currentHourStr, todayStr]);
      }
    } catch { /* silent fallback */ }
  },

  // USERS
  getUsers: async () => {
    try {
      const rows = await query('SELECT email, password, role, mc_username as mcUsername, custom_skin_url as customSkinUrl, totp_secret as totpSecret, totp_enabled as totpEnabled, website_currency as websiteCurrency, newsletter_opt_out as newsletterOptOut, created_at as createdAt FROM users');
      return rows;
    } catch {
      const fb = readFallbackJson();
      return fb.users || [];
    }
  },

  // WEBSITE CURRENCY ("Gears") — a purely website-native currency, entirely
  // decoupled from the real in-game Vault economy the shop otherwise spends
  // (see shop_items.currency). Lives on users.website_currency, keyed by
  // email like the rest of the account. Earned via awardAchievement below
  // (achievements.currency_reward); spent via a 'website'-currency shop
  // item (see api/shop/purchase). No db.json fallback — like achievements,
  // this feature is MySQL-only; if MySQL is down the whole session/auth
  // layer is already unusable anyway.
  getUserWebsiteCurrency: async (email) => {
    try {
      const rows = await query('SELECT website_currency as websiteCurrency FROM users WHERE LOWER(email) = LOWER(?)', [email]);
      return rows && rows[0] ? (rows[0].websiteCurrency || 0) : 0;
    } catch (err) {
      console.error('Failed to read website currency from MySQL:', err);
      return 0;
    }
  },

  // Atomically spends `amount` if (and only if) the balance is actually
  // sufficient — the balance check lives in the WHERE clause itself so a
  // race between two simultaneous spends can never take a user negative,
  // same guard shape as consumeShopPurchase's ownership check below.
  spendWebsiteCurrency: async (email, amount) => {
    try {
      const result = await query(
        'UPDATE users SET website_currency = website_currency - ? WHERE LOWER(email) = LOWER(?) AND website_currency >= ?',
        [amount, email, amount]
      );
      return result.affectedRows > 0;
    } catch (err) {
      console.error('Failed to spend website currency in MySQL:', err);
      return false;
    }
  },

  // PENDING REGISTRATIONS — transient state for the initiate → send_code → verify
  // handshake, keyed by lowercased mcUsername. Used to live in
  // `global.__pending_registrations` (in-memory only), so any server restart
  // between "whitelisted" and "code entered" wiped it and stranded already-
  // whitelisted players with no way to finish. db.json survives restarts without
  // touching the `users` table — these never become real accounts unless
  // verification succeeds, same as before.
  getPendingRegistration: async (key) => {
    const fb = readFallbackJson();
    return (fb.pending_registrations && fb.pending_registrations[key]) || null;
  },

  savePendingRegistration: async (key, data) => {
    const fb = readFallbackJson();
    fb.pending_registrations = fb.pending_registrations || {};
    fb.pending_registrations[key] = data;
    writeFallbackJson(fb);
  },

  deletePendingRegistration: async (key) => {
    const fb = readFallbackJson();
    if (fb.pending_registrations) {
      delete fb.pending_registrations[key];
      writeFallbackJson(fb);
    }
  },

  // All in-flight registrations (staff visibility only — e.g. the Staff
  // Panel's member list showing "pending" members who whitelisted/requested
  // a code but haven't verified it in-game yet). Never exposes `password`;
  // `code` is dropped too since there's no staff use for it and it's the
  // one thing a player still needs to keep secret to complete their own
  // verification.
  getAllPendingRegistrations: async () => {
    const fb = readFallbackJson();
    const all = fb.pending_registrations || {};
    return Object.values(all).map(({ password, code, ...rest }) => rest);
  },

  // PENDING 2FA TICKETS — same rationale as pending_registrations above: a
  // short-lived, db.json-only ticket proving "password already checked for
  // this email", handed to the client after phase 1 of staff login so phase 2
  // (TOTP setup confirm / TOTP code verify) doesn't need to resend the
  // password. Never becomes a real session unless the TOTP step also passes.
  getPendingTwoFactor: async (ticket) => {
    const fb = readFallbackJson();
    return (fb.pending_2fa && fb.pending_2fa[ticket]) || null;
  },

  savePendingTwoFactor: async (ticket, data) => {
    const fb = readFallbackJson();
    fb.pending_2fa = fb.pending_2fa || {};
    fb.pending_2fa[ticket] = data;
    writeFallbackJson(fb);
  },

  deletePendingTwoFactor: async (ticket) => {
    const fb = readFallbackJson();
    if (fb.pending_2fa) {
      delete fb.pending_2fa[ticket];
      writeFallbackJson(fb);
    }
  },

  // Returns true if the user ended up persisted somewhere (MySQL, or the db.json
  // fallback if MySQL is unreachable) — false only if both writes failed. Previously
  // this swallowed MySQL errors with no fallback at all, so a registration could
  // report "success" to the caller while the account existed nowhere.
  addUser: async (user) => {
    try {
      await query(
        'INSERT INTO users (email, password, role, mc_username, custom_skin_url) VALUES (?, ?, ?, ?, ?)',
        [user.email, user.password, user.role || 'player', user.mcUsername || null, user.customSkinUrl || null]
      );
      return true;
    } catch (err) {
      console.error('Failed to add user to MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.users = fb.users || [];
      if (fb.users.some(u => u.email && u.email.toLowerCase() === user.email.toLowerCase())) {
        return false; // already present in the fallback store — don't duplicate
      }
      fb.users.push({
        email: user.email,
        password: user.password,
        role: user.role || 'player',
        mcUsername: user.mcUsername || null,
        customSkinUrl: user.customSkinUrl || null
      });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to add user to db.json fallback:', fbErr);
      return false;
    }
  },

  updateUserRole: async (email, role) => {
    try {
      await query('UPDATE users SET role = ? WHERE email = ?', [role, email]);
    } catch (err) {
      console.error('Failed to update user role in MySQL:', err);
    }
  },

  // Lazy password-hash migration (see src/lib/password.js) — called by the
  // login route right after a legacy plaintext password checks out, so it
  // never needs touching again. Best-effort: a failure here just means this
  // particular login stays on plaintext for one more round, not a failed
  // login (the caller already has a valid session by this point).
  setUserPasswordHash: async (email, passwordHash) => {
    try {
      await query('UPDATE users SET password = ? WHERE email = ?', [passwordHash, email]);
      return true;
    } catch (err) {
      console.error('Failed to migrate password hash in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const u = (fb.users || []).find(u => u.email && u.email.toLowerCase() === email.toLowerCase());
      if (!u) return false;
      u.password = passwordHash;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to migrate password hash in db.json fallback:', fbErr);
      return false;
    }
  },

  // Custom banner image (uploaded file). setProfileBannerImage stores the new
  // URL (or null to clear) and returns { success, previous } so the caller can
  // delete the file it replaced. getProfileBanners / clearProfileBanner are the
  // staff-moderation side (same shape as the bio helpers below).
  setProfileBannerImage: async (email, url) => {
    try {
      await ensureProfileColumns();
      const rows = await query('SELECT profile_banner_img as img FROM users WHERE LOWER(email) = LOWER(?)', [email]);
      if (!rows || rows.length === 0) return { success: false };
      await query('UPDATE users SET profile_banner_img = ? WHERE LOWER(email) = LOWER(?)', [url || null, email]);
      return { success: true, previous: rows[0].img || null };
    } catch { /* fall through to db.json */ }
    try {
      const fb = readFallbackJson();
      const u = (fb.users || []).find(x => x.email && x.email.toLowerCase() === String(email).toLowerCase());
      if (!u) return { success: false };
      const previous = u.profileBannerImg || null;
      u.profileBannerImg = url || null;
      writeFallbackJson(fb);
      return { success: true, previous };
    } catch {
      return { success: false };
    }
  },

  getProfileBanners: async () => {
    try {
      await ensureProfileColumns();
      const rows = await query("SELECT email, mc_username as mcUsername, profile_banner_img as bannerImage FROM users WHERE profile_banner_img IS NOT NULL AND profile_banner_img <> '' ORDER BY mc_username");
      return rows || [];
    } catch { /* fall through to db.json */ }
    return (readFallbackJson().users || [])
      .filter(u => u.profileBannerImg)
      .map(u => ({ email: u.email, mcUsername: u.mcUsername, bannerImage: u.profileBannerImg }));
  },

  clearProfileBanner: async (username) => {
    try {
      await ensureProfileColumns();
      const rows = await query("SELECT email, profile_banner_img as img FROM users WHERE LOWER(mc_username) = LOWER(?) AND profile_banner_img IS NOT NULL AND profile_banner_img <> ''", [username]);
      if (!rows || rows.length === 0) return { success: false, notFound: true };
      await query('UPDATE users SET profile_banner_img = NULL WHERE LOWER(email) = LOWER(?)', [rows[0].email]);
      return { success: true, email: rows[0].email, url: rows[0].img };
    } catch (err) {
      console.error('Failed to clear profile banner in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const u = (fb.users || []).find(x => x.mcUsername && x.mcUsername.toLowerCase() === String(username).toLowerCase() && x.profileBannerImg);
      if (!u) return { success: false, notFound: true };
      const url = u.profileBannerImg;
      u.profileBannerImg = null;
      writeFallbackJson(fb);
      return { success: true, email: u.email, url };
    } catch {
      return { success: false };
    }
  },

  // Staff moderation of profile bios. getProfileBios lists every non-empty
  // bio (includes email so the caller can notify; strip it before sending to
  // a browser). clearProfileBio blanks one player's bio by in-game name and
  // returns { success, email, bio } (bio = what was removed, for the audit log).
  getProfileBios: async () => {
    try {
      await ensureProfileColumns();
      const rows = await query("SELECT email, mc_username as mcUsername, profile_bio as bio, profile_banner as banner FROM users WHERE profile_bio IS NOT NULL AND profile_bio <> '' ORDER BY mc_username");
      return rows || [];
    } catch { /* fall through to db.json */ }
    return (readFallbackJson().users || [])
      .filter(u => u.profileBio)
      .map(u => ({ email: u.email, mcUsername: u.mcUsername, bio: u.profileBio, banner: u.profileBanner || null }));
  },

  clearProfileBio: async (username) => {
    try {
      await ensureProfileColumns();
      const rows = await query("SELECT email, profile_bio as bio FROM users WHERE LOWER(mc_username) = LOWER(?) AND profile_bio IS NOT NULL AND profile_bio <> ''", [username]);
      if (!rows || rows.length === 0) return { success: false, notFound: true };
      await query('UPDATE users SET profile_bio = NULL WHERE LOWER(email) = LOWER(?)', [rows[0].email]);
      return { success: true, email: rows[0].email, bio: rows[0].bio };
    } catch (err) {
      console.error('Failed to clear profile bio in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const u = (fb.users || []).find(x => x.mcUsername && x.mcUsername.toLowerCase() === String(username).toLowerCase() && x.profileBio);
      if (!u) return { success: false, notFound: true };
      const removed = u.profileBio;
      u.profileBio = '';
      writeFallbackJson(fb);
      return { success: true, email: u.email, bio: removed };
    } catch {
      return { success: false };
    }
  },

  // Profile personalisation. Returns { bio, banner, hidden } with safe
  // defaults. The public variant is looked up by in-game name and never
  // includes the email.
  getProfileSettings: async (email) => {
    const empty = { bio: '', banner: null, hidden: [] };
    try {
      await ensureProfileColumns();
      const rows = await query('SELECT profile_bio as bio, profile_banner as banner, profile_hidden as hidden, profile_banner_img as bannerImage, profile_banner_pos as bannerPos FROM users WHERE LOWER(email) = LOWER(?)', [email]);
      if (rows && rows[0]) return { bio: rows[0].bio || '', banner: rows[0].banner || null, hidden: rows[0].hidden ? String(rows[0].hidden).split(',').filter(Boolean) : [], bannerImage: rows[0].bannerImage || null, bannerPos: rows[0].bannerPos ?? null };
      return empty;
    } catch { /* fall through to db.json */ }
    const u = (readFallbackJson().users || []).find(x => x.email && x.email.toLowerCase() === String(email).toLowerCase());
    return u ? { bio: u.profileBio || '', banner: u.profileBanner || null, hidden: u.profileHidden || [], bannerImage: u.profileBannerImg || null, bannerPos: u.profileBannerPos ?? null } : empty;
  },

  getPublicProfileSettings: async (username) => {
    const empty = { bio: '', banner: null, hidden: [] };
    try {
      await ensureProfileColumns();
      const rows = await query('SELECT profile_bio as bio, profile_banner as banner, profile_hidden as hidden, profile_banner_img as bannerImage, profile_banner_pos as bannerPos, created_at as createdAt FROM users WHERE LOWER(mc_username) = LOWER(?)', [username]);
      if (rows && rows[0]) return { bio: rows[0].bio || '', banner: rows[0].banner || null, hidden: rows[0].hidden ? String(rows[0].hidden).split(',').filter(Boolean) : [], bannerImage: rows[0].bannerImage || null, bannerPos: rows[0].bannerPos ?? null, createdAt: rows[0].createdAt || null };
      return empty;
    } catch { /* fall through to db.json */ }
    const u = (readFallbackJson().users || []).find(x => x.mcUsername && x.mcUsername.toLowerCase() === String(username).toLowerCase());
    return u ? { bio: u.profileBio || '', banner: u.profileBanner || null, hidden: u.profileHidden || [], bannerImage: u.profileBannerImg || null, bannerPos: u.profileBannerPos ?? null, createdAt: u.createdAt || null } : empty;
  },

  // Returns true if persisted somewhere (MySQL, else db.json), false if both failed.
  saveProfileSettings: async (email, { bio, banner, hidden, bannerPos }) => {
    try {
      await ensureProfileColumns();
      await query('UPDATE users SET profile_bio = ?, profile_banner = ?, profile_hidden = ?' + (bannerPos !== undefined ? ', profile_banner_pos = ?' : '') + ' WHERE LOWER(email) = LOWER(?)',
        [bio || null, banner || null, hidden && hidden.length ? hidden.join(',') : null, ...(bannerPos !== undefined ? [bannerPos] : []), email]);
      return true;
    } catch (err) {
      console.error('Failed to save profile settings in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const u = (fb.users || []).find(x => x.email && x.email.toLowerCase() === String(email).toLowerCase());
      if (!u) return false;
      u.profileBio = bio || ''; u.profileBanner = banner || null; u.profileHidden = hidden || [];
      if (bannerPos !== undefined) u.profileBannerPos = bannerPos;
      writeFallbackJson(fb);
      return true;
    } catch {
      return false;
    }
  },

  updateUserIGN: async (email, mcUsername) => {
    try {
      await query('UPDATE users SET mc_username = ? WHERE email = ?', [mcUsername, email]);
    } catch (err) {
      console.error('Failed to update IGN in MySQL:', err);
    }
  },

  // Returns true if the skin URL ended up persisted somewhere (MySQL, or db.json
  // if MySQL is unreachable) — false if both failed. Previously this silently
  // swallowed MySQL errors with no fallback at all, so the "Wardrobe" form could
  // report success while nothing was actually saved — the same bug class as
  // addUser/saveBrewingRecipe, caught while investigating the skin customizer
  // not working end-to-end.
  updateUserSkin: async (email, customSkinUrl) => {
    try {
      await query('UPDATE users SET custom_skin_url = ? WHERE email = ?', [customSkinUrl, email]);
      return true;
    } catch (err) {
      console.error('Failed to update skin in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.users = fb.users || [];
      const idx = fb.users.findIndex(u => u.email && u.email.toLowerCase() === email.toLowerCase());
      if (idx === -1) return false;
      fb.users[idx].customSkinUrl = customSkinUrl;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to update skin in db.json fallback:', fbErr);
      return false;
    }
  },

  // INACTIVITY CLEANUP — "last activity" has no dedicated column on `users`;
  // it's derived from player_stats.last_updated (bumped only on join/quit/
  // while-online sync by the IekcorbarSiteSync plugin, see MySQLSync.java —
  // never for an offline player, so for someone who has logged off it's
  // effectively their last-seen timestamp). A user who registered but was
  // never synced yet (no player_stats row for their mc_username) falls back
  // to the account's created_at instead. Staff (admin/moderator) are always
  // excluded here regardless of inactivity — see the Account Cleanup tab.
  getInactiveAccountCandidates: async (cutoffDate) => {
    try {
      const rows = await query(
        `SELECT u.email, u.mc_username as mcUsername, u.role, u.created_at as createdAt, ps.last_updated as lastActive
         FROM users u
         LEFT JOIN player_stats ps ON LOWER(ps.username) = LOWER(u.mc_username)
         WHERE u.role NOT IN ('admin', 'moderator')
           AND (
             (ps.last_updated IS NOT NULL AND ps.last_updated < ?)
             OR (ps.last_updated IS NULL AND u.created_at < ?)
           )
         ORDER BY COALESCE(ps.last_updated, u.created_at) ASC`,
        [cutoffDate, cutoffDate]
      );
      return rows || [];
    } catch (err) {
      console.error('Failed to get inactive account candidates from MySQL:', err);
      // No sensible db.json fallback for a cross-table inactivity query —
      // an empty list just means staff sees nothing to purge until MySQL is
      // back, rather than risking an incomplete/stale purge list.
      return [];
    }
  },

  // Cascades deletion of the account's own private data (session, push
  // token, friendships, personal notifications, journal). Deliberately
  // leaves username-keyed staff/game records alone (moderation_logs,
  // bug_reports, chats, vote_log, event/project signups, player_stats,
  // brewing_sessions) — those are history tied to the in-game name, not
  // "account data", and stay visible to staff if the same player later
  // registers a fresh account under that IGN again.
  deleteUserAccount: async (email) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM sessions WHERE user_email = ?', [email]);
      await query('DELETE FROM device_tokens WHERE user_email = ?', [email]);
      try { await query('DELETE FROM push_preferences WHERE user_email = ?', [email]); } catch { /* table may not exist yet */ }
      try { await query('DELETE FROM web_push_subscriptions WHERE user_email = ?', [email]); } catch { /* table may not exist yet */ }
      await query('DELETE FROM journal_entries WHERE user_email = ?', [email]);
      await query('DELETE FROM user_notifications WHERE recipient_email = ?', [email]);
      await query('DELETE FROM friendships WHERE requester_email = ? OR addressee_email = ?', [email, email]);
      await query('DELETE FROM users WHERE email = ?', [email]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to delete user account in MySQL:', err);
    }
    if (mysqlOk) return true;

    try {
      const fb = readFallbackJson();
      const lower = email.toLowerCase();
      if (fb.users) fb.users = fb.users.filter(u => !u.email || u.email.toLowerCase() !== lower);
      if (fb.sessions) {
        for (const token of Object.keys(fb.sessions)) {
          if ((fb.sessions[token]?.userEmail || '').toLowerCase() === lower) delete fb.sessions[token];
        }
      }
      if (fb.device_tokens) fb.device_tokens = fb.device_tokens.filter(t => (t.userEmail || '').toLowerCase() !== lower);
      if (fb.push_preferences) delete fb.push_preferences[lower];
      if (fb.web_push_subscriptions) fb.web_push_subscriptions = fb.web_push_subscriptions.filter(s => (s.userEmail || '').toLowerCase() !== lower);
      if (fb.journal_entries) fb.journal_entries = fb.journal_entries.filter(j => (j.userEmail || '').toLowerCase() !== lower);
      if (fb.user_notifications) fb.user_notifications = fb.user_notifications.filter(n => (n.recipientEmail || '').toLowerCase() !== lower);
      if (fb.friendships) fb.friendships = fb.friendships.filter(f => (f.requesterEmail || '').toLowerCase() !== lower && (f.addresseeEmail || '').toLowerCase() !== lower);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to delete user account in db.json fallback:', fbErr);
      return false;
    }
  },

  // TOTP 2FA — returns true if the write landed somewhere (MySQL or the
  // db.json fallback), false only if both failed, following the addUser
  // pattern rather than the older silent-swallow style used above.
  setUserTotpSecret: async (email, secret) => {
    try {
      await query('UPDATE users SET totp_secret = ? WHERE email = ?', [secret, email]);
      return true;
    } catch (err) {
      console.error('Failed to set TOTP secret in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.users = fb.users || [];
      const user = fb.users.find(u => u.email && u.email.toLowerCase() === email.toLowerCase());
      if (!user) return false;
      user.totpSecret = secret;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to set TOTP secret in db.json fallback:', fbErr);
      return false;
    }
  },

  enableUserTotp: async (email) => {
    try {
      await query('UPDATE users SET totp_enabled = 1 WHERE email = ?', [email]);
      return true;
    } catch (err) {
      console.error('Failed to enable TOTP in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.users = fb.users || [];
      const user = fb.users.find(u => u.email && u.email.toLowerCase() === email.toLowerCase());
      if (!user) return false;
      user.totpEnabled = true;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to enable TOTP in db.json fallback:', fbErr);
      return false;
    }
  },

  // SESSIONS — backs the httpOnly `iekcorbar_session` cookie (see
  // src/lib/session.js). MySQL-first with a db.json fallback, same shape as
  // the rest of the persisted (non-transient) data in this file.
  createSession: async (token, email, expiresAt, isStaff = false) => {
    try {
      await query('INSERT INTO sessions (token, user_email, expires_at, is_staff) VALUES (?, ?, ?, ?)', [token, email, expiresAt, isStaff ? 1 : 0]);
      return true;
    } catch (err) {
      console.error('Failed to create session in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.sessions = fb.sessions || {};
      fb.sessions[token] = { userEmail: email, expiresAt: new Date(expiresAt).toISOString(), isStaff: !!isStaff };
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to create session in db.json fallback:', fbErr);
      return false;
    }
  },

  getSessionByToken: async (token) => {
    try {
      const rows = await query('SELECT token, user_email as userEmail, expires_at as expiresAt, is_staff as isStaff FROM sessions WHERE token = ?', [token]);
      if (rows && rows.length > 0) {
        const session = rows[0];
        if (new Date(session.expiresAt) < new Date()) return null;
        return { ...session, isStaff: !!session.isStaff };
      }
      return null;
    } catch {
      const fb = readFallbackJson();
      const session = fb.sessions && fb.sessions[token];
      if (!session) return null;
      if (new Date(session.expiresAt) < new Date()) return null;
      return { token, userEmail: session.userEmail, expiresAt: session.expiresAt, isStaff: !!session.isStaff };
    }
  },

  // Sliding-expiry bump for staff sessions (see src/lib/session.js's
  // getSessionUser) — called on every authenticated staff request so an
  // actively-working staff member is never logged out mid-task, while a
  // session left open and unused still expires STAFF_SESSION_DURATION_MS
  // after the *last* request. Best-effort: a failed bump just means this
  // session expires at its previous (still valid) expiresAt instead of a
  // refreshed one, never fails the request itself.
  updateSessionExpiry: async (token, expiresAt) => {
    try {
      await query('UPDATE sessions SET expires_at = ? WHERE token = ?', [expiresAt, token]);
      return true;
    } catch (err) {
      console.error('Failed to bump session expiry in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      if (fb.sessions && fb.sessions[token]) {
        fb.sessions[token].expiresAt = new Date(expiresAt).toISOString();
        writeFallbackJson(fb);
        return true;
      }
      return false;
    } catch (fbErr) {
      console.error('Failed to bump session expiry in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteSession: async (token) => {
    try {
      await query('DELETE FROM sessions WHERE token = ?', [token]);
    } catch (err) {
      console.error('Failed to delete session in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (fb.sessions && fb.sessions[token]) {
      delete fb.sessions[token];
      writeFallbackJson(fb);
    }
  },

  // Invalidates every logged-in session for an email — called after a
  // successful "forgot password" reset (see api/auth/forgot-password) so a
  // session opened before the password changed doesn't stay valid, same
  // idea as most sites' "you've been logged out everywhere" behavior. Not
  // used by the self-service password change in api/account (that route
  // requires the current password anyway, so there's no "attacker had it,
  // legit owner didn't" scenario to close).
  invalidateSessionsForEmail: async (email) => {
    try {
      await query('DELETE FROM sessions WHERE user_email = ?', [email]);
    } catch (err) {
      console.error('Failed to invalidate sessions in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      if (fb.sessions) {
        for (const [token, session] of Object.entries(fb.sessions)) {
          if (session.userEmail && session.userEmail.toLowerCase() === email.toLowerCase()) {
            delete fb.sessions[token];
          }
        }
        writeFallbackJson(fb);
      }
    } catch (fbErr) {
      console.error('Failed to invalidate sessions in db.json fallback:', fbErr);
    }
  },

  // PASSWORD RESETS — backs "Forgot password?" (api/auth/forgot-password).
  // Same MySQL-first-with-db.json-fallback shape as sessions above, but a
  // real single-use, expiring, server-verified token rather than a
  // stateless signed link (see schema.sql's comment on this table for why).
  createPasswordReset: async (token, email, expiresAt) => {
    try {
      await query('INSERT INTO password_resets (token, user_email, expires_at) VALUES (?, ?, ?)', [token, email, expiresAt]);
      return true;
    } catch (err) {
      console.error('Failed to create password reset in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.password_resets = fb.password_resets || {};
      fb.password_resets[token] = { userEmail: email, expiresAt: new Date(expiresAt).toISOString(), usedAt: null };
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to create password reset in db.json fallback:', fbErr);
      return false;
    }
  },

  // Deletes any other outstanding reset tokens for this email — called both
  // when a fresh reset is requested (so an old, still-mailed link stops
  // working the moment a newer one is sent) and after one is successfully
  // consumed (tidy up rather than leaving used/expired rows around).
  deletePasswordResetsForEmail: async (email) => {
    try {
      await query('DELETE FROM password_resets WHERE user_email = ?', [email]);
    } catch (err) {
      console.error('Failed to delete password resets in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      if (fb.password_resets) {
        for (const token of Object.keys(fb.password_resets)) {
          if (fb.password_resets[token].userEmail?.toLowerCase() === email.toLowerCase()) {
            delete fb.password_resets[token];
          }
        }
        writeFallbackJson(fb);
      }
    } catch (fbErr) {
      console.error('Failed to delete password resets in db.json fallback:', fbErr);
    }
  },

  // Returns the pending reset (userEmail, expiresAt) if `token` exists,
  // hasn't been used, and hasn't expired yet — null otherwise (caller can't
  // tell "no such token" from "expired"/"used" apart, deliberately, same as
  // login not distinguishing "no such email" from "wrong password").
  getPasswordResetByToken: async (token) => {
    try {
      const rows = await query('SELECT user_email as userEmail, expires_at as expiresAt, used_at as usedAt FROM password_resets WHERE token = ?', [token]);
      if (rows && rows.length > 0) {
        const reset = rows[0];
        if (reset.usedAt || new Date(reset.expiresAt) < new Date()) return null;
        return reset;
      }
      return null;
    } catch {
      const fb = readFallbackJson();
      const reset = fb.password_resets && fb.password_resets[token];
      if (!reset || reset.usedAt || new Date(reset.expiresAt) < new Date()) return null;
      return reset;
    }
  },

  markPasswordResetUsed: async (token) => {
    try {
      await query('UPDATE password_resets SET used_at = NOW() WHERE token = ?', [token]);
    } catch (err) {
      console.error('Failed to mark password reset used in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (fb.password_resets && fb.password_resets[token]) {
      fb.password_resets[token].usedAt = new Date().toISOString();
      writeFallbackJson(fb);
    }
  },

  // PASSKEYS (WebAuthn) — see src/lib/webauthn.js + api/auth/passkey/*.
  // Same MySQL-first-with-db.json-fallback shape as sessions/password_resets.
  // publicKey is stored as a base64 string (the library hands back raw
  // bytes) and decoded back to a Buffer wherever it's actually verified
  // against, never exposed to the client — getPasskeysForUser strips it.

  // Safe fields only, for the "Manage Passkeys" list on /profile — never
  // the public key or counter, neither of which any UI needs to show.
  getPasskeysForUser: async (email) => {
    try {
      const rows = await query(
        'SELECT id, device_type as deviceType, backed_up as backedUp, transports, name, created_at as createdAt, last_used_at as lastUsedAt FROM passkey_credentials WHERE user_email = ? ORDER BY created_at ASC',
        [email]
      );
      return rows.map(r => ({ ...r, backedUp: !!r.backedUp, transports: r.transports ? r.transports.split(',') : [] }));
    } catch (err) {
      console.error('Failed to load passkeys from MySQL:', err);
      const fb = readFallbackJson();
      return (fb.passkey_credentials || [])
        .filter(c => c.userEmail.toLowerCase() === email.toLowerCase())
        .map(({ publicKey, counter, userEmail, ...safe }) => safe);
    }
  },

  // Full row, including the public key + counter needed to actually verify
  // a login assertion — internal use only (api/auth/login's passkey_auth_*
  // actions), never returned from an API route as-is.
  getPasskeyCredentialById: async (id) => {
    try {
      const rows = await query(
        'SELECT id, user_email as userEmail, public_key as publicKey, counter, transports FROM passkey_credentials WHERE id = ?',
        [id]
      );
      return rows[0] || null;
    } catch (err) {
      console.error('Failed to load passkey credential from MySQL:', err);
      const fb = readFallbackJson();
      return (fb.passkey_credentials || []).find(c => c.id === id) || null;
    }
  },

  savePasskeyCredential: async ({ id, userEmail, publicKey, counter, deviceType, backedUp, transports, name }) => {
    try {
      await query(
        'INSERT INTO passkey_credentials (id, user_email, public_key, counter, device_type, backed_up, transports, name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [id, userEmail, publicKey, counter, deviceType, backedUp ? 1 : 0, transports ? transports.join(',') : null, name || null]
      );
      return true;
    } catch (err) {
      console.error('Failed to save passkey credential in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.passkey_credentials = fb.passkey_credentials || [];
      fb.passkey_credentials.push({
        id, userEmail, publicKey, counter, deviceType, backedUp: !!backedUp,
        transports: transports || [], name: name || null,
        createdAt: new Date().toISOString(), lastUsedAt: null
      });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save passkey credential in db.json fallback:', fbErr);
      return false;
    }
  },

  // Called after every successful passkey login — WebAuthn's counter is how
  // a cloned/duplicated authenticator gets detected (a clone's counter falls
  // behind the real one's), so this must actually persist or that whole
  // protection is silently a no-op.
  updatePasskeyCounter: async (id, counter) => {
    try {
      await query('UPDATE passkey_credentials SET counter = ?, last_used_at = NOW() WHERE id = ?', [counter, id]);
      return true;
    } catch (err) {
      console.error('Failed to update passkey counter in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const cred = (fb.passkey_credentials || []).find(c => c.id === id);
      if (!cred) return false;
      cred.counter = counter;
      cred.lastUsedAt = new Date().toISOString();
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to update passkey counter in db.json fallback:', fbErr);
      return false;
    }
  },

  // Scoped to the owner's own email — a route calling this still needs to
  // check the row actually belonged to the caller before calling it (delete
  // isn't itself ownership-checked here, same as e.g. deleteWikiPage).
  deletePasskeyCredential: async (id) => {
    try {
      await query('DELETE FROM passkey_credentials WHERE id = ?', [id]);
    } catch (err) {
      console.error('Failed to delete passkey credential in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (fb.passkey_credentials) {
      fb.passkey_credentials = fb.passkey_credentials.filter(c => c.id !== id);
      writeFallbackJson(fb);
    }
  },

  // PASSKEY CHALLENGES — short-lived, one-shot values a WebAuthn ceremony
  // needs to survive between "generate options" and "verify response".
  // db.json-only by design, same as pending_2fa/pending_registrations: this
  // is transient handshake state, not something worth a real table or
  // MySQL durability for. `key` is either a logged-in user's email
  // (registering a new passkey from /profile) or a login ticket (verifying
  // one during the login flow, reusing the same ticket phase 1 already
  // issued) — either way, at most one challenge in flight per key at a time.
  savePasskeyChallenge: async (key, data) => {
    const fb = readFallbackJson();
    fb.passkey_challenges = fb.passkey_challenges || {};
    fb.passkey_challenges[key] = data;
    writeFallbackJson(fb);
  },

  getPasskeyChallenge: async (key) => {
    const fb = readFallbackJson();
    return (fb.passkey_challenges && fb.passkey_challenges[key]) || null;
  },

  deletePasskeyChallenge: async (key) => {
    const fb = readFallbackJson();
    if (fb.passkey_challenges) {
      delete fb.passkey_challenges[key];
      writeFallbackJson(fb);
    }
  },

  // WIKI PAGES & REVISIONS
  getWikiPages: async () => {
    try {
      const pages = await query(
        'SELECT id, slug, title, category, content, updated_by as updatedBy, parent_id as parentId, attachments, sort_order as sortOrder, updated_at as updatedAt FROM wiki_pages ORDER BY sort_order ASC, title ASC'
      );

      // Attach revisions to each wiki page
      for (const page of pages) {
        const revs = await query(
          'SELECT content, updated_by as updatedBy, updated_at as updatedAt FROM wiki_revisions WHERE page_id = ? ORDER BY id DESC',
          [page.id]
        );
        page.revisions = revs || [];
        // Real uploaded files (PDFs/screenshots) — separate from the
        // inline-base64-image-in-markdown feature, see schema.sql's note.
        try {
          page.attachments = page.attachments ? JSON.parse(page.attachments) : [];
        } catch {
          page.attachments = [];
        }
      }
      return pages;
    } catch {
      const fb = readFallbackJson();
      return [...(fb.wiki_pages || [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.title.localeCompare(b.title));
    }
  },

  // Real uploaded files (PDFs/screenshots) attached to a wiki page — see
  // schema.sql's note on `wiki_pages.attachments` for why this is separate
  // from the older inline-base64-image feature. Fallback-to-db.json-and-
  // return-boolean pattern (see addUser). Not part of the revision history
  // (only `content` is revisioned) — same scope as title/category/parent_id.
  addWikiAttachment: async (pageId, attachment) => {
    try {
      const rows = await query('SELECT attachments FROM wiki_pages WHERE id = ?', [pageId]);
      if (!rows || rows.length === 0) return false;
      let list = [];
      try { list = rows[0].attachments ? JSON.parse(rows[0].attachments) : []; } catch { list = []; }
      list.push(attachment);
      await query('UPDATE wiki_pages SET attachments = ? WHERE id = ?', [JSON.stringify(list), pageId]);
      return true;
    } catch (err) {
      console.error('Failed to add wiki attachment in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const page = (fb.wiki_pages || []).find(p => String(p.id) === String(pageId));
      if (!page) return false;
      page.attachments = Array.isArray(page.attachments) ? page.attachments : [];
      page.attachments.push(attachment);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to add wiki attachment in db.json fallback:', fbErr);
      return false;
    }
  },

  removeWikiAttachment: async (pageId, url) => {
    try {
      const rows = await query('SELECT attachments FROM wiki_pages WHERE id = ?', [pageId]);
      if (!rows || rows.length === 0) return false;
      let list = [];
      try { list = rows[0].attachments ? JSON.parse(rows[0].attachments) : []; } catch { list = []; }
      list = list.filter(a => a.url !== url);
      await query('UPDATE wiki_pages SET attachments = ? WHERE id = ?', [JSON.stringify(list), pageId]);
      return true;
    } catch (err) {
      console.error('Failed to remove wiki attachment in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const page = (fb.wiki_pages || []).find(p => String(p.id) === String(pageId));
      if (!page) return false;
      page.attachments = (Array.isArray(page.attachments) ? page.attachments : []).filter(a => a.url !== url);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to remove wiki attachment in db.json fallback:', fbErr);
      return false;
    }
  },

  saveWikiPage: async (page) => {
    try {
      const existing = page.id 
        ? await query('SELECT * FROM wiki_pages WHERE id = ?', [page.id])
        : [];
      
      let finalSlug = page.slug;

      if (existing && existing.length > 0) {
        const oldPage = existing[0];
        // 1. Save old version to wiki_revisions table
        await query(
          'INSERT INTO wiki_revisions (page_id, content, updated_by, updated_at) VALUES (?, ?, ?, ?)',
          [oldPage.id, oldPage.content, oldPage.updated_by || 'Staff', oldPage.updated_at || new Date()]
        );

        // 2. Update page content
        await query(
          'UPDATE wiki_pages SET title = ?, slug = ?, category = ?, content = ?, updated_by = ?, parent_id = ?, updated_at = NOW() WHERE id = ?',
          [page.title, finalSlug, page.category, page.content, page.updatedBy || 'Staff', page.parentId || null, oldPage.id]
        );

        // 3. Notify players (player routine — see notifyPlayersOfContent).
        // `silent` = bulk imports, which send one notification themselves.
        if (!page.silent) await notifyPlayersOfContent('wiki', 'updated', { id: oldPage.id, title: page.title, link: `/wiki?page=${encodeURIComponent(finalSlug)}` });
      } else {
        // Ensure slug is unique for brand new pages
        const slugCheck = await query('SELECT id FROM wiki_pages WHERE slug = ?', [finalSlug]);
        if (slugCheck && slugCheck.length > 0) {
          finalSlug = `${finalSlug}-${Date.now().toString(36)}`;
        }

        const newId = page.id || String(Date.now());

        // New pages go to the end of their sibling group (same parent_id)
        // rather than defaulting to sort_order 0, which would otherwise
        // bump every newly-created page to the front of the index list.
        const maxOrderRows = await query(
          page.parentId
            ? 'SELECT MAX(sort_order) as maxOrder FROM wiki_pages WHERE parent_id = ?'
            : 'SELECT MAX(sort_order) as maxOrder FROM wiki_pages WHERE parent_id IS NULL',
          page.parentId ? [page.parentId] : []
        );
        const nextOrder = (maxOrderRows?.[0]?.maxOrder ?? -1) + 1;

        await query(
          'INSERT INTO wiki_pages (id, slug, title, category, content, updated_by, parent_id, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
          [newId, finalSlug, page.title, page.category, page.content, page.updatedBy || 'Staff', page.parentId || null, nextOrder]
        );

        if (!page.silent) await notifyPlayersOfContent('wiki', 'created', { id: newId, title: page.title, link: `/wiki?page=${encodeURIComponent(finalSlug)}` });
      }
    } catch (err) {
      console.error('Failed to save wiki page in MySQL:', err);
    }
  },

  // Persists staff-driven drag reordering of sibling wiki pages (same
  // category + parent group) in the wiki index view. `orderedIds` is the
  // full new top-to-bottom order for that one sibling group; each id's
  // position becomes its sort_order. Follows the fallback-to-db.json-and-
  // return-a-boolean pattern (see addUser / saveBrewingRecipe) rather than
  // the older silent-swallow style still used by some other save* methods.
  reorderWikiPages: async (orderedIds) => {
    try {
      await Promise.all(
        orderedIds.map((id, index) => query('UPDATE wiki_pages SET sort_order = ? WHERE id = ?', [index, id]))
      );
      return true;
    } catch (err) {
      console.error('Failed to reorder wiki pages in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const byId = new Map((fb.wiki_pages || []).map(p => [String(p.id), p]));
      orderedIds.forEach((id, index) => {
        const page = byId.get(String(id));
        if (page) page.sortOrder = index;
      });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to reorder wiki pages in db.json fallback:', fbErr);
      return false;
    }
  },

  // Returns a boolean (fallback-to-db.json pattern, see addUser) rather than
  // the older fire-and-forget style this used to have — the staff delete
  // confirmation now depends on knowing whether anything actually happened
  // instead of a route that always reports success regardless.
  deleteWikiPage: async (id) => {
    try {
      const [doomed] = await query('SELECT title FROM wiki_pages WHERE id = ?', [id]);
      await query('UPDATE wiki_pages SET parent_id = NULL WHERE parent_id = ?', [id]);
      await query('DELETE FROM wiki_revisions WHERE page_id = ?', [id]);
      await query('DELETE FROM wiki_pages WHERE id = ?', [id]);
      if (doomed) await notifyPlayersOfContent('wiki', 'deleted', { id, title: doomed.title, link: '/wiki' });
      return true;
    } catch (err) {
      console.error('Failed to delete wiki page in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const existed = (fb.wiki_pages || []).some(p => String(p.id) === String(id));
      fb.wiki_pages = (fb.wiki_pages || [])
        .filter(p => String(p.id) !== String(id))
        .map(p => String(p.parentId) === String(id) ? { ...p, parentId: null } : p);
      writeFallbackJson(fb);
      return existed;
    } catch (fbErr) {
      console.error('Failed to delete wiki page in db.json fallback:', fbErr);
      return false;
    }
  },

  // Deletes every page inside one category in a single action, for the
  // staff "Manage Categories" delete button — previously the only way to
  // remove a whole category was editing each of its pages one by one.
  // Returns { success, count } so the confirm modal can report exactly how
  // many pages were removed.
  deleteWikiCategory: async (category) => {
    try {
      const rows = await query('SELECT id FROM wiki_pages WHERE category = ?', [category]);
      const ids = (rows || []).map(r => r.id);
      for (const pageId of ids) {
        await query('UPDATE wiki_pages SET parent_id = NULL WHERE parent_id = ?', [pageId]);
        await query('DELETE FROM wiki_revisions WHERE page_id = ?', [pageId]);
      }
      await query('DELETE FROM wiki_pages WHERE category = ?', [category]);
      await notifyPlayersOfContent('wiki_category', 'deleted', { id: category, title: `${category} (${ids.length} pages)`, link: '/wiki' });

      // Drop the category from the staff-defined chapter order too, so it
      // doesn't linger as a ghost entry in the reorder widget.
      const settings = await db.getSettings();
      if (settings.wiki_category_order) {
        try {
          const order = JSON.parse(settings.wiki_category_order).filter(c => c !== category);
          await db.saveSettings({ wiki_category_order: JSON.stringify(order) });
        } catch { /* malformed stored order — not worth failing the delete over */ }
      }

      return { success: true, count: ids.length };
    } catch (err) {
      console.error('Failed to delete wiki category in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const before = (fb.wiki_pages || []).length;
      const removedIds = new Set((fb.wiki_pages || []).filter(p => p.category === category).map(p => String(p.id)));
      fb.wiki_pages = (fb.wiki_pages || [])
        .filter(p => p.category !== category)
        .map(p => (p.parentId && removedIds.has(String(p.parentId))) ? { ...p, parentId: null } : p);
      const count = before - fb.wiki_pages.length;
      if (fb.settings?.wiki_category_order) {
        try {
          const order = JSON.parse(fb.settings.wiki_category_order).filter(c => c !== category);
          fb.settings.wiki_category_order = JSON.stringify(order);
        } catch { /* malformed stored order — leave as-is */ }
      }
      writeFallbackJson(fb);
      return { success: true, count };
    } catch (fbErr) {
      console.error('Failed to delete wiki category in db.json fallback:', fbErr);
      return { success: false, count: 0 };
    }
  },

  // Renames a category across every page inside it in one go — for the
  // staff "Manage Categories" rename action, and the "strip numbering"
  // bulk-cleanup button (both in moderation/page.js). Same shape as
  // deleteWikiCategory just above, but changes the value instead of
  // removing the rows. Renaming into an already-existing category name is
  // a valid, intentional way to merge two categories — categories aren't a
  // separate table with a uniqueness constraint, just a string column.
  renameWikiCategory: async (oldCategory, newCategory) => {
    try {
      const rows = await query('SELECT id FROM wiki_pages WHERE category = ?', [oldCategory]);
      const count = (rows || []).length;
      await query('UPDATE wiki_pages SET category = ? WHERE category = ?', [newCategory, oldCategory]);

      // Keep the renamed category's position in the staff-defined chapter
      // order instead of it falling back to "newly discovered, append at
      // the end".
      const settings = await db.getSettings();
      if (settings.wiki_category_order) {
        try {
          const order = JSON.parse(settings.wiki_category_order).map(c => c === oldCategory ? newCategory : c);
          await db.saveSettings({ wiki_category_order: JSON.stringify(order) });
        } catch { /* malformed stored order — not worth failing the rename over */ }
      }

      return { success: true, count };
    } catch (err) {
      console.error('Failed to rename wiki category in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      let count = 0;
      (fb.wiki_pages || []).forEach(p => {
        if (p.category === oldCategory) { p.category = newCategory; count++; }
      });
      if (fb.settings?.wiki_category_order) {
        try {
          const order = JSON.parse(fb.settings.wiki_category_order).map(c => c === oldCategory ? newCategory : c);
          fb.settings.wiki_category_order = JSON.stringify(order);
        } catch { /* malformed stored order — leave as-is */ }
      }
      writeFallbackJson(fb);
      return { success: true, count };
    } catch (fbErr) {
      console.error('Failed to rename wiki category in db.json fallback:', fbErr);
      return { success: false, count: 0 };
    }
  },

  restoreWikiRevision: async (pageId, revisionIndex) => {
    try {
      const pageRows = await query('SELECT * FROM wiki_pages WHERE id = ?', [pageId]);
      if (!pageRows || pageRows.length === 0) return null;
      const page = pageRows[0];

      const revRows = await query(
        'SELECT id, content, updated_by as updatedBy, updated_at as updatedAt FROM wiki_revisions WHERE page_id = ? ORDER BY id DESC',
        [pageId]
      );

      if (revRows && revRows[revisionIndex]) {
        const targetRev = revRows[revisionIndex];

        // Store current state as new revision
        await query(
          'INSERT INTO wiki_revisions (page_id, content, updated_by, updated_at) VALUES (?, ?, ?, ?)',
          [pageId, page.content, page.updated_by || 'Staff', page.updated_at || new Date()]
        );

        // Update page with revision content
        await query(
          'UPDATE wiki_pages SET content = ?, updated_by = ?, updated_at = NOW() WHERE id = ?',
          [targetRev.content, targetRev.updatedBy, pageId]
        );

        // Delete restored revision row
        await query('DELETE FROM wiki_revisions WHERE id = ?', [targetRev.id]);

        return { ...page, content: targetRev.content };
      }
    } catch (err) {
      console.error('Failed to restore revision in MySQL:', err);
    }
    return null;
  },

  // EVENTS
  getEvents: async () => {
    await cleanupFinishedContent();
    try {
      return await query('SELECT id, title, description, date, time, timezone, image FROM events');
    } catch {
      const fb = readFallbackJson();
      return fb.events || [];
    }
  },

  saveEvent: async (event) => {
    try {
      const existing = await query('SELECT id FROM events WHERE id = ?', [event.id || '']);
      if (existing && existing.length > 0) {
        await query(
          'UPDATE events SET title = ?, description = ?, date = ?, time = ?, timezone = ?, image = ? WHERE id = ?',
          [event.title, event.description, event.date, event.time, event.timezone || 'UTC+2', event.image || null, event.id]
        );
        await notifyPlayersOfContent('event', 'updated', { id: event.id, title: event.title, link: `/events?event=${encodeURIComponent(event.id)}` });
      } else {
        const newId = event.id || String(Date.now());
        await query(
          'INSERT INTO events (id, title, description, date, time, timezone, image) VALUES (?, ?, ?, ?, ?, ?, ?)',
          [newId, event.title, event.description, event.date, event.time, event.timezone || 'UTC+2', event.image || null]
        );
        await notifyPlayersOfContent('event', 'created', { id: newId, title: event.title, link: `/events?event=${encodeURIComponent(newId)}`, extra: `on ${event.date}${event.time ? ` at ${event.time}` : ''}` });
      }
    } catch (err) {
      console.error('Failed to save event in MySQL:', err);
    }
  },

  deleteEvent: async (id) => {
    try {
      const [doomed] = await query('SELECT title FROM events WHERE id = ?', [id]);
      await query('DELETE FROM event_registrations WHERE event_id = ?', [id]);
      await query('DELETE FROM events WHERE id = ?', [id]);
      if (doomed) await notifyPlayersOfContent('event', 'deleted', { id, title: doomed.title, link: '/events' });
    } catch (err) {
      console.error('Failed to delete event in MySQL:', err);
    }
  },


  // MODERATION LOGS
  getModerationLogs: async () => {
    try {
      return await query(
        'SELECT id, username, type, reason, staff_member as staffMember, date FROM moderation_logs ORDER BY date DESC'
      );
    } catch {
      const fb = readFallbackJson();
      return fb.moderation_logs || [];
    }
  },

  addModerationLog: async (log) => {
    try {
      // Random suffix, not just Date.now(): a batch operation (e.g. the
      // inactivity Account Cleanup purge) can write several of these fast
      // enough to land in the same millisecond, which would otherwise
      // collide on the VARCHAR PRIMARY KEY and silently drop entries.
      const newId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      await query(
        'INSERT INTO moderation_logs (id, username, type, reason, staff_member, date) VALUES (?, ?, ?, ?, ?, NOW())',
        [newId, log.username, log.type, log.reason, log.staffMember]
      );
    } catch (err) {
      console.error('Failed to add moderation log in MySQL:', err);
    }
  },

  // RCON CONSOLE LOG (Staff Panel > Console tab — admin-only raw RCON
  // terminal). Follows the fallback-to-db.json-and-return-boolean pattern
  // (see addUser) rather than the older silent-swallow pattern still used
  // above by addModerationLog — this audit trail is the only record that a
  // given raw command was ever run, so a swallowed write here would be a
  // real accountability gap, not just a display glitch.
  getRconConsoleLog: async () => {
    try {
      return await query(
        'SELECT id, command, response, staff_member as staffMember, date FROM rcon_console_log ORDER BY date DESC LIMIT 100'
      );
    } catch {
      const fb = readFallbackJson();
      return (fb.rcon_console_log || []).slice().reverse();
    }
  },

  addRconConsoleLog: async ({ command, response, staffMember }) => {
    const newId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    try {
      await query(
        'INSERT INTO rcon_console_log (id, command, response, staff_member, date) VALUES (?, ?, ?, ?, NOW())',
        [newId, command, response ?? null, staffMember]
      );
      return true;
    } catch (err) {
      console.error('Failed to add RCON console log in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.rcon_console_log = fb.rcon_console_log || [];
      fb.rcon_console_log.push({ id: newId, command, response: response ?? null, staffMember, date: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to add RCON console log in fallback JSON:', fbErr);
      return false;
    }
  },

  // NEWSLETTER — admin-only broadcast email (see api/admin/newsletter).
  // Send-history audit trail, same fallback-to-db.json-and-return-boolean
  // pattern as the RCON console log just above (a swallowed write here
  // would mean staff can't tell what's already gone out, same real
  // accountability gap).
  getNewsletterHistory: async () => {
    try {
      return await query(
        'SELECT id, subject, body, sent_by as sentBy, recipient_count as recipientCount, sent_at as sentAt FROM newsletters ORDER BY sent_at DESC LIMIT 50'
      );
    } catch {
      const fb = readFallbackJson();
      return (fb.newsletters || []).slice().reverse();
    }
  },

  saveNewsletterRecord: async ({ subject, body, sentBy, recipientCount }) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    try {
      await query(
        'INSERT INTO newsletters (id, subject, body, sent_by, recipient_count) VALUES (?, ?, ?, ?, ?)',
        [id, subject, body, sentBy, recipientCount]
      );
      return true;
    } catch (err) {
      console.error('Failed to save newsletter record in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.newsletters = fb.newsletters || [];
      fb.newsletters.push({ id, subject, body, sentBy, recipientCount, sentAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save newsletter record in db.json fallback:', fbErr);
      return false;
    }
  },

  // Flipped from the one-click unsubscribe link in every newsletter email,
  // or from the player's own Profile page — either way, always opt-*out*
  // (everyone starts subscribed, see users.newsletter_opt_out).
  setNewsletterOptOut: async (email, optOut) => {
    try {
      await query('UPDATE users SET newsletter_opt_out = ? WHERE email = ?', [optOut ? 1 : 0, email]);
      return true;
    } catch (err) {
      console.error('Failed to update newsletter opt-out in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const user = (fb.users || []).find(u => u.email && u.email.toLowerCase() === email.toLowerCase());
      if (!user) return false;
      user.newsletterOptOut = !!optOut;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to update newsletter opt-out in db.json fallback:', fbErr);
      return false;
    }
  },

  // ADMIN SHARED FILES — admin-only file drop (see api/admin/files) for
  // passing plugins/configs/misc files between admins. Only metadata lives
  // here; the actual bytes are written straight to a private, non-public
  // directory by the API route regardless of MySQL outcome (same split as
  // every other upload feature, e.g. saveEvent's poster URL) — so a MySQL
  // hiccup never loses the uploaded file itself, only its listing until the
  // next successful write.
  getAdminFiles: async () => {
    try {
      return await query(
        'SELECT id, filename, original_name as originalName, size_bytes as sizeBytes, description, uploaded_by as uploadedBy, created_at as createdAt FROM admin_shared_files ORDER BY created_at DESC'
      );
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.admin_shared_files || [];
  },

  saveAdminFile: async ({ id, filename, originalName, sizeBytes, description, uploadedBy }) => {
    try {
      await query(
        'INSERT INTO admin_shared_files (id, filename, original_name, size_bytes, description, uploaded_by) VALUES (?, ?, ?, ?, ?, ?)',
        [id, filename, originalName, sizeBytes, description || null, uploadedBy]
      );
      return true;
    } catch (err) {
      console.error('Failed to save admin shared file in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.admin_shared_files = fb.admin_shared_files || [];
      fb.admin_shared_files.push({ id, filename, originalName, sizeBytes, description: description || null, uploadedBy, createdAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save admin shared file in db.json fallback:', fbErr);
      return false;
    }
  },

  // Returns the deleted row's `filename` (or null if nothing matched) so the
  // API route can unlink the actual file from disk — the DB row and the
  // file on disk have to be deleted together or one silently orphans.
  deleteAdminFile: async (id) => {
    try {
      const rows = await query('SELECT filename FROM admin_shared_files WHERE id = ?', [id]);
      if (!rows || rows.length === 0) return null;
      const result = await query('DELETE FROM admin_shared_files WHERE id = ?', [id]);
      if (result.affectedRows > 0) return rows[0].filename;
    } catch (err) {
      console.error('Failed to delete admin shared file in MySQL:', err);
    }
    const fb = readFallbackJson();
    const idx = (fb.admin_shared_files || []).findIndex(f => String(f.id) === String(id));
    if (idx < 0) return null;
    const filename = fb.admin_shared_files[idx].filename;
    fb.admin_shared_files.splice(idx, 1);
    writeFallbackJson(fb);
    return filename;
  },

  // PLAYER REPORTS ("Anonymous Report" on member profile pages) — these
  // methods were missing entirely (api/reports/route.js called
  // db.submitReport/getReports/deleteReport, none of which existed), the
  // same class of bug CLAUDE.md already calls out for the chat backend.
  // Reporter identity is intentionally never stored, matching the feature's
  // "100% anonymous" promise shown in the UI.
  getReports: async () => {
    try {
      return await query(
        'SELECT id, accused as targetUsername, category, reason as details, status, created_at as submittedAt FROM reports ORDER BY created_at DESC'
      );
    } catch {
      const fb = readFallbackJson();
      return fb.reports || [];
    }
  },

  submitReport: async ({ targetUsername, category, details }) => {
    const id = String(Date.now());
    try {
      await query(
        'INSERT INTO reports (id, accused, category, reason, status) VALUES (?, ?, ?, ?, ?)',
        [id, targetUsername, category, details, 'open']
      );
      return true;
    } catch (err) {
      console.error('Failed to submit report in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.reports = fb.reports || [];
      fb.reports.push({ id, targetUsername, category, details, status: 'open', submittedAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to submit report in db.json fallback:', fbErr);
      return false;
    }
  },

  // Real bug found live on production 2026-09-08: the trash-icon delete in
  // the Reports Inbox always returned success but never actually removed
  // anything. Root cause — a MySQL DELETE that matches zero rows does NOT
  // throw, so the old code's "only touch db.json if the MySQL call threw"
  // logic never ran the db.json fallback removal even when the report
  // actually only ever lived in db.json (MySQL silently not having the
  // row is a real, distinct failure mode from MySQL being unreachable).
  // Now checks affectedRows and always also attempts the db.json removal,
  // so the report is gone regardless of which store actually held it.
  deleteReport: async (id) => {
    let mysqlDeleted = false;
    try {
      const result = await query('DELETE FROM reports WHERE id = ?', [id]);
      mysqlDeleted = (result?.affectedRows || 0) > 0;
    } catch (err) {
      console.error('Failed to delete report in MySQL:', err);
    }

    let jsonDeleted = false;
    try {
      const fb = readFallbackJson();
      if (fb.reports) {
        const before = fb.reports.length;
        fb.reports = fb.reports.filter(r => String(r.id) !== String(id));
        jsonDeleted = fb.reports.length !== before;
        if (jsonDeleted) writeFallbackJson(fb);
      }
    } catch (fbErr) {
      console.error('Failed to delete report in db.json fallback:', fbErr);
    }

    return mysqlDeleted || jsonDeleted;
  },

  // BUG REPORTS — submitted from a logged-in user's own profile (identity
  // IS stored here, unlike player reports above, since staff may need to
  // follow up with the reporter). See src/app/api/bug-reports/route.js and
  // src/lib/bug-categories.js for the category list / archive-window const.
  // NOTE: unlike most other bug_reports methods here, updateBugReportStatus/
  // deleteBugReport below check `affectedRows` before treating the MySQL
  // attempt as a success — a report that only ever landed in the db.json
  // fallback (MySQL was briefly down on submit) would otherwise silently
  // "succeed" at 0 rows changed once MySQL is back, exactly the false-
  // success bug class CLAUDE.md calls out for other save*/delete* methods.
  getBugReports: async () => {
    const SQL_COLUMNS = 'id, reporter_email as reporterEmail, reporter_username as reporterUsername, subject, description, category, custom_category as customCategory, status, affected_count as affectedCount, duplicate_of as duplicateOf, staff_notes as staffNotes, created_at as submittedAt, resolved_at as resolvedAt, resolved_by as resolvedBy';
    try {
      return await query(`SELECT ${SQL_COLUMNS} FROM bug_reports ORDER BY created_at DESC`);
    } catch {
      const fb = readFallbackJson();
      return fb.bug_reports || [];
    }
  },

  // Public, unauthenticated-safe view for the /known-bugs page — never
  // exposes reporter identity or staff_notes, folds duplicates into the
  // canonical report they point to (so they're never listed twice), and
  // drops anything past the archive window (see isBugReportArchived).
  // Optionally filtered to one `category`.
  getPublicBugReports: async (category) => {
    const all = await db.getBugReports();
    return all
      .filter(r => r.status !== 'duplicate' && !isBugReportArchived(r))
      .filter(r => !category || r.category === category)
      .map(({ reporterEmail, reporterUsername, staffNotes, duplicateOf, ...safe }) => safe);
  },

  // A reporter's own submitted reports (Profile page's "My Bug Reports")
  // — case-insensitive email match (see CLAUDE.md's journal bug for why
  // that matters), staff_notes always stripped since those are never meant
  // for the reporter to see.
  getBugReportsByReporter: async (email) => {
    const all = await db.getBugReports();
    return all
      .filter(r => r.reporterEmail && r.reporterEmail.toLowerCase() === String(email).toLowerCase())
      .map(({ staffNotes, ...safe }) => safe);
  },

  submitBugReport: async ({ reporterEmail, reporterUsername, subject, description, category, customCategory }) => {
    const id = String(Date.now());
    try {
      await query(
        'INSERT INTO bug_reports (id, reporter_email, reporter_username, subject, description, category, custom_category, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [id, reporterEmail, reporterUsername, subject, description, category, customCategory || null, 'open']
      );
      return true;
    } catch (err) {
      console.error('Failed to submit bug report in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.bug_reports = fb.bug_reports || [];
      fb.bug_reports.push({
        id, reporterEmail, reporterUsername, subject, description,
        category, customCategory: customCategory || null,
        status: 'open', affectedCount: 1, duplicateOf: null, staffNotes: null,
        submittedAt: new Date().toISOString(), resolvedAt: null, resolvedBy: null
      });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to submit bug report in db.json fallback:', fbErr);
      return false;
    }
  },

  updateBugReportStatus: async (id, status, resolvedBy, duplicateOf = null) => {
    const resolvedAt = (status === 'fixed' || status === 'wont_fix' || status === 'duplicate') ? new Date() : null;
    try {
      const result = await query(
        'UPDATE bug_reports SET status = ?, resolved_at = ?, resolved_by = ?, duplicate_of = ? WHERE id = ?',
        [status, resolvedAt, resolvedAt ? resolvedBy : null, status === 'duplicate' ? duplicateOf : null, id]
      );
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to update bug report status in MySQL:', err);
    }
    const fb = readFallbackJson();
    const report = (fb.bug_reports || []).find(r => r.id === id);
    if (!report) return false;
    report.status = status;
    report.resolvedAt = resolvedAt ? resolvedAt.toISOString() : null;
    report.resolvedBy = resolvedAt ? resolvedBy : null;
    report.duplicateOf = status === 'duplicate' ? duplicateOf : null;
    writeFallbackJson(fb);
    return true;
  },

  // Staff-only notes (never shown to the reporter or on /known-bugs) — the
  // idea being that when the same bug resurfaces, whoever picks it up next
  // already knows where to look. See CLAUDE.md's addUser/saveBrewingRecipe
  // pattern this follows (return a real boolean, don't swallow failures).
  updateBugReportNotes: async (id, staffNotes) => {
    try {
      const result = await query('UPDATE bug_reports SET staff_notes = ? WHERE id = ?', [staffNotes, id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to update bug report staff notes in MySQL:', err);
    }
    const fb = readFallbackJson();
    const report = (fb.bug_reports || []).find(r => r.id === id);
    if (!report) return false;
    report.staffNotes = staffNotes;
    writeFallbackJson(fb);
    return true;
  },

  // Staff re-categorizing an existing report — added so reports filed as
  // "Other" before a matching predefined category existed (see
  // bug-categories.js's comment) can be moved into one after the fact,
  // which also makes them eligible for duplicate-matching/"I have this
  // too" going forward (that's deliberately skipped for "Other").
  updateBugReportCategory: async (id, category, customCategory) => {
    try {
      const result = await query('UPDATE bug_reports SET category = ?, custom_category = ? WHERE id = ?', [category, customCategory || null, id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to update bug report category in MySQL:', err);
    }
    const fb = readFallbackJson();
    const report = (fb.bug_reports || []).find(r => r.id === id);
    if (!report) return false;
    report.category = category;
    report.customCategory = customCategory || null;
    writeFallbackJson(fb);
    return true;
  },

  // "I have this too" while composing a new report — see the duplicate-
  // detection panel in Profile's bug report form. Records *who* confirmed
  // it (bug_report_watchers) so they can be notified alongside the
  // original reporter once staff marks it fixed (see api/bug-reports'
  // update_status), and bumps the display counter — but only the first
  // time a given email confirms; a repeat click, or the original reporter
  // "confirming" their own report, is a harmless no-op rather than
  // inflating the count or double-notifying them later.
  bumpBugReportAffectedCount: async (id, email) => {
    try {
      const reportRows = await query('SELECT reporter_email as reporterEmail FROM bug_reports WHERE id = ?', [id]);
      const report = reportRows?.[0];
      if (!report) return false;
      if (report.reporterEmail && report.reporterEmail.toLowerCase() === email.toLowerCase()) return true;

      const insertResult = await query('INSERT IGNORE INTO bug_report_watchers (bug_report_id, email) VALUES (?, ?)', [id, email]);
      if (insertResult.affectedRows === 0) return true; // already watching — not an error
      await query('UPDATE bug_reports SET affected_count = affected_count + 1 WHERE id = ?', [id]);
      return true;
    } catch (err) {
      console.error('Failed to bump bug report affected_count in MySQL:', err);
    }
    const fb = readFallbackJson();
    const report = (fb.bug_reports || []).find(r => r.id === id);
    if (!report) return false;
    if (report.reporterEmail && report.reporterEmail.toLowerCase() === email.toLowerCase()) return true;
    fb.bug_report_watchers = fb.bug_report_watchers || [];
    const already = fb.bug_report_watchers.some(w => w.bugReportId === id && w.email.toLowerCase() === email.toLowerCase());
    if (already) return true;
    fb.bug_report_watchers.push({ bugReportId: id, email, createdAt: new Date().toISOString() });
    report.affectedCount = (report.affectedCount || 1) + 1;
    writeFallbackJson(fb);
    return true;
  },

  // Every email that confirmed "I have this too" on a report — notified
  // alongside the original reporter once it's marked fixed.
  getBugReportWatcherEmails: async (bugReportId) => {
    try {
      const rows = await query('SELECT email FROM bug_report_watchers WHERE bug_report_id = ?', [bugReportId]);
      return (rows || []).map(r => r.email);
    } catch (err) {
      console.error('Failed to get bug report watchers from MySQL:', err);
      const fb = readFallbackJson();
      return (fb.bug_report_watchers || []).filter(w => w.bugReportId === bugReportId).map(w => w.email);
    }
  },

  deleteBugReport: async (id) => {
    try {
      const result = await query('DELETE FROM bug_reports WHERE id = ?', [id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to delete bug report in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (!fb.bug_reports || !fb.bug_reports.some(r => r.id === id)) return false;
    fb.bug_reports = fb.bug_reports.filter(r => r.id !== id);
    writeFallbackJson(fb);
    return true;
  },

  // BUG REPORT MESSAGES — the conversation thread on a single report (see
  // schema.sql's bug_report_messages). `deliveryChannel` is only set on
  // staff-authored messages ('bell' | 'chat' | 'email' — see the Staff
  // Panel's "Contact Reporter" action); a reporter's own follow-up from
  // their Profile page leaves it null.
  getBugReportMessages: async (bugReportId) => {
    try {
      return await query(
        'SELECT id, bug_report_id as bugReportId, sender_type as senderType, sender_name as senderName, body, delivery_channel as deliveryChannel, created_at as createdAt FROM bug_report_messages WHERE bug_report_id = ? ORDER BY created_at ASC',
        [bugReportId]
      );
    } catch {
      const fb = readFallbackJson();
      return (fb.bug_report_messages || []).filter(m => m.bugReportId === bugReportId);
    }
  },

  addBugReportMessage: async ({ bugReportId, senderType, senderName, body, deliveryChannel }) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    try {
      await query(
        'INSERT INTO bug_report_messages (id, bug_report_id, sender_type, sender_name, body, delivery_channel) VALUES (?, ?, ?, ?, ?, ?)',
        [id, bugReportId, senderType, senderName || null, body, deliveryChannel || null]
      );
      return true;
    } catch (err) {
      console.error('Failed to add bug report message in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.bug_report_messages = fb.bug_report_messages || [];
      fb.bug_report_messages.push({ id, bugReportId, senderType, senderName: senderName || null, body, deliveryChannel: deliveryChannel || null, createdAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to add bug report message in db.json fallback:', fbErr);
      return false;
    }
  },

  // STAFF APPLICATIONS — see schema.sql's staff_applications comment and
  // src/app/api/staff-applications/route.js. Follows the same
  // affectedRows-checked pattern as updateBugReportStatus/deleteBugReport
  // above (never silently "succeed" at 0 rows changed).
  getStaffApplications: async () => {
    const SQL_COLUMNS = 'id, applicant_email as applicantEmail, applicant_username as applicantUsername, age, availability, timezone, discord_username as discordUsername, experience, reference_name as referenceName, reason, contribution, status, staff_notes as staffNotes, reviewed_by as reviewedBy, reviewed_at as reviewedAt, created_at as createdAt';
    try {
      return await query(`SELECT ${SQL_COLUMNS} FROM staff_applications ORDER BY created_at DESC`);
    } catch {
      const fb = readFallbackJson();
      return fb.staff_applications || [];
    }
  },

  // An applicant's own submissions (Staff Application page's status view) —
  // case-insensitive email match, staff_notes always stripped since those
  // are never meant for the applicant to see.
  getStaffApplicationsByApplicant: async (email) => {
    const all = await db.getStaffApplications();
    return all
      .filter(a => a.applicantEmail && a.applicantEmail.toLowerCase() === String(email).toLowerCase())
      .map(({ staffNotes, ...safe }) => safe);
  },

  submitStaffApplication: async ({ applicantEmail, applicantUsername, age, availability, timezone, discordUsername, experience, referenceName, reason, contribution }) => {
    const id = String(Date.now());
    try {
      await query(
        'INSERT INTO staff_applications (id, applicant_email, applicant_username, age, availability, timezone, discord_username, experience, reference_name, reason, contribution, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [id, applicantEmail, applicantUsername || null, age, availability, timezone || null, discordUsername || null, experience || null, referenceName, reason, contribution, 'pending']
      );
      return true;
    } catch (err) {
      console.error('Failed to submit staff application in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.staff_applications = fb.staff_applications || [];
      fb.staff_applications.push({
        id, applicantEmail, applicantUsername: applicantUsername || null, age, availability,
        timezone: timezone || null, discordUsername: discordUsername || null, experience: experience || null,
        referenceName, reason, contribution, status: 'pending', staffNotes: null, reviewedBy: null, reviewedAt: null,
        createdAt: new Date().toISOString()
      });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to submit staff application in db.json fallback:', fbErr);
      return false;
    }
  },

  updateStaffApplicationStatus: async (id, status, reviewedBy) => {
    const reviewedAt = new Date();
    try {
      const result = await query(
        'UPDATE staff_applications SET status = ?, reviewed_at = ?, reviewed_by = ? WHERE id = ?',
        [status, reviewedAt, reviewedBy, id]
      );
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to update staff application status in MySQL:', err);
    }
    const fb = readFallbackJson();
    const app = (fb.staff_applications || []).find(a => a.id === id);
    if (!app) return false;
    app.status = status;
    app.reviewedAt = reviewedAt.toISOString();
    app.reviewedBy = reviewedBy;
    writeFallbackJson(fb);
    return true;
  },

  updateStaffApplicationNotes: async (id, staffNotes) => {
    try {
      const result = await query('UPDATE staff_applications SET staff_notes = ? WHERE id = ?', [staffNotes, id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to update staff application notes in MySQL:', err);
    }
    const fb = readFallbackJson();
    const app = (fb.staff_applications || []).find(a => a.id === id);
    if (!app) return false;
    app.staffNotes = staffNotes;
    writeFallbackJson(fb);
    return true;
  },

  deleteStaffApplication: async (id) => {
    try {
      const result = await query('DELETE FROM staff_applications WHERE id = ?', [id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to delete staff application in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (!fb.staff_applications || !fb.staff_applications.some(a => a.id === id)) return false;
    fb.staff_applications = fb.staff_applications.filter(a => a.id !== id);
    writeFallbackJson(fb);
    return true;
  },

  // CAROUSEL SETS — staff group screenshots into named sets (unlimited items
  // each); exactly one set is "active" and feeds the public homepage, which
  // only ever sees a random sample (see getCarouselDisplay below). A single
  // slide (carousel_items row) can belong to any number of sets at once —
  // see carousel_item_sets, the many-to-many join table — so the same
  // upload doesn't have to be re-uploaded into every set that wants it.
  getCarouselSets: async () => {
    try {
      const rows = await query(
        `SELECT s.id, s.name, s.is_active as isActive, s.created_at as createdAt, COUNT(cis.item_id) as itemCount
         FROM carousel_sets s
         LEFT JOIN carousel_item_sets cis ON cis.set_id = s.id
         GROUP BY s.id, s.name, s.is_active, s.created_at
         ORDER BY s.created_at ASC`
      );
      return (rows || []).map(r => ({ ...r, isActive: Boolean(r.isActive) }));
    } catch (err) {
      console.error('Failed to get carousel sets from MySQL:', err);
      const fb = readFallbackJson();
      const memberships = migrateFallbackCarouselMemberships(fb);
      return (fb.carousel_sets || []).map(s => ({ ...s, itemCount: memberships.filter(m => m.setId === s.id).length }));
    }
  },

  saveCarouselSet: async (set) => {
    const finalId = set.id || String(Date.now());
    try {
      const existing = await query('SELECT id FROM carousel_sets WHERE id = ?', [set.id || '']);
      if (existing && existing.length > 0) {
        await query('UPDATE carousel_sets SET name = ? WHERE id = ?', [set.name, set.id]);
        await notifyPlayersOfContent('carousel_set', 'updated', { id: set.id, title: set.name, link: '/' });
      } else {
        // The very first set ever created starts active automatically (so
        // the homepage has something to show without an extra click); every
        // set after that starts inactive until staff explicitly activates it.
        const [{ total } = { total: 0 }] = await query('SELECT COUNT(*) as total FROM carousel_sets');
        await query('INSERT INTO carousel_sets (id, name, is_active) VALUES (?, ?, ?)', [finalId, set.name, total === 0 ? 1 : 0]);
        await notifyPlayersOfContent('carousel_set', 'created', { id: finalId, title: set.name, link: '/' });
      }
      return true;
    } catch (err) {
      console.error('Failed to save carousel set in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.carousel_sets = fb.carousel_sets || [];
      const idx = fb.carousel_sets.findIndex(s => s.id === set.id);
      if (idx >= 0) {
        fb.carousel_sets[idx].name = set.name;
      } else {
        fb.carousel_sets.push({ id: finalId, name: set.name, isActive: fb.carousel_sets.length === 0, createdAt: new Date().toISOString() });
      }
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save carousel set in db.json fallback:', fbErr);
      return false;
    }
  },

  // Flips is_active on for `id` and off for every other set in one query —
  // only one set is ever active at a time.
  setActiveCarouselSet: async (id) => {
    let mysqlOk = false;
    try {
      await query('UPDATE carousel_sets SET is_active = (id = ?)', [id]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to set active carousel set in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.carousel_sets) {
        fb.carousel_sets.forEach(s => { s.isActive = s.id === id; });
        writeFallbackJson(fb);
      }
    }
  },

  // Deleting a set only ever removes *membership* — a slide shared with
  // another set must survive. Only slides left with zero remaining
  // memberships afterward (true orphans) actually get deleted.
  deleteCarouselSet: async (id) => {
    try {
      const [doomedSet] = await query('SELECT name FROM carousel_sets WHERE id = ?', [id]);
      if (doomedSet) await notifyPlayersOfContent('carousel_set', 'deleted', { id, title: doomedSet.name, link: '/' });
      const memberRows = await query('SELECT item_id as itemId FROM carousel_item_sets WHERE set_id = ?', [id]);
      const memberIds = (memberRows || []).map(r => r.itemId);
      await query('DELETE FROM carousel_item_sets WHERE set_id = ?', [id]);
      if (memberIds.length > 0) {
        const stillUsedRows = await query(
          `SELECT DISTINCT item_id as itemId FROM carousel_item_sets WHERE item_id IN (${memberIds.map(() => '?').join(',')})`,
          memberIds
        );
        const stillUsed = new Set((stillUsedRows || []).map(r => r.itemId));
        const orphanIds = memberIds.filter(itemId => !stillUsed.has(itemId));
        if (orphanIds.length > 0) {
          await query(`DELETE FROM carousel_items WHERE id IN (${orphanIds.map(() => '?').join(',')})`, orphanIds);
        }
      }
      await query('DELETE FROM carousel_sets WHERE id = ?', [id]);
    } catch (err) {
      console.error('Failed to delete carousel set in MySQL:', err);
    }
    const fb = readFallbackJson();
    const memberships = migrateFallbackCarouselMemberships(fb);
    const memberIds = memberships.filter(m => m.setId === id).map(m => m.itemId);
    fb.carousel_item_sets = memberships.filter(m => m.setId !== id);
    const stillUsed = new Set(fb.carousel_item_sets.map(m => m.itemId));
    const orphanIds = new Set(memberIds.filter(itemId => !stillUsed.has(itemId)));
    if (fb.carousel_items && orphanIds.size > 0) fb.carousel_items = fb.carousel_items.filter(c => !orphanIds.has(c.id));
    fb.carousel_sets = (fb.carousel_sets || []).filter(s => s.id !== id);
    writeFallbackJson(fb);
  },

  // All images in one set, unfiltered and uncapped — backs the staff CRUD
  // view (Settings -> Carousel Manager) once a set is selected for editing.
  // Ordered by that set's own sort_order (staff drag-reorder — see
  // reorderCarouselItems) — a slide's order can differ per set since
  // membership + ordering both live in carousel_item_sets now.
  getCarouselItemsBySet: async (setId) => {
    try {
      return await query(
        `SELECT ci.id, ci.src, ci.title, ci.desc_text as \`desc\`, cis.sort_order as sortOrder,
                (SELECT COUNT(*) FROM carousel_item_sets other WHERE other.item_id = ci.id) as setCount
         FROM carousel_item_sets cis
         JOIN carousel_items ci ON ci.id = cis.item_id
         WHERE cis.set_id = ?
         ORDER BY cis.sort_order ASC`,
        [setId]
      );
    } catch (err) {
      console.error('Failed to get carousel items by set from MySQL:', err);
      const fb = readFallbackJson();
      const allMemberships = migrateFallbackCarouselMemberships(fb);
      const memberships = allMemberships.filter(m => m.setId === setId).sort((a, b) => a.sortOrder - b.sortOrder);
      const byId = new Map((fb.carousel_items || []).map(c => [c.id, c]));
      return memberships
        .map(m => {
          const item = byId.get(m.itemId);
          if (!item) return null;
          const setCount = allMemberships.filter(x => x.itemId === m.itemId).length;
          return { id: item.id, src: item.src, title: item.title, desc: item.desc, sortOrder: m.sortOrder, setCount };
        })
        .filter(Boolean);
    }
  },

  // Every set a given slide currently belongs to — feeds the "which sets
  // is this slide in" checklist on the edit form.
  getCarouselItemSetIds: async (itemId) => {
    try {
      const rows = await query('SELECT set_id as setId FROM carousel_item_sets WHERE item_id = ?', [itemId]);
      return (rows || []).map(r => r.setId);
    } catch (err) {
      console.error('Failed to get carousel item set memberships from MySQL:', err);
      const fb = readFallbackJson();
      return migrateFallbackCarouselMemberships(fb).filter(m => m.itemId === itemId).map(m => m.setId);
    }
  },

  // Persists staff drag-reordering of the slides within one carousel set —
  // same pattern as reorderWikiPages: `orderedIds` is the full new
  // top-to-bottom order for that one set, each id's position becomes its
  // sort_order *for that set specifically* (a shared slide keeps whatever
  // position it already had in any other set).
  reorderCarouselItems: async (orderedIds, setId) => {
    try {
      await Promise.all(
        orderedIds.map((id, index) => query('UPDATE carousel_item_sets SET sort_order = ? WHERE item_id = ? AND set_id = ?', [index, id, setId]))
      );
      return true;
    } catch (err) {
      console.error('Failed to reorder carousel items in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const memberships = migrateFallbackCarouselMemberships(fb);
      orderedIds.forEach((id, index) => {
        const m = memberships.find(m => m.itemId === id && m.setId === setId);
        if (m) m.sortOrder = index;
      });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to reorder carousel items in db.json fallback:', fbErr);
      return false;
    }
  },

  // Public homepage view: a random sample (max 10) of the active set's
  // images. Falls back to treating every carousel_items row as one pool if
  // no sets exist yet at all (pre-migration data, or a fresh local db.json).
  getCarouselDisplay: async () => {
    const CAROUSEL_DISPLAY_LIMIT = 10;
    try {
      const sets = await query('SELECT id, is_active as isActive FROM carousel_sets');
      let items;
      if (!sets || sets.length === 0) {
        items = await query('SELECT id, src, title, desc_text as `desc` FROM carousel_items');
      } else {
        const active = sets.find(s => s.isActive);
        items = active
          ? await query(
              `SELECT ci.id, ci.src, ci.title, ci.desc_text as \`desc\`
               FROM carousel_item_sets cis JOIN carousel_items ci ON ci.id = cis.item_id
               WHERE cis.set_id = ?`,
              [active.id]
            )
          : [];
      }
      return shuffleAndCap(items || [], CAROUSEL_DISPLAY_LIMIT);
    } catch (err) {
      console.error('Failed to get carousel display from MySQL:', err);
      const fb = readFallbackJson();
      const sets = fb.carousel_sets || [];
      const all = fb.carousel_items || [];
      let items;
      if (sets.length === 0) {
        items = all;
      } else {
        const active = sets.find(s => s.isActive);
        const memberships = migrateFallbackCarouselMemberships(fb);
        const activeIds = active ? new Set(memberships.filter(m => m.setId === active.id).map(m => m.itemId)) : new Set();
        items = active ? all.filter(c => activeIds.has(c.id)) : [];
      }
      return shuffleAndCap(items, CAROUSEL_DISPLAY_LIMIT);
    }
  },

  // Same fallback-to-db.json-and-return-a-boolean pattern as saveBrewingRecipe
  // (see the CLAUDE.md note about save* methods that used to swallow MySQL
  // failures silently — this one used to be one of them). `item.setIds` is
  // the full desired membership list for this slide (checkboxes on the
  // edit form) — memberships not in that list are dropped, new ones are
  // added at the end of that set's own order, and memberships that were
  // already there keep their existing position.
  saveCarouselItem: async (item) => {
    const finalId = item.id || String(Date.now());
    const setIds = Array.isArray(item.setIds) ? item.setIds : (item.setId ? [item.setId] : []);
    try {
      const existing = await query('SELECT id FROM carousel_items WHERE id = ?', [item.id || '']);
      if (existing && existing.length > 0) {
        await query('UPDATE carousel_items SET src = ?, title = ?, desc_text = ? WHERE id = ?', [item.src, item.title, item.desc, item.id]);
        await notifyPlayersOfContent('carousel', 'updated', { id: item.id, title: item.title, link: '/' });
      } else {
        await query('INSERT INTO carousel_items (id, src, title, desc_text) VALUES (?, ?, ?, ?)', [finalId, item.src, item.title, item.desc]);
        await notifyPlayersOfContent('carousel', 'created', { id: finalId, title: item.title, link: '/' });
      }

      const currentRows = await query('SELECT set_id as setId FROM carousel_item_sets WHERE item_id = ?', [finalId]);
      const currentSetIds = new Set((currentRows || []).map(r => r.setId));
      const wantedSetIds = new Set(setIds);

      const toRemove = [...currentSetIds].filter(s => !wantedSetIds.has(s));
      if (toRemove.length > 0) {
        await query(`DELETE FROM carousel_item_sets WHERE item_id = ? AND set_id IN (${toRemove.map(() => '?').join(',')})`, [finalId, ...toRemove]);
      }

      const toAdd = setIds.filter(s => !currentSetIds.has(s));
      for (const setId of toAdd) {
        // New membership lands at the end of that set's own order, same
        // "append after the current max" reasoning as wiki_pages' insert.
        const maxOrderRows = await query('SELECT MAX(sort_order) as maxOrder FROM carousel_item_sets WHERE set_id = ?', [setId]);
        const nextOrder = (maxOrderRows?.[0]?.maxOrder ?? -1) + 1;
        await query('INSERT INTO carousel_item_sets (item_id, set_id, sort_order) VALUES (?, ?, ?)', [finalId, setId, nextOrder]);
      }

      return true;
    } catch (err) {
      console.error('Failed to save carousel item in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.carousel_items = fb.carousel_items || [];
      const idx = fb.carousel_items.findIndex(c => c.id === item.id);
      if (idx >= 0) {
        fb.carousel_items[idx] = { ...fb.carousel_items[idx], src: item.src, title: item.title, desc: item.desc };
      } else {
        fb.carousel_items.push({ id: finalId, src: item.src, title: item.title, desc: item.desc });
      }

      const memberships = migrateFallbackCarouselMemberships(fb);
      const currentSetIds = new Set(memberships.filter(m => m.itemId === finalId).map(m => m.setId));
      const wantedSetIds = new Set(setIds);
      fb.carousel_item_sets = memberships.filter(m => m.itemId !== finalId || wantedSetIds.has(m.setId));
      setIds.filter(s => !currentSetIds.has(s)).forEach(setId => {
        const siblingOrders = fb.carousel_item_sets.filter(m => m.setId === setId).map(m => m.sortOrder ?? 0);
        const nextOrder = siblingOrders.length ? Math.max(...siblingOrders) + 1 : 0;
        fb.carousel_item_sets.push({ itemId: finalId, setId, sortOrder: nextOrder });
      });

      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save carousel item in db.json fallback:', fbErr);
      return false;
    }
  },

  // Removes a slide from exactly one set — the normal "Delete" button on a
  // slide card, which now means "take it out of the set I'm looking at",
  // not "destroy it everywhere" (it may still be shown in other sets). The
  // underlying image only actually gets deleted once this was its last
  // remaining membership (a true orphan).
  removeCarouselItemFromSet: async (itemId, setId) => {
    try {
      await query('DELETE FROM carousel_item_sets WHERE item_id = ? AND set_id = ?', [itemId, setId]);
      const remaining = await query('SELECT 1 FROM carousel_item_sets WHERE item_id = ? LIMIT 1', [itemId]);
      if (!remaining || remaining.length === 0) {
        await query('DELETE FROM carousel_items WHERE id = ?', [itemId]);
      }
      return true;
    } catch (err) {
      console.error('Failed to remove carousel item from set in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const memberships = migrateFallbackCarouselMemberships(fb);
      fb.carousel_item_sets = memberships.filter(m => !(m.itemId === itemId && m.setId === setId));
      const stillMember = fb.carousel_item_sets.some(m => m.itemId === itemId);
      if (!stillMember && fb.carousel_items) fb.carousel_items = fb.carousel_items.filter(c => c.id !== itemId);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to remove carousel item from set in db.json fallback:', fbErr);
      return false;
    }
  },

  // Deletes a slide outright, everywhere it's used — not exposed in the
  // Carousel Manager UI (which only offers the per-set removeCarouselItemFromSet
  // above), kept for completeness / potential future "delete everywhere" action.
  deleteCarouselItem: async (id) => {
    let mysqlOk = false;
    try {
      const [doomed] = await query('SELECT title FROM carousel_items WHERE id = ?', [id]);
      await query('DELETE FROM carousel_item_sets WHERE item_id = ?', [id]);
      await query('DELETE FROM carousel_items WHERE id = ?', [id]);
      mysqlOk = true;
      if (doomed) await notifyPlayersOfContent('carousel', 'deleted', { id, title: doomed.title, link: '/' });
    } catch (err) {
      console.error('Failed to delete carousel item in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      const memberships = migrateFallbackCarouselMemberships(fb);
      fb.carousel_item_sets = memberships.filter(m => m.itemId !== id);
      if (fb.carousel_items) fb.carousel_items = fb.carousel_items.filter(c => c.id !== id);
      writeFallbackJson(fb);
    }
  },

  // POLLS
  // UPDATES
  getUpdates: async () => {
    try {
      return await query('SELECT id, title, category, date, author, content FROM updates ORDER BY date DESC');
    } catch {
      const fb = readFallbackJson();
      return fb.updates || [];
    }
  },

  saveUpdate: async (update) => {
    let notifyMessage = null;
    try {
      const existing = await query('SELECT id FROM updates WHERE id = ?', [update.id || '']);
      if (existing && existing.length > 0) {
        await query(
          'UPDATE updates SET title = ?, category = ?, author = ?, content = ?, date = NOW() WHERE id = ?',
          [update.title, update.category, update.author, update.content, update.id]
        );
        notifyMessage = `Content update revised: "${update.title}" by ${update.author || 'Staff'}`;
      } else {
        const newId = update.id || String(Date.now());
        await query(
          'INSERT INTO updates (id, title, category, date, author, content) VALUES (?, ?, ?, NOW(), ?, ?)',
          [newId, update.title, update.category, update.author, update.content]
        );
        notifyMessage = `New update published: "${update.title}" by ${update.author || 'Staff'}`;
      }
    } catch (err) {
      console.error('Failed to save update in MySQL:', err);
      return;
    }
    // Site updates are player-facing news, not a staff-only content-
    // management concern — broadcast to every registered user's personal
    // bell instead of the staff-only `notifications` table (see that
    // table's remaining use, wiki edits, for the actual staff-internal
    // case). Deliberately outside the try/catch above so a save that
    // already succeeded never gets reported as failed by this.
    await broadcastUserNotification('updates', notifyMessage, '/updates');
  },

  // Same false-success bug class CLAUDE.md documents elsewhere (see the
  // Reports Inbox trash-icon fix): a MySQL DELETE matching 0 rows doesn't
  // throw, so without checking affectedRows this used to report success
  // back to the Staff Panel while a seeded/hardcoded update row (present
  // in the DB, but for whatever reason not matched — id type mismatch,
  // stale seed data, etc.) silently stayed put. Real live bug, 2026-09-10:
  // staff couldn't delete two placeholder updates seeded at initial setup.
  deleteUpdate: async (id) => {
    try {
      const result = await query('DELETE FROM updates WHERE id = ?', [id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to delete update in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (!fb.updates || !fb.updates.some(u => String(u.id) === String(id))) return false;
    fb.updates = fb.updates.filter(u => String(u.id) !== String(id));
    writeFallbackJson(fb);
    return true;
  },

  getUpdateCategories: async () => {
    try {
      const rows = await query('SELECT name FROM update_categories');
      if (rows && rows.length > 0) return rows.map(r => r.name);
    } catch { /* fallback */ }
    return ["General", "Fixes", "Survival", "Skyblock", "Staff"];
  },

  addUpdateCategory: async (category) => {
    try {
      await query('INSERT IGNORE INTO update_categories (name) VALUES (?)', [category]);
    } catch (err) {
      console.error('Failed to add category in MySQL:', err);
    }
  },

  deleteUpdateCategory: async (category) => {
    try {
      await query('DELETE FROM update_categories WHERE name = ?', [category]);
      await query('UPDATE updates SET category = "General" WHERE category = ?', [category]);
    } catch (err) {
      console.error('Failed to delete category in MySQL:', err);
    }
  },

  // NEWS & UPCOMING
  // Player-facing announcements shown on /news, distinct from the Updates
  // changelog above: posts are grouped under a staff-managed topic
  // (news_topics) and a player opts into following a topic
  // (news_topic_follows) to get a bell notification only for posts under
  // it — see broadcastTopicFollowersNotification, called from
  // saveNewsPost below. `type` is 'news' (already happened, e.g. a real
  // Minecraft/vanilla update) or 'upcoming' (a planned server/website
  // feature), so the public page can show them differently.
  getNewsPosts: async () => {
    try {
      return await query('SELECT id, title, type, topic, content, author, link, created_at as createdAt FROM news_posts ORDER BY created_at DESC');
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.news_posts || [];
  },

  // Follows the fallback-to-db.json-and-return-a-boolean pattern (see
  // saveBrewingRecipe) rather than saveUpdate's older silent-swallow one,
  // so the Staff Panel can actually tell whether this landed.
  saveNewsPost: async (post) => {
    const { id, title, content, author } = post;
    const type = post.type === 'upcoming' ? 'upcoming' : 'news';
    const topic = post.topic || 'General';
    const link = post.link ? post.link.trim() || null : null;
    const finalId = id || String(Date.now());
    let isNew = true;
    let persisted = false;
    try {
      const existing = await query('SELECT id FROM news_posts WHERE id = ?', [id || '']);
      if (existing && existing.length > 0) {
        isNew = false;
        await query('UPDATE news_posts SET title = ?, type = ?, topic = ?, content = ?, author = ?, link = ? WHERE id = ?', [title, type, topic, content, author, link, id]);
      } else {
        await query('INSERT INTO news_posts (id, title, type, topic, content, author, link) VALUES (?, ?, ?, ?, ?, ?, ?)', [finalId, title, type, topic, content, author, link]);
      }
      persisted = true;
    } catch (err) {
      console.error('Failed to save news post in MySQL:', err);
    }

    if (!persisted) {
      try {
        const fb = readFallbackJson();
        fb.news_posts = fb.news_posts || [];
        const idx = fb.news_posts.findIndex(p => String(p.id) === String(id));
        isNew = idx < 0;
        const saved = { id: finalId, title, type, topic, content, author, link, createdAt: new Date().toISOString() };
        if (idx >= 0) fb.news_posts[idx] = saved; else fb.news_posts.push(saved);
        writeFallbackJson(fb);
        persisted = true;
      } catch (fbErr) {
        console.error('Failed to save news post in db.json fallback:', fbErr);
        return false;
      }
    }

    // Only followers of this post's topic get pinged — deliberately outside
    // the try/catch above so a save that already succeeded is never
    // reported as failed by a notification hiccup (same reasoning as
    // saveUpdate's own broadcastUserNotification call).
    const verb = isNew ? (type === 'upcoming' ? 'New upcoming feature announced' : 'New Minecraft news posted') : 'Post updated';
    await broadcastTopicFollowersNotification(topic, `${verb}: "${title}"`, '/news');
    return persisted;
  },

  // Same false-success bug class CLAUDE.md documents elsewhere (Updates,
  // Reports Inbox) — check affectedRows rather than assume a MySQL DELETE
  // that matched 0 rows means something went wrong.
  deleteNewsPost: async (id) => {
    try {
      const result = await query('DELETE FROM news_posts WHERE id = ?', [id]);
      if (result.affectedRows > 0) return true;
    } catch (err) {
      console.error('Failed to delete news post in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (!fb.news_posts || !fb.news_posts.some(p => String(p.id) === String(id))) return false;
    fb.news_posts = fb.news_posts.filter(p => String(p.id) !== String(id));
    writeFallbackJson(fb);
    return true;
  },

  getNewsTopics: async () => {
    try {
      const rows = await query('SELECT name FROM news_topics');
      if (rows && rows.length > 0) return rows.map(r => r.name);
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    if (fb.news_topics && fb.news_topics.length > 0) return fb.news_topics.map(t => t.name || t);
    return ["General", "Minecraft News", "Server Updates", "Website Features", "Events"];
  },

  addNewsTopic: async (name) => {
    try {
      await query('INSERT IGNORE INTO news_topics (name) VALUES (?)', [name]);
      return;
    } catch (err) {
      console.error('Failed to add news topic in MySQL:', err);
    }
    const fb = readFallbackJson();
    fb.news_topics = fb.news_topics || [];
    if (!fb.news_topics.some(t => (t.name || t) === name)) fb.news_topics.push({ name });
    writeFallbackJson(fb);
  },

  deleteNewsTopic: async (name) => {
    try {
      await query('DELETE FROM news_topics WHERE name = ?', [name]);
      await query('UPDATE news_posts SET topic = "General" WHERE topic = ?', [name]);
      await query('DELETE FROM news_topic_follows WHERE topic = ?', [name]);
      return;
    } catch (err) {
      console.error('Failed to delete news topic in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (fb.news_topics) fb.news_topics = fb.news_topics.filter(t => (t.name || t) !== name);
    if (fb.news_posts) fb.news_posts.forEach(p => { if (p.topic === name) p.topic = 'General'; });
    if (fb.news_topic_follows) fb.news_topic_follows = fb.news_topic_follows.filter(f => f.topic !== name);
    writeFallbackJson(fb);
  },

  // Player-facing subscription list — see src/app/api/news/follow/route.js,
  // which is session-verified (never a client-sent email) unlike some of
  // the older role-trusting routes CLAUDE.md warns about.
  getFollowedNewsTopics: async (email) => {
    try {
      const rows = await query('SELECT topic FROM news_topic_follows WHERE user_email = ?', [email]);
      return rows.map(r => r.topic);
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    const lower = email.toLowerCase();
    return (fb.news_topic_follows || []).filter(f => (f.userEmail || '').toLowerCase() === lower).map(f => f.topic);
  },

  followNewsTopic: async (email, topic) => {
    try {
      await query('INSERT IGNORE INTO news_topic_follows (user_email, topic) VALUES (?, ?)', [email, topic]);
      return true;
    } catch (err) {
      console.error('Failed to follow news topic in MySQL:', err);
    }
    const fb = readFallbackJson();
    fb.news_topic_follows = fb.news_topic_follows || [];
    if (!fb.news_topic_follows.some(f => f.userEmail === email && f.topic === topic)) {
      fb.news_topic_follows.push({ userEmail: email, topic });
    }
    writeFallbackJson(fb);
    return true;
  },

  unfollowNewsTopic: async (email, topic) => {
    try {
      await query('DELETE FROM news_topic_follows WHERE user_email = ? AND topic = ?', [email, topic]);
      return true;
    } catch (err) {
      console.error('Failed to unfollow news topic in MySQL:', err);
    }
    const fb = readFallbackJson();
    if (fb.news_topic_follows) {
      fb.news_topic_follows = fb.news_topic_follows.filter(f => !(f.userEmail === email && f.topic === topic));
    }
    writeFallbackJson(fb);
    return true;
  },

  // SETTINGS
  // java_version defaults to "25" — the actual Java runtime players need,
  // not to be confused with the Paper build number (26.1.2), which is a
  // separate thing surfaced by api/status.js. An earlier session had these
  // two mixed up (the Paper number was used as the Java-version default),
  // corrected 2026-08-13 per a live user correction.
  getSettings: async () => {
    // server_address is the public join IP:port shown on the homepage,
    // footer, registration flow, and used to query mcsrvstat.us for live
    // status — staff-editable (General Settings) rather than hardcoded in
    // every one of those places, after the server moved to a new host
    // (104.243.32.156:2201) and every hardcoded copy had to be tracked down
    // by hand. This default is only the seed value for a fresh table row —
    // once staff save a value it's read from MySQL/db.json like any other
    // setting.
    const DEFAULTS = { java_version: "25", map_url: "", server_address: "play.iekcorbar.com", donate_paypal_url: "" };
    try {
      const rows = await query('SELECT setting_key, setting_value FROM settings');
      const settingsObj = { ...DEFAULTS };
      for (const row of rows) {
        settingsObj[row.setting_key] = row.setting_value;
      }
      return settingsObj;
    } catch {
      const fb = readFallbackJson();
      return { ...DEFAULTS, ...(fb.settings || {}) };
    }
  },

  // Fallback-to-db.json-and-return-a-boolean pattern (see addUser) rather
  // than the older silent-swallow style — added when the wiki category-order
  // widget started relying on this to actually persist locally without a DB.
  saveSettings: async (settings) => {
    try {
      for (const [key, val] of Object.entries(settings)) {
        await query(
          'INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?',
          [key, String(val), String(val)]
        );
      }
      return true;
    } catch (err) {
      console.error('Failed to save settings in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const stringified = Object.fromEntries(Object.entries(settings).map(([k, v]) => [k, String(v)]));
      fb.settings = { ...(fb.settings || {}), ...stringified };
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save settings in db.json fallback:', fbErr);
      return false;
    }
  },

  // Ingredient Hunt "junk"/curio pool (2026-09-05) — collectible-only
  // ingredient names that never gate any recipe (getRecipeIngredientNames
  // only ever reads from real recipe steps, so these can't unlock anything
  // by construction), purely so there's still something new to find once a
  // player already holds every real ingredient. One more string in the
  // generic settings store, same pattern as wiki_category_order.
  getIngredientHuntJunkItems: async () => {
    const settings = await db.getSettings();
    if (!settings.ingredient_hunt_junk_items) return [];
    try {
      return JSON.parse(settings.ingredient_hunt_junk_items);
    } catch {
      return [];
    }
  },

  saveIngredientHuntJunkItems: async (items) => {
    return db.saveSettings({ ingredient_hunt_junk_items: JSON.stringify(items) });
  },

  // Staff manual override of today's hunt target — { date, page, ingredientName }.
  // Only ever consulted for the exact `date` it was saved for (see
  // getEffectiveHuntTarget), so a forgotten override just stops applying on
  // its own the next day rather than needing to be actively cleared.
  getIngredientHuntOverride: async () => {
    const settings = await db.getSettings();
    if (!settings.ingredient_hunt_override) return null;
    try {
      return JSON.parse(settings.ingredient_hunt_override);
    } catch {
      return null;
    }
  },

  saveIngredientHuntOverride: async (override) => {
    return db.saveSettings({ ingredient_hunt_override: JSON.stringify(override) });
  },

  clearIngredientHuntOverride: async () => {
    return db.saveSettings({ ingredient_hunt_override: '' });
  },

  // SITE CONTENT — admin-editable text labels (role display names, a couple
  // of page titles). Defaults live in src/lib/site-content.js; a row here
  // only exists once a key has actually been edited, same "seed only what's
  // been touched" spirit as `settings`.
  getSiteContent: async () => {
    const merged = siteContentDefaultsMap();
    try {
      const rows = await query('SELECT content_key, content_value FROM site_content');
      for (const row of rows) merged[row.content_key] = row.content_value;
      return merged;
    } catch {
      const fb = readFallbackJson();
      return { ...merged, ...(fb.site_content || {}) };
    }
  },

  // `entries` is a flat { key: value } object. Returns true if the write
  // landed somewhere (MySQL, or db.json if MySQL is unreachable).
  saveSiteContent: async (entries) => {
    try {
      for (const [key, val] of Object.entries(entries)) {
        const meta = siteContentMetaFor(key);
        await query(
          'INSERT INTO site_content (content_key, content_value, label, category) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE content_value = ?',
          [key, String(val), meta ? meta.label : key, meta ? meta.category : 'general', String(val)]
        );
      }
      const labels = Object.keys(entries).map(k => siteContentMetaFor(k)?.label || k);
      if (labels.length > 0) {
        await notifyPlayersOfContent('site_content', 'updated', { id: 'site', title: labels.slice(0, 3).join(', ') + (labels.length > 3 ? ` +${labels.length - 3}` : ''), link: '/' });
      }
      return true;
    } catch (err) {
      console.error('Failed to save site content in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.site_content = { ...(fb.site_content || {}), ...entries };
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save site content in db.json fallback:', fbErr);
      return false;
    }
  },

  // STARTER GUIDE
  getGuide: async () => {
    try {
      return await query('SELECT id, title, content, display_order as `order` FROM starter_guide ORDER BY display_order ASC');
    } catch {
      const fb = readFallbackJson();
      return (fb.starter_guide || []).sort((a, b) => a.order - b.order);
    }
  },

  saveGuideStep: async (step) => {
    try {
      const existing = await query('SELECT id FROM starter_guide WHERE id = ?', [step.id || '']);
      if (existing && existing.length > 0) {
        await query('UPDATE starter_guide SET title = ?, content = ?, display_order = ? WHERE id = ?', [step.title, step.content, step.order || 0, step.id]);
      } else {
        const newId = step.id || String(Date.now());
        await query('INSERT INTO starter_guide (id, title, content, display_order) VALUES (?, ?, ?, ?)', [newId, step.title, step.content, step.order || 0]);
      }
    } catch (err) {
      console.error('Failed to save guide step in MySQL:', err);
    }
  },

  deleteGuideStep: async (id) => {
    try {
      await query('DELETE FROM starter_guide WHERE id = ?', [id]);
    } catch (err) {
      console.error('Failed to delete guide step in MySQL:', err);
    }
  },

  // RULES
  getRules: async () => {
    try {
      return await query('SELECT id, title, content, display_order as `order` FROM rules ORDER BY display_order ASC');
    } catch {
      const fb = readFallbackJson();
      return (fb.rules || []).sort((a, b) => a.order - b.order);
    }
  },

  saveRule: async (rule) => {
    try {
      const existing = await query('SELECT id FROM rules WHERE id = ?', [rule.id || '']);
      if (existing && existing.length > 0) {
        await query('UPDATE rules SET title = ?, content = ?, display_order = ? WHERE id = ?', [rule.title, rule.content, rule.order || 0, rule.id]);
      } else {
        const newId = rule.id || String(Date.now());
        await query('INSERT INTO rules (id, title, content, display_order) VALUES (?, ?, ?, ?)', [newId, rule.title, rule.content, rule.order || 0]);
      }
    } catch (err) {
      console.error('Failed to save rule in MySQL:', err);
    }
  },

  deleteRule: async (id) => {
    try {
      await query('DELETE FROM rules WHERE id = ?', [id]);
    } catch (err) {
      console.error('Failed to delete rule in MySQL:', err);
    }
  },

  // `rewarded` column still exists in the table but is no longer read or
  // written meaningfully — see api/vote/log/route.js's 2026-08-13 comment:
  // this log is purely an informational click record for the cooldown
  // countdown now, not a reward queue (that was a real double-reward bug,
  // since VoteSpeed grants its own reward natively and for real).
  getVoteLog: async () => {
    try {
      return await query('SELECT id, username, site_name as siteName, timestamp FROM vote_log ORDER BY timestamp DESC');
    } catch {
      const fb = readFallbackJson();
      return fb.vote_log || [];
    }
  },

  // Same fallback-to-db.json-and-return-a-boolean pattern as saveBrewingRecipe
  // (see the CLAUDE.md note about save* methods that used to swallow MySQL
  // failures silently). Note: an older, differently-shaped `vote_log` array
  // may exist in db.json (siteId/votedAt) from before this table existed in
  // MySQL — that's dead data now, this always reads/writes the current
  // shape (id/username/siteName/timestamp).
  logVote: async (log) => {
    const newId = String(Date.now());
    try {
      await query(
        'INSERT INTO vote_log (id, username, site_name, timestamp, rewarded) VALUES (?, ?, ?, NOW(), 1)',
        [newId, log.username, log.siteName]
      );
      return true;
    } catch (err) {
      console.error('Failed to log vote in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.vote_log = fb.vote_log || [];
      fb.vote_log.unshift({ id: newId, username: log.username, siteName: log.siteName, timestamp: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to log vote in db.json fallback:', fbErr);
      return false;
    }
  },

  // markVoteRewarded / getVoteRewardConfig / setVoteRewardConfig removed
  // 2026-08-13 — they backed our own separate, unverified vote-reward
  // dispatch (see api/vote/log/route.js's comment), which was double-
  // rewarding on top of VoteSpeed's own real, verified reward and has been
  // removed entirely per an explicit user decision. The `vote_reward_config`
  // table itself is left in schema.sql/production untouched (no destructive
  // migration) — it's just unused now.

  // COMMUNITY PROJECTS
  getCommunityProjects: async () => {
    await cleanupFinishedContent();
    try {
      return await query(
        'SELECT id, slug, title, category, status, description, max_participants as maxParticipants, created_by as createdBy, created_at as createdAt, updated_at as updatedAt FROM community_projects ORDER BY updated_at DESC'
      );
    } catch {
      const fb = readFallbackJson();
      return fb.community_projects || [];
    }
  },

  // Same fallback-to-db.json-and-return-a-boolean pattern as saveBrewingRecipe
  // (see the CLAUDE.md note about save* methods that used to swallow MySQL
  // failures silently — this one used to be one of them, and it's the actual
  // cause of a real bug: a staff edit raising max_participants above its
  // schema DEFAULT of 10 would silently fail to persist on a flaky MySQL
  // write, the API route returned {success:true} regardless, and the next
  // read just kept showing 10 — looked like a hardcoded cap but wasn't).
  saveCommunityProject: async (project) => {
    const finalId = project.id || String(Date.now());
    try {
      const existing = await query('SELECT id FROM community_projects WHERE id = ? OR slug = ?', [project.id || '', project.slug || '']);
      if (existing && existing.length > 0) {
        await query(
          'UPDATE community_projects SET slug = ?, title = ?, category = ?, status = ?, description = ?, max_participants = ?, updated_at = NOW() WHERE id = ?',
          [project.slug, project.title, project.category, project.status || 'open', project.description, project.maxParticipants || 10, existing[0].id]
        );
        await notifyPlayersOfContent('project', 'updated', { id: existing[0].id, title: project.title, link: `/projects?project=${encodeURIComponent(existing[0].id)}` });
      } else {
        await query(
          'INSERT INTO community_projects (id, slug, title, category, status, description, max_participants, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
          [finalId, project.slug, project.title, project.category, project.status || 'open', project.description, project.maxParticipants || 10, project.createdBy]
        );
        await notifyPlayersOfContent('project', 'created', { id: finalId, title: project.title, link: `/projects?project=${encodeURIComponent(finalId)}` });
      }
      return true;
    } catch (err) {
      console.error('Failed to save community project in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.community_projects = fb.community_projects || [];
      const idx = fb.community_projects.findIndex(p => p.id === project.id || p.slug === project.slug);
      const saved = {
        id: finalId,
        slug: project.slug,
        title: project.title,
        category: project.category,
        status: project.status || 'open',
        description: project.description,
        maxParticipants: project.maxParticipants || 10,
        createdBy: project.createdBy,
        createdAt: idx >= 0 ? fb.community_projects[idx].createdAt : new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      if (idx >= 0) fb.community_projects[idx] = saved;
      else fb.community_projects.push(saved);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save community project in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteCommunityProject: async (id) => {
    let mysqlOk = false;
    try {
      const [doomed] = await query('SELECT title FROM community_projects WHERE id = ?', [id]);
      await query('DELETE FROM project_signups WHERE project_id = ?', [id]);
      await query('DELETE FROM community_projects WHERE id = ?', [id]);
      mysqlOk = true;
      if (doomed) await notifyPlayersOfContent('project', 'deleted', { id, title: doomed.title, link: '/projects' });
    } catch (err) {
      console.error('Failed to delete community project in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      let changed = false;
      if (fb.community_projects) { fb.community_projects = fb.community_projects.filter(p => p.id !== id); changed = true; }
      if (fb.project_signups) { fb.project_signups = fb.project_signups.filter(s => s.projectId !== id); changed = true; }
      if (changed) writeFallbackJson(fb);
    }
  },

  getProjectSignups: async () => {
    try {
      return await query('SELECT id, project_id as projectId, username, signed_up_at as signedUpAt FROM project_signups');
    } catch {
      const fb = readFallbackJson();
      return fb.project_signups || [];
    }
  },

  signUpForProject: async (projectId, username) => {
    try {
      const existing = await query('SELECT id FROM project_signups WHERE project_id = ? AND username = ?', [projectId, username]);
      if (!existing || existing.length === 0) {
        const newId = String(Date.now());
        await query('INSERT INTO project_signups (id, project_id, username) VALUES (?, ?, ?)', [newId, projectId, username]);
      }
    } catch (err) {
      console.error('Failed to sign up for project in MySQL:', err);
    }
  },

  withdrawFromProject: async (projectId, username) => {
    try {
      await query('DELETE FROM project_signups WHERE project_id = ? AND username = ?', [projectId, username]);
    } catch (err) {
      console.error('Failed to withdraw from project in MySQL:', err);
    }
  },

  // NOTIFICATIONS
  // Staff notification bell (see providers.js's StaffNotificationBell) — the
  // table and its read/dismiss/clear methods have existed for a while, but
  // nothing in the app ever actually wrote a row into it (same "plumbing
  // exists, no trigger wired up" pattern CLAUDE.md already flags for mobile
  // push). First real writer, added 2026-09-08 for new player reports (see
  // api/reports' POST) — reusable for any future staff-facing event.
  createNotification: async ({ type, message, link }) => {
    queueBellPush({ staff: true, type, message, link });
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    try {
      await query('INSERT INTO notifications (id, type, message, link) VALUES (?, ?, ?, ?)', [id, type, message, link || null]);
      return true;
    } catch (err) {
      console.error('Failed to create notification in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.notifications = fb.notifications || [];
      fb.notifications.unshift({ id, createdAt: new Date().toISOString(), read: false, type, message, link: link || null });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to create notification in db.json fallback:', fbErr);
      return false;
    }
  },

  getNotifications: async () => {
    let mysqlOk = false;
    try {
      // Return MySQL result even when empty — MySQL is authoritative when reachable
      const rows = await query('SELECT id, created_at as createdAt, is_read as `read`, type, message, link FROM notifications ORDER BY created_at DESC LIMIT 50');
      mysqlOk = true;
      return rows || [];
    } catch { /* MySQL unreachable — fall through to db.json */ }
    if (mysqlOk) return [];
    const fb = readFallbackJson();
    return fb.notifications || [];
  },

  dismissNotification: async (id) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM notifications WHERE id = ?', [id]);
      mysqlOk = true;
    } catch { /* fallback */ }
    // Only touch db.json if MySQL is unreachable
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.notifications) {
        fb.notifications = fb.notifications.filter(n => String(n.id) !== String(id));
        writeFallbackJson(fb);
      }
    }
  },

  markNotificationRead: async (id) => {
    try {
      await query('UPDATE notifications SET is_read = 1 WHERE id = ?', [id]);
    } catch (err) {
      console.error('Failed to mark notification read in MySQL:', err);
    }
  },

  clearAllNotifications: async () => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM notifications');
      mysqlOk = true;
    } catch { /* fallback */ }
    // Only touch db.json if MySQL is unreachable
    if (!mysqlOk) {
      const fb = readFallbackJson();
      fb.notifications = [];
      writeFallbackJson(fb);
    }
  },

  clearNotifications: async () => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM notifications');
      mysqlOk = true;
    } catch { /* fallback */ }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      fb.notifications = [];
      writeFallbackJson(fb);
    }
  },

  // PUSH PREFERENCES (Android app) — per-user opt-outs for push
  // notifications, managed on the Profile page (api/push/preferences).
  // Only affects pushes: the notification bell still records everything.
  // Stored as one JSON blob per user; a missing row means "all enabled".
  // The table is created lazily on first use so no manual migration is
  // needed on the server (also listed in schema.sql).
  getPushPreferencesFor: async (emails) => {
    const map = new Map();
    const list = [...new Set((emails || []).filter(Boolean).map(e => e.toLowerCase()))];
    if (list.length === 0) return map;
    try {
      await ensurePushPreferencesTable();
      const rows = await query(
        `SELECT user_email as email, prefs_json as prefs FROM push_preferences WHERE LOWER(user_email) IN (${list.map(() => '?').join(',')})`,
        list
      );
      for (const r of rows || []) {
        try { map.set(r.email.toLowerCase(), JSON.parse(r.prefs)); } catch { /* corrupt row = defaults */ }
      }
      return map;
    } catch { /* MySQL unreachable — fall through to db.json */ }
    const fb = readFallbackJson();
    for (const [email, prefs] of Object.entries(fb.push_preferences || {})) {
      if (list.includes(email.toLowerCase())) map.set(email.toLowerCase(), prefs);
    }
    return map;
  },

  savePushPreferences: async (email, prefs) => {
    const json = JSON.stringify(prefs);
    try {
      await ensurePushPreferencesTable();
      await query(
        'INSERT INTO push_preferences (user_email, prefs_json) VALUES (?, ?) ON DUPLICATE KEY UPDATE prefs_json = ?',
        [email, json, json]
      );
      return true;
    } catch (err) {
      console.error('Failed to save push preferences in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.push_preferences = { ...(fb.push_preferences || {}), [email.toLowerCase()]: prefs };
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save push preferences in db.json fallback:', fbErr);
      return false;
    }
  },

  // WEB PUSH SUBSCRIPTIONS — see ensureWebPushTable above.
  getWebPushSubscriptions: async (userEmail) => {
    try {
      await ensureWebPushTable();
      const rows = await query('SELECT subscription_json as json FROM web_push_subscriptions WHERE LOWER(user_email) = ?', [String(userEmail).toLowerCase()]);
      return (rows || []).map(r => { try { return JSON.parse(r.json); } catch { return null; } }).filter(Boolean);
    } catch { /* MySQL unreachable — fall through to db.json */ }
    const fb = readFallbackJson();
    return (fb.web_push_subscriptions || [])
      .filter(s => (s.userEmail || '').toLowerCase() === String(userEmail).toLowerCase())
      .map(s => s.subscription);
  },

  saveWebPushSubscription: async ({ userEmail, subscription, userAgent }) => {
    const id = webPushId(subscription.endpoint);
    const json = JSON.stringify(subscription);
    const ua = userAgent ? String(userAgent).slice(0, 255) : null;
    try {
      await ensureWebPushTable();
      await query(
        'INSERT INTO web_push_subscriptions (id, user_email, subscription_json, user_agent) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE user_email = ?, subscription_json = ?, user_agent = ?',
        [id, userEmail, json, ua, userEmail, json, ua]
      );
      return true;
    } catch (err) {
      console.error('Failed to save web push subscription in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.web_push_subscriptions = (fb.web_push_subscriptions || []).filter(s => s.id !== id);
      fb.web_push_subscriptions.push({ id, userEmail, subscription, userAgent: ua });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save web push subscription in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteWebPushSubscription: async (endpoint) => {
    const id = webPushId(endpoint);
    try {
      await ensureWebPushTable();
      await query('DELETE FROM web_push_subscriptions WHERE id = ?', [id]);
      return true;
    } catch (err) {
      console.error('Failed to delete web push subscription in MySQL:', err);
    }
    const fb = readFallbackJson();
    fb.web_push_subscriptions = (fb.web_push_subscriptions || []).filter(s => s.id !== id);
    writeFallbackJson(fb);
    return true;
  },

  // ANALYTICS — first-party usage stats, see src/lib/analytics.js (privacy
  // rules) and api/admin/analytics (Staff Panel → Analytics).
  insertAnalyticsEvent: async ({ type, path, visitorHash, userEmail, platform, appVersion, browser, os, referrerHost }) => {
    try {
      await ensureAnalyticsTable();
      await query(
        'INSERT INTO analytics_events (type, path, visitor_hash, user_email, platform, app_version, browser, os, referrer_host) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [type, path, visitorHash, userEmail, platform, appVersion, browser, os, referrerHost]
      );
    } catch { /* analytics must never break a page — MySQL only, no db.json fallback */ }
  },

  // All aggregates for the Analytics tab. "visits" = unique visitor per day
  // (anonymous visitors can't be linked across days by design).
  getAnalytics: async (days = 30) => {
    await ensureAnalyticsTable();
    const d = Math.min(Math.max(parseInt(days, 10) || 30, 1), 90);
    const since = `created_at >= DATE_SUB(CURDATE(), INTERVAL ${d - 1} DAY)`;
    const visitKey = "CONCAT(DATE(created_at), visitor_hash)";
    const grouped = (col) => query(
      `SELECT ${col} AS label, COUNT(*) AS views, COUNT(DISTINCT ${visitKey}) AS visits
       FROM analytics_events WHERE ${since} AND type = 'pageview' AND ${col} IS NOT NULL
       GROUP BY ${col} ORDER BY visits DESC, views DESC LIMIT 15`
    );

    const [[totals], [today], daily, topPages, platforms, browsers, oses, referrers, appVersions, players] = await Promise.all([
      query(`SELECT
          COALESCE(SUM(type = 'pageview'), 0) AS pageviews,
          COUNT(DISTINCT CASE WHEN type = 'pageview' THEN ${visitKey} END) AS visits,
          COUNT(DISTINCT CASE WHEN type = 'pageview' THEN user_email END) AS activePlayers,
          COUNT(DISTINCT CASE WHEN type = 'pageview' AND platform IN ('android-app', 'iphone-homescreen', 'homescreen-other')
                THEN COALESCE(user_email, ${visitKey}) END) AS appUsers,
          COALESCE(SUM(type = 'apk_download'), 0) AS downloads
        FROM analytics_events WHERE ${since}`),
      query(`SELECT COUNT(DISTINCT visitor_hash) AS visitors, COUNT(*) AS pageviews
        FROM analytics_events WHERE type = 'pageview' AND created_at >= CURDATE()`),
      query(`SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day,
          COALESCE(SUM(type = 'pageview'), 0) AS pageviews,
          COUNT(DISTINCT CASE WHEN type = 'pageview' THEN visitor_hash END) AS visitors,
          COALESCE(SUM(type = 'apk_download'), 0) AS downloads
        FROM analytics_events WHERE ${since} GROUP BY day ORDER BY day`),
      grouped('path'),
      grouped('platform'),
      grouped('browser'),
      grouped('os'),
      grouped('referrer_host'),
      query(`SELECT app_version AS label, COUNT(DISTINCT COALESCE(user_email, visitor_hash)) AS users
        FROM analytics_events WHERE ${since} AND platform = 'android-app' AND app_version IS NOT NULL
        GROUP BY app_version ORDER BY app_version DESC`),
      query(`SELECT user_email AS email, COUNT(*) AS views, MIN(created_at) AS firstSeen, MAX(created_at) AS lastSeen,
          GROUP_CONCAT(DISTINCT platform) AS platforms,
          SUBSTRING_INDEX(GROUP_CONCAT(app_version ORDER BY created_at DESC SEPARATOR ','), ',', 1) AS appVersion
        FROM analytics_events WHERE ${since} AND type = 'pageview' AND user_email IS NOT NULL
        GROUP BY user_email ORDER BY lastSeen DESC`)
    ]);

    const androidDevices = await query(
      "SELECT user_email AS email, COUNT(*) AS devices, MIN(created_at) AS since, MAX(updated_at) AS lastRegistered FROM device_tokens WHERE platform = 'android-fcm' GROUP BY user_email"
    ).catch(() => []);
    const webPush = await query('SELECT user_email AS email, user_agent AS userAgent, created_at AS since FROM web_push_subscriptions').catch(() => []);

    return { days: d, totals, today, daily, topPages, platforms, browsers, oses, referrers, appVersions, players, androidDevices, webPush };
  },

  // ACCOUNT LINKING (new registrations since 2026-09-27) — see
  // api/auth/register, api/account/link and reminders.js runLinkDeadlines.
  // link_status: NULL = active/linked (every pre-existing account),
  // 'pending' = registered, must link before link_deadline,
  // 'on_hold' = didn't link in time: removed from whitelist + kicked until linked.
  getLinkStates: async () => {
    try {
      await ensureLinkColumns();
      return await query('SELECT email, mc_username AS mcUsername, link_status AS status, link_deadline AS deadline FROM users WHERE link_status IS NOT NULL') || [];
    } catch {
      return [];
    }
  },

  getLinkState: async (email) => {
    try {
      await ensureLinkColumns();
      const rows = await query(
        'SELECT email, mc_username AS mcUsername, link_status AS status, link_deadline AS deadline, link_code AS code, link_code_expires AS codeExpires FROM users WHERE LOWER(email) = ?',
        [String(email).toLowerCase()]
      );
      return rows && rows[0] ? rows[0] : null;
    } catch {
      return null;
    }
  },

  // Only the keys present in `changes` are updated (null clears a column).
  updateLinkState: async (email, changes) => {
    const cols = { status: 'link_status', deadline: 'link_deadline', code: 'link_code', codeExpires: 'link_code_expires' };
    const sets = [];
    const params = [];
    for (const [k, col] of Object.entries(cols)) {
      if (k in changes) { sets.push(`${col} = ?`); params.push(changes[k]); }
    }
    if (sets.length === 0) return true;
    try {
      await ensureLinkColumns();
      await query(`UPDATE users SET ${sets.join(', ')} WHERE LOWER(email) = ?`, [...params, String(email).toLowerCase()]);
      return true;
    } catch (err) {
      console.error('Failed to update link state:', err);
      return false;
    }
  },

  // Old-flow leftovers: pending registrations (db.json) kept the plaintext
  // password for their 15-minute window and were never cleaned up after.
  prunePendingRegistrations: async () => {
    try {
      const fb = readFallbackJson();
      const all = fb.pending_registrations || {};
      let changed = false;
      for (const [k, v] of Object.entries(all)) {
        if (!v || !v.expiresAt || v.expiresAt < Date.now()) { delete all[k]; changed = true; }
      }
      if (changed) { fb.pending_registrations = all; writeFallbackJson(fb); }
    } catch { /* best effort */ }
  },

  // DEVICE TOKENS (push notifications)
  // Keyed by email, not a numeric user id — getUsers()/login never expose users.id,
  // the rest of this codebase (roles, IGN, skin) identifies users by email too.
  getDeviceTokens: async (userEmail) => {
    let mysqlOk = false;
    try {
      const rows = await query('SELECT id, user_email as userEmail, platform, expo_push_token as token FROM device_tokens WHERE user_email = ?', [userEmail]);
      mysqlOk = true;
      return rows || [];
    } catch { /* MySQL unreachable — fall through to db.json */ }
    if (mysqlOk) return [];
    const fb = readFallbackJson();
    return (fb.device_tokens || []).filter(t => t.userEmail === userEmail);
  },

  saveDeviceToken: async ({ userEmail, platform, token }) => {
    let mysqlOk = false;
    try {
      const existing = await query('SELECT id FROM device_tokens WHERE expo_push_token = ?', [token]);
      if (existing && existing.length > 0) {
        await query('UPDATE device_tokens SET user_email = ?, platform = ? WHERE expo_push_token = ?', [userEmail, platform, token]);
      } else {
        const newId = String(Date.now());
        await query('INSERT INTO device_tokens (id, user_email, platform, expo_push_token) VALUES (?, ?, ?, ?)', [newId, userEmail, platform, token]);
      }
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to save device token in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      fb.device_tokens = (fb.device_tokens || []).filter(t => t.token !== token);
      fb.device_tokens.push({ id: String(Date.now()), userEmail, platform, token });
      writeFallbackJson(fb);
    }
  },

  deleteDeviceToken: async (token) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM device_tokens WHERE expo_push_token = ?', [token]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to delete device token in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      fb.device_tokens = (fb.device_tokens || []).filter(t => t.token !== token);
      writeFallbackJson(fb);
    }
  },

  // EVENT REGISTRATIONS — this file used to have getRegistrations/
  // registerForEvent/unregisterFromEvent defined twice (see CLAUDE.md's
  // documented dupe-method gotcha, "the second definition silently wins");
  // consolidated into one correct copy here. The dead first copy's
  // registerForEvent had the right shape (INSERT included `id`) but the
  // *active* one that had been winning was `INSERT INTO event_registrations
  // (event_id, username) VALUES (?, ?)` — no `id` column, which on this
  // table's `id VARCHAR(50) PRIMARY KEY` (no default) either fails outright
  // in strict SQL mode, or in non-strict mode succeeds once with id='' and
  // then hits a duplicate-key error on every subsequent registration by
  // *any* player for *any* event — explaining reports that "only one
  // registration works at a time".
  // Event reminders (src/lib/reminders.js): atomically claims one reminder
  // kind for one event. Returns true only for the first caller, so parallel
  // Passenger processes/triggers never send the same reminder twice.
  // MySQL only — without the DB there's no safe way to dedupe, so no reminder.
  claimEventReminder: async (eventId, kind) => {
    try {
      await ensureEventRemindersTable();
      const res = await query('INSERT IGNORE INTO event_reminders_sent (event_id, kind) VALUES (?, ?)', [String(eventId), kind]);
      return !!(res && res.affectedRows === 1);
    } catch (err) {
      console.error('Failed to claim event reminder:', err);
      return false;
    }
  },

  getRegistrations: async () => {
    try {
      return await query('SELECT id, event_id as eventId, username FROM event_registrations');
    } catch {
      const fb = readFallbackJson();
      return fb.event_registrations || [];
    }
  },

  registerForEvent: async (eventId, username) => {
    try {
      const existing = await query('SELECT id FROM event_registrations WHERE event_id = ? AND username = ?', [eventId, username]);
      if (!existing || existing.length === 0) {
        await query('INSERT INTO event_registrations (id, event_id, username) VALUES (?, ?, ?)', [String(Date.now()), eventId, username]);
      }
      return true;
    } catch (err) {
      console.error('Failed to register for event in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.event_registrations = fb.event_registrations || [];
      const exists = fb.event_registrations.some(
        r => String(r.eventId) === String(eventId) && r.username.toLowerCase() === username.toLowerCase()
      );
      if (!exists) {
        fb.event_registrations.push({ id: String(Date.now()), eventId: String(eventId), username });
        writeFallbackJson(fb);
      }
      return true;
    } catch (fbErr) {
      console.error('Failed to register for event in db.json fallback:', fbErr);
      return false;
    }
  },

  unregisterFromEvent: async (eventId, username) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM event_registrations WHERE event_id = ? AND LOWER(username) = LOWER(?)', [eventId, username]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to unregister from event in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.event_registrations) {
        fb.event_registrations = fb.event_registrations.filter(
          r => !(String(r.eventId) === String(eventId) && r.username.toLowerCase() === username.toLowerCase())
        );
        writeFallbackJson(fb);
      }
    }
  },

  // POLLS
  getPolls: async () => {
    try {
      const rows = await query('SELECT id, question, options_json as optionsJson, voters_json as votersJson FROM polls ORDER BY id DESC');
      if (rows && rows.length > 0) {
        return rows.map(r => ({
          id: String(r.id),
          question: r.question,
          options: JSON.parse(r.optionsJson || '[]'),
          voters: JSON.parse(r.votersJson || '[]')
        }));
      }
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.polls || [];
  },

  savePoll: async (poll) => {
    const formattedOptions = (poll.options || []).map(opt => 
      typeof opt === 'string' ? { text: opt, votes: 0 } : opt
    );

    try {
      if (poll.id) {
        await query('UPDATE polls SET question = ?, options_json = ? WHERE id = ?', [poll.question, JSON.stringify(formattedOptions), poll.id]);
        await notifyPlayersOfContent('poll', 'updated', { id: poll.id, title: poll.question, link: '/events' });
      } else {
        const id = String(Date.now());
        await query('INSERT INTO polls (id, question, options_json, voters_json) VALUES (?, ?, ?, ?)', [id, poll.question, JSON.stringify(formattedOptions), '[]']);
        await notifyPlayersOfContent('poll', 'created', { id, title: poll.question, link: '/events' });
      }
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (!fb.polls) fb.polls = [];
    if (poll.id) {
      fb.polls = fb.polls.map(p => {
        if (String(p.id) === String(poll.id)) {
          const updatedOptions = formattedOptions.map(newOpt => {
            const existingOpt = (p.options || []).find(o => o.text === newOpt.text);
            return existingOpt ? { ...newOpt, votes: existingOpt.votes || 0 } : newOpt;
          });
          return { ...p, question: poll.question, options: updatedOptions };
        }
        return p;
      });
    } else {
      const newPoll = {
        id: String(Date.now()),
        question: poll.question,
        options: formattedOptions,
        voters: []
      };
      fb.polls.unshift(newPoll);
    }
    writeFallbackJson(fb);
  },

  deletePoll: async (id) => {
    try {
      const [doomed] = await query('SELECT question FROM polls WHERE id = ?', [id]);
      await query('DELETE FROM polls WHERE id = ?', [id]);
      if (doomed) await notifyPlayersOfContent('poll', 'deleted', { id, title: doomed.question, link: '/events' });
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (fb.polls) {
      fb.polls = fb.polls.filter(p => String(p.id) !== String(id));
      writeFallbackJson(fb);
    }
  },

  // Records a vote in one poll. `voterKeys` is every identity checked
  // against a repeat vote — email + IP (see api/polls/route.js), no
  // Minecraft IGN required. Either key already present in the poll's
  // voters blocks the vote; a new vote records both. Previously this
  // method only ever touched db.json and never reached MySQL at all —
  // votes never actually landed in the real `polls` table in production,
  // while getPolls reads straight from MySQL, so a vote could "succeed"
  // client-side and still never visibly change anything. Now hits MySQL
  // first like every other poll method, with the same fallback-and-
  // return-result pattern as the rest of this file.
  voteInPoll: async (pollId, optionIndex, voterKeys) => {
    try {
      const rows = await query('SELECT options_json as optionsJson, voters_json as votersJson FROM polls WHERE id = ?', [pollId]);
      if (!rows || rows.length === 0) return { success: false, alreadyVoted: false };
      const options = JSON.parse(rows[0].optionsJson || '[]');
      const voters = JSON.parse(rows[0].votersJson || '[]');

      if (voterKeys.some(k => voters.includes(k))) {
        return { success: true, alreadyVoted: true };
      }
      if (options[optionIndex]) {
        options[optionIndex].votes = (options[optionIndex].votes || 0) + 1;
      }
      await query(
        'UPDATE polls SET options_json = ?, voters_json = ? WHERE id = ?',
        [JSON.stringify(options), JSON.stringify([...voters, ...voterKeys]), pollId]
      );
      return { success: true, alreadyVoted: false };
    } catch (err) {
      console.error('Failed to vote in poll in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      let alreadyVoted = false;
      let found = false;
      fb.polls = (fb.polls || []).map(p => {
        if (String(p.id) !== String(pollId)) return p;
        found = true;
        const voters = p.voters || [];
        if (voterKeys.some(k => voters.includes(k))) {
          alreadyVoted = true;
          return p;
        }
        const options = (p.options || []).map((opt, idx) => idx === optionIndex ? { ...opt, votes: (opt.votes || 0) + 1 } : opt);
        return { ...p, options, voters: [...voters, ...voterKeys] };
      });
      if (!found) return { success: false, alreadyVoted: false };
      writeFallbackJson(fb);
      return { success: true, alreadyVoted };
    } catch (fbErr) {
      console.error('Failed to vote in poll in db.json fallback:', fbErr);
      return { success: false, alreadyVoted: false };
    }
  },

  // RULES
  getRules: async () => {
    try {
      const rows = await query('SELECT id, title, content, display_order as `order` FROM rules ORDER BY display_order ASC, id ASC');
      if (rows && rows.length > 0) return rows;
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.rules || [];
  },

  saveRule: async (rule) => {
    try {
      if (rule.id) {
        await query('UPDATE rules SET title = ?, content = ? WHERE id = ?', [rule.title, rule.content, rule.id]);
        await notifyPlayersOfContent('rules', 'updated', { id: rule.id, title: rule.title, link: '/rules' });
      } else {
        const id = String(Date.now());
        await query('INSERT INTO rules (id, title, content) VALUES (?, ?, ?)', [id, rule.title, rule.content]);
        await notifyPlayersOfContent('rules', 'created', { id, title: rule.title, link: '/rules' });
      }
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (!fb.rules) fb.rules = [];
    if (rule.id) {
      fb.rules = fb.rules.map(r => String(r.id) === String(rule.id) ? { ...r, title: rule.title, content: rule.content } : r);
    } else {
      const newRule = {
        id: String(Date.now()),
        title: rule.title,
        content: rule.content,
        order: fb.rules.length + 1
      };
      fb.rules.push(newRule);
    }
    writeFallbackJson(fb);
  },

  deleteRule: async (id) => {
    try {
      const [doomed] = await query('SELECT title FROM rules WHERE id = ?', [id]);
      await query('DELETE FROM rules WHERE id = ?', [id]);
      if (doomed) await notifyPlayersOfContent('rules', 'deleted', { id, title: doomed.title, link: '/rules' });
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (fb.rules) {
      fb.rules = fb.rules.filter(r => String(r.id) !== String(id));
      writeFallbackJson(fb);
    }
  },

  // TIP OF THE DAY — homepage widget + Settings CRUD. Deliberately does NOT
  // pick "today's" tip itself; that's done in api/tips (day-of-year modulo
  // active count) so it stays a pure server-side date calc, not something
  // baked into the data layer.
  getTips: async () => {
    try {
      return await query('SELECT id, content, active, created_at as createdAt FROM daily_tips ORDER BY created_at ASC');
    } catch {
      const fb = readFallbackJson();
      return fb.daily_tips || [];
    }
  },

  saveTip: async (tip) => {
    try {
      if (tip.id) {
        await query('UPDATE daily_tips SET content = ?, active = ? WHERE id = ?', [tip.content, tip.active !== false, tip.id]);
        await notifyPlayersOfContent('tip', 'updated', { id: tip.id, title: shortText(tip.content), link: '/' });
      } else {
        const id = String(Date.now());
        await query('INSERT INTO daily_tips (id, content, active) VALUES (?, ?, ?)', [id, tip.content, tip.active !== false]);
        await notifyPlayersOfContent('tip', 'created', { id, title: shortText(tip.content), link: '/' });
      }
      return true;
    } catch (err) {
      console.error('Failed to save tip in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      if (!fb.daily_tips) fb.daily_tips = [];
      if (tip.id) {
        fb.daily_tips = fb.daily_tips.map(t => String(t.id) === String(tip.id) ? { ...t, content: tip.content, active: tip.active !== false } : t);
      } else {
        fb.daily_tips.push({ id: String(Date.now()), content: tip.content, active: tip.active !== false, createdAt: new Date().toISOString() });
      }
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save tip in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteTip: async (id) => {
    try {
      const [doomed] = await query('SELECT content FROM daily_tips WHERE id = ?', [id]);
      await query('DELETE FROM daily_tips WHERE id = ?', [id]);
      if (doomed) await notifyPlayersOfContent('tip', 'deleted', { id, title: shortText(doomed.content), link: '/' });
      return true;
    } catch (err) {
      console.error('Failed to delete tip in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      if (fb.daily_tips) {
        fb.daily_tips = fb.daily_tips.filter(t => String(t.id) !== String(id));
        writeFallbackJson(fb);
      }
      return true;
    } catch (fbErr) {
      console.error('Failed to delete tip in db.json fallback:', fbErr);
      return false;
    }
  },

  // INGREDIENT HUNT — see src/lib/ingredient-hunt.js for the daily-target
  // logic and api/brewing/hunt for how these get used.
  // Returns AVAILABLE (unconsumed) counts per ingredient — [{name, count}] —
  // not just distinct names, now that a recipe needs an actual quantity of
  // each ingredient (see getRecipeIngredientRequirements) rather than just
  // having found it once.
  getIngredientCollection: async (username) => {
    try {
      const rows = await query(
        'SELECT ingredient_name as name, COUNT(*) as count FROM ingredient_hunt_finds WHERE username = ? AND consumed = 0 GROUP BY ingredient_name',
        [username]
      );
      return (rows || []).map(r => ({ name: r.name, count: Number(r.count) }));
    } catch {
      const fb = readFallbackJson();
      const finds = (fb.ingredient_hunt_finds || []).filter(f => f.username.toLowerCase() === username.toLowerCase() && !f.consumed);
      const counts = {};
      for (const f of finds) counts[f.ingredientName] = (counts[f.ingredientName] || 0) + 1;
      return Object.entries(counts).map(([name, count]) => ({ name, count }));
    }
  },

  // Spends the required quantity of each ingredient when a brew actually
  // starts (see api/brewing/sessions) — marks the OLDEST unconsumed finds
  // of each ingredient as consumed rather than deleting them, so a
  // player's full find history stays visible. Two passes on purpose: fully
  // confirms every requirement is available before marking anything
  // consumed, so a shortfall on ingredient #3 never leaves #1 and #2
  // partially spent. Returns false (and consumes nothing) if any
  // requirement can't be fully met — callers should already have checked
  // via getIngredientCollection, but this re-verifies rather than trusting
  // a possibly-stale read.
  consumeIngredients: async (username, requirements) => {
    try {
      const idsToConsume = [];
      for (const [name, count] of Object.entries(requirements)) {
        const rows = await query(
          'SELECT id FROM ingredient_hunt_finds WHERE username = ? AND ingredient_name = ? AND consumed = 0 ORDER BY found_at ASC LIMIT ?',
          [username, name, count]
        );
        if (!rows || rows.length < count) return false;
        idsToConsume.push(...rows.map(r => r.id));
      }
      if (idsToConsume.length > 0) {
        await query(
          `UPDATE ingredient_hunt_finds SET consumed = 1 WHERE id IN (${idsToConsume.map(() => '?').join(',')})`,
          idsToConsume
        );
      }
      return true;
    } catch (err) {
      console.error('Failed to consume ingredients in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const finds = (fb.ingredient_hunt_finds || []).filter(f => f.username.toLowerCase() === username.toLowerCase() && !f.consumed);
      const idsToConsume = new Set();
      for (const [name, count] of Object.entries(requirements)) {
        const matches = finds
          .filter(f => f.ingredientName === name)
          .sort((a, b) => new Date(a.foundAt) - new Date(b.foundAt));
        if (matches.length < count) return false;
        matches.slice(0, count).forEach(f => idsToConsume.add(f.id));
      }
      (fb.ingredient_hunt_finds || []).forEach(f => { if (idsToConsume.has(f.id)) f.consumed = true; });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to consume ingredients in db.json fallback:', fbErr);
      return false;
    }
  },

  hasClaimedHuntToday: async (username, dateStr) => {
    try {
      const rows = await query('SELECT 1 FROM ingredient_hunt_finds WHERE username = ? AND found_date = ?', [username, dateStr]);
      return Boolean(rows && rows.length > 0);
    } catch {
      const fb = readFallbackJson();
      return (fb.ingredient_hunt_finds || []).some(f => f.username.toLowerCase() === username.toLowerCase() && f.foundDate === dateStr);
    }
  },

  // Returns { success, alreadyClaimed } rather than a plain boolean — the
  // caller (api/brewing/hunt POST) needs to tell "you already found today's"
  // apart from "the database is unreachable" to give an honest response
  // either way, same reasoning as the addUser-style fixes elsewhere.
  claimHuntFind: async (username, ingredientName, dateStr) => {
    try {
      const existing = await query('SELECT 1 FROM ingredient_hunt_finds WHERE username = ? AND found_date = ?', [username, dateStr]);
      if (existing && existing.length > 0) return { success: false, alreadyClaimed: true };
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      await query('INSERT INTO ingredient_hunt_finds (id, username, ingredient_name, found_date) VALUES (?, ?, ?, ?)', [id, username, ingredientName, dateStr]);
      return { success: true };
    } catch (err) {
      console.error('Failed to claim ingredient hunt find in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.ingredient_hunt_finds = fb.ingredient_hunt_finds || [];
      if (fb.ingredient_hunt_finds.some(f => f.username.toLowerCase() === username.toLowerCase() && f.foundDate === dateStr)) {
        return { success: false, alreadyClaimed: true };
      }
      fb.ingredient_hunt_finds.push({
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        username, ingredientName, foundDate: dateStr, foundAt: new Date().toISOString()
      });
      writeFallbackJson(fb);
      return { success: true };
    } catch (fbErr) {
      console.error('Failed to claim ingredient hunt find in db.json fallback:', fbErr);
      return { success: false };
    }
  },

  // STARTER GUIDE
  getGuide: async () => {
    try {
      const rows = await query('SELECT id, title, content, display_order as `order`, attachment_url as attachmentUrl, attachment_name as attachmentName FROM starter_guide ORDER BY display_order ASC, id ASC');
      if (rows && rows.length > 0) return rows;
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.starter_guide || [];
  },

  // `step.attachmentUrl`/`attachmentName` come from the /uploads/guide/
  // runtime upload (see api/guide/route.js) — e.g. a downloadable PDF
  // manual or an image attached to a step. Pass them through as `undefined`
  // (not `null`) to keep whatever attachment already exists on an edit.
  saveGuideStep: async (step) => {
    try {
      if (step.id) {
        await query('UPDATE starter_guide SET title = ?, content = ?, attachment_url = ?, attachment_name = ? WHERE id = ?', [step.title, step.content, step.attachmentUrl ?? null, step.attachmentName ?? null, step.id]);
        await notifyPlayersOfContent('guide', 'updated', { id: step.id, title: step.title, link: '/' });
      } else {
        const id = String(Date.now());
        await query('INSERT INTO starter_guide (id, title, content, attachment_url, attachment_name) VALUES (?, ?, ?, ?, ?)', [id, step.title, step.content, step.attachmentUrl ?? null, step.attachmentName ?? null]);
        await notifyPlayersOfContent('guide', 'created', { id, title: step.title, link: '/' });
      }
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (!fb.starter_guide) fb.starter_guide = [];
    if (step.id) {
      fb.starter_guide = fb.starter_guide.map(s => String(s.id) === String(step.id) ? { ...s, title: step.title, content: step.content, attachmentUrl: step.attachmentUrl ?? null, attachmentName: step.attachmentName ?? null } : s);
    } else {
      const newStep = {
        id: String(Date.now()),
        title: step.title,
        content: step.content,
        attachmentUrl: step.attachmentUrl ?? null,
        attachmentName: step.attachmentName ?? null,
        order: fb.starter_guide.length + 1
      };
      fb.starter_guide.push(newStep);
    }
    writeFallbackJson(fb);
  },

  deleteGuideStep: async (id) => {
    try {
      await query('DELETE FROM starter_guide WHERE id = ?', [id]);
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (fb.starter_guide) {
      fb.starter_guide = fb.starter_guide.filter(s => String(s.id) !== String(id));
      writeFallbackJson(fb);
    }
  },

  // VOTE SITES
  // Real bug fixed 2026-08-13 (user report: "deleting a vote site doesn't
  // work"): `getVoteSites` used to only trust a *successful* MySQL query if
  // it came back with `rows.length > 0` — a genuinely successful "0 sites
  // left" result (e.g. right after deleting the last one) was silently
  // treated the same as a connection failure and papered over with
  // db.json's stale list, so a deleted site could reappear. Also, none of
  // these three ever reported success/failure, so api/vote/sites/route.js
  // always responded `success: true` even when a MySQL write silently
  // failed and the db.json fallback was all that actually changed — same
  // "false success" bug class CLAUDE.md flags for other save* methods.
  // Fixed to the standard fallback-to-db.json-and-return-boolean pattern
  // (see addUser), and to only fall back when MySQL genuinely errored, not
  // just returned an empty-but-valid result.
  getVoteSites: async () => {
    try {
      return await query('SELECT id, name, url, icon FROM vote_sites');
    } catch {
      const fb = readFallbackJson();
      return fb.vote_sites || [];
    }
  },

  saveVoteSite: async (site) => {
    try {
      if (site.id) {
        await query('UPDATE vote_sites SET name = ?, url = ?, icon = ? WHERE id = ?', [site.name, site.url, site.icon, site.id]);
        await notifyPlayersOfContent('vote_site', 'updated', { id: site.id, title: site.name, link: '/vote' });
      } else {
        const id = String(Date.now());
        await query('INSERT INTO vote_sites (id, name, url, icon) VALUES (?, ?, ?, ?)', [id, site.name, site.url, site.icon]);
        await notifyPlayersOfContent('vote_site', 'created', { id, title: site.name, link: '/vote' });
      }
      return true;
    } catch (err) {
      console.error('Failed to save vote site in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      if (!fb.vote_sites) fb.vote_sites = [];
      if (site.id) {
        fb.vote_sites = fb.vote_sites.map(v => String(v.id) === String(site.id) ? { ...v, name: site.name, url: site.url, icon: site.icon } : v);
      } else {
        fb.vote_sites.push({ id: String(Date.now()), name: site.name, url: site.url, icon: site.icon || '🗳️' });
      }
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save vote site in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteVoteSite: async (id) => {
    try {
      const [doomed] = await query('SELECT name FROM vote_sites WHERE id = ?', [id]);
      await query('DELETE FROM vote_sites WHERE id = ?', [id]);
      if (doomed) await notifyPlayersOfContent('vote_site', 'deleted', { id, title: doomed.name, link: '/vote' });
      return true;
    } catch (err) {
      console.error('Failed to delete vote site in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      if (fb.vote_sites) {
        fb.vote_sites = fb.vote_sites.filter(v => String(v.id) !== String(id));
        writeFallbackJson(fb);
      }
      return true;
    } catch (fbErr) {
      console.error('Failed to delete vote site in db.json fallback:', fbErr);
      return false;
    }
  },

  // PLAYER STATS FROM MYSQL
  // Real total across every synced player — backs the homepage Live
  // Broadcast widget's "Claims protected" figure (previously a fabricated
  // sine-wave formula, not actual data). No db.json fallback: player_stats
  // is populated externally by the IekcorbarSiteSync Minecraft plugin, not
  // by any website write path, so there's nothing meaningful to mirror
  // locally — 0 (rather than a fake number) is the honest answer if MySQL
  // is unreachable.
  // 2026-08-13: these four (getTotalLandsClaimed / getTotalActiveJobs /
  // getPlayerStats / getVotifierEventsByUsername) now read from
  // pluginDataQuery (src/lib/plugin-data-db.js) instead of this file's own
  // `query()` — root-caused that the IekcorbarSiteSync plugin's config.yml
  // has always synced into a different RevivenNode database than this
  // website's own DB_HOST, so these tables here were always empty no
  // matter how well the plugin itself was working. See that file's comment
  // for the full story. No db.json fallback (as before) — this is
  // exclusively plugin-populated data with no local mirror to fall back to.
  // 2026-10-26: the Lands plugin is retired — Gouw (plugins/Gouw/claims.json) owns all claims
  // now, and SiteSync writes every owner's chunk count straight into player_stats.lands_claimed
  // (offline owners included), so the old lands_* table reads are gone.
  getTotalLandsClaimed: async () => {
    try {
      const rows = await pluginDataQuery('SELECT SUM(lands_claimed) as total FROM player_stats');
      return (rows && rows[0] && rows[0].total) || 0;
    } catch (err) {
      console.error('Failed to get total claimed chunks from plugin DB:', err);
      return 0;
    }
  },

  // Real count across every synced player's jobs_json — backs the homepage
  // Live Broadcast widget's "Active Jobs" figure (previously a fabricated
  // formula). Counting has to happen in JS since jobs are stored as a JSON
  // array string per row, same as getPlayerStats already does per-player.
  getTotalActiveJobs: async () => {
    try {
      const rows = await pluginDataQuery('SELECT jobs_json as jobsJson FROM player_stats');
      let total = 0;
      for (const row of rows || []) {
        try {
          const jobs = row.jobsJson ? JSON.parse(row.jobsJson) : [];
          if (Array.isArray(jobs)) total += jobs.length;
        } catch { /* skip malformed row */ }
      }
      return total;
    } catch (err) {
      console.error('Failed to get total active jobs from plugin DB:', err);
      return 0;
    }
  },

  // 2026-09-28: EliteCoins come straight from EliteMobs' own PlayerData table
  // (same plugin DB) rather than player_stats.elite_coins / the live
  // `webstats` reply — the plugin's metadata-based read always yielded 0, and
  // this also works for offline players. CurrencyCents is the current
  // format; older rows only have CurrencyV2. null = unknown (not found or
  // DB unreachable), so callers can keep whatever value they already had.
  getEliteCoins: async (username) => {
    try {
      const rows = await pluginDataQuery('SELECT COALESCE(CurrencyCents / 100, CurrencyV2) AS coins FROM PlayerData WHERE LOWER(DisplayName) = LOWER(?) LIMIT 1', [username]);
      if (rows && rows.length > 0 && rows[0].coins != null) return Number(rows[0].coins);
    } catch (err) {
      console.error('Failed to get EliteMobs coins from plugin DB:', err);
    }
    return null;
  },

  // 2026-10-26: Lands is retired — SiteSync keeps player_stats.lands_claimed up to date for
  // every Gouw owner (claims.json on the server, offline owners included), so this is now a
  // plain per-player column read. null = unknown (player has no synced row or DB unreachable).
  getLandsClaimedByPlayer: async (username) => {
    try {
      const rows = await pluginDataQuery('SELECT lands_claimed AS chunks FROM player_stats WHERE LOWER(username) = LOWER(?) LIMIT 1', [username]);
      if (rows && rows.length > 0 && rows[0].chunks != null) return Number(rows[0].chunks);
    } catch (err) {
      console.error('Failed to get claimed chunks from player_stats:', err);
    }
    return null;
  },

  getPlayerStats: async (username) => {
    try {
      const rows = await pluginDataQuery('SELECT username, online, playtime_hours as playtimeHours, kills, deaths, mob_kills as mobKills, blocks_mined as blocksMined, balance, elite_coins as eliteCoins, lands_claimed as landsClaimed, jobs_json as jobsJson, plugin_placeholders_json as pluginStatsJson FROM player_stats WHERE LOWER(username) = LOWER(?)', [username]);
      if (rows && rows.length > 0) {
        const r = rows[0];
        let jobs = [];
        try { if (r.jobsJson) jobs = JSON.parse(r.jobsJson); } catch {}
        // Generic PlaceholderAPI bridge (Lands/ValhallaMMO/ValhallaRaces/...) —
        // see PlaceholderSync.java in IekcorbarSiteSync. Same field name
        // (`pluginStats`) as the live RCON `webstats` reply below, so callers
        // get a consistent shape regardless of which path served the data.
        let pluginStats = {};
        try { if (r.pluginStatsJson) pluginStats = JSON.parse(r.pluginStatsJson); } catch {}
        const [emCoins, landsChunks] = await Promise.all([
          db.getEliteCoins(username),
          db.getLandsClaimedByPlayer(username),
        ]);
        const eliteCoins = emCoins != null ? emCoins : (r.eliteCoins || 0);
        const landsClaimed = landsChunks != null ? landsChunks : (r.landsClaimed || 0);
        return {
          username: r.username,
          online: Boolean(r.online),
          playtimeHours: r.playtimeHours || 0,
          kills: r.kills || 0,
          deaths: r.deaths || 0,
          mobKills: r.mobKills || 0,
          blocksMined: r.blocksMined || 0,
          balance: r.balance || 0,
          eliteCoins: eliteCoins,
          landsClaimed: landsClaimed,
          jobs: jobs,
          pluginStats: pluginStats
        };
      }
    } catch (err) {
      console.error('Failed to get player stats from plugin DB:', err);
    }
    return null;
  },

  // Real, verified votes (written by VotifierListener.java on the plugin
  // side only — the website never writes to this table). No db.json
  // fallback, same reasoning as getTotalLandsClaimed: it's plugin-populated
  // data with no local mirror, so an empty list while unreachable is more
  // honest than pretending to have data.
  getVotifierEventsByUsername: async (username) => {
    try {
      const rows = await pluginDataQuery(
        'SELECT id, username, service_name as serviceName, address, vote_timestamp as voteTimestamp, received_at as receivedAt FROM votifier_events WHERE LOWER(username) = LOWER(?) ORDER BY received_at DESC',
        [username]
      );
      return rows || [];
    } catch (err) {
      console.error('Failed to get votifier events from plugin DB:', err);
      return [];
    }
  },

  // Mirrors of the server's real banned-players.json / whitelist.json (see
  // AccessListSync.java) — plugin-populated only, no db.json fallback, same
  // reasoning as getVotifierEventsByUsername. Staff-panel-only data —
  // deliberately never exposed through /api/members or anywhere the public
  // members list reads from. Now read via pluginDataQuery (see that 2026-
  // 08-13 comment above getTotalLandsClaimed) — these two tables exist in
  // this website's own DB too (same CREATE TABLE the plugin runs), but
  // nothing has ever written to *those* copies since the plugin syncs
  // elsewhere, which is exactly why Access Control showed nothing before.
  getBannedPlayers: async () => {
    try {
      const rows = await pluginDataQuery(
        'SELECT uuid, username, created, source, expires, reason, synced_at as syncedAt FROM banned_players ORDER BY created DESC'
      );
      return rows || [];
    } catch (err) {
      console.error('Failed to get banned players from plugin DB:', err);
      return [];
    }
  },

  getWhitelistedPlayers: async () => {
    try {
      const rows = await pluginDataQuery('SELECT uuid, username, synced_at as syncedAt FROM whitelisted_players ORDER BY username ASC');
      return rows || [];
    } catch (err) {
      console.error('Failed to get whitelisted players from plugin DB:', err);
      return [];
    }
  },

  // 2026-10-27 (Staff Panel > Player Data): one player's combat record, read
  // straight from the plugin DB's own tables — EliteMobs' PlayerData (kills,
  // deaths, score, quests, dungeons, elite coins, per-weapon SkillXP) plus
  // MMOCore's mmocore_playerdata (level, XP, class, guild, unspent points).
  // The elite row is looked up by DisplayName first and by whitelisted_players'
  // uuid second, so a name change or a non-whitelisted account still resolves
  // where it can. uuid formats differ between the tables (dashes or not) — the
  // mmocore lookup strips them on both sides. Read-only like every other
  // pluginDataQuery use; staff-panel data only, never exposed publicly.
  getCombatProfile: async (username) => {
    const name = String(username || '').trim();
    if (!/^[A-Za-z0-9_]{2,16}$/.test(name)) return { name, found: false, error: 'invalid-name' };
    try {
      const [eliteRows, wlRows] = await Promise.all([
        pluginDataQuery('SELECT PlayerUUID AS uuid, DisplayName AS name, Kills AS kills, Deaths AS deaths, Score AS score, QuestsCompleted AS quests, DungeonsCompleted AS dungeons, HighestLevelKilled AS highestMobLevel, COALESCE(CurrencyCents / 100, CurrencyV2) AS coins, SkillXP_ARMOR AS armor, SkillXP_SWORDS AS swords, SkillXP_AXES AS axes, SkillXP_BOWS AS bows, SkillXP_CROSSBOWS AS crossbows, SkillXP_TRIDENTS AS tridents, SkillXP_HOES AS hoes, SkillXP_MACES AS maces, SkillXP_SPEARS AS spears, SkillXP_STAVES AS staves, SkillXP_WANDS AS wands FROM PlayerData WHERE LOWER(DisplayName) = LOWER(?) LIMIT 1', [name]),
        pluginDataQuery('SELECT uuid FROM whitelisted_players WHERE LOWER(username) = LOWER(?) LIMIT 1', [name])
      ]);
      let elite = eliteRows && eliteRows.length ? eliteRows[0] : null;
      let resolvedName = elite ? (elite.name || name) : name;
      const uuid = (elite && elite.uuid) || (wlRows && wlRows.length ? wlRows[0].uuid : null);
      let rpg = null;
      if (uuid) {
        const stripped = String(uuid).replace(/-/g, '');
        const rpgRows = await pluginDataQuery("SELECT level, experience AS xp, class, guild, class_points AS classPoints, skill_points AS skillPoints, attribute_points AS attributePoints FROM mmocore_playerdata WHERE REPLACE(uuid, '-', '') = ? LIMIT 1", [stripped]);
        rpg = rpgRows && rpgRows.length ? rpgRows[0] : null;
      }
      if (!elite && uuid) {
        // name unknown to EliteMobs' DisplayName but the uuid was on the whitelist —
        // one follow-up by uuid so renamed accounts still surface
        const byUuid = await pluginDataQuery("SELECT DisplayName AS name, Kills AS kills, Deaths AS deaths, Score AS score, QuestsCompleted AS quests, DungeonsCompleted AS dungeons, HighestLevelKilled AS highestMobLevel, COALESCE(CurrencyCents / 100, CurrencyV2) AS coins, SkillXP_ARMOR AS armor, SkillXP_SWORDS AS swords, SkillXP_AXES AS axes, SkillXP_BOWS AS bows, SkillXP_CROSSBOWS AS crossbows, SkillXP_TRIDENTS AS tridents, SkillXP_HOES AS hoes, SkillXP_MACES AS maces, SkillXP_SPEARS AS spears, SkillXP_STAVES AS staves, SkillXP_WANDS AS wands FROM PlayerData WHERE REPLACE(PlayerUUID, '-', '') = ? LIMIT 1", [String(uuid).replace(/-/g, '')]);
        if (byUuid && byUuid.length) { elite = byUuid[0]; resolvedName = elite.name || name; }
      }
      return { name: resolvedName, found: !!(elite || rpg), elite, rpg };
    } catch (err) {
      console.error('Failed to read combat profile from plugin DB:', err);
      return { name, found: false, error: 'db-unreachable' };
    }
  },

  // Whitelisted IGNs with no matching website account — e.g. someone
  // whitelisted mid-registration who never finished, or added manually by
  // staff outside the site's own registration flow. Used to be a single
  // cross-table JOIN, but `whitelisted_players` and `users` now live in two
  // physically separate MySQL servers (see above) — a JOIN can't span that,
  // so this does the anti-join in JS instead: fetch both lists, filter.
  getUnregisteredWhitelistedPlayers: async () => {
    try {
      const [whitelisted, users] = await Promise.all([
        pluginDataQuery('SELECT uuid, username, synced_at as syncedAt FROM whitelisted_players ORDER BY username ASC'),
        db.getUsers()
      ]);
      const registeredIgns = new Set(
        (users || []).filter(u => u.mcUsername).map(u => u.mcUsername.toLowerCase())
      );
      return (whitelisted || []).filter(w => !registeredIgns.has((w.username || '').toLowerCase()));
    } catch (err) {
      console.error('Failed to get unregistered whitelisted players:', err);
      return [];
    }
  },

  // CHAT MESSAGES
  getRecentChats: async (username) => {
    const lowerUsername = username.toLowerCase();

    const groupByContact = (rows) => {
      const byContact = new Map();
      for (const row of rows) {
        const isSender = row.sender.toLowerCase() === lowerUsername;
        const contact = isSender ? row.receiver : row.sender;
        const key = contact.toLowerCase();
        if (!byContact.has(key)) {
          byContact.set(key, {
            contact,
            lastMessage: row.message && row.message.trim() ? row.message : (row.imageUrl ? '📷 Image' : ''),
            timestamp: row.timestamp,
            unreadCount: 0
          });
        }
        if (!isSender && !row.read) {
          byContact.get(key).unreadCount += 1;
        }
      }
      return Array.from(byContact.values());
    };

    try {
      await ensureChatsTable();
      // Rows come back newest-first so the first row seen per contact is the last message.
      const rows = await query(
        'SELECT sender, receiver, message, image_url as imageUrl, timestamp, `read` FROM chats WHERE LOWER(sender) = LOWER(?) OR LOWER(receiver) = LOWER(?) ORDER BY timestamp DESC',
        [username, username]
      );
      if (rows) return groupByContact(rows);
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    const sorted = [...(fb.chats || [])].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
    const relevant = sorted.filter(m =>
      m.sender.toLowerCase() === lowerUsername || m.receiver.toLowerCase() === lowerUsername
    );
    return groupByContact(relevant);
  },

  // One conversation, oldest-first: the newest `limit` messages, or with
  // `before` (a Date) the `limit` messages before it ("load older").
  // Returns { messages, hasMore }.
  getMessages: async (username, contact, { before = null, limit = 100 } = {}) => {
    const take = Math.max(1, Math.min(500, Number(limit) || 100));
    const finish = (newestFirst) => ({ messages: newestFirst.slice(0, take).reverse(), hasMore: newestFirst.length > take });
    try {
      await ensureChatsTable();
      const rows = await query(
        'SELECT id, sender, receiver, message, image_url as imageUrl, timestamp, `read` FROM chats ' +
        'WHERE ((LOWER(sender) = LOWER(?) AND LOWER(receiver) = LOWER(?)) OR (LOWER(sender) = LOWER(?) AND LOWER(receiver) = LOWER(?)))' +
        (before ? ' AND timestamp < ?' : '') +
        ` ORDER BY timestamp DESC, id DESC LIMIT ${take + 1}`,
        before ? [username, contact, contact, username, before] : [username, contact, contact, username]
      );
      if (rows) return finish(rows);
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    const u = username.toLowerCase();
    const c = contact.toLowerCase();
    return finish((fb.chats || [])
      .filter(m =>
        ((m.sender.toLowerCase() === u && m.receiver.toLowerCase() === c) ||
         (m.sender.toLowerCase() === c && m.receiver.toLowerCase() === u)) &&
        (!before || new Date(m.timestamp) < before)
      )
      .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))
      .slice(0, take + 1));
  },

  getChatMessage: async (id) => {
    try {
      await ensureChatsTable();
      const rows = await query('SELECT id, sender, receiver, message, image_url as imageUrl, timestamp, `read` FROM chats WHERE id = ?', [String(id)]);
      if (rows) return rows[0] || null;
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return (fb.chats || []).find(m => String(m.id) === String(id)) || null;
  },

  deleteChatMessage: async (id) => {
    try {
      await ensureChatsTable();
      const result = await query('DELETE FROM chats WHERE id = ?', [String(id)]);
      return !!(result && result.affectedRows);
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    const before = (fb.chats || []).length;
    fb.chats = (fb.chats || []).filter(m => String(m.id) !== String(id));
    if (fb.chats.length === before) return false;
    writeFallbackJson(fb);
    return true;
  },

  // Unread messages for the nav badge: total, and per sender.
  getUnreadChatCount: async (username) => {
    // Muted conversations never count towards the badge. Grouped per sender in
    // SQL and filtered in JS, so no cross-table string comparison is involved.
    try {
      await ensureChatsTable();
      const muted = new Set((await db.getChatMutes(username)).map(n => n.toLowerCase()));
      const rows = await query('SELECT sender, COUNT(*) as n FROM chats WHERE LOWER(receiver) = LOWER(?) AND `read` = 0 GROUP BY sender', [username]);
      if (rows) return rows.filter(r => !muted.has(String(r.sender).toLowerCase())).reduce((sum, r) => sum + (Number(r.n) || 0), 0);
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    const mutedFb = new Set((fb.chat_mutes || []).filter(m => m.user.toLowerCase() === username.toLowerCase()).map(m => m.muted.toLowerCase()));
    return (fb.chats || []).filter(m => m.receiver.toLowerCase() === username.toLowerCase() && !m.read && !mutedFb.has(m.sender.toLowerCase())).length;
  },

  // Conversation mutes (per user, per contact name).
  getChatMutes: async (username) => {
    try {
      await ensureChatTables();
      const rows = await query('SELECT muted FROM chat_mutes WHERE LOWER(user_key) = LOWER(?)', [username]);
      if (rows) return rows.map(r => r.muted);
    } catch { /* fallback */ }
    return (readFallbackJson().chat_mutes || []).filter(m => m.user.toLowerCase() === String(username).toLowerCase()).map(m => m.muted);
  },

  setChatMute: async (username, contact, on) => {
    try {
      await ensureChatTables();
      if (on) await query('INSERT IGNORE INTO chat_mutes (user_key, muted) VALUES (?, ?)', [username, contact]);
      else await query('DELETE FROM chat_mutes WHERE LOWER(user_key) = LOWER(?) AND LOWER(muted) = LOWER(?)', [username, contact]);
      return true;
    } catch { /* fallback */ }
    try {
      const fb = readFallbackJson();
      const same = (m) => m.user.toLowerCase() === username.toLowerCase() && m.muted.toLowerCase() === contact.toLowerCase();
      fb.chat_mutes = (fb.chat_mutes || []).filter(m => !same(m));
      if (on) fb.chat_mutes.push({ user: username, muted: contact, createdAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch {
      return false;
    }
  },

  // How many players have blocked each member (lower-cased name -> count).
  // Counts only — staff see "blocked by 3 players", never who. Used as context
  // on Reports Inbox entries.
  getChatBlockCounts: async () => {
    try {
      await ensureChatTables();
      const rows = await query('SELECT blocked, COUNT(*) as n FROM chat_blocks GROUP BY blocked');
      if (rows) return Object.fromEntries(rows.map(r => [String(r.blocked).toLowerCase(), Number(r.n) || 0]));
    } catch { /* fallback */ }
    const counts = {};
    for (const b of readFallbackJson().chat_blocks || []) {
      const k = String(b.blocked).toLowerCase();
      counts[k] = (counts[k] || 0) + 1;
    }
    return counts;
  },

  // Audit log. Never throws: an audit write failing must not undo or block
  // the change being audited. `areas` (array) restricts reads to those areas.
  addAuditLog: async ({ actor, actorEmail, actorRole, area, action, target, detail }) => {
    const id = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
    try {
      await ensureAuditTable();
      await query(
        'INSERT INTO audit_log (id, actor, actor_email, actor_role, area, action, target, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [id, actor, actorEmail || null, actorRole || null, area, action, target || null, detail || null]
      );
      return true;
    } catch { /* fall through to db.json */ }
    try {
      const fb = readFallbackJson();
      fb.audit_log = fb.audit_log || [];
      fb.audit_log.push({ id, createdAt: new Date().toISOString(), actor, actorEmail: actorEmail || null, actorRole: actorRole || null, area, action, target: target || null, detail: detail || null });
      if (fb.audit_log.length > 1000) fb.audit_log = fb.audit_log.slice(-1000);
      writeFallbackJson(fb);
      return true;
    } catch {
      return false;
    }
  },

  getAuditLog: async ({ limit = 50, offset = 0, area = null, actor = null, q = null, areas = null, excludeAreas = null, from = null, to = null } = {}) => {
    limit = Math.min(Math.max(parseInt(limit, 10) || 50, 1), 5000);
    offset = Math.max(parseInt(offset, 10) || 0, 0);
    if (Array.isArray(areas) && areas.length === 0) return { entries: [], total: 0 };
    try {
      await ensureAuditTable();
      const where = [];
      const params = [];
      if (Array.isArray(areas)) { where.push(`area IN (${areas.map(() => '?').join(',')})`); params.push(...areas); }
      if (Array.isArray(excludeAreas) && excludeAreas.length) { where.push(`area NOT IN (${excludeAreas.map(() => '?').join(',')})`); params.push(...excludeAreas); }
      if (area) { where.push('area = ?'); params.push(area); }
      if (from) { where.push('created_at >= ?'); params.push(`${from} 00:00:00`); }
      if (to) { where.push('created_at <= ?'); params.push(`${to} 23:59:59`); }
      if (actor) { where.push('LOWER(actor) = LOWER(?)'); params.push(actor); }
      if (q) { where.push('(target LIKE ? OR detail LIKE ? OR action LIKE ?)'); params.push(`%${q}%`, `%${q}%`, `%${q}%`); }
      const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
      const entries = await query(
        `SELECT id, created_at as createdAt, actor, actor_email as actorEmail, actor_role as actorRole, area, action, target, detail FROM audit_log ${clause} ORDER BY created_at DESC, id DESC LIMIT ${limit} OFFSET ${offset}`,
        params
      );
      const totalRows = await query(`SELECT COUNT(*) as n FROM audit_log ${clause}`, params);
      return { entries, total: Number(totalRows?.[0]?.n || 0) };
    } catch { /* fall through to db.json */ }
    const fb = readFallbackJson();
    let rows = (fb.audit_log || []).slice().reverse();
    if (Array.isArray(areas)) rows = rows.filter(r => areas.includes(r.area));
    if (Array.isArray(excludeAreas)) rows = rows.filter(r => !excludeAreas.includes(r.area));
    if (area) rows = rows.filter(r => r.area === area);
    if (from) rows = rows.filter(r => String(r.createdAt) >= from);
    if (to) rows = rows.filter(r => String(r.createdAt).slice(0, 10) <= to);
    if (actor) rows = rows.filter(r => (r.actor || '').toLowerCase() === actor.toLowerCase());
    if (q) { const l = q.toLowerCase(); rows = rows.filter(r => [r.target, r.detail, r.action].some(v => (v || '').toLowerCase().includes(l))); }
    return { entries: rows.slice(offset, offset + limit), total: rows.length };
  },

  // Chat blocks — usernames (same key the chats table uses). Returns every
  // row where `username` is either side, so the caller knows both "I blocked
  // them" and "they blocked me".
  getChatBlocks: async (username) => {
    try {
      await ensureChatTables();
      const rows = await query('SELECT blocker, blocked FROM chat_blocks WHERE LOWER(blocker) = LOWER(?) OR LOWER(blocked) = LOWER(?)', [username, username]);
      if (rows) return rows;
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    const u = username.toLowerCase();
    return (fb.chat_blocks || []).filter(b => b.blocker.toLowerCase() === u || b.blocked.toLowerCase() === u);
  },

  setChatBlock: async (blocker, blocked, on) => {
    try {
      await ensureChatTables();
      if (on) await query('INSERT IGNORE INTO chat_blocks (blocker, blocked) VALUES (?, ?)', [blocker, blocked]);
      else await query('DELETE FROM chat_blocks WHERE LOWER(blocker) = LOWER(?) AND LOWER(blocked) = LOWER(?)', [blocker, blocked]);
      return true;
    } catch { /* fallback */ }
    try {
      const fb = readFallbackJson();
      const same = (b) => b.blocker.toLowerCase() === blocker.toLowerCase() && b.blocked.toLowerCase() === blocked.toLowerCase();
      fb.chat_blocks = (fb.chat_blocks || []).filter(b => !same(b));
      if (on) fb.chat_blocks.push({ blocker, blocked, createdAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch {
      return false;
    }
  },

  sendMessage: async (sender, receiver, message, imageUrl = null) => {
    const id = String(Date.now() + Math.random());
    const timestamp = new Date().toISOString();
    const newMsg = { id, sender, receiver, message: message || '', imageUrl: imageUrl || null, timestamp, read: false };

    try {
      await ensureChatsTable();
      await query(
        'INSERT INTO chats (id, sender, receiver, message, image_url) VALUES (?, ?, ?, ?, ?)',
        [id, sender, receiver, message || null, imageUrl || null]
      );
      return newMsg;
    } catch { /* fallback */ }

    const fb = readFallbackJson();
    if (!fb.chats) fb.chats = [];
    fb.chats.push({ id, sender, receiver, message: message || '', imageUrl: imageUrl || null, timestamp, read: false });
    writeFallbackJson(fb);
    return newMsg;
  },

  markChatAsRead: async (sender, receiver) => {
    // Marks messages sent BY `sender` TO `receiver` as read (matches the caller's semantics:
    // the viewer of a thread marks the other party's messages as read).
    let mysqlOk = false;
    try {
      await ensureChatsTable();
      await query('UPDATE chats SET `read` = 1 WHERE LOWER(sender) = LOWER(?) AND LOWER(receiver) = LOWER(?)', [sender, receiver]);
      mysqlOk = true;
    } catch { /* fallback */ }

    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.chats) {
        fb.chats = fb.chats.map(m =>
          m.sender.toLowerCase() === sender.toLowerCase() && m.receiver.toLowerCase() === receiver.toLowerCase()
            ? { ...m, read: true }
            : m
        );
        writeFallbackJson(fb);
      }
    }
  },

  // BREWING MINI-GAME
  getBrewingRecipes: async () => {
    try {
      const rows = await query('SELECT id, slug, difficulty, recipe_json as recipeJson FROM brewing_recipes');
      return rows.map(r => ({ id: r.id, slug: r.slug, difficulty: r.difficulty, ...JSON.parse(r.recipeJson) }));
    } catch {
      const fb = readFallbackJson();
      return fb.brewing_recipes || [];
    }
  },

  // Returns true if the recipe ended up persisted somewhere (MySQL, or db.json if
  // MySQL is unreachable) — false if both failed. Callers (the seed route, the staff
  // CRUD route) should check this rather than assuming success, same reasoning as
  // the addUser fix: a save that silently goes nowhere is worse than one that fails
  // loudly.
  saveBrewingRecipe: async (recipe) => {
    const { id, slug, difficulty, ...rest } = recipe;
    const recipeJson = JSON.stringify(rest);
    const finalId = id || String(Date.now());
    try {
      const existing = await query('SELECT id FROM brewing_recipes WHERE id = ?', [id || '']);
      if (existing && existing.length > 0) {
        await query('UPDATE brewing_recipes SET slug = ?, difficulty = ?, recipe_json = ? WHERE id = ?', [slug, difficulty, recipeJson, id]);
        await notifyPlayersOfContent('brewing', 'updated', { id, title: recipeDisplayName(rest, slug), link: '/brewing' });
      } else {
        await query('INSERT INTO brewing_recipes (id, slug, difficulty, recipe_json) VALUES (?, ?, ?, ?)', [finalId, slug, difficulty, recipeJson]);
        await notifyPlayersOfContent('brewing', 'created', { id: finalId, title: recipeDisplayName(rest, slug), link: '/brewing' });
      }
      return true;
    } catch (err) {
      console.error('Failed to save brewing recipe in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.brewing_recipes = fb.brewing_recipes || [];
      const idx = fb.brewing_recipes.findIndex(r => r.id === id);
      const saved = { id: finalId, slug, difficulty, ...rest };
      if (idx >= 0) fb.brewing_recipes[idx] = saved;
      else fb.brewing_recipes.push(saved);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save brewing recipe in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteBrewingRecipe: async (id) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM brewing_recipes WHERE id = ?', [id]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to delete brewing recipe in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.brewing_recipes) {
        fb.brewing_recipes = fb.brewing_recipes.filter(r => r.id !== id);
        writeFallbackJson(fb);
      }
    }
  },

  // Sessions for one player, newest first. "Ready" is a computed clock comparison
  // (ready_at <= now) done by the caller — nothing here needs a background job.
  getBrewingSessions: async (username) => {
    try {
      const rows = await query(
        'SELECT id, username, recipe_id as recipeId, quality_tier as qualityTier, failed, started_at as startedAt, ready_at as readyAt ' +
        'FROM brewing_sessions WHERE LOWER(username) = LOWER(?) ORDER BY started_at DESC LIMIT 50',
        [username]
      );
      return (rows || []).map(r => ({ ...r, failed: Boolean(r.failed) }));
    } catch {
      const fb = readFallbackJson();
      return (fb.brewing_sessions || [])
        .filter(s => s.username.toLowerCase() === username.toLowerCase())
        .sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt));
    }
  },

  // Public /brewing leaderboard — four independent rankings (competitive
  // players care about different things: grinding volume, breadth,
  // collecting, or best-roll luck) rather than one combined score. All four
  // are plain aggregates over the existing brewing_sessions/
  // ingredient_hunt_finds tables — no new columns/tables needed.
  getBrewingLeaderboard: async () => {
    try {
      const [mostBrews, mostRecipes, mostIngredients, bestQuality] = await Promise.all([
        query('SELECT username, COUNT(*) as count FROM brewing_sessions WHERE failed = 0 GROUP BY username ORDER BY count DESC LIMIT 10'),
        query('SELECT username, COUNT(DISTINCT recipe_id) as count FROM brewing_sessions WHERE failed = 0 GROUP BY username ORDER BY count DESC LIMIT 10'),
        query('SELECT username, COUNT(DISTINCT ingredient_name) as count FROM ingredient_hunt_finds GROUP BY username ORDER BY count DESC LIMIT 10'),
        query(
          `SELECT s.username, s.recipe_id as recipeId, s.quality_tier as qualityTier
           FROM brewing_sessions s
           INNER JOIN (
             SELECT username, MAX(quality_tier) as maxTier FROM brewing_sessions WHERE failed = 0 GROUP BY username
           ) best ON s.username = best.username AND s.quality_tier = best.maxTier
           WHERE s.failed = 0
           GROUP BY s.username, s.recipe_id, s.quality_tier
           ORDER BY s.quality_tier DESC, s.username ASC
           LIMIT 10`
        )
      ]);
      return {
        mostBrews: mostBrews || [],
        mostRecipes: mostRecipes || [],
        mostIngredients: mostIngredients || [],
        bestQuality: bestQuality || []
      };
    } catch (err) {
      console.error('Failed to compute brewing leaderboard from MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      const sessions = (fb.brewing_sessions || []).filter(s => !s.failed);
      const finds = fb.ingredient_hunt_finds || [];

      const groupBy = (arr, keyFn) => {
        const map = new Map();
        for (const item of arr) {
          const key = keyFn(item);
          if (!map.has(key)) map.set(key, []);
          map.get(key).push(item);
        }
        return map;
      };

      const brewsByUser = groupBy(sessions, s => s.username);
      const mostBrews = [...brewsByUser.entries()]
        .map(([username, list]) => ({ username, count: list.length }))
        .sort((a, b) => b.count - a.count).slice(0, 10);

      const mostRecipes = [...brewsByUser.entries()]
        .map(([username, list]) => ({ username, count: new Set(list.map(s => s.recipeId)).size }))
        .sort((a, b) => b.count - a.count).slice(0, 10);

      const findsByUser = groupBy(finds, f => f.username);
      const mostIngredients = [...findsByUser.entries()]
        .map(([username, list]) => ({ username, count: new Set(list.map(f => f.ingredientName)).size }))
        .sort((a, b) => b.count - a.count).slice(0, 10);

      const bestQuality = [...brewsByUser.entries()]
        .map(([username, list]) => list.reduce((best, s) => (!best || s.qualityTier > best.qualityTier) ? s : best, null))
        .filter(Boolean)
        .map(s => ({ username: s.username, recipeId: s.recipeId, qualityTier: s.qualityTier }))
        .sort((a, b) => b.qualityTier - a.qualityTier).slice(0, 10);

      return { mostBrews, mostRecipes, mostIngredients, bestQuality };
    } catch (fbErr) {
      console.error('Failed to compute brewing leaderboard from db.json fallback:', fbErr);
      return { mostBrews: [], mostRecipes: [], mostIngredients: [], bestQuality: [] };
    }
  },

  // True if this player has a brew that hasn't finished yet — used to block starting
  // a second one concurrently (keeps it a once-at-a-time activity, not spammable).
  hasActiveBrewingSession: async (username) => {
    const sessions = await db.getBrewingSessions(username);
    return sessions.some(s => new Date(s.readyAt).getTime() > Date.now());
  },

  // Real count of brews in progress right now, across every player — backs
  // the homepage Live Broadcast widget's "Active Brewing Vats" figure
  // (previously a fabricated formula).
  getActiveBrewingVatsCount: async () => {
    try {
      const rows = await query('SELECT COUNT(*) as count FROM brewing_sessions WHERE ready_at > NOW()');
      return (rows && rows[0] && rows[0].count) || 0;
    } catch {
      const fb = readFallbackJson();
      const now = Date.now();
      return (fb.brewing_sessions || []).filter(s => new Date(s.readyAt).getTime() > now).length;
    }
  },

  startBrewingSession: async ({ username, recipeId, qualityTier, failed, durationMinutes }) => {
    const id = String(Date.now() + Math.random());
    const startedAt = new Date().toISOString();
    const readyAt = new Date(Date.now() + durationMinutes * 60 * 1000).toISOString();
    const session = { id, username, recipeId, qualityTier, failed: Boolean(failed), startedAt, readyAt };

    try {
      await query(
        'INSERT INTO brewing_sessions (id, username, recipe_id, quality_tier, failed, ready_at) VALUES (?, ?, ?, ?, ?, ?)',
        [id, username, recipeId, qualityTier, Boolean(failed), readyAt]
      );
      return session;
    } catch (err) {
      console.error('Failed to start brewing session in MySQL:', err);
    }

    const fb = readFallbackJson();
    fb.brewing_sessions = fb.brewing_sessions || [];
    fb.brewing_sessions.push(session);
    writeFallbackJson(fb);
    return session;
  },

  // One-time recovery, 2026-08-13: `brewing_sessions` turned out to have
  // never actually existed in production MySQL (see the seed data below
  // and the sixth-batch plugin-DB-mismatch fix this same day) — every real
  // brew a player ever completed had only ever been written to db.json's
  // fallback, invisible once the real table existed and started answering
  // (empty) instead of falling through. This reads db.json directly (not
  // the query() helper — there's nothing to query yet if this table was
  // just created) and copies every session into MySQL, `INSERT IGNORE` so
  // it's safe to run more than once. Legacy sessions predate the `failed`
  // column entirely, so they're migrated as successful (false) — brews
  // always succeeded before that feature existed, which is accurate, not a
  // guess. Same removal-after-use pattern as the recipe reseed button.
  migrateBrewingSessionsFromFallback: async () => {
    const fb = readFallbackJson();
    const legacy = fb.brewing_sessions || [];
    if (legacy.length === 0) return { migrated: 0, total: 0 };

    let migrated = 0;
    for (const s of legacy) {
      try {
        await query(
          'INSERT IGNORE INTO brewing_sessions (id, username, recipe_id, quality_tier, failed, started_at, ready_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
          [s.id, s.username, s.recipeId, s.qualityTier || 0, false, s.startedAt, s.readyAt]
        );
        migrated++;
      } catch (err) {
        console.error(`Failed to migrate brewing session ${s.id}:`, err.message);
      }
    }
    return { migrated, total: legacy.length };
  },

  getBrewingBlueprints: async () => {
    try {
      const rows = await query('SELECT id, slug, title, type, layers_json as layersJson FROM brewing_blueprints');
      return rows.map(r => ({ id: r.id, slug: r.slug, title: r.title, type: r.type, layers: JSON.parse(r.layersJson) }));
    } catch {
      const fb = readFallbackJson();
      return fb.brewing_blueprints || [];
    }
  },

  saveBrewingBlueprint: async (blueprint) => {
    const { id, slug, title, type, layers } = blueprint;
    const layersJson = JSON.stringify(layers || []);
    const finalId = id || String(Date.now());
    try {
      const existing = await query('SELECT id FROM brewing_blueprints WHERE id = ?', [id || '']);
      if (existing && existing.length > 0) {
        await query('UPDATE brewing_blueprints SET slug = ?, title = ?, type = ?, layers_json = ? WHERE id = ?', [slug, title, type, layersJson, id]);
      } else {
        await query('INSERT INTO brewing_blueprints (id, slug, title, type, layers_json) VALUES (?, ?, ?, ?, ?)', [finalId, slug, title, type, layersJson]);
      }
      return true;
    } catch (err) {
      console.error('Failed to save brewing blueprint in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.brewing_blueprints = fb.brewing_blueprints || [];
      const idx = fb.brewing_blueprints.findIndex(b => b.id === id);
      const saved = { id: finalId, slug, title, type, layers: layers || [] };
      if (idx >= 0) fb.brewing_blueprints[idx] = saved;
      else fb.brewing_blueprints.push(saved);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save brewing blueprint in db.json fallback:', fbErr);
      return false;
    }
  },

  // PROFILE JOURNAL — private diary entries shown on the profile page's
  // book UI. Same insert-or-update-by-id / MySQL-first-with-db.json-fallback
  // pattern as saveBrewingRecipe, returning a boolean so callers can tell a
  // save actually landed somewhere (see the CLAUDE.md note about save*
  // methods that used to swallow failures silently).
  getJournalEntries: async (email) => {
    try {
      const rows = await query(
        'SELECT id, user_email as userEmail, entry_date as entryDate, content, image_url as imageUrl, created_at as createdAt, share_token as shareToken FROM journal_entries WHERE user_email = ? ORDER BY entry_date ASC, created_at ASC',
        [email]
      );
      return rows;
    } catch {
      const fb = readFallbackJson();
      return (fb.journal_entries || [])
        .filter(e => e.userEmail && e.userEmail.toLowerCase() === email.toLowerCase())
        .sort((a, b) => new Date(a.entryDate) - new Date(b.entryDate) || new Date(a.createdAt) - new Date(b.createdAt));
    }
  },

  // Public, unauthenticated lookup used by the "shared journal entry" page —
  // deliberately the mirror image of getJournalEntries above: keyed by the
  // random share_token instead of the owner's email, and resolves the
  // owner's mcUsername (never their email — same email-hiding convention
  // GET /api/profile/journal already uses for friend views) so the shared
  // page can show "From <IGN>'s logbook" without exposing the account.
  getJournalEntryByShareToken: async (token) => {
    try {
      const rows = await query(
        `SELECT j.id, j.entry_date as entryDate, j.content, j.image_url as imageUrl, j.created_at as createdAt,
                u.mc_username as mcUsername
         FROM journal_entries j
         LEFT JOIN users u ON u.email = j.user_email
         WHERE j.share_token = ?`,
        [token]
      );
      return rows && rows[0] ? rows[0] : null;
    } catch (err) {
      console.error('Failed to look up shared journal entry in MySQL:', err);
      const fb = readFallbackJson();
      const entry = (fb.journal_entries || []).find(e => e.shareToken === token);
      if (!entry) return null;
      const owner = (fb.users || []).find(u => u.email && u.email.toLowerCase() === (entry.userEmail || '').toLowerCase());
      return { id: entry.id, entryDate: entry.entryDate, content: entry.content, imageUrl: entry.imageUrl || null, createdAt: entry.createdAt, mcUsername: owner ? owner.mcUsername : null };
    }
  },

  // Generates (or returns the existing) public share token for an entry the
  // caller owns. Returns the token string on success, null if the entry
  // doesn't exist / isn't owned by this email, or both MySQL and the
  // db.json fallback failed.
  setJournalEntryShareToken: async (id, email, token) => {
    try {
      const existing = await query('SELECT id FROM journal_entries WHERE id = ? AND user_email = ?', [id, email]);
      if (!existing || existing.length === 0) return null;
      await query('UPDATE journal_entries SET share_token = ? WHERE id = ? AND user_email = ?', [token, id, email]);
      return token;
    } catch (err) {
      console.error('Failed to set journal share token in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.journal_entries = fb.journal_entries || [];
      const idx = fb.journal_entries.findIndex(e => e.id === id && e.userEmail && e.userEmail.toLowerCase() === email.toLowerCase());
      if (idx === -1) return null;
      fb.journal_entries[idx].shareToken = token;
      writeFallbackJson(fb);
      return token;
    } catch (fbErr) {
      console.error('Failed to set journal share token in db.json fallback:', fbErr);
      return null;
    }
  },

  saveJournalEntry: async (entry) => {
    const { id, email, entryDate, content, imageUrl } = entry;
    const finalId = id || String(Date.now());
    try {
      const existing = await query('SELECT id FROM journal_entries WHERE id = ? AND user_email = ?', [id || '', email]);
      if (existing && existing.length > 0) {
        await query('UPDATE journal_entries SET entry_date = ?, content = ?, image_url = ? WHERE id = ? AND user_email = ?', [entryDate, content, imageUrl || null, id, email]);
      } else {
        await query('INSERT INTO journal_entries (id, user_email, entry_date, content, image_url) VALUES (?, ?, ?, ?, ?)', [finalId, email, entryDate, content, imageUrl || null]);
      }
      return true;
    } catch (err) {
      console.error('Failed to save journal entry in MySQL:', err);
    }

    try {
      const fb = readFallbackJson();
      fb.journal_entries = fb.journal_entries || [];
      const idx = fb.journal_entries.findIndex(e => e.id === id && e.userEmail && e.userEmail.toLowerCase() === email.toLowerCase());
      const saved = { id: finalId, userEmail: email, entryDate, content, imageUrl: imageUrl || null, createdAt: idx >= 0 ? fb.journal_entries[idx].createdAt : new Date().toISOString() };
      if (idx >= 0) fb.journal_entries[idx] = saved;
      else fb.journal_entries.push(saved);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save journal entry in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteJournalEntry: async (id, email) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM journal_entries WHERE id = ? AND user_email = ?', [id, email]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to delete journal entry in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.journal_entries) {
        fb.journal_entries = fb.journal_entries.filter(e => !(e.id === id && e.userEmail && e.userEmail.toLowerCase() === email.toLowerCase()));
        writeFallbackJson(fb);
      }
    }
  },

  // FRIENDS — every row involving `email`, on either side, joined with each
  // side's mcUsername so callers (the API route) never need a second lookup
  // just to display who's who. The route is responsible for splitting these
  // into friends/incoming/outgoing based on `status` and which side `email`
  // is on.
  getFriendships: async (email) => {
    try {
      const rows = await query(
        `SELECT f.id, f.requester_email as requesterEmail, f.addressee_email as addresseeEmail, f.status, f.created_at as createdAt,
                ru.mc_username as requesterUsername, au.mc_username as addresseeUsername
         FROM friendships f
         LEFT JOIN users ru ON ru.email = f.requester_email
         LEFT JOIN users au ON au.email = f.addressee_email
         WHERE f.requester_email = ? OR f.addressee_email = ?`,
        [email, email]
      );
      return rows;
    } catch {
      const fb = readFallbackJson();
      const users = fb.users || [];
      const usernameFor = (e) => {
        const u = users.find(u => u.email && u.email.toLowerCase() === e.toLowerCase());
        return u ? u.mcUsername : null;
      };
      return (fb.friendships || [])
        .filter(f => f.requesterEmail.toLowerCase() === email.toLowerCase() || f.addresseeEmail.toLowerCase() === email.toLowerCase())
        .map(f => ({ ...f, requesterUsername: usernameFor(f.requesterEmail), addresseeUsername: usernameFor(f.addresseeEmail) }));
    }
  },

  sendFriendRequest: async (requesterEmail, addresseeEmail) => {
    const id = String(Date.now());
    try {
      await query('INSERT INTO friendships (id, requester_email, addressee_email, status) VALUES (?, ?, ?, ?)', [id, requesterEmail, addresseeEmail, 'pending']);
      return true;
    } catch (err) {
      console.error('Failed to create friend request in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.friendships = fb.friendships || [];
      fb.friendships.push({ id, requesterEmail, addresseeEmail, status: 'pending', createdAt: new Date().toISOString(), respondedAt: null });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to create friend request in db.json fallback:', fbErr);
      return false;
    }
  },

  // `accept` true -> mark accepted; false -> delete the row (covers both
  // "decline an incoming request" and "cancel my own outgoing request" —
  // the caller decides who's allowed to call this for a given row).
  respondToFriendRequest: async (id, accept) => {
    if (accept) {
      let mysqlOk = false;
      try {
        await query("UPDATE friendships SET status = 'accepted', responded_at = NOW() WHERE id = ?", [id]);
        mysqlOk = true;
      } catch (err) {
        console.error('Failed to accept friend request in MySQL:', err);
      }
      if (!mysqlOk) {
        const fb = readFallbackJson();
        if (fb.friendships) {
          const idx = fb.friendships.findIndex(f => f.id === id);
          if (idx >= 0) {
            fb.friendships[idx].status = 'accepted';
            fb.friendships[idx].respondedAt = new Date().toISOString();
            writeFallbackJson(fb);
          }
        }
      }
    } else {
      await db.removeFriendship(id);
    }
  },

  removeFriendship: async (id) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM friendships WHERE id = ?', [id]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to remove friendship in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.friendships) {
        fb.friendships = fb.friendships.filter(f => f.id !== id);
        writeFallbackJson(fb);
      }
    }
  },

  areFriends: async (emailA, emailB) => {
    const rows = await db.getFriendships(emailA);
    return rows.some(f =>
      f.status === 'accepted' &&
      (f.requesterEmail.toLowerCase() === emailB.toLowerCase() || f.addresseeEmail.toLowerCase() === emailB.toLowerCase())
    );
  },

  // USER NOTIFICATIONS — personal (per-recipient) notifications, distinct
  // from the global staff-only `notifications` table used elsewhere in this
  // file. Backs the notification bell shown to every logged-in user.
  getUserNotifications: async (email) => {
    try {
      const rows = await query(
        'SELECT id, recipient_email as recipientEmail, type, message, link, is_read as isRead, created_at as createdAt FROM user_notifications WHERE recipient_email = ? ORDER BY created_at DESC',
        [email]
      );
      return rows;
    } catch {
      const fb = readFallbackJson();
      return (fb.user_notifications || [])
        .filter(n => n.recipientEmail && n.recipientEmail.toLowerCase() === email.toLowerCase())
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    }
  },

  // pushTitle/pushBody/tag only shape the Android push notification (e.g. the
  // chat route passes the actual message text); the bell row itself is unchanged.
  createUserNotification: async ({ recipientEmail, type, message, link, pushTitle, pushBody, tag, actions }) => {
    queueBellPush({ recipients: [recipientEmail], type, message, link, pushTitle, pushBody, tag, actions });
    const id = String(Date.now()) + Math.floor(Math.random() * 1000);
    try {
      await query('INSERT INTO user_notifications (id, recipient_email, type, message, link) VALUES (?, ?, ?, ?, ?)', [id, recipientEmail, type, message, link || null]);
      return true;
    } catch (err) {
      console.error('Failed to create user notification in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.user_notifications = fb.user_notifications || [];
      fb.user_notifications.push({ id, recipientEmail, type, message, link: link || null, isRead: false, createdAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to create user notification in db.json fallback:', fbErr);
      return false;
    }
  },

  markUserNotificationRead: async (id, email) => {
    let mysqlOk = false;
    try {
      await query('UPDATE user_notifications SET is_read = 1 WHERE id = ? AND recipient_email = ?', [id, email]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to mark user notification read in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.user_notifications) {
        const n = fb.user_notifications.find(n => n.id === id && n.recipientEmail && n.recipientEmail.toLowerCase() === email.toLowerCase());
        if (n) { n.isRead = true; writeFallbackJson(fb); }
      }
    }
  },

  deleteUserNotification: async (id, email) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM user_notifications WHERE id = ? AND recipient_email = ?', [id, email]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to delete user notification in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.user_notifications) {
        fb.user_notifications = fb.user_notifications.filter(n => !(n.id === id && n.recipientEmail && n.recipientEmail.toLowerCase() === email.toLowerCase()));
        writeFallbackJson(fb);
      }
    }
  },

  clearUserNotifications: async (email) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM user_notifications WHERE recipient_email = ?', [email]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to clear user notifications in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.user_notifications) {
        fb.user_notifications = fb.user_notifications.filter(n => !(n.recipientEmail && n.recipientEmail.toLowerCase() === email.toLowerCase()));
        writeFallbackJson(fb);
      }
    }
  },

  deleteBrewingBlueprint: async (id) => {
    let mysqlOk = false;
    try {
      await query('DELETE FROM brewing_blueprints WHERE id = ?', [id]);
      mysqlOk = true;
    } catch (err) {
      console.error('Failed to delete brewing blueprint in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.brewing_blueprints) {
        fb.brewing_blueprints = fb.brewing_blueprints.filter(b => b.id !== id);
        writeFallbackJson(fb);
      }
    }
  },

  // SHOP — see schema.sql's comment above CREATE TABLE shop_items for the
  // design rationale (staff-editable RCON command templates instead of
  // hardcoded syntax). getShopItems always returns every item including
  // inactive ones — api/shop/items/route.js is responsible for filtering to
  // active-only for non-staff callers, same "route decides visibility"
  // shape as getSiteContent/getUsers elsewhere in this file.
  getShopItems: async () => {
    try {
      const rows = await query(
        "SELECT id, slug, name, description, price, give_command as giveCommand, image_url as imageUrl, active, clears_tipsy_effect as clearsTipsyEffect, currency, created_at as createdAt FROM shop_items ORDER BY created_at ASC"
      );
      return rows.map(r => ({ ...r, clearsTipsyEffect: Boolean(r.clearsTipsyEffect), currency: r.currency || 'balance' }));
    } catch {
      const fb = readFallbackJson();
      return (fb.shop_items || []).map(i => ({ ...i, currency: i.currency || 'balance' }));
    }
  },

  // Returns true if the item ended up persisted somewhere (MySQL, or db.json
  // if MySQL is unreachable) — false if both failed, same pattern as
  // saveBrewingRecipe/saveCommunityProject.
  //
  // `currency`: 'balance' (default) spends the real in-game Vault balance
  // via RCON, same as always. 'website' spends the website-only "Gears"
  // currency instead (see getUserWebsiteCurrency/spendWebsiteCurrency) and
  // needs the Minecraft server reachable for nothing at all — no online
  // check, no RCON take, no RCON give — so giveCommand is optional for
  // those items (there's nothing to deliver in-game).
  saveShopItem: async (item) => {
    const { id, slug, name, description, price, giveCommand, imageUrl, active, clearsTipsyEffect, currency } = item;
    const finalCurrency = currency === 'website' ? 'website' : 'balance';
    const finalId = id || String(Date.now());
    try {
      const existing = await query('SELECT id FROM shop_items WHERE id = ?', [id || '']);
      if (existing && existing.length > 0) {
        await query(
          'UPDATE shop_items SET slug = ?, name = ?, description = ?, price = ?, give_command = ?, image_url = ?, active = ?, clears_tipsy_effect = ?, currency = ? WHERE id = ?',
          [slug, name, description || null, price, giveCommand || null, imageUrl || null, active ? 1 : 0, clearsTipsyEffect ? 1 : 0, finalCurrency, id]
        );
        if (active) await notifyPlayersOfContent('shop', 'updated', { id, title: name, link: '/shop' });
      } else {
        await query(
          'INSERT INTO shop_items (id, slug, name, description, price, give_command, image_url, active, clears_tipsy_effect, currency) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
          [finalId, slug, name, description || null, price, giveCommand || null, imageUrl || null, active ? 1 : 0, clearsTipsyEffect ? 1 : 0, finalCurrency]
        );
        if (active) await notifyPlayersOfContent('shop', 'created', { id: finalId, title: name, link: '/shop' });
      }
      return true;
    } catch (err) {
      console.error('Failed to save shop item in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.shop_items = fb.shop_items || [];
      const idx = fb.shop_items.findIndex(i => i.id === id);
      const saved = { id: finalId, slug, name, description: description || null, price, giveCommand: giveCommand || null, imageUrl: imageUrl || null, active: !!active, clearsTipsyEffect: !!clearsTipsyEffect, currency: finalCurrency, createdAt: idx >= 0 ? fb.shop_items[idx].createdAt : new Date().toISOString() };
      if (idx >= 0) fb.shop_items[idx] = saved;
      else fb.shop_items.push(saved);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save shop item in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteShopItem: async (id) => {
    let mysqlOk = false;
    try {
      const [doomed] = await query('SELECT name, active FROM shop_items WHERE id = ?', [id]);
      await query('DELETE FROM shop_items WHERE id = ?', [id]);
      mysqlOk = true;
      if (doomed && doomed.active) await notifyPlayersOfContent('shop', 'deleted', { id, title: doomed.name, link: '/shop' });
    } catch (err) {
      console.error('Failed to delete shop item in MySQL:', err);
    }
    if (!mysqlOk) {
      const fb = readFallbackJson();
      if (fb.shop_items) {
        fb.shop_items = fb.shop_items.filter(i => i.id !== id);
        writeFallbackJson(fb);
      }
    }
  },

  // Global shop config — currently just the RCON command template used to
  // deduct a player's balance (see the design note on shop_items). Same
  // single-row (id=1) shape as vote_reward_config.
  getShopSettings: async () => {
    try {
      const rows = await query('SELECT take_command as takeCommand, coins_take_command as coinsTakeCommand, enabled FROM shop_settings WHERE id = 1');
      if (rows && rows.length > 0) return { takeCommand: rows[0].takeCommand, coinsTakeCommand: rows[0].coinsTakeCommand, enabled: Boolean(rows[0].enabled) };
    } catch { /* fallback below */ }
    const fb = readFallbackJson();
    return fb.shop_settings || { takeCommand: 'cmi money take {player} {amount}', coinsTakeCommand: 'elitecoins take {player} {amount}', enabled: true };
  },

  // Merge-safe: only overwrites the fields actually passed in, so toggling
  // `enabled` from the shop page's staff panel never blanks out
  // `takeCommand` (and vice versa) — the two are edited from separate
  // controls in the UI.
  saveShopSettings: async (settings) => {
    const current = await db.getShopSettings();
    const merged = {
      takeCommand: settings.takeCommand !== undefined ? settings.takeCommand : current.takeCommand,
      coinsTakeCommand: settings.coinsTakeCommand !== undefined ? settings.coinsTakeCommand : current.coinsTakeCommand,
      enabled: settings.enabled !== undefined ? !!settings.enabled : current.enabled
    };
    try {
      await query(
        'INSERT INTO shop_settings (id, take_command, coins_take_command, enabled) VALUES (1, ?, ?, ?) ON DUPLICATE KEY UPDATE take_command = ?, coins_take_command = ?, enabled = ?',
        [merged.takeCommand, merged.coinsTakeCommand, merged.enabled ? 1 : 0, merged.takeCommand, merged.coinsTakeCommand, merged.enabled ? 1 : 0]
      );
      return true;
    } catch (err) {
      console.error('Failed to save shop settings in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.shop_settings = merged;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save shop settings in db.json fallback:', fbErr);
      return false;
    }
  },

  getShopPurchasesByUsername: async (username) => {
    try {
      return await query(
        'SELECT id, username, item_id as itemId, item_name as itemName, price_paid as pricePaid, status, take_message as takeMessage, give_message as giveMessage, consumed_at as consumedAt, created_at as createdAt FROM shop_purchases WHERE LOWER(username) = LOWER(?) ORDER BY created_at DESC',
        [username]
      );
    } catch {
      const fb = readFallbackJson();
      return (fb.shop_purchases || []).filter(p => (p.username || '').toLowerCase() === username.toLowerCase());
    }
  },

  // Staff-facing: every purchase, most recent first — used by the Shop
  // page's staff panel to spot/retry 'queued' deliveries across all players.
  getAllShopPurchases: async () => {
    try {
      return await query(
        'SELECT id, username, item_id as itemId, item_name as itemName, price_paid as pricePaid, status, take_message as takeMessage, give_message as giveMessage, consumed_at as consumedAt, created_at as createdAt FROM shop_purchases ORDER BY created_at DESC'
      );
    } catch {
      const fb = readFallbackJson();
      return fb.shop_purchases || [];
    }
  },

  createShopPurchase: async (purchase) => {
    const { id, username, itemId, itemName, pricePaid, status, takeMessage, giveMessage } = purchase;
    try {
      await query(
        'INSERT INTO shop_purchases (id, username, item_id, item_name, price_paid, status, take_message, give_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [id, username, itemId, itemName, pricePaid, status, takeMessage || null, giveMessage || null]
      );
      return true;
    } catch (err) {
      console.error('Failed to record shop purchase in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.shop_purchases = fb.shop_purchases || [];
      fb.shop_purchases.unshift({ id, username, itemId, itemName, pricePaid, status, takeMessage: takeMessage || null, giveMessage: giveMessage || null, consumedAt: null, createdAt: new Date().toISOString() });
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to record shop purchase in db.json fallback:', fbErr);
      return false;
    }
  },

  // Marks a clears_tipsy_effect purchase (Glass of Milk) as actually drunk —
  // see PATCH /api/shop/purchases {action:'consume'} and the "Your Remedies"
  // inventory on /shop. Scoped to the owning username and only succeeds once
  // per purchase (already-consumed or never-paid-for rows don't match), so
  // it doubles as the ownership + double-spend check for the route.
  consumeShopPurchase: async (id, username) => {
    try {
      const result = await query(
        "UPDATE shop_purchases SET consumed_at = NOW() WHERE id = ? AND LOWER(username) = LOWER(?) AND consumed_at IS NULL AND status IN ('delivered', 'queued')",
        [id, username]
      );
      return result.affectedRows > 0;
    } catch (err) {
      console.error('Failed to consume shop purchase in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const idx = (fb.shop_purchases || []).findIndex(p =>
        p.id === id && (p.username || '').toLowerCase() === username.toLowerCase() &&
        !p.consumedAt && ['delivered', 'queued'].includes(p.status)
      );
      if (idx === -1) return false;
      fb.shop_purchases[idx].consumedAt = new Date().toISOString();
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to consume shop purchase in db.json fallback:', fbErr);
      return false;
    }
  },

  // Used both right after the initial give attempt and by the retry/claim
  // endpoint (PATCH /api/shop/purchases) once a 'queued' delivery is
  // reattempted.
  updateShopPurchaseStatus: async (id, status, giveMessage) => {
    try {
      await query('UPDATE shop_purchases SET status = ?, give_message = ? WHERE id = ?', [status, giveMessage || null, id]);
      return true;
    } catch (err) {
      console.error('Failed to update shop purchase status in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      const idx = (fb.shop_purchases || []).findIndex(p => p.id === id);
      if (idx === -1) return false;
      fb.shop_purchases[idx].status = status;
      fb.shop_purchases[idx].giveMessage = giveMessage || null;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to update shop purchase status in db.json fallback:', fbErr);
      return false;
    }
  },

  // STORY ROOM — staff storyline workspace (see ensureStoryTables() above for
  // why these are MySQL-only). Reads return null on failure and writes return
  // false/null, so the API routes can report "couldn't reach the database"
  // honestly instead of pretending a save worked.
  getStoryTree: async () => {
    try {
      await ensureStoryTables();
      return await query(
        `SELECT id, parent_id AS parentId, type, title, status, sort_order AS sortOrder,
                updated_by AS updatedBy, updated_at AS updatedAt, published_wiki_id AS publishedWikiId,
                IF(lock_until > NOW(), lock_user, NULL) AS lockUser
         FROM story_nodes ORDER BY sort_order ASC, title ASC`
      );
    } catch (err) {
      console.error('Failed to load story tree:', err);
      return null;
    }
  },

  getStoryNode: async (id) => {
    try {
      await ensureStoryTables();
      const rows = await query(
        `SELECT id, parent_id AS parentId, type, title, status, content, sort_order AS sortOrder,
                created_by AS createdBy, created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt,
                published_wiki_id AS publishedWikiId, IF(lock_until > NOW(), lock_user, NULL) AS lockUser
         FROM story_nodes WHERE id = ?`,
        [id]
      );
      return rows && rows.length > 0 ? rows[0] : null;
    } catch (err) {
      console.error('Failed to load story node:', err);
      return null;
    }
  },

  createStoryNode: async ({ parentId, type, title, actor }) => {
    try {
      await ensureStoryTables();
      const id = crypto.randomUUID();
      const maxRows = await query(
        parentId
          ? 'SELECT MAX(sort_order) AS maxOrder FROM story_nodes WHERE parent_id = ?'
          : 'SELECT MAX(sort_order) AS maxOrder FROM story_nodes WHERE parent_id IS NULL',
        parentId ? [parentId] : []
      );
      const nextOrder = (maxRows?.[0]?.maxOrder ?? -1) + 1;
      await query(
        'INSERT INTO story_nodes (id, parent_id, type, title, status, content, sort_order, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [id, parentId || null, type, title, 'idea', type === 'doc' ? '' : null, nextOrder, actor, actor]
      );
      return id;
    } catch (err) {
      console.error('Failed to create story node:', err);
      return null;
    }
  },

  // Content saves require the caller to hold the edit lock — that's what
  // stops two writers silently overwriting each other. Returns
  // { ok: true } | { ok: false, reason: 'lock' | 'db' }.
  saveStoryContent: async (id, { title, content }, actor) => {
    try {
      await ensureStoryTables();
      const result = await query(
        `UPDATE story_nodes SET title = ?, content = ?, updated_by = ?, updated_at = NOW()
         WHERE id = ? AND type = 'doc' AND lock_user = ? AND lock_until > NOW()`,
        [title, content, actor, id, actor]
      );
      if (!result || result.affectedRows === 0) return { ok: false, reason: 'lock' };

      const latest = await query(
        `SELECT id, saved_by AS savedBy, saved_at > DATE_SUB(NOW(), INTERVAL ${STORY_REVISION_MERGE_MINUTES} MINUTE) AS recent FROM story_revisions WHERE node_id = ? ORDER BY saved_at DESC, id DESC LIMIT 1`,
        [id]
      );
      if (latest && latest.length > 0 && latest[0].savedBy === actor && Number(latest[0].recent) === 1) {
        await query('UPDATE story_revisions SET title = ?, content = ?, saved_at = NOW() WHERE id = ?', [title, content, latest[0].id]);
      } else {
        await query('INSERT INTO story_revisions (node_id, title, content, saved_by) VALUES (?, ?, ?, ?)', [id, title, content, actor]);
      }
      return { ok: true };
    } catch (err) {
      console.error('Failed to save story content:', err);
      return { ok: false, reason: 'db' };
    }
  },

  // Title/status changes from the tree or status picker — no lock needed,
  // they don't touch the document body someone else may be typing in.
  updateStoryMeta: async (id, { title, status }, actor) => {
    try {
      await ensureStoryTables();
      const sets = [];
      const params = [];
      if (title !== undefined) { sets.push('title = ?'); params.push(title); }
      if (status !== undefined) { sets.push('status = ?'); params.push(status); }
      if (sets.length === 0) return true;
      await query(`UPDATE story_nodes SET ${sets.join(', ')}, updated_by = ?, updated_at = NOW() WHERE id = ?`, [...params, actor, id]);
      return true;
    } catch (err) {
      console.error('Failed to update story node:', err);
      return false;
    }
  },

  moveStoryNode: async (id, parentId) => {
    try {
      await ensureStoryTables();
      const maxRows = await query(
        parentId
          ? 'SELECT MAX(sort_order) AS maxOrder FROM story_nodes WHERE parent_id = ?'
          : 'SELECT MAX(sort_order) AS maxOrder FROM story_nodes WHERE parent_id IS NULL',
        parentId ? [parentId] : []
      );
      const nextOrder = (maxRows?.[0]?.maxOrder ?? -1) + 1;
      await query('UPDATE story_nodes SET parent_id = ?, sort_order = ? WHERE id = ?', [parentId || null, nextOrder, id]);
      return true;
    } catch (err) {
      console.error('Failed to move story node:', err);
      return false;
    }
  },

  reorderStoryNodes: async (orderedIds) => {
    try {
      await ensureStoryTables();
      await Promise.all(orderedIds.map((id, index) => query('UPDATE story_nodes SET sort_order = ? WHERE id = ?', [index, id])));
      return true;
    } catch (err) {
      console.error('Failed to reorder story nodes:', err);
      return false;
    }
  },

  // `ids` is the node plus every descendant (the route resolves the subtree
  // from the tree it already has). Their revisions and comments go with
  // them; timeline events that pointed at a deleted chapter are unlinked,
  // not deleted — the event itself is still part of the world's history.
  deleteStoryNodes: async (ids) => {
    if (!ids || ids.length === 0) return true;
    try {
      await ensureStoryTables();
      const marks = ids.map(() => '?').join(',');
      await query(`DELETE FROM story_revisions WHERE node_id IN (${marks})`, ids);
      await query(`DELETE FROM story_comments WHERE node_id IN (${marks})`, ids);
      await query(`UPDATE story_timeline SET node_id = NULL WHERE node_id IN (${marks})`, ids);
      // Uploaded files are kept: they move to the general asset library.
      await query(`UPDATE story_files SET node_id = NULL WHERE node_id IN (${marks})`, ids);
      await query(`DELETE FROM story_nodes WHERE id IN (${marks})`, ids);
      return true;
    } catch (err) {
      console.error('Failed to delete story nodes:', err);
      return false;
    }
  },

  // Takes (or renews) the edit lock. Succeeds when the document is free, the
  // old lock has expired, the caller already holds it, or `force` is set
  // (admin "take over" for a lock left behind). Returns
  // { ok, lockUser } where lockUser is whoever holds it afterwards.
  acquireStoryLock: async (id, actor, force = false) => {
    try {
      await ensureStoryTables();
      const result = await query(
        `UPDATE story_nodes SET lock_user = ?, lock_until = DATE_ADD(NOW(), INTERVAL ${STORY_LOCK_SECONDS} SECOND)
         WHERE id = ? AND type = 'doc' AND (? = 1 OR lock_user IS NULL OR lock_until IS NULL OR lock_until < NOW() OR lock_user = ?)`,
        [actor, id, force ? 1 : 0, actor]
      );
      if (result && result.affectedRows > 0) return { ok: true, lockUser: actor };
      const rows = await query('SELECT IF(lock_until > NOW(), lock_user, NULL) AS lockUser FROM story_nodes WHERE id = ?', [id]);
      return { ok: false, lockUser: rows?.[0]?.lockUser || null };
    } catch (err) {
      console.error('Failed to acquire story lock:', err);
      return { ok: false, lockUser: null, error: true };
    }
  },

  releaseStoryLock: async (id, actor) => {
    try {
      await ensureStoryTables();
      await query('UPDATE story_nodes SET lock_user = NULL, lock_until = NULL WHERE id = ? AND lock_user = ?', [id, actor]);
      return true;
    } catch (err) {
      console.error('Failed to release story lock:', err);
      return false;
    }
  },

  setStoryPublished: async (id, wikiId) => {
    try {
      await ensureStoryTables();
      await query('UPDATE story_nodes SET published_wiki_id = ? WHERE id = ?', [wikiId, id]);
      return true;
    } catch (err) {
      console.error('Failed to mark story node as published:', err);
      return false;
    }
  },

  getStoryRevisions: async (nodeId) => {
    try {
      await ensureStoryTables();
      return await query(
        'SELECT id, title, saved_by AS savedBy, saved_at AS savedAt, CHAR_LENGTH(content) AS size FROM story_revisions WHERE node_id = ? ORDER BY saved_at DESC, id DESC LIMIT 200',
        [nodeId]
      );
    } catch (err) {
      console.error('Failed to load story revisions:', err);
      return null;
    }
  },

  getStoryRevision: async (revisionId) => {
    try {
      await ensureStoryTables();
      const rows = await query(
        'SELECT id, node_id AS nodeId, title, content, saved_by AS savedBy, saved_at AS savedAt FROM story_revisions WHERE id = ?',
        [revisionId]
      );
      return rows && rows.length > 0 ? rows[0] : null;
    } catch (err) {
      console.error('Failed to load story revision:', err);
      return null;
    }
  },

  getStoryComments: async (nodeId) => {
    try {
      await ensureStoryTables();
      const rows = await query(
        'SELECT id, node_id AS nodeId, author, body, quote, resolved, created_at AS createdAt FROM story_comments WHERE node_id = ? ORDER BY created_at ASC, id ASC',
        [nodeId]
      );
      return (rows || []).map(r => ({ ...r, resolved: !!r.resolved }));
    } catch (err) {
      console.error('Failed to load story comments:', err);
      return null;
    }
  },

  getStoryComment: async (id) => {
    try {
      await ensureStoryTables();
      const rows = await query('SELECT id, node_id AS nodeId, author FROM story_comments WHERE id = ?', [id]);
      return rows && rows.length > 0 ? rows[0] : null;
    } catch (err) {
      console.error('Failed to load story comment:', err);
      return null;
    }
  },

  addStoryComment: async ({ nodeId, author, body, quote }) => {
    try {
      await ensureStoryTables();
      await query('INSERT INTO story_comments (node_id, author, body, quote) VALUES (?, ?, ?, ?)', [nodeId, author, body, quote || null]);
      return true;
    } catch (err) {
      console.error('Failed to add story comment:', err);
      return false;
    }
  },

  setStoryCommentResolved: async (id, resolved) => {
    try {
      await ensureStoryTables();
      await query('UPDATE story_comments SET resolved = ? WHERE id = ?', [resolved ? 1 : 0, id]);
      return true;
    } catch (err) {
      console.error('Failed to update story comment:', err);
      return false;
    }
  },

  deleteStoryComment: async (id) => {
    try {
      await ensureStoryTables();
      await query('DELETE FROM story_comments WHERE id = ?', [id]);
      return true;
    } catch (err) {
      console.error('Failed to delete story comment:', err);
      return false;
    }
  },

  getStoryEntities: async () => {
    try {
      await ensureStoryTables();
      return await query(
        'SELECT id, kind, name, summary, content, image_url AS imageUrl, updated_by AS updatedBy, updated_at AS updatedAt FROM story_entities ORDER BY name ASC'
      );
    } catch (err) {
      console.error('Failed to load story entities:', err);
      return null;
    }
  },

  saveStoryEntity: async ({ id, kind, name, summary, content, imageUrl }, actor) => {
    try {
      await ensureStoryTables();
      const entityId = id || crypto.randomUUID();
      await query(
        `INSERT INTO story_entities (id, kind, name, summary, content, image_url, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE kind = VALUES(kind), name = VALUES(name), summary = VALUES(summary),
           content = VALUES(content), image_url = VALUES(image_url), updated_by = VALUES(updated_by)`,
        [entityId, kind, name, summary || null, content || null, imageUrl || null, actor]
      );
      return entityId;
    } catch (err) {
      console.error('Failed to save story entity:', err);
      return null;
    }
  },

  deleteStoryEntity: async (id) => {
    try {
      await ensureStoryTables();
      await query('UPDATE story_files SET entity_id = NULL WHERE entity_id = ?', [id]);
      await query('DELETE FROM story_entities WHERE id = ?', [id]);
      return true;
    } catch (err) {
      console.error('Failed to delete story entity:', err);
      return false;
    }
  },

  // Documents that link to a lore entry via the [[Name]] syntax. Chapter
  // bodies are Tiptap HTML, where "&" / "<" / ">" in a name are stored
  // escaped, so both spellings are searched.
  getStoryMentions: async (name) => {
    try {
      await ensureStoryTables();
      const likeEscape = (s) => s.replace(/[\\%_]/g, ch => '\\' + ch);
      const htmlName = name.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      return await query(
        "SELECT id, title, status FROM story_nodes WHERE type = 'doc' AND (content LIKE ? OR content LIKE ?) ORDER BY title ASC",
        [`%[[${likeEscape(name)}]]%`, `%[[${likeEscape(htmlName)}]]%`]
      );
    } catch (err) {
      console.error('Failed to load story mentions:', err);
      return null;
    }
  },

  getStoryTimeline: async () => {
    try {
      await ensureStoryTables();
      return await query(
        `SELECT t.id, t.sort_key AS sortKey, t.era_label AS eraLabel, t.title, t.description, t.node_id AS nodeId,
                n.title AS nodeTitle, t.updated_by AS updatedBy, t.updated_at AS updatedAt
         FROM story_timeline t LEFT JOIN story_nodes n ON n.id = t.node_id
         ORDER BY t.sort_key ASC, t.title ASC`
      );
    } catch (err) {
      console.error('Failed to load story timeline:', err);
      return null;
    }
  },

  saveStoryTimelineEvent: async ({ id, sortKey, eraLabel, title, description, nodeId }, actor) => {
    try {
      await ensureStoryTables();
      const eventId = id || crypto.randomUUID();
      await query(
        `INSERT INTO story_timeline (id, sort_key, era_label, title, description, node_id, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE sort_key = VALUES(sort_key), era_label = VALUES(era_label), title = VALUES(title),
           description = VALUES(description), node_id = VALUES(node_id), updated_by = VALUES(updated_by)`,
        [eventId, Number.isFinite(sortKey) ? sortKey : 0, eraLabel || null, title, description || null, nodeId || null, actor]
      );
      return eventId;
    } catch (err) {
      console.error('Failed to save story timeline event:', err);
      return null;
    }
  },

  deleteStoryTimelineEvent: async (id) => {
    try {
      await ensureStoryTables();
      await query('DELETE FROM story_timeline WHERE id = ?', [id]);
      return true;
    } catch (err) {
      console.error('Failed to delete story timeline event:', err);
      return false;
    }
  },

  // Every chapter/folder including its body, for the download/export
  // feature (getStoryTree deliberately leaves `content` out to stay light).
  getStoryNodesWithContent: async () => {
    try {
      await ensureStoryTables();
      return await query(
        `SELECT id, parent_id AS parentId, type, title, status, content, sort_order AS sortOrder,
                created_by AS createdBy, created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt
         FROM story_nodes ORDER BY sort_order ASC, title ASC`
      );
    } catch (err) {
      console.error('Failed to load story nodes for export:', err);
      return null;
    }
  },

  // STORY ROOM FILES — see api/story/files. Metadata only; the bytes are on
  // disk under private-uploads/story-files/<stored_name>.
  listStoryFiles: async () => {
    try {
      await ensureStoryTables();
      return await query(
        `SELECT f.id, f.node_id AS nodeId, f.entity_id AS entityId, f.stored_name AS storedName,
                f.original_name AS originalName, f.mime, f.size_bytes AS sizeBytes, f.note,
                f.uploaded_by AS uploadedBy, f.created_at AS createdAt,
                n.title AS nodeTitle, e.name AS entityName
         FROM story_files f
         LEFT JOIN story_nodes n ON n.id = f.node_id
         LEFT JOIN story_entities e ON e.id = f.entity_id
         ORDER BY f.created_at DESC`
      );
    } catch (err) {
      console.error('Failed to load story files:', err);
      return null;
    }
  },

  getStoryFile: async (id) => {
    try {
      await ensureStoryTables();
      const rows = await query(
        `SELECT id, node_id AS nodeId, entity_id AS entityId, stored_name AS storedName, original_name AS originalName,
                mime, size_bytes AS sizeBytes, note, uploaded_by AS uploadedBy, created_at AS createdAt
         FROM story_files WHERE id = ?`,
        [id]
      );
      return rows && rows.length > 0 ? rows[0] : null;
    } catch (err) {
      console.error('Failed to load story file:', err);
      return null;
    }
  },

  addStoryFile: async ({ id, nodeId, entityId, storedName, originalName, mime, sizeBytes, note, actor }) => {
    try {
      await ensureStoryTables();
      await query(
        'INSERT INTO story_files (id, node_id, entity_id, stored_name, original_name, mime, size_bytes, note, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [id, nodeId || null, entityId || null, storedName, originalName, mime || null, sizeBytes || 0, note || null, actor]
      );
      return true;
    } catch (err) {
      console.error('Failed to save story file:', err);
      return false;
    }
  },

  updateStoryFile: async (id, { nodeId, entityId, note, originalName }) => {
    try {
      await ensureStoryTables();
      const result = await query(
        'UPDATE story_files SET node_id = ?, entity_id = ?, note = ?, original_name = ? WHERE id = ?',
        [nodeId || null, entityId || null, note || null, originalName, id]
      );
      return (result?.affectedRows ?? 0) > 0;
    } catch (err) {
      console.error('Failed to update story file:', err);
      return false;
    }
  },

  // Returns true only when a row was really removed (a 0-row DELETE doesn't
  // throw — see the Reports Inbox false-success bug).
  deleteStoryFile: async (id) => {
    try {
      await ensureStoryTables();
      const result = await query('DELETE FROM story_files WHERE id = ?', [id]);
      return (result?.affectedRows ?? 0) > 0;
    } catch (err) {
      console.error('Failed to delete story file:', err);
      return false;
    }
  },

  // STORY ROOM GUEST LINKS — see src/lib/story-access.js.
  listStoryGuestLinks: async () => {
    try {
      await ensureStoryTables();
      return await query(
        `SELECT id, name, access, expires_at AS expiresAt, revoked, created_by AS createdBy,
                created_at AS createdAt, last_used_at AS lastUsedAt
         FROM story_guest_links ORDER BY revoked ASC, created_at DESC`
      );
    } catch (err) {
      console.error('Failed to load story guest links:', err);
      return null;
    }
  },

  createStoryGuestLink: async ({ tokenHash, name, access, expiresAt, actor }) => {
    try {
      await ensureStoryTables();
      const id = crypto.randomUUID();
      await query(
        'INSERT INTO story_guest_links (id, token_hash, name, access, expires_at, created_by) VALUES (?, ?, ?, ?, ?, ?)',
        [id, tokenHash, name, access, expiresAt || null, actor]
      );
      return id;
    } catch (err) {
      console.error('Failed to create story guest link:', err);
      return null;
    }
  },

  // Only returns a link that is still usable (not revoked, not expired).
  getActiveStoryGuestLinkByHash: async (tokenHash) => {
    try {
      await ensureStoryTables();
      const rows = await query(
        `SELECT id, name, access, expires_at AS expiresAt FROM story_guest_links
         WHERE token_hash = ? AND revoked = 0 AND (expires_at IS NULL OR expires_at > NOW())`,
        [tokenHash]
      );
      return rows && rows.length > 0 ? rows[0] : null;
    } catch (err) {
      console.error('Failed to look up story guest link:', err);
      return null;
    }
  },

  touchStoryGuestLink: async (id) => {
    try {
      // Throttled to once a minute: the Story Room polls every 20s.
      await query(
        'UPDATE story_guest_links SET last_used_at = NOW() WHERE id = ? AND (last_used_at IS NULL OR last_used_at < NOW() - INTERVAL 1 MINUTE)',
        [id]
      );
    } catch { /* best effort */ }
  },

  revokeStoryGuestLink: async (id) => {
    try {
      await ensureStoryTables();
      const result = await query('UPDATE story_guest_links SET revoked = 1 WHERE id = ?', [id]);
      // A 0-row UPDATE doesn't throw — report it instead of claiming success
      // (same false-success bug class as the old Reports Inbox delete).
      return (result?.affectedRows ?? 0) > 0;
    } catch (err) {
      console.error('Failed to revoke story guest link:', err);
      return false;
    }
  },

  // STAFF ROLES — per-role Staff Panel tab permissions, managed from Settings
  // > Roles & Permissions (see src/lib/permissions.js for the actual
  // enforcement, and schema.sql's comment on `roles` for why 'admin' is
  // never a row here). Follows the fallback-to-db.json-and-return-a-boolean
  // pattern (see addUser/saveBrewingRecipe) so a save that only reaches the
  // fallback still reports honestly instead of silently doing nothing.
  getRoles: async () => {
    try {
      // `color` was added 2026-09-28. Fall back to the old column list if
      // that ALTER hasn't been run yet, so Role Manager (and every
      // permission check that reads roles) keeps working either way.
      let rows;
      try {
        rows = await query('SELECT name, label, is_staff as isStaff, tabs, color FROM roles ORDER BY name ASC');
      } catch (colErr) {
        if (colErr?.code !== 'ER_BAD_FIELD_ERROR') throw colErr;
        rows = await query('SELECT name, label, is_staff as isStaff, tabs FROM roles ORDER BY name ASC');
      }
      if (rows) {
        return rows.map(r => ({
          ...r,
          isStaff: !!r.isStaff,
          tabs: Array.isArray(r.tabs) ? r.tabs : (typeof r.tabs === 'string' ? JSON.parse(r.tabs) : [])
        }));
      }
    } catch (err) {
      console.error('Failed to load roles from MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      return fb.roles || [];
    } catch (fbErr) {
      console.error('Failed to load roles from db.json fallback:', fbErr);
      return [];
    }
  },

  saveRole: async ({ name, label, isStaff, tabs, color }) => {
    const tabsJson = JSON.stringify(Array.isArray(tabs) ? tabs : []);
    try {
      try {
        await query(
          'INSERT INTO roles (name, label, is_staff, tabs, color) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE label = VALUES(label), is_staff = VALUES(is_staff), tabs = VALUES(tabs), color = VALUES(color)',
          [name, label, isStaff !== false, tabsJson, color || null]
        );
      } catch (colErr) {
        // `roles.color` not added yet: save everything else rather than failing.
        if (colErr?.code !== 'ER_BAD_FIELD_ERROR') throw colErr;
        await query(
          'INSERT INTO roles (name, label, is_staff, tabs) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE label = VALUES(label), is_staff = VALUES(is_staff), tabs = VALUES(tabs)',
          [name, label, isStaff !== false, tabsJson]
        );
      }
      return true;
    } catch (err) {
      console.error('Failed to save role in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.roles = fb.roles || [];
      const idx = fb.roles.findIndex(r => r.name === name);
      const row = { name, label, isStaff: isStaff !== false, tabs: Array.isArray(tabs) ? tabs : [], color: color || null };
      if (idx === -1) fb.roles.push(row); else fb.roles[idx] = row;
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save role in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteRole: async (name) => {
    try {
      await query('DELETE FROM roles WHERE name = ?', [name]);
      return true;
    } catch (err) {
      console.error('Failed to delete role in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.roles = (fb.roles || []).filter(r => r.name !== name);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to delete role in db.json fallback:', fbErr);
      return false;
    }
  },

  // ACHIEVEMENTS — catalog CRUD (staff) + per-player earned records. See
  // schema.sql's comment on `achievements`/`player_achievements` for the
  // "notified" delivery-to-plugin handshake.
  getAchievements: async () => {
    try {
      const rows = await query('SELECT id, slug, title, description, icon, active, currency_reward as currencyReward FROM achievements ORDER BY created_at ASC');
      if (rows) return rows;
    } catch { /* fallback */ }
    const fb = readFallbackJson();
    return fb.achievements || [];
  },

  saveAchievement: async (achievement) => {
    const { id, slug, title, description, icon, active, currencyReward } = achievement;
    const reward = Number.isFinite(Number(currencyReward)) ? Math.max(0, Math.floor(Number(currencyReward))) : 0;
    try {
      if (id) {
        await query(
          'UPDATE achievements SET slug = ?, title = ?, description = ?, icon = ?, active = ?, currency_reward = ? WHERE id = ?',
          [slug, title, description || null, icon || 'minecraft:paper', active !== false, reward, id]
        );
        if (active !== false) await notifyPlayersOfContent('achievement', 'updated', { id, title, link: '/achievements' });
        return true;
      }
      const newId = String(Date.now());
      await query(
        'INSERT INTO achievements (id, slug, title, description, icon, active, currency_reward) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [newId, slug, title, description || null, icon || 'minecraft:paper', active !== false, reward]
      );
      if (active !== false) await notifyPlayersOfContent('achievement', 'created', { id: newId, title, link: '/achievements' });
      return true;
    } catch (err) {
      console.error('Failed to save achievement in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.achievements = fb.achievements || [];
      if (id) {
        const idx = fb.achievements.findIndex(a => a.id === id);
        if (idx === -1) return false;
        fb.achievements[idx] = { ...fb.achievements[idx], slug, title, description: description || null, icon: icon || 'minecraft:paper', active: active !== false, currencyReward: reward };
      } else {
        fb.achievements.push({ id: String(Date.now()), slug, title, description: description || null, icon: icon || 'minecraft:paper', active: active !== false, currencyReward: reward });
      }
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to save achievement in db.json fallback:', fbErr);
      return false;
    }
  },

  deleteAchievement: async (id) => {
    try {
      await query('DELETE FROM player_achievements WHERE achievement_id = ?', [id]);
      await query('DELETE FROM achievements WHERE id = ?', [id]);
      return true;
    } catch (err) {
      console.error('Failed to delete achievement in MySQL:', err);
    }
    try {
      const fb = readFallbackJson();
      fb.achievements = (fb.achievements || []).filter(a => a.id !== id);
      fb.player_achievements = (fb.player_achievements || []).filter(a => a.achievementId !== id);
      writeFallbackJson(fb);
      return true;
    } catch (fbErr) {
      console.error('Failed to delete achievement in db.json fallback:', fbErr);
      return false;
    }
  },

  // Awards `slug` to `username` if not already earned and the achievement is
  // active. Returns { success, alreadyEarned, achievement } — mirrors the
  // addUser/claimHuntFind-style shape (see CLAUDE.md's brewing section) so
  // callers can tell "already had it" apart from "database unreachable"
  // apart from "no such achievement" rather than a plain boolean. Never
  // throws into the caller — every trigger site treats this as fire-and-
  // forget alongside the real action it's attached to (a message send, a
  // journal save, ...), so a failure here must never block that action.
  awardAchievement: async (username, slug) => {
    try {
      const rows = await query('SELECT id, title, description, icon, currency_reward as currencyReward FROM achievements WHERE slug = ? AND active = 1', [slug]);
      if (!rows || rows.length === 0) return { success: false, alreadyEarned: false };
      const achievement = rows[0];

      const existing = await query('SELECT 1 FROM player_achievements WHERE username = ? AND achievement_id = ?', [username, achievement.id]);
      if (existing && existing.length > 0) return { success: true, alreadyEarned: true, achievement };

      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      await query(
        'INSERT INTO player_achievements (id, username, achievement_id) VALUES (?, ?, ?)',
        [id, username, achievement.id]
      );

      // First-time earn only (the alreadyEarned check above guards against a
      // repeat trigger re-paying out). Keyed by mc_username, same identity
      // achievements themselves use — not email — so this never needs a
      // separate username→email lookup. Wrapped in its own try/catch: a
      // currency-grant failure must never undo/block the achievement itself.
      if (achievement.currencyReward > 0) {
        try {
          await query('UPDATE users SET website_currency = website_currency + ? WHERE LOWER(mc_username) = LOWER(?)', [achievement.currencyReward, username]);
        } catch (err) {
          console.error(`Failed to award ${achievement.currencyReward} website currency to ${username}:`, err);
        }
      }

      return { success: true, alreadyEarned: false, achievement };
    } catch (err) {
      console.error(`Failed to award achievement '${slug}' to ${username} in MySQL:`, err);
      return { success: false, alreadyEarned: false };
    }
  },

  getPlayerAchievements: async (username) => {
    try {
      const rows = await query(
        `SELECT a.id, a.slug, a.title, a.description, a.icon, pa.earned_at as earnedAt
         FROM player_achievements pa JOIN achievements a ON a.id = pa.achievement_id
         WHERE pa.username = ? ORDER BY pa.earned_at ASC`,
        [username]
      );
      if (rows) return rows;
    } catch (err) {
      console.error('Failed to load player achievements from MySQL:', err);
    }
    return [];
  }
};
