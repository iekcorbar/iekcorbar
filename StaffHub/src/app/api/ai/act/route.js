// Voert een door het staff-lid GOEDGEKEURD AI-voorstel uit. Alles wordt hier opnieuw
// gecontroleerd met de rechten van de ingelogde gebruiker (zie lib/aiacties).
import { me } from '@/lib/auth';
import { uitvoeren } from '@/lib/aiacties';

export const dynamic = 'force-dynamic';

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const { acties } = await req.json().catch(() => ({}));
  if (!Array.isArray(acties) || !acties.length) return Response.json({ error: 'leeg' }, { status: 400 });
  return Response.json({ resultaat: uitvoeren(req, u, acties) });
}
