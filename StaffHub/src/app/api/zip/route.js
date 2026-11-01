// Download als zip: /api/zip?ids=1,2,3[&name=Naam]
//
// Een map (of een selectie) als een bestand meenemen. De zip is "stored" (niet
// gecomprimeerd) en wordt gestreamd, dus ook een map met modellen of video's
// belandt nooit in één keer in het geheugen. CRC en grootte staan vooraf in de
// lokale header (geen data descriptor): sommige lezers (bsdtar, Verkenner) kunnen
// een "stored" bestand met onbekende grootte niet streamend uitpakken. Daarom
// leest de server elk bestand eerst één keer voor de CRC en daarna nog eens om
// te sturen. Pagina's gaan mee als .md (zelfde omzetting als /api/page?fmt=md).
// Alleen wat de ingelogde gebruiker mag zien komt erin (canSee per node).
import fs from 'node:fs';
import zlib from 'node:zlib';
import { getDb, blobPath } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';
import { htmlToMd } from '@/lib/docconv';

export const dynamic = 'force-dynamic';

const MAX_NODES = 5000;
const MAX_BYTES = 3.5 * 1024 ** 3;           // blijft onder de 4 GB-grens van gewone zip (geen zip64)

const schoon = (s) => String(s).replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').trim() || '_';

function dosTime(ms) {
  const d = new Date(ms || Date.now());
  const date = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1);
  return { date: date & 0xffff, time: time & 0xffff };
}

// Verzamelt { pad, node } voor alle bestanden en pagina's onder de gekozen nodes.
function verzamel(d, u, wortels) {
  const uit = [];
  const loop = (n, voor) => {
    if (uit.length > MAX_NODES) return;
    const pad = voor + schoon(n.name);
    if (n.type === 'folder') {
      const kids = d.prepare('SELECT * FROM nodes WHERE parent = ? ORDER BY name').all(n.id).filter((k) => canSee(u, k));
      if (!kids.length) uit.push({ pad: pad + '/', node: n, leeg: true });
      for (const k of kids) loop(k, pad + '/');
    } else uit.push({ pad, node: n });
  };
  for (const n of wortels) loop(n, '');
  return uit;
}

export async function GET(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const url = new URL(req.url);
  const ids = String(url.searchParams.get('ids') || '').split(',').map(Number).filter((x) => Number.isInteger(x) && x > 0);
  if (!ids.length) return Response.json({ error: 'geen selectie' }, { status: 400 });
  const d = getDb();
  const wortels = ids.map((i) => d.prepare('SELECT * FROM nodes WHERE id = ?').get(i)).filter((n) => n && canSee(u, n));
  if (!wortels.length) return Response.json({ error: 'not found' }, { status: 404 });

  const items = verzamel(d, u, wortels);
  if (items.length > MAX_NODES) return Response.json({ error: 'te veel bestanden (max ' + MAX_NODES + ')' }, { status: 413 });

  // Bron per item bepalen en de totale grootte controleren voor we beginnen.
  const bron = [];
  let totaal = 0;
  for (const it of items) {
    const n = it.node;
    if (it.leeg) { bron.push({ ...it, kind: 'dir' }); continue; }
    if (n.type === 'page') {
      const p = d.prepare('SELECT html FROM pages WHERE node = ?').get(n.id);
      const buf = Buffer.from(htmlToMd(p ? p.html : ''), 'utf8');
      bron.push({ pad: it.pad + '.md', node: n, kind: 'buf', buf }); totaal += buf.length;
    } else if (n.type === 'file') {
      const v = d.prepare('SELECT sha FROM versions WHERE node = ? ORDER BY id DESC LIMIT 1').get(n.id);
      const pad = v && blobPath(v.sha);
      if (!pad || !fs.existsSync(pad)) continue;
      const size = fs.statSync(pad).size;
      bron.push({ pad: it.pad, node: n, kind: 'file', file: pad, size }); totaal += size;
    }
  }
  if (totaal > MAX_BYTES) return Response.json({ error: 'te groot voor een zip (max 3,5 GB), kies een kleinere selectie' }, { status: 413 });

  const naam = schoon(url.searchParams.get('name') || (wortels.length === 1 ? wortels[0].name : 'staffhub')) + '.zip';

  async function* maak() {
    const centraal = [];
    let offset = 0;
    const schrijf = (b) => { offset += b.length; return b; };
    for (const s of bron) {
      const nm = Buffer.from(s.pad, 'utf8');
      const { date, time } = dosTime(s.node.updated);
      const dir = s.kind === 'dir';
      let crc = 0, size = 0;
      if (s.kind === 'buf') { crc = zlib.crc32(s.buf); size = s.buf.length; }
      else if (s.kind === 'file') {
        for await (const chunk of fs.createReadStream(s.file)) { crc = zlib.crc32(chunk, crc); size += chunk.length; }
      }
      const lh = Buffer.alloc(30);
      lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4);
      lh.writeUInt16LE(0x0800, 6);                                 // bit 11 = UTF-8 namen
      lh.writeUInt16LE(0, 8); lh.writeUInt16LE(time, 10); lh.writeUInt16LE(date, 12);
      lh.writeUInt32LE(crc >>> 0, 14); lh.writeUInt32LE(size, 18); lh.writeUInt32LE(size, 22);
      lh.writeUInt16LE(nm.length, 26);
      const localOffset = offset;
      yield schrijf(lh); yield schrijf(nm);
      if (s.kind === 'buf') yield schrijf(s.buf);
      else if (s.kind === 'file') for await (const chunk of fs.createReadStream(s.file)) yield schrijf(chunk);
      centraal.push({ nm, crc: crc >>> 0, size, date, time, dir, localOffset });
    }
    const start = offset;
    for (const c of centraal) {
      const h = Buffer.alloc(46);
      h.writeUInt32LE(0x02014b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(20, 6);
      h.writeUInt16LE(0x0800, 8); h.writeUInt16LE(0, 10);
      h.writeUInt16LE(c.time, 12); h.writeUInt16LE(c.date, 14);
      h.writeUInt32LE(c.crc, 16); h.writeUInt32LE(c.size, 20); h.writeUInt32LE(c.size, 24);
      h.writeUInt16LE(c.nm.length, 28);
      if (c.dir) h.writeUInt32LE(0x10, 38);                       // extern attribuut: map
      h.writeUInt32LE(c.localOffset, 42);
      yield schrijf(h); yield schrijf(c.nm);
    }
    const eind = Buffer.alloc(22);
    eind.writeUInt32LE(0x06054b50, 0);
    eind.writeUInt16LE(centraal.length, 8); eind.writeUInt16LE(centraal.length, 10);
    eind.writeUInt32LE(offset - start, 12); eind.writeUInt32LE(start, 16);
    yield eind;
  }

  const it = maak();
  const stream = new ReadableStream({
    async pull(ctl) {
      const { value, done } = await it.next();
      if (done) ctl.close(); else ctl.enqueue(value);
    },
    cancel() { it.return(); },
  });
  return new Response(stream, {
    headers: {
      'Content-Type': 'application/zip',
      'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(naam)}`,
      'Cache-Control': 'no-store',
    },
  });
}
