// Zichtbaarheid: een node erft van zijn ouders tenzij hij zelf iets stelt in.
// 'all' = iedereen, 'team' = alleen dat team, 'owner' = alleen eigenaar + owners.
import { getDb, ancestors } from './db.js';

export function effectiveVis(node) {
  if (node.vis) return { vis: node.vis, team: node.team };
  for (const a of ancestors(node).reverse()) if (a.vis) return { vis: a.vis, team: a.team };
  return { vis: 'all', team: null };
}

export function canSee(user, node) {
  if (!user) return false;
  if (user.role === 'owner') return true;
  if (node.owner === user.id) return true;
  const { vis, team } = effectiveVis(node);
  if (vis === 'all') return true;
  if (vis === 'team') return !!team && user.team === team;
  return false; // 'owner'-map: alleen de eigenaar (hierboven) of een site-owner
}

export function canWrite(user, node) {
  if (!user) return false;
  if (user.role === 'owner') return true;
  if (!node) return true; // nieuw top-level item door staff: hun eigen
  if (node.owner === user.id) return true;
  const { vis, team } = effectiveVis(node);
  if (vis === 'team') return !!team && user.team === team;
  return vis === 'all'; // openbare mappen: staff mag bijdragen
}

/** Kinderen van een (nul = root) ouder die deze gebruiker mag zien. */
export function visibleChildren(user, parent) {
  return getDb().prepare('SELECT * FROM nodes WHERE parent IS ? ORDER BY type = \'folder\' DESC, name COLLATE NOCASE').all(parent ?? null)
    .filter((n) => canSee(user, n));
}
