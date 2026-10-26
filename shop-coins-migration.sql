-- Shop "Elite coins" currency (2026-10-26): run this ONCE on the website
-- MySQL (Plesk > databases > iekcorbar) before deploying the new site, so
-- staff-priced items can spend EliteMobs' Elite coins next to the Vault
-- balance. Idempotent-ish: MySQL errors if the column already exists - just
-- ignore that one error.
ALTER TABLE shop_settings
  ADD COLUMN coins_take_command VARCHAR(255) DEFAULT 'elitecoins take {player} {amount}';

-- The single settings row might not exist yet on a young database.
INSERT IGNORE INTO shop_settings (id, enabled) VALUES (1, TRUE);
