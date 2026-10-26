// One-time self-repair for the `chats` table, so chat always lives in MySQL.
//
// Why this exists: production's `chats` table was created before image
// attachments and has no `image_url` column. Every chat query that mentions
// that column threw, db.js silently fell back to the server's own db.json, and
// chat ended up split across two stores: reads came from db.json while some
// writes (mark-as-read, the nav unread count) went to MySQL — so an unread
// badge could never clear, and messages sent since then only exist in db.json.
//
// ensureChatsTable() (called at the top of EVERY chat method in db.js, so they
// all agree on one store — if it throws, they all fall back together):
//   1. adds the missing `image_url` column,
//   2. imports every chat message sitting in db.json into MySQL (INSERT IGNORE
//      by id, so it is safe to repeat) and only then removes them from db.json,
//      after writing a backup file next to it.
// Any failure (e.g. the database user may not ALTER) throws before anything is
// removed, leaving things exactly as they were.
import fs from 'fs';

export function createChatsPreparer({ query, readFallbackJson, writeFallbackJson, dbFile, log = console.log }) {
  let ready = false;
  let running = null;

  async function importFallbackChats() {
    const rows = (readFallbackJson().chats || []).filter(m => m && m.id && m.sender && m.receiver);
    if (rows.length === 0) return;

    for (const m of rows) {
      const when = m.timestamp ? new Date(m.timestamp) : new Date();
      await query(
        'INSERT IGNORE INTO chats (id, sender, receiver, message, image_url, timestamp, `read`) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [String(m.id), m.sender, m.receiver, m.message ?? null, m.imageUrl ?? null, Number.isNaN(when.getTime()) ? new Date() : when, m.read ? 1 : 0]
      );
    }

    // Everything reached MySQL: keep a copy, then drop exactly those messages
    // from db.json (re-read first, in case something was added meanwhile).
    const backup = dbFile.replace(/\.json$/i, '') + `.chats-backup-${Date.now()}.json`;
    fs.writeFileSync(backup, JSON.stringify(rows, null, 2), 'utf8');
    const imported = new Set(rows.map(m => String(m.id)));
    const latest = readFallbackJson();
    latest.chats = (latest.chats || []).filter(m => !imported.has(String(m && m.id)));
    writeFallbackJson(latest);
    log(`[chat] moved ${rows.length} message(s) from db.json into MySQL (backup: ${backup})`);
  }

  async function run() {
    const cols = await query("SHOW COLUMNS FROM chats LIKE 'image_url'");
    if (!cols || cols.length === 0) {
      await query('ALTER TABLE chats ADD COLUMN image_url TEXT NULL AFTER message');
      log('[chat] added the missing chats.image_url column');
    }
    await importFallbackChats();
  }

  return async function ensureChatsTable() {
    if (ready) return;
    if (!running) running = run().then(() => { ready = true; }).finally(() => { running = null; });
    return running;
  };
}
