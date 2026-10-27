// One-time schema probe: table + column NAMES only (no rows, no player data,
// no credential output). Reads PLUGIN_DB_* from .env.local without echoing them.
const fs = require('fs');
const mysql = require('mysql2/promise');

for (const line of fs.readFileSync('.env.local', 'utf8').split(/\r?\n/)) {
  const m = line.match(/^([A-Z_][A-Z0-9_]*)=(.*)$/);
  if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^"|"$/g, '');
}

(async () => {
  const c = await mysql.createConnection({
    host: process.env.PLUGIN_DB_HOST, port: +(process.env.PLUGIN_DB_PORT || 3306),
    user: process.env.PLUGIN_DB_USER, password: process.env.PLUGIN_DB_PASSWORD,
    database: process.env.PLUGIN_DB_NAME, connectTimeout: 8000,
  });
  const [t] = await c.query(
    "SELECT TABLE_NAME t FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND (TABLE_NAME LIKE '%elite%' OR TABLE_NAME LIKE '%em_%' OR TABLE_NAME LIKE 'mmocore%' OR TABLE_NAME = 'PlayerData') ORDER BY t"
  );
  console.log('tables:', t.map(r => r.t).join(', '));
  for (const row of t) {
    const [cols] = await c.query(
      'SELECT COLUMN_NAME c FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION',
      [row.t]
    );
    console.log(row.t + ': ' + cols.map(x => x.c).join(', '));
  }
  await c.end();
})().catch(e => { console.log('ERR:', e.message); process.exit(0); });
