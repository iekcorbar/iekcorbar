// Eén plek om de Boord-AI aan te spreken, want er zijn nu twee klanten: de chat
// (/api/ai) en het vertalen van complete documenten. De sandbox is netwerk-only
// bereikbaar met basic-auth en kent geen buitenwereld.
//
// Antwoordaltijd is hier de prijs die je betaalt: een volledige gids kost al
// gauw een paar model-calls achter elkaar. Daarom alles met dezelfde sessie,
// zodat begrippen over de chunks heen consistent blijven.

const BASE = () => String(process.env.STAFFHUB_AI_URL || '').replace(/\/+$/, '');
const PASS = () => String(process.env.STAFFHUB_AI_PASS || '');
const MODEL = () => String(process.env.STAFFHUB_AI_MODEL || 'beam-free');
const PROVIDER = () => String(process.env.STAFFHUB_AI_PROVIDER || 'opencode-go');

export function ingeschakeld() { return Boolean(BASE() && PASS()); }
export function modelnaam() { return MODEL() || 'standaard van de sandbox'; }

function headers() {
  return { authorization: 'Basic ' + Buffer.from('opencode:' + PASS()).toString('base64'), 'content-type': 'application/json' };
}

async function roep(path, init = {}, ms = 180_000) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), ms);
  try { return await fetch(BASE() + path, { ...init, signal: ctl.signal }); }
  finally { clearTimeout(t); }
}

/** Sessie openen (of de bestaande houden). Geeft '' terug als het niet lukt. */
export async function nieuweSessie(titel) {
  const r = await roep('/session', { method: 'POST', headers: headers(), body: JSON.stringify({ title: String(titel || 'boordai').slice(0, 80) }) }, 20_000);
  if (!r.ok) return '';
  return String((await r.json().catch(() => ({}))).id || '');
}

export async function sessieLeeft(sid) {
  if (!sid) return false;
  try { return (await roep('/session/' + encodeURIComponent(sid), { headers: headers() }, 15_000)).ok; }
  catch { return false; }
}

/**
 * Echte proef of de sandbox bereikbaar is én de sleutel klopt. Zonder deze
 * functie zou het board "aan" zeggen terwijl de sleutel verkeerd is.
 */
export async function status() {
  if (!ingeschakeld()) return { on: false, reden: 'niet-ingesteld' };
  try {
    const r = await roep('/session', { headers: headers() }, 9_000);
    if (r.ok) return { on: true, model: modelnaam() };
    return { on: false, reden: r.status === 401 ? 'verkeerde-sleutel' : 'sandbox-fout', code: r.status };
  } catch (e) {
    return { on: false, reden: e?.name === 'AbortError' ? 'time-out' : 'sandbox-bereikt-niet' };
  }
}

/**
 * Eén beurt: systeemtekst + gebruikersdeel, platte tekst terug.
 * { sid, text, system, ms } -> { ok, answer, sid, error }
 */
export async function beurt({ sid, text, system, ms = 180_000 }) {
  let session = sid || '';
  if (session && !(await sessieLeeft(session))) session = '';
  if (!session) session = await nieuweSessie('boordai');
  if (!session) return { ok: false, error: 'ai-uit' };

  const body = { parts: [{ type: 'text', text }] };
  if (system) body.system = system;
  if (MODEL()) body.model = { modelID: MODEL(), providerID: PROVIDER() };

  const r = await roep('/session/' + encodeURIComponent(session) + '/message', { method: 'POST', headers: headers(), body: JSON.stringify(body) }, ms);
  if (!r.ok) return { ok: false, error: 'ai-fout (' + r.status + ')', sid: session };
  const j = await r.json().catch(() => ({}));
  if (j.info?.error) {
    const m = j.info.error.data?.message || j.info.error.name || 'onbekend';
    return { ok: false, error: 'ai-modelfout: ' + m, sid: session };
  }
  const answer = (j.parts || []).filter((p) => p.type === 'text').map((p) => p.text).join('\n').trim();
  return { ok: true, answer: answer || '(leeg antwoord)', sid: session };
}
