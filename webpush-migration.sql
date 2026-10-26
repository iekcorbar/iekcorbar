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
