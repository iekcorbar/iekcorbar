-- Account linking for NEW registrations (2026-09-27): link status per account.
-- Existing players keep NULL in all four columns = active/linked, untouched.
-- Run once in phpMyAdmin (database `iekcorbar` -> SQL). If you run it a second
-- time MySQL says "Duplicate column name" — that's harmless.
ALTER TABLE users
  ADD COLUMN link_status VARCHAR(20) NULL DEFAULT NULL,
  ADD COLUMN link_deadline DATETIME NULL DEFAULT NULL,
  ADD COLUMN link_code VARCHAR(10) NULL DEFAULT NULL,
  ADD COLUMN link_code_expires DATETIME NULL DEFAULT NULL;
