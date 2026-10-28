// WebDAV-tokens van de ingelogde gebruiker (voor Verkenner / iOS Bestanden / Tadfile).
import crypto from 'node:crypto';
import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const rows = getDb().prepare('SELECT token, label, created FROM davtokens WHERE user = ? ORDER BY created DESC').all(u.id);
  return Response.json({ tokens: rows.map((r) => ({ ...r, hint: r.token.slice(0, 6) + '…' + r.token.slice(-4) })) });
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { label } = await req.json().catch(() => ({}));
  const token = 'sh-' + crypto.randomBytes(16).toString('hex');
  getDb().prepare('INSERT INTO davtokens(token,user,label,created) VALUES(?,?,?,?)').run(token, u.id, String(label || 'token').slice(0, 60), now());
  return Response.json({ token }); // het echte adres maar één keer tonen
}

export async function DELETE(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const token = String(new URL(req.url).searchParams.get('token') || '');
  getDb().prepare('DELETE FROM davtokens WHERE token = ? AND user = ?').run(token, u.id);
  return Response.json({ ok: true });
}
