// The in-game rank ladder (CMI Ranks.yml; LuckPerms groups rank_1..rank_5).
// ONE place for the names the website shows, so they stay the same as in the game.
// Keys are the CMI rank keys, normalised (lower-case, no accents, letters only).
export const RANKS = {
  default: { name: 'Adventure', group: 'default' },
  kosei: { name: 'Kōsei', kanji: '工生', group: 'rank_1' },
  kikaishi: { name: 'Kikai-shi', kanji: '機械師', group: 'rank_2' },
  tetsubushi: { name: 'Tetsu-bushi', kanji: '鉄武士', group: 'rank_3' },
  karakuri: { name: 'Karakuri Daimyō', kanji: '絡繰大名', group: 'rank_4' },
  jokitenno: { name: 'Jōki Tennō', kanji: '蒸気天皇', group: 'rank_5' },
};

const norm = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z]/g, '');

/** Display name for a CMI rank key ("Kōsei", "JokiTenno" ...); unknown keys are shown as they are. */
export function rankLabel(key) {
  if (!key) return null;
  const r = RANKS[norm(key)];
  return r ? r.name : String(key);
}
