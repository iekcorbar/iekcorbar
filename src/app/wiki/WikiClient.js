'use client';

import React, { useState, useEffect } from 'react';
import { useUser } from '../providers';
import {
  BookOpen,
  Search,
  Clock,
  User,
  X,
  FileText,
  Bookmark,
  ChevronRight,
  ArrowLeft,
  Layers,
  GripVertical,
  History,
  Eye,
  Paperclip,
  Download,
  Share2,
  Check
} from 'lucide-react';
import { renderMarkdown } from '@/lib/markdown';
import { Bookcase, OpenBook } from './Bookcase';
import BookArticle from './BookArticle';
import './bookcase.css';

// Rough markdown-to-plaintext for index card previews — doesn't need to be
// perfect, just readable enough to help someone pick the right topic.
function getExcerpt(content, maxLen = 130) {
  const plain = (content || '')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')       // images
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')    // links -> label text
    .replace(/[#>*_`|-]/g, ' ')                 // markdown punctuation
    .replace(/\s+/g, ' ')
    .trim();
  return plain.length > maxLen ? plain.slice(0, maxLen).trim() + '…' : plain;
}

// Staff-only: writes the bundled plugin docs (src/lib/wiki-seeds, one per
// plugin: seed = file name, label = the pages' category) into the wiki, or
// refreshes them after a plugin update.
function SeedImport({ seed, label, hasPages, onDone }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);
  const run = async () => {
    setBusy(true);
    setMsg(null);
    try {
      const res = await fetch('/api/wiki', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'import_seed', seed }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.success) throw new Error(data.error || `Import failed (${res.status})`);
      setMsg({ ok: true, text: `Done: ${data.created} page(s) created, ${data.updated} updated.` });
      onDone && onDone();
    } catch (e) {
      setMsg({ ok: false, text: e.message });
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-sp-bronze/30 bg-neutral-900/50 px-4 py-2 text-xs text-text-warm/60">
      <span>Staff: {label} plugin docs</span>
      <button onClick={run} disabled={busy}
        className="px-3 py-1 rounded-md border border-sp-gold/50 text-sp-gold hover:bg-sp-gold/10 disabled:opacity-50">
        {busy ? 'Importing…' : hasPages ? `Refresh ${label} pages` : `Import ${label} pages`}
      </button>
      {hasPages && !msg && <span className="text-text-warm/40">Overwrites the {label} pages with the docs bundled in the site (old text stays in each page's history).</span>}
      {msg && <span className={msg.ok ? 'text-emerald-300' : 'text-red-300'}>{msg.text}</span>}
    </div>
  );
}

export default function WikiClient() {
  const { user } = useUser();
  const [pages, setPages] = useState([]);
  const [loading, setLoading] = useState(true);

  const [selectedPage, setSelectedPage] = useState(null);
  // Which chapter's book is pulled off the shelf (null = all books on the shelves).
  const [openBook, setOpenBook] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  // Staff drag-reorder state (topics/subtopics, and separately chapters)
  const [draggedId, setDraggedId] = useState(null);
  const [draggedCategory, setDraggedCategory] = useState(null);
  const [savingOrder, setSavingOrder] = useState(false);

  // Staff-defined chapter order, persisted via the generic settings store —
  // null until staff have actually dragged one, meaning "use discovery order".
  const [categoryOrder, setCategoryOrder] = useState(null);

  // Which page's share link was just copied, for a brief "Copied!" state.
  const [copiedId, setCopiedId] = useState(null);

  const pagesList = Array.isArray(pages) ? pages : [];
  // Purely data-driven — this used to always union in the site's original
  // ['Races', 'Jobs', 'Brewery', 'Lands'] seed categories regardless of
  // whether any page still used them, which left permanent empty chapter
  // tabs (clicking one just showed "No entries found") once staff had
  // reorganized everything into real categories.
  const discoveredCategories = Array.from(new Set(pagesList.map(p => p.category)));
  // Apply the staff-defined order where one exists; anything newly discovered
  // (a brand new category nobody has ordered yet) is appended at the end
  // rather than silently dropped.
  const orderedCategories = categoryOrder
    ? [...categoryOrder.filter(c => discoveredCategories.includes(c)), ...discoveredCategories.filter(c => !categoryOrder.includes(c))]
    : discoveredCategories;
  const categories = orderedCategories;
  const topicCounts = {};
  pagesList.forEach(p => {
    if (!p.parentId || !pagesList.some(parent => parent.id === p.parentId)) {
      topicCounts[p.category] = (topicCounts[p.category] || 0) + 1;
    }
  });

  const isStaff = user && (user.role === 'admin' || user.role === 'moderator');

  useEffect(() => {
    fetchWiki();
  }, []);

  useEffect(() => {
    const handleHashChange = () => {
      const hash = window.location.hash ? window.location.hash.substring(1) : '';
      if (hash && pages.length > 0) {
        const match = pages.find(p => p.slug === hash || p.id === hash);
        if (match) {
          setSelectedPage(match);
        }
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, [pages]);

  async function fetchWiki() {
    try {
      const res = await fetch('/api/wiki');
      const data = await res.json();
      setPages(data.pages || []);
      if (Array.isArray(data.categoryOrder)) setCategoryOrder(data.categoryOrder);
      if (data.pages && data.pages.length > 0) {
        // Deep-link support only — a hash/query param jumps straight to an
        // article. Otherwise we land on the chapter index, not an article,
        // so people see the list of topics before reading anything.
        const hash = window.location.hash ? window.location.hash.substring(1) : '';
        const params = new URLSearchParams(window.location.search);
        const querySlug = params.get('page') || hash;

        if (querySlug) {
          const initialPage = data.pages.find(p => p.slug === querySlug || p.id === querySlug);
          if (initialPage) {
            setSelectedPage(initialPage);
          }
        } else {
          // Keep whatever's already selected in sync with the fresh fetch
          // (e.g. after a reorder/save), but don't force-open anything new.
          setSelectedPage(prev => prev ? (data.pages.find(p => p.id === prev.id) || null) : null);
        }
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  const handleSelectPage = (page) => {
    setSelectedPage(page);
    // The article is read inside its chapter's book, so that book must be
    // the open one (also when arriving from search or a deep link).
    setOpenBook(page.category);
  };

  // Back to the chapter's contents page (book stays open)...
  const showContents = () => setSelectedPage(null);
  // ...versus shutting the book: back to the bookcase.
  const closeBook = () => { setSelectedPage(null); setOpenBook(null); };

  const isSearching = searchQuery.trim().length > 0;

  const filteredPages = pages.filter(p =>
    p.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.content.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Top-level topics inside one chapter's book. Subtopics stay tucked inside
  // their parent's article ("Subtopics" section), so they don't clutter it.
  const topLevel = (p) => !p.parentId || !pages.some(parent => parent.id === p.parentId);
  const bookCategory = selectedPage ? selectedPage.category : openBook;
  const bookEntries = bookCategory ? pages.filter(p => p.category === bookCategory && topLevel(p)) : [];
  // While searching, show every match flat — a match nested under a subtopic
  // must stay reachable even if its parent doesn't itself match.
  const indexList = filteredPages;

  const getBreadcrumb = (page) => {
    const chain = [];
    let current = page;
    let guard = 0;
    while (current && guard < 10) {
      chain.unshift(current);
      current = pages.find(p => p.id === current.parentId) || null;
      guard++;
    }
    return chain;
  };

  async function persistOrder(orderedIds) {
    if (!user) return;
    setSavingOrder(true);
    try {
      const res = await fetch('/api/wiki', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'reorder', role: user.role, orderedIds })
      });
      const data = await res.json();
      if (data.pages) setPages(data.pages);
    } catch (err) {
      console.error('Failed to save new wiki order:', err);
    } finally {
      setSavingOrder(false);
    }
  }

  const handleDrop = (targetId, siblingIds) => {
    if (!draggedId || draggedId === targetId) return;
    const newOrder = [...siblingIds];
    const from = newOrder.indexOf(draggedId);
    const to = newOrder.indexOf(targetId);
    setDraggedId(null);
    if (from === -1 || to === -1) return;
    newOrder.splice(from, 1);
    newOrder.splice(to, 0, draggedId);

    // Optimistic local reorder so the drag feels instant; the fetch above
    // reconciles with whatever the DB actually saved.
    setPages(prev => {
      const orderMap = new Map(newOrder.map((id, idx) => [id, idx]));
      return [...prev]
        .map(p => orderMap.has(p.id) ? { ...p, sortOrder: orderMap.get(p.id) } : p)
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.title.localeCompare(b.title));
    });

    persistOrder(newOrder);
  };

  async function persistCategoryOrder(newOrder) {
    if (!user) return;
    setSavingOrder(true);
    try {
      const res = await fetch('/api/wiki', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'reorder_categories', role: user.role, categories: newOrder })
      });
      const data = await res.json();
      if (data.categoryOrder) setCategoryOrder(data.categoryOrder);
    } catch (err) {
      console.error('Failed to save new chapter order:', err);
    } finally {
      setSavingOrder(false);
    }
  }

  const handleCategoryDrop = (targetCat) => {
    if (!draggedCategory || draggedCategory === targetCat) return;
    const newOrder = [...orderedCategories];
    const from = newOrder.indexOf(draggedCategory);
    const to = newOrder.indexOf(targetCat);
    setDraggedCategory(null);
    if (from === -1 || to === -1) return;
    newOrder.splice(from, 1);
    newOrder.splice(to, 0, draggedCategory);
    setCategoryOrder(newOrder); // optimistic
    persistCategoryOrder(newOrder);
  };

  // Copies an absolute link to this page (works with the existing
  // ?page=<slug> deep-link handling in fetchWiki) so it can be pasted
  // straight into Discord or in-game chat — not a social-share menu, just
  // the URL on the clipboard.
  const copyShareLink = async (page, e) => {
    e?.stopPropagation();
    const url = `${window.location.origin}/wiki?page=${page.slug}`;
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      // Clipboard API unavailable (older browser, non-secure context) —
      // fall back to the classic hidden-textarea copy trick.
      const ta = document.createElement('textarea');
      ta.value = url;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); } catch { /* nothing more we can do */ }
      document.body.removeChild(ta);
    }
    setCopiedId(page.id);
    setTimeout(() => setCopiedId(prev => (prev === page.id ? null : prev)), 1500);
  };

  // A single topic card, used both for the chapter index and for the
  // "Subtopics" list inside an article. `siblingIds` is the reorderable
  // group this card belongs to — dragging is only offered when that group
  // actually has more than one member and isn't a mixed search-result list.
  const renderTopicCard = (page, siblingIds, { showBreadcrumb = false } = {}) => {
    const childCount = pages.filter(p => p.parentId === page.id).length;
    const canDrag = isStaff && !isSearching && siblingIds.length > 1;
    // Optional cover image/GIF — the first image-type attachment on the
    // page, surfaced on its card rather than staying buried at the bottom
    // of the article. Staff add these via the Staff Panel's file attach
    // control (accepts images and GIFs), same for topics and subtopics.
    const coverImage = page.attachments?.find(a => /\.(png|jpe?g|gif|webp)(\?|$)/i.test(a.url));

    return (
      <div
        key={page.id}
        draggable={canDrag}
        onDragStart={() => setDraggedId(page.id)}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          // Always swallow the browser's own drop behavior — a drop that
          // lands on the cover <img> itself is natively draggable
          // regardless of this div's `draggable` flag, and without this
          // the browser can navigate the page straight to the image URL
          // (surfaces as a bare 404 for a relative path). Reorder logic
          // still only runs when dragging is actually enabled.
          e.preventDefault();
          if (canDrag) handleDrop(page.id, siblingIds);
        }}
        onClick={() => handleSelectPage(page)}
        className={`group relative bg-neutral-900/50 border border-sp-bronze/40 hover:border-sp-gold rounded-xl p-4 cursor-pointer transition-all flex flex-col gap-1.5 overflow-hidden ${draggedId === page.id ? 'opacity-40' : ''}`}
      >
        {coverImage && (
          <img
            src={coverImage.url}
            alt=""
            draggable={false}
            className="-mx-4 -mt-4 mb-1 w-[calc(100%+2rem)] h-28 object-cover border-b border-sp-bronze/30"
          />
        )}
        <div className={`absolute top-2.5 right-2.5 flex items-center gap-1 ${coverImage ? 'bg-black/50 rounded-lg px-0.5' : ''}`}>
          <button
            onClick={(e) => copyShareLink(page, e)}
            title="Copy share link"
            className={`p-1 rounded-sm transition-colors cursor-pointer ${copiedId === page.id ? 'text-emerald-400' : 'text-text-warm/30 hover:text-sp-gold'}`}
          >
            {copiedId === page.id ? <Check className="w-3.5 h-3.5" /> : <Share2 className="w-3.5 h-3.5" />}
          </button>
          {canDrag && (
            <GripVertical className="w-3.5 h-3.5 text-text-warm/25 group-hover:text-sp-gold cursor-grab" />
          )}
        </div>
        <div className="flex items-center gap-1.5 text-[9px] uppercase font-bold text-sp-copper pr-10">
          <Bookmark className="w-3 h-3 shrink-0" />
          {showBreadcrumb ? (
            <span className="truncate">
              {getBreadcrumb(page).slice(0, -1).map(p => p.title).join(' / ') || page.category}
            </span>
          ) : (
            <span>{page.category}</span>
          )}
          {childCount > 0 && (
            <span className="ml-auto text-text-warm/40 normal-case font-normal shrink-0 flex items-center gap-1">
              <Layers className="w-3 h-3" /> {childCount}
            </span>
          )}
        </div>
        <h3 className="font-serif text-base font-bold text-white group-hover:text-sp-gold transition-colors leading-snug">
          {page.title}
        </h3>
        <p className="text-xs text-text-warm/60 line-clamp-2">{getExcerpt(page.content)}</p>
        <div className="text-[9px] text-text-warm/30 flex items-center gap-1 mt-auto pt-1.5">
          <Clock className="w-3 h-3" /> {new Date(page.updatedAt).toLocaleDateString()}
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-6">

      {/* HEADER SECTION */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-sp-bronze pb-4">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <BookOpen className="w-8 h-8 text-sp-copper" /> Chronicles of Iekcorbar
          </h1>
          <p className="text-xs text-text-warm/60 mt-1">Read lore guides, brewery logs, and territory claiming policies.</p>
        </div>
      </div>

      {/* SEARCH — always reachable; results replace the shelves while typing */}
      <div className="relative max-w-xl mx-auto w-full">
        <input
          type="text"
          placeholder="Search the archive..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          className="w-full bg-neutral-900 border border-sp-bronze text-white text-sm rounded-lg pl-10 pr-9 py-3 focus:outline-hidden focus:border-sp-gold"
        />
        <Search className="w-4 h-4 text-text-warm/40 absolute left-3.5 top-3.5" />
        {searchQuery && (
          <button
            type="button"
            onClick={() => setSearchQuery('')}
            className="absolute right-3 top-3.5 text-text-warm/40 hover:text-white cursor-pointer"
            title="Clear search"
          >
            <X className="w-4 h-4" />
          </button>
        )}
        {savingOrder && (
          <span className="absolute -bottom-5 right-1 text-[10px] text-text-warm/40 italic">Saving order…</span>
        )}
      </div>

      {isStaff && !isSearching && [[ 'steamworks', 'Steamworks'], ['additions', 'Steamworks Additions'], ['power', 'Steamworks Power'], ['kitchen', 'Kitchen'], ['wildlife', 'Wildlife'], ['sakagura', 'Sakagura'], ['kura', 'Kura Storage'], ['satchel', 'Satchel Backpacks'], ['zukan', 'Zukan'], ['gouw', 'Gouw Land Claims'], ['xmas', 'Xmas Town'], ['relics', 'Relics']].map(([seed, label]) => (
        <SeedImport key={seed} seed={seed} label={label} hasPages={pagesList.some(p => p.category === label)} onDone={fetchWiki} />
      ))}

      {/* MAIN AREA: the bookcase, or search results. An open book (with its
          article, if any) is layered on top — see OpenBook in Bookcase.js. */}
      {isSearching ? (
        <div className="space-y-3">
          <span className="text-[10px] uppercase font-bold text-text-warm/40 tracking-wider">
            Results for "{searchQuery}"
          </span>
          {indexList.length === 0 ? (
            <div className="p-8 text-center text-xs text-text-warm/40 border border-sp-bronze/30 rounded-xl">No entries found.</div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
              {indexList.map(page => renderTopicCard(page, [], { showBreadcrumb: true }))}
            </div>
          )}
        </div>
      ) : loading ? (
        <div className="p-8 text-center text-xs text-text-warm/40 border border-sp-bronze/30 rounded-xl">Scanning archives...</div>
      ) : categories.length === 0 ? (
        <div className="p-8 text-center text-xs text-text-warm/40 border border-sp-bronze/30 rounded-xl">The archive is empty.</div>
      ) : (
        <Bookcase
          categories={categories}
          counts={topicCounts}
          totalTopics={pagesList.length}
          openBook={bookCategory}
          onOpen={setOpenBook}
          isStaff={isStaff}
          draggedCategory={draggedCategory}
          setDraggedCategory={setDraggedCategory}
          onDropCategory={handleCategoryDrop}
        />
      )}

      {bookCategory && !loading && (
        <OpenBook
          key={bookCategory}
          category={bookCategory}
          entries={bookEntries}
          selectedPage={selectedPage}
          articleNode={selectedPage && (
            <BookArticle
              key={selectedPage.id}
              page={selectedPage}
              pages={pages}
              onSelect={handleSelectPage}
              onCopy={copyShareLink}
              copiedId={copiedId}
            />
          )}
          onShowContents={showContents}
          onClose={closeBook}
          childCount={(page) => pages.filter(p => p.parentId === page.id).length}
          subEntries={(page) => pages.filter(p => p.parentId === page.id)}
          getExcerpt={getExcerpt}
          coverOf={(page) => page.attachments?.find(a => /\.(png|jpe?g|gif|webp)(\?|$)/i.test(a.url))}
          onSelect={handleSelectPage}
          isStaff={isStaff}
          draggedId={draggedId}
          setDraggedId={setDraggedId}
          onDropEntry={handleDrop}
          copiedId={copiedId}
          onCopy={copyShareLink}
        />
      )}

    </div>
  );
}
