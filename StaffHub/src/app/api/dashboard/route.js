import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { may } from '@/lib/acl';

export const dynamic = 'force-dynamic';

// Known plugin versions (update when deploying new versions)
// Gemeten staat op de server — bron: deploy/TE-DEPLOYEN.txt, peil 12 nov ~17:00 (kerstbatch).
const PLUGINS = [
  { name: 'Xmas', version: '0.3.1', jar: 'Xmas-0.3.1.jar', notes: 'Kerstblokken + de kerstman (FMM-model); /xmas santa spawn|goto|remove|status, ook via console' },
  { name: 'Wildlife', version: '0.9.30', jar: 'Wildlife-0.9.30.jar', notes: 'Dierenronde: olifant/giraf/neushoorn/leeuw/ara/pinguin/meerkat op echte maat, hersingerigde botten, orca + deer-AI fixes' },
  { name: 'Kura', version: '0.1.11', jar: 'Kura-0.1.11.jar', notes: 'Eén regel: een kabel verbindt wat hij raakt. Part eraf = shift + rechtsklik met lege hand' },
  { name: 'Kitchen', version: '0.3.28', jar: 'Kitchen-0.3.28.jar', notes: 'Gedeelde gewassen (Kitchen-item is canoniek); tien iconen bijgezet via het pack' },
  { name: 'Sakagura', version: '0.7.14', jar: 'Sakagura-0.7.14.jar', notes: 'Fruit→zaad/boompjes via PDC-match; crashfix (vanilla MaterialChoice)' },
  { name: 'Steamworks', version: '0.3.41', jar: 'Steamworks-0.3.41.jar', notes: 'Pijpen sluiten aan op containers; trein-NPE-fix' },
  { name: 'Relics', version: '0.7.4', jar: 'Relics-0.7.4.jar', notes: '16 kerstprops + countdown-bord met 10 tijdzones' },
  { name: 'Gouw', version: '0.4.2', jar: 'Gouw-0.4.2.jar', notes: 'Vandaag NIET aangeraakt (de 0.3.0-jar in deploy/ was een terugdraai-val)' },
  { name: 'Zukan', version: '0.2.7', jar: 'Zukan-0.2.7.jar', notes: 'Toont weer alle Kitchen-iconen (magenta kubussen weg, build57/58)' },
  { name: 'SiteSync', version: '1.16.4', jar: 'SiteSync-1.16.4.jar', notes: 'Money deduction + delivery on join (niet in de kerstbatch)' },
  { name: 'Resource pack', version: 'build58', jar: 'iekcorbar-pack-build58.zip [0a946951223f]', notes: 'In de RPM-mixer; build55 verwijderd. Client herstarten = downloadbalk afwachten' },
];

// Deploy checklist items (parsed from TE-DEPLOYEN.txt)
// Peil 12 nov ~17:00: jars draaien al — dit is wat de eigenaar nog ZELF doet.
const DEPLOY_ITEMS = [
  { id: 130, plugin: 'Kerstdorp', version: '6 schems', action: 'In het spel: //schem load kerstdorp-01-plein + //paste -a, dan de andere vijf delen (kersboom, rode hut, huisjes-rij, marktstraat, lantaarntoren). Schems staan in plugins/WorldEdit/schematics/', test: 'zes delen op het plein; klokgeleiding en vrij pad voor het countdown-bord intact' },
  { id: 129, plugin: 'Xmas', version: '0.3.1', action: '/xmas santa spawn op het plein (of spawn <world> <x> <z>). Test-cadeaus buiten 24-26 dec: plugins/Xmas/config.yml → santa.gifts-anytime: true', test: 'kerstman loopt binnen 24 blok van zijn plek, bel bij nadering, na herstart dezelfde UUID (status)' },
  { id: 128, plugin: 'Relics', version: '0.7.4', action: '/relics give countdown_board en plaats op het lege pad vóór de toren; shift + rechtsklik = tijdzone', test: 'bord telt af naar 24 dec in de gekozen tijdzone' },
  { id: 127, plugin: 'Discord', version: '12 nov', action: 'Publiceer de 2 posts uit deploy/discord-kerstdorp-dierenronde-1112.md (#announcements + #staff). OUDE discord-wildlife-storage-1112.md NIET posten (build55/0.9.29 nooit live); discord-kura-0.1.11.md blijft geldig', test: 'abonnees zien de post (per post op Publish klikken)' },
  { id: 126, plugin: 'Site', version: 'whatsnew', action: 'site-whatsnew.zip [7314e88b0799] naar Plesk: uitpakken + npm install + build + restart. Plesk is vanaf de agent-machine niet bereikbaar — paneelwerk', test: '/whatsnew toont de kerstbatch + Kura-vergelijking' },
];

export async function GET() {
  const u = await me();
  if (!u) return Response.json({ error: 'login' }, { status: 401 });
  const d = getDb();
  const nu = now();
  return Response.json({
    plugins: PLUGINS,
    deploy: DEPLOY_ITEMS,
    stats: {
      documents: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='page'").get().n,
      folders: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='folder'").get().n,
      files: d.prepare("SELECT count(*) AS n FROM nodes WHERE type='file'").get().n,
      users: d.prepare('SELECT count(*) AS n FROM users').get().n,
      versions: d.prepare('SELECT count(*) AS n FROM versions').get().n,
      comments: d.prepare('SELECT count(*) AS n FROM comments').get().n,
    },
    generated: new Date().toISOString(),
  });
}