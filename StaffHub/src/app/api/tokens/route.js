// WebDAV-tokens van de ingelogde gebruiker (voor Verkenner / iOS Bestanden / Tadfile).
import crypto from 'node:crypto';
import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { may } from '@/lib/acl';

// cap 'tokens' (lib/caps): standaard aan voor elk staff-lid, de eigenaar kan
// het uitzetten. Zo krijgt een lid dat volgens de eigenaar geen netwerkschijf
// mag koppelen die deur niet open.
async function magTokens() {
  const u = await me();
  if (!u) return { err: Response.json({ error: 'login' }, { status: 401 }) };
  if (!may(u, 'tokens')) return { err: Response.json({ error: 'verboden' }, { status: 403 }) };
  return { u };
}

export async function GET() {
  const { u, err } = await magTokens(); if (err) return err;
  const rows = getDb().prepare('SELECT token, label, created FROM davtokens WHERE user = ? ORDER BY created DESC').all(u.id);
  return Response.json({ tokens: rows.map((r) => ({ ...r, hint: r.token.slice(0, 6) + '…' + r.token.slice(-4) })) });
}

export async function POST(req) {
  const { u, err } = await magTokens(); if (err) return err;
  const { label } = await req.json().catch(() => ({}));
  const token = 'sh-' + crypto.randomBytes(16).toString('hex');
  getDb().prepare('INSERT INTO davtokens(token,user,label,created) VALUES(?,?,?,?)').run(token, u.id, String(label || 'token').slice(0, 60), now());
  return Response.json({ token }); // het echte adres maar één keer tonen
}

export async function DELETE(req) {
  const { u, err } = await magTokens(); if (err) return err;
  const token = String(new URL(req.url).searchParams.get('token') || '');
  getDb().prepare('DELETE FROM davtokens WHERE token = ? AND user = ?').run(token, u.id);
  return Response.json({ ok: true });
}
