// Voor het pull-script op de pc van de eigenaar (scripts/pull-hub-models.py):
//   GET /api/changes?since=<ms>   → lijst bewerkte bbmodels (nieuwste versie per bestand)
//   GET /api/changes?id=<node>    → de inhoud van die nieuwste versie
// Inloggen met Basic auth (gebruikersnaam + WebDAV-token), zoals /dav.
import fs from 'node:fs';
import { getDb, blobPath, ensureEdits } from '@/lib/db';
import { me, userFromBasic } from '@/lib/auth';
import { canSee, may } from '@/lib/acl';

export const dynamic = 'force-dynamic';

export async function GET(req) {
  const u = (await me()) || userFromBasic(req.headers.get('authorization'));
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'upload')) return Response.json({ error: 'verboden' }, { status: 403 });
  const d = getDb();
  ensureEdits(d);
  const url = new URL(req.url);
  const id = Number(url.searchParams.get('id'));
  if (id) {
    const node = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
    if (!node || !canSee(u, node)) return Response.json({ error: 'niet gevonden' }, { status: 404 });
    const v = d.prepare('SELECT * FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(id);
    if (!v) return Response.json({ error: 'geen versie' }, { status: 404 });
    return new Response(fs.readFileSync(blobPath(v.sha)), { headers: { 'Content-Type': 'application/json' } });
  }
  const since = Number(url.searchParams.get('since')) || 0;
  const rows = d.prepare(`SELECT e.node AS id, n.name, MAX(e.created) AS created, MAX(e.version) AS version
      FROM model_edits e JOIN nodes n ON n.id = e.node
      WHERE e.created > ? GROUP BY e.node ORDER BY created`).all(since)
    .filter((r) => canSee(u, d.prepare('SELECT * FROM nodes WHERE id = ?').get(r.id)));
  return Response.json({ now: Date.now(), files: rows });
}
