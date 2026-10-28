// Een complete handleiding vertalen, zonder dat er een samenvatting van wordt.
//
// Drempels die dit ontwerp omzeilt:
//  - HTML door een model heen sturen: dat verbrukt de opmaak. Daarom gaat de
//    tekst via de markdown-omzetter (lib/docconv) - die is gemaakt voor precies
//    deze TipTap-output - en komt hij er ook weer doorheen.
//  - Eén mega-opdracht: lange documenten worden gaar of ingekort. Daarom chunk
//    voor chunk, in dezelfde sessie, met het vorige stuk als anker voor termen.
//  - Goede voornemens zijn niets: na afloop wordt de structuur nagemeten
//    (koppen, lijstjes, plaatjes), zodat een ingestorte vertaling opvalt.
import { getDb, now } from './db.js';
import { beurt, nieuweSessie } from './aikit.js';
import { htmlToMd, mdToHtml } from './docconv.js';

// Finse staff? Ook vertalen kan inmiddels naar het Fins: één regel in deze kaart
// maakt de knop, de controle én de tabbalk af - de rest van de pijplijn is taal-blind.
export const TALEN = { nl: 'Nederlands', en: 'English', it: 'Italiano', fi: 'Fins (suomi)' };

/** Markdown in stukken van ±omvang tekens, op kop- of blanco-regelgrenzen. */
export function chunks(md, omvang = 5200) {
  let bron = String(md || '').replace(/\r\n/g, '\n').split(/\n{2,}/);
  const out = [];
  let cur = '';
  for (let blok of bron) {
    // een enorm blok zonder blanco regels alsnog op regelgrenzen knippen
    while (blok.length > omvang) {
      const knip = blok.slice(0, omvang).lastIndexOf('\n');
      const bij = knip > 800 ? knip : omvang;
      if (cur) { out.push(cur); cur = ''; }
      out.push(blok.slice(0, bij));
      blok = blok.slice(bij);
    }
    if ((cur + '\n\n' + blok).length > omvang) { if (cur) out.push(cur); cur = blok; }
    else cur = cur ? cur + '\n\n' + blok : blok;
  }
  if (cur) out.push(cur);
  return out.filter(Boolean);
}

function systeem(maal) {
  return [
    `Je bent een professionele vertaler van technische documentatie. Vertaal naar het ${TALEN[maal]}.`,
    'Het is markdown uit een intern staffhandboek. Behoud ELK kopje (#, ##), elk lijstje (-, 1.), elke codeblok en elke afbeelding ![alt](src) op zijn plek, in dezelfde volgorde en in hetzelfde aantal.',
    'Vertaal VOLLEDIG: niet samenvatten, niet weglaten, niet toevoegen, geen commentaar, geen voorwoord. Geef uitsluitend de vertaalde markdown terug, zonder ```-omwalling.',
    "Laat commando's, bestandsnamen, plugin-namen, items en minecraft:-id's onvertaald; vertaal alleen de gewone zinnen eromheen.",
    'Houd terminology consistent met de eerdere stukken van hetzelfde document.',
  ].join('\n');
}

const lijm = (delen) => delen.join('\n\n').replace(/\n{3,}/g, '\n\n').trim() + '\n';

/** Structurele telwoorden, om de vertaling na afloop mee te toetsen. */
function tel(md) {
  const s = String(md);
  return {
    koppen: (s.match(/^#{1,6}\s/gm) || []).length,
    listen: (s.match(/^\s*[-*+]\s/gm) || []).length,
    nummerd: (s.match(/^\s*\d+[.)]\s/gm) || []).length,
    plaatjes: (s.match(/!\[[^\]]*\]\(/g) || []).length,
    codeblok: (s.match(/```/g) || []).length,
  };
}

/**
 * Vertaalt één pagina naar één taal en zet het weg als variant (tabel
 * `vertalingen`), zodat herlezen niets kosts. Bestaande varianten worden
 * teruggegeven tenzij `opnieuw` eraan zit.
 */
export async function vertaalPagina(node, naar, { wie = null, opnieuw = false } = {}) {
  const d = getDb();
  if (!TALEN[naar] || naar === 'nl') return { ok: false, fout: 'onbekende taal' };
  const bron = d.prepare('SELECT html FROM pages WHERE node = ?').get(node);
  if (!bron) return { ok: false, fout: 'geen document' };
  const bestaand = d.prepare('SELECT html, updated FROM vertalingen WHERE node = ? AND lang = ?').get(node, naar);
  if (bestaand && !opnieuw) return { ok: true, html: bestaand.html, cached: true, updated: bestaand.updated };

  const md = htmlToMd(bron.html);
  const stukken = chunks(md);
  if (!stukken.length) return { ok: false, fout: 'leeg document' };
  if (stukken.length > 12) return { ok: false, fout: 'te lang voor één beurt (' + stukken.length + ' stukken)' };

  const sid = await nieuweSessie('vertaling:' + node + ':' + naar);
  if (!sid) return { ok: false, fout: 'ai-uit' };

  const delen = [];
  for (let i = 0; i < stukken.length; i++) {
    const anker = i === 0 ? '' : '\n\nEINDE VAN HET VOORAFGAANDE STUK (alleen om termen te herkennen, niet opnieuw vertalen):\n' + delen[i - 1].slice(-600);
    const b = await beurt({
      sid,
      system: systeem(naar),
      text: `Stuk ${i + 1} van ${stukken.length}. Alleen de vertaling teruggeven.\n\n${stukken[i]}${anker}`,
      ms: 240_000,
    });
    if (!b.ok) return { ok: false, fout: b.error, stuk: i + 1 };
    delen.push(String(b.answer).replace(/^```(?:markdown|md)?\s*/i, '').replace(/\s*```$/, ''));
  }

  const resultaat = lijm(delen);
  const vooraf = tel(md), achteraf = tel(resultaat);
  // Een ingestorte vertaling is erger dan een onvertaalde: zeg het als het scheelt.
  const afwijking = vooraf.koppen > 2 && Math.abs(achteraf.koppen - vooraf.koppen) / vooraf.koppen > 0.34;
  const html = mdToHtml(resultaat);
  d.prepare('INSERT INTO vertalingen(node,lang,html,who,updated) VALUES(?,?,?,?,?) ' +
    'ON CONFLICT(node,lang) DO UPDATE SET html=excluded.html, who=excluded.who, updated=excluded.updated')
    .run(node, naar, html, wie, now());

  return { ok: true, html, markdown: resultaat, stukken: stukken.length, controle: { vooraf, achteraf, twijfel: afwijking } };
}

/** Welke talen van dit document al klaarliggen. */
export function varianten(node) {
  return getDb().prepare('SELECT lang, updated FROM vertalingen WHERE node = ?').all(node);
}
