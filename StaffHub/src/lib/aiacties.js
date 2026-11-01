// Acties die de Boord-AI mag VOORSTELLEN en het staff-lid met één klik goedkeurt.
//
// Het model kan zelf niets: het schrijft een lijstje acties, de hub controleert elke actie
// met de rechten van de VRAGER (canSee/canWrite/caps, precies als de gewone knoppen) en toont
// ze als kaart. Pas na "Uitvoeren" gebeurt er iets, en dan wordt alles OPNIEUW gecontroleerd -
// de server vertrouwt nooit wat de browser terugstuurt.
//
// Wat het model NIET kan: definitief wissen, de prullenbak legen, accounts/rechten/tokens
// aanraken, een pagina overschrijven met een volledige nieuwe tekst (het model ziet vaak maar
// een stuk van een gids; overschrijven zou de rest wissen). Wijzigen kan alleen als
// toevoegen of als exacte tekstvervanging, en vóór elke wijziging komt er een versie in de
// geschiedenis. Verwijderen = naar de Trashbin, terug te zetten.
import { getDb, clean, uniqueName, findChild, now, logAudit } from './db.js';
import { canSee, canWrite, may } from './acl.js';

export const MAX_ACTIES = 10;
const MAX_HTML = 200_000;

/** Dak op wat het model aan HTML mag aanleveren: geen scripts, geen on*-handlers. */
export function veiligHtml(h) {
  return String(h || '').slice(0, MAX_HTML)
    .replace(/<\s*(script|style|iframe|object|embed|link|meta)[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
    .replace(/<\s*(script|style|iframe|object|embed|link|meta)[^>]*\/?>/gi, '')
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/(href|src)\s*=\s*("|')\s*javascript:[^"']*\2/gi, '$1=$2#$2');
}

const node = (d, id) => d.prepare('SELECT * FROM nodes WHERE id = ?').get(Number(id));

/** Map via id of via pad "Guides/Plugins" (namen, zonder Trashbin). null = root. */
function map(d, u, ref) {
  if (ref === null || ref === undefined || ref === '' || ref === 0 || ref === '/') return { ok: true, id: null, pad: '/' };
  if (typeof ref === 'number' || /^\d+$/.test(String(ref))) {
    const n = node(d, ref);
    if (!n || n.type !== 'folder' || !canSee(u, n)) return { ok: false, fout: `map ${ref} bestaat niet of is niet zichtbaar` };
    return { ok: true, id: n.id, pad: n.name };
  }
  let ouder = null;
  const stukken = String(ref).split('/').map((s) => s.trim()).filter(Boolean);
  for (const s of stukken) {
    const k = findChild(ouder, s);
    if (!k || k.type !== 'folder' || !canSee(u, k)) return { ok: false, fout: `map "${ref}" niet gevonden` };
    ouder = k.id;
  }
  return { ok: true, id: ouder, pad: stukken.join(' / ') };
}

function kanSchrijvenIn(d, u, mapId) {
  if (mapId == null) return canWrite(u, null);
  return canWrite(u, node(d, mapId));
}

/** Eén actie controleren. Geeft { ok, tekst, norm } of { ok:false, fout }. */
function controleer(d, u, a) {
  if (!a || typeof a !== 'object') return { ok: false, fout: 'ongeldige actie' };
  switch (a.actie) {
    case 'map_maken': {
      if (!may(u, 'upload')) return { ok: false, fout: 'je mag geen mappen maken' };
      const nm = clean(a.naam);
      if (!nm) return { ok: false, fout: 'map zonder naam' };
      const m = map(d, u, a.in);
      if (!m.ok) return m;
      if (!kanSchrijvenIn(d, u, m.id)) return { ok: false, fout: 'geen schrijfrecht in ' + m.pad };
      return { ok: true, tekst: `Map "${nm}" maken in ${m.pad}`, norm: { actie: 'map_maken', naam: nm, in: m.id } };
    }
    case 'pagina_maken': {
      if (!may(u, 'pages')) return { ok: false, fout: 'je mag geen documenten schrijven' };
      const nm = clean(a.naam);
      if (!nm) return { ok: false, fout: 'pagina zonder naam' };
      const m = map(d, u, a.in);
      if (!m.ok) return m;
      if (!kanSchrijvenIn(d, u, m.id)) return { ok: false, fout: 'geen schrijfrecht in ' + m.pad };
      const html = veiligHtml(a.html);
      return { ok: true, tekst: `Pagina "${nm}" maken in ${m.pad} (${html.length} tekens)`, norm: { actie: 'pagina_maken', naam: nm, in: m.id, html } };
    }
    case 'pagina_toevoegen':
    case 'pagina_vervangen': {
      if (!may(u, 'pages')) return { ok: false, fout: 'je mag geen documenten schrijven' };
      const n = node(d, a.id);
      if (!n || n.type !== 'page' || !canSee(u, n)) return { ok: false, fout: `pagina ${a.id} niet gevonden` };
      if (!canWrite(u, n)) return { ok: false, fout: `geen schrijfrecht op "${n.name}"` };
      if (a.actie === 'pagina_toevoegen') {
        const html = veiligHtml(a.html);
        if (!html.trim()) return { ok: false, fout: 'niets om toe te voegen' };
        return { ok: true, tekst: `Tekst onderaan "${n.name}" toevoegen (${html.length} tekens)`, norm: { actie: 'pagina_toevoegen', id: n.id, html } };
      }
      const zoek = String(a.zoek || '');
      const nieuw = veiligHtml(a.nieuw);
      if (zoek.length < 3) return { ok: false, fout: 'zoektekst te kort' };
      const cur = d.prepare('SELECT html FROM pages WHERE node = ?').get(n.id);
      const delen = String(cur?.html || '').split(zoek).length - 1;
      if (delen !== 1) return { ok: false, fout: delen === 0 ? `tekst niet gevonden in "${n.name}"` : `tekst komt ${delen}x voor in "${n.name}", wees specifieker` };
      return { ok: true, tekst: `In "${n.name}": "${zoek.slice(0, 60)}" vervangen door "${nieuw.replace(/<[^>]+>/g, ' ').trim().slice(0, 60)}"`, norm: { actie: 'pagina_vervangen', id: n.id, zoek, nieuw } };
    }
    case 'hernoemen': {
      if (!may(u, 'move')) return { ok: false, fout: 'je mag niet hernoemen' };
      const n = node(d, a.id);
      if (!n || !canSee(u, n)) return { ok: false, fout: `item ${a.id} niet gevonden` };
      if (!canWrite(u, n)) return { ok: false, fout: `geen schrijfrecht op "${n.name}"` };
      const nm = clean(a.naam);
      if (!nm) return { ok: false, fout: 'lege naam' };
      if (nm !== n.name && findChild(n.parent, nm)) return { ok: false, fout: `"${nm}" bestaat hier al` };
      return { ok: true, tekst: `"${n.name}" hernoemen naar "${nm}"`, norm: { actie: 'hernoemen', id: n.id, naam: nm } };
    }
    case 'verplaatsen': {
      if (!may(u, 'move')) return { ok: false, fout: 'je mag niet verplaatsen' };
      const n = node(d, a.id);
      if (!n || !canSee(u, n)) return { ok: false, fout: `item ${a.id} niet gevonden` };
      if (!canWrite(u, n)) return { ok: false, fout: `geen schrijfrecht op "${n.name}"` };
      const m = map(d, u, a.naar);
      if (!m.ok) return m;
      if (m.id == null && u.role !== 'owner') return { ok: false, fout: 'naar de bovenste laag mag alleen een owner' };
      if (!kanSchrijvenIn(d, u, m.id)) return { ok: false, fout: 'geen schrijfrecht in ' + m.pad };
      if (m.id != null && (m.id === n.id || d.prepare('WITH RECURSIVE s(id) AS (SELECT ? UNION SELECT id FROM nodes WHERE parent IN s) SELECT 1 FROM s WHERE id = ?').get(n.id, m.id))) {
        return { ok: false, fout: 'een map kan niet in zichzelf' };
      }
      return { ok: true, tekst: `"${n.name}" verplaatsen naar ${m.pad}`, norm: { actie: 'verplaatsen', id: n.id, naar: m.id } };
    }
    case 'prullen': {
      if (!may(u, 'trash')) return { ok: false, fout: 'je mag niets naar de Trashbin' };
      const n = node(d, a.id);
      if (!n || !canSee(u, n)) return { ok: false, fout: `item ${a.id} niet gevonden` };
      if (!canWrite(u, n)) return { ok: false, fout: `geen schrijfrecht op "${n.name}"` };
      if (n.name === 'Trashbin' && n.parent == null) return { ok: false, fout: 'de Trashbin zelf kan niet' };
      return { ok: true, tekst: `"${n.name}" naar de Trashbin (terug te zetten)`, norm: { actie: 'prullen', id: n.id } };
    }
    default:
      return { ok: false, fout: 'onbekende actie: ' + String(a.actie).slice(0, 30) };
  }
}

/** Voorstel doorlichten voor de kaart. Geeft per actie tekst of fout. */
export function doorlichten(u, lijst) {
  const d = getDb();
  const rij = (Array.isArray(lijst) ? lijst : []).slice(0, MAX_ACTIES);
  return rij.map((a) => controleer(d, u, a));
}

function snapshot(d, n, u) {
  d.prepare(`CREATE TABLE IF NOT EXISTS page_versions (id INTEGER PRIMARY KEY, node INTEGER NOT NULL, html TEXT NOT NULL, who INTEGER, created INTEGER NOT NULL, size INTEGER)`).run();
  const cur = d.prepare('SELECT html FROM pages WHERE node = ?').get(n.id);
  if (cur) d.prepare('INSERT INTO page_versions(node,html,who,created,size) VALUES(?,?,?,?,?)').run(n.id, cur.html, u.id, now(), cur.html.length);
}

function naarBak(d, u, n) {
  let ton = findChild(null, 'Trashbin');
  if (!ton || ton.type !== 'folder') {
    const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(NULL,?,?,?,?,?)').run('Trashbin', 'folder', u.id, now(), now()).lastInsertRowid);
    ton = node(d, id);
  }
  d.prepare('INSERT INTO meta(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v = excluded.v')
    .run('trash:' + n.id, JSON.stringify({ p: n.parent, n: n.name, at: now(), by: u.id }));
  const nm = findChild(ton.id, n.name) ? uniqueName(ton.id, n.name) : n.name;
  d.prepare('UPDATE nodes SET parent = ?, name = ?, updated = ? WHERE id = ?').run(ton.id, nm, now(), n.id);
}

/** Uitvoeren: elke actie wordt opnieuw gecontroleerd; een mislukte stopt de rest niet. */
export function uitvoeren(req, u, lijst) {
  const d = getDb();
  const uit = [];
  for (const a of (Array.isArray(lijst) ? lijst : []).slice(0, MAX_ACTIES)) {
    const c = controleer(d, u, a);
    if (!c.ok) { uit.push({ ok: false, tekst: c.fout }); continue; }
    const x = c.norm;
    try {
      if (x.actie === 'map_maken') {
        const bestaand = findChild(x.in, x.naam);
        if (!bestaand) d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)').run(x.in, uniqueName(x.in, x.naam), 'folder', u.id, now(), now());
      } else if (x.actie === 'pagina_maken') {
        const id = Number(d.prepare('INSERT INTO nodes(parent,name,type,owner,created,updated) VALUES(?,?,?,?,?,?)').run(x.in, uniqueName(x.in, x.naam), 'page', u.id, now(), now()).lastInsertRowid);
        d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)').run(id, x.html, u.id, now());
      } else if (x.actie === 'pagina_toevoegen' || x.actie === 'pagina_vervangen') {
        const n = node(d, x.id);
        snapshot(d, n, u);
        const cur = d.prepare('SELECT html FROM pages WHERE node = ?').get(n.id);
        const nieuwHtml = x.actie === 'pagina_toevoegen' ? (cur?.html || '') + x.html : String(cur?.html || '').replace(x.zoek, () => x.nieuw);
        if (cur) d.prepare('UPDATE pages SET html = ?, who = ?, updated = ? WHERE node = ?').run(nieuwHtml.slice(0, 2_000_000), u.id, now(), n.id);
        else d.prepare('INSERT INTO pages(node,html,who,updated) VALUES(?,?,?,?)').run(n.id, nieuwHtml.slice(0, 2_000_000), u.id, now());
        d.prepare('UPDATE nodes SET updated = ? WHERE id = ?').run(now(), n.id);
      } else if (x.actie === 'hernoemen') {
        d.prepare('UPDATE nodes SET name = ?, updated = ? WHERE id = ?').run(x.naam, now(), x.id);
      } else if (x.actie === 'verplaatsen') {
        const n = node(d, x.id);
        const nm = findChild(x.naar, n.name) && findChild(x.naar, n.name).id !== n.id ? uniqueName(x.naar, n.name) : n.name;
        d.prepare('UPDATE nodes SET parent = ?, name = ?, updated = ? WHERE id = ?').run(x.naar, nm, now(), n.id);
      } else if (x.actie === 'prullen') {
        naarBak(d, u, node(d, x.id));
      }
      uit.push({ ok: true, tekst: c.tekst });
    } catch (e) {
      uit.push({ ok: false, tekst: c.tekst + ' - mislukt: ' + String(e?.message || e).slice(0, 80) });
    }
  }
  // Audit: wie liet de AI wat doen. Geen inhoud, alleen de beschrijvingen.
  try { logAudit(req, { area: 'ai', actie: 'acties', wie: u.id, detail: uit.map((r) => (r.ok ? '+ ' : '! ') + r.tekst).join(' | ').slice(0, 900) }); } catch { /* audit mag nooit blokkeren */ }
  return uit;
}
