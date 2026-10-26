-- ============================================================
-- IEKCORBAR — MySQL Database Seed Script
-- Run this in Plesk phpMyAdmin or via MySQL terminal
-- Safe to run multiple times (uses IF NOT EXISTS / INSERT IGNORE)
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─────────────────────────────────────────────
-- TABLES
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `users` (
  `email`           VARCHAR(255) NOT NULL PRIMARY KEY,
  `password`        VARCHAR(255) NOT NULL,
  `role`            VARCHAR(50)  NOT NULL DEFAULT 'player',
  `mc_username`     VARCHAR(100) DEFAULT NULL,
  `custom_skin_url` TEXT         DEFAULT NULL,
  `totp_secret`     VARCHAR(255) DEFAULT NULL,
  `totp_enabled`    TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `totp_secret` VARCHAR(255) DEFAULT NULL;
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `totp_enabled` TINYINT(1) NOT NULL DEFAULT 0;
-- Website-only "Gears" currency, separate from the real in-game Vault
-- balance — see schema.sql's comment on this column.
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `website_currency` INT NOT NULL DEFAULT 0;
-- Newsletter opt-out flag — everyone receives by default, see schema.sql.
ALTER TABLE `users` ADD COLUMN IF NOT EXISTS `newsletter_opt_out` BOOLEAN NOT NULL DEFAULT FALSE;

-- Profile journal/diary entries (private to the owning account). image_url
-- points into public/uploads/journal/, same runtime-upload pattern as chat
-- images and custom skins.
CREATE TABLE IF NOT EXISTS `journal_entries` (
  `id`           VARCHAR(50)  NOT NULL PRIMARY KEY,
  `user_email`   VARCHAR(255) NOT NULL,
  `entry_date`   DATE         NOT NULL,
  `content`      TEXT         NOT NULL,
  `image_url`    TEXT         DEFAULT NULL,
  `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `share_token`  VARCHAR(32)  DEFAULT NULL,
  INDEX `idx_journal_email` (`user_email`),
  UNIQUE INDEX `idx_journal_share_token` (`share_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bug reports submitted from a logged-in user's own profile (identity IS
-- stored, unlike the anonymous `reports` table above). Emailed to
-- BUG_REPORT_EMAIL via src/lib/mail.js on submit, best-effort. See
-- src/lib/bug-categories.js for the category list / archive window.
CREATE TABLE IF NOT EXISTS `bug_reports` (
  `id`                VARCHAR(50)  NOT NULL PRIMARY KEY,
  `reporter_email`    VARCHAR(255) NOT NULL,
  `reporter_username` VARCHAR(100) DEFAULT NULL,
  `subject`           VARCHAR(255) NOT NULL,
  `description`       TEXT         NOT NULL,
  `category`          VARCHAR(50)  NOT NULL DEFAULT 'other',
  `custom_category`   VARCHAR(150) DEFAULT NULL,
  `status`            VARCHAR(20)  NOT NULL DEFAULT 'open',
  `affected_count`    INT          NOT NULL DEFAULT 1,
  `duplicate_of`      VARCHAR(50)  DEFAULT NULL,
  `staff_notes`       TEXT         DEFAULT NULL,
  `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `resolved_at`       DATETIME     DEFAULT NULL,
  `resolved_by`       VARCHAR(100) DEFAULT NULL,
  INDEX `idx_bug_reports_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Conversation thread on a single bug report — see schema.sql's comment.
CREATE TABLE IF NOT EXISTS `bug_report_messages` (
  `id`                VARCHAR(50)  NOT NULL PRIMARY KEY,
  `bug_report_id`     VARCHAR(50)  NOT NULL,
  `sender_type`       VARCHAR(10)  NOT NULL,
  `sender_name`       VARCHAR(100) DEFAULT NULL,
  `body`              TEXT         NOT NULL,
  `delivery_channel`  VARCHAR(20)  DEFAULT NULL,
  `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_bug_report_messages_report` (`bug_report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Who confirmed "I have this too" on a report — see schema.sql's comment.
CREATE TABLE IF NOT EXISTS `bug_report_watchers` (
  `bug_report_id` VARCHAR(50)  NOT NULL,
  `email`         VARCHAR(255) NOT NULL,
  `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`bug_report_id`, `email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Admin-editable site text (role display names, a couple of page titles) —
-- see src/lib/site-content.js. Rows only exist here once a key has been
-- edited at least once; unedited keys just use the code default.
CREATE TABLE IF NOT EXISTS `site_content` (
  `content_key`   VARCHAR(100) NOT NULL PRIMARY KEY,
  `content_value` TEXT         NOT NULL,
  `label`         VARCHAR(255) NOT NULL,
  `category`      VARCHAR(50)  NOT NULL DEFAULT 'general',
  `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Friend requests/friendships. status: 'pending' or 'accepted' — decline/
-- cancel just deletes the row.
CREATE TABLE IF NOT EXISTS `friendships` (
  `id`              VARCHAR(50)  NOT NULL PRIMARY KEY,
  `requester_email` VARCHAR(255) NOT NULL,
  `addressee_email` VARCHAR(255) NOT NULL,
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'pending',
  `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `responded_at`    DATETIME     DEFAULT NULL,
  INDEX `idx_friendships_requester` (`requester_email`),
  INDEX `idx_friendships_addressee` (`addressee_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-user notifications (friend requests/accepts, journal shares) — distinct
-- from the existing global staff-only `notifications` table, which has no
-- recipient column.
CREATE TABLE IF NOT EXISTS `user_notifications` (
  `id`              VARCHAR(50)  NOT NULL PRIMARY KEY,
  `recipient_email` VARCHAR(255) NOT NULL,
  `type`            VARCHAR(50)  NOT NULL,
  `message`         TEXT         NOT NULL,
  `link`            VARCHAR(500) DEFAULT NULL,
  `is_read`         TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`       DATETIME    DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user_notifications_recipient` (`recipient_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Real server-verified sessions for the httpOnly `iekcorbar_session` cookie
-- (see src/lib/session.js). token is a random id, not a JWT, so a session
-- can be revoked instantly by deleting the row.
CREATE TABLE IF NOT EXISTS `sessions` (
  `token`      CHAR(64)     NOT NULL PRIMARY KEY,
  `user_email` VARCHAR(255) NOT NULL,
  `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `expires_at` DATETIME     NOT NULL,
  `is_staff`   TINYINT(1)   NOT NULL DEFAULT 0,
  INDEX `idx_sessions_email` (`user_email`),
  INDEX `idx_sessions_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `password_resets` (
  `token`      CHAR(64)     NOT NULL PRIMARY KEY,
  `user_email` VARCHAR(255) NOT NULL,
  `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `expires_at` DATETIME     NOT NULL,
  `used_at`    DATETIME     DEFAULT NULL,
  INDEX `idx_password_resets_email` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `passkey_credentials` (
  `id`            VARCHAR(255) NOT NULL PRIMARY KEY,
  `user_email`    VARCHAR(255) NOT NULL,
  `public_key`    TEXT         NOT NULL,
  `counter`       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `device_type`   VARCHAR(20)  NOT NULL DEFAULT 'singleDevice',
  `backed_up`     TINYINT(1)   NOT NULL DEFAULT 0,
  `transports`    VARCHAR(255) DEFAULT NULL,
  `name`          VARCHAR(100) DEFAULT NULL,
  `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `last_used_at`  DATETIME     DEFAULT NULL,
  INDEX `idx_passkey_email` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wiki_pages` (
  `id`          VARCHAR(64)  NOT NULL PRIMARY KEY,
  `slug`        VARCHAR(255) NOT NULL UNIQUE,
  `title`       VARCHAR(255) NOT NULL,
  `category`    VARCHAR(100) DEFAULT NULL,
  `content`     LONGTEXT     DEFAULT NULL,
  `updated_by`  VARCHAR(100) DEFAULT 'Staff',
  `parent_id`   VARCHAR(64)  DEFAULT NULL,
  `attachments` LONGTEXT     DEFAULT NULL,
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `wiki_revisions` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `page_id`     VARCHAR(64)  NOT NULL,
  `content`     LONGTEXT     DEFAULT NULL,
  `updated_by`  VARCHAR(100) DEFAULT 'Staff',
  `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY `page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `events` (
  `id`          VARCHAR(64)  NOT NULL PRIMARY KEY,
  `title`       VARCHAR(255) NOT NULL,
  `description` TEXT         DEFAULT NULL,
  `date`        VARCHAR(20)  DEFAULT NULL,
  `time`        VARCHAR(20)  DEFAULT NULL,
  `timezone`    VARCHAR(30)  DEFAULT 'UTC+2',
  `image`       TEXT         DEFAULT NULL,
  `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `event_registrations` (
  `id`          VARCHAR(64)  NOT NULL PRIMARY KEY,
  `event_id`    VARCHAR(64)  NOT NULL,
  `username`    VARCHAR(100) NOT NULL,
  `registered_at` DATETIME  DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `unique_reg` (`event_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `moderation_logs` (
  `id`           VARCHAR(64)  NOT NULL PRIMARY KEY,
  `username`     VARCHAR(100) NOT NULL,
  `type`         VARCHAR(50)  NOT NULL,
  `reason`       TEXT         DEFAULT NULL,
  `staff_member` VARCHAR(100) DEFAULT NULL,
  `date`         DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `player_history` (
  `id`           INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `day`          VARCHAR(10)  NOT NULL UNIQUE,
  `players`      INT          NOT NULL DEFAULT 0,
  `peak_time`    VARCHAR(10)  DEFAULT NULL,
  `last_updated` VARCHAR(20)  DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `carousel_sets` (
  `id`         VARCHAR(64)  NOT NULL PRIMARY KEY,
  `name`       VARCHAR(255) NOT NULL,
  `is_active`  TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- set_id/sort_order are legacy (unused since carousel_item_sets below, a
-- many-to-many join table letting one slide belong to several sets at
-- once) — see schema.sql's comment for why they're never DROPped.
CREATE TABLE IF NOT EXISTS `carousel_items` (
  `id`         VARCHAR(64)  NOT NULL PRIMARY KEY,
  `src`        LONGTEXT     NOT NULL,
  `title`      VARCHAR(255) DEFAULT NULL,
  `desc_text`  TEXT         DEFAULT NULL,
  `set_id`     VARCHAR(64)  DEFAULT NULL,
  `sort_order` INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `carousel_item_sets` (
  `item_id`    VARCHAR(64) NOT NULL,
  `set_id`     VARCHAR(64) NOT NULL,
  `sort_order` INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`item_id`, `set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `polls` (
  `id`           VARCHAR(64)  NOT NULL PRIMARY KEY,
  `question`     TEXT         NOT NULL,
  `options_json` LONGTEXT     DEFAULT '[]',
  `voters_json`  LONGTEXT     DEFAULT '[]',
  `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `updates` (
  `id`       VARCHAR(64)  NOT NULL PRIMARY KEY,
  `title`    VARCHAR(255) NOT NULL,
  `category` VARCHAR(100) DEFAULT 'General',
  `date`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `author`   VARCHAR(100) DEFAULT NULL,
  `content`  LONGTEXT     DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `update_categories` (
  `name` VARCHAR(100) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `settings` (
  `setting_key`   VARCHAR(100) NOT NULL PRIMARY KEY,
  `setting_value` TEXT         DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `starter_guide` (
  `id`               VARCHAR(64)  NOT NULL PRIMARY KEY,
  `title`            VARCHAR(255) NOT NULL,
  `content`          LONGTEXT     DEFAULT NULL,
  `display_order`    INT          NOT NULL DEFAULT 0,
  `attachment_url`   TEXT         DEFAULT NULL,
  `attachment_name`  VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `starter_guide` ADD COLUMN IF NOT EXISTS `attachment_url` TEXT DEFAULT NULL;
ALTER TABLE `starter_guide` ADD COLUMN IF NOT EXISTS `attachment_name` VARCHAR(255) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS `rules` (
  `id`            VARCHAR(64)  NOT NULL PRIMARY KEY,
  `title`         VARCHAR(255) NOT NULL,
  `content`       LONGTEXT     DEFAULT NULL,
  `display_order` INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- "Tip of the Day" — see src/lib/schema.sql for the full design note.
-- Seeded with real facts about this specific server (verified against its
-- actual installed plugins/data this session — CMI, Jobs, QuickShop,
-- BetonQuest, TheBrewingProject, the landclaim system, real vote sites),
-- not generic Minecraft trivia.
CREATE TABLE IF NOT EXISTS `daily_tips` (
  `id`         VARCHAR(64) NOT NULL PRIMARY KEY,
  `content`    TEXT        NOT NULL,
  `active`     BOOLEAN     NOT NULL DEFAULT TRUE,
  `created_at` DATETIME    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `daily_tips` (`id`, `content`) VALUES
('tip-1', 'Vote for Iekcorbar every 24 hours on each site under /vote — real votes help us climb the public server rankings and bring new players in.'),
('tip-2', 'Set multiple homes so you can fast-travel between your builds, farms, and shops in seconds.'),
('tip-3', 'Land claims support roles like Member and Co-Owner, each with their own permissions (doors, chests, redstone, and more) — share a build with friends without handing over full control.'),
('tip-4', 'Pick up a job to start earning money for actions you''re probably already doing — mining, building, farming, and more.'),
('tip-5', 'Run your own shop and let other players buy directly from your chest at the price you set.'),
('tip-6', 'New here? You need to be whitelisted before you can join — register on this website and it happens automatically.'),
('tip-7', 'Brewing is a real crafting system with actual recipes to discover, not just flavor text — check the Brewing page to get started.'),
('tip-8', 'You can change your in-game skin anytime from your website profile — no resource pack required.'),
('tip-9', 'Questlines are scattered across the world with real objectives and rewards — keep an eye out for quest givers as you explore.');

-- 2026-09-05 batch — real facts about that day's new features (Ingredient
-- Hunt curios, drinking/tipsy status + milk cure, Brewing Leaderboard, the
-- rebuilt wiki), same "verified, not invented" bar as tip-1 through tip-9.
INSERT IGNORE INTO `daily_tips` (`id`, `content`) VALUES
('tip-10', 'A new brewing ingredient is hidden somewhere on the site every day — look for a small pulsing leaf icon. Find it before midnight and it''s yours for good.'),
('tip-11', 'Not every ingredient you find unlocks a recipe. Some are just curios — pure collectibles for players who like completing a full set.'),
('tip-12', 'Successfully brewed something? Click it in your Brewing Collection to drink it. Just don''t be surprised if the page reacts.'),
('tip-13', 'Feeling a brew''s effects for too long? A Glass of Milk in the Shop clears it early — or just wait it out.'),
('tip-14', 'The Brewing Leaderboard ranks players four different ways: most brews, best quality ever pulled, recipes mastered, and ingredients collected.'),
('tip-15', 'The wiki is organized into browsable chapters — pick one to see its topics, or search to find anything across every chapter at once.'),
('tip-16', 'Every wiki article has a share button that copies a direct link — handy for pasting into Discord or in-game chat.'),
('tip-17', 'Your profile shows real, live server data — rank, homes used, and exact playtime, pulled straight from the server.'),
('tip-18', 'Building a barrel or still for brewing? The Brewing page''s Blueprints tab has real layer-by-layer reference sheets.');

CREATE TABLE IF NOT EXISTS `vote_sites` (
  `id`   VARCHAR(64)  NOT NULL PRIMARY KEY,
  `name` VARCHAR(255) NOT NULL,
  `url`  TEXT         NOT NULL,
  `icon` VARCHAR(50)  DEFAULT '⚙️'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `vote_log` (
  `id`        VARCHAR(64)  NOT NULL PRIMARY KEY,
  `username`  VARCHAR(100) NOT NULL,
  `site_name` VARCHAR(255) DEFAULT NULL,
  `timestamp` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `rewarded`  TINYINT(1)   DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `vote_reward_config` (
  `id`          INT          NOT NULL PRIMARY KEY DEFAULT 1,
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
  `command`     TEXT         DEFAULT NULL,
  `description` TEXT         DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `community_projects` (
  `id`               VARCHAR(64)  NOT NULL PRIMARY KEY,
  `slug`             VARCHAR(255) NOT NULL UNIQUE,
  `title`            VARCHAR(255) NOT NULL,
  `category`         VARCHAR(100) DEFAULT NULL,
  `status`           VARCHAR(50)  DEFAULT 'open',
  `description`      LONGTEXT     DEFAULT NULL,
  `max_participants` INT          DEFAULT 10,
  `created_by`       VARCHAR(100) DEFAULT NULL,
  `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `project_signups` (
  `id`           VARCHAR(64)  NOT NULL PRIMARY KEY,
  `project_id`   VARCHAR(64)  NOT NULL,
  `username`     VARCHAR(100) NOT NULL,
  `signed_up_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `unique_signup` (`project_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `notifications` (
  `id`         VARCHAR(64)  NOT NULL PRIMARY KEY,
  `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `is_read`    TINYINT(1)   DEFAULT 0,
  `type`       VARCHAR(50)  DEFAULT NULL,
  `message`    TEXT         DEFAULT NULL,
  `link`       VARCHAR(500) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `reports` (
  `id`           VARCHAR(64)  NOT NULL PRIMARY KEY,
  `reporter`     VARCHAR(100) DEFAULT NULL,
  `accused`      VARCHAR(100) DEFAULT NULL,
  `category`     VARCHAR(100) DEFAULT NULL,
  `reason`       TEXT         DEFAULT NULL,
  `status`       VARCHAR(50)  DEFAULT 'open',
  `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `resolved_by`  VARCHAR(100) DEFAULT NULL,
  `resolution`   TEXT         DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- `category` was missing from the original table even though the report
-- form and staff panel both already read/write it — this ALTER makes an
-- existing production table (created before this fix) match.
ALTER TABLE `reports` ADD COLUMN IF NOT EXISTS `category` VARCHAR(100) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS `chats` (
  `id`        VARCHAR(64)  NOT NULL PRIMARY KEY,
  `sender`    VARCHAR(100) NOT NULL,
  `receiver`  VARCHAR(100) NOT NULL,
  `message`   TEXT         DEFAULT NULL,
  `image_url` TEXT         DEFAULT NULL,
  `timestamp` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `read`      TINYINT(1)   DEFAULT 0,
  KEY `sender` (`sender`),
  KEY `receiver` (`receiver`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Safe to re-run: adds image_url to a `chats` table that already exists from an earlier
-- run of this script (which had no image support yet).
ALTER TABLE `chats` ADD COLUMN IF NOT EXISTS `image_url` TEXT DEFAULT NULL;

CREATE TABLE IF NOT EXISTS `player_stats` (
  `username`       VARCHAR(100) NOT NULL PRIMARY KEY,
  `online`         TINYINT(1)   DEFAULT 0,
  `playtime_hours` DECIMAL(10,2) DEFAULT 0,
  `kills`          INT          DEFAULT 0,
  `deaths`         INT          DEFAULT 0,
  `mob_kills`      INT          DEFAULT 0,
  `blocks_mined`   INT          DEFAULT 0,
  `balance`        DECIMAL(15,2) DEFAULT 0,
  `elite_coins`    INT          DEFAULT 0,
  `lands_claimed`  INT          DEFAULT 0,
  `jobs_json`      LONGTEXT     DEFAULT '[]',
  `plugin_placeholders_json` LONGTEXT DEFAULT NULL,
  `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Real, verified votes — see src/lib/schema.sql for the design note.
CREATE TABLE IF NOT EXISTS `votifier_events` (
  `id`             VARCHAR(50)  NOT NULL PRIMARY KEY,
  `username`       VARCHAR(100) NOT NULL,
  `service_name`   VARCHAR(100) NOT NULL,
  `address`        VARCHAR(100) DEFAULT NULL,
  `vote_timestamp` VARCHAR(50)  DEFAULT NULL,
  `received_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_votifier_events_user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors of the server's real banned-players.json / whitelist.json — see
-- src/lib/schema.sql for the full design note.
CREATE TABLE IF NOT EXISTS `banned_players` (
  `uuid`       VARCHAR(36)  NOT NULL PRIMARY KEY,
  `username`   VARCHAR(100) NOT NULL,
  `created`    VARCHAR(50)  DEFAULT NULL,
  `source`     VARCHAR(100) DEFAULT NULL,
  `expires`    VARCHAR(50)  DEFAULT NULL,
  `reason`     TEXT         DEFAULT NULL,
  `synced_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `whitelisted_players` (
  `uuid`       VARCHAR(36)  NOT NULL PRIMARY KEY,
  `username`   VARCHAR(100) NOT NULL,
  `synced_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit trail for the Staff Panel > Console tab (admin-only raw RCON terminal).
-- See src/lib/schema.sql for the full design note.
CREATE TABLE IF NOT EXISTS `rcon_console_log` (
  `id`            VARCHAR(50)  NOT NULL PRIMARY KEY,
  `command`       TEXT         NOT NULL,
  `response`      TEXT         DEFAULT NULL,
  `staff_member`  VARCHAR(100) NOT NULL,
  `date`          DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Device push tokens for the Expo mobile app. Keyed by email (not a numeric user id —
-- `users` has one, but getUsers()/login never select/return it; the rest of this
-- database identifies users by email too, so this follows that instead of introducing
-- an id the app never sees).
CREATE TABLE IF NOT EXISTS `device_tokens` (
  `id`               VARCHAR(64)  NOT NULL PRIMARY KEY,
  `user_email`       VARCHAR(255) NOT NULL,
  `platform`         VARCHAR(20)  NOT NULL,
  `expo_push_token`  VARCHAR(255) NOT NULL,
  `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `unique_expo_push_token` (`expo_push_token`),
  KEY `idx_device_tokens_user` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BREWING MINI-GAME (website companion to the in-game Brewery plugin recipes.yml —
-- recipe_json holds the cook/distill/age/mix steps and quality-tier names ported
-- from there). No seed data here on purpose — staff populate the 29 starter recipes
-- + 2 blueprints from the site itself (Brewing page → "Starterdata seeden", staff-only),
-- which POSTs to /api/brewing/seed and reaches this table through the app, not SQL.
CREATE TABLE IF NOT EXISTS `brewing_recipes` (
  `id`          VARCHAR(50)  NOT NULL PRIMARY KEY,
  `slug`        VARCHAR(100) NOT NULL UNIQUE,
  `difficulty`  INT          NOT NULL DEFAULT 1,
  `recipe_json` LONGTEXT     NOT NULL,
  `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `brewing_sessions` (
  `id`            VARCHAR(50)  NOT NULL PRIMARY KEY,
  `username`      VARCHAR(100) NOT NULL,
  `recipe_id`     VARCHAR(50)  NOT NULL,
  `quality_tier`  INT          NOT NULL DEFAULT 0,
  `failed`        BOOLEAN      NOT NULL DEFAULT FALSE,
  `started_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `ready_at`      DATETIME     NOT NULL,
  KEY `idx_brewing_sessions_user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Ingredient Hunt — see src/lib/schema.sql for the full design note.
CREATE TABLE IF NOT EXISTS `ingredient_hunt_finds` (
  `id`              VARCHAR(50)  NOT NULL PRIMARY KEY,
  `username`        VARCHAR(100) NOT NULL,
  `ingredient_name` VARCHAR(100) NOT NULL,
  `found_date`      DATE         NOT NULL,
  `found_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `consumed`        BOOLEAN      NOT NULL DEFAULT FALSE,
  UNIQUE KEY `idx_hunt_user_date` (`username`, `found_date`),
  KEY `idx_hunt_user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `brewing_blueprints` (
  `id`          VARCHAR(50)  NOT NULL PRIMARY KEY,
  `slug`        VARCHAR(100) NOT NULL UNIQUE,
  `title`       VARCHAR(255) NOT NULL,
  `type`        VARCHAR(50)  NOT NULL,
  `layers_json` LONGTEXT     NOT NULL,
  `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SHOP — see src/lib/schema.sql for the design notes (staff-configurable
-- RCON command templates, since the server's real economy "take money"
-- syntax isn't confirmed anywhere in this codebase).
CREATE TABLE IF NOT EXISTS `shop_items` (
  `id`           VARCHAR(50)  NOT NULL PRIMARY KEY,
  `slug`         VARCHAR(150) NOT NULL UNIQUE,
  `name`         VARCHAR(255) NOT NULL,
  `description`  TEXT         DEFAULT NULL,
  `price`        DOUBLE       NOT NULL,
  `give_command` VARCHAR(255) DEFAULT NULL,
  `image_url`    TEXT         DEFAULT NULL,
  `active`       BOOLEAN      DEFAULT TRUE,
  `clears_tipsy_effect` BOOLEAN NOT NULL DEFAULT FALSE,
  `currency`     VARCHAR(20)  NOT NULL DEFAULT 'balance',
  `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `shop_items` MODIFY COLUMN `give_command` VARCHAR(255) DEFAULT NULL;
-- Website-only "Gears" currency items (currency='website') never touch RCON
-- at all — no online check, no balance take, no in-game give — so they
-- don't need a give_command. See src/lib/schema.sql for the full design note.
ALTER TABLE `shop_items` ADD COLUMN IF NOT EXISTS `currency` VARCHAR(20) NOT NULL DEFAULT 'balance';

-- Real, ready-to-buy cure for the website's cosmetic "tipsy" effect (see
-- src/lib/tipsy-effect.js) — staff can reprice/rename/delete this like any
-- other shop item, clears_tipsy_effect is what actually matters. Paid with
-- website "Gears" (not real in-game balance) so it's buyable even when the
-- Minecraft server itself is unreachable — see the currency column above.
INSERT IGNORE INTO `shop_items` (`id`, `slug`, `name`, `description`, `price`, `active`, `clears_tipsy_effect`, `currency`) VALUES
('glass-of-milk', 'glass-of-milk', 'Glass of Milk', 'Clears any lingering tipsy effect from a brew you drank on the website.', 5, 1, 1, 'website');

CREATE TABLE IF NOT EXISTS `shop_purchases` (
  `id`            VARCHAR(50)  NOT NULL PRIMARY KEY,
  `username`      VARCHAR(100) NOT NULL,
  `item_id`       VARCHAR(50)  NOT NULL,
  `item_name`     VARCHAR(255) NOT NULL,
  `price_paid`    DOUBLE       NOT NULL,
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'failed',
  `take_message`  TEXT         DEFAULT NULL,
  `give_message`  TEXT         DEFAULT NULL,
  `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_shop_purchases_user` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `shop_settings` (
  `id`           INT          NOT NULL PRIMARY KEY DEFAULT 1,
  `take_command` VARCHAR(255) DEFAULT 'cmi money take {player} {amount}',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ─────────────────────────────────────────────
-- SEED DATA
-- ─────────────────────────────────────────────

-- USERS
INSERT IGNORE INTO `users` (`email`, `password`, `role`, `mc_username`) VALUES
('mjckappers@gmail.com', '_8iek', 'admin', 'IekjeBR36');

-- PLAYER HISTORY
INSERT IGNORE INTO `player_history` (`day`, `players`, `peak_time`, `last_updated`) VALUES
('Mon', 12, '19:00', ''),
('Tue', 18, '20:30', ''),
('Wed', 15, '19:30', ''),
('Thu',  0, '19:29', '2026-07-09'),
('Fri', 35, '22:00', ''),
('Sat',  1, '21:28', '2026-07-04'),
('Sun',  0, '07:50', '2026-07-12');

-- CAROUSEL SETS & ITEMS — one default set, marked active, holding the two
-- original seed screenshots. Staff can create further sets and switch which
-- one is active from Settings -> Carousel Manager.
INSERT IGNORE INTO `carousel_sets` (`id`, `name`, `is_active`) VALUES
('default', 'Default', 1);

INSERT IGNORE INTO `carousel_items` (`id`, `src`, `title`, `desc_text`, `set_id`) VALUES
('1', '/images/castle.png', 'The Copper Shogunate Capital',     'Our primary hub where ancient Japanese architecture blends seamlessly with steam power, cherry blossoms, and mechanical airships.', 'default'),
('2', '/images/boss.png',   'Weekly Boss Raid: Shogun Automaton','Assemble your squad at the Adventure Guild to battle high-level mechanical elites and loot legendary custom trinkets.', 'default');

INSERT IGNORE INTO `carousel_item_sets` (`item_id`, `set_id`, `sort_order`) VALUES
('1', 'default', 0),
('2', 'default', 1);

-- WIKI PAGES
INSERT IGNORE INTO `wiki_pages` (`id`, `slug`, `title`, `category`, `content`, `updated_by`, `updated_at`) VALUES
('1', 'races-of-iekcorbar', 'Races of Iekcorbar', 'Races',
'### Choose Your Path: Steampunk & Mythical Races\n\nOn **iekcorbar**, your character belongs to one of four legendary races, combining mechanical ingenuity and traditional Japanese folklore.\n\n#### 1. Automaton (Kikai-Jin)\n*Mechanical entities powered by pressurized steam and ancient scrolls.*\n* **Bonus**: Immune to fire damage for 5 seconds (1 min cooldown), +10% mining speed.\n* **Weakness**: -20% movement speed when in deep water.\n\n#### 2. Kitsune (Kitsune-Zoku)\n*Mystical fox spirits with magical affinities and illusory tricks.*\n* **Bonus**: Night vision in dark caves, double jump every 10 seconds.\n* **Weakness**: +15% more damage from heavy iron weapons.\n\n#### 3. Tengu (Tengu-Zoku)\n*Avian mountain protectors commanding wind and steel.*\n* **Bonus**: Slow falling when crouching mid-air, +10% bow velocity.\n* **Weakness**: +20% fall damage when wings are not deployed.\n\n#### 4. Human (Ningen)\n*Versatile craftspeople pushing the boundaries of steampunk technology.*\n* **Bonus**: +15% gold earnings from all jobs, can learn any 2 skills from other races.',
'IekcorbarAdmin', '2026-07-03 19:11:49'),

('2', 'steampunk-professions', 'Steampunk Professions & Jobs', 'Jobs',
'### Earn Money: Jobs in the Brass Metropolis\n\nWe use a custom steampunk jobs system where players earn currency ($) and skills by performing server activities.\n\n#### Available Jobs:\n1. **Machinist**: Craft mechanisms, gearboxes, and steam boilers.\n2. **Alchemist**: Brew steampunk concoctions and special beers.\n3. **Samurai**: Protect the cities from Elite Mobs.\n4. **Merchant**: Trade with the Villager Market.\n\nType `/jobs join <jobname>` in-game to start!',
'IekcorbarAdmin', '2026-07-04 07:11:49'),

('3', 'the-art-of-brewing', 'The Art of Steampunk Brewing', 'Brewery',
'### The Brewery: Sake, Beer, and Elixirs\n\nThe **Brewery** is a community project where players set up custom barrel-aging systems to ferment special recipes.\n\n#### Brewing Process:\n1. **Boil**: Place ingredients in a cauldron over a fire. Wait for the correct cooking duration.\n2. **Distill**: Place the brew in a brewing stand with glowstone dust.\n3. **Age**: Place the brew in a wooden barrel and let it age for a set number of in-game days.\n\n#### Popular Recipes:\n* **Sakura Blossom Sake**: Boil wheat for 8 min, distill twice, age in Birch barrel 3 years. Grants Speed III.\n* **Steam-Charged Stout**: Boil cocoa beans and redstone for 15 min, age in Dark Oak barrel. Grants Haste II.',
'IekcorbarMod', '2026-07-04 16:11:49'),

('4', 'claiming-territory', 'Claiming Territory with Gouw', 'Gouw',
'### Gouw: Protect Your Steampunk Outpost\n\nLand claims run on **Gouw** - the in-house claim system (successor of the old Lands plugin). Everything goes through `/gouw` (aliases `/g` and `/claim`) or its **menu**.\n\n#### The menu (GUI)\n`/gouw` without arguments opens the gouw menu:\n* **Main**: chunk stats, claim, autoclaim (a 5x5 square), unclaim (shift-click = whole gouw), border flash and rename.\n* **Flags**: per trusted member what they may do - **Build** (break/place/buckets), **Access** (chests, doors, buttons, beds), **Mob** (attack mobs) and **PvP**. New friends get everything on by default.\n* **Admin** (staff only): delete gouwen, re-import, reload.\n\n#### Player commands\n* `/gouw claim` - claim the chunk you stand in.\n* `/gouw autoclaim [r]` - claim a square around you (r = radius in chunks, default 2).\n* `/gouw unclaim` / `/gouw unclaim all` - release one chunk or the whole gouw.\n* `/gouw trust <player>` / `/gouw untrust <player>` - share your gouw with friends.\n* `/gouw members` - list who you trust.\n* `/gouw rename <name>` - name your gouw.\n* `/gouw info` / `/gouw list` / `/gouw near [r]` - see what is around you.\n* `/gouw border` - briefly light up the outline of your gouw.\n* `/gouw leave` - step out of a gouw you were trusted in.\n\n#### Staff\n`/gouw admin delete | import | reload | bypass` - remove any gouw, re-run the old Lands import, reload claims.json, or toggle walking through claims while building.\n\nStrangers walking into your gouw get an action-bar notice and a gold-dust border pulse; explosions never destroy claimed chunks and pistons cannot push blocks across the border.',
'IekcorbarAdmin', '2026-07-04 19:11:49'),

('5', 'economy', 'Economy', 'Economy',
'There are 2 kinds of currencies; regular money and elite coins. The elite coins can be spent at the adventure guild. For everything else, the regular in-game money is used.\n\nCommands:\n- `/balance` — to check your regular money\n- `/p` — click on the 3rd bell and check the stats to see your elite coin balance',
'IekcorbarAdmin', '2026-07-09 18:15:15');

-- EVENTS
INSERT IGNORE INTO `events` (`id`, `title`, `description`, `date`, `time`, `timezone`, `image`) VALUES
('1', 'The Great Sakura Airship Derby',       'Race through the sakura floating islands in custom steam-propelled airships! Speed wins prizes including Legendary Machinist Trinkets.', '2026-07-06', '19:00', 'UTC+2', 'derby'),
('2', 'Steampunk Boss Raid: Shogun Automaton', 'Join forces at the World Spawn to defeat the level 150 Elite Mob. Bring your best gear and coordination.',                               '2026-07-09', '20:30', 'UTC+2', 'boss');

-- MODERATION LOGS
INSERT IGNORE INTO `moderation_logs` (`id`, `username`, `type`, `reason`, `staff_member`, `date`) VALUES
('1', 'NaughtySteve',  'kick', 'Spamming the chat with copper gear ads',    'IekcorbarMod',   '2026-07-04 18:11:49'),
('2', 'HackerGamer',   'ban',  'X-Ray hacking in the Steampunk Dungeon',    'IekcorbarAdmin', '2026-07-02 19:11:49'),
('3', 'iekjebr36',     'note', 'we need to pay attention',                  'antimatter87',   '2026-07-04 19:17:31');

-- UPDATES
INSERT IGNORE INTO `updates` (`id`, `title`, `category`, `date`, `author`, `content`) VALUES
('1', 'Steam Pressures & Sakura Season Launch', 'General', '2026-07-01 19:11:49', 'IekcorbarAdmin',
'### Welcome to Season 1 of Iekcorbar!\nWe have officially lit the steam boilers and opened the gates to the public.\n\n**Patch Highlights:**\n* **Sakura Quests**: Earn Yen and copper gears by completing daily challenges.\n* **Boilers**: Pressurized furnaces now process ores 20% faster.\n* **Balance adjustments**: Kitsune double jump cooldown reduced to 10s.'),

('2', 'Brewery Recipe Bugfix & Balance Update', 'Fixes', '2026-07-03 19:11:49', 'IekcorbarMod',
'### Bugfixes and Brewery Balance\n\n* **Brewery Fix**: Resolved a bug where Sakura Blossom Mead caused nausea.\n* **Land Claims**: Increased maximum claims per pilot from 10 chunks to 15 chunks.');

-- UPDATE CATEGORIES
INSERT IGNORE INTO `update_categories` (`name`) VALUES
('General'),
('Fixes'),
('Survival'),
('Staff');

-- SETTINGS
INSERT IGNORE INTO `settings` (`setting_key`, `setting_value`) VALUES
('java_version', '26.1.2'),
('map_url', '');

-- STARTER GUIDE
INSERT IGNORE INTO `starter_guide` (`id`, `title`, `content`, `display_order`) VALUES
('1', 'Read the rules',         'Read the **[server rules](/rules)** to understand our community values before you start playing.',                                                                                         1),
('2', 'Watch out for Elite Mobs','While you start your adventure, be aware of **Elite mobs** — they are harder to beat. They wander randomly and can be found in buildings spawned below ground, on the surface, and in the air!', 2),
('3', 'Check your E menu',      'Check out your **E menu**. Here you can find your crafting grid on the right, which has shortcuts to your quest jobs, pyrofishing menu, pyromining menu and warps.',                        3),
('4', 'Explore the Wiki',       'Continue reading in the **[Wiki](/wiki)** — it covers races, jobs, brewing, land claiming and much more!',                                                                                  4);

-- RULES
INSERT IGNORE INTO `rules` (`id`, `title`, `content`, `display_order`) VALUES
('1',  '🧑‍🤝‍🧑 Be Kind & Respectful',       '• **Be friendly** to everyone\n• **No bullying, harassment, or toxic behavior**\n• **Don''t pretend** to be staff or other players\n• If you find a bug, **report it** — don''t abuse it',  1),
('2',  '🚫 No Farming or Grinding',          'This server is about adventure and RPG, not repetitive farming.\n\n**Not allowed:**\n• AFK farms\n• Repeating the same action for XP or money\n• Automated redstone farms\n• AFK fishing\n• Mining loops or autoclickers\n\n**Allowed:**\n• Normal gameplay\n• Exploring\n• Fighting mobs normally\n• Building',  2),
('3',  '🏡 Land Claims',                     '• **Claim only** what you use\n• **Don''t block** other players with your claims\n• **Don''t claim** around dungeons or structures\n• **No intentional griefing** anywhere',  3),
('4',  '⚔️ EliteMobs',                       '• **Don''t trap or cheese** bosses\n• **Don''t farm** dungeons repeatedly\n• **Don''t exploit** loot or coins\n• **Solo dungeons = solo** (unless allowed)',  4),
('5',  '🏗️ BetterStructures',               '• Explore structures **normally**\n• **Don''t loot** the same structure over and over\n• **Don''t force** structures to regenerate',  5),
('6',  '🎣 PyroFishing & PyroMining',        '• **No AFK fishing**\n• **No automated mining**\n• **No treasure** or enchant exploits',  6),
('7',  '🧪 Brewing & Garden',               '• **No mass-producing** potions\n• **No automatic** crop farms',  7),
('8',  '⚰️ MourningGraves',                  '• **Don''t camp** someone''s death location',  8),
('9',  '📜 Quests (QuestBorn)',              '• **No repeating quests** for extra rewards\n• **No alts**',  9),
('10', '🛒 Shops (QuickShop)',               '• **No scams**\n• **No price manipulation**\n• **No duping** or exploiting shops', 10),
('11', '🛏️ BedWars',                        '• **No teaming** with other teams\n• **No map exploits**\n• **Play fair**', 11),
('12', '💬 Discord & Social Rules',         '• **No spam**\n• **No harassment**\n• **No NSFW**\n• **No manipulation**\n• **No advertising**\n• **Respect staff decisions**', 12),
('13', '🛠️ Mods & Clients',                 '**Allowed:**\n• Litematica / schematics\n• OptiFine\n• Shaders\n• Minimaps (no radar)\n• Cosmetic mods\n\n**Not allowed:**\n• Hacked clients\n• X-ray\n• Autoclickers\n• Scripts\n• Transparent block packs', 13),
('14', '👥 Alt Accounts',                   '• **No alts** for farming, quests, or PvP\n• **No ban evasion** (we will notice)', 14),
('15', '🔨 Punishments',                    'Depending on the situation:\n• **Warning**\n• **Temporary ban**\n• **Permanent ban**\n• **Removal** of illegal items/farms', 15),
('16', '🎯 Final Rule',                     'If you''re unsure whether something is allowed, **ask staff first**.\n**Staff always have the final say.**', 16);

-- COMMUNITY PROJECTS
INSERT IGNORE INTO `community_projects` (`id`, `slug`, `title`, `category`, `status`, `description`, `max_participants`, `created_by`, `created_at`, `updated_at`) VALUES
('1', 'grand-clocktower-restoration', 'Grand Clocktower Restoration', 'Infrastructure', 'open',
'Help us restore the grand clocktower in the main town square! We need copper gears, redstone components, and spruce logs.',
12, 'IekcorbarAdmin', '2026-07-05 19:11:36', '2026-07-05 19:11:36');

-- PROJECT SIGNUPS
INSERT IGNORE INTO `project_signups` (`id`, `project_id`, `username`) VALUES
('1', '1', 'IekcorbarAdmin');

-- VOTE REWARD CONFIG (default)
INSERT IGNORE INTO `vote_reward_config` (`id`, `enabled`, `command`, `description`) VALUES
(1, 1, 'give {player} diamond 3', 'Grant 3 Diamonds per registered vote');

-- STAFF ROLES & PERMISSIONS (per-role Staff Panel tab toggles) — see
-- src/lib/schema.sql's fuller comment on `roles`. No seed rows on purpose:
-- 'admin' is hardcoded (never a row here) and 'moderator' falls back to its
-- pre-this-feature tab set until an admin saves a real row for it from
-- Settings > Roles & Permissions.
CREATE TABLE IF NOT EXISTS `roles` (
  `name` VARCHAR(50) PRIMARY KEY,
  `label` VARCHAR(100) NOT NULL,
  `is_staff` BOOLEAN NOT NULL DEFAULT TRUE,
  `tabs` JSON NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Newsletter send history — see schema.sql's comment.
CREATE TABLE IF NOT EXISTS `newsletters` (
  `id` VARCHAR(50) PRIMARY KEY,
  `subject` VARCHAR(255) NOT NULL,
  `body` TEXT NOT NULL,
  `sent_by` VARCHAR(100) NOT NULL,
  `recipient_count` INT NOT NULL DEFAULT 0,
  `sent_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Admin-only shared file drop — see schema.sql's comment.
CREATE TABLE IF NOT EXISTS `admin_shared_files` (
  `id` VARCHAR(50) PRIMARY KEY,
  `filename` VARCHAR(255) NOT NULL,
  `original_name` VARCHAR(255) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `description` TEXT NULL,
  `uploaded_by` VARCHAR(100) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NEWS & UPCOMING — see schema.sql's fuller comment. Distinct from the
-- `updates` changelog above: posts are grouped by topic and only players
-- following a topic get a bell notification for a post under it.
CREATE TABLE IF NOT EXISTS `news_topics` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) UNIQUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `news_posts` (
  `id` VARCHAR(50) PRIMARY KEY,
  `title` VARCHAR(255) NOT NULL,
  `type` VARCHAR(20) NOT NULL DEFAULT 'news',
  `topic` VARCHAR(100) NOT NULL DEFAULT 'General',
  `content` LONGTEXT NOT NULL,
  `author` VARCHAR(100) NOT NULL,
  `link` VARCHAR(500) NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `news_topic_follows` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_email` VARCHAR(255) NOT NULL,
  `topic` VARCHAR(100) NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uniq_news_follow` (`user_email`, `topic`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `news_topics` (`name`) VALUES
('General'),
('Minecraft News'),
('Server Updates'),
('Website Features'),
('Events');

-- STAFF APPLICATIONS — see schema.sql's fuller comment. Player applications
-- for a Staff role (Moderator, unpaid/volunteer), reviewed from the Staff
-- Panel's "Staff Applications" tab.
CREATE TABLE IF NOT EXISTS `staff_applications` (
  `id` VARCHAR(50) PRIMARY KEY,
  `applicant_email` VARCHAR(255) NOT NULL,
  `applicant_username` VARCHAR(100) DEFAULT NULL,
  `age` INT NOT NULL,
  `availability` VARCHAR(255) NOT NULL,
  `timezone` VARCHAR(100) DEFAULT NULL,
  `discord_username` VARCHAR(100) DEFAULT NULL,
  `experience` TEXT DEFAULT NULL,
  `reference_name` VARCHAR(150) NOT NULL,
  `reason` TEXT NOT NULL,
  `contribution` TEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
  `staff_notes` TEXT DEFAULT NULL,
  `reviewed_by` VARCHAR(100) DEFAULT NULL,
  `reviewed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_staff_applications_status` (`status`),
  INDEX `idx_staff_applications_applicant` (`applicant_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────
-- DONE
-- ─────────────────────────────────────────────
SELECT 'Iekcorbar database seeded successfully!' AS status;
