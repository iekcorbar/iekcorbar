import { getDb } from '@/lib/db';
import { checkPass, createSession, setSessionCookie } from '@/lib/auth';

export async function POST(req) {
  const { username, password } = await req.json().catch(() => ({}));
  const u = getDb().prepare('SELECT * FROM users WHERE username = ?').get(String(username || '').toLowerCase().trim());
  if (!u || !checkPass(String(password || ''), u.pass)) {
    return Response.json({ error: 'Onjuze gebruikersnaam of wachtwoord' }, { status: 401 });
  }
  await setSessionCookie(createSession(u.id));
  return Response.json({ ok: true });
}
