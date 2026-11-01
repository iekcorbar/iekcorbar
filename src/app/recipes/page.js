'use client';
import React, { useMemo, useState } from 'react';
import { BookOpen, Search, Hammer, Compass } from 'lucide-react';
import data from '@/lib/recipes-data.json';
import iconIds from '@/lib/recipe-icons.json';
import pictureIds from '@/lib/recipe-pictures.json';
import BuiltBy from '@/components/BuiltBy';

// Pictures: our own items use a render of their model (public/recipes/<plugin>/, from the plugins' preview3d.py),
// everything else a small drawn icon (scripts/make-recipe-icons.py). The plugin being viewed is looked at first.
const PICTURES = Object.fromEntries(Object.entries(pictureIds).map(([k, v]) => [k, new Set(v)]));
const ICONS = new Set(iconIds);
const picId = (name) => (name || '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
const PluginCtx = React.createContext('steamworks');

function usePic(name) {
  const plugin = React.useContext(PluginCtx);
  const id = picId(name);
  if (!id) return null;
  for (const p of [plugin, ...Object.keys(PICTURES).filter((k) => k !== plugin)]) {
    if (PICTURES[p] && PICTURES[p].has(id)) return { src: `/recipes/${p}/${id}.png`, pixel: false };
  }
  if (ICONS.has(id)) return { src: `/recipes/icons/${id}.png`, pixel: true };
  return null;
}

function Pic({ name, className }) {
  const pic = usePic(name);
  if (!pic) return null;
  return <img src={pic.src} alt="" loading="lazy" className={className} style={pic.pixel ? { imageRendering: 'pixelated' } : undefined} />;
}

function Slot({ name }) {
  return (
    <div className={`h-[4.5rem] w-[4.5rem] sm:h-20 sm:w-20 rounded-md border flex flex-col items-center justify-center text-center p-0.5 gap-0.5 ${name ? 'border-sp-bronze/50 bg-black/50 text-text-warm' : 'border-sp-bronze/15 bg-black/20'}`} title={name || ''}>
      {name && <Pic name={name} className="h-9 w-9 sm:h-10 sm:w-10 object-contain" />}
      {name && <span className="text-[8px] leading-[1.05] line-clamp-2 break-words">{name}</span>}
    </div>
  );
}

function Stack({ s }) {
  return (
    <span className="inline-flex items-center gap-1 bg-black/40 border border-sp-bronze/30 rounded px-2 py-0.5 text-xs text-text-warm">
      <Pic name={s.name} className="h-5 w-5 object-contain" />
      {s.count > 1 ? `${s.count}× ` : ''}{s.name}{s.chance ? ` (${s.chance}%)` : ''}
    </span>
  );
}

function RecipeCard({ r }) {
  return (
    <div className="bg-neutral-900/60 border border-sp-bronze/30 rounded-xl p-4 space-y-3 steampunk-glass">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] uppercase tracking-wide text-sp-copper font-bold">{r.category}</span>
        <span className="flex items-baseline gap-2 min-w-0">
          {r.result ? <span className="font-serif text-base text-sp-gold leading-tight truncate">{r.count > 1 ? `${r.count}× ` : ''}{r.result}</span> : null}
          {r.time ? <span className="text-[11px] text-text-warm/60 whitespace-nowrap">{r.time} {r.category === 'Cooking Pot' ? 's' : 's at full speed'}</span> : null}
        </span>
      </div>
      {r.type === 'shaped' && (
        <div className="flex items-center gap-3 flex-wrap">
          <div className="grid gap-1" style={{ gridTemplateColumns: `repeat(${Math.max(...r.grid.map((g) => g.length))}, minmax(0, 1fr))` }}>
            {r.grid.flat().map((n, i) => <Slot key={i} name={n} />)}
          </div>
          <span className="text-sp-gold text-xl">→</span>
          <div className="text-sm font-bold text-sp-gold flex items-center gap-2"><Pic name={r.result} className="h-16 w-16 object-contain" /><span>{r.count > 1 ? `${r.count}× ` : ''}{r.result}</span></div>
        </div>
      )}
      {r.type === 'shapeless' && (
        <div className="space-y-1">
          <div className="flex flex-wrap gap-1">{r.ingredients.map((s, i) => <Stack key={i} s={s} />)}</div>
          <div className="text-xs text-text-warm/60 flex items-center gap-2">Shapeless → <Pic name={r.result} className="h-12 w-12 object-contain" /><span className="text-sp-gold font-bold">{r.count > 1 ? `${r.count}× ` : ''}{r.result}</span></div>
        </div>
      )}
      {r.type === 'machine' && (
        <div className="space-y-1.5">
          <div className="flex flex-wrap gap-1 items-center">{r.ingredients.map((s, i) => <Stack key={i} s={s} />)}</div>
          <div className="flex flex-wrap gap-1 items-center"><span className="text-sp-gold">→</span>{r.outputs.map((s, i) => <Stack key={i} s={s} />)}</div>
          {(r.water > 0 || r.heat || r.note) && (
            <div className="text-[11px] text-text-warm/60">
              {r.water > 0 ? `Water: ${r.water} quarter bucket${r.water === 1 ? '' : 's'}. ` : ''}
              {r.heat ? 'Needs heat. ' : ''}{r.note || ''}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function Browser() {
  const [plugin, setPlugin] = useState(data.plugins[0].id);
  const [cat, setCat] = useState('all');
  const [q, setQ] = useState('');
  const current = data.plugins.find((p) => p.id === plugin);
  const cats = useMemo(() => [...new Set(current.recipes.map((r) => r.category))], [current]);
  const shown = useMemo(() => {
    const t = q.trim().toLowerCase();
    return current.recipes.filter((r) => {
      if (cat !== 'all' && r.category !== cat) return false;
      if (!t) return true;
      const hay = [r.result, ...(r.ingredients || []).map((i) => i.name), ...(r.outputs || []).map((i) => i.name)].filter(Boolean).join(' ').toLowerCase();
      return hay.includes(t);
    });
  }, [current, cat, q]);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap gap-2">
        {data.plugins.map((p) => (
          <button key={p.id} onClick={() => { setPlugin(p.id); setCat('all'); }} className={`px-4 py-2 rounded-lg text-sm font-bold border ${plugin === p.id ? 'bg-sp-copper/30 border-sp-gold text-sp-gold' : 'border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold'}`}>
            {p.name}
          </button>
        ))}
      </div>
      <p className="text-sm text-text-warm/70">{current.blurb}</p>
      <div className="flex flex-wrap gap-3 items-center">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-text-warm/50" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search an item…" className="w-full bg-black/50 border border-sp-bronze/40 rounded-lg pl-9 pr-3 py-2 text-sm text-text-warm" />
        </div>
        <select value={cat} onChange={(e) => setCat(e.target.value)} className="bg-black/50 border border-sp-bronze/40 rounded-lg px-3 py-2 text-sm text-text-warm">
          <option value="all">All types</option>
          {cats.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>
      <div className="text-xs text-text-warm/50">{shown.length} recipe{shown.length === 1 ? '' : 's'}</div>
      <div className="grid gap-3 md:grid-cols-2">
        {shown.map((r, i) => <PluginCtx.Provider key={i} value={plugin}><RecipeCard r={r} /></PluginCtx.Provider>)}
      </div>
      {shown.length === 0 && <p className="text-text-warm/60 text-sm">No recipe matches.</p>}
    </div>
  );
}

function Guide() {
  return (
    <div className="space-y-6 text-text-warm/85 text-sm leading-relaxed">
      <p>Zukan is the server&apos;s in-game recipe book. It lists <b>every item</b> (vanilla and from our plugins) and shows how to make it and what it is used for.</p>
      <div>
        <h3 className="font-serif text-lg text-sp-gold mb-2">Open it</h3>
        <ul className="list-disc pl-5 space-y-1">
          <li>Type <code className="text-sp-gold">/zukan</code> (or /recipes, /jei).</li>
          <li>Craft the <b>Zukan book</b> (a book and a compass) and right-click it, or press <b>F</b> while holding it.</li>
          <li><code className="text-sp-gold">/zukan copper</code> opens the list already searched.</li>
          <li>Hold an item: <code className="text-sp-gold">/zukan hand</code> for its recipes, <code className="text-sp-gold">/zukan uses</code> for its uses.</li>
        </ul>
      </div>
      <div>
        <h3 className="font-serif text-lg text-sp-gold mb-2">The item list</h3>
        <ul className="list-disc pl-5 space-y-1">
          <li><b>Click</b> an item: how it is made. <b>Right-click</b>: what it is used for.</li>
          <li><b>Shift-click</b>: bookmark it. The bookmark button shows only your bookmarks.</li>
          <li>Search: words for a name, <code className="text-sp-gold">#logs</code> for an item group, <code className="text-sp-gold">@steamworks</code> for one plugin.</li>
          <li>The <b>Show</b> button switches between everything, vanilla and each plugin.</li>
        </ul>
      </div>
      <div>
        <h3 className="font-serif text-lg text-sp-gold mb-2">Recipes</h3>
        <ul className="list-disc pl-5 space-y-1">
          <li>Recipe types are tabs along the top: crafting, smelting, brewing and one for each machine.</li>
          <li>The <b>station</b> icon shows where a recipe is made.</li>
          <li>A slot with several options (like &quot;any planks&quot;) cycles through them on its own.</li>
          <li>Use the arrows for more recipes of one type. <b>Back</b> returns to the previous item.</li>
        </ul>
      </div>
      <div>
        <h3 className="font-serif text-lg text-sp-gold mb-2">With Kura</h3>
        <p>At a Kura <b>Crafting Terminal</b>, press <b>Recipes</b> to look up an item. On a crafting recipe, <b>Move ingredients</b> takes what you need from your storage network and fills the grid.</p>
      </div>
    </div>
  );
}

export default function RecipesPage() {
  const [tab, setTab] = useState('recipes');
  return (
    <div className="max-w-5xl mx-auto px-4 py-10 space-y-6">
      <title>Recipes - iekcorbar</title>
      <header className="space-y-2">
        <h1 className="flex items-center gap-3 font-serif text-3xl text-sp-gold font-bold uppercase tracking-wide"><Hammer className="w-7 h-7 text-sp-copper" /> Recipes</h1>
        <p className="text-text-warm/70 text-sm">Every recipe of our plugins in one place, plus how to use Zukan, the in-game recipe book.</p>
      </header>
      <div className="flex gap-2 border-b border-sp-bronze/30">
        {[['recipes', 'Plugin recipes', BookOpen], ['guide', 'Zukan guide', Compass]].map(([id, label, Icon]) => (
          <button key={id} onClick={() => setTab(id)} className={`flex items-center gap-2 px-4 py-2 text-sm font-bold border-b-2 -mb-px ${tab === id ? 'border-sp-gold text-sp-gold' : 'border-transparent text-text-warm/60 hover:text-sp-gold'}`}>
            <Icon className="w-4 h-4" /> {label}
          </button>
        ))}
      </div>
      <section className="bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-5 sm:p-8 steampunk-glass">
        {tab === 'recipes' ? <Browser /> : <Guide />}
      </section>
      <p className="text-xs text-text-warm/40">Generated from the plugin files ({data.generated}). Relics and Wildlife items are listed in-game in Zukan.</p>
      <BuiltBy what="This recipe browser" />
    </div>
  );
}
