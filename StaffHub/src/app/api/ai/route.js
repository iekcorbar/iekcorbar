// Boord-AI: doorluizen naar de opencode-agent in zijn eigen sandbox-container
// (netwerk-only, basic-auth, bash/edit daar op deny).
//
// TWEE dingen maken dit bruikbaar, en geen van beide was er voorheen:
//  1) De vraag gaat mèt de hub-content mee naar binnen. De sandbox ziet StaffHub
//     zelf niet, dus zonder dit pakket moet de AI gokken. nu krijgt hij de
//     pagina's die deze vragensteller mag zien (via knowledge.js) als context.
//  2) GET op deze route is een status-petterdje: de webapp kan vóór iemand iets
//     vraagt laten zien of de Boord-AI aan staat en zo nee, waarom.
import { me } from '@/lib/auth';
import { knowledgeFor } from '@/lib/knowledge';

export const dynamic = 'force-dynamic';

const BASE = () => String(process.env.STAFFHUB_AI_URL || '').replace(/\/+$/, '');
const PASS = () => String(process.env.STAFFHUB_AI_PASS || '');
const MODEL = () => String(process.env.STAFFHUB_AI_MODEL || '');

function authHeaders() {
  const tok = Buffer.from('opencode:' + PASS()).toString('base64');
  return { authorization: 'Basic ' + tok, 'content-type': 'application/json' };
}

async function api(path, init = {}, ms = 150_000) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), ms);
  try { return await fetch(BASE() + path, { ...init, signal: ctl.signal }); }
  finally { clearTimeout(t); }
}

const Systeem = (bronnen) => [
  'Je bent de Boord-AI van iekcorbar: een rustige, zakelijke assistent voor het staffteam.',
  'Je antwoordt UITSLUITEND uit het kennispakket hieronder - dat is de actualiteit van de eigen StaffHub.',
  'Noem bij elk feit welke gids (naam uit de kopregel van het stuk) het zegt. Liggt het antwoord er niet in: zeg dat eerlijk, verzin niets en stel voor welke gids aangevuld moet worden.',
  'Gebruik de taal van de vraag (nl/en/it). Beknopt, met -kopjes en lijstjes waar dat leest.',
  'Voer niets uit en verander niets; je bent alleen een lezer.',
  `Bronnen opgegeven als [id] PAD :: NAAM. Klikbare bron-id's voor de interface: ${bronnen}.`,
].join('\n');

/** Status voor de interface: aan/uit + reden, zonder iemand te laten gokken. */
export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!BASE() || !PASS()) return Response.json({ on: false, reden: 'niet-ingesteld' });
  try {
    // Géén /health verondersteld: /session bestaat zeker (POST erop is de route
    // zelf ook langs). Lijst het netjes, dan staat de sandbox en is de sleutel goed.
    const r = await api('/session', { headers: authHeaders() }, 8_000);
    if (r.ok) return Response.json({ on: true, model: MODEL() || 'standaard van de sandbox' });
    return Response.json({ on: false, reden: r.status === 401 ? 'verkeerde-sleutel' : 'sandbox-fout', code: r.status });
  } catch (e) {
    return Response.json({ on: false, reden: e?.name === 'AbortError' ? 'time-out' : 'sandbox-bereikt-niet' });
  }
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!BASE() || !PASS()) return Response.json({ error: 'ai-uit' }, { status: 503 });

  const { question, session } = await req.json().catch(() => ({}));
  const q = String(question || '').trim();
  if (q.length < 3) return Response.json({ error: 'leeg' }, { status: 400 });
  if (q.length > 4000) return Response.json({ error: 'te lang (max 4000)' }, { status: 400 });

  // Kennis uit de hub, met de rechten van deze vrager.
  const kn = knowledgeFor(u, q);
  if (!kn.block) {
    // Zonder pakket is elk antwoord verzinsel - dat doen we niet.
    return Response.json({
      error: 'ai-geen-kennis',
      sources: [], scanned: kn.scanned,
      hint: 'er is nog geen gids gevonden die bij deze vraag past',
    }, { status: 200 });
  }

  try {
    let sid = String(session || '');
    if (sid) {
      const check = await api('/session/' + encodeURIComponent(sid));
      if (!check.ok) sid = '';
    }
    if (!sid) {
      const r = await api('/session', { method: 'POST', headers: authHeaders(), body: JSON.stringify({ title: 'boordai:' + u.username }) });
      if (!r.ok) return Response.json({ error: 'ai-uit' }, { status: 502 });
      sid = (await r.json()).id || '';
      if (!sid) return Response.json({ error: 'ai-uit' }, { status: 502 });
    }

    const bronnen = kn.sources.map((s) => `${s.id}:${s.name}`).join(', ');
    const body = {
      system: Systeem(bronnen || 'geen'),
      ...(MODEL() ? { model: { modelID: MODEL(), providerID: process.env.STAFFHUB_AI_PROVIDER || 'opencode-go' } } : {}),
      parts: [{
        type: 'text',
        text: `KENNISPAKKET uit StaffHub (${kn.sources.length} gid(s), alleen wat ${u.username} mag zien):\n\n${kn.block}\n\n---\nVRAAG van staff-lid ${u.username}: ${q}`,
      }],
    };
    const r = await api('/session/' + encodeURIComponent(sid) + '/message', { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) });
    if (!r.ok) return Response.json({ error: 'ai-fout (' + r.status + ')' }, { status: 502 });
    const j = await r.json();
    if (j.info?.error) {
      const m = j.info.error.data?.message || j.info.error.name || 'onbekend';
      return Response.json({ error: 'ai-modelfout: ' + m }, { status: 502 });
    }
    const answer = (j.parts || []).filter((p) => p.type === 'text').map((p) => p.text).join('\n').trim();
    return Response.json({
      answer: answer || '(leeg antwoord)',
      session: sid,
      sources: kn.sources,          // klikbaar in de interface
      gebruikt: kn.sources.length,
    });
  } catch (e) {
    return Response.json({ error: 'ai-uit: ' + (e?.name === 'AbortError' ? 'timeout' : e.message) }, { status: 502 });
  }
}
