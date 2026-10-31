// Trashbin: "wissen" is hier terugdraaibaar. Items verhuizen naar een
// root-map 'Trashbin'; hun thuisbasis (parent + naam) blijft staan in de
// meta-tabel onder 'trash:<id>' — precies genoeg om ze later terug te zetten.
// Pas 'legenen' maakt ze echt weg (deleteNodeTree + gcBlobs zoals toen).
import { getDb, findChild, uniqueName, now, deleteNodeTree, gcBlobs } from '@/lib/db';
import { me } from '@/lib/auth';
import { canWrite, canSee, visibleChildren, may } from '@/lib/acl';

export const dynamic = 'force-dynamic';

const NAAM = 'Trashbin';

function trashNode(d, u) {
  let f = findChild(null, NAAM);
  if (!f || f.type !== 'folder') {
    const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(NULL,?,?,?,?,?)')
      .run(NAAM, 'folder', u.id, now(), now()).lastInsertRowid);
    f = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
  }
  return f;
}

/** Zit `kind` (of is het zelf) in de deelboom van `voorouder`? */
function inDeelBoom(d, voorouderId, kindId) {
  return !!d.prepare('WITH RECURSIVE s(id) AS (SELECT ? UNION SELECT id FROM nodes WHERE parent IN s) SELECT 1 FROM s WHERE id = ?')
    .get(voorouderId, kindId);
}

function zetMeta(d, id, thuishaven) {
  d.prepare('INSERT INTO meta(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v = excluded.v')
    .run('trash:' + id, JSON.stringify(thuishaven));
}
function leesMeta(d, id) {
  const r = d.prepare('SELECT v FROM meta WHERE k = ?').get('trash:' + id);
  try { return r ? JSON.parse(r.v) : null; } catch { return null; }
}
function wisMeta(d, id) { d.prepare('DELETE FROM meta WHERE k = ?').run('trash:' + id); }

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const d = getDb();
  const ton = trashNode(d, u);
  const items = visibleChildren(u, ton.id).map((n) => {
    const m = leesMeta(d, n.id);
    let thuishaven = m?.p === null || m?.p === undefined ? 'root' : null;
    if (m && m.p != null) {
      const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(m.p);
      thuishaven = p ? p.name : '?';
    }
    return { id: n.id, name: n.name, type: n.type, at: m?.at ?? n.updated, thuishaven, nogDaar: !m || m.p == null || !!d.prepare('SELECT 1 AS x FROM nodes WHERE id = ?').get(m.p) };
  });
  return Response.json({ id: ton.id, items });
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { actie, ids } = await req.json().catch(() => ({}));
  const lijst = Array.isArray(ids) ? ids.map(Number).filter(Boolean) : [];
  const d = getDb();
  const ton = trashNode(d, u);

  if (actie === 'prullen') {
    if (!may(u, 'trash')) return Response.json({ error: 'verboden', gedaan: 0 }, { status: 403 });   // cap 'trash'
    let gedaan = 0;
    for (const id of lijst) {
      const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
      if (!n || !canWrite(u, n)) continue;
      if (n.id === ton.id || inDeelBoom(d, n.id, ton.id)) continue;    // de bak in de bak: nee
      if (n.parent === ton.id) continue;                                // staat al in de bak
      zetMeta(d, n.id, { p: n.parent, n: n.name, at: now(), by: u.id });
      const bestaand = findChild(ton.id, n.name);
      const nm = bestaand && bestaand.id !== n.id ? uniqueName(ton.id, n.name) : n.name;
      d.prepare('UPDATE nodes SET parent = ?, name = ?, updated = ? WHERE id = ?').run(ton.id, nm, now(), n.id);
      gedaan++;
    }
    return Response.json({ ok: true, gedaan });
  }

  if (actie === 'herstel') {
    if (!may(u, 'trash')) return Response.json({ error: 'verboden', gedaan: 0, mislukt: 0 }, { status: 403 });
    let gedaan = 0, mislukt = 0;
    for (const id of lijst) {
      const n = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
      const m = leesMeta(d, id);
      if (!n || !m || !canWrite(u, n)) { mislukt++; continue; }
      if (n.parent === ton.id && m.p === ton.id) { wisMeta(d, id); continue; }   // thuis was de bak zelf
      const p = m.p == null ? null : d.prepare('SELECT * FROM nodes WHERE id = ?').get(m.p);
      if (m.p != null && (!p || p.type !== 'folder')) { mislukt++; continue; }    // thuis is weggehaald
      if (p && p.type === 'folder' && inDeelBoom(d, n.id, p.id)) { mislukt++; continue; }
      const bots = m.p == null ? findChild(null, m.n) : findChild(m.p, m.n);
      const nm = bots && bots.id !== n.id ? uniqueName(m.p ?? null, m.n) : m.n;
      d.prepare('UPDATE nodes SET parent = ?, name = ?, updated = ? WHERE id = ?').run(m.p ?? null, nm, now(), n.id);
      wisMeta(d, id);
      gedaan++;
    }
    return Response.json({ ok: true, gedaan, mislukt });
  }

  if (actie === 'legenen') {
    // Legen ís echt verwijderen: daarvoor is de cap 'purge' nodig (uitgereikt
    // door de eigenaar of vanzelfsprekend de eigenaar zelf). Vóór 0.5.0 mocht
    // dit van iedereen met schrijfrechten - te breed voor een onomkeerbare daad.
    if (!may(u, 'purge')) return Response.json({ error: 'verboden', gedaan: 0 }, { status: 403 });
    let gedaan = 0;
    for (const kind of d.prepare('SELECT * FROM nodes WHERE parent = ?').all(ton.id)) {
      if (!canWrite(u, kind) || !canSee(u, kind)) continue;
      deleteNodeTree(kind.id);
      wisMeta(d, kind.id);
      gedaan++;
    }
    gcBlobs();
    return Response.json({ ok: true, gedaan });
  }

  return Response.json({ error: 'actie onbekend' }, { status: 400 });
}
