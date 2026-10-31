// Meldingen van de ingelogde gebruiker. GET = lijst + aantal ongelezen (de bel
// pollt dit), POST = { action: 'read' | 'clear' | 'send' }.
import { me } from '@/lib/auth';
import { listFor, markRead, clearRead, broadcast } from '@/lib/notify';
import { logAudit } from '@/lib/db';

export const dynamic = 'force-dynamic';

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  return Response.json(listFor(u.id));
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const b = await req.json().catch(() => ({}));
  if (b.action === 'read') {
    markRead(u.id, b.all ? 'all' : (Array.isArray(b.ids) ? b.ids.slice(0, 100) : []));
    return Response.json(listFor(u.id));
  }
  if (b.action === 'clear') {
    clearRead(u.id);
    return Response.json(listFor(u.id));
  }
  if (b.action === 'send') {
    if (u.role !== 'owner') return Response.json({ error: 'verboden' }, { status: 403 });
    const text = String(b.text || '').trim().slice(0, 300);
    if (!text) return Response.json({ error: 'leeg' }, { status: 400 });
    const sent = broadcast(u.id, text, b.audience);
    logAudit(req, { area: 'notify', actie: 'announce', wie: u.username, detail: `${sent} ontvangers` });
    return Response.json({ sent });
  }
  return Response.json({ error: 'onbekend' }, { status: 400 });
}
