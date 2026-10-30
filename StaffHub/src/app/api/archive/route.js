// Archief uitpakken: ZIP uploaden in een map, de hub laat EERST zien wat er
// gaat gebeuren en pakt pas uit na een klik. Twee stappen, één route:
//   POST met multipart {file, parent}      -> plan + tijdelijk bewaar-token
//   POST met json {token}                  -> uitpakken naar de hub
// Waarom zo: uitpakken is de enige handeling in de hub die in één klap tientallen
// bestanden kan OVERSCHRIJVEN. Revisies redden je (oude versies blijven), maar
// "per ongeluk 40 handleidingen vervangen door een oude zip" blijft vervelend.
// Eerst kijken, dan pas doen. Het archief zelf wordt niet in de hub gezet; het
// tijdelijke bestand (data/archief) dunnt vanzelf na een dag.
//
// Veiligigheid: unzip.js weigert zip64/versleuteld/zip-bommen/'..'-paden; hier
// overheen: alleen schrijven waar de vrager schrijfrechten heeft (kanWrite, bij
// BEIDE stappen gecheckt - de map kan tussen plan en bevestigd veranderd zijn),
// en het token is een willekeurige uuid die alleen bij deze vrager hoort.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { getDb, DATA_DIR, clean, uniqueName, findChild, storeBlob, now, logAudit } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee, canWrite, may } from '@/lib/acl';
import { plan, data, ZipFout } from '@/lib/unzip';

export const dynamic = 'force-dynamic';
// Een grote zip uitpakken is lokaal werk (geen model), maar wel blob-schrijven
// per bestand; de poort hangt niet, Node werkt door. maxDuration is de zekerheid.
export const maxDuration = 300;

const ARCHIEF_MAP = path.join(DATA_DIR, 'archief');
const MAX = 512 * 1024 * 1024;

// mime per extensie: de browser geeft die niet door uit een zip, en de hub
// bruikt het mime-type wél voor voorbeeld/icoon. Typische staff-bestanden.
const EXT = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp',
  bmp: 'image/bmp', svg: 'image/svg+xml', ico: 'image/vnd.microsoft.icon',
  pdf: 'application/pdf', txt: 'text/plain', md: 'text/markdown', log: 'text/plain',
  json: 'application/json', csv: 'text/csv', yml: 'text/plain', yaml: 'text/plain',
  html: 'text/html', htm: 'text/html', xml: 'text/xml', toml: 'text/plain',
  mp3: 'audio/mpeg', ogg: 'audio/ogg', wav: 'audio/wav', m4a: 'audio/mp4',
  mp4: 'video/mp4', webm: 'video/webm',
  zip: 'application/zip', rar: 'application/vnd.rar', '7z': 'application/x-7z-compressed',
  tar: 'application/x-tar', gz: 'application/gzip', jar: 'application/java-archive',
  doc: 'application/msword', docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xls: 'application/vnd.ms-excel', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ppt: 'application/vnd.ms-powerpoint', pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  bbmodel: 'application/json', f3d: 'application/octet-stream',
};
const mimeVoor = (naam) => EXT[String(naam).split('.').pop()?.toLowerCase()] || 'application/octet-stream';

function ruimOp() {
  // plans en bevestigingen oudere dan een dag: weg. (Geen cron in deze container;
  // dit is de lui-methode die bij elk archiefverzoek meedraait.)
  try {
    fs.mkdirSync(ARCHIEF_MAP, { recursive: true });
    const cutoff = Date.now() - 86400000;
    for (const f of fs.readdirSync(ARCHIEF_MAP)) {
      const p = path.join(ARCHIEF_MAP, f);
      try { if (fs.statSync(p).mtimeMs < cutoff) fs.rmSync(p, { force: true }); } catch { }
    }
  } catch { }
}

async function mapCheck(u, parentId) {
  const d = getDb();
  if (parentId == null) return { ok: true, parentId: null };
  const p = d.prepare('SELECT * FROM nodes WHERE id = ?').get(parentId);
  if (!p || p.type !== 'folder' || !canSee(u, p) || !canWrite(u, p)) return { fout: { error: 'verboden', status: 403 } };
  return { ok: true, parentId };
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!may(u, 'upload')) return Response.json({ error: 'verboden' }, { status: 403 });  // uitpakken = massaal uploaden
  ruimOp();
  const isJson = String(req.headers.get('content-type') || '').includes('application/json');
  return isJson ? bevestigen(req, u) : plannen(req, u);
}

// ---- stap 1: plan (droog) ----------------------------------------------------
async function plannen(req, u) {
  const form = await req.formData().catch(() => null);
  if (!form) return Response.json({ error: 'verwacht multipart/form-data' }, { status: 400 });
  const file = form.get('file');
  if (!(file instanceof File)) return Response.json({ error: 'geen bestand' }, { status: 400 });
  if (!/\.zip$/i.test(file.name)) return Response.json({ error: 'alleen gewone .zip-archieven' }, { status: 400 });
  if (file.size > MAX) return Response.json({ error: 'te groot (max 512 MB)' }, { status: 400 });

  const parentRaw = form.get('parent');
  const mc = await mapCheck(u, parentRaw ? Number(parentRaw) : null);
  if (mc.fout) return Response.json({ error: mc.fout.error }, { status: mc.fout.status });

  const buf = Buffer.from(await file.arrayBuffer());
  let inz;
  try { inz = plan(buf); }
  catch (e) {
    if (e instanceof ZipFout) return Response.json({ error: e.message }, { status: 400 });
    throw e;
  }
  if (!inz.length) return Response.json({ error: 'geen bestanden in het archief' }, { status: 400 });

  // Simuleer de writers zonder iets te schrijven: loop elk pad door de bestaande
  // boom (plus de mappen die DIT plan zelf aanlegt) en bepaal per inzendinging
  // of het nieuw is, een nieuwe versie wordt, of botst met een bestaande map.
  const d = getDb();
  const gesimuleerd = new Map();     // 'parentId|naam' -> {id,type}
  const regels = [];
  let nieuw = 0, versies = 0, botsingen = 0;
  const nieuweMappen = new Set();
  for (const z of inz) {
    let cur = mc.parentId;
    let ok = true;
    for (let i = 0; i < z.pad.length - 1 && ok; i++) {
      const seg = clean(z.pad[i]) || 'naamloos';
      const sleutel = cur + '|' + seg.toLowerCase();
      let kind = gesimuleerd.get(sleutel) || findChild(cur, seg);
      if (!kind) {
        kind = { id: 'nieuw:' + sleutel, type: 'folder' };
        gesimuleerd.set(sleutel, kind);
        nieuweMappen.add(z.pad.slice(0, i + 1).join('/'));
      } else if (kind.type !== 'folder') {
        ok = false; botsingen++;            // file staat waar een map moet: de upload-regel zegt 'bestaat al'
        break;
      }
      cur = kind.id;
    }
    if (!ok) { regels.push({ pad: z.pad.join('/'), actie: 'botsing', grootte: z.grootte }); continue; }
    const bestandsSeg = clean(z.pad[z.pad.length - 1]) || 'naamloos';
    const bestaand = typeof cur === 'number' ? findChild(cur, bestandsSeg) : null;
    // bij een gesimuleerde (nieuwe) map kan er niets bestaan: nieuw.
    let actie;
    if (bestaand && bestaand.type === 'folder') { actie = 'botsing'; botsingen++; }
    else if (bestaand && bestaand.type === 'file') { actie = 'versie'; versies++; }
    else { actie = 'nieuw'; nieuw++; }
    regels.push({ pad: z.pad.join('/'), actie, grootte: z.grootte });
  }

  const token = crypto.randomUUID();
  fs.mkdirSync(ARCHIEF_MAP, { recursive: true });
  fs.writeFileSync(path.join(ARCHIEF_MAP, token + '.zip'), buf);
  fs.writeFileSync(path.join(ARCHIEF_MAP, token + '.json'), JSON.stringify({ parentId: mc.parentId, wie: u.id,ennaam: file.name }));

  return Response.json({
    token,ennaam: file.name, omvang: file.size, aantal: inz.length,
    nieuw, versies, botsingen, mappen: nieuweMappen.size,
    regels: regels.slice(0, 60), afgekapt: regels.length > 60,
  });
}

// ---- stap 2: bevestigd uitpakken --------------------------------------------
async function bevestigen(req, u) {
  const body = await req.json().catch(() => ({}));
  const token = String(body.token || '');
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(token)) {
    return Response.json({ error: 'onbekend archief' }, { status: 400 });
  }
  const zipPad = path.join(ARCHIEF_MAP, token + '.zip');
  const metaPad = path.join(ARCHIEF_MAP, token + '.json');
  if (!fs.existsSync(zipPad) || !fs.existsSync(metaPad)) return Response.json({ error: 'onbekend archief' }, { status: 404 });
  const meta = JSON.parse(fs.readFileSync(metaPad, 'utf8'));
  if (meta.wie !== u.id) return Response.json({ error: 'verboden' }, { status: 403 });
  // Rechten NU opnieuw checken: tussen plan en bevestigen kan de map veranderd
  // zijn van eigenaar of zichtbaarheid. De server is de baas, niet het moment.
  const mc = await mapCheck(u, meta.parentId);
  if (mc.fout) return Response.json({ error: mc.fout.error }, { status: mc.fout.status });

  const buf = fs.readFileSync(zipPad);
  let inz;
  try { inz = plan(buf); } catch (e) { return Response.json({ error: e instanceof ZipFout ? e.message : 'archief onleesbaar' }, { status: 400 }); }

  const d = getDb();
  const gemaakt = [];
  const zorgVoor = (base, parts) => {
    let cur = base;
    for (const raw of parts) {
      const nm = clean(raw);
      if (!nm) continue;
      let kid = findChild(cur, nm);
      if (!kid || kid.type !== 'folder') {
        const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
          .run(cur, uniqueName(cur, nm), 'folder', u.id, now(), now()).lastInsertRowid);
        kid = d.prepare('SELECT * FROM nodes WHERE id = ?').get(id);
      }
      cur = kid.id;
    }
    return cur;
  };

  for (const z of inz) {
    const folder = zorgVoor(mc.parentId, z.pad.slice(0, -1));
    if (!canWrite(u, folder ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(folder) : null)) {
      gemaakt.push({ naam: z.pad.join('/'), error: 'verboden' }); continue;
    }
    const naam = clean(z.pad[z.pad.length - 1]) || 'naamloos';
    let bodyZ;
    try { bodyZ = data(buf, z); }
    catch (e) { gemaakt.push({ naam, error: e instanceof ZipFout ? e.message : 'fout' }); continue; }
    if (bodyZ.length > MAX) { gemaakt.push({ naam, error: 'te groot' }); continue; }
    const sha = storeBlob(bodyZ);
    const mime = mimeVoor(naam);
    let node = findChild(folder, naam);
    if (node && node.type === 'file') {                    // zelfde naam = revisie, nooit overschrijven
      const last = d.prepare('SELECT * FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(node.id);
      if (last && last.sha === sha) { gemaakt.push({ naam, unchanged: true }); continue; }
      d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)').run(node.id, sha, bodyZ.length, mime, u.id, now());
      d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), node.id);
      gemaakt.push({ naam, versioned: true });
      continue;
    }
    if (node) { gemaakt.push({ naam, error: 'er bestaat al een map met die naam' }); continue; }
    const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)')
      .run(folder, uniqueName(folder, naam), 'file', u.id, now(), now()).lastInsertRowid);
    d.prepare('INSERT INTO versions(node,sha,size,mime,who,created) VALUES(?,?,?,?,?,?)').run(id, sha, bodyZ.length, mime, u.id, now());
    gemaakt.push({ id, naam });
  }

  fs.rmSync(zipPad, { force: true }); fs.rmSync(metaPad, { force: true });
  logAudit(req, { area: 'archief', actie: 'uitgepakt', wie: u.username, detail: `${meta.ennaam} → ${inz.length} bestanden` });
  return Response.json({ ok: true, gemaakt });
}
