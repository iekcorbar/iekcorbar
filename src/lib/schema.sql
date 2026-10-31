-- MySQL Database Schema for Iekcorbar Website

-- NOTE: users also has link_status / link_deadline / link_code / link_code_expires
-- (account-link-migration.sql, added 2026-09-27; NULL = active/linked).
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL DEFAULT 'player',
  mc_username VARCHAR(100),
  custom_skin_url TEXT,
  totp_secret VARCHAR(255) DEFAULT NULL,
  totp_enabled TINYINT(1) NOT NULL DEFAULT 0,
  -- Website-only "Gears" currency — entirely separate from the real in-game
  -- Vault economy (player_stats.balance). Earned via achievements
  -- (achievements.currency_reward, see db.awardAchievement) and spent on
  -- currency='website' shop items (see api/shop/purchase) — the whole
  -- point being a way to buy a purely-cosmetic website item without ever
  -- touching RCON/the Minecraft server.
  website_currency INT NOT NULL DEFAULT 0,
  -- Newsletter (see the `newsletters` table below): everyone receives it by
  -- default (matches how server-wide announcements have always worked
  -- here, e.g. the Updates feed) — this is the opt-*out* flag, flipped
  -- either from the one-click unsubscribe link in every newsletter email
  -- or from the player's own Profile page.
  newsletter_opt_out BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Profile journal/diary entries (private to the owning account — see the
-- profile page's "book" UI). One row per entry; `image_url` points into
-- public/uploads/journal/ (same runtime-upload pattern as chat images and
-- custom skins, see CLAUDE.md "Runtime file uploads").
CREATE TABLE IF NOT EXISTS journal_entries (
  id VARCHAR(50) PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  entry_date DATE NOT NULL,
  content TEXT NOT NULL,
  image_url TEXT DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  share_token VARCHAR(32) DEFAULT NULL,
  INDEX idx_journal_email (user_email),
  UNIQUE INDEX idx_journal_share_token (share_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bug reports submitted from a logged-in user's own profile (identity IS
-- stored, unlike the anonymous player-report `reports` table, since staff
-- may need to follow up with the reporter). Emailed to BUG_REPORT_EMAIL via
-- src/lib/mail.js on submit (best-effort — a report is saved here either
-- way even if that email bounces or SMTP isn't configured).
-- `category`/`custom_category` and the public /known-bugs page: see
-- src/lib/bug-categories.js for the shared category list. `status` is one
-- of 'open' | 'fixed' | 'wont_fix' | 'duplicate' — 'duplicate' pairs with
-- `duplicate_of` (another report's id, the canonical one it was folded
-- into). `affected_count` starts at 1 (the original reporter) and is
-- bumped whenever another member confirms "I have this too" while
-- composing a new report instead of filing a separate duplicate — see
-- db.bumpBugReportAffectedCount. `staff_notes` is never shown to the
-- reporter or on the public page — staff-only, meant to hold the eventual
-- fix/cause so a recurrence is fast to place. Resolved reports (fixed /
-- wont_fix / duplicate) auto-archive out of the default staff/public views
-- 14 days after `resolved_at` — computed lazily off that timestamp (see
-- BUG_ARCHIVE_DAYS), no cron job involved, same pattern as the brewing
-- minigame's ready_at.
CREATE TABLE IF NOT EXISTS bug_reports (
  id VARCHAR(50) PRIMARY KEY,
  reporter_email VARCHAR(255) NOT NULL,
  reporter_username VARCHAR(100) DEFAULT NULL,
  subject VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  category VARCHAR(50) NOT NULL DEFAULT 'other',
  custom_category VARCHAR(150) DEFAULT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'open',
  affected_count INT NOT NULL DEFAULT 1,
  duplicate_of VARCHAR(50) DEFAULT NULL,
  staff_notes TEXT DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME DEFAULT NULL,
  resolved_by VARCHAR(100) DEFAULT NULL,
  INDEX idx_bug_reports_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Conversation thread on a single bug report — either side ('staff' or
-- 'reporter') can add a message; staff messages also record which channel
-- actually delivered the ping to the reporter (the bell/chat/email choice
-- in the Staff Panel's "Contact Reporter" action), while a reporter's own
-- follow-up (from their Profile page) never sets one, since they're
-- already looking at the site. This is the durable record of the
-- conversation regardless of which delivery channel staff used.
CREATE TABLE IF NOT EXISTS bug_report_messages (
  id VARCHAR(50) PRIMARY KEY,
  bug_report_id VARCHAR(50) NOT NULL,
  sender_type VARCHAR(10) NOT NULL,
  sender_name VARCHAR(100) DEFAULT NULL,
  body TEXT NOT NULL,
  delivery_channel VARCHAR(20) DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_bug_report_messages_report (bug_report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Who confirmed "I have this too" while composing a new report (see the
-- duplicate-check panel in Profile's bug report form / db.bumpBugReportAffectedCount)
-- — recorded so they can be notified alongside the original reporter once
-- staff marks it Fixed, not just counted. bug_report_id is only ever
-- compared against a JS-supplied id here, never JOINed column-to-column
-- against bug_reports.id, so — unlike carousel_item_sets — its collation
-- can't trigger MySQL #1267 regardless of what bug_reports.id's actual
-- collation turns out to be on production.
CREATE TABLE IF NOT EXISTS bug_report_watchers (
  bug_report_id VARCHAR(50) NOT NULL,
  email VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (bug_report_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Admin-editable site text (role display names, a couple of page titles) —
-- see src/lib/site-content.js for the full registry of keys/defaults, and
-- the "Site Content" sub-tab under Settings in the staff panel. Rows are
-- only written here on first edit; unedited keys just use the code default.
CREATE TABLE IF NOT EXISTS site_content (
  content_key VARCHAR(100) PRIMARY KEY,
  content_value TEXT NOT NULL,
  label VARCHAR(255) NOT NULL,
  category VARCHAR(50) NOT NULL DEFAULT 'general',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Friend requests/friendships. status starts 'pending' (requester -> addressee)
-- and becomes 'accepted' when the addressee responds; a decline/cancel just
-- deletes the row rather than tracking a 'declined' state, matching how
-- other list-y features in this codebase (e.g. moderation logs) keep only
-- what's currently true rather than a full history.
CREATE TABLE IF NOT EXISTS friendships (
  id VARCHAR(50) PRIMARY KEY,
  requester_email VARCHAR(255) NOT NULL,
  addressee_email VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  responded_at DATETIME DEFAULT NULL,
  INDEX idx_friendships_requester (requester_email),
  INDEX idx_friendships_addressee (addressee_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Per-user notifications (friend requests/accepts, journal page shares).
-- Distinct from the existing `notifications` table above, which is a global
-- staff-only feed with no recipient column — this one is scoped to a single
-- player via recipient_email, for the personal notification bell shown to
-- every logged-in user.
CREATE TABLE IF NOT EXISTS user_notifications (
  id VARCHAR(50) PRIMARY KEY,
  recipient_email VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  message TEXT NOT NULL,
  link VARCHAR(500) DEFAULT NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_notifications_recipient (recipient_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Real server-verified sessions for the httpOnly `iekcorbar_session` cookie.
-- Replaces trusting the client-sent `role` field for routes that opt into
-- requireRole() (see src/lib/session.js). token is a random 64-char hex id,
-- never a JWT, so a session can be revoked instantly by deleting the row.
CREATE TABLE IF NOT EXISTS sessions (
  token CHAR(64) PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NOT NULL,
  -- Set at login time (see api/auth/login) and never re-derived from the
  -- user's current role — staff sessions get a much shorter, sliding
  -- expiry (auto-logout, see src/lib/session.js's STAFF_SESSION_DURATION_MS)
  -- and a browser-session-only cookie instead of a 7-day persistent one.
  is_staff TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_sessions_email (user_email),
  INDEX idx_sessions_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Forgot-password tokens (see api/auth/forgot-password). Same shape/rationale
-- as `sessions` above — a real, single-use, expiring, server-verified token
-- rather than a stateless signed link (unlike src/lib/unsubscribe-token.js's
-- HMAC approach), because a leaked reset link is account takeover, not just
-- an unwanted opt-out. used_at is set (not deleted) when consumed so a
-- reused/old link can tell "expired" apart from "already used" if that's
-- ever worth surfacing; requesting a fresh reset deletes any other
-- outstanding tokens for that email so only the newest link ever works.
CREATE TABLE IF NOT EXISTS password_resets (
  token CHAR(64) PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NOT NULL,
  used_at DATETIME DEFAULT NULL,
  INDEX idx_password_resets_email (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Passkeys (WebAuthn) — an optional, faster alternative to typing a TOTP
-- code on every staff login (see api/auth/passkey/* + src/lib/webauthn.js).
-- Deliberately additive, not a replacement: TOTP stays the fallback for a
-- new device or anyone who never sets one up. `public_key` is stored
-- base64-encoded (the library hands back raw bytes); `counter` is the
-- signature counter WebAuthn uses to detect a cloned authenticator — must
-- be updated after every successful use, never just left at its initial
-- value. `id` is the credential ID the browser itself generates (a real
-- base64url string, not something we assign), so it's the primary key
-- rather than a separate auto-increment id.
CREATE TABLE IF NOT EXISTS passkey_credentials (
  id VARCHAR(255) PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  public_key TEXT NOT NULL,
  counter BIGINT UNSIGNED NOT NULL DEFAULT 0,
  device_type VARCHAR(20) NOT NULL DEFAULT 'singleDevice',
  backed_up TINYINT(1) NOT NULL DEFAULT 0,
  transports VARCHAR(255) DEFAULT NULL,
  -- User-friendly label ("MacBook Touch ID", "YubiKey") — optional, shown in
  -- the Manage Passkeys list on /profile so multiple keys are distinguishable.
  name VARCHAR(100) DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  last_used_at DATETIME DEFAULT NULL,
  INDEX idx_passkey_email (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wiki_pages (
  id VARCHAR(50) PRIMARY KEY,
  slug VARCHAR(255) UNIQUE NOT NULL,
  title VARCHAR(255) NOT NULL,
  category VARCHAR(100) NOT NULL,
  content LONGTEXT NOT NULL,
  updated_by VARCHAR(100) DEFAULT 'Staff',
  parent_id VARCHAR(50) DEFAULT NULL,
  -- JSON array of {url, name} — real uploaded files (PDFs, screenshots),
  -- separate from the existing inline-base64-image-in-markdown feature
  -- (which only ever handled images pasted directly into the article body,
  -- never PDFs, and bloats `content` with base64 rather than a real file).
  attachments LONGTEXT DEFAULT NULL,
  -- Manual staff ordering within a category/parent group (the wiki index
  -- view lists siblings by this, not alphabetically) — see the wiki
  -- redesign note in CLAUDE.md-adjacent commit for why this exists.
  sort_order INT NOT NULL DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wiki_revisions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  page_id VARCHAR(50) NOT NULL,
  content LONGTEXT NOT NULL,
  updated_by VARCHAR(100) DEFAULT 'Staff',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_wiki_revisions_page (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS events (
  id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  date VARCHAR(50) NOT NULL,
  time VARCHAR(50) NOT NULL,
  timezone VARCHAR(50) NOT NULL DEFAULT 'UTC+2',
  -- Holds a full uploaded poster URL (see api/events/route.js) now that the
  -- old "Poster Theme" dropdown is gone; VARCHAR(100) was too small for a
  -- real absolute URL and would truncate it (the same class of bug the
  -- carousel's `src` column had — see that ALTER TABLE elsewhere in this
  -- file). Old rows may still hold the legacy 'derby'/'boss' sentinel
  -- values, which the homepage/events page still map to a static image.
  image TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE events MODIFY COLUMN image TEXT;

-- Event reminders: remembers which reminder (24h / 1h) was already sent per
-- event, so nobody gets duplicates. Run once in phpMyAdmin. Safe to run twice.
CREATE TABLE IF NOT EXISTS event_reminders_sent (
  event_id VARCHAR(50) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS event_registrations (
  id VARCHAR(50) PRIMARY KEY,
  event_id VARCHAR(50) NOT NULL,
  username VARCHAR(100) NOT NULL,
  registered_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moderation_logs (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  type VARCHAR(50) NOT NULL,
  reason TEXT NOT NULL,
  staff_member VARCHAR(100) NOT NULL,
  date DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS updates (
  id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  category VARCHAR(100) NOT NULL,
  date DATETIME DEFAULT CURRENT_TIMESTAMP,
  author VARCHAR(100) NOT NULL,
  content LONGTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS update_categories (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) UNIQUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- News & Upcoming (see /news, distinct from the `updates` changelog above):
-- staff post Minecraft news and planned server/website features, grouped by
-- topic (news_topics), and players opt into a topic (news_topic_follows) to
-- get bell notifications only for posts under it — unlike `updates`, which
-- broadcasts to every registered user regardless of interest.
CREATE TABLE IF NOT EXISTS news_topics (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) UNIQUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS news_posts (
  id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  -- 'news' (already happened, e.g. a Minecraft/vanilla update) or
  -- 'upcoming' (a planned server/website feature) — see db.saveNewsPost.
  type VARCHAR(20) NOT NULL DEFAULT 'news',
  topic VARCHAR(100) NOT NULL DEFAULT 'General',
  content LONGTEXT NOT NULL,
  author VARCHAR(100) NOT NULL,
  -- Optional external link (e.g. a YouTube video) staff can attach to a
  -- post, shown as a button on the public /news card. NULL/empty = none.
  link VARCHAR(500) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per (player, topic) they follow. Deliberately separate from
-- user_notifications (which stores the delivered bell entries themselves) —
-- this is just the subscription list db.saveNewsPost reads to decide who
-- gets pinged for a new post.
CREATE TABLE IF NOT EXISTS news_topic_follows (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  topic VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_news_follow (user_email, topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Named collections of homepage carousel screenshots. Exactly one set is
-- "active" at a time (is_active) — that's the set the public homepage draws
-- from, randomly sampling up to 10 of its images per request (see
-- db.getCarouselDisplay()) so a set can hold unlimited screenshots without
-- flooding the slider.
CREATE TABLE IF NOT EXISTS carousel_sets (
  id VARCHAR(50) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- set_id/sort_order below are legacy — a slide only ever belonged to one
-- set when these columns were added. They're now unused (never DROPped,
-- per this codebase's deliberate caution about destructive schema changes
-- — see CLAUDE.md's db.json incident) in favor of carousel_item_sets just
-- below, a many-to-many join table so one upload can appear in several
-- sets at once instead of needing to be re-uploaded per set.
CREATE TABLE IF NOT EXISTS carousel_items (
  id VARCHAR(50) PRIMARY KEY,
  src LONGTEXT NOT NULL,
  title VARCHAR(255) NOT NULL,
  desc_text TEXT,
  set_id VARCHAR(50) DEFAULT NULL,
  sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE carousel_items ADD COLUMN IF NOT EXISTS set_id VARCHAR(50) DEFAULT NULL;
-- Staff drag-reorder within a set (Settings -> Carousel Manager) — see
-- db.reorderCarouselItems(). Added after the table already existed in
-- production, same as set_id above.
ALTER TABLE carousel_items ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

-- Many-to-many: which sets a slide belongs to, and its own sort_order
-- within each one (a shared slide can sit at a different position per
-- set). Replaces carousel_items.set_id/sort_order above as the real
-- source of truth going forward — see db.saveCarouselItem/
-- getCarouselItemsBySet/reorderCarouselItems.
--
-- COLLATE is deliberately utf8mb4_general_ci here, NOT this file's usual
-- utf8mb4_unicode_ci — carousel_sets.id/carousel_items.id actually live on
-- production as utf8mb4_general_ci (this table's declared COLLATE was
-- apparently never what actually got applied when they were created,
-- despite what's written above), and a JOIN across mismatched collations
-- throws MySQL #1267 ("Illegal mix of collations"). Hit + fixed live
-- 2026-09-09 — a real "database is fine, JOIN just errors" incident, not
-- data loss. Before adding a new table that JOINs an existing one on a
-- string column, verify the existing column's *actual* collation via
-- `SELECT TABLE_NAME, COLUMN_NAME, COLLATION_NAME FROM
-- information_schema.COLUMNS WHERE TABLE_NAME = '...'` rather than
-- trusting this file's stated default.
CREATE TABLE IF NOT EXISTS carousel_item_sets (
  item_id VARCHAR(50) NOT NULL,
  set_id VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (item_id, set_id),
  INDEX idx_carousel_item_sets_set (set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- One-time migration of existing single-set assignments into the new
-- join table above — safe to re-run, INSERT IGNORE skips anything already
-- migrated (e.g. re-running schema.sql via migrate.js later).
INSERT IGNORE INTO carousel_item_sets (item_id, set_id, sort_order)
SELECT id, set_id, sort_order FROM carousel_items WHERE set_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS polls (
  id VARCHAR(50) PRIMARY KEY,
  question TEXT NOT NULL,
  options_json LONGTEXT NOT NULL,
  voters_json LONGTEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS starter_guide (
  id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  attachment_url TEXT DEFAULT NULL,
  attachment_name VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE starter_guide ADD COLUMN IF NOT EXISTS attachment_url TEXT DEFAULT NULL;
ALTER TABLE starter_guide ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS rules (
  id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  display_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- "Tip of the Day" — homepage widget, staff CRUD in Settings. One tip is
-- picked deterministically per calendar day (day-of-year modulo active tip
-- count, see api/tips GET), not re-randomized per page load, so it's stable
-- for the whole day and rotates the next.
CREATE TABLE IF NOT EXISTS daily_tips (
  id VARCHAR(50) PRIMARY KEY,
  content TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vote_sites (
  id VARCHAR(50) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  url TEXT NOT NULL,
  icon VARCHAR(50) DEFAULT '⚙️'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vote_log (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  site_name VARCHAR(255) NOT NULL,
  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
  rewarded BOOLEAN DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vote_reward_config (
  id INT PRIMARY KEY DEFAULT 1,
  enabled BOOLEAN DEFAULT TRUE,
  command VARCHAR(255) DEFAULT 'give {player} diamond 3',
  description TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_projects (
  id VARCHAR(50) PRIMARY KEY,
  slug VARCHAR(255) UNIQUE NOT NULL,
  title VARCHAR(255) NOT NULL,
  category VARCHAR(100) NOT NULL,
  status VARCHAR(50) NOT NULL DEFAULT 'open',
  description LONGTEXT NOT NULL,
  max_participants INT NOT NULL DEFAULT 10,
  created_by VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_signups (
  id VARCHAR(50) PRIMARY KEY,
  project_id VARCHAR(50) NOT NULL,
  username VARCHAR(100) NOT NULL,
  signed_up_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_history (
  day VARCHAR(10) PRIMARY KEY,
  players INT NOT NULL DEFAULT 0,
  peak_time VARCHAR(10) DEFAULT '',
  last_updated VARCHAR(20) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Staff-only activity feed (api/notifications, requires admin/moderator) —
-- shared rows with no recipient column, unlike user_notifications above.
-- Only ever written to for wiki page edits now; site "updates" used to be
-- written here too but that's player-facing news, not a staff-internal
-- content-management concern, so it broadcasts into every user's personal
-- user_notifications instead (see db.js's broadcastUserNotification).
CREATE TABLE IF NOT EXISTS notifications (
  id VARCHAR(50) PRIMARY KEY,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  is_read BOOLEAN DEFAULT FALSE,
  type VARCHAR(50) NOT NULL,
  message TEXT NOT NULL,
  link VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settings (
  setting_key VARCHAR(100) PRIMARY KEY,
  setting_value TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_stats (
  username VARCHAR(100) PRIMARY KEY,
  online BOOLEAN DEFAULT FALSE,
  playtime_hours INT DEFAULT 0,
  kills INT DEFAULT 0,
  deaths INT DEFAULT 0,
  mob_kills INT DEFAULT 0,
  blocks_mined INT DEFAULT 0,
  balance DOUBLE DEFAULT 0.0,
  elite_coins INT DEFAULT 0,
  lands_claimed INT DEFAULT 0,
  jobs_json LONGTEXT,
  plugin_placeholders_json LONGTEXT,
  last_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Real, verified votes — written by VotifierListener.java whenever the
-- server's Votifier-compatible receiver (VoteSpeed's built-in one) actually
-- receives a vote, from any service (external sites or our own direct
-- "Iekcorbar" one via src/lib/votifier.js). Deliberately separate from the
-- older vote_log table (click-based honor system, still used for its own
-- reward flow) rather than replacing it — this is purely additive, real
-- verified data for display. Also auto-created by the plugin itself
-- (MySQLSync.java's createTablesIfNotExist) the same way player_stats is.
CREATE TABLE IF NOT EXISTS votifier_events (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  service_name VARCHAR(100) NOT NULL,
  address VARCHAR(100),
  vote_timestamp VARCHAR(50),
  received_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_votifier_events_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mirrors of the server's real banned-players.json / whitelist.json — see
-- AccessListSync.java. Full-replace on every plugin sync tick (not
-- incremental), read-only from the website's side. Used by the Staff Panel
-- "Access Control" tab; deliberately NEVER shown on the public /members
-- page, which only ever lists real website accounts (db.getUsers()).
CREATE TABLE IF NOT EXISTS banned_players (
  uuid VARCHAR(36) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  created VARCHAR(50),
  source VARCHAR(100),
  expires VARCHAR(50),
  reason TEXT,
  synced_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS whitelisted_players (
  uuid VARCHAR(36) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  synced_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audit trail for the Staff Panel > Console tab (admin-only raw RCON
-- terminal). Deliberately separate from moderation_logs, which assumes a
-- per-player action (username NOT NULL) — a raw console command isn't
-- necessarily about any one player.
CREATE TABLE IF NOT EXISTS rcon_console_log (
  id VARCHAR(50) PRIMARY KEY,
  command TEXT NOT NULL,
  response TEXT,
  staff_member VARCHAR(100) NOT NULL,
  date DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Web Push subscriptions (iPhone home-screen web app + desktop browsers).
-- Run once in phpMyAdmin (database `iekcorbar` -> SQL or Import). Safe to run more than once.
CREATE TABLE IF NOT EXISTS web_push_subscriptions (
  id CHAR(64) PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  subscription_json TEXT NOT NULL,
  user_agent VARCHAR(255) DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_web_push_user (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Website & app analytics (Staff Panel -> Analytics). Run once in phpMyAdmin.
-- Safe to run more than once. Rows older than 90 days are removed automatically.
CREATE TABLE IF NOT EXISTS analytics_events (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Per-user push notification opt-outs (Profile page). Also created lazily by db.js.
CREATE TABLE IF NOT EXISTS push_preferences (
  user_email VARCHAR(255) PRIMARY KEY,
  prefs_json TEXT NOT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_tokens (
  id VARCHAR(50) PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  platform VARCHAR(20) NOT NULL,
  expo_push_token VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY unique_expo_push_token (expo_push_token),
  KEY idx_device_tokens_user (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chats (
  id VARCHAR(64) PRIMARY KEY,
  sender VARCHAR(100) NOT NULL,
  receiver VARCHAR(100) NOT NULL,
  message TEXT DEFAULT NULL,
  image_url TEXT DEFAULT NULL,
  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
  `read` TINYINT(1) DEFAULT 0,
  KEY sender (sender),
  KEY receiver (receiver)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chat blocks: `blocker` no longer receives messages from `blocked`
-- (usernames, same as chats.sender/receiver). Also auto-created by db.js.
CREATE TABLE IF NOT EXISTS chat_blocks (
  blocker VARCHAR(100) NOT NULL,
  blocked VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (blocker, blocked),
  KEY idx_chat_blocks_blocked (blocked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Staff audit trail: who changed what in the Staff Panel. Also auto-created by db.js.
CREATE TABLE IF NOT EXISTS audit_log (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BREWING MINI-GAME (website-only companion to the in-game Brewery plugin recipes —
-- see recipe_json for the full cook/distill/age/mix step data and quality-tier names,
-- ported from the plugin's own recipes.yml so results match what players know in-game)
CREATE TABLE IF NOT EXISTS brewing_recipes (
  id VARCHAR(50) PRIMARY KEY,
  slug VARCHAR(100) UNIQUE NOT NULL,
  difficulty INT NOT NULL DEFAULT 1,
  recipe_json LONGTEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per brew attempt. quality_tier is rolled at start time (index into that
-- recipe's name-tier list) but only revealed client-side once ready_at has passed —
-- no separate "claim" step, a session is simply ready or not based on the clock.
-- `failed` (2026-08-13) is rolled at the same time — a failed brew still runs its
-- full timer (nothing to "cancel", the suspense is the point) but reveals as a
-- bust instead of a quality tier; ingredients aren't consumed either way (see
-- ingredient_hunt_finds below), so a failure costs the wait, not progress.
CREATE TABLE IF NOT EXISTS brewing_sessions (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  recipe_id VARCHAR(50) NOT NULL,
  quality_tier INT NOT NULL DEFAULT 0,
  failed BOOLEAN NOT NULL DEFAULT FALSE,
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  ready_at DATETIME NOT NULL,
  KEY idx_brewing_sessions_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE brewing_sessions ADD COLUMN IF NOT EXISTS failed BOOLEAN NOT NULL DEFAULT FALSE;

-- Ingredient Hunt (2026-08-13): a real, staff-uncurated ingredient name (drawn
-- from the actual recipe data, see src/lib/ingredient-hunt.js) is hidden on one
-- random public page each day (deterministic per calendar day, same idea as
-- daily_tips). Each player can find/claim at most one per real day — the
-- UNIQUE key below enforces that at the database level, not just in the route
-- handler. Collected ingredients are permanent (never consumed) and unlock a
-- recipe for brewing once a player holds every distinct ingredient name that
-- recipe's cook steps call for — see api/brewing/sessions' unlock check.
CREATE TABLE IF NOT EXISTS ingredient_hunt_finds (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  ingredient_name VARCHAR(100) NOT NULL,
  found_date DATE NOT NULL,
  found_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  -- 2026-09-05: recipes now require a real (scaled-down) quantity of each
  -- ingredient, not just "found at least once" — consumed when a brew
  -- actually starts (see api/brewing/sessions, db.consumeIngredients).
  -- Marked rather than deleted so a player's full find history stays
  -- intact even after spending some of it.
  consumed BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE KEY idx_hunt_user_date (username, found_date),
  KEY idx_hunt_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS brewing_blueprints (
  id VARCHAR(50) PRIMARY KEY,
  slug VARCHAR(100) UNIQUE NOT NULL,
  title VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  layers_json LONGTEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SHOP — spends a player's in-game economy balance (player_stats.balance,
-- Vault, synced one-way FROM the server INTO MySQL, see MySQLSync.java) on
-- staff-curated items delivered in-game via RCON. Both give_command (per
-- item) and shop_settings.take_command (global) are free-text RCON
-- templates with {player}/{amount} placeholders — same pattern as
-- vote_reward_config.command — rather than hardcoded syntax, because this
-- server's actual money-take command (CMI? EssentialsX? something else
-- behind Vault) isn't confirmed anywhere in this codebase; staff can correct
-- the template from the Shop page's own settings panel without a redeploy
-- if the default guess turns out wrong. See api/shop/purchase/route.js for
-- how both commands are used and verified before/after being sent.
CREATE TABLE IF NOT EXISTS shop_items (
  id VARCHAR(50) PRIMARY KEY,
  slug VARCHAR(150) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT DEFAULT NULL,
  price DOUBLE NOT NULL,
  -- NULL for currency='website' items — those are never delivered in-game
  -- (no RCON contact at all, see `currency` below), so there's nothing to
  -- give a command for.
  give_command VARCHAR(255) DEFAULT NULL,
  image_url TEXT DEFAULT NULL,
  active BOOLEAN DEFAULT TRUE,
  -- Buying this item also clears the website's cosmetic "tipsy" status
  -- effect from drinking a collected brew (src/lib/tipsy-effect.js) — a
  -- flag rather than matching on a magic slug like "glass-of-milk", so
  -- staff can flag any item (or rename/reprice the default one) without
  -- silently breaking the cure.
  clears_tipsy_effect BOOLEAN NOT NULL DEFAULT FALSE,
  -- 'balance' (default): spends the real in-game Vault balance via RCON,
  -- as this table's design was originally built around — needs the MC
  -- server reachable to verify/take/deliver. 'website': spends the
  -- website-only "Gears" currency (users.website_currency) instead —
  -- entirely decoupled from the Minecraft server, no RCON contact of any
  -- kind (see api/shop/purchase). Added 2026-09-07 after Glass of Milk (a
  -- purely cosmetic website effect to begin with) turned out to be
  -- unbuyable whenever the server's real economy command was misconfigured
  -- or the server itself was unreachable — a website-only cosmetic
  -- shouldn't depend on either.
  currency VARCHAR(20) NOT NULL DEFAULT 'balance',
  -- 'cosmetic' (default): in-game item delivered via queued give-command.
  -- 'download': digital product (plugin jar/zip) sold for real money via
  -- PayPal; delivered as a secure download link after payment. Added 2026-10-31
  -- in preparation for the plugin-sales v2.
  product_type VARCHAR(20) NOT NULL DEFAULT 'cosmetic',
  -- For product_type='download': relative path under private-uploads/plugins/
  -- (never publicly served; streamed via /api/shop/download after purchase).
  file_path VARCHAR(500) DEFAULT NULL,
  -- For product_type='download': human-readable version string (e.g. "1.2.0").
  version VARCHAR(50) DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- status: 'delivered' (money taken + item given), 'queued' (money taken,
-- item delivery failed/pending retry — see PATCH /api/shop/purchases),
-- 'failed' (money was never confirmed taken, nothing delivered).
CREATE TABLE IF NOT EXISTS shop_purchases (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  item_id VARCHAR(50) NOT NULL,
  item_name VARCHAR(255) NOT NULL,
  price_paid DOUBLE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'failed',
  take_message TEXT DEFAULT NULL,
  give_message TEXT DEFAULT NULL,
  -- Set once a clears_tipsy_effect item (Glass of Milk) is actually drunk —
  -- see PATCH /api/shop/purchases {action:'consume'}. Buying one no longer
  -- clears the website's tipsy effect by itself: an unconsumed purchase sits
  -- in the buyer's "Your Remedies" inventory on /shop until they drink it
  -- (immediately, or a bottle bought earlier). NULL for every other item —
  -- it's simply never checked for anything that isn't clears_tipsy_effect.
  consumed_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_shop_purchases_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shop_settings (
  id INT PRIMARY KEY DEFAULT 1,
  take_command VARCHAR(255) DEFAULT 'cmi money take {player} {amount}',
  -- currency='coins' items spend EliteMobs' Elite coins instead of the Vault
  -- balance; same free-text RCON template, correctable live from the Shop
  -- page's staff settings panel if the default guess doesn't match the
  -- server's actual command (added 2026-10-26 with the coins currency).
  coins_take_command VARCHAR(255) DEFAULT 'elitecoins take {player} {amount}',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  -- PayPal (v2 plugin sales). Stored here so staff can configure without redeploy.
  paypal_client_id VARCHAR(255) DEFAULT NULL,
  paypal_secret VARCHAR(500) DEFAULT NULL,
  paypal_mode VARCHAR(20) NOT NULL DEFAULT 'sandbox'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Achievement/advancement system (2026-09-06) — website actions (chat,
-- journal, friends, brewing, shop, ...) award these; IekcorbarSiteSync
-- delivers them in-game as a real advancement toast (see ToastNotifier.java)
-- the next time the player is online, using `notified` the same way
-- shop_purchases uses `status` to track "still needs plugin-side delivery".
-- `icon` is a Minecraft item id (e.g. "minecraft:milk_bucket") — same
-- convention as the toast/give-command icons elsewhere in this feature.
CREATE TABLE IF NOT EXISTS achievements (
  id VARCHAR(50) PRIMARY KEY,
  slug VARCHAR(100) UNIQUE NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT DEFAULT NULL,
  icon VARCHAR(100) NOT NULL DEFAULT 'minecraft:paper',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  -- Website "Gears" currency (users.website_currency) paid out the first
  -- time this achievement is earned — 0 (default) means no payout, staff
  -- opts individual achievements in from the Achievements page's own form.
  currency_reward INT NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_achievements (
  id VARCHAR(50) PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  achievement_id VARCHAR(50) NOT NULL,
  earned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  -- Whether IekcorbarSiteSync has shown the in-game toast for this yet —
  -- awarded on the website first (instant, no RCON round trip needed), the
  -- toast catches up whenever the player is next online.
  notified BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE KEY uniq_player_achievement (username, achievement_id),
  KEY idx_player_achievements_user (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Configurable staff roles, managed by admins from the Staff Panel's Settings
-- > Roles & Permissions screen (src/lib/permissions.js does the actual
-- enforcement). 'admin' is NOT a row in this table — it's hardcoded to
-- always have every tab (see isAdminRole in src/lib/staff-tabs.js) so this
-- table being empty or misconfigured can never lock every admin out. A row
-- for 'moderator' is optional too: src/lib/permissions.js falls back to the
-- pre-this-feature tab set for 'moderator' specifically if no row exists,
-- so nobody's access changes just because this table is new/empty.
CREATE TABLE IF NOT EXISTS roles (
  name VARCHAR(50) PRIMARY KEY,
  label VARCHAR(100) NOT NULL,
  -- Reserved for a future non-staff use of custom roles (e.g. a cosmetic
  -- VIP/Builder rank with zero Staff Panel tabs) — every role created via
  -- Role Manager today is a staff role, so this is always TRUE for now.
  is_staff BOOLEAN NOT NULL DEFAULT TRUE,
  -- JSON array of STAFF_TABS keys this role can access, e.g. '["wiki","reports"]'.
  tabs JSON NOT NULL,
  -- Badge colour key from ROLE_COLORS in src/lib/staff-tabs.js (e.g.
  -- 'emerald', 'sky'); NULL = the default for that role.
  color VARCHAR(20) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Newsletter send history (see api/admin/newsletter) — admin-only broadcast
-- email to every user who hasn't opted out (users.newsletter_opt_out). One
-- row per actual send, kept as an audit trail (same spirit as
-- rcon_console_log/moderation_logs) and so the compose UI can show what's
-- already gone out rather than staff guessing/resending blind.
CREATE TABLE IF NOT EXISTS newsletters (
  id VARCHAR(50) PRIMARY KEY,
  subject VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  sent_by VARCHAR(100) NOT NULL,
  recipient_count INT NOT NULL DEFAULT 0,
  sent_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Admin-only shared file drop (Settings > Shared Files) — admins pass
-- plugins/configs/misc files to each other. Deliberately admin-only the same
-- way Roles & Permissions/Newsletter are (see staff-tabs.js's comment): not
-- a STAFF_TABS entry, so there's no permission key a custom role could ever
-- be granted here. The actual file bytes live OUTSIDE public/ (see
-- api/admin/files/route.js) precisely so they're never reachable through the
-- unauthenticated /uploads/* static-file passthrough app.js/server.js expose
-- for player-facing uploads (chat images, skins, wiki attachments) — every
-- byte here is only ever served through an admin-gated API route.
CREATE TABLE IF NOT EXISTS admin_shared_files (
  id VARCHAR(50) PRIMARY KEY,
  filename VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL,
  description TEXT NULL,
  uploaded_by VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Player applications for a Staff role (currently only Moderator is offered
-- — see src/app/staff-application/page.js for the explainer copy, which is
-- explicit that this is unpaid/volunteer). Members-only submission (any
-- real session, not public — /staff-application itself gates on `user`
-- the same way /known-bugs does), reviewed from the Staff Panel's
-- "Staff Applications" tab. One row per submission; a member can have at
-- most one 'pending' row at a time (enforced in the API route, not here,
-- same as the brewing one-active-session rule) so the queue doesn't fill
-- with repeat submissions from the same person while they wait.
-- reference_name is required: a staff member or player who can vouch for
-- the applicant, free-text (not a lookup against `users`) since it may
-- name someone in-game staff know by IGN only.
CREATE TABLE IF NOT EXISTS staff_applications (
  id VARCHAR(50) PRIMARY KEY,
  applicant_email VARCHAR(255) NOT NULL,
  applicant_username VARCHAR(100) DEFAULT NULL,
  age INT NOT NULL,
  availability VARCHAR(255) NOT NULL,
  timezone VARCHAR(100) DEFAULT NULL,
  discord_username VARCHAR(100) DEFAULT NULL,
  experience TEXT DEFAULT NULL,
  reference_name VARCHAR(150) NOT NULL,
  reason TEXT NOT NULL,
  contribution TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  staff_notes TEXT DEFAULT NULL,
  reviewed_by VARCHAR(100) DEFAULT NULL,
  reviewed_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_staff_applications_status (status),
  INDEX idx_staff_applications_applicant (applicant_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Story Room (Staff Panel -> Story Room): staff storyline workspace. Also
-- created lazily by db.js ensureStoryTables(); standalone copy in
-- story-room-migration.sql at the repo root.
CREATE TABLE IF NOT EXISTS story_nodes (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_revisions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  node_id CHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content LONGTEXT DEFAULT NULL,
  saved_by VARCHAR(100) DEFAULT NULL,
  saved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_story_revisions_node (node_id, saved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_comments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  node_id CHAR(36) NOT NULL,
  author VARCHAR(100) NOT NULL,
  body TEXT NOT NULL,
  quote TEXT DEFAULT NULL,
  resolved TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_story_comments_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_entities (
  id CHAR(36) PRIMARY KEY,
  kind VARCHAR(20) NOT NULL DEFAULT 'character',
  name VARCHAR(150) NOT NULL,
  summary VARCHAR(500) DEFAULT NULL,
  content LONGTEXT DEFAULT NULL,
  image_url VARCHAR(500) DEFAULT NULL,
  updated_by VARCHAR(100) DEFAULT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Story Room uploaded files (images, 3D models, schematics, documents...).
-- Bytes are stored in private-uploads/story-files/, never under public/.
CREATE TABLE IF NOT EXISTS story_files (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Story Room guest links: personal links for people without a website
-- account (see src/lib/story-access.js). Only a hash of the secret is stored.
CREATE TABLE IF NOT EXISTS story_guest_links (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_timeline (
  id CHAR(36) PRIMARY KEY,
  sort_key INT NOT NULL DEFAULT 0,
  era_label VARCHAR(100) DEFAULT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT DEFAULT NULL,
  node_id CHAR(36) DEFAULT NULL,
  updated_by VARCHAR(100) DEFAULT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
