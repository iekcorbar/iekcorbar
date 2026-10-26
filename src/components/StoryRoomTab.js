'use client';

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import {
  ScrollText, Folder, FolderOpen, FileText, ChevronRight, ChevronDown, Plus, FolderPlus, Pencil, Trash2,
  ArrowUp, ArrowDown, MoveRight, Lock, Unlock, MessageSquare, History, BookOpen, Clock, Search, X, Check,
  RotateCcw, GitCompare, Upload, Users, MapPin, Flag, Gem, Sparkles, Link2, RefreshCw, Eye, Download, KeyRound, Copy, Paperclip
} from 'lucide-react';
import StoryEditor from '@/components/StoryEditor';
import StoryFiles from '@/components/StoryFiles';
import { STORY_STATUSES, STORY_ENTITY_KINDS, extractLoreLinks } from '@/lib/story';

// Staff Panel → Story Room: the staff workspace for the iekcorbar storyline.
// Three views — Chapters (folder tree + editor + comments + history), Lore
// Library (characters/places/factions/items) and Timeline. Backed by
// /api/story/** (all gated by the `story` permission).
//
// Working together: anyone can read any chapter at any time; writing needs
// the chapter's edit lock ("Edit"), which is renewed every 30s while the
// editor is open and expires ~90s after the browser goes away. Others see
// "<name> is editing" plus a read-only copy that refreshes. Every save goes
// into the version history (one entry per writing session), so nothing is
// ever lost and any version can be compared or restored.
//
// Also used outside the Staff Panel on /story-room by people WITHOUT a
// website account, through a personal guest link (src/lib/story-access.js)
// — `guestMode`. What a viewer may do comes from the API (`access` on GET
// /api/story) and is shared through StoryAccessContext; the API enforces
// the same rules, the UI just hides what wouldn't work. Anyone can download
// a chapter, a folder or everything (/api/story/export). Files (images, 3D
// models, schematics…) can be uploaded to a chapter, a lore entry or the
// general Files library — see StoryFiles.js.

const TREE_POLL_MS = 20000;
const HEARTBEAT_MS = 30000;
const AUTOSAVE_MS = 2500;

const KIND_ICONS = { character: Users, place: MapPin, faction: Flag, item: Gem, other: Sparkles };

// Staff in the Staff Panel can do everything; guests start read-only until
// the API says otherwise.
const STAFF_ACCESS = { isGuest: false, canComment: true, canEdit: true, canManage: true, canPublish: true };
const GUEST_DEFAULT_ACCESS = { isGuest: true, canComment: false, canEdit: false, canManage: false, canPublish: false };
const StoryAccessContext = createContext(STAFF_ACCESS);
const useStoryAccess = () => useContext(StoryAccessContext);

const GUEST_LEVELS = [
  { key: 'edit', label: 'Can edit', hint: 'Read, comment and write chapters, lore and timeline. Cannot delete anything.' },
  { key: 'comment', label: 'Can comment', hint: 'Read everything and leave comments.' },
  { key: 'read', label: 'Can read', hint: 'Read and download only.' }
];

// Kept here (not imported from story-export.js, which is server-only).
const DOWNLOAD_FORMATS = [
  { key: 'docx', label: 'Word', ext: '.docx' },
  { key: 'md', label: 'Markdown', ext: '.md' },
  { key: 'txt', label: 'Plain text', ext: '.txt' },
  { key: 'html', label: 'Web page', ext: '.html' }
];

// Fetches a download and saves it, so a failure shows a normal error
// message instead of the browser saving an error page as the file.
async function downloadFile(url) {
  const res = await fetch(url);
  if (!res.ok) {
    let msg = `Download failed (${res.status})`;
    try { msg = (await res.json()).error || msg; } catch { /* not JSON */ }
    throw new Error(msg);
  }
  const cd = res.headers.get('Content-Disposition') || '';
  const star = /filename\*=UTF-8''([^;]+)/i.exec(cd);
  const plain = /filename="([^"]+)"/i.exec(cd);
  const name = star ? decodeURIComponent(star[1]) : plain ? plain[1] : 'download';
  const blob = await res.blob();
  const href = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 10000);
}

// Small "Download ▾" button with a format menu. `buildUrl(format)` gives
// the export URL; `before` (optional) runs first, e.g. to save open edits.
function DownloadMenu({ label = 'Download', title, buildUrl, before, flash, compact = false, align = 'right' }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('mousedown', onDown); document.removeEventListener('keydown', onKey); };
  }, [open]);

  const pick = async (format) => {
    setOpen(false);
    setBusy(true);
    try {
      if (before) await before();
      await downloadFile(buildUrl(format));
    } catch (err) {
      flash?.('error', err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <span ref={ref} className="relative inline-flex">
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        disabled={busy}
        title={title || label}
        aria-label={title || label}
        aria-haspopup="menu"
        aria-expanded={open}
        className={compact
          ? 'p-1 rounded cursor-pointer transition-colors text-text-warm/40 hover:text-white hover:bg-neutral-700/60 disabled:opacity-40'
          : 'flex items-center gap-1 cursor-pointer hover:text-white disabled:opacity-40'}
      >
        <Download className={compact ? 'w-3 h-3' : 'w-3.5 h-3.5'} />
        {!compact && <>{busy ? 'Preparing…' : label}<ChevronDown className="w-3 h-3" /></>}
      </button>
      {open && (
        <span role="menu" className={`absolute z-30 top-full mt-1 ${align === 'left' ? 'left-0' : 'right-0'} min-w-[170px] bg-sp-dark border border-sp-bronze/50 rounded-lg shadow-xl p-1 flex flex-col`}>
          {DOWNLOAD_FORMATS.map(f => (
            <button
              key={f.key}
              role="menuitem"
              type="button"
              onClick={() => pick(f.key)}
              className="flex items-center justify-between gap-3 text-left text-[11px] text-text-warm/85 hover:text-white hover:bg-neutral-800/70 rounded px-2 py-1.5 cursor-pointer"
            >
              <span>{f.label}</span><span className="text-text-warm/40 font-mono text-[10px]">{f.ext}</span>
            </button>
          ))}
        </span>
      )}
    </span>
  );
}

const fmtDateTime = (d) => (d ? new Date(d).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');
const fmtTime = (d) => (d ? new Date(d).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }) : '');

async function api(url, options = {}) {
  const res = await fetch(url, {
    ...options,
    headers: options.body ? { 'Content-Type': 'application/json', ...(options.headers || {}) } : options.headers
  });
  let data = {};
  try { data = await res.json(); } catch { /* empty body */ }
  if (!res.ok) {
    const err = new Error(data.error || `Request failed (${res.status})`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}
const post = (url, body) => api(url, { method: 'POST', body: JSON.stringify(body) });

function StatusBadge({ status, small }) {
  const s = STORY_STATUSES.find(x => x.key === status) || STORY_STATUSES[0];
  return (
    <span className={`inline-flex items-center border rounded-full font-bold uppercase tracking-wider ${small ? 'text-[8px] px-1.5 py-px' : 'text-[9px] px-2 py-0.5'} ${s.className}`}>
      {s.label}
    </span>
  );
}

// Plain-text blocks of a chapter (paragraphs, headings, list items, quotes)
// for the version compare view — parsed with DOMParser and read via
// textContent only, never rendered as HTML.
function htmlToBlocks(html) {
  if (typeof window === 'undefined' || !html) return [];
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const blocks = [];
  doc.body.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li,hr').forEach(el => {
    if (el.tagName === 'P' && el.closest('li')) return;
    const text = el.tagName === 'HR' ? '───' : el.textContent.trim();
    if (text) blocks.push(text);
  });
  return blocks;
}

// Paragraph-level diff (longest common subsequence). Chapters are at most a
// few hundred paragraphs, so the O(n·m) table stays small.
function diffBlocks(oldBlocks, newBlocks) {
  const n = oldBlocks.length, m = newBlocks.length;
  if (n * m > 400000) {
    return [...oldBlocks.map(text => ({ type: 'removed', text })), ...newBlocks.map(text => ({ type: 'added', text }))];
  }
  const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = oldBlocks[i] === newBlocks[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  const out = [];
  let i = 0, j = 0;
  while (i < n && j < m) {
    if (oldBlocks[i] === newBlocks[j]) { out.push({ type: 'same', text: oldBlocks[i] }); i++; j++; }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { out.push({ type: 'removed', text: oldBlocks[i] }); i++; }
    else { out.push({ type: 'added', text: newBlocks[j] }); j++; }
  }
  while (i < n) out.push({ type: 'removed', text: oldBlocks[i++] });
  while (j < m) out.push({ type: 'added', text: newBlocks[j++] });
  return out;
}

export default function StoryRoomTab({ canPublishToWiki = false, isAdmin = false, guestMode = false }) {
  const [view, setView] = useState('chapters'); // chapters | library | timeline | guests
  const [tree, setTree] = useState([]);
  const [me, setMe] = useState('');
  const [serverAccess, setServerAccess] = useState(null);
  const [treeError, setTreeError] = useState('');
  const [loadingTree, setLoadingTree] = useState(true);
  const [notice, setNotice] = useState(null); // { type: 'error'|'success', text }

  const [entities, setEntities] = useState([]);
  const [focusEntityName, setFocusEntityName] = useState(null);

  const flash = useCallback((type, text) => {
    setNotice({ type, text });
    if (type === 'success') setTimeout(() => setNotice(n => (n && n.text === text ? null : n)), 3500);
  }, []);

  const loadTree = useCallback(async () => {
    try {
      const data = await api('/api/story');
      setTree(data.tree || []);
      setMe(data.me || '');
      if (data.access) setServerAccess(data.access);
      setTreeError('');
    } catch (err) {
      setTreeError(err.message);
    } finally {
      setLoadingTree(false);
    }
  }, []);

  const loadEntities = useCallback(async () => {
    try {
      const data = await api('/api/story/library');
      setEntities(data.entities || []);
    } catch { /* library view shows its own error */ }
  }, []);

  useEffect(() => {
    loadTree();
    loadEntities();
    const t = setInterval(loadTree, TREE_POLL_MS);
    return () => clearInterval(t);
  }, [loadTree, loadEntities]);

  const openLore = (name) => {
    setFocusEntityName(name);
    setView('library');
  };

  const access = useMemo(() => {
    const base = serverAccess || (guestMode ? GUEST_DEFAULT_ACCESS : STAFF_ACCESS);
    return { ...base, isGuest: guestMode || !!base.isGuest };
  }, [serverAccess, guestMode]);
  const effectiveIsAdmin = !access.isGuest && (isAdmin || !!access.isAdmin);
  const effectivePublish = !access.isGuest && canPublishToWiki && access.canPublish !== false;

  const VIEWS = [
    { key: 'chapters', label: 'Chapters', icon: ScrollText },
    { key: 'library', label: 'Lore Library', icon: BookOpen },
    { key: 'timeline', label: 'Timeline', icon: Clock },
    { key: 'files', label: 'Files', icon: Paperclip },
    ...(access.canManage && !access.isGuest ? [{ key: 'guests', label: 'Guest links', icon: KeyRound }] : [])
  ];

  return (
    <StoryAccessContext.Provider value={access}>
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-serif text-xl font-bold text-sp-gold flex items-center gap-2">
            <ScrollText className="w-5 h-5" /> Story Room
          </h2>
          <p className="text-[11px] text-text-warm/55 mt-0.5">
            {access.isGuest
              ? 'The iekcorbar storyline workspace. You were invited with a personal guest link — please don\'t share it.'
              : 'The staff workspace for the iekcorbar storyline. Only people with Story Room access (or a guest link) can see anything here.'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
        <span className="text-[11px] text-text-warm/60 border border-sp-bronze/25 rounded-lg px-2.5 py-1.5 bg-neutral-950/40">
          <DownloadMenu label="Download everything" title="Download the whole Story Room as a ZIP" buildUrl={(f) => `/api/story/export?all=1&format=${f}`} flash={flash} />
        </span>
        <div className="flex gap-1 bg-neutral-950/40 border border-sp-bronze/25 rounded-lg p-1">
          {VIEWS.map(v => (
            <button
              key={v.key}
              onClick={() => setView(v.key)}
              className={`px-3 py-1.5 rounded-md text-[11px] font-bold uppercase tracking-wider flex items-center gap-1.5 cursor-pointer transition-colors ${
                view === v.key ? 'bg-sp-gold text-black' : 'text-text-warm/60 hover:text-white'
              }`}
            >
              <v.icon className="w-3.5 h-3.5" /> {v.label}
            </button>
          ))}
        </div>
        </div>
      </div>

      {notice && (
        <div className={`text-xs px-3 py-2 rounded-lg border flex items-start justify-between gap-3 ${
          notice.type === 'error' ? 'bg-crimson/20 border-crimson text-rose-200' : 'bg-green-500/10 border-green-500 text-green-400'
        }`}>
          <span>{notice.text}</span>
          <button onClick={() => setNotice(null)} className="cursor-pointer opacity-70 hover:opacity-100" aria-label="Dismiss"><X className="w-3.5 h-3.5" /></button>
        </div>
      )}

      {view === 'chapters' && (
        <ChaptersView
          tree={tree}
          me={me}
          loadingTree={loadingTree}
          treeError={treeError}
          reloadTree={loadTree}
          entities={entities}
          onOpenLore={openLore}
          canPublishToWiki={effectivePublish}
          isAdmin={effectiveIsAdmin}
          flash={flash}
        />
      )}
      {view === 'library' && (
        <LibraryView
          tree={tree}
          entities={entities}
          reloadEntities={loadEntities}
          focusName={focusEntityName}
          clearFocus={() => setFocusEntityName(null)}
          flash={flash}
        />
      )}
      {view === 'timeline' && (
        <TimelineView tree={tree} flash={flash} />
      )}
      {view === 'files' && (
        <div className="space-y-2">
          <p className="text-[11px] text-text-warm/50 max-w-2xl">
            Every file in the Story Room: concept art, textures, 3D models, schematics, reference documents. Files can belong to a chapter or a lore entry, or sit here as general assets.
          </p>
          <StoryFiles access={access} tree={tree} entities={entities} flash={flash} />
        </div>
      )}
      {view === 'guests' && access.canManage && !access.isGuest && (
        <GuestLinksView flash={flash} />
      )}
    </div>
    </StoryAccessContext.Provider>
  );
}

/* ============================== CHAPTERS ============================== */

function ChaptersView({ tree, me, loadingTree, treeError, reloadTree, entities, onOpenLore, canPublishToWiki, isAdmin, flash }) {
  const access = useStoryAccess();
  const [selectedId, setSelectedId] = useState(null);
  const [expanded, setExpanded] = useState(() => new Set());
  const [filter, setFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [creating, setCreating] = useState(null); // { parentId, type }
  const [createTitle, setCreateTitle] = useState('');
  const [renamingId, setRenamingId] = useState(null);
  const [renameTitle, setRenameTitle] = useState('');
  const [movingId, setMovingId] = useState(null);

  const byParent = useMemo(() => {
    const map = new Map();
    for (const n of tree) {
      const key = n.parentId || 'root';
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(n);
    }
    for (const list of map.values()) list.sort((a, b) => (a.sortOrder - b.sortOrder) || a.title.localeCompare(b.title));
    return map;
  }, [tree]);

  const folders = tree.filter(n => n.type === 'folder');
  const q = filter.trim().toLowerCase();
  const filtering = q || statusFilter !== 'all';
  const matches = (n) => n.type === 'doc' && (!q || n.title.toLowerCase().includes(q)) && (statusFilter === 'all' || n.status === statusFilter);
  const subtreeHasMatch = (node) => matches(node) || (byParent.get(node.id) || []).some(subtreeHasMatch);

  const toggle = (id) => setExpanded(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  const startCreate = (parentId, type) => {
    setCreating({ parentId, type });
    setCreateTitle('');
    if (parentId) setExpanded(prev => new Set(prev).add(parentId));
  };

  const submitCreate = async (e) => {
    e?.preventDefault();
    if (!createTitle.trim()) return;
    try {
      const data = await post('/api/story', { action: 'create', parentId: creating.parentId, type: creating.type, title: createTitle.trim() });
      setCreating(null);
      await reloadTree();
      if (creating.type === 'doc') setSelectedId(data.id);
    } catch (err) {
      flash('error', err.message);
    }
  };

  const submitRename = async (node) => {
    const title = renameTitle.trim();
    setRenamingId(null);
    if (!title || title === node.title) return;
    try {
      await post('/api/story', { action: 'meta', id: node.id, title });
      await reloadTree();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const moveSibling = async (node, dir) => {
    const siblings = byParent.get(node.parentId || 'root') || [];
    const idx = siblings.findIndex(s => s.id === node.id);
    const swap = idx + dir;
    if (idx < 0 || swap < 0 || swap >= siblings.length) return;
    const ordered = siblings.map(s => s.id);
    [ordered[idx], ordered[swap]] = [ordered[swap], ordered[idx]];
    try {
      await post('/api/story', { action: 'reorder', orderedIds: ordered });
      await reloadTree();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const moveTo = async (node, parentId) => {
    setMovingId(null);
    try {
      await post('/api/story', { action: 'move', id: node.id, parentId: parentId || null });
      if (parentId) setExpanded(prev => new Set(prev).add(parentId));
      await reloadTree();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const remove = async (node) => {
    const childCount = (() => {
      let count = 0;
      const walk = (id) => (byParent.get(id) || []).forEach(c => { count++; walk(c.id); });
      walk(node.id);
      return count;
    })();
    const what = node.type === 'folder'
      ? `the folder "${node.title}"${childCount ? ` and everything inside it (${childCount} item${childCount === 1 ? '' : 's'})` : ''}`
      : `the chapter "${node.title}" including its version history and comments`;
    if (!window.confirm(`Permanently delete ${what}? This cannot be undone.`)) return;
    try {
      await api(`/api/story?id=${encodeURIComponent(node.id)}`, { method: 'DELETE' });
      if (selectedId === node.id) setSelectedId(null);
      await reloadTree();
      flash('success', 'Deleted.');
    } catch (err) {
      flash('error', err.message);
    }
  };

  const renderNode = (node, depth) => {
    if (filtering && !subtreeHasMatch(node)) return null;
    const isFolder = node.type === 'folder';
    const isOpen = filtering || expanded.has(node.id);
    const children = byParent.get(node.id) || [];
    const active = selectedId === node.id;
    return (
      <div key={node.id}>
        <div
          className={`group flex items-center gap-1 rounded-md pr-1 ${active ? 'bg-sp-gold/15' : 'hover:bg-neutral-800/50'}`}
          style={{ paddingLeft: `${depth * 14 + 4}px` }}
        >
          {isFolder ? (
            <button onClick={() => toggle(node.id)} className="p-0.5 text-text-warm/50 hover:text-white cursor-pointer" aria-label={isOpen ? 'Collapse' : 'Expand'}>
              {isOpen ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
            </button>
          ) : <span className="w-4.5 shrink-0" />}

          {renamingId === node.id ? (
            <input
              autoFocus
              value={renameTitle}
              onChange={e => setRenameTitle(e.target.value)}
              onBlur={() => submitRename(node)}
              onKeyDown={e => { if (e.key === 'Enter') submitRename(node); if (e.key === 'Escape') setRenamingId(null); }}
              className="flex-1 min-w-0 bg-neutral-950 border border-sp-gold text-white text-xs rounded px-1.5 py-0.5 focus:outline-hidden"
            />
          ) : (
            <button
              onClick={() => (isFolder ? toggle(node.id) : setSelectedId(node.id))}
              className={`flex-1 min-w-0 flex items-center gap-1.5 py-1 text-left text-xs cursor-pointer ${active ? 'text-sp-gold font-bold' : 'text-text-warm/85'}`}
              title={node.title}
            >
              {isFolder
                ? (isOpen ? <FolderOpen className="w-3.5 h-3.5 text-sp-copper shrink-0" /> : <Folder className="w-3.5 h-3.5 text-sp-copper shrink-0" />)
                : <FileText className="w-3.5 h-3.5 text-text-warm/50 shrink-0" />}
              <span className="truncate">{node.title}</span>
              {!isFolder && <StatusBadge status={node.status} small />}
              {node.lockUser && (
                <span className="flex items-center gap-0.5 text-[9px] text-amber-300 shrink-0" title={`${node.lockUser} is editing`}>
                  <Pencil className="w-2.5 h-2.5" />{node.lockUser === me ? 'you' : node.lockUser}
                </span>
              )}
            </button>
          )}

          <div className="hidden group-hover:flex group-focus-within:flex items-center gap-0.5 shrink-0">
            {isFolder && access.canEdit && (
              <>
                <IconBtn title="New chapter here" onClick={() => startCreate(node.id, 'doc')}><Plus className="w-3 h-3" /></IconBtn>
                <IconBtn title="New folder here" onClick={() => startCreate(node.id, 'folder')}><FolderPlus className="w-3 h-3" /></IconBtn>
              </>
            )}
            {isFolder && (
              <DownloadMenu compact title="Download this folder (ZIP)" buildUrl={(f) => `/api/story/export?id=${encodeURIComponent(node.id)}&format=${f}`} flash={flash} />
            )}
            {access.canEdit && (
              <>
                <IconBtn title="Rename" onClick={() => { setRenamingId(node.id); setRenameTitle(node.title); }}><Pencil className="w-3 h-3" /></IconBtn>
                <IconBtn title="Move up" onClick={() => moveSibling(node, -1)}><ArrowUp className="w-3 h-3" /></IconBtn>
                <IconBtn title="Move down" onClick={() => moveSibling(node, 1)}><ArrowDown className="w-3 h-3" /></IconBtn>
                <IconBtn title="Move to folder..." onClick={() => setMovingId(movingId === node.id ? null : node.id)}><MoveRight className="w-3 h-3" /></IconBtn>
              </>
            )}
            {access.canManage && (
              <IconBtn title="Delete" onClick={() => remove(node)} danger><Trash2 className="w-3 h-3" /></IconBtn>
            )}
          </div>
        </div>

        {movingId === node.id && (
          <div className="flex items-center gap-1.5 my-1" style={{ paddingLeft: `${depth * 14 + 24}px` }}>
            <span className="text-[10px] text-text-warm/50">Move to</span>
            <select
              autoFocus
              defaultValue=""
              onChange={e => moveTo(node, e.target.value === '__root' ? null : e.target.value)}
              className="bg-neutral-950 border border-sp-bronze/40 text-white text-[11px] rounded px-1.5 py-0.5"
            >
              <option value="" disabled>Choose a folder…</option>
              <option value="__root">(Top level)</option>
              {folders.filter(f => f.id !== node.id).map(f => <option key={f.id} value={f.id}>{f.title}</option>)}
            </select>
            <IconBtn title="Cancel" onClick={() => setMovingId(null)}><X className="w-3 h-3" /></IconBtn>
          </div>
        )}

        {isFolder && isOpen && (
          <div>
            {creating && creating.parentId === node.id && (
              <CreateRow depth={depth + 1} creating={creating} title={createTitle} setTitle={setCreateTitle} onSubmit={submitCreate} onCancel={() => setCreating(null)} />
            )}
            {children.map(c => renderNode(c, depth + 1))}
            {children.length === 0 && !creating && (
              <p className="text-[10px] text-text-warm/30 italic py-1" style={{ paddingLeft: `${(depth + 1) * 14 + 22}px` }}>Empty folder</p>
            )}
          </div>
        )}
      </div>
    );
  };

  const roots = byParent.get('root') || [];
  const selectedNode = tree.find(n => n.id === selectedId) || null;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[280px_minmax(0,1fr)] gap-4">
      {/* Tree */}
      <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-3 space-y-2 lg:max-h-[calc(100vh-220px)] lg:overflow-y-auto">
        <div className="flex items-center gap-1.5">
          {access.canEdit ? (
            <>
              <button onClick={() => startCreate(null, 'doc')} className="flex-1 flex items-center justify-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md py-1.5 hover:bg-white cursor-pointer">
                <Plus className="w-3 h-3" /> Chapter
              </button>
              <button onClick={() => startCreate(null, 'folder')} className="flex-1 flex items-center justify-center gap-1 text-[10px] font-bold uppercase tracking-wider border border-sp-bronze/40 text-text-warm/80 rounded-md py-1.5 hover:text-white hover:border-sp-gold cursor-pointer">
                <FolderPlus className="w-3 h-3" /> Folder
              </button>
            </>
          ) : (
            <span className="flex-1 text-[10px] text-text-warm/45 italic">{access.canComment ? 'You can read and comment.' : 'Read-only access.'}</span>
          )}
          <IconBtn title="Refresh" onClick={reloadTree}><RefreshCw className="w-3 h-3" /></IconBtn>
        </div>
        <div className="relative">
          <Search className="w-3 h-3 text-text-warm/40 absolute left-2 top-2" />
          <input
            value={filter}
            onChange={e => setFilter(e.target.value)}
            placeholder="Find a chapter..."
            className="w-full bg-neutral-950/40 border border-sp-bronze/25 text-white text-[11px] rounded pl-6 pr-2 py-1 focus:outline-hidden focus:border-sp-gold"
          />
        </div>
        <div className="flex flex-wrap gap-1">
          {[{ key: 'all', label: 'All' }, ...STORY_STATUSES].map(s => (
            <button
              key={s.key}
              onClick={() => setStatusFilter(s.key)}
              className={`text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full border cursor-pointer ${
                statusFilter === s.key ? 'border-sp-gold text-sp-gold bg-sp-gold/10' : 'border-sp-bronze/25 text-text-warm/50 hover:text-white'
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>

        <div className="pt-1">
          {loadingTree && <p className="text-[11px] text-text-warm/40 italic">Loading…</p>}
          {treeError && <p className="text-[11px] text-rose-300">{treeError}</p>}
          {creating && !creating.parentId && (
            <CreateRow depth={0} creating={creating} title={createTitle} setTitle={setCreateTitle} onSubmit={submitCreate} onCancel={() => setCreating(null)} />
          )}
          {roots.map(n => renderNode(n, 0))}
          {!loadingTree && !treeError && tree.length === 0 && !creating && (
            <p className="text-[11px] text-text-warm/40 italic py-2">
              {access.canEdit ? 'Nothing here yet. Start with a folder (e.g. "Season 2") and add chapters inside it.' : 'Nothing here yet.'}
            </p>
          )}
          {filtering && tree.length > 0 && !roots.some(subtreeHasMatch) && (
            <p className="text-[11px] text-text-warm/40 italic py-2">No chapter matches.</p>
          )}
        </div>
      </div>

      {/* Editor area */}
      <div className="min-w-0">
        {selectedNode && selectedNode.type === 'doc' ? (
          <ChapterPane
            key={selectedNode.id}
            nodeId={selectedNode.id}
            treeNode={selectedNode}
            tree={tree}
            me={me}
            reloadTree={reloadTree}
            entities={entities}
            onOpenLore={onOpenLore}
            canPublishToWiki={canPublishToWiki}
            isAdmin={isAdmin}
            flash={flash}
          />
        ) : (
          <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-10 text-center space-y-2">
            <ScrollText className="w-8 h-8 text-sp-copper mx-auto" />
            <p className="text-sm text-text-warm/70">{access.canEdit ? 'Pick a chapter on the left, or create a new one.' : 'Pick a chapter on the left to read it.'}</p>
            {access.canEdit ? (
              <p className="text-[11px] text-text-warm/40 max-w-md mx-auto">
                Anyone can read any chapter. Click <b>Edit</b> to start writing — the chapter is then reserved for you,
                so nobody overwrites your work. Everything saves automatically and every writing session is kept in the history.
              </p>
            ) : (
              <p className="text-[11px] text-text-warm/40 max-w-md mx-auto">
                Use <b>Download</b> to save a chapter, a folder or everything as Word, Markdown, text or a web page.
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function IconBtn({ title, onClick, children, danger }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      aria-label={title}
      className={`p-1 rounded cursor-pointer transition-colors ${danger ? 'text-text-warm/40 hover:text-rose-300 hover:bg-crimson/20' : 'text-text-warm/40 hover:text-white hover:bg-neutral-700/60'}`}
    >
      {children}
    </button>
  );
}

function CreateRow({ depth, creating, title, setTitle, onSubmit, onCancel }) {
  return (
    <form onSubmit={onSubmit} className="flex items-center gap-1 my-1" style={{ paddingLeft: `${depth * 14 + 22}px` }}>
      {creating.type === 'folder' ? <Folder className="w-3.5 h-3.5 text-sp-copper shrink-0" /> : <FileText className="w-3.5 h-3.5 text-text-warm/50 shrink-0" />}
      <input
        autoFocus
        value={title}
        onChange={e => setTitle(e.target.value)}
        onKeyDown={e => { if (e.key === 'Escape') onCancel(); }}
        placeholder={creating.type === 'folder' ? 'Folder name' : 'Chapter title'}
        className="flex-1 min-w-0 bg-neutral-950 border border-sp-gold text-white text-xs rounded px-1.5 py-0.5 focus:outline-hidden"
      />
      <IconBtn title="Create" onClick={onSubmit}><Check className="w-3 h-3" /></IconBtn>
      <IconBtn title="Cancel" onClick={onCancel}><X className="w-3 h-3" /></IconBtn>
    </form>
  );
}

/* ----------------------------- Chapter pane ----------------------------- */

function ChapterPane({ nodeId, treeNode, tree, me, reloadTree, entities, onOpenLore, canPublishToWiki, isAdmin, flash }) {
  const access = useStoryAccess();
  const [node, setNode] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [editorKey, setEditorKey] = useState(0);
  const [saveState, setSaveState] = useState('idle'); // idle | dirty | saving | saved | error
  const [savedAt, setSavedAt] = useState(null);
  const [panel, setPanel] = useState(null); // comments | history | null
  const [selectionText, setSelectionText] = useState('');
  const [viewingRevision, setViewingRevision] = useState(null); // { revision, compare }
  const [publishing, setPublishing] = useState(false);

  const editingRef = useRef(false);
  const dirtyRef = useRef(false);
  const latest = useRef({ title: '', content: '' });
  const saveTimer = useRef(null);
  const savingRef = useRef(null);

  const loadNode = useCallback(async () => {
    try {
      const data = await api(`/api/story?id=${encodeURIComponent(nodeId)}`);
      setNode(data.node);
      setLoadError('');
      return data.node;
    } catch (err) {
      setLoadError(err.message);
      return null;
    }
  }, [nodeId]);

  // Initial load.
  useEffect(() => {
    loadNode().then(n => {
      if (!n) return;
      setTitle(n.title);
      setContent(n.content || '');
      latest.current = { title: n.title, content: n.content || '' };
      setEditorKey(k => k + 1);
    });
  }, [loadNode]);

  // While someone else holds the lock, keep the read-only copy fresh.
  useEffect(() => {
    if (editing) return;
    const t = setInterval(async () => {
      const n = await loadNode();
      if (n && !editingRef.current && (n.content || '') !== latest.current.content) {
        setTitle(n.title);
        setContent(n.content || '');
        latest.current = { title: n.title, content: n.content || '' };
        setEditorKey(k => k + 1);
      }
    }, TREE_POLL_MS);
    return () => clearInterval(t);
  }, [editing, loadNode]);

  const doSave = useCallback(async () => {
    if (!editingRef.current || !dirtyRef.current) return true;
    if (savingRef.current) { await savingRef.current; if (!dirtyRef.current) return true; }
    const snapshot = { ...latest.current };
    if (!snapshot.title.trim()) { setSaveState('error'); return false; }
    dirtyRef.current = false;
    setSaveState('saving');
    const p = (async () => {
      try {
        const data = await post('/api/story', { action: 'save', id: nodeId, title: snapshot.title.trim(), content: snapshot.content });
        setSavedAt(data.savedAt);
        setSaveState(dirtyRef.current ? 'dirty' : 'saved');
        return true;
      } catch (err) {
        dirtyRef.current = true;
        setSaveState('error');
        if (err.status === 409) {
          editingRef.current = false;
          setEditing(false);
          flash('error', `${err.message}${err.data?.lockUser ? ` ${err.data.lockUser} is editing it now.` : ''} Copy anything you still need before leaving this chapter.`);
        } else {
          flash('error', err.message);
        }
        return false;
      } finally {
        savingRef.current = null;
      }
    })();
    savingRef.current = p;
    return p;
  }, [nodeId, flash]);

  const scheduleSave = () => {
    dirtyRef.current = true;
    setSaveState('dirty');
    clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(doSave, AUTOSAVE_MS);
  };

  const onContentChange = (html) => {
    latest.current.content = html;
    scheduleSave();
  };
  const onTitleChange = (value) => {
    setTitle(value);
    latest.current.title = value;
    scheduleSave();
  };

  // Heartbeat: renew the lock while editing.
  useEffect(() => {
    if (!editing) return;
    const t = setInterval(async () => {
      try {
        const data = await post('/api/story', { action: 'lock', id: nodeId });
        if (!data.success) {
          editingRef.current = false;
          setEditing(false);
          flash('error', `Your edit lock was taken over${data.lockUser ? ` by ${data.lockUser}` : ''}. Unsaved changes since the last save were not stored.`);
          reloadTree();
        }
      } catch { /* transient network error — next beat retries */ }
    }, HEARTBEAT_MS);
    return () => clearInterval(t);
  }, [editing, nodeId, flash, reloadTree]);

  // Leaving the chapter / closing the tab: save and free the lock.
  useEffect(() => {
    const onBeforeUnload = (e) => {
      if (!editingRef.current) return;
      try {
        navigator.sendBeacon('/api/story', new Blob([JSON.stringify({ action: 'unlock', id: nodeId })], { type: 'application/json' }));
      } catch { /* best effort — the lock expires by itself anyway */ }
      if (dirtyRef.current) { e.preventDefault(); e.returnValue = ''; }
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload);
      clearTimeout(saveTimer.current);
      if (editingRef.current) {
        const wasDirty = dirtyRef.current;
        (wasDirty ? doSave() : Promise.resolve()).finally(() => {
          post('/api/story', { action: 'unlock', id: nodeId }).catch(() => {});
          reloadTree();
        });
        editingRef.current = false;
      }
    };
  }, [nodeId, doSave, reloadTree]);

  const startEditing = async (force = false) => {
    try {
      const data = await post('/api/story', { action: 'lock', id: nodeId, force });
      if (!data.success) {
        flash('error', `${data.lockUser || 'Someone else'} is editing this chapter right now.`);
        reloadTree();
        return;
      }
      const fresh = await loadNode();
      if (fresh) {
        setTitle(fresh.title);
        setContent(fresh.content || '');
        latest.current = { title: fresh.title, content: fresh.content || '' };
        setEditorKey(k => k + 1);
      }
      dirtyRef.current = false;
      editingRef.current = true;
      setEditing(true);
      setSaveState('idle');
      setViewingRevision(null);
      reloadTree();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const stopEditing = async () => {
    clearTimeout(saveTimer.current);
    const ok = await doSave();
    if (!ok && dirtyRef.current) {
      if (!window.confirm('The last changes could not be saved. Stop editing anyway? (Unsaved text will be lost.)')) return;
    }
    editingRef.current = false;
    setEditing(false);
    try { await post('/api/story', { action: 'unlock', id: nodeId }); } catch { /* expires by itself */ }
    await loadNode();
    reloadTree();
  };

  const changeStatus = async (status) => {
    try {
      await post('/api/story', { action: 'meta', id: nodeId, status });
      setNode(n => ({ ...n, status }));
      reloadTree();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const publish = async () => {
    const category = window.prompt(
      node?.publishedWikiId
        ? 'Update the published wiki page. Wiki chapter (category):'
        : 'Publish a copy of this chapter to the public wiki. Wiki chapter (category):',
      'Lore'
    );
    if (category === null) return;
    if (editingRef.current && dirtyRef.current) await doSave();
    setPublishing(true);
    try {
      const data = await post('/api/story', { action: 'publish', id: nodeId, category });
      flash('success', `Published to the wiki: /wiki?page=${data.slug}`);
      await loadNode();
      reloadTree();
    } catch (err) {
      flash('error', err.message);
    } finally {
      setPublishing(false);
    }
  };

  const restoreRevision = async (revision) => {
    if (!editingRef.current) {
      flash('error', 'Click Edit first — restoring an older version needs the edit lock.');
      return;
    }
    if (!window.confirm(`Replace the current text with the version from ${fmtDateTime(revision.savedAt)} by ${revision.savedBy}? The current text stays in the history.`)) return;
    clearTimeout(saveTimer.current);
    await doSave();
    try {
      const data = await post('/api/story/revisions', { action: 'restore', id: revision.id });
      setTitle(data.title);
      setContent(data.content);
      latest.current = { title: data.title, content: data.content };
      dirtyRef.current = false;
      setEditorKey(k => k + 1);
      setViewingRevision(null);
      setSaveState('saved');
      setSavedAt(new Date().toISOString());
      flash('success', 'Version restored.');
    } catch (err) {
      flash('error', err.message);
    }
  };

  if (loadError && !node) {
    return <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">{loadError}</div>;
  }
  if (!node) {
    return <div className="text-xs text-text-warm/40 italic p-6">Loading chapter…</div>;
  }

  const lockUser = editing ? me : (treeNode.lockUser || node.lockUser);
  const lockedByOther = !editing && lockUser && lockUser !== me;
  const loreNames = entities.map(e => e.name);
  const linkedLore = extractLoreLinks(editing ? latest.current.content : content);

  const saveLabel = {
    idle: '',
    dirty: 'Unsaved changes…',
    saving: 'Saving…',
    saved: savedAt ? `Saved ${fmtTime(savedAt)}` : 'Saved',
    error: 'Not saved!'
  }[saveState];

  return (
    <div className={`grid grid-cols-1 ${panel ? 'xl:grid-cols-[minmax(0,1fr)_300px]' : ''} gap-4`}>
      <div className="space-y-3 min-w-0">
        {/* Header */}
        <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-3 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            {editing ? (
              <input
                value={title}
                onChange={e => onTitleChange(e.target.value)}
                className="flex-1 min-w-[200px] bg-neutral-950/60 border border-sp-bronze/30 text-white font-serif text-lg font-bold rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold"
              />
            ) : (
              <h3 className="flex-1 min-w-[200px] font-serif text-lg font-bold text-white">{title}</h3>
            )}
            <select
              value={node.status}
              onChange={e => changeStatus(e.target.value)}
              disabled={!access.canEdit}
              className="bg-neutral-950 border border-sp-bronze/40 text-white text-[11px] rounded px-2 py-1 cursor-pointer disabled:cursor-default disabled:opacity-60"
              title="Status"
            >
              {STORY_STATUSES.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
            </select>
            {editing ? (
              <button onClick={stopEditing} className="flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-3 py-1.5 hover:bg-white cursor-pointer">
                <Unlock className="w-3.5 h-3.5" /> Done editing
              </button>
            ) : lockedByOther ? (
              isAdmin && (
                <button
                  onClick={() => { if (window.confirm(`Take over the edit lock from ${lockUser}? Only do this if they left it open by accident — their unsaved typing would be lost.`)) startEditing(true); }}
                  className="flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider border border-amber-500/60 text-amber-300 rounded-md px-3 py-1.5 hover:bg-amber-500/10 cursor-pointer"
                >
                  <Lock className="w-3.5 h-3.5" /> Take over
                </button>
              )
            ) : access.canEdit ? (
              <button onClick={() => startEditing(false)} className="flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-3 py-1.5 hover:bg-white cursor-pointer">
                <Pencil className="w-3.5 h-3.5" /> Edit
              </button>
            ) : (
              <span className="flex items-center gap-1 text-[10px] uppercase tracking-wider text-text-warm/45 border border-sp-bronze/25 rounded-md px-2.5 py-1.5">
                <Eye className="w-3.5 h-3.5" /> Read only
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] text-text-warm/50">
            <StatusBadge status={node.status} />
            <span>Last edited by <b className="text-text-warm/75">{node.updatedBy || '—'}</b> · {fmtDateTime(node.updatedAt)}</span>
            {node.publishedWikiId && <span className="text-emerald-300/80 flex items-center gap-1"><Upload className="w-3 h-3" /> On the wiki</span>}
            {saveLabel && (
              <span className={saveState === 'error' ? 'text-rose-300 font-bold' : saveState === 'saved' ? 'text-emerald-300' : 'text-amber-300'}>{saveLabel}</span>
            )}
            <span className="flex-1" />
            <button onClick={() => setPanel(panel === 'comments' ? null : 'comments')} className={`flex items-center gap-1 cursor-pointer hover:text-white ${panel === 'comments' ? 'text-sp-gold' : ''}`}>
              <MessageSquare className="w-3.5 h-3.5" /> Comments
            </button>
            <button onClick={() => setPanel(panel === 'history' ? null : 'history')} className={`flex items-center gap-1 cursor-pointer hover:text-white ${panel === 'history' ? 'text-sp-gold' : ''}`}>
              <History className="w-3.5 h-3.5" /> History
            </button>
            <button onClick={() => setPanel(panel === 'files' ? null : 'files')} className={`flex items-center gap-1 cursor-pointer hover:text-white ${panel === 'files' ? 'text-sp-gold' : ''}`}>
              <Paperclip className="w-3.5 h-3.5" /> Files
            </button>
            <DownloadMenu
              title="Download this chapter"
              buildUrl={(f) => `/api/story/export?id=${encodeURIComponent(nodeId)}&format=${f}`}
              before={async () => { if (editingRef.current && dirtyRef.current) { clearTimeout(saveTimer.current); await doSave(); } }}
              flash={flash}
            />
            {canPublishToWiki && (
              <button onClick={publish} disabled={publishing} className="flex items-center gap-1 cursor-pointer hover:text-white disabled:opacity-40">
                <Upload className="w-3.5 h-3.5" /> {node.publishedWikiId ? 'Update wiki' : 'Publish to wiki'}
              </button>
            )}
          </div>

          {lockedByOther && (
            <div className="flex items-center gap-2 text-[11px] bg-amber-500/10 border border-amber-500/40 text-amber-200 rounded-md px-2.5 py-1.5">
              <Lock className="w-3.5 h-3.5 shrink-0" />
              <span><b>{lockUser}</b> is editing this chapter. You're looking at a read-only copy that refreshes by itself.{access.canComment ? ' You can still leave comments.' : ''}</span>
            </div>
          )}

          {linkedLore.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5 pt-1">
              <span className="text-[9px] uppercase tracking-widest text-text-warm/35 flex items-center gap-1"><Link2 className="w-3 h-3" /> Lore in this chapter</span>
              {linkedLore.map(name => {
                const exists = loreNames.some(n => n.toLowerCase() === name.toLowerCase());
                return (
                  <button
                    key={name}
                    onClick={() => onOpenLore(name)}
                    title={exists ? 'Open lore entry' : 'No lore entry yet — click to create it'}
                    className={`text-[10px] px-2 py-0.5 rounded-full border cursor-pointer ${exists ? 'border-sp-copper/50 text-sp-copper hover:bg-sp-copper/10' : 'border-dashed border-text-warm/30 text-text-warm/50 hover:text-white'}`}
                  >
                    {name}{!exists && ' +'}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Body */}
        {viewingRevision ? (
          <RevisionViewer
            revision={viewingRevision.revision}
            compare={viewingRevision.compare}
            currentContent={editing ? latest.current.content : content}
            onToggleCompare={() => setViewingRevision(v => ({ ...v, compare: !v.compare }))}
            onRestore={() => restoreRevision(viewingRevision.revision)}
            canRestore={editing}
            onClose={() => setViewingRevision(null)}
          />
        ) : editing ? (
          <StoryEditor
            key={`edit-${editorKey}`}
            value={content}
            onChange={onContentChange}
            loreNames={loreNames}
            onSelectionText={setSelectionText}
          />
        ) : (
          <div className="border border-sp-bronze/20 rounded-lg bg-neutral-950/40">
            {(content || '').replace(/<[^>]+>/g, '').trim() ? (
              <StoryEditor key={`read-${editorKey}`} value={content} editable={false} onSelectionText={setSelectionText} minHeight="min-h-[200px]" />
            ) : (
              <p className="text-xs text-text-warm/40 italic p-6">This chapter is still empty.{access.canEdit ? <> Click <b>Edit</b> to start writing.</> : ''}</p>
            )}
          </div>
        )}
      </div>

      {panel === 'comments' && (
        <CommentsPanel nodeId={nodeId} me={me} isAdmin={isAdmin} selectionText={selectionText} flash={flash} onClose={() => setPanel(null)} />
      )}
      {panel === 'files' && (
        <aside className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-3 space-y-2 xl:max-h-[calc(100vh-220px)] xl:overflow-y-auto">
          <div className="flex items-center justify-between">
            <h4 className="text-[11px] font-bold uppercase tracking-wider text-sp-gold flex items-center gap-1.5"><Paperclip className="w-3.5 h-3.5" /> Files for this chapter</h4>
            <button onClick={() => setPanel(null)} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Close files"><X className="w-3.5 h-3.5" /></button>
          </div>
          <StoryFiles scope={{ nodeId }} access={access} tree={tree} entities={entities} flash={flash} compact />
        </aside>
      )}
      {panel === 'history' && (
        <HistoryPanel
          nodeId={nodeId}
          refreshKey={savedAt}
          activeId={viewingRevision?.revision?.id}
          onOpen={async (rev) => {
            try {
              const data = await api(`/api/story/revisions?id=${rev.id}`);
              setViewingRevision({ revision: data.revision, compare: false });
            } catch (err) {
              flash('error', err.message);
            }
          }}
          onClose={() => setPanel(null)}
        />
      )}
    </div>
  );
}

function RevisionViewer({ revision, compare, currentContent, onToggleCompare, onRestore, canRestore, onClose }) {
  const diff = useMemo(
    () => (compare ? diffBlocks(htmlToBlocks(revision.content || ''), htmlToBlocks(currentContent || '')) : null),
    [compare, revision, currentContent]
  );
  const changes = diff ? diff.filter(d => d.type !== 'same').length : 0;

  return (
    <div className="border border-sky-500/40 rounded-lg bg-neutral-950/40">
      <div className="flex flex-wrap items-center gap-2 border-b border-sky-500/30 px-3 py-2 text-[11px]">
        <Eye className="w-3.5 h-3.5 text-sky-300" />
        <span className="text-sky-200">
          Version from <b>{fmtDateTime(revision.savedAt)}</b> by <b>{revision.savedBy}</b>
          {revision.title && <> — “{revision.title}”</>}
        </span>
        <span className="flex-1" />
        <button onClick={onToggleCompare} className="flex items-center gap-1 text-text-warm/70 hover:text-white cursor-pointer">
          <GitCompare className="w-3.5 h-3.5" /> {compare ? 'Show this version' : 'Compare with current'}
        </button>
        <button
          onClick={onRestore}
          disabled={!canRestore}
          title={canRestore ? 'Restore this version' : 'Click Edit first to restore a version'}
          className="flex items-center gap-1 text-amber-300 hover:text-amber-200 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <RotateCcw className="w-3.5 h-3.5" /> Restore
        </button>
        <button onClick={onClose} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Close version"><X className="w-3.5 h-3.5" /></button>
      </div>
      {compare ? (
        <div className="p-4 space-y-1.5 font-serif text-sm">
          <p className="text-[10px] font-sans text-text-warm/45 mb-2">
            {changes === 0 ? 'No differences — this version matches the current text.' : (
              <><span className="text-emerald-300">Green</span> = in the current text only · <span className="text-rose-300">red</span> = only in this older version</>
            )}
          </p>
          {diff.map((d, i) => (
            <p
              key={i}
              className={
                d.type === 'added' ? 'bg-emerald-500/10 border-l-2 border-emerald-500 pl-2 text-emerald-100'
                : d.type === 'removed' ? 'bg-crimson/15 border-l-2 border-crimson pl-2 text-rose-200 line-through decoration-rose-400/50'
                : 'pl-2.5 text-text-warm/60'
              }
            >
              {d.text}
            </p>
          ))}
        </div>
      ) : (
        <StoryEditor key={`rev-${revision.id}`} value={revision.content || ''} editable={false} minHeight="min-h-[200px]" />
      )}
    </div>
  );
}

function HistoryPanel({ nodeId, refreshKey, activeId, onOpen, onClose }) {
  const [revisions, setRevisions] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    api(`/api/story/revisions?nodeId=${encodeURIComponent(nodeId)}`)
      .then(d => { if (!cancelled) { setRevisions(d.revisions || []); setError(''); } })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [nodeId, refreshKey]);

  return (
    <aside className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-3 space-y-2 xl:max-h-[calc(100vh-220px)] xl:overflow-y-auto">
      <div className="flex items-center justify-between">
        <h4 className="text-[11px] font-bold uppercase tracking-wider text-sp-gold flex items-center gap-1.5"><History className="w-3.5 h-3.5" /> Version history</h4>
        <button onClick={onClose} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Close history"><X className="w-3.5 h-3.5" /></button>
      </div>
      <p className="text-[10px] text-text-warm/40">One entry per writing session. Open one to read it, compare it, or restore it.</p>
      {error && <p className="text-[11px] text-rose-300">{error}</p>}
      {revisions === null && !error && <p className="text-[11px] text-text-warm/40 italic">Loading…</p>}
      {revisions && revisions.length === 0 && <p className="text-[11px] text-text-warm/40 italic">No saved versions yet.</p>}
      <div className="space-y-1">
        {(revisions || []).map((r, i) => (
          <button
            key={r.id}
            onClick={() => onOpen(r)}
            className={`w-full text-left rounded-md px-2 py-1.5 border cursor-pointer ${activeId === r.id ? 'border-sky-500/60 bg-sky-500/10' : 'border-transparent hover:border-sp-bronze/30 hover:bg-neutral-800/40'}`}
          >
            <p className="text-[11px] text-white">{fmtDateTime(r.savedAt)} {i === 0 && <span className="text-[9px] text-emerald-300 ml-1">latest</span>}</p>
            <p className="text-[10px] text-text-warm/50">{r.savedBy} · {Number(r.size || 0).toLocaleString('en-GB')} chars</p>
          </button>
        ))}
      </div>
    </aside>
  );
}

function CommentsPanel({ nodeId, me, isAdmin, selectionText, flash, onClose }) {
  const access = useStoryAccess();
  const [comments, setComments] = useState(null);
  const [error, setError] = useState('');
  const [text, setText] = useState('');
  const [quote, setQuote] = useState('');
  const [showResolved, setShowResolved] = useState(false);
  const [sending, setSending] = useState(false);

  const load = useCallback(async () => {
    try {
      const d = await api(`/api/story/comments?nodeId=${encodeURIComponent(nodeId)}`);
      setComments(d.comments || []);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  }, [nodeId]);

  useEffect(() => {
    load();
    const t = setInterval(load, TREE_POLL_MS);
    return () => clearInterval(t);
  }, [load]);

  const add = async (e) => {
    e.preventDefault();
    if (!text.trim()) return;
    setSending(true);
    try {
      await post('/api/story/comments', { action: 'add', nodeId, body: text.trim(), quote: quote || null });
      setText('');
      setQuote('');
      await load();
    } catch (err) {
      flash('error', err.message);
    } finally {
      setSending(false);
    }
  };

  const resolve = async (c, resolved) => {
    try {
      await post('/api/story/comments', { action: 'resolve', id: c.id, resolved });
      await load();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const remove = async (c) => {
    if (!window.confirm('Delete this comment?')) return;
    try {
      await api(`/api/story/comments?id=${c.id}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const open = (comments || []).filter(c => !c.resolved);
  const resolved = (comments || []).filter(c => c.resolved);
  const shown = showResolved ? [...open, ...resolved] : open;

  return (
    <aside className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-3 space-y-2 xl:max-h-[calc(100vh-220px)] xl:overflow-y-auto">
      <div className="flex items-center justify-between">
        <h4 className="text-[11px] font-bold uppercase tracking-wider text-sp-gold flex items-center gap-1.5">
          <MessageSquare className="w-3.5 h-3.5" /> Comments {open.length > 0 && <span className="text-text-warm/50">({open.length})</span>}
        </h4>
        <button onClick={onClose} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Close comments"><X className="w-3.5 h-3.5" /></button>
      </div>

      {access.canComment ? (
      <form onSubmit={add} className="space-y-1.5">
        {quote ? (
          <div className="flex items-start gap-1 text-[10px] italic text-text-warm/60 border-l-2 border-sp-copper pl-2">
            <span className="flex-1 line-clamp-3">“{quote}”</span>
            <button type="button" onClick={() => setQuote('')} className="cursor-pointer hover:text-white" aria-label="Remove quote"><X className="w-3 h-3" /></button>
          </div>
        ) : selectionText ? (
          <button type="button" onClick={() => setQuote(selectionText.slice(0, 500))} className="text-[10px] text-sp-copper hover:text-sp-gold cursor-pointer">
            + Quote the selected text
          </button>
        ) : (
          <p className="text-[10px] text-text-warm/35">Tip: select text in the chapter to quote it in your comment.</p>
        )}
        <textarea
          value={text}
          onChange={e => setText(e.target.value)}
          rows={3}
          placeholder="Write a comment…"
          className="w-full bg-neutral-950/60 border border-sp-bronze/25 text-white text-xs rounded px-2 py-1.5 focus:outline-hidden focus:border-sp-gold resize-y"
        />
        <button disabled={sending || !text.trim()} className="w-full text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md py-1.5 hover:bg-white cursor-pointer disabled:opacity-40">
          Add comment
        </button>
      </form>
      ) : (
        <p className="text-[10px] text-text-warm/40 italic">Your access is read-only, so you can't add comments.</p>
      )}

      {error && <p className="text-[11px] text-rose-300">{error}</p>}
      {comments === null && !error && <p className="text-[11px] text-text-warm/40 italic">Loading…</p>}
      {comments && shown.length === 0 && <p className="text-[11px] text-text-warm/40 italic">No open comments.</p>}

      <div className="space-y-2">
        {shown.map(c => (
          <div key={c.id} className={`rounded-lg border p-2 space-y-1 ${c.resolved ? 'border-sp-bronze/15 opacity-60' : 'border-sp-bronze/30 bg-neutral-950/30'}`}>
            {c.quote && <p className="text-[10px] italic text-text-warm/55 border-l-2 border-sp-copper pl-2 line-clamp-3">“{c.quote}”</p>}
            <p className="text-xs text-white whitespace-pre-wrap break-words">{c.body}</p>
            <div className="flex items-center gap-2 text-[9px] text-text-warm/45">
              <span><b className="text-text-warm/70">{c.author}</b> · {fmtDateTime(c.createdAt)}</span>
              <span className="flex-1" />
              {access.canComment && (
                <button onClick={() => resolve(c, !c.resolved)} className="flex items-center gap-0.5 hover:text-emerald-300 cursor-pointer">
                  <Check className="w-3 h-3" /> {c.resolved ? 'Reopen' : 'Resolve'}
                </button>
              )}
              {access.canComment && (c.author === me || isAdmin) && (
                <button onClick={() => remove(c)} className="hover:text-rose-300 cursor-pointer" aria-label="Delete comment"><Trash2 className="w-3 h-3" /></button>
              )}
            </div>
          </div>
        ))}
      </div>

      {resolved.length > 0 && (
        <button onClick={() => setShowResolved(s => !s)} className="text-[10px] text-text-warm/45 hover:text-white cursor-pointer">
          {showResolved ? 'Hide' : 'Show'} resolved ({resolved.length})
        </button>
      )}
    </aside>
  );
}

/* ============================== LIBRARY ============================== */

const EMPTY_ENTITY = { id: null, kind: 'character', name: '', summary: '', content: '', imageUrl: '' };

function LibraryView({ tree, entities, reloadEntities, focusName, clearFocus, flash }) {
  const access = useStoryAccess();
  const [kindFilter, setKindFilter] = useState('all');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null); // entity being viewed/edited (copy)
  const [editing, setEditing] = useState(false);
  const [editorKey, setEditorKey] = useState(0);
  const [saving, setSaving] = useState(false);
  const [mentions, setMentions] = useState(null);

  // Arriving from a [[Name]] chip in a chapter: open it, or start a new entry.
  useEffect(() => {
    if (!focusName) return;
    const found = entities.find(e => e.name.toLowerCase() === focusName.toLowerCase());
    if (found) { openEntity(found); }
    else if (access.canEdit) { setSelected({ ...EMPTY_ENTITY, name: focusName }); setEditing(true); setEditorKey(k => k + 1); setMentions(null); }
    else { setSelected(null); setSearch(focusName); }
    clearFocus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusName]);

  useEffect(() => {
    if (!selected?.id || !selected.name) { setMentions(null); return; }
    let cancelled = false;
    api(`/api/story/library?mentions=${encodeURIComponent(selected.name)}`)
      .then(d => { if (!cancelled) setMentions(d.docs || []); })
      .catch(() => { if (!cancelled) setMentions([]); });
    return () => { cancelled = true; };
  }, [selected?.id, selected?.name]);

  function openEntity(entity) {
    setSelected({ ...EMPTY_ENTITY, ...entity, summary: entity.summary || '', content: entity.content || '', imageUrl: entity.imageUrl || '' });
    setEditing(false);
    setEditorKey(k => k + 1);
  }

  const save = async () => {
    if (!selected.name.trim()) { flash('error', 'A name is required.'); return; }
    setSaving(true);
    try {
      const data = await post('/api/story/library', selected);
      await reloadEntities();
      setSelected(s => ({ ...s, id: data.id }));
      setEditing(false);
      setEditorKey(k => k + 1);
      flash('success', 'Lore entry saved.');
    } catch (err) {
      flash('error', err.message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!selected?.id) { setSelected(null); return; }
    if (!window.confirm(`Delete the lore entry "${selected.name}"? Links to it in chapters stay as plain text.`)) return;
    try {
      await api(`/api/story/library?id=${encodeURIComponent(selected.id)}`, { method: 'DELETE' });
      setSelected(null);
      await reloadEntities();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const q = search.trim().toLowerCase();
  const list = entities.filter(e =>
    (kindFilter === 'all' || e.kind === kindFilter) &&
    (!q || e.name.toLowerCase().includes(q) || (e.summary || '').toLowerCase().includes(q))
  );

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)] gap-4">
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-1.5">
          {[{ key: 'all', label: 'All' }, ...STORY_ENTITY_KINDS].map(k => (
            <button
              key={k.key}
              onClick={() => setKindFilter(k.key)}
              className={`text-[10px] uppercase font-bold tracking-wider px-2.5 py-1 rounded-full border cursor-pointer ${
                kindFilter === k.key ? 'border-sp-gold text-sp-gold bg-sp-gold/10' : 'border-sp-bronze/25 text-text-warm/50 hover:text-white'
              }`}
            >
              {k.label}
            </button>
          ))}
          <span className="flex-1" />
          {access.canEdit && (
            <button
              onClick={() => { setSelected({ ...EMPTY_ENTITY, kind: kindFilter === 'all' ? 'character' : kindFilter }); setEditing(true); setEditorKey(k => k + 1); setMentions(null); }}
              className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-2.5 py-1.5 hover:bg-white cursor-pointer"
            >
              <Plus className="w-3 h-3" /> New entry
            </button>
          )}
        </div>
        <div className="relative">
          <Search className="w-3 h-3 text-text-warm/40 absolute left-2 top-2" />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search the lore library..."
            className="w-full bg-neutral-950/40 border border-sp-bronze/25 text-white text-[11px] rounded pl-6 pr-2 py-1 focus:outline-hidden focus:border-sp-gold"
          />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          {list.map(e => {
            const Icon = KIND_ICONS[e.kind] || Sparkles;
            return (
              <button
                key={e.id}
                onClick={() => openEntity(e)}
                className={`text-left rounded-lg border p-2.5 flex gap-2.5 cursor-pointer transition-colors ${
                  selected?.id === e.id ? 'border-sp-gold bg-sp-gold/10' : 'border-sp-bronze/25 bg-neutral-900/40 hover:border-sp-bronze/50'
                }`}
              >
                {e.imageUrl
                  ? <img src={e.imageUrl} alt="" className="w-10 h-10 rounded object-cover shrink-0 bg-neutral-800" />
                  : <span className="w-10 h-10 rounded bg-neutral-800/70 flex items-center justify-center shrink-0"><Icon className="w-4 h-4 text-sp-copper" /></span>}
                <span className="min-w-0">
                  <span className="block text-xs font-bold text-white truncate">{e.name}</span>
                  <span className="block text-[9px] uppercase tracking-wider text-sp-copper/80">{STORY_ENTITY_KINDS.find(k => k.key === e.kind)?.label || e.kind}</span>
                  {e.summary && <span className="block text-[10px] text-text-warm/55 line-clamp-2 mt-0.5">{e.summary}</span>}
                </span>
              </button>
            );
          })}
        </div>
        {list.length === 0 && (
          <p className="text-[11px] text-text-warm/40 italic">
            {entities.length === 0 ? (access.canEdit ? 'The lore library is empty. Add characters, places, factions and items the story refers to.' : 'The lore library is empty.') : 'Nothing matches.'}
          </p>
        )}
      </div>

      <div>
        {selected ? (
          <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-4 space-y-3">
            {editing ? (
              <>
                <div className="grid grid-cols-1 sm:grid-cols-[1fr_140px] gap-2">
                  <label className="block">
                    <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Name</span>
                    <input value={selected.name} onChange={e => setSelected(s => ({ ...s, name: e.target.value }))} className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-sm rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold" />
                  </label>
                  <label className="block">
                    <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Kind</span>
                    <select value={selected.kind} onChange={e => setSelected(s => ({ ...s, kind: e.target.value }))} className="mt-0.5 w-full bg-neutral-950 border border-sp-bronze/30 text-white text-sm rounded px-2 py-1">
                      {STORY_ENTITY_KINDS.map(k => <option key={k.key} value={k.key}>{k.label}</option>)}
                    </select>
                  </label>
                </div>
                <label className="block">
                  <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Short summary (one or two sentences)</span>
                  <textarea value={selected.summary} onChange={e => setSelected(s => ({ ...s, summary: e.target.value }))} rows={2} maxLength={500} className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold resize-y" />
                </label>
                <label className="block">
                  <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Image link (optional)</span>
                  <input value={selected.imageUrl} onChange={e => setSelected(s => ({ ...s, imageUrl: e.target.value }))} placeholder="https://..." className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold" />
                </label>
                <div>
                  <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Details</span>
                  <div className="mt-0.5">
                    <StoryEditor
                      key={`entity-${editorKey}`}
                      value={selected.content}
                      onChange={html => setSelected(s => ({ ...s, content: html }))}
                      loreNames={entities.map(e => e.name).filter(n => n !== selected.name)}
                      minHeight="min-h-[220px]"
                    />
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button onClick={save} disabled={saving} className="text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-3 py-1.5 hover:bg-white cursor-pointer disabled:opacity-40">
                    {saving ? 'Saving…' : 'Save'}
                  </button>
                  <button
                    onClick={() => { if (selected.id) openEntity(entities.find(e => e.id === selected.id) || selected); else setSelected(null); }}
                    className="text-[10px] font-bold uppercase tracking-wider border border-sp-bronze/40 text-text-warm/70 rounded-md px-3 py-1.5 hover:text-white cursor-pointer"
                  >
                    Cancel
                  </button>
                  <span className="flex-1" />
                  {selected.id && access.canManage && <button onClick={remove} className="text-[10px] text-rose-300/80 hover:text-rose-300 cursor-pointer flex items-center gap-1"><Trash2 className="w-3 h-3" /> Delete</button>}
                </div>
              </>
            ) : (
              <>
                <div className="flex gap-3">
                  {selected.imageUrl && <img src={selected.imageUrl} alt="" className="w-20 h-20 rounded-lg object-cover bg-neutral-800 shrink-0" />}
                  <div className="min-w-0 flex-1">
                    <p className="text-[9px] uppercase tracking-wider text-sp-copper">{STORY_ENTITY_KINDS.find(k => k.key === selected.kind)?.label}</p>
                    <h3 className="font-serif text-xl font-bold text-white">{selected.name}</h3>
                    {selected.summary && <p className="text-xs text-text-warm/70 mt-1">{selected.summary}</p>}
                    <p className="text-[9px] text-text-warm/40 mt-1">Last edited by {selected.updatedBy || '—'} · {fmtDateTime(selected.updatedAt)}</p>
                  </div>
                  {access.canEdit && (
                    <button onClick={() => { setEditing(true); setEditorKey(k => k + 1); }} className="self-start flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-2.5 py-1.5 hover:bg-white cursor-pointer">
                      <Pencil className="w-3 h-3" /> Edit
                    </button>
                  )}
                </div>
                {(selected.content || '').replace(/<[^>]+>/g, '').trim() && (
                  <div className="border border-sp-bronze/20 rounded-lg bg-neutral-950/40">
                    <StoryEditor key={`entity-read-${editorKey}`} value={selected.content} editable={false} minHeight="min-h-0" />
                  </div>
                )}
                {selected.id && (
                  <div>
                    <p className="text-[9px] uppercase tracking-widest text-text-warm/40 mb-1 flex items-center gap-1"><Paperclip className="w-3 h-3" /> Files</p>
                    <StoryFiles key={selected.id} scope={{ entityId: selected.id }} access={access} tree={tree} entities={entities} flash={flash} compact />
                  </div>
                )}
                <div>
                  <p className="text-[9px] uppercase tracking-widest text-text-warm/40 mb-1">Mentioned in</p>
                  {mentions === null ? <p className="text-[10px] text-text-warm/40 italic">Loading…</p>
                    : mentions.length === 0 ? <p className="text-[10px] text-text-warm/40 italic">No chapter links to [[{selected.name}]] yet.</p>
                    : (
                      <div className="flex flex-wrap gap-1.5">
                        {mentions.map(d => (
                          <span key={d.id} className="inline-flex items-center gap-1 text-[10px] border border-sp-bronze/30 rounded-full px-2 py-0.5 text-text-warm/80">
                            <FileText className="w-3 h-3" /> {d.title} <StatusBadge status={d.status} small />
                          </span>
                        ))}
                      </div>
                    )}
                </div>
              </>
            )}
          </div>
        ) : (
          <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-10 text-center space-y-2">
            <BookOpen className="w-8 h-8 text-sp-copper mx-auto" />
            <p className="text-sm text-text-warm/70">{access.canEdit ? 'Pick an entry, or add a new one.' : 'Pick an entry to read it.'}</p>
            <p className="text-[11px] text-text-warm/40 max-w-sm mx-auto">
              In a chapter, write <b>[[Name]]</b> (or use the book button in the toolbar) to link to an entry here.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ============================== TIMELINE ============================== */

const EMPTY_EVENT = { id: null, sortKey: '', eraLabel: '', title: '', description: '', nodeId: '' };

function TimelineView({ tree, flash }) {
  const access = useStoryAccess();
  const [events, setEvents] = useState(null);
  const [error, setError] = useState('');
  const [form, setForm] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const d = await api('/api/story/timeline');
      setEvents(d.events || []);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  const docs = tree.filter(n => n.type === 'doc').sort((a, b) => a.title.localeCompare(b.title));

  const save = async (e) => {
    e.preventDefault();
    if (!form.title.trim()) { flash('error', 'A title is required.'); return; }
    setSaving(true);
    try {
      await post('/api/story/timeline', { ...form, sortKey: form.sortKey === '' ? 0 : Number(form.sortKey) });
      setForm(null);
      await load();
    } catch (err) {
      flash('error', err.message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (ev) => {
    if (!window.confirm(`Delete the event "${ev.title}"?`)) return;
    try {
      await api(`/api/story/timeline?id=${encodeURIComponent(ev.id)}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      flash('error', err.message);
    }
  };

  const nextKey = events && events.length ? Math.max(...events.map(e => e.sortKey)) + 10 : 10;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-[11px] text-text-warm/50 max-w-xl">
          Events are ordered by their <b>order number</b> — use an in-world year, or leave gaps of 10 so you can slot events in between later.
        </p>
        {access.canEdit && (
          <button onClick={() => setForm({ ...EMPTY_EVENT, sortKey: String(nextKey) })} className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-2.5 py-1.5 hover:bg-white cursor-pointer">
            <Plus className="w-3 h-3" /> New event
          </button>
        )}
      </div>

      {form && (
        <form onSubmit={save} className="bg-neutral-900/40 border border-sp-gold/40 rounded-xl p-4 space-y-2">
          <div className="grid grid-cols-1 sm:grid-cols-[110px_180px_1fr] gap-2">
            <label className="block">
              <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Order number</span>
              <input type="number" value={form.sortKey} onChange={e => setForm(f => ({ ...f, sortKey: e.target.value }))} className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold" />
            </label>
            <label className="block">
              <span className="text-[9px] uppercase tracking-wider text-text-warm/50">When (as shown)</span>
              <input value={form.eraLabel} onChange={e => setForm(f => ({ ...f, eraLabel: e.target.value }))} placeholder="e.g. Year 312, Spring" className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold" />
            </label>
            <label className="block">
              <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Title</span>
              <input autoFocus value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold" />
            </label>
          </div>
          <label className="block">
            <span className="text-[9px] uppercase tracking-wider text-text-warm/50">What happened</span>
            <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={3} className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold resize-y" />
          </label>
          <label className="block">
            <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Linked chapter (optional)</span>
            <select value={form.nodeId || ''} onChange={e => setForm(f => ({ ...f, nodeId: e.target.value }))} className="mt-0.5 w-full bg-neutral-950 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1">
              <option value="">— none —</option>
              {docs.map(d => <option key={d.id} value={d.id}>{d.title}</option>)}
            </select>
          </label>
          <div className="flex gap-2">
            <button disabled={saving} className="text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md px-3 py-1.5 hover:bg-white cursor-pointer disabled:opacity-40">{saving ? 'Saving…' : 'Save event'}</button>
            <button type="button" onClick={() => setForm(null)} className="text-[10px] font-bold uppercase tracking-wider border border-sp-bronze/40 text-text-warm/70 rounded-md px-3 py-1.5 hover:text-white cursor-pointer">Cancel</button>
          </div>
        </form>
      )}

      {error && <p className="text-[11px] text-rose-300">{error}</p>}
      {events === null && !error && <p className="text-[11px] text-text-warm/40 italic">Loading…</p>}
      {events && events.length === 0 && !form && (
        <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-10 text-center space-y-2">
          <Clock className="w-8 h-8 text-sp-copper mx-auto" />
          <p className="text-sm text-text-warm/70">No events yet.</p>
          {access.canEdit && <p className="text-[11px] text-text-warm/40">Build the history of the world, from ancient times to what happens this season.</p>}
        </div>
      )}

      {events && events.length > 0 && (
        <ol className="relative border-l-2 border-sp-bronze/30 ml-3 space-y-4">
          {events.map(ev => (
            <li key={ev.id} className="ml-5 group">
              <span className="absolute -left-[7px] w-3 h-3 rounded-full bg-sp-gold border-2 border-sp-dark mt-1.5" />
              <div className="bg-neutral-900/40 border border-sp-bronze/25 rounded-lg p-3">
                <div className="flex flex-wrap items-baseline gap-2">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-sp-copper">{ev.eraLabel || `#${ev.sortKey}`}</span>
                  <h4 className="font-serif text-sm font-bold text-white">{ev.title}</h4>
                  <span className="flex-1" />
                  <span className="hidden group-hover:flex gap-0.5">
                    {access.canEdit && <IconBtn title="Edit" onClick={() => setForm({ id: ev.id, sortKey: String(ev.sortKey), eraLabel: ev.eraLabel || '', title: ev.title, description: ev.description || '', nodeId: ev.nodeId || '' })}><Pencil className="w-3 h-3" /></IconBtn>}
                    {access.canManage && <IconBtn title="Delete" onClick={() => remove(ev)} danger><Trash2 className="w-3 h-3" /></IconBtn>}
                  </span>
                </div>
                {ev.description && <p className="text-xs text-text-warm/75 mt-1 whitespace-pre-wrap">{ev.description}</p>}
                {ev.nodeTitle && <p className="text-[10px] text-text-warm/45 mt-1.5 flex items-center gap-1"><FileText className="w-3 h-3" /> Chapter: {ev.nodeTitle}</p>}
                <p className="text-[9px] text-text-warm/30 mt-1">by {ev.updatedBy || '—'} · {fmtDateTime(ev.updatedAt)}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

/* ============================== GUEST LINKS ============================== */

const EXPIRY_OPTIONS = [
  { key: '7', label: '7 days' },
  { key: '30', label: '30 days' },
  { key: '90', label: '90 days' },
  { key: 'never', label: 'Never' }
];

function GuestLinksView({ flash }) {
  const [links, setLinks] = useState(null);
  const [error, setError] = useState('');
  const [name, setName] = useState('');
  const [level, setLevel] = useState('edit');
  const [expiry, setExpiry] = useState('30');
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState(null); // { name, url }
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    try {
      const d = await api('/api/story/guests');
      setLinks(d.links || []);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  const create = async (e) => {
    e.preventDefault();
    if (!name.trim()) return;
    setCreating(true);
    try {
      const d = await post('/api/story/guests', { name: name.trim(), access: level, expiry });
      setCreated({ name: d.name, url: `${window.location.origin}/story-room?key=${encodeURIComponent(d.token)}` });
      setCopied(false);
      setName('');
      await load();
    } catch (err) {
      flash('error', err.message);
    } finally {
      setCreating(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(created.url);
      setCopied(true);
    } catch {
      flash('error', 'Could not copy automatically — select the link and copy it yourself.');
    }
  };

  const revoke = async (link) => {
    if (!window.confirm(`Switch off the guest link for ${link.name}? They lose access straight away. Their work stays in the Story Room.`)) return;
    try {
      await api(`/api/story/guests?id=${encodeURIComponent(link.id)}`, { method: 'DELETE' });
      await load();
      flash('success', `Link for ${link.name} switched off.`);
    } catch (err) {
      flash('error', err.message);
    }
  };

  const statusOf = (l) => (l.revoked ? 'off' : l.expiresAt && new Date(l.expiresAt) < new Date() ? 'expired' : 'active');

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,360px)_minmax(0,1fr)] gap-4">
      <div className="space-y-3">
        <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-4 space-y-2 text-[11px] text-text-warm/65">
          <p className="text-xs font-bold text-sp-gold flex items-center gap-1.5"><KeyRound className="w-3.5 h-3.5" /> Invite someone without an account</p>
          <p>A guest link lets a writer work in this Story Room without a website account. They open the link, and they're in — same editor, same chapters.</p>
          <ul className="list-disc pl-4 space-y-0.5">
            <li>Make <b>one link per person</b>, so their changes show up under their own name.</li>
            <li>Guests can never delete anything or publish to the wiki. Every edit is kept in the version history.</li>
            <li>The link is shown <b>once</b>. Lost it? Switch it off and make a new one.</li>
          </ul>
        </div>

        <form onSubmit={create} className="bg-sp-dark border border-sp-bronze p-4 rounded-xl space-y-3 steampunk-glass">
          <label className="block">
            <span className="text-[9px] uppercase font-bold tracking-wider text-text-warm/50">Name of the person</span>
            <input value={name} onChange={e => setName(e.target.value)} maxLength={60} placeholder="e.g. Lotte" required className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1.5 focus:outline-hidden focus:border-sp-gold" />
          </label>
          <div>
            <span className="text-[9px] uppercase font-bold tracking-wider text-text-warm/50">What may they do?</span>
            <div className="mt-1 space-y-1">
              {GUEST_LEVELS.map(l => (
                <label key={l.key} className={`flex items-start gap-2 rounded-md border px-2 py-1.5 cursor-pointer ${level === l.key ? 'border-sp-gold bg-sp-gold/10' : 'border-sp-bronze/25 hover:border-sp-bronze/50'}`}>
                  <input type="radio" name="guest-level" value={l.key} checked={level === l.key} onChange={() => setLevel(l.key)} className="mt-0.5" />
                  <span><span className="block text-xs font-bold text-white">{l.label}</span><span className="block text-[10px] text-text-warm/55">{l.hint}</span></span>
                </label>
              ))}
            </div>
          </div>
          <label className="block">
            <span className="text-[9px] uppercase font-bold tracking-wider text-text-warm/50">Link stops working after</span>
            <select value={expiry} onChange={e => setExpiry(e.target.value)} className="mt-0.5 w-full bg-neutral-950 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1.5">
              {EXPIRY_OPTIONS.map(o => <option key={o.key} value={o.key}>{o.label}</option>)}
            </select>
          </label>
          <button disabled={creating || !name.trim()} className="w-full text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md py-2 hover:bg-white cursor-pointer disabled:opacity-40">
            {creating ? 'Creating…' : 'Create guest link'}
          </button>
        </form>

        {created && (
          <div className="bg-emerald-500/10 border border-emerald-500/50 rounded-xl p-3 space-y-2">
            <p className="text-xs text-emerald-200"><b>Link for {created.name}</b> — copy it now and send it to them privately. It won't be shown again.</p>
            <div className="flex gap-1.5">
              <input readOnly value={created.url} onFocus={e => e.target.select()} className="flex-1 min-w-0 bg-neutral-950 border border-emerald-500/40 text-white text-[11px] font-mono rounded px-2 py-1.5" />
              <button type="button" onClick={copy} className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-emerald-500 text-black rounded-md px-2.5 hover:bg-white cursor-pointer">
                {copied ? <><Check className="w-3 h-3" /> Copied</> : <><Copy className="w-3 h-3" /> Copy</>}
              </button>
            </div>
            <button type="button" onClick={() => setCreated(null)} className="text-[10px] text-text-warm/50 hover:text-white cursor-pointer">Done</button>
          </div>
        )}
      </div>

      <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-3 space-y-2 min-w-0">
        <h4 className="text-[11px] font-bold uppercase tracking-wider text-sp-gold">Guest links</h4>
        {error && <p className="text-[11px] text-rose-300">{error}</p>}
        {links === null && !error && <p className="text-[11px] text-text-warm/40 italic">Loading…</p>}
        {links && links.length === 0 && <p className="text-[11px] text-text-warm/40 italic">No guest links yet.</p>}
        <div className="space-y-1.5">
          {(links || []).map(l => {
            const st = statusOf(l);
            const lvl = GUEST_LEVELS.find(x => x.key === l.access);
            return (
              <div key={l.id} className={`rounded-lg border p-2.5 flex flex-wrap items-center gap-x-3 gap-y-1 ${st === 'active' ? 'border-sp-bronze/30 bg-neutral-950/30' : 'border-sp-bronze/15 opacity-55'}`}>
                <span className="text-xs font-bold text-white">{l.name}</span>
                <span className="text-[9px] uppercase tracking-wider border border-sp-bronze/30 rounded-full px-1.5 py-px text-text-warm/70">{lvl?.label || l.access}</span>
                <span className={`text-[9px] uppercase font-bold tracking-wider ${st === 'active' ? 'text-emerald-300' : st === 'expired' ? 'text-amber-300' : 'text-rose-300'}`}>
                  {st === 'active' ? 'Active' : st === 'expired' ? 'Expired' : 'Switched off'}
                </span>
                <span className="flex-1" />
                <span className="text-[10px] text-text-warm/45">
                  {l.expiresAt ? `${st === 'expired' ? 'Expired' : 'Until'} ${fmtDateTime(l.expiresAt)}` : 'No end date'}
                  {' · '}{l.lastUsedAt ? `last used ${fmtDateTime(l.lastUsedAt)}` : 'never used'}
                  {l.createdBy ? ` · by ${l.createdBy}` : ''}
                </span>
                {st === 'active' && (
                  <button onClick={() => revoke(l)} className="text-[10px] text-rose-300/80 hover:text-rose-300 cursor-pointer flex items-center gap-1">
                    <Lock className="w-3 h-3" /> Switch off
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
