// De Windows-installateur van de desktop-app, opgehaald vanaf de site zelf:
// staff hoeft hem niet meer door te mailen of uit een Discord-kanaal te vissen.
// Het .exe-bestand staat BEWUST niet in git en niet in de release-zip (78 MB
// bouwrommel hoort niet bij de bron), maar in data/downloads/ op de server: die
// map zit op de docker-volume, dus een herbouw laat hem staan en de weekcron
// pakt hem mee in de back-up. Opvragen kan alleen met een geldige sessie - dit
// is een staff-artefact, geen publieke download.
import fs from 'node:fs';
import path from 'node:path';
import { Readable } from 'node:stream';
import { me } from '@/lib/auth';
import { DATA_DIR } from '@/lib/db';

export const dynamic = 'force-dynamic';

const DIR = path.join(DATA_DIR, 'downloads');

/** Nieuwste staffhub-desktop-installateur in de downloadmap, of null. */
function newest() {
  try {
    const hits = fs.readdirSync(DIR)
      .filter((f) => /^staffhub-desktop-.*\.exe$/i.test(f))
      .map((f) => {
        const st = fs.statSync(path.join(DIR, f));
        const m = f.match(/(\d+\.\d+\.\d+)/);
        return { f, file: f, size: st.size, updated: Math.round(st.mtimeMs), version: m ? m[1] : '' };
      })
      .sort((a, b) => b.updated - a.updated);
    return hits[0] || null;
  } catch {
    return null;   // map bestaat nog niet: nette melding terug, geen stacktrace
  }
}

export async function GET(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });

  const hit = newest();
  if (!hit) return Response.json({ error: 'geen desktop-app geupload' }, { status: 503 });

  // ?info=1 = alleen wat er staat (knop-text), ?info=weg = het bestand zelf.
  if (new URL(req.url).searchParams.has('info')) return Response.json(hit);

  const stream = Readable.toWeb(fs.createReadStream(path.join(DIR, hit.f)));
  return new Response(stream, {
    headers: {
      'content-type': 'application/octet-stream',
      'content-length': String(hit.size),
      'content-disposition': `attachment; filename="${hit.f}"`,
      'cache-control': 'no-store',
    },
  });
}
