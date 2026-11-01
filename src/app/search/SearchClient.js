'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Search as SearchIcon, BookOpen, FileText, Newspaper, ChefHat, Compass } from 'lucide-react';
import { SITE_PAGES, stripMarkdown, scoreEntry, snippet, norm } from '@/lib/site-index';

const KIND = {
  page: { label: 'Page', icon: Compass },
  wiki: { label: 'Wiki', icon: BookOpen },
  news: { label: 'News', icon: Newspaper },
  recipe: { label: 'Recipe', icon: ChefHat },
};

export default function SearchClient() {
  const router = useRouter();
  const params = useSearchParams();
  const q = params.get('q') || '';
  const [input, setInput] = useState(q);
  const [wiki, setWiki] = useState([]);
  const [news, setNews] = useState([]);
  const [recipes, setRecipes] = useState([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => { setInput(q); }, [q]);

  // Wiki, news and recipes are fetched once; matching happens in the browser.
  useEffect(() => {
    let live = true;
    (async () => {
      const [w, n, r] = await Promise.allSettled([
        fetch('/api/wiki').then(x => x.json()),
        fetch('/api/news').then(x => x.json()),
        import('@/lib/recipes-data.json'),
      ]);
      if (!live) return;
      if (w.status === 'fulfilled') {
        setWiki((w.value.pages || []).map(p => ({
          kind: 'wiki', href: `/wiki?page=${encodeURIComponent(p.slug)}`, title: p.title, kw: p.category || '',
          text: stripMarkdown(p.content), meta: p.category,
        })));
      }
      if (n.status === 'fulfilled') {
        setNews((n.value.posts || []).map(p => ({
          kind: 'news', href: '/news', title: p.title, kw: `${p.topic || ''} ${p.type || ''}`, text: stripMarkdown(p.content), meta: p.topic,
        })));
      }
      if (r.status === 'fulfilled') {
        const data = r.value.default || r.value;
        const seen = new Set();
        const out = [];
        for (const pl of data.plugins || []) {
          for (const rec of pl.recipes || []) {
            const key = `${pl.id}:${rec.result}`;
            if (seen.has(key)) continue;
            seen.add(key);
            const ing = (rec.ingredients || []).map(i => i.name).filter(Boolean).join(', ');
            out.push({ kind: 'recipe', href: '/recipes', title: rec.result, kw: pl.name, text: ing ? `Made from: ${ing}` : '', meta: pl.name });
          }
        }
        setRecipes(out);
      }
      setLoaded(true);
    })();
    return () => { live = false; };
  }, []);

  const words = useMemo(() => norm(q).split(/\s+/).filter(w => w.length > 0), [q]);

  const results = useMemo(() => {
    if (!words.length) return [];
    const all = [
      ...SITE_PAGES.map(p => ({ ...p, kind: 'page' })),
      ...wiki, ...news, ...recipes,
    ];
    const scored = [];
    for (const e of all) {
      const s = scoreEntry(e, words);
      if (s > 0) scored.push({ e, s: s + (e.kind === 'page' ? 3 : e.kind === 'wiki' ? 1 : 0) });
    }
    scored.sort((a, b) => b.s - a.s);
    return scored.slice(0, 60).map(x => x.e);
  }, [words, wiki, news, recipes]);

  const submit = (ev) => {
    ev.preventDefault();
    router.push(`/search?q=${encodeURIComponent(input.trim())}`);
  };

  return (
    <div className="space-y-6 pb-12 max-w-3xl mx-auto">
      <section className="text-center space-y-3 pt-2">
        <h1 className="font-serif text-4xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">Search</h1>
        <p className="text-text-warm/70 text-sm">Guides, the wiki, news and every custom recipe in one place.</p>
      </section>

      <form onSubmit={submit} className="flex gap-2">
        <input
          autoFocus
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Try: dragon, claim, whisky, shaft…"
          className="flex-1 px-4 py-3 rounded-lg bg-black/60 border border-sp-bronze/40 text-white text-sm focus:outline-none focus:border-sp-gold"
        />
        <button className="steampunk-btn px-5 rounded-lg text-white inline-flex items-center gap-2 text-sm"><SearchIcon className="w-4 h-4" /> Search</button>
      </form>

      {!q && (
        <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-5 text-sm text-text-warm/80 space-y-2">
          <p>Popular starting points:</p>
          <div className="flex flex-wrap gap-2">
            {['claim land', 'dragon', 'brew', 'steamworks', 'zukan', 'rank'].map(s => (
              <Link key={s} href={`/search?q=${encodeURIComponent(s)}`} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-sp-gold hover:border-sp-gold text-xs">{s}</Link>
            ))}
          </div>
        </div>
      )}

      {q && (
        <p className="text-xs text-text-warm/50">
          {results.length} result{results.length === 1 ? '' : 's'} for &quot;{q}&quot;{!loaded && ' · still loading the wiki…'}
        </p>
      )}

      <ul className="space-y-3">
        {results.map((r, i) => {
          const K = KIND[r.kind] || { label: '', icon: FileText };
          const Icon = K.icon;
          return (
            <li key={`${r.kind}-${r.href}-${i}`}>
              <Link href={r.href} className="block rounded-xl border border-sp-bronze/30 bg-neutral-900/60 p-4 hover:border-sp-gold transition-colors space-y-1">
                <p className="flex items-center gap-2 text-[11px] uppercase tracking-widest text-sp-copper">
                  <Icon className="w-3.5 h-3.5" /> {K.label}{r.meta ? ` · ${r.meta}` : ''}
                </p>
                <p className="font-serif text-lg text-sp-gold leading-tight">{r.title}</p>
                {r.text && <p className="text-sm text-text-warm/70">{snippet(r.text, words)}</p>}
              </Link>
            </li>
          );
        })}
      </ul>

      {q && loaded && results.length === 0 && (
        <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-5 text-sm text-text-warm/80">
          Nothing found. Try a shorter or different word, or browse the <Link href="/wiki" className="text-sp-gold underline">wiki</Link> and{' '}
          <Link href="/start" className="text-sp-gold underline">the new-player route</Link>.
        </div>
      )}
    </div>
  );
}
