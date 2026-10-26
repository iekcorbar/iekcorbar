'use client';

import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { X, Share2, Check, Layers, GripVertical, ChevronRight, ArrowLeft } from 'lucide-react';

// Steampunk bookcase for the wiki start page. Every chapter (category) is a
// leather volume on a shelf; clicking one pulls it out and opens it to its
// topic list (OpenBook). All visuals are CSS in bookcase.css — no animation
// library, so nothing extra to install or ship.

const LEATHERS = [
  { bg: '#5b1a1a', band: '#d4af37' },
  { bg: '#1f3b2c', band: '#c9a24b' },
  { bg: '#1c2f4a', band: '#d9b45a' },
  { bg: '#4a2c14', band: '#e0b25a' },
  { bg: '#3e1f3d', band: '#cfa84a' },
  { bg: '#2a2a2e', band: '#b87333' },
  { bg: '#6a3b12', band: '#e6c06a' },
  { bg: '#17403f', band: '#d4af37' },
  { bg: '#6b2a1e', band: '#c9a24b' }
];

function hash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

// Stable per-chapter look, derived from the name (not the position) so a
// volume keeps its colour and size when staff drag the shelves around.
function spineLook(name) {
  const h = hash(name);
  const leather = LEATHERS[h % LEATHERS.length];
  return {
    bg: leather.bg,
    band: leather.band,
    height: 215 + (h >> 4) % 70,
    width: 58 + (h >> 9) % 26,
    tilt: (h >> 14) % 9 === 0 ? ((h >> 3) % 2 ? 5 : -5) : 0
  };
}

const ROMAN = [['M', 1000], ['CM', 900], ['D', 500], ['CD', 400], ['C', 100], ['XC', 90], ['L', 50], ['XL', 40], ['X', 10], ['IX', 9], ['V', 5], ['IV', 4], ['I', 1]];
function toRoman(n) {
  let out = '';
  for (const [s, v] of ROMAN) while (n >= v) { out += s; n -= v; }
  return out;
}

function gearPath(teeth, rOut, rIn, hole) {
  const step = (Math.PI * 2) / teeth;
  const offsets = [-0.3, -0.17, 0.17, 0.3];
  const pts = [];
  for (let i = 0; i < teeth; i++) {
    const a = i * step;
    pts.push([rIn, a + offsets[0] * step], [rOut, a + offsets[1] * step], [rOut, a + offsets[2] * step], [rIn, a + offsets[3] * step]);
  }
  const outline = pts.map(([r, t], i) => `${i ? 'L' : 'M'}${(50 + r * Math.cos(t)).toFixed(2)} ${(50 + r * Math.sin(t)).toFixed(2)}`).join('') + 'Z';
  const bore = `M${50 + hole} 50A${hole} ${hole} 0 1 0 ${50 - hole} 50A${hole} ${hole} 0 1 0 ${50 + hole} 50Z`;
  return outline + bore;
}

export function Gear({ size = 60, teeth = 14, className = '', style }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} className={`bc-gear ${className}`} style={style} aria-hidden="true">
      <defs>
        <linearGradient id={`gg-${teeth}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#f0c76a" />
          <stop offset="0.5" stopColor="#b87333" />
          <stop offset="1" stopColor="#5e3512" />
        </linearGradient>
      </defs>
      <path d={gearPath(teeth, 48, 39, 9)} fill={`url(#gg-${teeth})`} fillRule="evenodd" stroke="#2a1608" strokeWidth="1.2" />
      <circle cx="50" cy="50" r="27" fill="none" stroke="#2a1608" strokeOpacity="0.5" strokeWidth="2" />
      <circle cx="50" cy="50" r="18" fill="none" stroke="#f0c76a" strokeOpacity="0.35" strokeWidth="1.5" />
    </svg>
  );
}

function usePerRow() {
  const [perRow, setPerRow] = useState(7);
  useEffect(() => {
    const calc = () => {
      const w = window.innerWidth;
      setPerRow(w < 640 ? 0 : w < 900 ? 4 : w < 1180 ? 5 : 7); // 0 = one swipeable shelf
    };
    calc();
    window.addEventListener('resize', calc);
    return () => window.removeEventListener('resize', calc);
  }, []);
  return perRow;
}

export function Bookcase({ categories, counts, openBook, onOpen, isStaff, draggedCategory, setDraggedCategory, onDropCategory, totalTopics }) {
  const perRow = usePerRow();
  const rows = perRow === 0 || categories.length === 0
    ? [categories]
    : Array.from({ length: Math.ceil(categories.length / perRow) }, (_, r) => categories.slice(r * perRow, (r + 1) * perRow));

  let index = 0;
  return (
    <div className="bc-stage">
      <div className="bc-steam bc-steam-l" aria-hidden="true"><i /><i /><i /></div>
      <div className="bc-steam bc-steam-r" aria-hidden="true"><i /><i /><i /></div>

      <div className="bc-case">
        <span className="bc-rivet bc-rivet-tl" /><span className="bc-rivet bc-rivet-tr" />
        <span className="bc-rivet bc-rivet-bl" /><span className="bc-rivet bc-rivet-br" />

        <div className="bc-crown">
          <Gear size={54} teeth={12} className="bc-spin-slow" />
          <div className="bc-plaque">
            <span className="bc-plaque-title">The Great Archive</span>
            <span className="bc-plaque-sub">{categories.length} volumes · {totalTopics} chronicles</span>
          </div>
          <Gear size={54} teeth={12} className="bc-spin-slow-rev" />
        </div>

        <div className="bc-interior">
          <div className="bc-glow" aria-hidden="true" />
          {rows.map((row, r) => (
            <div className="bc-shelf" key={r}>
              <div className={`bc-books ${perRow === 0 ? 'bc-books-scroll' : ''}`}>
                <Gear size={26} teeth={9} className={`bc-bookend ${r % 2 ? 'bc-spin-slow-rev' : 'bc-spin-slow'}`} />
                {row.map((cat) => {
                  const look = spineLook(cat);
                  const i = index++;
                  return (
                    <button
                      key={cat}
                      type="button"
                      className={`bc-spine ${openBook === cat ? 'is-out' : ''} ${draggedCategory === cat ? 'is-dragged' : ''}`}
                      style={{
                        '--c': look.bg, '--band': look.band, '--h': `${look.height}px`, '--w': `${look.width}px`,
                        '--tilt': `${look.tilt}deg`, '--i': i
                      }}
                      draggable={isStaff}
                      onDragStart={() => setDraggedCategory(cat)}
                      onDragEnd={() => setDraggedCategory(null)}
                      onDragOver={(e) => isStaff && e.preventDefault()}
                      onDrop={(e) => { if (isStaff) { e.preventDefault(); onDropCategory(cat); } }}
                      onClick={() => onOpen(cat)}
                      aria-label={`Open ${cat} (${counts[cat] || 0} topics)`}
                    >
                      <span className="bc-band bc-band-t" />
                      <span className="bc-label">{cat}</span>
                      <span className="bc-emblem" />
                      <span className="bc-count">{counts[cat] || 0}</span>
                      <span className="bc-band bc-band-b" />
                      <span className="bc-tag">{cat}</span>
                    </button>
                  );
                })}
                <Gear size={20} teeth={8} className="bc-bookend bc-spin-slow-rev" />
              </div>
              <div className="bc-plank" />
            </div>
          ))}
        </div>

        <div className="bc-base">
          <span>{perRow === 0 ? 'Swipe along the shelf · tap a volume to open it' : isStaff ? 'Drag volumes to rearrange the shelves' : 'Pick a volume to open it'}</span>
        </div>
      </div>
    </div>
  );
}

function EntryList({ entries, selectedPage, compact, childCount, getExcerpt, coverOf, subEntries, onSelect, isStaff, draggedId, setDraggedId, onDropEntry, copiedId, onCopy, baseDelay }) {
  const canDrag = isStaff && entries.length > 1;
  const siblingIds = entries.map((p) => p.id);
  // The top-level topic that "owns" the open article (itself, or its ancestor),
  // so the contents page can highlight it and show its subtopics underneath.
  const activeRootId = selectedPage
    ? (entries.find((e) => e.id === selectedPage.id || subEntries(e).some((c) => c.id === selectedPage.id)) || {}).id
    : null;

  return (
    <ol className={`bk-list ${compact ? 'bk-list-compact' : ''}`} style={{ '--step': `${Math.max(8, Math.min(45, Math.floor(650 / Math.max(entries.length, 1))))}ms` }}>
      {entries.map((page, i) => {
        const kids = childCount(page);
        const cover = compact ? null : coverOf(page);
        const isActive = selectedPage && selectedPage.id === page.id;
        return (
          <li
            key={page.id}
            className={`bk-entry ${draggedId === page.id ? 'is-dragged' : ''} ${isActive ? 'is-active' : ''}`}
            style={{ '--i': i, '--base': baseDelay }}
            draggable={canDrag}
            onDragStart={() => setDraggedId(page.id)}
            onDragEnd={() => setDraggedId(null)}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => { e.preventDefault(); if (canDrag) onDropEntry(page.id, siblingIds); }}
          >
            <div className="bk-entry-row">
              <div
                role="button"
                tabIndex={0}
                className="bk-entry-main"
                onClick={() => onSelect(page)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(page); } }}
              >
                <span className="bk-num">{toRoman(i + 1)}.</span>
                {cover && <img src={cover.url} alt="" draggable={false} className="bk-thumb" />}
                <span className="bk-entry-text">
                  <span className="bk-entry-title">
                    {page.title}
                    {!compact && kids > 0 && <span className="bk-kids"><Layers className="w-3 h-3" /> {kids}</span>}
                  </span>
                  {!compact && <span className="bk-entry-excerpt">{getExcerpt(page.content, 110)}</span>}
                </span>
                {!compact && <ChevronRight className="bk-chev w-4 h-4" />}
              </div>
              {!compact && (
                <div className="bk-entry-tools">
                  <button type="button" onClick={(e) => onCopy(page, e)} title="Copy share link" className={copiedId === page.id ? 'is-copied' : ''}>
                    {copiedId === page.id ? <Check className="w-3.5 h-3.5" /> : <Share2 className="w-3.5 h-3.5" />}
                  </button>
                  {canDrag && <GripVertical className="w-3.5 h-3.5 bk-grip" />}
                </div>
              )}
              {compact && canDrag && <GripVertical className="w-3 h-3 bk-grip" />}
            </div>
            {compact && activeRootId === page.id && subEntries(page).length > 0 && (
              <ul className="bk-sublist">
                {subEntries(page).map((c) => (
                  <li key={c.id} className={selectedPage && selectedPage.id === c.id ? 'is-active' : ''}>
                    <button type="button" onClick={() => onSelect(c)}>{c.title}</button>
                  </li>
                ))}
              </ul>
            )}
          </li>
        );
      })}
    </ol>
  );
}

// The open volume. Two modes on the same spread:
//  - no article open: title page on the left, the chapter's topic list on the right
//  - article open: compact contents on the left, the article itself on the right
// "Shut the book" always closes the whole thing, back to the bookcase.
export function OpenBook({ category, entries, selectedPage, articleNode, onShowContents, onClose, isStaff, ...listProps }) {
  const [closing, setClosing] = useState(false);
  const [mounted, setMounted] = useState(false);
  // opened arrives one/two frames after mount: the browser first computes the
  // closed pose, so the flip runs as a transition - interruptible, which is
  // what stops a mid-open "shut" click from teleporting the book.
  const [opened, setOpened] = useState(false);
  // Entries wait for the cover to finish swinging open the first time; once
  // the book has settled (e.g. coming back to Contents) they fade in at once.
  const [settled, setSettled] = useState(false);
  useEffect(() => {
    setMounted(true);
    let inner = 0;
    const outer = requestAnimationFrame(() => { inner = requestAnimationFrame(() => setOpened(true)); });
    const t = setTimeout(() => setSettled(true), 1900);
    return () => { cancelAnimationFrame(outer); cancelAnimationFrame(inner); clearTimeout(t); };
  }, []);

  const close = () => {
    if (closing) return;
    setClosing(true);
    setTimeout(onClose, 620);
  };

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [closing]);

  const countText = entries.length === 1 ? '1 chronicle is' : `${entries.length} chronicles are`;
  const common = { entries, selectedPage, isStaff, ...listProps };

  // Portalled to <body>: an ancestor with a transform would otherwise become
  // the containing block for position:fixed and clip the overlay.
  if (!mounted) return null;
  return createPortal(
    <div
      className={`bk-overlay ${opened ? 'is-opened' : ''} ${closing ? 'is-closing' : ''}`}
      onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}
      role="dialog"
      aria-modal="true"
      aria-label={category}
    >
      <div className="bk-scene">
        <div className={`bk ${opened ? 'is-opened' : ''} ${closing ? 'is-closing' : ''}`}>
          <div className="bk-board" />
          <div className="bk-leftbase" />

          <div className="bk-right">
            <button type="button" className="bk-close" onClick={close} aria-label="Shut the book" title="Shut the book (Esc)">
              <X className="w-4 h-4" />
            </button>

            {selectedPage ? (
              <div className="bk-article-scroll" key={selectedPage.id}>
                <button type="button" className="bk-contents-btn" onClick={onShowContents}>
                  <ArrowLeft className="w-3.5 h-3.5" /> Contents
                </button>
                {articleNode}
              </div>
            ) : (
              <>
                <div className="bk-mobile-head">
                  <h2>{category}</h2>
                  <span>{entries.length} {entries.length === 1 ? 'chronicle' : 'chronicles'}</span>
                </div>
                {entries.length === 0 ? (
                  <p className="bk-empty">The pages of this volume are still blank.</p>
                ) : (
                  <EntryList {...common} compact={false} baseDelay={settled ? '0.05s' : '1.25s'} />
                )}
              </>
            )}
          </div>

          <div className="bk-cover">
            <div className="bk-front">
              <div className="bk-front-frame">
                <Gear size={92} teeth={16} className="bc-spin-slow" />
                <h2>{category}</h2>
                <span className="bk-front-rule" />
                <small>Iekcorbar Archive</small>
              </div>
            </div>
            <div className="bk-back">
              {selectedPage ? (
                <div className="bk-toc">
                  <h2>{category}</h2>
                  <span className="bk-toc-rule"><Gear size={18} teeth={8} className="bc-spin-slow" /></span>
                  <EntryList {...common} compact baseDelay="0s" />
                  <button type="button" className="bk-back-close" onClick={close}>Shut the book</button>
                </div>
              ) : (
                <>
                  <h2>{category}</h2>
                  <span className="bk-back-rule"><Gear size={22} teeth={8} className="bc-spin-slow" /></span>
                  <p>{countText} bound within this volume.</p>
                  <p className="bk-back-hint">
                    Choose a chapter on the opposite page.
                    {isStaff && entries.length > 1 && <><br />Staff: drag a chronicle to reorder.</>}
                  </p>
                  <button type="button" className="bk-back-close" onClick={close}>Shut the book</button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}
