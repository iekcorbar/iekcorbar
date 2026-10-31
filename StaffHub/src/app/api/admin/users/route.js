// Staff-accounts: aanmaken, teams, rollen, wachtwoorden én - sinds 0.5.0 - de
// permissies per lid (caps uit lib/caps, opgeslagen als JSON-lijst in
// users.perms). De poort gaat open voor de eigenaar OF voor een lid met de
// cap 'users'; daarbinnen blijven drie sloten dicht voor gedelegeerden:
//   1. eigenaarsaccounts kunnen niet worden gewijzigd of verwijderd;
//   2. rollen (staff/owner) zet alleen de eigenaar;
//   3. vorendrechten (ownervis/purge/users/admin) geeft alleen de eigenaar -
//      sanitizePerms filtert ze weg uit elk voorstel van een 'users'-houder.
// Zo is "mijn tweede kan accounts maken" veilig, en "mijn tweede kan zichzelf
// tot eigenaar maken" onmogelijk.
import { getDb, now, logAudit } from '@/lib/db';
import { hashPass, me, dropSessions } from '@/lib/auth';
import { may, sanitizePerms } from '@/lib/caps';
import { notify } from '@/lib/notify';

async function guard() {
  const u = await me();
  if (!u) return { err: Response.json({ error: 'login' }, { status: 401 }) };
  if (!(u.role === 'owner' || may(u, 'users'))) return { err: Response.json({ error: 'alleen owners' }, { status: 403 }) };
  return { u };
}

export async function GET() {
  const { err } = await guard(); if (err) return err;
  const users = getDb().prepare("SELECT id, username, role, team, perms, created FROM users ORDER BY role DESC, username").all()
    .map((u) => ({ ...u, perms: (() => { try { return u.perms ? JSON.parse(u.perms) : null; } catch { return null; } })() }));
  return Response.json({ users });
}

export async function POST(req) {
  const { u, err } = await guard(); if (err) return err;
  const { username, password, role, team, perms } = await req.json().catch(() => ({}));
  const name = String(username || '').toLowerCase().trim();
  if (!/^[a-z0-9_]{2,24}$/.test(name)) return Response.json({ error: 'Gebruikersnaam: 2-24 kleine letters/cijfers/_' }, { status: 400 });
  if (String(password || '').length < 10) return Response.json({ error: 'Wachtwoord minstens 10 tekens' }, { status: 400 });
  const d = getDb();
  if (d.prepare('SELECT 1 AS x FROM users WHERE username = ?').get(name)) return Response.json({ error: 'die naam is er al' }, { status: 409 });
  const r = u.role === 'owner' && role === 'owner' ? 'owner' : 'staff';   // eigenaar maken kan alleen door de eigenaar
  const pm = sanitizePerms(perms, u);
  d.prepare('INSERT INTO users(username,pass,role,team,perms,created) VALUES(?,?,?,?,?,?)')
    .run(name, hashPass(password), r, String(team || '').trim() || null, pm ? JSON.stringify(pm) : null, now());
  logAudit(req, { area: 'users', actie: 'aanmaken', wie: u.username, detail: name + ' als ' + r });
  return Response.json({ ok: true });
}

export async function PATCH(req) {
  const { u, err } = await guard(); if (err) return err;
  const { id, role, team, password, perms } = await req.json().catch(() => ({}));
  const d = getDb();
  const target = d.prepare('SELECT * FROM users WHERE id = ?').get(Number(id));
  if (!target) return Response.json({ error: 'not found' }, { status: 404 });
  if (target.role === 'owner' && u.role !== 'owner') return Response.json({ error: 'verboden' }, { status: 403 });
  if (password) {
    if (String(password).length < 10) return Response.json({ error: 'Wachtwoord minstens 10 tekens' }, { status: 400 });
    d.prepare('UPDATE users SET pass = ? WHERE id = ?').run(hashPass(password), target.id);
    dropSessions(target.id);
  }
  if (role && u.role === 'owner') d.prepare('UPDATE users SET role = ? WHERE id = ?').run(role === 'owner' ? 'owner' : 'staff', target.id);
  if (team !== undefined) d.prepare('UPDATE users SET team = ? WHERE id = ?').run(String(team).trim() || null, target.id);
  if (perms !== undefined) {
    if (target.role === 'owner') return Response.json({ error: 'eigenaars hebben alles al' }, { status: 400 });
    const pm = perms === null ? null : sanitizePerms(perms, u);
    d.prepare('UPDATE users SET perms = ? WHERE id = ?').run(pm ? JSON.stringify(pm) : null, target.id);
    notify([target.id], { kind: 'perms', text: pm ? 'Your permissions were changed by ' + u.username + '.' : 'Your permissions were reset to the defaults by ' + u.username + '.', from: u.id });
    logAudit(req, { area: 'users', actie: 'permissies', wie: u.username, detail: target.username + ': ' + (pm ? pm.join(',') : 'standaard') });
  }
  return Response.json({ ok: true });
}

export async function DELETE(req) {
  const { u, err } = await guard(); if (err) return err;
  const id = Number(new URL(req.url).searchParams.get('id'));
  if (id === u.id) return Response.json({ error: 'je kunt jezelf niet verwijderen' }, { status: 400 });
  const d = getDb();
  const target = d.prepare('SELECT * FROM users WHERE id = ?').get(id);
  if (target?.role === 'owner' && u.role !== 'owner') return Response.json({ error: 'verboden' }, { status: 403 });
  if (target) logAudit(req, { area: 'users', actie: 'verwijderen', wie: u.username, detail: target.username });
  d.prepare('DELETE FROM davtokens WHERE user = ?').run(id);
  d.prepare('DELETE FROM sessions WHERE user = ?').run(id);
  d.prepare('DELETE FROM users WHERE id = ?').run(id);
  return Response.json({ ok: true });
}
