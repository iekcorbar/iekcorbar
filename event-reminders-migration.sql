-- Event reminders: remembers which reminder (24h / 1h) was already sent per
-- event, so nobody gets duplicates. Run once in phpMyAdmin. Safe to run twice.
CREATE TABLE IF NOT EXISTS event_reminders_sent (
  event_id VARCHAR(50) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
