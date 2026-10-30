import { getDb, logAudit } from '@/lib/db';
import { me, needUser } from '@/lib/auth';
import { generateTotpSecret, totpUri, generateRecoveryCodes } from '@/lib/auth';

export const dynamic = 'force-dynamic';

export async function POST(req) {
  const user = await me();
  needUser(user);

  const u = getDb().prepare('SELECT totp_enabled, totp_secret FROM users WHERE id = ?').get(user.id);
  if (u.totp_enabled) {
    return Response.json({ error: '2FA is al ingeschakeld' }, { status: 400 });
  }

  const secret = generateTotpSecret();
  getDb().prepare('UPDATE users SET totp_secret = ? WHERE id = ?').run(secret, user.id);

  const uri = totpUri(secret, user.username);
  logAudit(req, { area: '2fa', actie: 'setup gestart', wie: user.username });
  return Response.json({ secret, uri });
}

export async function PUT(req) {
  const user = await me();
  needUser(user);
  const { code } = await req.json().catch(() => ({}));

  const u = getDb().prepare('SELECT totp_secret FROM users WHERE id = ?').get(user.id);
  if (!u.totp_secret) {
    return Response.json({ error: 'Start eerst de setup' }, { status: 400 });
  }

  const { verifyTotp } = await import('@/lib/auth');
  if (!verifyTotp(u.totp_secret, code)) {
    return Response.json({ error: 'Ongeldige code' }, { status: 401 });
  }

  // Enable 2FA and generate recovery codes
  getDb().prepare('UPDATE users SET totp_enabled = 1 WHERE id = ?').run(user.id);

  const codes = generateRecoveryCodes();
  const now = Date.now();
  for (const code of codes) {
    getDb().prepare('INSERT INTO recovery_codes(user, code, created) VALUES(?,?,?)').run(user.id, code, now);
  }

  logAudit(req, { area: '2fa', actie: 'ingeschakeld', wie: user.username });
  return Response.json({ ok: true, recoveryCodes: codes });
}

export async function DELETE(req) {
  const user = await me();
  needUser(user);
  const { password } = await req.json().catch(() => ({}));

  const u = getDb().prepare('SELECT pass FROM users WHERE id = ?').get(user.id);
  const { checkPass } = await import('@/lib/auth');
  if (!checkPass(password, u.pass)) {
    return Response.json({ error: 'Wachtwoord onjuist' }, { status: 401 });
  }

  getDb().prepare('UPDATE users SET totp_secret = NULL, totp_enabled = 0 WHERE id = ?').run(user.id);
  getDb().prepare('DELETE FROM recovery_codes WHERE user = ?').run(user.id);
  logAudit(req, { area: '2fa', actie: 'uitgeschakeld', wie: user.username });
  return Response.json({ ok: true });
}