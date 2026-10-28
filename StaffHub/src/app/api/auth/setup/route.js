// Eerste start: er zijn nog geen accounts. De eerste bezoeker mag zichzelf
// eigenaar noemen - maar alleen als de BOOTSTRAP-claim uit .env.meegestuurd wordt,
// anders kan iedereen de bak overnemen. Na het eerste account is deze deur dicht.
import crypto from 'node:crypto';
import { getDb, now } from '@/lib/db';
import { createSession, hashPass, setSessionCookie } from '@/lib/auth';

export async function POST(req) {
  if (getDb().prepare('SELECT COUNT(*) AS n FROM users').get().n > 0) {
    return Response.json({ error: 'StaffHub is al ingericht' }, { status: 403 });
  }
  const want = process.env.STAFFHUB_BOOTSTRAP;
  if (!want) return Response.json({ error: 'Zet STAFFHUB_BOOTSTRAP in .env om de eerste admin aan te maken' }, { status: 503 });
  const { username, password, bootstrap } = await req.json().catch(() => ({}));
  const got = Buffer.from(String(bootstrap || ''));
  const exp = Buffer.from(String(want));
  if (got.length !== exp.length || !crypto.timingSafeEqual(got, exp)) {
    return Response.json({ error: 'Bootstrap-code onjuist' }, { status: 403 });
  }
  const name = String(username || '').toLowerCase().trim();
  if (!/^[a-z0-9_]{2,24}$/.test(name)) return Response.json({ error: 'Gebruikersnaam: 2-24 kleine letters/cijfers' }, { status: 400 });
  if (String(password || '').length < 10) return Response.json({ error: 'Wachtwoord minstens 10 tekens' }, { status: 400 });
  const id = Number(getDb().prepare('INSERT INTO users(username,pass,role,created) VALUES(?,?,?,?)').run(name, hashPass(password), 'owner', now()).lastInsertRowid);
  await setSessionCookie(createSession(id));
  return Response.json({ ok: true });
}
