-- Shop "Elite coins" currency (2026-10-26), SELF-HEALING version.
-- Run ONCE in Plesk > databases > iekcorbar (SQL tab) before deploying the
-- new site. Works on any current shape of shop_settings:
--   * table missing entirely (very old/never-created)  -> created fresh
--   * old shape without `enabled`  (the live table's actual state, see the
--     #1054 the first version of this file hit on 2026-10-26)             -> added
--   * already fully migrated                             -> every step no-ops.
-- After this, the Shop page's settings panel persists to MySQL for real
-- (until now an old shop_settings silently fell back to the local db.json).

-- 0) make sure the table itself exists at all (no-op when it does)
CREATE TABLE IF NOT EXISTS shop_settings (
  id INT PRIMARY KEY DEFAULT 1,
  take_command VARCHAR(255) DEFAULT 'cmi money take {player} {amount}',
  coins_take_command VARCHAR(255) DEFAULT 'elitecoins take {player} {amount}',
  enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1) add coins_take_command when missing (newer shape created by step 0 already has it)
SET @q = (SELECT IF(COUNT(*) = 0,
  "ALTER TABLE shop_settings ADD COLUMN coins_take_command VARCHAR(255) DEFAULT 'elitecoins take {player} {amount}'",
  "SELECT 'coins_take_command already present'")
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings'
    AND COLUMN_NAME = 'coins_take_command');
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) add enabled when missing (this is what failed with #1054 before)
SET @q = (SELECT IF(COUNT(*) = 0,
  "ALTER TABLE shop_settings ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE",
  "SELECT 'enabled already present'")
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings'
    AND COLUMN_NAME = 'enabled');
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) add take_command when missing (only ever existed on a hand-made table;
--    harmless check, keeps the next step safe on every shape)
SET @q = (SELECT IF(COUNT(*) = 0,
  "ALTER TABLE shop_settings ADD COLUMN take_command VARCHAR(255) DEFAULT 'cmi money take {player} {amount}'",
  "SELECT 'take_command already present'")
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings'
    AND COLUMN_NAME = 'take_command');
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) the single settings row (every column above has a DEFAULT, so a
--    column-minimal INSERT is safe on any shape reached through 0-3)
INSERT IGNORE INTO shop_settings (id) VALUES (1);
