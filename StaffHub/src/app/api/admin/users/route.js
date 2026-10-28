// Admin-only: staff-accounts aanmaken / teams zetten / verwijderen.
import { getDb, now } from '@/lib/db';
import { hashPass, me } from '@/lib/auth';

async function guard() {
  const u = await me();
  if (!u) return { err: Response.json({ error: 'login' }, { status: 401 }) };
  if (u.role !== 'owner') return { err: Response.json({ error: 'alleen owners' }, { status: 403 }) };
  return { u };
}

export async function GET() {
  const { err } = await guard(); if (err) return err;
  const users = getDb().prepare("SELECT id, username, role, team, created FROM users ORDER BY role DESC, username").all();
  return Response.json({ users });
}

export async function POST(req) {
  const { err } = await guard(); if (err) return err;
  const { username, password, role, team } = await req.json().catch(() => ({}));
  const name = String(username || '').toLowerCase().trim();
  if (!/^[a-z0-9_]{2,24}$/.test(name)) return Response.json({ error: 'Gebruikersnaam: 2-24 kleine letters/cijfers/_' }, { status: 400 });
  if (String(password || '').length < 10) return Response.json({ error: 'Wachtwoord minstens 10 tekens' }, { status: 400 });
  const d = getDb();
  if (d.prepare('SELECT 1 AS x FROM users WHERE username = ?').get(name)) return Response.json({ error: 'die naam is er al' }, { status: 409 });
  const r = role === 'owner' ? 'owner' : 'staff';
  d.prepare('INSERT INTO users(username,pass,role,team,created) VALUES(?,?,?,?,?)')
    .run(name, hashPass(password), r, String(team || '').trim() || null, now());
  return Response.json({ ok: true });
}

export async function PATCH(req) {
  const { err } = await guard(); if (err) return err;
  const { id, role, team, password } = await req.json().catch(() => ({}));
  const d = getDb();
  const u = d.prepare('SELECT * FROM users WHERE id = ?').get(Number(id));
  if (!u) return Response.json({ error: 'not found' }, { status: 404 });
  if (password) {
    if (String(password).length < 10) return Response.json({ error: 'Wachtwoord minstens 10 tekens' }, { status: 400 });
    d.prepare('UPDATE users SET pass = ? WHERE id = ?').run(hashPass(password), u.id);
    d.prepare('DELETE FROM sessions WHERE user = ?').run(u.id);
  }
  if (role) d.prepare('UPDATE users SET role = ? WHERE id = ?').run(role === 'owner' ? 'owner' : 'staff', u.id);
  if (team !== undefined) d.prepare('UPDATE users SET team = ? WHERE id = ?').run(String(team).trim() || null, u.id);
  return Response.json({ ok: true });
}

export async function DELETE(req) {
  const { u, err } = await guard(); if (err) return err;
  const id = Number(new URL(req.url).searchParams.get('id'));
  if (id === u.id) return Response.json({ error: 'je kunt jezelf niet verwijderen' }, { status: 400 });
  const d = getDb();
  d.prepare('DELETE FROM davtokens WHERE user = ?').run(id);
  d.prepare('DELETE FROM sessions WHERE user = ?').run(id);
  d.prepare('DELETE FROM users WHERE id = ?').run(id);
  return Response.json({ ok: true });
}
