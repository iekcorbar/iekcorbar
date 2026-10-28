// Boord-AI: doorluizen naar de opencode-agent die in zijn eigen sandbox-container
// draait (netwerk-only bereikbaar, basic-auth, bash/edit staan daar op deny).
// Zonder STAFFHUB_AI_URL op dit platform is de functie netjes UIT (503) - dan
// gebruikt de webapp gewoon de gratis keyword-assistent.
import { me } from '@/lib/auth';

const BASE = () => String(process.env.STAFFHUB_AI_URL || '').replace(/\/+$/, '');
const PASS = () => String(process.env.STAFFHUB_AI_PASS || '');

const SYSTEM = [
  'Je bent de Boord-AI van iekcorbar: een rustige, zakelijke assistent voor het staffteam.',
  'Je mag uitsluitend lezen en zoeken in de werkmap; voer niets uit en verander niets.',
  'Antwoord feitelijk en beknopt, in DEZELFDE TAAL als de vraag (nl/en/it).',
  'Als je iets niet kunt vinden in de beschikbare bestanden: zeg dat eerlijk, verzin niets.',
].join(' ');

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

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!BASE() || !PASS()) return Response.json({ error: 'ai-uit' }, { status: 503 });

  const { question, session } = await req.json().catch(() => ({}));
  const q = String(question || '').trim();
  if (q.length < 3) return Response.json({ error: 'leeg' }, { status: 400 });
  if (q.length > 4000) return Response.json({ error: 'te lang (max 4000)' }, { status: 400 });

  try {
    // Sessie: per chatgesprek (cliënt houdt het id vast), anders een nieuwe.
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

    const r = await api('/session/' + encodeURIComponent(sid) + '/message', {
      method: 'POST', headers: authHeaders(),
      body: JSON.stringify({
        system: SYSTEM,
        parts: [{ type: 'text', text: '[vraag van staff-lid ' + u.username + '] ' + q }],
      }),
    });
    if (!r.ok) return Response.json({ error: 'ai-fout (' + r.status + ')' }, { status: 502 });
    const j = await r.json();
    const answer = (j.parts || []).filter((p) => p.type === 'text').map((p) => p.text).join('\n').trim();
    return Response.json({ answer: answer || '(leeg antwoord)', session: sid });
  } catch (e) {
    return Response.json({ error: 'ai-uit: ' + (e?.name === 'AbortError' ? 'timeout' : e.message) }, { status: 502 });
  }
}
