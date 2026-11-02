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
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState(null);
  const current = data.plugins.find((p) => p.id === plugin);

  const items = useMemo(() => {
    const byName = new Map();
    const entry = (name) => {
      if (!byName.has(name)) byName.set(name, { name, makes: [], uses: [] });
      return byName.get(name);
    };
    for (const r of current.recipes) {
      if (r.result) entry(r.result).makes.push(r);
      for (const o of r.outputs || []) entry(o.name).makes.push(r);
      const used = new Set([...(r.grid || []).flat(), ...(r.ingredients || []).map((i) => i.name)].filter(Boolean));
      for (const n of used) entry(n).uses.push(r);
    }
    return [...byName.values()].sort((a, b) => a.name.localeCompare(b.name));
  }, [current]);

  const shown = useMemo(() => {
    const t = q.trim().toLowerCase();
    return items.filter((i) => !t || i.name.toLowerCase().includes(t));
  }, [items, q]);

  const sel = items.find((i) => i.name === selected) || null;

  return (
    <PluginCtx.Provider value={plugin}>
      <div className="space-y-5">
        <div className="flex flex-wrap gap-2">
          {data.plugins.map((p) => (
            <button key={p.id} onClick={() => { setPlugin(p.id); setSelected(null); setQ(''); }} className={`px-4 py-2 rounded-lg text-sm font-bold border ${plugin === p.id ? 'bg-sp-copper/30 border-sp-gold text-sp-gold' : 'border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold'}`}>
              {p.name}
            </button>
          ))}
        </div>
        <p className="text-sm text-text-warm/70">{current.blurb}</p>
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-text-warm/50" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search an item…" className="w-full bg-black/50 border border-sp-bronze/40 rounded-lg pl-9 pr-3 py-2.5 text-sm text-text-warm" />
        </div>
        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
          <div className="space-y-2">
            <div className="text-xs text-text-warm/50">{shown.length} item{shown.length === 1 ? '' : 's'}</div>
            <div className="grid grid-cols-4 sm:grid-cols-5 gap-2 max-h-[60vh] overflow-y-auto pr-1">
              {shown.map((i) => {
                const active = sel && sel.name === i.name;
                return (
                  <button key={i.name} onClick={() => setSelected(i.name)} title={i.name} className={`flex flex-col items-center gap-1 p-1.5 rounded-md border text-center ${active ? 'border-sp-gold bg-sp-gold/10' : 'border-sp-bronze/30 bg-black/30 hover:border-sp-gold/60'}`}>
                    <div className="h-12 w-12 flex items-center justify-center">
                      <Pic name={i.name} className="h-12 w-12 object-contain" />
                    </div>
                    <span className="text-[10px] leading-tight line-clamp-2 break-words text-text-warm/80">{i.name}</span>
                  </button>
                );
              })}
            </div>
            {shown.length === 0 && <p className="text-text-warm/60 text-sm">No item matches.</p>}
          </div>
          <div className="space-y-4">
            {!sel && <p className="text-text-warm/60 text-sm">Pick an item on the left to see how it is made and what it is used for.</p>}
            {sel && (
              <>
                <div className="flex items-center gap-4">
                  <div className="h-28 w-28 rounded-xl border border-sp-bronze/40 bg-black/40 flex items-center justify-center">
                    <Pic name={sel.name} className="h-24 w-24 object-contain" />
                  </div>
                  <div>
                    <h2 className="font-serif text-2xl text-sp-gold">{sel.name}</h2>
                    <p className="text-xs text-text-warm/60">{sel.makes.length} recipe{sel.makes.length === 1 ? '' : 's'} · used in {sel.uses.length}</p>
                  </div>
                </div>
                {sel.makes.length > 0 && (
                  <div className="space-y-3">
                    <h3 className="text-xs uppercase tracking-wide text-sp-copper font-bold">Made by</h3>
                    {sel.makes.map((r, i) => <RecipeCard key={`m${i}`} r={r} />)}
                  </div>
                )}
                {sel.uses.length > 0 && (
                  <div className="space-y-3">
                    <h3 className="text-xs uppercase tracking-wide text-sp-copper font-bold">Used in</h3>
                    {sel.uses.slice(0, 12).map((r, i) => <RecipeCard key={`u${i}`} r={r} />)}
                    {sel.uses.length > 12 && <p className="text-xs text-text-warm/50">+{sel.uses.length - 12} more: search for them.</p>}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </PluginCtx.Provider>
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
