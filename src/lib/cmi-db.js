import mysql from 'mysql2/promise';

// Read-only access to the CMI plugin's OWN database — a completely
// separate RevivenNode MySQL instance from the website's own `iekcorbar` DB
// (see db.js). Unlike that file, this module deliberately exposes only
// SELECT queries and nothing else: the website never writes here. Every
// economy/balance change still goes through RCON (see api/shop/purchase),
// which keeps CMI's own live in-memory state authoritative — writing
// directly into this DB would desync from that until CMI next reloads.
// This exists purely to surface real CMI-only data (Rank, Homes,
// TotalPlayTime, ...) that no RCON command currently returns.
let pool = null;

function getPool() {
  if (!pool) {
    if (!process.env.CMI_DB_HOST) return null;
    pool = mysql.createPool({
      host: process.env.CMI_DB_HOST,
      port: parseInt(process.env.CMI_DB_PORT) || 3306,
      user: process.env.CMI_DB_USER,
      password: process.env.CMI_DB_PASSWORD,
      database: process.env.CMI_DB_NAME,
      waitForConnections: true,
      connectionLimit: 5,
      queueLimit: 0,
      connectTimeout: 3000
    });
  }
  return pool;
}

async function query(sql, params = []) {
  const p = getPool();
  if (!p) throw new Error('CMI_DB_HOST not configured');
  const queryPromise = p.execute(sql, params);
  const timeoutPromise = new Promise((_, reject) =>
    setTimeout(() => reject(new Error('CMI DB query timeout')), 3000)
  );
  const [rows] = await Promise.race([queryPromise, timeoutPromise]);
  return rows;
}

// Returns real CMI-only data for a player, or null if the DB is
// unreachable / the player has never been seen by CMI. Deliberately
// non-fatal for callers to fold into a bigger response (see
// api/player-stats) — this is a "nice to have if available" data source,
// not something anything else should hard-depend on being up.
export async function getCmiPlayerData(username) {
  try {
    const rows = await query(
      'SELECT Rank, Homes, TotalPlayTime, LastLoginTime FROM CMI_users WHERE username = ? LIMIT 1',
      [username]
    );
    if (!rows || rows.length === 0) return null;
    const row = rows[0];

    let homeNames = [];
    if (row.Homes) {
      try {
        homeNames = Object.keys(JSON.parse(row.Homes));
      } catch { /* malformed/empty Homes JSON — treat as no homes */ }
    }

    return {
      rank: row.Rank || null,
      homesCount: homeNames.length,
      homeNames,
      // CMI stores TotalPlayTime in milliseconds — real, precise tracking
      // (vs. the Bukkit PLAY_ONE_MINUTE statistic api/player-stats' RCON
      // path uses, which is known to be less reliable across some server
      // migrations/resets).
      totalPlayTimeHours: typeof row.TotalPlayTime === 'number' ? Math.round(row.TotalPlayTime / 3600000) : null,
      lastLoginTime: row.LastLoginTime || null
    };
  } catch (err) {
    console.warn(`[CMI DB Warning] ${err.message}`);
    return null;
  }
}

// Rank (CMI rank key) for many players in ONE query, for the members list.
// Non-fatal like getCmiPlayerData: unreachable DB -> empty map. Cached for 5 minutes
// so a busy members page does not hit the plugin database on every request.
let rankCache = { at: 0, map: {} };
export async function getCmiRanks(usernames) {
  if (Date.now() - rankCache.at < 5 * 60 * 1000) return rankCache.map;
  try {
    const names = [...new Set((usernames || []).filter(Boolean))];
    if (!names.length) return {};
    const rows = await query(
      `SELECT username, Rank FROM CMI_users WHERE username IN (${names.map(() => '?').join(',')})`,
      names
    );
    const map = {};
    for (const r of rows || []) if (r.username && r.Rank) map[String(r.username).toLowerCase()] = r.Rank;
    rankCache = { at: Date.now(), map };
    return map;
  } catch (err) {
    console.warn(`[CMI DB Warning] ${err.message}`);
    return {};
  }
}
