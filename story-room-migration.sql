-- Story Room (Staff Panel -> Story Room): staff storyline workspace.
-- The website also creates these tables by itself on first use
-- (db.js ensureStoryTables), so running this in phpMyAdmin is optional.
-- Safe to run more than once.

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

-- Guest links (2026-10-22): personal links for writers without a website
-- account. Also created automatically by db.js on first use.
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

-- Uploaded files (2026-10-22). Also created automatically by db.js.
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
