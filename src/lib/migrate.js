import fs from 'fs';
import path from 'path';
import mysql from 'mysql2/promise';

async function getPool() {
  return mysql.createPool({
    host: process.env.DB_HOST || '127.0.0.1',
    port: parseInt(process.env.DB_PORT) || 3306,
    user: process.env.DB_USER || 'iekjeBR36',
    // 2026-10-26: echt wachtwoord hier weggehaald (bron gaat naar git) -
    // zie .env.local op dev-machine en server.
    password: process.env.DB_PASSWORD || 'TODO-in-env-local',
    database: process.env.DB_NAME || 'iekcorbar',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
  });
}

export async function runMigrations() {
  let connection;
  try {
    const pool = await getPool();
    connection = await pool.getConnection();

    console.log('Connected to MySQL server. Running schema migrations...');

    const schemaPath = path.join(process.cwd(), 'src', 'lib', 'schema.sql');
    const sql = fs.readFileSync(schemaPath, 'utf8');

    // Split SQL queries by semicolon
    const statements = sql
      .split(';')
      .map(s => s.trim())
      .filter(s => s.length > 0);

    for (const statement of statements) {
      await connection.query(statement);
    }
    console.log('Schema tables verified successfully.');

    // Migration / Seeding from db.json
    const dbJsonPath = path.join(process.cwd(), 'src', 'lib', 'db.json');
    if (fs.existsSync(dbJsonPath)) {
      console.log('Found local db.json file. Seeding MySQL database...');
      const rawData = fs.readFileSync(dbJsonPath, 'utf8');
      const data = JSON.parse(rawData);

      // Seed Users
      if (data.users && Array.isArray(data.users)) {
        for (const user of data.users) {
          await connection.query(
            `INSERT IGNORE INTO users (email, password, role, mc_username, custom_skin_url) VALUES (?, ?, ?, ?, ?)`,
            [user.email, user.password, user.role || 'player', user.mcUsername || null, user.customSkinUrl || null]
          );
        }
      }

      // Seed Wiki Pages
      if (data.wiki_pages && Array.isArray(data.wiki_pages)) {
        for (const p of data.wiki_pages) {
          await connection.query(
            `INSERT IGNORE INTO wiki_pages (id, slug, title, category, content, updated_by, parent_id, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
            [p.id, p.slug, p.title, p.category, p.content, p.updatedBy || 'Staff', p.parentId || null, p.updatedAt || new Date()]
          );
          if (p.revisions && Array.isArray(p.revisions)) {
            for (const r of p.revisions) {
              await connection.query(
                `INSERT INTO wiki_revisions (page_id, content, updated_by, updated_at) VALUES (?, ?, ?, ?)`,
                [p.id, r.content, r.updatedBy || 'Staff', r.updatedAt || new Date()]
              );
            }
          }
        }
      }

      // Seed Events
      if (data.events && Array.isArray(data.events)) {
        for (const e of data.events) {
          await connection.query(
            `INSERT IGNORE INTO events (id, title, description, date, time, timezone, image)
             VALUES (?, ?, ?, ?, ?, ?, ?)`,
            [e.id, e.title, e.description, e.date, e.time, e.timezone || 'UTC+2', e.image || null]
          );
        }
      }

      // Seed Event Registrations
      if (data.event_registrations && Array.isArray(data.event_registrations)) {
        for (const r of data.event_registrations) {
          await connection.query(
            `INSERT IGNORE INTO event_registrations (id, event_id, username) VALUES (?, ?, ?)`,
            [r.id, r.eventId, r.username]
          );
        }
      }

      // Seed Moderation Logs
      if (data.moderation_logs && Array.isArray(data.moderation_logs)) {
        for (const l of data.moderation_logs) {
          await connection.query(
            `INSERT IGNORE INTO moderation_logs (id, username, type, reason, staff_member, date)
             VALUES (?, ?, ?, ?, ?, ?)`,
            [l.id, l.username, l.type, l.reason, l.staffMember, l.date || new Date()]
          );
        }
      }

      // Seed Updates
      if (data.updates && Array.isArray(data.updates)) {
        for (const u of data.updates) {
          await connection.query(
            `INSERT IGNORE INTO updates (id, title, category, date, author, content)
             VALUES (?, ?, ?, ?, ?, ?)`,
            [u.id, u.title, u.category, u.date || new Date(), u.author, u.content]
          );
        }
      }

      // Seed Update Categories
      if (data.update_categories && Array.isArray(data.update_categories)) {
        for (const cat of data.update_categories) {
          await connection.query(
            `INSERT IGNORE INTO update_categories (name) VALUES (?)`,
            [cat]
          );
        }
      }

      // Seed Carousel Sets (falls back to a single synthesized "Default"
      // active set if db.json predates the sets feature, so pre-existing
      // carousel_items below always land somewhere instead of going orphaned).
      const carouselSets = (data.carousel_sets && Array.isArray(data.carousel_sets) && data.carousel_sets.length > 0)
        ? data.carousel_sets
        : [{ id: 'default', name: 'Default', isActive: true }];
      for (const s of carouselSets) {
        await connection.query(
          `INSERT IGNORE INTO carousel_sets (id, name, is_active) VALUES (?, ?, ?)`,
          [s.id, s.name, s.isActive ? 1 : 0]
        );
      }

      // Seed Carousel Items — carousel_items itself no longer carries a
      // set_id (a slide can belong to several sets now, see
      // carousel_item_sets, the many-to-many join table), so each old
      // db.json item's single setId becomes its first membership row.
      if (data.carousel_items && Array.isArray(data.carousel_items)) {
        for (const c of data.carousel_items) {
          await connection.query(
            `INSERT IGNORE INTO carousel_items (id, src, title, desc_text) VALUES (?, ?, ?, ?)`,
            [c.id, c.src, c.title, c.desc || '']
          );
          await connection.query(
            `INSERT IGNORE INTO carousel_item_sets (item_id, set_id, sort_order) VALUES (?, ?, ?)`,
            [c.id, c.setId || carouselSets[0].id, c.sortOrder ?? 0]
          );
        }
      }

      // Seed Polls
      if (data.polls && Array.isArray(data.polls)) {
        for (const pol of data.polls) {
          await connection.query(
            `INSERT IGNORE INTO polls (id, question, options_json, voters_json, created_at)
             VALUES (?, ?, ?, ?, ?)`,
            [pol.id, pol.question, JSON.stringify(pol.options || []), JSON.stringify(pol.voters || []), pol.createdAt || new Date()]
          );
        }
      }

      // Seed Starter Guide
      if (data.starter_guide && Array.isArray(data.starter_guide)) {
        for (const g of data.starter_guide) {
          await connection.query(
            `INSERT IGNORE INTO starter_guide (id, title, content, display_order) VALUES (?, ?, ?, ?)`,
            [g.id, g.title, g.content, g.order || 0]
          );
        }
      }

      // Seed Rules
      if (data.rules && Array.isArray(data.rules)) {
        for (const rule of data.rules) {
          await connection.query(
            `INSERT IGNORE INTO rules (id, title, content, display_order) VALUES (?, ?, ?, ?)`,
            [rule.id, rule.title, rule.content, rule.order || 0]
          );
        }
      }

      // Seed Vote Sites
      if (data.vote_sites && Array.isArray(data.vote_sites)) {
        for (const site of data.vote_sites) {
          await connection.query(
            `INSERT IGNORE INTO vote_sites (id, name, url, icon) VALUES (?, ?, ?, ?)`,
            [site.id, site.name, site.url, site.icon || '⚙️']
          );
        }
      }

      // Seed Community Projects
      if (data.community_projects && Array.isArray(data.community_projects)) {
        for (const cp of data.community_projects) {
          await connection.query(
            `INSERT IGNORE INTO community_projects (id, slug, title, category, status, description, max_participants, created_by, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [cp.id, cp.slug, cp.title, cp.category, cp.status || 'open', cp.description, cp.maxParticipants || 10, cp.createdBy, cp.createdAt || new Date(), cp.updatedAt || new Date()]
          );
        }
      }

      // Seed Project Signups
      if (data.project_signups && Array.isArray(data.project_signups)) {
        for (const ps of data.project_signups) {
          await connection.query(
            `INSERT IGNORE INTO project_signups (id, project_id, username, signed_up_at) VALUES (?, ?, ?, ?)`,
            [ps.id, ps.projectId, ps.username, ps.signedUpAt || new Date()]
          );
        }
      }

      // Seed Player History
      if (data.player_history && Array.isArray(data.player_history)) {
        for (const ph of data.player_history) {
          await connection.query(
            `INSERT IGNORE INTO player_history (day, players, peak_time, last_updated) VALUES (?, ?, ?, ?)`,
            [ph.day, ph.players || 0, ph.peakTime || '', ph.lastUpdated || '']
          );
        }
      }

      // Seed Settings
      if (data.settings) {
        for (const [key, val] of Object.entries(data.settings)) {
          await connection.query(
            `INSERT IGNORE INTO settings (setting_key, setting_value) VALUES (?, ?)`,
            [key, String(val)]
          );
        }
      }

      console.log('Database seeding complete!');
    }

    return true;
  } catch (err) {
    console.error('Migration error:', err.message);
    throw err;
  } finally {
    if (connection) connection.release();
  }
}
