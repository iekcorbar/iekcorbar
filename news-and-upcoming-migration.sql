-- News & Upcoming feature — run this once against the website's own
-- `iekcorbar` database (not the plugin's separate database).
-- Import via phpMyAdmin: select the `iekcorbar` database, go to the
-- "Import" tab, choose this file, click "Go". No copy/paste needed.

CREATE TABLE IF NOT EXISTS news_topics (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) UNIQUE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS news_posts (
  id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'news',
  topic VARCHAR(100) NOT NULL DEFAULT 'General',
  content LONGTEXT NOT NULL,
  author VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS news_topic_follows (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  topic VARCHAR(100) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_news_follow (user_email, topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO news_topics (name) VALUES ('General');
INSERT IGNORE INTO news_topics (name) VALUES ('Minecraft News');
INSERT IGNORE INTO news_topics (name) VALUES ('Server Updates');
INSERT IGNORE INTO news_topics (name) VALUES ('Website Features');
INSERT IGNORE INTO news_topics (name) VALUES ('Events');
