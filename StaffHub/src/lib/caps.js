// Bekwaamheden ("caps") per lid - de erfenis van de eigenaar, bestuurbaar per
// account in het Accounts-venster. De lijst is DE bron: de backend-poort checkt
// dezelfde namen als de vlaggetjes in de toonbank.
//
// Twee lagen, en die bewust:
//   gewone werkrechten (upload/pages/trash/move/tokens) - een staff-lid ZONDER
//     ingestelde permissies heeft die vanzelf, precies zoals vóór 0.5.0;
//     de eigenaar kan ze per lid uitzetten door één keer iets aan te vinken.
//   vorendrechten (ownervis/purge/users/admin) - standaard UIT; die kon alleen
//     de eigenaar en die kunnen alleen door de eigenaar worden weggegeven
//     (PRIV hieronder). Een 'users'-houder mag ze dus zelf niet aan zetetten.
//
// users.perms is NULL zolang de eigenaar nooit iets aanraakte: dan gelden de
// gewone rechten (geen verrassing voor bestaande staff). Eenmaal bewaard is het
// een lijst: wát erin staat mag, wát eruit staat mag niet.
export const CAPS = [
  ['upload',   'Upload files and create folders'],
  ['pages',    'Write and edit documents'],
  ['trash',    'Move things to the trash'],
  ['move',     'Rename and move things'],
  ['tokens',   'Create WebDAV tokens'],
  ['ownervis', 'Open and mark owner-only places'],
  ['purge',    'Delete things permanently (empty the trash)'],
  ['users',    'Manage staff accounts'],
  ['admin',    'See the admin overview (sessions, audit log)'],
];
export const CAP_KEYS = CAPS.map(([k]) => k);
export const PRIV = new Set(['ownervis', 'purge', 'users', 'admin']);
export const STAFF_DEFAULT = ['upload', 'pages', 'trash', 'move', 'tokens'];
const DEFAULT_SET = new Set(STAFF_DEFAULT);

/** Wat een lid mag als er NOOIT iets werd ingesteld (de oude.staffrechten). */
export function effectieveCaps(user) {
  if (!user) return [];
  if (user.role === 'owner') return CAP_KEYS.slice();
  if (Array.isArray(user.perms)) return user.perms.filter((c) => CAP_KEYS.includes(c));
  return STAFF_DEFAULT.slice();
}

/** Mag deze gebruiker `cap`? Owner ja, altijd. Zonder ingestelde perms: de
 *  oude staffrechten; met permslijst: exact die lijst. */
export function may(user, cap) {
  if (!user) return false;
  if (user.role === 'owner') return true;
  if (Array.isArray(user.perms)) return user.perms.includes(cap);
  return DEFAULT_SET.has(cap);
}

/** Schoon een aangedragen lijst: echte namen, vorendrechten alleen voor de
 *  eigenaar. Retourneert NULL voor "oud gedrag" (nooit ingesteld of leeg). */
export function sanitizePerms(lijst, griffier) {
  if (!Array.isArray(lijst)) return null;
  const out = [...new Set(lijst.map(String))].filter((c) => CAP_KEYS.includes(c));
  if (griffier?.role !== 'owner') return out.filter((c) => !PRIV.has(c));
  return out;
}
