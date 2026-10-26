-- Push notification preferences (Android app) — run once in phpMyAdmin (SQL tab).
-- Safe to run more than once: only creates the table if it doesn't exist yet.
-- Each row holds one user's on/off switches for push notifications (Profile page).
CREATE TABLE IF NOT EXISTS push_preferences (
  user_email VARCHAR(255) PRIMARY KEY,
  prefs_json TEXT NOT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
