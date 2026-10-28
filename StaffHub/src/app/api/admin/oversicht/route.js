// Admin-ruimte, ALLEEN voor owners: wie zit er in de hub (met apparaat en
// verval), hoeveel plaats het kost, en welke sessies zijn in te trekken.
//
// Waarom dit bestaat: de eigenaar kon vanmiddag niet raden wat er met z'n
// sessies gebeurde - er staan er simpelweg dertien in een tabel die niemand
// ziet. Dit maakt die tabel zichtbaar. Tokens zelf worden NOOIT meegestuurd
// (retracten gaat op rowid), want een gelekte token is een ingelogd account.
import fs from 'node:fs';
import path from 'node:path';
import { getDb, now, DATA_DIR, BLOBS } from '@/lib/db';
import { me } from '@/lib/auth';

export const dynamic = 'force-dynamic';

async function ownerOnly() {
  const u = await me();
  if (!u) return { err: Response.json({ error: 'login' }, { status: 401 }) };
  if (u.role !== 'owner') return { err: Response.json({ error: 'alleen owners' }, { status: 403 }) };
  return { u };
}

/** Groote van de bak: db + WAL + content-addressede blobs (diepe tel-lijk). */
function opslag() {
  let bytes = 0, bestanden = 0;
  try {
    for (const f of fs.readdirSync(DATA_DIR)) {
      const p = path.join(DATA_DIR, f);
      const st = fs.statSync(p);
      if (st.isFile()) { bytes += st.size; bestanden++; }
    }
  } catch { /* nog niets */ }
  try {
    for (const pre of fs.readdirSync(BLOBS)) {
      const dir = path.join(BLOBS, pre);
      if (!fs.statSync(dir).isDirectory()) continue;
      for (const f of fs.readdirSync(dir)) {
        bytes += fs.statSync(path.join(dir, f)).size;
        bestanden++;
      }
    }
  } catch { /* blobs bestaan nog niet */ }
  return { bytes, bestanden };
}

export async function GET() {
  const { err } = await ownerOnly();
  if (err) return err;
  const d = getDb();
  const nu = now();
  const gebruikers = d.prepare('SELECT id, username, role, team, created FROM users ORDER BY role DESC, username').all();
  const sessies = d.prepare('SELECT rowid AS id, user, agent, expires FROM sessions ORDER BY expires DESC LIMIT 200').all();
  return Response.json({
    gebruikers: gebruikers.map((g) => ({
      ...g,
      sessies: sessies.filter((s) => s.user === g.id && s.expires > nu).length,
      items: d.prepare('SELECT count(*) AS n FROM nodes WHERE owner = ?').get(g.id).n,
    })),
    sessies: sessies.map((s) => ({
      id: s.id, user: s.user, agent: s.agent || 'onbekend toestel',
      expires: s.expires, over: Math.max(0, Math.round((s.expires - nu) / 86400000)), verlopen: s.expires <= nu,
    })),
    telling: {
      mappen: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='folder'").get().n,
      documenten: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='page'").get().n,
      bestanden: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='file'").get().n,
      versies: d.prepare('SELECT count(*) AS n FROM versions').get().n,
      reacties: d.prepare('SELECT count(*) AS n FROM comments').get().n,
      davtokens: d.prepare('SELECT count(*) AS n FROM davtokens').get().n,
    },
    opslag: opslag(),
  });
}

/** Sessie intrekken (rowid) of een davtoken schrappen. */
export async function DELETE(req) {
  const { err } = await ownerOnly();
  if (err) return err;
  const p = new URL(req.url).searchParams;
  const d = getDb();
  if (p.get('sessie')) {
    d.prepare('DELETE FROM sessions WHERE rowid = ?').run(Number(p.get('sessie')));
    return Response.json({ ok: true });
  }
  if (p.get('token')) {
    d.prepare('DELETE FROM davtokens WHERE token = ?').run(String(p.get('token')));
    return Response.json({ ok: true });
  }
  return Response.json({ error: 'niets opgegeven' }, { status: 400 });
}
