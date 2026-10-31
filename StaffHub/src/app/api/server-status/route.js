import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { may } from '@/lib/acl';
import fs from 'node:fs';
import path from 'node:path';

export const dynamic = 'force-dynamic';

// Server status file path (written by a cron job or monitoring script on the VPS)
const STATUS_FILE = process.env.SERVER_STATUS_FILE || '/app/server-status.json';

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });

  let status = {
    online: false,
    players: 0,
    maxPlayers: 0,
    ram: { used: 0, total: 0, percent: 0 },
    disk: { used: 0, total: 0, percent: 0 },
    cpu: 0,
    tps: 20,
    version: '26.2',
    plugins: [],
    lastUpdate: null,
  };

  try {
    const content = fs.readFileSync(STATUS_FILE, 'utf-8');
    const data = JSON.parse(content);
    status = { ...status, ...data, online: true };
  } catch (e) {
    // File not found or parse error - return offline status
    status.online = false;
    status.error = 'Status bestand niet gevonden. Configureer SERVER_STATUS_FILE env var.';
  }

  // Add DB stats
  const d = getDb();
  status.db = {
    documents: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='page'").get().n,
    folders: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='folder'").get().n,
    users: d.prepare('SELECT count(*) AS n FROM users').get().n,
    versions: d.prepare('SELECT count(*) AS n FROM versions').get().n,
  };

  return Response.json(status);
}