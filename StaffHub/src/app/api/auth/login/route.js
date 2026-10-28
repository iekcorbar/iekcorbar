import { getDb } from '@/lib/db';
import { checkPass, createSession, setSessionCookie } from '@/lib/auth';

export async function POST(req) {
  const { username, password } = await req.json().catch(() => ({}));
  const u = getDb().prepare('SELECT * FROM users WHERE username = ?').get(String(username || '').toLowerCase().trim());
  if (!u || !checkPass(String(password || ''), u.pass)) {
    return Response.json({ error: 'Onjuze gebruikersnaam of wachtwoord' }, { status: 401 });
  }
  // Het apparaat meezetten: de admin-ruimte laat anders alleen kale tokens zien.
  await setSessionCookie(createSession(u.id, req.headers.get('user-agent') || ''));
  return Response.json({ ok: true });
}
