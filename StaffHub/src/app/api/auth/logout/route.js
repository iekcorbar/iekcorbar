// Uitloggen = DIT toestel uitschrijven. Vroeger heet hier "DELETE FROM sessions
// WHERE user = ?", wat alle sessies van het account in één klap introk: wie de
// hub in de browser én in de desktop-app open had, stond na één klik overal
// buiten - en dat voelde als "mijn eigen sessie blokkeert me". Iedere token
// wordt nu los ingetrokken; alles-slopend is er alleen nog bij een
// wachtwoordwissel of een verwijderd account (api/admin/users).
import { cookies } from 'next/headers';
import { SID, clearSessionCookie } from '@/lib/auth';
import { getDb } from '@/lib/db';

export async function POST() {
  const token = (await cookies()).get(SID)?.value;
  if (token) getDb().prepare('DELETE FROM sessions WHERE token = ?').run(token);
  await clearSessionCookie();
  return Response.json({ ok: true });
}
