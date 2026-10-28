// Eigen profiel: voorkeuren en eigen wachtwoord. Dit is wat élke rol mag; de
// admin-ruimte is apart (/api/admin/*).
//
// Wachtwoord wijzigen vraagt HET OUDE wachtwoord. Zonder die check kan een
// rondslingerende sessie (de laptop in de kantine) het account overnemen en de
// echte gebruiker buiten zetten - precies het gevoel dat de eigenaar vanmiddag
// over sessies had.
import { cookies } from 'next/headers';
import { getDb } from '@/lib/db';
import { SID, me, setSessionCookie, createSession } from '@/lib/auth';
import { checkPass, hashPass } from '@/lib/secure';

export const dynamic = 'force-dynamic';

function parsePrefs(json) {
  try { return json ? JSON.parse(json) : {}; } catch { return {}; }
}

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const d = getDb();
  const rij = d.prepare('SELECT id, username, role, team, created, prefs FROM users WHERE id = ?').get(u.id);
  // Alleen rowid + wat voor toestel het is: een sessie-token verlaat NOOIT de
  // server, ook niet naar de eigenaar - anders kan hij gekopieerd worden.
  // `nu` markeert hét toestel waar dit verzoek vandaan komt: de UI mag alleen
  // ándere toestellen een intrek-knop geven, "dit blad uitschrijven" doet je
  // meglio met de gewone uitlogknop (en is via rowid onmogelijk zonder het
  // token zelf mee te geven, en dát blijft binnen de server).
  const huidigToken = (await cookies()).get(SID)?.value;
  const rijen = d.prepare('SELECT rowid AS id, token, agent, expires FROM sessions WHERE user = ? ORDER BY expires DESC').all(u.id);
  const eigen = rijen.map((s) => ({ id: s.id, agent: s.agent || 'onbekend toestel', tot: s.expires, nu: !!huidigToken && s.token === huidigToken }));
  const gemaakt = d.prepare('SELECT count(*) AS n FROM nodes WHERE owner = ?').get(u.id).n;
  return Response.json({
    user: {
      id: rij.id, username: rij.username, role: rij.role, team: rij.team,
      sinds: rij.created, prefs: parsePrefs(rij.prefs),
    },
    sessies: eigen.map((s) => ({ id: s.id, agent: s.agent || 'onbekend toestel', tot: s.expires })),
    gemaakt,
  });
}

export async function PATCH(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const body = await req.json().catch(() => ({}));
  const d = getDb();

  // ---- voorkeuren (taal en wat er later bijschikt): samenvoegen, niet vervangen
  if (body.prefs && typeof body.prefs === 'object') {
    const huidig = parsePrefs(d.prepare('SELECT prefs FROM users WHERE id = ?').get(u.id)?.prefs);
    const nieuw = { ...huidig, ...body.prefs };
    d.prepare('UPDATE users SET prefs = ? WHERE id = ?').run(JSON.stringify(nieuw), u.id);
    return Response.json({ ok: true, prefs: nieuw });
  }

  // ---- eigen wachtwoord verzetten
  if (body.pass) {
    const oud = String(body.oud || '');
    const nieuw = String(body.pass || '');
    const rij = d.prepare('SELECT pass FROM users WHERE id = ?').get(u.id);
    if (!checkPass(oud, rij.pass)) return Response.json({ error: 'huidige wachtwoord niet goed' }, { status: 403 });
    if (nieuw.length < 10) return Response.json({ error: 'Wachtwoord minstens 10 tekens' }, { status: 400 });
    if (nieuw === oud) return Response.json({ error: 'nieuw is hetzelfde als oud' }, { status: 400 });
    d.prepare('UPDATE users SET pass = ? WHERE id = ?').run(hashPass(nieuw), u.id);

    // DIT toestel blijft ingelogd, alle andere toestellen van het account gaan
    // eruit: wie zijn wachtwoord verzet wil niet meteen opnieuw inloggen, maar
    // wil wél dat de laptop in de kantine buiten staat.
    const huidig = (await cookies()).get(SID)?.value;
    if (huidig && d.prepare('SELECT token FROM sessions WHERE token = ?').get(huidig)) {
      d.prepare('DELETE FROM sessions WHERE user = ? AND token <> ?').run(u.id, huidig);
      return Response.json({ ok: true, anders: 1 });
    }
    // Blaadje zonder geldige sessie (bijv. via WebDAV-wachtwoord): alles op nul.
    d.prepare('DELETE FROM sessions WHERE user = ?').run(u.id);
    const res = Response.json({ ok: true, anders: 1 });
    await setSessionCookie(createSession(u.id, req.headers.get('user-agent') || ''));
    return res;
  }

  return Response.json({ error: 'niets te doen' }, { status: 400 });
}

/**
 * Eén eigen toestel uitschrijven (rowid uit /api/profile). Alleen sessies van
 * deze vrager - een staff-lid kan geen ander account raken, en de admin-ruimte
 * is apart voor de owner. De WHERE-clausule dubbel-checkt op `user`: een geraden
 * rij-id van iemand anders raakt hier dus niets.
 *
 * "Dit toestel zelf" mag ook: de rij verdwijnt, het blaadje merkt bij het
 * volgende verzoek dat `me()` niets meer vindt en valt netjes terug op het
 * inlogscherm. Er is geen cookie weg te halen op serverniveau zonder het token
 * zelf in de UI te laten roundtripp'en, en dát blijven we juist buiten.
 */
export async function DELETE(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const id = Number(new URL(req.url).searchParams.get('sessie'));
  if (!id) return Response.json({ error: 'niets opgegeven' }, { status: 400 });
  const c = getDb().prepare('DELETE FROM sessions WHERE rowid = ? AND user = ?').run(id, u.id).changes;
  if (!c) return Response.json({ error: 'not found' }, { status: 404 });
  return Response.json({ ok: true });
}
