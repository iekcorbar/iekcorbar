// Meldingen: een persoonlijke inbox per account. De bel in de header haalt ze
// op; routes die iets belangrijks doen (reactie, @vermelding, bewerking van
// andermans pagina, rechtenwijziging, mededeling van de eigenaar) schrijven er een.
// Een melding mag nooit de route onderbreken die hem veroorzaakt: alles hier is
// best-effort en slikt fouten.
import { getDb, now } from './db.js';
import { canSee } from './acl.js';

const KEEP_PER_USER = 200;
const MAX_AGE = 60 * 86400000;

function ensure(d) {
  d.exec(`CREATE TABLE IF NOT EXISTS notifications(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user INTEGER NOT NULL,          -- ontvanger
    kind TEXT NOT NULL,             -- comment | mention | edit | perms | announce
    text TEXT NOT NULL,
    node INTEGER,                   -- document om naartoe te springen
    from_user INTEGER,
    created INTEGER NOT NULL,
    is_read INTEGER NOT NULL DEFAULT 0
  );
  CREATE INDEX IF NOT EXISTS ix_notif_user ON notifications(user, is_read, id DESC);`);
}

let klaar = false;
function db() {
  const d = getDb();
  if (!klaar) { ensure(d); klaar = true; }
  return d;
}

/**
 * Meld één of meer ontvangers iets. `skip` = de veroorzaker zelf (krijgt nooit
 * een melding van eigen actie). Dubbele ongelezen meldingen van dezelfde soort
 * voor hetzelfde document binnen 10 minuten worden samengevoegd (anders levert
 * een pagina die je tien keer bewerkt tien regels op).
 */
export function notify(userIds, { kind, text, node = null, from = null }) {
  try {
    const d = db();
    const t = String(text).slice(0, 300);
    const ids = [...new Set(userIds)].filter((id) => id != null && id !== from);
    for (const uid of ids) {
      if (node != null && (kind === 'edit' || kind === 'comment')) {
        const dup = d.prepare('SELECT id FROM notifications WHERE user = ? AND kind = ? AND node = ? AND from_user IS ? AND is_read = 0 AND created > ?')
          .get(uid, kind, node, from, now() - 600000);
        if (dup) { d.prepare('UPDATE notifications SET text = ?, created = ? WHERE id = ?').run(t, now(), dup.id); continue; }
      }
      d.prepare('INSERT INTO notifications(user,kind,text,node,from_user,created) VALUES(?,?,?,?,?,?)').run(uid, kind, t, node, from, now());
      d.prepare('DELETE FROM notifications WHERE user = ? AND id NOT IN (SELECT id FROM notifications WHERE user = ? ORDER BY id DESC LIMIT ?)').run(uid, uid, KEEP_PER_USER);
    }
    if (Math.random() < 0.02) d.prepare('DELETE FROM notifications WHERE created < ?').run(now() - MAX_AGE);
  } catch { /* melden mag de route nooit stukmaken */ }
}

/** @gebruikersnamen in een tekst; alleen leden die dat document mogen zien. */
export function mentionedUsers(text, node, exceptId) {
  try {
    const names = [...new Set((String(text).match(/@([a-z0-9_]{2,24})/g) || []).map((m) => m.slice(1)))];
    if (!names.length) return [];
    const d = db();
    return names
      .map((n) => d.prepare('SELECT * FROM users WHERE username = ?').get(n))
      .filter((u) => u && u.id !== exceptId && (!node || canSee(u, node)))
      .map((u) => u.id);
  } catch { return []; }
}

export function listFor(userId, limit = 40) {
  const d = db();
  const rows = d.prepare(`SELECT n.id, n.kind, n.text, n.node, n.created, n.is_read AS read, u.username AS fromName
    FROM notifications n LEFT JOIN users u ON u.id = n.from_user
    WHERE n.user = ? ORDER BY n.id DESC LIMIT ?`).all(userId, limit);
  const unread = d.prepare('SELECT COUNT(*) AS c FROM notifications WHERE user = ? AND is_read = 0').get(userId).c;
  return { items: rows, unread };
}

export function markRead(userId, ids) {
  const d = db();
  if (ids === 'all') return d.prepare('UPDATE notifications SET is_read = 1 WHERE user = ? AND is_read = 0').run(userId).changes;
  let n = 0;
  for (const id of ids || []) n += d.prepare('UPDATE notifications SET is_read = 1 WHERE user = ? AND id = ?').run(userId, Number(id)).changes;
  return n;
}

export function clearRead(userId) {
  return db().prepare('DELETE FROM notifications WHERE user = ? AND is_read = 1').run(userId).changes;
}

/** Mededeling van de eigenaar: iedereen, één team of één lid. */
export function broadcast(from, text, audience) {
  const d = db();
  let users;
  if (audience?.type === 'team') users = d.prepare('SELECT id FROM users WHERE team = ?').all(String(audience.value));
  else if (audience?.type === 'user') users = d.prepare('SELECT id FROM users WHERE username = ?').all(String(audience.value));
  else users = d.prepare('SELECT id FROM users').all();
  notify(users.map((u) => u.id), { kind: 'announce', text, from });
  return users.filter((u) => u.id !== from).length;
}
