import { getDb, logAudit } from '@/lib/db';
import { checkPass, createSession, setSessionCookie, verifyTotp } from '@/lib/auth';

export const dynamic = 'force-dynamic';

export async function POST(req) {
  const body = await req.json().catch(() => ({}));
  const username = String(body.username || '').toLowerCase().trim();
  const password = String(body.password || '');
  const totp = String(body.totp || '').trim();
  const recoveryCode = String(body.recoveryCode || '').trim().toUpperCase();

  if (!username || !password) {
    return Response.json({ error: 'Onjuze gebruikersnaam of wachtwoord' }, { status: 401 });
  }

  const u = getDb().prepare('SELECT * FROM users WHERE username = ?').get(username);
  if (!u || !checkPass(password, u.pass)) {
    return Response.json({ error: 'Onjuze gebruikersnaam of wachtwoord' }, { status: 401 });
  }

  if (!u.totp_enabled || !u.totp_secret) {
    return Response.json({ error: '2FA niet ingeschakeld' }, { status: 400 });
  }

  // Try TOTP code first
  if (totp && verifyTotp(u.totp_secret, totp)) {
    getDb().prepare('DELETE FROM loginwacht WHERE username = ?').run(username);
    logAudit(req, { area: 'login', actie: 'ingelogd (2fa)', wie: username });
    await setSessionCookie(createSession(u.id, req.headers.get('user-agent') || ''));
    return Response.json({ ok: true });
  }

  // Try recovery code
  if (recoveryCode) {
    const rc = getDb().prepare('SELECT * FROM recovery_codes WHERE user = ? AND code = ? AND used = 0').get(u.id, recoveryCode);
    if (rc) {
      getDb().prepare('UPDATE recovery_codes SET used = 1 WHERE id = ?').run(rc.id);
      getDb().prepare('DELETE FROM loginwacht WHERE username = ?').run(username);
      logAudit(req, { area: 'login', actie: 'ingelogd (recovery code)', wie: username });
      await setSessionCookie(createSession(u.id, req.headers.get('user-agent') || ''));
      return Response.json({ ok: true, usedRecoveryCode: true });
    }
  }

  logAudit(req, { area: 'login', actie: 'fout 2fa', wie: username, detail: 'ongeldige code' });
  return Response.json({ error: 'Ongeldige 2FA code' }, { status: 401 });
}