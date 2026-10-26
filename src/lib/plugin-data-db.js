import mysql from 'mysql2/promise';

// Read-only access to the database the IekcorbarSiteSync plugin *actually*
// syncs into. Root-caused 2026-08-13: the plugin's own config.yml on the
// live server has always pointed at a separate RevivenNode MySQL instance
// (`s30266_iekcorbar_player_data` on sql1.revivenode.com — shared with
// LuckPerms, Jobs, BetonQuest, QuickShop, and everything else that DB
// holds) — a completely different server from the website's own DB_HOST
// (127.0.0.1, Plesk-local). db.js's plugin-synced reads (player_stats,
// votifier_events, banned_players, whitelisted_players, lands/jobs totals)
// had always queried the website's own DB, where those tables exist but
// were never written to by anything — hence "Access Control shows nothing",
// "0 total votes cast" even after voting, stale lands-claimed counts, etc.
// This isn't a deploy-timing issue, it's been two different databases the
// whole time. Same read-only-by-convention pattern as cmi-db.js — the
// website never writes here, only RCON (talking to the live server
// directly) or the plugin itself ever changes this data.
let pool = null;

function getPool() {
  if (!pool) {
    if (!process.env.PLUGIN_DB_HOST) return null;
    pool = mysql.createPool({
      host: process.env.PLUGIN_DB_HOST,
      port: parseInt(process.env.PLUGIN_DB_PORT) || 3306,
      user: process.env.PLUGIN_DB_USER,
      password: process.env.PLUGIN_DB_PASSWORD,
      database: process.env.PLUGIN_DB_NAME,
      waitForConnections: true,
      connectionLimit: 8,
      queueLimit: 0,
      connectTimeout: 3000
    });
  }
  return pool;
}

export async function pluginDataQuery(sql, params = []) {
  const p = getPool();
  if (!p) throw new Error('PLUGIN_DB_HOST not configured');
  const queryPromise = p.execute(sql, params);
  const timeoutPromise = new Promise((_, reject) =>
    setTimeout(() => reject(new Error('Plugin data DB query timeout')), 3000)
  );
  const [rows] = await Promise.race([queryPromise, timeoutPromise]);
  return rows;
}
