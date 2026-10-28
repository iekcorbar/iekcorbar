// Kennispakket voor de Boord-AI: de hub ZELF is de bron, niet de git-clone.
//
// De AI draait in een sandbox die niets van StaffHub ziet. Vroeger moest hij het
// daarom hebben van een handmatig gekloonde repo in ./workspace - en die bevat
// precies NIET wat staff in de hub schrijft. Dit bouwt per vraag een pakket van
// de pagina's die deze vragensteller mag zien (zelfde canSee-poort als de boom en
// zoekbalk), zodat het antwoord gegrondeerd is in eigen content en klikbare
// bronnen kan noemen.
import { getDb } from './db.js';
import { canSee } from './acl.js';

/** HTML naar leesbare platte tekst (TipTap-uitvoer, dus ook lijsten en koppen). */
export function toText(html) {
  return String(html || '')
    .replace(/<\/(p|h[1-6]|li|div|tr|pre|blockquote)>/gi, '\n')
    .replace(/<li[^>]*>/gi, '- ')
    .replace(/<h([1-6])[^>]*>/gi, '\n#')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim();
}

/** Woorden uit de vraag die worth zijn om op te zoeken (stopwords eruit). */
const STOP = new Set(['hoe', 'wat', 'waar', 'wordt', 'worden', 'kun', 'kan', 'ben', 'zijn', 'een', 'de', 'het', 'en', 'of', 'ik', 'je', 'we', 'is', 'are', 'the', 'what', 'how', 'where', 'for', 'and', 'can', 'chi', 'come', 'per', 'che', 'non', 'cosa', 'dove', 'questo']);

function keywords(q) {
  const words = String(q || '').toLowerCase().replace(/[^\p{L}\p{N}\s-]/gu, ' ').split(/\s+/).filter((w) => w.length > 2 && !STOP.has(w));
  const out = new Set(words);
  // ook de hele vraag: "Steam Still" steekt op zichzelf meer dan "still" alleen
  const full = String(q || '').toLowerCase().trim();
  if (full.length > 3 && full.length < 60) out.add(full);
  return [...out];
}

/**
 * Plukt de best passende pagina's voor deze vraag en deze vrager.
 * @returns {{ block: string, sources: Array<{id:number,name:string,trail:string}>, scanned: number }}
 */
export function knowledgeFor(user, question, { maxPages = 6, maxChars = 14000 } = {}) {
  const d = getDb();
  const kws = keywords(question);
  const rows = d.prepare('SELECT n.*, pg.html FROM pages pg JOIN nodes n ON n.id = pg.node ORDER BY pg.updated DESC LIMIT 400').all();
  const scored = [];
  for (const p of rows) {
    if (!canSee(user, p)) continue;                            // dezelfde rechten als de boom
    const text = toText(p.html);
    const name = (p.name || '').toLowerCase();
    const body = text.toLowerCase();
    let score = 0;
    for (const k of kws) {
      const inName = name.includes(k) ? 6 : 0;
      const hits = body.split(k).length - 1;
      if (inName || hits) score += inName + Math.min(hits, 8);
    }
    if (score > 0) scored.push({ n: p, text, score });
  }
  scored.sort((a, b) => b.score - a.score);
  const top = scored.slice(0, maxPages);

  const parts = [];
  let used = 0;
  for (const s of top) {
    let text = s.text;
    const room = maxChars - used;
    if (room <= 400) break;
    if (text.length > room) text = text.slice(0, room) + '\n[… afgekort]';
    used += text.length;
    parts.push(`--- [${s.n.id}] ${trail(d, s.n)} :: ${s.n.name} ---\n${text}`);
  }
  return {
    block: parts.join('\n\n'),
    sources: top.map((s) => ({ id: s.n.id, name: s.n.name, trail: trail(d, s.n), score: s.score })),
    scanned: rows.length,
  };
}

/** Pad van root naar dit node, voor de bron-regel ("Guides/Plugins > Sakagura"). */
function trail(d, n) {
  const out = [];
  let cur = n.parent ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(n.parent) : null;
  while (cur && out.length < 8) {
    out.unshift(cur.name);
    cur = cur.parent ? d.prepare('SELECT * FROM nodes WHERE id = ?').get(cur.parent) : null;
  }
  return out.join('/') || '/';
}
