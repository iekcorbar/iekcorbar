import { getDb, logAudit } from '@/lib/db';
import { checkPass, createSession, setSessionCookie } from '@/lib/auth';

// Inloggen. Vóór dit bestand stond hier geen enkele drempel: de nginx-log van
// 28-10 liet vijf verkeerde pogingen zien en niets legde vast wie het probeerde.
// Een staff-wachtwoord is het enige slot op de hele hub (handleidingen, lijsten,
// de netwerkschijf), dus doorproberen mag niet gratis zijn.
//
// Hoe het werkt (in de databak, dus een containerherstart wist een lopende
// blokkade niet):
//   5 foute wachtwoorden binnen 15 min -> account 15 min op slot;
//   elke volgende fout verlengt de slot; bij een gelukte login alles op nul.
// Een op-slot-account krijgt DEZELFDE melding als een verkeerd wachtwoord:
// we laten een aanvaller niet winnen dat hij de juiste naam geraden heeft.
//
// Onthouden voor de eigenaar: een slot staat in tabel `loginwacht`. Met
//   DELETE FROM loginwacht WHERE username = 'naam';
// is het direct opgeheven - mocht je jezelf buiten staan met vijf typfouten.

export const dynamic = 'force-dynamic';

const MAX_POEG = 5;
const VENSTER_MS = 15 * 60 * 1000;
const SLOT_MS = 15 * 60 * 1000;

/** Huidige stand van een account; onbekende naam = schone lei. */
function stand(username, nu) {
  const rij = getDb().prepare('SELECT pogingen, laatste, slot FROM loginwacht WHERE username = ?').get(username);
  if (!rij) return { pogingen: 0, laatste: 0, slot: 0 };
  // Een oude teller buiten het venster begint opnieuw - maar een lopende slot
  // blijft staan, anders kan een aanvaller gewoon even wachten.
  const pogingen = (nu - (rij.laatste || 0)) <= VENSTER_MS ? (rij.pogingen || 0) : 0;
  return { pogingen, laatste: rij.laatste || 0, slot: rij.slot || 0 };
}

function zet(username, s) {
  getDb().prepare(
    'INSERT INTO loginwacht(username,pogingen,laatste,slot) VALUES(?,?,?,?) ' +
    'ON CONFLICT(username) DO UPDATE SET pogingen=excluded.pogingen, laatste=excluded.laatste, slot=excluded.slot')
    .run(username, s.pogingen, s.laatste, s.slot);
}

export async function POST(req) {
  const nu = Date.now();
  const body = await req.json().catch(() => ({}));
  const username = String(body.username || '').toLowerCase().trim();
  const password = String(body.password || '');
  const generiek = Response.json({ error: 'Onjuze gebruikersnaam of wachtwoord' }, { status: 401 });

  if (!username || !password) return generiek;

  // Eerst de slot-check: een geblokkeerd account kost geen scrypt-berekening.
  const s = stand(username, nu);
  if (s.slot > nu) {
    logAudit(req, { area: 'login', actie: 'geblokkeerd', wie: username, detail: `nog ${Math.ceil((s.slot - nu) / 60000)} min op slot` });
    return generiek;
  }

  const u = getDb().prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!u || !checkPass(password, u.pass)) {
    const pogingen = s.pogingen + 1;
    const nieuw = { pogingen, laatste: nu, slot: pogingen >= MAX_POEG ? nu + SLOT_MS : 0 };
    if (nieuw.slot && s.slot) nieuw.slot = Math.max(nieuw.slot, s.slot);      // verlengen
    zet(username, nieuw);
    logAudit(req, {
      area: 'login', actie: 'fout wachtwoord', wie: username,
      detail: pogingen + '/' + MAX_POEG + (nieuw.slot ? ' - op slot tot ' + new Date(nieuw.slot).toLocaleTimeString('nl-NL') : ''),
    });
    return generiek;
  }

  // Gelukt: teller op nul en één regel in de log (wie komt erbij, vanaf waar).
  getDb().prepare('DELETE FROM loginwacht WHERE username = ?').run(username);
  logAudit(req, { area: 'login', actie: 'ingelogd', wie: username, detail: String(req.headers.get('user-agent') || '').slice(0, 60) });

  const res = Response.json({ ok: true });
  await setSessionCookie(createSession(u.id, req.headers.get('user-agent') || ''));
  return res;
}
