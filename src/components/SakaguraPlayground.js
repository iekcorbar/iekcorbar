'use client';
import { useMemo, useState } from 'react';

// Interactive brew-quality simulator for the Sakagura guide: pick a drink, set how you worked,
// and see the star score the plugin will give. The maths is a direct port of the plugin's
// Recipes.score (Sakagura/src/main/java/com/iekcorbar/sakagura/Recipes.java):
// ingredients 40% (assumed perfect here), cook/mix time 25%, distill runs 15%, age+wood 20%,
// tolerance tol = 1.6 - difficulty * 0.1, and a finished brew below 0.3 becomes a Murky Brew.

const SIMILAR = [['oak', 'dark_oak'], ['oak', 'birch'], ['spruce', 'dark_oak'], ['jungle', 'bamboo'], ['acacia', 'mangrove'], ['cherry', 'birch']];
const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'cherry', 'mangrove', 'bamboo', 'copper'];

const RECIPES = [
  { slug: 'mugicha', name: 'Mugicha', color: '#a8743a', difficulty: 1, alcohol: 0, step: 'M', time: 2, ings: ['3 barley', '2 snowball'] },
  { slug: 'amazake', name: 'Amazake', color: '#f6efe0', difficulty: 2, alcohol: 0, step: 'C', time: 4, ings: ['3 steamed rice', '1 ginger'] },
  { slug: 'nigori', name: 'Nigori Sake', color: '#ffffff', difficulty: 3, alcohol: 12, step: 'C', time: 6, ings: ['5 steamed rice', '2 sugar'] },
  { slug: 'hop_ale', name: 'Hop Ale', color: '#d9a441', difficulty: 3, alcohol: 6, step: 'C', time: 6, age: { years: 2, wood: 'oak' }, ings: ['6 barley', '3 hops'] },
  { slug: 'umeshu', name: 'Umeshu', color: '#c98a3a', difficulty: 5, alcohol: 14, step: 'M', time: 3, age: { years: 3, wood: 'cherry' }, ings: ['6 ume plums', '4 sugar', '1 shochu'] },
  { slug: 'red_wine', name: 'Red Wine', color: '#7b1e2b', difficulty: 6, alcohol: 12, step: 'C', time: 6, age: { years: 4, wood: 'oak' }, ings: ['8 grapes'] },
  { slug: 'sake', name: 'Daiginjo Sake', color: '#f2efe0', difficulty: 6, alcohol: 15, step: 'C', time: 8, age: { years: 2, wood: 'spruce' }, ings: ['6 steamed rice', '1 sugar'] },
  { slug: 'shochu', name: 'Shochu', color: '#e8e4d8', difficulty: 6, alcohol: 25, step: 'C', time: 7, dist: 2, ings: ['6 barley', '2 potato'] },
  { slug: 'sakura_liqueur', name: 'Sakura Liqueur', color: '#ffb7c5', difficulty: 6, alcohol: 18, step: 'C', time: 5, dist: 1, age: { years: 2, wood: 'cherry' }, ings: ['6 cherries', '3 sugar', '4 pink petals'] },
  { slug: 'whiskey', name: 'Scotch Whiskey', color: '#e68228', difficulty: 7, alcohol: 26, step: 'C', time: 10, dist: 2, age: { years: 18, wood: 'spruce' }, ings: ['10 wheat'] },
  { slug: 'steam_whisky', name: 'Steam Whisky', color: '#b06a2c', difficulty: 8, alcohol: 32, step: 'C', time: 10, dist: 3, age: { years: 8, wood: 'copper' }, ings: ['10 barley'] },
  { slug: 'brandy', name: 'French Brandy', color: '#cc7241', difficulty: 8, alcohol: 35, step: 'C', time: 5, dist: 4, age: { years: 12, wood: 'dark_oak' }, ings: ['5 sweet berries'] },
];

const NAME_TIERS = {
  mugicha: ['Weak Mugicha', 'Mugicha', 'Roasted Mugicha'], amazake: ['Thin Amazake', 'Amazake', 'Rich Amazake'],
  nigori: ['Watery Nigori', 'Nigori Sake', 'Creamy Nigori'], hop_ale: ['Flat Ale', 'Hop Ale', 'Golden Hop Ale'],
  umeshu: ['Sour Umeshu', 'Umeshu', 'Aged Umeshu'], red_wine: ['Harsh Red Wine', 'Red Wine', 'Full-Bodied Red Wine'],
  sake: ['Rough Sake', 'Sake', 'Daiginjo Sake'], shochu: ['Harsh Shochu', 'Shochu', 'Smooth Shochu'],
  sakura_liqueur: ['Wilted Liqueur', 'Sakura Liqueur', 'Cherry Blossom Liqueur'], whiskey: ['Unsightly Whiskey', 'Whiskey', 'Scotch Whiskey'],
  steam_whisky: ['Sooty Whisky', 'Steam Whisky', 'Brass Reserve Whisky'], brandy: ['Unsightly Brandy', 'Brandy', 'French Brandy'],
};

function band(q) { return q <= 3 ? 1 : q <= 7 ? 2 : 3; }

/** Port of Recipes.score for a finished brew with perfect ingredients. */
function score(r, work) {
  const tol = 1.6 - r.difficulty * 0.1;
  let total = 0.4, weight = 0.4;
  const axes = [{ label: 'Ingredients', pct: 1 }];
  const timeScore = Math.max(0, 1 - Math.abs(work.time - r.time) / (Math.max(1.5, r.time * 0.4) * tol));
  total += 0.25 * timeScore; weight += 0.25;
  axes.push({ label: r.step === 'C' ? 'Cook time' : 'Mix time', pct: timeScore });
  if (r.dist != null) {
    const d = Math.max(0, 1 - Math.abs(work.dist - r.dist) * 0.3 / tol);
    total += 0.15 * d; weight += 0.15;
    axes.push({ label: 'Distill runs', pct: d });
  }
  if (r.age) {
    let a = Math.max(0, 1 - Math.abs(work.years - r.age.years) / (Math.max(1, r.age.years * 0.4) * tol));
    const woodScore = a * (r.age.wood === 'any' || work.wood === r.age.wood ? 1
      : SIMILAR.some(p => (p[0] === r.age.wood && p[1] === work.wood) || (p[1] === r.age.wood && p[0] === work.wood)) ? 0.75 : 0.55);
    total += 0.2 * woodScore; weight += 0.2;
    axes.push({ label: r.age.wood === 'any' ? 'Ageing' : `Ageing in ${r.age.wood.replace('_', ' ')}`, pct: a });
    axes.push({ label: 'Wood choice', pct: a === 0 ? 0 : woodScore / a });
  }
  const s = Math.min(1, total / weight);
  const quality = s < 0.3 ? 0 : Math.max(1, Math.min(10, Math.round(s * 10)));
  return { s, quality, axes };
}

function Stars({ q }) {
  const full = Math.floor(q / 2), half = q % 2 === 1;
  return (
    <span className="text-lg tracking-wider select-none">
      {[0, 1, 2, 3, 4].map(i => {
        const f = i < full, h = i === full && half;
        return <span key={i} className={f ? 'text-yellow-300' : h ? 'text-yellow-300/70' : 'text-white/20'}>{f ? '★' : h ? '⯪' : '☆'}</span>;
      })}
    </span>
  );
}

function Bottle({ color, murky }) {
  const fluid = murky ? '#5f5032' : color;
  return (
    <svg viewBox="0 0 24 40" className="h-14 w-auto drop-shadow-[0_2px_6px_rgba(0,0,0,0.7)]" aria-hidden>
      <rect x="9.5" y="1" width="5" height="4" rx="1" fill="#8d6b3f" />
      <path d="M10 5 h4 v6 c3 1.5 5 4.5 5 9 v15 a4 4 0 0 1 -4 4 h-6 a4 4 0 0 1 -4 -4 v-15 c0 -4.5 2 -7.5 5 -9 z" fill="#dfeaf0" opacity="0.35" />
      <path d="M10 5 h4 v6 c3 1.5 5 4.5 5 9 v15 a4 4 0 0 1 -4 4 h-6 a4 4 0 0 1 -4 -4 v-15 c0 -4.5 2 -7.5 5 -9 z" fill="none" stroke="#e8f2f8" strokeWidth="1" opacity="0.5" />
      <path d="M7 22 c0 -4 1.5 -7 4.5 -9 h1 c3 2 4.5 5 4.5 9 v13 a3 3 0 0 1 -3 3 h-4 a3 3 0 0 1 -3 -3 z" fill={fluid} opacity="0.9" />
      <path d="M9 26 q2 -3 4 0 t4 0 v10 a3 3 0 0 1 -3 3 h-4 a3 3 0 0 1 -3 -3 z" fill={fluid} />
      <path d="M8.5 34 q3 -2 7 0 v1 q-4 -2 -7 0 z" fill="#ffffff" opacity="0.25" />
    </svg>
  );
}

export default function SakaguraPlayground() {
  const [slug, setSlug] = useState('sake');
  const r = RECIPES.find(x => x.slug === slug);
  const [time, setTime] = useState(r.time);
  const [dist, setDist] = useState(r.dist ?? 0);
  const [years, setYears] = useState(r.age?.years ?? 0);
  const [wood, setWood] = useState(r.age?.wood && r.age.wood !== 'any' ? r.age.wood : 'oak');

  const pick = (x) => {
    setSlug(x.slug); setTime(x.time); setDist(x.dist ?? 0); setYears(x.age?.years ?? 0);
    setWood(x.age && x.age.wood !== 'any' ? x.age.wood : 'oak');
  };
  const perfect = () => { setTime(r.time); setDist(r.dist ?? 0); setYears(r.age?.years ?? 0); if (r.age && r.age.wood !== 'any') setWood(r.age.wood); };

  const { s, quality, axes } = useMemo(() => score(r, { time, dist, years, wood }), [r, time, dist, years, wood]);
  const names = NAME_TIERS[r.slug];
  const label = quality === 0 ? 'Murky Brew' : names[band(quality) - 1];

  return (
    <div className="rounded-xl border-2 border-sp-copper/50 bg-black/40 p-4 sm:p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">Quality playground - how many stars would you get?</p>
        <button onClick={perfect} className="text-[11px] px-2 py-1 rounded border border-sp-bronze/40 text-text-warm/60 hover:text-sp-gold">fill in the perfect brew</button>
      </div>

      <div className="flex flex-wrap gap-1.5">
        {RECIPES.map(x => (
          <button key={x.slug} onClick={() => pick(x)}
            className={`px-2.5 py-1 rounded-md border text-xs ${x.slug === slug ? 'border-sp-gold text-sp-gold bg-sp-gold/10' : 'border-sp-bronze/40 text-text-warm/60 hover:text-sp-gold'}`}>
            {x.name} · ★{x.difficulty}
          </button>
        ))}
      </div>

      <div className="grid sm:grid-cols-[auto_1fr] gap-4 items-start">
        <div className="flex flex-col items-center gap-1 pt-1">
          <Bottle color={r.color} murky={quality === 0} />
          <Stars q={quality} />
          <p className="font-serif text-base leading-tight" style={{ color: quality === 0 ? '#9aa08c' : r.color }}>{label}</p>
          <p className="text-[10px] text-text-warm/50">{r.alcohol > 0 ? `${r.alcohol}% alcohol` : 'no alcohol'} · difficulty {r.difficulty}/10</p>
        </div>

        <div className="space-y-3">
          <div>
            <div className="flex justify-between text-xs mb-0.5">
              <span className="text-text-warm/70">{r.step === 'C' ? 'Cooked' : 'Mixed'} <b className="text-sp-gold">{time} min</b> <span className="text-text-warm/40">(recipe wants {r.time})</span></span>
            </div>
            <input type="range" min={0} max={Math.max(20, r.time * 2)} value={time} onChange={e => setTime(Number(e.target.value))} className="w-full accent-amber-500" />
          </div>
          {r.dist != null && (
            <div>
              <div className="text-xs mb-0.5 text-text-warm/70">Distilled <b className="text-sp-gold">{dist}x</b> <span className="text-text-warm/40">(recipe wants {r.dist} runs)</span></div>
              <input type="range" min={0} max={8} value={dist} onChange={e => setDist(Number(e.target.value))} className="w-full accent-amber-500" />
            </div>
          )}
          {r.age && (
            <div className="grid sm:grid-cols-2 gap-3">
              <div>
                <div className="text-xs mb-0.5 text-text-warm/70">Aged <b className="text-sp-gold">{years} yr</b> <span className="text-text-warm/40">(wants {r.age.years}{r.age.wood !== 'any' ? ` in ${r.age.wood.replace('_', ' ')}` : ''})</span></div>
                <input type="range" min={0} max={Math.max(20, r.age.years * 2)} value={years} onChange={e => setYears(Number(e.target.value))} className="w-full accent-amber-500" />
              </div>
              <label className="text-xs text-text-warm/70 flex items-center gap-2">
                Cask wood
                <select value={wood} onChange={e => setWood(e.target.value)} disabled={r.age.wood === 'any'}
                  className="bg-black/60 border border-sp-bronze/40 rounded-md px-2 py-1 text-sp-gold disabled:opacity-40">
                  {WOODS.map(w => <option key={w} value={w}>{w.replace('_', ' ')}</option>)}
                </select>
              </label>
            </div>
          )}

          <div className="rounded-lg border border-sp-bronze/30 bg-black/30 p-3">
            <div className="h-3 rounded-full bg-black/60 border border-sp-bronze/40 overflow-hidden mb-2">
              <div className={`h-full transition-all duration-300 ${quality === 0 ? 'bg-red-700' : quality >= 8 ? 'bg-yellow-400' : 'bg-amber-500'}`} style={{ width: `${Math.round(s * 100)}%` }} />
            </div>
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px]">
              {axes.map(a => (
                <span key={a.label} className="text-text-warm/60">{a.label}:
                  <b className={a.pct > 0.85 ? 'text-emerald-300 ml-1' : a.pct > 0.45 ? 'text-amber-300 ml-1' : 'text-red-400 ml-1'}>{Math.round(a.pct * 100)}%</b>
                </span>
              ))}
            </div>
            <p className={`text-sm mt-2 ${quality === 0 ? 'text-red-300' : quality >= 8 ? 'text-yellow-300' : 'text-text-warm/70'}`}>
              {quality === 0
                ? 'This comes out a Murky Brew - the recipe is unrecognisable. Stay close to the times, runs and years.'
                : quality >= 10 ? 'Five stars. The sake master nods.'
                  : quality >= 8 ? 'Top band - the good name, the best effects and the smallest hangover.'
                    : quality >= 4 ? 'A solid normal drink. The last stars are in the timings and the wood.'
                    : 'Drinkable, just barely - the poor name and harsh effects. Try the perfect brew above and see the difference.'}
            </p>
          </div>
        </div>
      </div>
      <p className="text-xs text-text-warm/40">Same maths as the live plugin: ingredients 40% (perfect here), time 25%, distill runs 15%, ageing + wood 20%. Wrong-but-familiar cask woods count for 75%; a finished brew below 30% turns Murky.</p>
    </div>
  );
}
