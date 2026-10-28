import { clearSessionCookie, me } from '@/lib/auth';
import { getDb } from '@/lib/db';

export async function POST() {
  const u = await me();
  if (u) getDb().prepare('DELETE FROM sessions WHERE user = ?').run(u.id);
  await clearSessionCookie();
  return Response.json({ ok: true });
}
