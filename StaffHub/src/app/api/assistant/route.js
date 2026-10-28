// Boord-assistent: pure zoek-service op eigen content (geen LLM, geen kosten).
// Vraag -> hits op naam-, inhoud- en reactietekst, ACL-filterd zoals /api/search,
// met snippet rond de vindplaats, zodat de chat-bronnen meteen leesbaar zijn.
import { getDb } from '@/lib/db';
import { me } from '@/lib/auth';
import { canSee } from '@/lib/acl';

const stripHtml = (h) => String(h || '')
  .replace(/<style[\s\S]*?<\/style>/gi, ' ').replace(/<[^>]+>/g, ' ')
  .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
  .replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, ' ').trim();

/** ±180 tekens rond de eerste aanraf van q. */
function around(text, q) {
  const i = text.toLowerCase().indexOf(q.toLowerCase());
  if (i < 0) return text.slice(0, 170) + (text.length > 170 ? '…' : '');
  const s = Math.max(0, i - 60);
  return (s > 0 ? '…' : '') + text.slice(s, s + 180) + (s + 180 < text.length ? '…' : '');
}

export async function GET(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const q = String(new URL(req.url).searchParams.get('q') || '').trim();
  if (q.length < 2) return Response.json({ hits: [] });
  const like = '%' + q.replace(/[%_]/g, (c) => '\\' + c) + '%';
  const d = getDb();
  const seen = new Set();
  const hits = [];
  const add = (n, why, snippet) => {
    if (hits.length >= 8 || seen.has(n.id)) return;
    if (!canSee(u, n)) return;
    seen.add(n.id);
    hits.push({ id: n.id, name: n.name, type: n.type, why, snippet: snippet || '' });
  };

  // 1) op naam, 2) in pagina-tekst, 3) in reacties - net als de zoekbalk.
  for (const n of d.prepare("SELECT * FROM nodes WHERE name LIKE ? ESCAPE '\\' ORDER BY updated DESC LIMIT 20").all(like)) add(n, 'naam');
  for (const r of d.prepare("SELECT n.*, pg.html FROM pages pg JOIN nodes n ON n.id = pg.node WHERE pg.html LIKE ? ESCAPE '\\' ORDER BY pg.updated DESC LIMIT 12").all(like)) add(r, 'inhoud', around(stripHtml(r.html), q));
  for (const r of d.prepare("SELECT n.*, cm.text AS ctext, cm.created AS cwhen FROM comments cm JOIN nodes n ON n.id = cm.node WHERE cm.text LIKE ? ESCAPE '\\' ORDER BY cm.created DESC LIMIT 12").all(like)) add(r, 'reactie', around(String(r.ctext || ''), q));

  // Naam-hits op pagina's zonder snippet: alsnog een stukje tekst meegenemen.
  for (const h of hits) if (!h.snippet && h.type === 'page') {
    const p = d.prepare('SELECT html FROM pages WHERE node = ?').get(h.id);
    if (p) h.snippet = around(stripHtml(p.html), q);
  }
  return Response.json({ hits });
}
