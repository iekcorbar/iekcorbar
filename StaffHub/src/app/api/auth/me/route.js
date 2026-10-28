import { me } from '@/lib/auth';
import { getDb } from '@/lib/db';

export async function GET() {
  const u = await me();
  const count = getDb().prepare('SELECT COUNT(*) AS n FROM users').get().n;
  return Response.json({ user: u ? { id: u.id, username: u.username, role: u.role, team: u.team } : null, needsSetup: count === 0 });
}
