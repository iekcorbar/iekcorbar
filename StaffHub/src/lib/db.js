// StaffHub data layer: node:sqlite (ingebouwd in Node 24, dus geen native builds
// op Windows noch in de docker-image) + content-addressede blobs op schijf.
// Alles is synchro - voor een staff-tool is dat prima en houdt de routes simpel.
import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const DATA = process.env.STAFFHUB_DATA || path.join(process.cwd(), 'data');
export const DATA_DIR = DATA;
export const BLOBS = path.join(DATA, 'blobs');

let db;

function migrate(d) {
  d.exec(`
  CREATE TABLE IF NOT EXISTS users(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    pass TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'staff',      -- 'owner' | 'staff'
    team TEXT,
    created INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS sessions(
    token TEXT PRIMARY KEY,
    user INTEGER NOT NULL,
    expires INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS davtokens(
    token TEXT PRIMARY KEY,
    user INTEGER NOT NULL,
    label TEXT,
    created INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS nodes(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent INTEGER,                          -- NULL = root
    name TEXT NOT NULL,
    type TEXT NOT NULL,                      -- 'folder' | 'file' | 'page'
    vis TEXT,                                -- NULL = erven | 'all' | 'team' | 'owner'
    team TEXT,
    owner INTEGER NOT NULL,
    created INTEGER NOT NULL,
    updated INTEGER NOT NULL
  );
  CREATE UNIQUE INDEX IF NOT EXISTS ux_child ON nodes(parent, name);
  CREATE TABLE IF NOT EXISTS versions(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    node INTEGER NOT NULL,
    sha TEXT NOT NULL,
    size INTEGER NOT NULL,
    mime TEXT,
    who INTEGER NOT NULL,
    created INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS ix_ver_node ON versions(node);
  CREATE TABLE IF NOT EXISTS pages(
    node INTEGER PRIMARY KEY,
    html TEXT NOT NULL,
    who INTEGER NOT NULL,
    updated INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS comments(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    node INTEGER NOT NULL,
    who INTEGER NOT NULL,
    text TEXT NOT NULL,
    created INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS ix_com_node ON comments(node);
  -- Vertaalde varianten van een handleiding, per taal één rij. Bewust een losse
  -- tabel: de pages-tabel blijft de originele tekst (de bron van waarheid), een
  -- vertaling is afgeleid en mag opnieuw gemaakt worden.
  -- (Geen backticks in dit SQL-blok: dat sluit de JS-template-literal.)
  CREATE TABLE IF NOT EXISTS vertalingen(
    node INTEGER NOT NULL,
    lang TEXT NOT NULL,
    html TEXT NOT NULL,
    who INTEGER,
    updated INTEGER NOT NULL,
    PRIMARY KEY(node, lang)
  );
  CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT);
  -- Inlogbegrenzing: vijf foute wachtwoorden per account binnen een kwartier
  -- geeft een tijdelijke slot. In de bak, want een containerherstart mag een
  -- lopende blokkade niet wegtoveren.
  CREATE TABLE IF NOT EXISTS loginwacht(
    username TEXT PRIMARY KEY,
    pogingen INTEGER NOT NULL DEFAULT 0,
    laatste INTEGER NOT NULL DEFAULT 0,
    slot INTEGER NOT NULL DEFAULT 0
  );
  -- Eenvoudige audit-log: wie logde in, waar ging het mis, vanaf welk adres.
  -- Vóór dit bestand was er geen spoor van mislukte pogingen.
  CREATE TABLE IF NOT EXISTS audit(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    at INTEGER NOT NULL,
    area TEXT NOT NULL,
    actie TEXT NOT NULL,
    wie TEXT,
    detail TEXT,
    ip TEXT
  );
  CREATE INDEX IF NOT EXISTS ix_audit_at ON audit(at DESC);
  `);
  // Twee kolommen die pas later bedacht zijn: bestaande bakken (de VPS draait er
  // al op) krijgen ze bij het openen, precies zoals SiteSync dat met zijn
  // deep-sync-kolommen doet. hasColumn() is hier want SQLite kent geen
  // "ADD COLUMN IF NOT EXISTS".
  if (!hasColumn(d, 'users', 'prefs')) d.exec('ALTER TABLE users ADD COLUMN prefs TEXT');
  if (!hasColumn(d, 'sessions', 'agent')) d.exec('ALTER TABLE sessions ADD COLUMN agent TEXT');
}

function hasColumn(d, table, col) {
  return d.prepare(`PRAGMA table_info(${table})`).all().some((c) => c.name === col);
}

export function getDb() {
  if (db) return db;
  fs.mkdirSync(BLOBS, { recursive: true });
  db = new DatabaseSync(path.join(DATA, 'staffhub.db'));
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;');
  migrate(db);
  return db;
}

/**
 * Regel in de audit-log. `req` mag nul zijn. Bewust GEEN wachtwoorden of tokens
 * in `detail`, en alleen het IP voor zover nodig om een aanval te herkennen.
 * De log wordt na 90 dunnen: een staff-tool heeft geen eeuwen administratie nodig.
 */
let auditsSinds = 0;
export function logAudit(req, { area, actie, wie = null, detail = null }) {
  try {
    const h = req?.headers;
    const ip = String(h?.get('x-real-ip') || h?.get('x-forwarded-for')?.split(',')[0]?.trim() || '').slice(0, 45) || null;
    const d = getDb();
    d.prepare('INSERT INTO audit(at,area,actie,wie,detail,ip) VALUES(?,?,?,?,?,?)')
      .run(Date.now(), String(area), String(actie), wie ? String(wie).slice(0, 24) : null, detail ? String(detail).slice(0, 200) : null, ip);
    if (++auditsSinds % 50 === 0) d.prepare('DELETE FROM audit WHERE at < ?').run(Date.now() - 90 * 86400000);
  } catch { /* logging mag nooit de route onderbreken */ }
}

// ---------------------------------------------------------------- blobs

export function storeBlob(buf) {
  const sha = crypto.createHash('sha256').update(buf).digest('hex');
  const dir = path.join(BLOBS, sha.slice(0, 2));
  const file = path.join(dir, sha);
  if (!fs.existsSync(file)) {
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(file + '.tmp', buf);
    fs.renameSync(file + '.tmp', file);
  }
  return sha;
}

export function blobPath(sha) {
  return path.join(BLOBS, sha.slice(0, 2), sha);
}

/** Blobs die door geen enkele versie meer worden gebruikt, van schijf halen. */
export function gcBlobs() {
  const d = getDb();
  const live = new Set(d.prepare('SELECT sha FROM versions').all().map((r) => r.sha));
  let n = 0;
  for (const pre of fs.readdirSync(BLOBS)) {
    const dir = path.join(BLOBS, pre);
    if (!fs.statSync(dir).isDirectory()) continue;
    for (const f of fs.readdirSync(dir)) {
      if (!live.has(f)) { fs.rmSync(path.join(dir, f), { force: true }); n++; }
    }
  }
  return n;
}

// ---------------------------------------------------------------- nodes

export const now = () => Date.now();
export const clean = (s) => String(s || '').replace(/[\\/:*?"<>|\u0000-\u001f]/g, '').trim().slice(0, 180);

export function findChild(parent, name) {
  return getDb().prepare('SELECT * FROM nodes WHERE parent IS ? AND name = ?').get(parent ?? null, name) || null;
}

export function uniqueName(parent, wanted) {
  if (!findChild(parent, wanted)) return wanted;
  const dot = wanted.lastIndexOf('.');
  const base = dot > 0 ? wanted.slice(0, dot) : wanted;
  const ext = dot > 0 ? wanted.slice(dot) : '';
  for (let i = 2; i < 1000; i++) {
    const cand = `${base} (${i})${ext}`;
    if (!findChild(parent, cand)) return cand;
  }
  return `${base}-${now()}${ext}`;
}

export function latestVersion(nodeId) {
  return getDb().prepare('SELECT * FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(nodeId) || null;
}

/** Pad van root naar dit node (exclusief het node zelf), oudst eerst. */
export function ancestors(node) {
  const d = getDb();
  const out = [];
  let cur = node;
  while (cur && cur.parent != null) {
    cur = d.prepare('SELECT * FROM nodes WHERE id = ?').get(cur.parent);
    if (cur) out.unshift(cur);
    if (out.length > 64) break;
  }
  return out;
}

export function deleteNodeTree(id) {
  const d = getDb();
  for (const c of d.prepare('SELECT id FROM nodes WHERE parent = ?').all(id)) deleteNodeTree(c.id);
  d.prepare('DELETE FROM comments WHERE node = ?').run(id);
  d.prepare('DELETE FROM pages WHERE node = ?').run(id);
  d.prepare('DELETE FROM vertalingen WHERE node = ?').run(id);
  d.prepare('DELETE FROM versions WHERE node = ?').run(id);
  d.prepare('DELETE FROM nodes WHERE id = ?').run(id);
}
