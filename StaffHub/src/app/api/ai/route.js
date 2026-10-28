// Boord-AI. Twee dingen maken dit een board in plaats van een gokautomaat:
//  1) De vraag gaat mèt de hub-content mee naar binnen (knowledge.js). De sandbox
//     ziet StaffHub zelf niet; zonder dit packet verzint het model iets.
//  2) Het model mag STEDES terugvragen. Een te brede vraag krijgt eerst één
//     verduidelijking, en elk antwoord eindigt met vervolgvragen die in het board
//     als klikbare chips verschijnen - doorvragen wordt zo geen typwerk.
// GET op deze route is een statusmeting: het board laat dáármeé zien of de
// Boord-AI aan staat, in plaats van dat pas te ontdekken ná een vraag.
import { me } from '@/lib/auth';
import { knowledgeFor } from '@/lib/knowledge';
import { ingeschakeld, beurt, status } from '@/lib/aikit';

export const dynamic = 'force-dynamic';

function systeem(bronnen) {
  return [
    'Je bent de Boord-AI van iekcorbar: een rustige, zakelijke assistent voor het staffteam.',
    'Je antwoordt ALLEEN uit het kennispakket hieronder - dat is de actualiteit van de eigen StaffHub. Bedenk niets erbij.',
    'Noem bij elk feit welke gids het zegt (de naam uit de kopregel van dat stuk).',
    'IS DE VRAAG TE BREED, MEERDUIDIG OF ONVOLLEDIG: vraag eerst door. Eén korte vraag, met de concrete keuzes erbij - niet drie vragen tegelijk.',
    'Ligt het antwoord niet in het pakket: zeg dat eerlijk en stel voor welke gids aangevuld moet worden.',
    'Gebruik de taal van de vraag (nl/en/it/fi, of welke taal er ook getypt wordt). Beknopt, met kopjes en lijstjes waar het leest.',
    'Voer niets uit en verander niets; je bent alleen een lezer.',
    'EINDIG ELK ANTWOORD met een regel "VRAGEN:" en daaronder maximaal 3 korte vervolgvragen (één per regel) die een staff-lid zelf níét zou bedenken. Laat die regel weg als er niets zinnigs te vragen is.',
    `Bronnen staan bekend als [id] PAD :: NAAM. Bekende id's: ${bronnen || 'geen'}.`,
  ].join('\n');
}

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  return Response.json(await status());
}

export async function POST(req) {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  if (!ingeschakeld()) return Response.json({ error: 'ai-uit' }, { status: 503 });

  const { question, session } = await req.json().catch(() => ({}));
  const q = String(question || '').trim();
  if (q.length < 3) return Response.json({ error: 'leeg' }, { status: 400 });
  if (q.length > 4000) return Response.json({ error: 'te lang (max 4000)' }, { status: 400 });

  const kn = knowledgeFor(u, q);
  if (!kn.block) {
    // Zonder pakket is elk antwoord verzinsel. Wel netjes zeggen wat er wél is.
    const titels = kn.bekende.map((n) => `Wat staat er in "${n}"?`);
    return Response.json({
      answer: '', error: 'ai-geen-kennis', bronnen: [], scanned: kn.scanned,
      volgende: ['Welke onderwerpen zijn er in de hub?', ...titels].slice(0, 3),
    });
  }

  const kop = kn.sources.map((s) => `${s.id}:${s.name}`).join(', ');
  const vraagTekst =
    `KENNISPAKKET uit StaffHub (${kn.sources.length} gid(s), alleen wat ${u.username} mag zien):\n\n${kn.block}\n\n---\n` +
    `VRAAG van staff-lid ${u.username} (${u.team ? 'team ' + u.team + ', ' : ''}rol ${u.role}): ${q}`;

  const r = await beurt({ sid: String(session || ''), system: systeem(kop), text: vraagTekst, ms: 200_000 });
  if (!r.ok) return Response.json({ error: r.error }, { status: r.error === 'ai-uit' ? 503 : 502 });

  // Het VRAGEN:-staartje eraf halen en als klikbare chips teruggeven.
  const stukken = String(r.answer).split(/^VRAGEN:\s*$/im);
  const antwoord = (stukken[0] || '').trim();
  const volgende = (stukken[1] || '')
    .split('\n').map((l) => l.replace(/^[-*\d.)\s]+/, '').trim()).filter((l) => l.length > 3).slice(0, 3);

  return Response.json({
    answer: antwoord || '(leeg antwoord)',
    session: r.sid,
    volgende,
    gebruikt: kn.sources.length,
    bronnen: kn.sources.map((s) => ({ id: s.id, name: s.name, trail: s.trail })),
  });
}
