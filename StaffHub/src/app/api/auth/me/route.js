import { me } from '@/lib/auth';
import { getDb } from '@/lib/db';

export async function GET() {
  const u = await me();
  const count = getDb().prepare('SELECT COUNT(*) AS n FROM users').get().n;
  // `perms` mee: de toonbank knipt knoppen op wat het lid mag (lib/caps), maar
  // de echte poort staat in de API-routes zelf - dit is alleen het oog.
  return Response.json({ user: u ? { id: u.id, username: u.username, role: u.role, team: u.team, perms: u.perms ?? null } : null, needsSetup: count === 0 });
}
