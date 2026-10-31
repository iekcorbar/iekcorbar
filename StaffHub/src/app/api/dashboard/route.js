import { getDb, now } from '@/lib/db';
import { me } from '@/lib/auth';
import { may } from '@/lib/acl';

export const dynamic = 'force-dynamic';

// Known plugin versions (update when deploying new versions)
const PLUGINS = [
  { name: 'Kitchen', version: '0.3.15', jar: 'Kitchen-0.3.15.jar', notes: 'Steam pot, sake pairing, Wildlife meat recipes' },
  { name: 'Steamworks', version: '0.3.33', jar: 'Steamworks-0.3.33.jar', notes: 'Steam power service for Kitchen' },
  { name: 'Sakagura', version: '0.7.9', jar: 'Sakagura-0.7.9.jar', notes: 'Food spoilage, co-op brewing' },
  { name: 'Wildlife', version: '0.8.5', jar: 'Wildlife-0.8.5.jar', notes: 'Animal rescue, global announcements' },
  { name: 'SiteSync', version: '1.16.4', jar: 'SiteSync-1.16.4.jar', notes: 'Money deduction + delivery on join' },
];

// Deploy checklist items (parsed from TE-DEPLOYEN.txt)
const DEPLOY_ITEMS = [
  { id: 120, plugin: 'Wildlife', version: '0.8.5', action: 'plugins/Wildlife-0.8.5.jar (vervang 0.8.4)', test: 'behaal een challenge-advancement -> chat-bericht voor iedereen' },
  { id: 119, plugin: 'Wildlife', version: '0.8.4', action: 'plugins/Wildlife-0.8.4.jar (vervang 0.8.3)', test: 'wacht lang (extremely rare) -> trap verschijnt -> rechtsklik -> bevrijd' },
  { id: 118, plugin: 'Wildlife', version: '0.8.3', action: 'plugins/Wildlife-0.8.3.jar (vervang 0.8.2)', test: '/advancements -> Wildlife tab -> nieuwe achievements zichtbaar' },
  { id: 117, plugin: 'Steamworks', version: '0.3.33', action: 'plugins/Steamworks-0.3.33.jar (vervang 0.3.32)', test: 'plaats steam pot naast draaiende shaft -> kookt 2x sneller' },
  { id: 116, plugin: 'Kitchen', version: '0.3.15', action: 'plugins/Kitchen-0.3.15.jar (vervang 0.3.14)', test: 'eet een meal terwijl drunk -> regeneratie + action bar' },
  { id: 115, plugin: 'Kitchen', version: '0.3.14', action: 'plugins/Kitchen-0.3.14.jar (vervang 0.3.13)', test: 'Wildlife jacht recepten + Sakagura sake-pairing' },
  { id: 114, plugin: 'Kitchen', version: '0.3.13', action: 'plugins/Kitchen-0.3.13.jar (vervang 0.3.12)', test: 'pluk fruit -> wacht X minuten -> item verdwijnt' },
  { id: 113, plugin: 'Sakagura', version: '0.7.9', action: 'plugins/Sakagura-0.7.9.jar (vervang 0.7.8)', test: '2 spelers bij een cauldron -> sneller koken/mixen' },
  { id: 112, plugin: 'Sakagura', version: '0.7.8', action: 'plugins/Sakagura-0.7.8.jar (vervang 0.7.7)', test: 'pluk fruit -> wacht X minuten -> item verdwijnt' },
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