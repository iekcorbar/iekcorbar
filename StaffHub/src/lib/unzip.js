// Minimale, eigen ZIP-lezer (geen npm-afhankelijkheid).
//
// Waarom zelf: de hub heeft voor dit onderwerp niets nodig dat er niet al is -
// one dependency extra voor alleen LEZEN is te veel gevraagd. Het zip-formaat
// is oud en simpel: alles interessants staat in de centrale directory aan het
// eind van het bestand (naam, groottes, methode, offset naar de data).
//
// Wat wordt afgewezen (elk met een begrijpelijke melding):
//  - zip64 (EOCD met 0xFFFF/0xFFFFFFFF - negen van de tien archive die hier
//    binnenkomen zijn gewone Windows-zipjes; zip64 is een apart project)
//  - versleutelde inzendingen (flag bit 0 of 64)
//  - methodes anders dan 0 (stored) en 8 (deflate)
//  - paden met '..', beginslashes of stationsletters (de klassieke zip-slip)
//  - zip-bommen: >5000 inzendingen, >512 MB uitgepakt (dezelfde drempel als de
//    gewone upload), of één inzending die meer dan 150x comprimeert
//
// Map-inzendingen (naam eindigend op '/'), macOS-rommel (__MACOSX, ._x) en
// dubbel voorkomende wegen worden overgeslagen, net zoals Verkenner doet.

import zlib from 'node:zlib';

const MAX_INZ = 5000;
const MAX_BYTES = 512 * 1024 * 1024;
const MAX_RATIO = 150;

export class ZipFout extends Error { }

/**
 * Buffer → array van {pad, naam, grootte, cSize, methode, lOff}: het PLAN,
 * zonder dat er één byte uitgepakt wordt. Gooit ZipFout op alles wat niet de
 * bedoeling kan zijn; de UI toont die melding onvertaald aan de uploadende.
 */
export function plan(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 22) throw new ZipFout('geen geldig zip-bestand');
  const eocd = vindEocd(buf);
  const aantal = buf.readUInt16LE(eocd + 10);
  const cStart = buf.readUInt32LE(eocd + 16);
  if (aantal === 0xFFFF || buf.readUInt32LE(eocd + 12) === 0xFFFFFFFF || cStart === 0xFFFFFFFF) {
    throw new ZipFout('zip64 wordt nog niet ondersteund - maak een gewone zip');
  }
  const inzendingen = [];
  let p = cStart;
  for (let i = 0; i < aantal; i++) {
    if (p + 46 > buf.length || buf.readUInt32LE(p) !== 0x02014b50) {
      throw new ZipFout('zip-beschadigd (centrale directory klopt niet)');
    }
    const vlag = buf.readUInt16LE(p + 8);
    const methode = buf.readUInt16LE(p + 10);
    const cSize = buf.readUInt32LE(p + 20);
    const uSize = buf.readUInt32LE(p + 24);
    const nLen = buf.readUInt16LE(p + 28);
    const xLen = buf.readUInt16LE(p + 30);
    const kLen = buf.readUInt16LE(p + 32);
    const lOff = buf.readUInt32LE(p + 42);
    const naam = buf.slice(p + 46, p + 46 + nLen).toString('utf8');
    p += 46 + nLen + xLen + kLen;

    if (naam.endsWith('/') || naam.endsWith('\\')) continue;      // map-zelf is geen bestand
    if (vlag & 1 || vlag & 64) throw new ZipFout('versleuteld zip-archief - maak het open voordat je het uploadt');
    if (methode !== 0 && methode !== 8) throw new ZipFout(`onbekende zip-methode (${methode})`);
    const segs = ruwPadNaarSegs(naam);
    if (!segs) continue;                                          // rommel of leeg
    inzendingen.push({ pad: segs, naam: segs[segs.length - 1], grootte: uSize, cSize, methode, lOff });
    if (inzendingen.length > MAX_INZ) throw new ZipFout(`te veel bestanden (>${MAX_INZ})`);
  }
  // Grenzen VÓÓR het uitpakken: dit is de bom-en-geheugenbescherming.
  let totaal = 0;
  for (const z of inzendingen) {
    if (z.grootte > MAX_BYTES) throw new ZipFout(`te groot: ${z.naam}`);
    totaal += z.grootte;
    if (totaal > MAX_BYTES) throw new ZipFout(`te veel inhoud samen (>${Math.round(MAX_BYTES / 1024 / 1024)} MB)`);
    if (z.methode === 8 && z.cSize * MAX_RATIO + 1024 < z.grootte) {
      throw new ZipFout('dit archief comprimeert ongezond (zip-bom?) - uitpakken geweigerd');
    }
  }
  return stripGedeeldeTop(zonderDuplicate(inzendingen));
}

/** Echte bytes van één inzending, uit de volledige buffer. */
export function data(buf, z) {
  // De lokale header heeft EIGEN naam/extra-lengtes (kunnen afwijken van de
  // centrale) - alleen vandaar lezen we waar de data begint.
  if (z.lOff + 30 > buf.length || buf.readUInt32LE(z.lOff) !== 0x04034b50) {
    throw new ZipFout('zip-beschadigd (lokale header)');
  }
  const n = buf.readUInt16LE(z.lOff + 26), x = buf.readUInt16LE(z.lOff + 28);
  const start = z.lOff + 30 + n + x;
  const eind = start + z.cSize;
  if (eind > buf.length) throw new ZipFout('zip-beschadigd (data loopt buiten het bestand)');
  const rauw = buf.slice(start, eind);
  if (z.methode === 0) return rauw;
  try { return zlib.inflateRawSync(rauw); }
  catch { throw new ZipFout('zip-beschadigd (lukt niet uit te comprimeren)'); }
}

function vindEocd(buf) {
  const zoekStart = Math.max(0, buf.length - 22 - 65536);
  for (let i = buf.length - 22; i >= zoekStart; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) return i;
  }
  throw new ZipFout('geen geldig zip-bestand (einde-archief niet gevonden)');
}

/**
 * Naam uit de zip → segmenten. Backslashes tellen als scheiding (Windows-
 * ritsprogramma's schrijven ze). '..', absolute paden en 'C:' worden afgewezen
 * (zip-slip). macOS-rommel → null (overgeslagen).
 */
function ruwPadNaarSegs(naam) {
  if (naam.startsWith('/') || /^[a-zA-Z]:[\\/]/.test(naam)) {
    throw new ZipFout(`absoluut pad in archief: ${naam}`);
  }
  const del = naam.replace(/\\/g, '/').split('/').filter(Boolean);
  if (del.length === 0) return null;
  if (del[0] === '__MACOSX') return null;
  const laatste = del[del.length - 1];
  if (laatste.startsWith('._') || laatste === '.DS_Store') return null;
  for (const s of del) {
    if (s === '.' || s === '..' || s.includes('\0')) {
      throw new ZipFout(`onzuiver pad in archief: ${naam}`);
    }
  }
  return del;
}

/**
 * "Alles zit in één mapje" is wat Windows (Verzenden naar → gecomprimeerde map)
 * en `zip -r archief map/` produceren: als élke inzending een dieper pad heeft
 * en ze allemaal dezelfde eerste segment delen, is die map de naam van het
 * archief zelf en niet de bedoelde structuur. Eén niveau wordt gestript; de
 * rest van de boom blijft zoals hij is. Losse files op topniveau raken dit niet.
 */
function stripGedeeldeTop(inz) {
  if (inz.length < 2) return inz;
  const top = inz[0].pad[0];
  if (!top || !inz.every((z) => z.pad.length > 1 && z.pad[0] === top)) return inz;
  return inz.map((z) => {
    const pad = z.pad.slice(1);
    return { ...z, pad, naam: pad[pad.length - 1] };
  });
}

/** Twee keer dezelfde genormaliseerde weg in één archief: de tweede is rommel. */
function zonderDuplicate(inz) {
  const gezien = new Set();
  return inz.filter((z) => {
    const k = z.pad.join('/').toLowerCase();
    if (gezien.has(k)) return false;
    gezien.add(k);
    return true;
  });
}
