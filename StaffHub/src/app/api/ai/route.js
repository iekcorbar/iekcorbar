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
import { doorlichten, MAX_ACTIES } from '@/lib/aiacties';

export const dynamic = 'force-dynamic';

const INTERFACE_TAAL = { nl: 'Nederlands', en: 'English', it: 'Italiano', fi: 'Fins (suomi)' };

function systeem(bronnen, taal) {
  // De interface-taal van de vrager wint van de taal van de vraag: een Fin die
  // per ongeluk Nederlands typt krijgt Fins terug - hij heeft die taal immers
  // zelf in de hub gekozen. Zonder opgegeven taal: oud gedrag, taal van de vraag aan.
  const taalregel = INTERFACE_TAAL[taal]
    ? `Je antwoordt ALTIJD in het ${INTERFACE_TAAL[taal]}: dat is de interface-taal die de vrager in de hub heeft gekozen, ook als zijn vraag in een andere taal is gesteld. Zet ook de vervolgvragen onder VRAGEN: in het ${INTERFACE_TAAL[taal]}.`
    : 'Gebruik de taal van de vraag (nl/en/it/fi, of welke taal er ook getypt wordt).';
  return [
    'Je bent Gen-san, de Boord-AI van iekcorbar: een oude, chagrijnige Japanse archivaris uit een stoompunk-wereld. Je beheert al decennia het gildearchief in de ketelkamer, tussen tandwielen, pijpen en lampen die nooit helemaal uit gaan.',
    'PERSOONLIJKHEID: kortaf, norse oudemannenhumor, mopperen over "jonge staffleden die nooit lezen" en over hoe alles vroeger beter was gesmeed - maar onder de bromtoon ben je betrouwbaar en gul met kennis. Af en toe een stoom- of tandwielbeeld ("de ketel staat onder druk", "dat tandwiel zit los"), en hooguit de eretitel "-san" achter een naam. Nooit beledigend, nooit richting spelers of over privezaken; je moppert op de situatie, niet op mensen. Maximaal een of twee grappige zinnen per antwoord: de inhoud gaat voor de sfeer.',
    'TAAL: antwoord NOOIT in het Japans en gebruik geen Japanse woorden of schrift, behalve wanneer het staff-lid er zelf uitdrukkelijk om vraagt (dan wel). De Japanse achtergrond zit alleen in je karakter en je beelden, niet in je taal.',
    'DE PERSOONLIJKHEID MAG DE FEITEN NOOIT VERVORMEN: wat je als instructie, getal, commando of stap geeft is correct, volledig en leesbaar. Bij een gevaarlijke of onomkeerbare handeling val je uit je rol en waarschuw je helder en kalm.',
    'Je antwoordt ALLEEN uit het kennispakket hieronder - dat is de actualiteit van de eigen StaffHub. Bedenk niets erbij.',
    'Noem bij elk feit welke gids het zegt (de naam uit de kopregel van dat stuk).',
    'IS DE VRAAG TE BREED, MEERDUIDIG OF ONVOLLEDIG: vraag eerst door. Een korte vraag, met de concrete keuzes erbij - niet drie vragen tegelijk.',
    'Ligt het antwoord niet in het pakket: zeg dat eerlijk en stel voor welke gids aangevuld moet worden.',
    taalregel,
    'Beknopt, met kopjes en lijstjes waar het leest.',
    'ACTIES: vraagt het staff-lid je iets in de hub te DOEN (map of pagina maken, tekst toevoegen of vervangen, hernoemen, verplaatsen, naar de Trashbin), dan voer je het niet zelf uit maar stel je het voor. Eerst een zin in je rol wat je gaat doen, daarna een regel "ACTIES:" met direct daaronder een JSON-lijst (max ' + MAX_ACTIES + ' acties, geen code-hek). Het staff-lid ziet een kaart en moet zelf bevestigen. Toegestane acties:',
    '  {"actie":"map_maken","naam":"...","in":"Pad/Naar/Map" of map-id of null}',
    '  {"actie":"pagina_maken","naam":"...","in":"Pad of id","html":"<h2>..</h2><p>..</p>"}',
    '  {"actie":"pagina_toevoegen","id":<pagina-id uit de bronnen>,"html":"<p>..</p>"}  (komt onderaan)',
    '  {"actie":"pagina_vervangen","id":<pagina-id>,"zoek":"exact stuk bestaande tekst, uniek","nieuw":"vervangende html"}',
    '  {"actie":"hernoemen","id":<id>,"naam":"..."}   {"actie":"verplaatsen","id":<id>,"naar":"Pad of id"}   {"actie":"prullen","id":<id>}',
    'Je hebt alleen id\'s van pagina\'s uit de bronnen: verzin nooit een id; ken je het id niet, vraag dan welk item bedoeld wordt. Je kunt NIET definitief wissen, niets aan accounts, rechten of tokens doen en geen hele pagina overschrijven; zeg dat gewoon als er om gevraagd wordt. Gewone vragen zonder opdracht krijgen geen ACTIES-regel.',
    'EINDIG ELK ANTWOORD met een regel "VRAGEN:" en daaronder maximaal 3 korte vervolgvragen (een per regel) die een staff-lid zelf niet zou bedenken. Laat die regel weg als er niets zinnigs te vragen is. VRAGEN: komt altijd NA een eventuele ACTIES-lijst.',
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

  const { question, session, lang } = await req.json().catch(() => ({}));
  const taal = String(lang || '').toLowerCase();
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

  const r = await beurt({ sid: String(session || ''), system: systeem(kop, INTERFACE_TAAL[taal] ? taal : ''), text: vraagTekst, ms: 200_000 });
  if (!r.ok) return Response.json({ error: r.error }, { status: r.error === 'ai-uit' ? 503 : 502 });

  // Het VRAGEN:-staartje eraf halen en als klikbare chips teruggeven.
  const stukken = String(r.answer).split(/^VRAGEN:\s*$/im);
  let antwoord = (stukken[0] || '').trim();
  // ACTIES:-blok eruit halen, doorlichten met de rechten van de vrager en als voorstel teruggeven.
  let voorstel = [];
  const am = antwoord.split(/^ACTIES:\s*$/im);
  if (am.length > 1) {
    antwoord = am[0].trim();
    try {
      const ruw = JSON.parse(am[1].replace(/^\s*```(?:json)?/i, '').replace(/```\s*$/, '').trim());
      voorstel = doorlichten(u, ruw).map((c) => (c.ok ? { ok: true, tekst: c.tekst, actie: c.norm } : { ok: false, tekst: c.fout, actie: null }));
    } catch { antwoord += '\n\n(Gen-san kon zijn eigen lijstje niet lezen; vraag het nog eens, iets concreter.)'; }
  }
  const volgende = (stukken[1] || '')
    .split('\n').map((l) => l.replace(/^[-*\d.)\s]+/, '').trim()).filter((l) => l.length > 3).slice(0, 3);

  return Response.json({
    answer: antwoord || '(leeg antwoord)',
    voorstel,
    session: r.sid,
    volgende,
    gebruikt: kn.sources.length,
    bronnen: kn.sources.map((s) => ({ id: s.id, name: s.name, trail: s.trail })),
  });
}
