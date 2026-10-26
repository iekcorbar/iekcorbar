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
