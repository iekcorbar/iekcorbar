'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Paperclip, Image as ImageIcon, Box, Pickaxe, FileText, Film, FileArchive, Download, CloudUpload,
  Pencil, Trash2, X, Search, Maximize2, Check
} from 'lucide-react';
import {
  STORY_FILE_CATEGORIES, STORY_FILE_ACCEPT, STORY_FILE_MAX_BYTES, storyFileCategory, isAllowedStoryFile,
  isStoryImage, storyFileInlineType, formatBytes
} from '@/lib/story-files';

// Story Room uploads (images, 3D models, schematics, documents…). Used in
// three places by StoryRoomTab: the "Files" view (everything), a chapter's
// Files panel (scope.nodeId) and a lore entry (scope.entityId). Talks to
// /api/story/files; bytes come from /api/story/files/raw.

const CATEGORY_ICONS = { image: ImageIcon, model: Box, minecraft: Pickaxe, document: FileText, media: Film, other: FileArchive };
const fmtDate = (d) => (d ? new Date(d).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '');
const rawUrl = (id, inline) => `/api/story/files/raw?id=${encodeURIComponent(id)}${inline ? '&inline=1' : ''}`;

// XHR instead of fetch so big uploads (3D models) can show progress.
function uploadOne(file, scope, onProgress) {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append('file', file);
    if (scope?.nodeId) form.append('nodeId', scope.nodeId);
    if (scope?.entityId) form.append('entityId', scope.entityId);
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/story/files');
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(e.loaded / e.total); };
    xhr.onload = () => {
      let data = {};
      try { data = JSON.parse(xhr.responseText); } catch { /* not JSON */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(data);
      else if (xhr.status === 413) reject(new Error(`"${file.name}" is too large for the server to accept.`));
      else reject(new Error(data.error || `Upload of "${file.name}" failed (${xhr.status}).`));
    };
    xhr.onerror = () => reject(new Error(`Upload of "${file.name}" failed — check your connection.`));
    xhr.send(form);
  });
}

async function jsonApi(url, options = {}) {
  const res = await fetch(url, { ...options, headers: options.body ? { 'Content-Type': 'application/json' } : undefined });
  let data = {};
  try { data = await res.json(); } catch { /* empty */ }
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

function attachedLabel(f) {
  if (f.nodeTitle) return { icon: FileText, text: f.nodeTitle };
  if (f.entityName) return { icon: Paperclip, text: f.entityName };
  return null;
}

export default function StoryFiles({ scope = null, access, tree = [], entities = [], flash, compact = false, onCount }) {
  const [files, setFiles] = useState(null);
  const [error, setError] = useState('');
  const [queue, setQueue] = useState([]); // [{ key, name, progress, error }]
  const [dragOver, setDragOver] = useState(false);
  const [category, setCategory] = useState('all');
  const [search, setSearch] = useState('');
  const [preview, setPreview] = useState(null);
  const [editing, setEditing] = useState(null); // { id, name, note, target }
  const inputRef = useRef(null);

  const load = useCallback(async () => {
    try {
      const d = await jsonApi('/api/story/files');
      setFiles(d.files || []);
      setError('');
    } catch (err) {
      setError(err.message);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  const inScope = useMemo(() => (files || []).filter(f =>
    !scope ? true : scope.nodeId ? f.nodeId === scope.nodeId : scope.entityId ? f.entityId === scope.entityId : true
  ), [files, scope]);

  useEffect(() => { if (files) onCount?.(inScope.length); }, [files, inScope.length, onCount]);

  const q = search.trim().toLowerCase();
  const shown = inScope.filter(f =>
    (category === 'all' || storyFileCategory(f.originalName) === category) &&
    (!q || f.originalName.toLowerCase().includes(q) || (f.note || '').toLowerCase().includes(q) ||
      (f.nodeTitle || '').toLowerCase().includes(q) || (f.entityName || '').toLowerCase().includes(q))
  );

  const startUploads = async (fileList) => {
    const list = Array.from(fileList || []);
    if (!list.length) return;
    const rejected = [];
    const ok = list.filter(f => {
      if (!isAllowedStoryFile(f.name)) { rejected.push(`"${f.name}" is not an accepted file type.`); return false; }
      if (f.size > STORY_FILE_MAX_BYTES) { rejected.push(`"${f.name}" is larger than ${formatBytes(STORY_FILE_MAX_BYTES)}.`); return false; }
      return true;
    });
    if (rejected.length) flash?.('error', rejected.join(' '));
    for (const file of ok) {
      const key = `${file.name}-${file.size}-${Math.random().toString(36).slice(2)}`;
      setQueue(qu => [...qu, { key, name: file.name, progress: 0, error: '' }]);
      try {
        await uploadOne(file, scope, (p) => setQueue(qu => qu.map(x => (x.key === key ? { ...x, progress: p } : x))));
        setQueue(qu => qu.filter(x => x.key !== key));
        await load();
      } catch (err) {
        setQueue(qu => qu.map(x => (x.key === key ? { ...x, error: err.message } : x)));
      }
    }
  };

  const onDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    if (access.canEdit) startUploads(e.dataTransfer.files);
  };

  const download = (f) => {
    const a = document.createElement('a');
    a.href = rawUrl(f.id, false);
    a.download = f.originalName;
    document.body.appendChild(a);
    a.click();
    a.remove();
  };

  const remove = async (f) => {
    if (!window.confirm(`Delete "${f.originalName}"? This cannot be undone.`)) return;
    try {
      await jsonApi(`/api/story/files?id=${encodeURIComponent(f.id)}`, { method: 'DELETE' });
      await load();
    } catch (err) {
      flash?.('error', err.message);
    }
  };

  const saveEdit = async (e) => {
    e.preventDefault();
    const [kind, targetId] = (editing.target || 'general:').split(':');
    try {
      await jsonApi('/api/story/files', {
        method: 'POST',
        body: JSON.stringify({
          action: 'update', id: editing.id, name: editing.name, note: editing.note,
          nodeId: kind === 'node' ? targetId : null, entityId: kind === 'entity' ? targetId : null
        })
      });
      setEditing(null);
      await load();
    } catch (err) {
      flash?.('error', err.message);
    }
  };

  const canDelete = (f) => access.canManage || (access.canEdit && f.uploadedBy === access.actor);
  const docs = tree.filter(n => n.type === 'doc').sort((a, b) => a.title.localeCompare(b.title));

  return (
    <div
      className={`space-y-3 ${dragOver ? 'ring-2 ring-sp-gold/60 rounded-xl' : ''}`}
      onDragOver={e => { if (access.canEdit) { e.preventDefault(); setDragOver(true); } }}
      onDragLeave={e => { if (e.currentTarget === e.target) setDragOver(false); }}
      onDrop={onDrop}
    >
      {access.canEdit && (
        <div className={`border-2 border-dashed rounded-xl ${compact ? 'p-3' : 'p-5'} text-center transition-colors ${dragOver ? 'border-sp-gold bg-sp-gold/10' : 'border-sp-bronze/35 bg-neutral-950/30'}`}>
          <CloudUpload className={`${compact ? 'w-5 h-5' : 'w-7 h-7'} text-sp-copper mx-auto`} />
          <p className="text-[11px] text-text-warm/70 mt-1">
            Drop files here, or{' '}
            <button type="button" onClick={() => inputRef.current?.click()} className="text-sp-gold font-bold hover:underline cursor-pointer">choose files</button>
          </p>
          {!compact && (
            <p className="text-[10px] text-text-warm/40 mt-1">
              Images, 3D models (.obj, .fbx, .glb, .blend, .bbmodel…), schematics (.schem, .litematic, .nbt), documents, audio/video and archives — up to {formatBytes(STORY_FILE_MAX_BYTES)} per file.
            </p>
          )}
          <input ref={inputRef} type="file" multiple accept={STORY_FILE_ACCEPT} className="hidden" onChange={e => { startUploads(e.target.files); e.target.value = ''; }} />
        </div>
      )}

      {queue.length > 0 && (
        <div className="space-y-1">
          {queue.map(u => (
            <div key={u.key} className="text-[10px] bg-neutral-950/40 border border-sp-bronze/25 rounded px-2 py-1.5">
              <div className="flex items-center gap-2">
                <span className="truncate flex-1 text-text-warm/80">{u.name}</span>
                {u.error
                  ? <button onClick={() => setQueue(qu => qu.filter(x => x.key !== u.key))} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Dismiss"><X className="w-3 h-3" /></button>
                  : <span className="text-text-warm/50">{Math.round(u.progress * 100)}%</span>}
              </div>
              {u.error
                ? <p className="text-rose-300 mt-0.5">{u.error}</p>
                : <div className="h-1 bg-neutral-800 rounded mt-1 overflow-hidden"><div className="h-full bg-sp-gold transition-all" style={{ width: `${Math.round(u.progress * 100)}%` }} /></div>}
            </div>
          ))}
        </div>
      )}

      {!compact && (
        <div className="flex flex-wrap items-center gap-1.5">
          {[{ key: 'all', label: 'All' }, ...STORY_FILE_CATEGORIES].map(c => {
            const count = c.key === 'all' ? inScope.length : inScope.filter(f => storyFileCategory(f.originalName) === c.key).length;
            return (
              <button
                key={c.key}
                onClick={() => setCategory(c.key)}
                className={`text-[10px] uppercase font-bold tracking-wider px-2.5 py-1 rounded-full border cursor-pointer ${category === c.key ? 'border-sp-gold text-sp-gold bg-sp-gold/10' : 'border-sp-bronze/25 text-text-warm/50 hover:text-white'}`}
              >
                {c.label}{count > 0 && <span className="opacity-60"> {count}</span>}
              </button>
            );
          })}
          <span className="flex-1" />
          <div className="relative">
            <Search className="w-3 h-3 text-text-warm/40 absolute left-2 top-2" />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search files..." className="bg-neutral-950/40 border border-sp-bronze/25 text-white text-[11px] rounded pl-6 pr-2 py-1 w-48 focus:outline-hidden focus:border-sp-gold" />
          </div>
        </div>
      )}

      {error && <p className="text-[11px] text-rose-300">{error}</p>}
      {files === null && !error && <p className="text-[11px] text-text-warm/40 italic">Loading files…</p>}
      {files && shown.length === 0 && (
        <p className="text-[11px] text-text-warm/40 italic">
          {inScope.length === 0 ? (scope ? 'No files attached yet.' : 'No files uploaded yet.') : 'No file matches.'}
        </p>
      )}

      <div className={compact ? 'space-y-1.5' : 'grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-2'}>
        {shown.map(f => {
          const cat = storyFileCategory(f.originalName) || 'other';
          const Icon = CATEGORY_ICONS[cat] || FileArchive;
          const image = isStoryImage(f.originalName);
          const playable = !image && !!storyFileInlineType(f.originalName);
          const attached = !scope && attachedLabel(f);
          return (
            <div key={f.id} className="group rounded-lg border border-sp-bronze/25 bg-neutral-900/40 p-2 flex gap-2.5 min-w-0">
              <button
                type="button"
                onClick={() => (image || playable ? setPreview(f) : download(f))}
                title={image || playable ? 'Preview' : 'Download'}
                className={`${compact ? 'w-10 h-10' : 'w-14 h-14'} shrink-0 rounded bg-neutral-800/70 overflow-hidden flex items-center justify-center cursor-pointer`}
              >
                {image
                  // eslint-disable-next-line @next/next/no-img-element
                  ? <img src={rawUrl(f.id, true)} alt="" loading="lazy" className="w-full h-full object-cover" />
                  : <Icon className={`${compact ? 'w-4 h-4' : 'w-5 h-5'} text-sp-copper`} />}
              </button>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-bold text-white truncate" title={f.originalName}>{f.originalName}</p>
                <p className="text-[9px] text-text-warm/45 truncate">
                  {formatBytes(f.sizeBytes)} · {f.uploadedBy || '—'} · {fmtDate(f.createdAt)}
                </p>
                {attached && (
                  <p className="text-[9px] text-sp-copper/85 truncate flex items-center gap-1 mt-0.5"><attached.icon className="w-2.5 h-2.5 shrink-0" /> {attached.text}</p>
                )}
                {f.note && <p className="text-[10px] text-text-warm/65 line-clamp-2 mt-0.5">{f.note}</p>}
                <div className="flex items-center gap-2 mt-1 text-[10px] text-text-warm/50">
                  {(image || playable) && <button onClick={() => setPreview(f)} className="flex items-center gap-0.5 hover:text-white cursor-pointer"><Maximize2 className="w-3 h-3" /> View</button>}
                  <button onClick={() => download(f)} className="flex items-center gap-0.5 hover:text-white cursor-pointer"><Download className="w-3 h-3" /> Download</button>
                  {access.canEdit && (
                    <button
                      onClick={() => setEditing({ id: f.id, name: f.originalName, note: f.note || '', target: f.nodeId ? `node:${f.nodeId}` : f.entityId ? `entity:${f.entityId}` : 'general:' })}
                      className="flex items-center gap-0.5 hover:text-white cursor-pointer"
                    >
                      <Pencil className="w-3 h-3" /> Edit
                    </button>
                  )}
                  {canDelete(f) && <button onClick={() => remove(f)} className="flex items-center gap-0.5 hover:text-rose-300 cursor-pointer"><Trash2 className="w-3 h-3" /></button>}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4" onClick={() => setEditing(null)}>
          <form onSubmit={saveEdit} onClick={e => e.stopPropagation()} className="w-full max-w-md bg-sp-dark border border-sp-bronze rounded-xl p-4 space-y-3 steampunk-glass">
            <div className="flex items-center justify-between">
              <h4 className="text-[11px] font-bold uppercase tracking-wider text-sp-gold">Edit file</h4>
              <button type="button" onClick={() => setEditing(null)} className="text-text-warm/50 hover:text-white cursor-pointer" aria-label="Close"><X className="w-4 h-4" /></button>
            </div>
            <label className="block">
              <span className="text-[9px] uppercase tracking-wider text-text-warm/50">File name</span>
              <input value={editing.name} onChange={e => setEditing(x => ({ ...x, name: e.target.value }))} className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1.5 focus:outline-hidden focus:border-sp-gold" />
            </label>
            <label className="block">
              <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Belongs to</span>
              <select value={editing.target} onChange={e => setEditing(x => ({ ...x, target: e.target.value }))} className="mt-0.5 w-full bg-neutral-950 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1.5">
                <option value="general:">General (not attached)</option>
                {docs.length > 0 && <optgroup label="Chapters">{docs.map(d => <option key={d.id} value={`node:${d.id}`}>{d.title}</option>)}</optgroup>}
                {entities.length > 0 && <optgroup label="Lore entries">{[...entities].sort((a, b) => a.name.localeCompare(b.name)).map(en => <option key={en.id} value={`entity:${en.id}`}>{en.name}</option>)}</optgroup>}
              </select>
            </label>
            <label className="block">
              <span className="text-[9px] uppercase tracking-wider text-text-warm/50">Note (optional)</span>
              <textarea value={editing.note} onChange={e => setEditing(x => ({ ...x, note: e.target.value }))} rows={2} maxLength={500} placeholder="e.g. Final texture for the clocktower" className="mt-0.5 w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded px-2 py-1.5 focus:outline-hidden focus:border-sp-gold resize-y" />
            </label>
            <button className="w-full flex items-center justify-center gap-1 text-[10px] font-bold uppercase tracking-wider bg-sp-gold text-black rounded-md py-2 hover:bg-white cursor-pointer">
              <Check className="w-3 h-3" /> Save
            </button>
          </form>
        </div>
      )}

      {preview && (
        <div className="fixed inset-0 z-50 bg-black/85 flex flex-col items-center justify-center p-4 gap-3" onClick={() => setPreview(null)}>
          <div className="flex items-center gap-3 text-xs text-white max-w-full" onClick={e => e.stopPropagation()}>
            <span className="truncate font-bold">{preview.originalName}</span>
            <button onClick={() => download(preview)} className="flex items-center gap-1 text-sp-gold hover:text-white cursor-pointer shrink-0"><Download className="w-3.5 h-3.5" /> Download</button>
            <button onClick={() => setPreview(null)} className="text-text-warm/60 hover:text-white cursor-pointer shrink-0" aria-label="Close preview"><X className="w-4 h-4" /></button>
          </div>
          <div onClick={e => e.stopPropagation()} className="max-w-full max-h-[80vh] flex items-center justify-center">
            {isStoryImage(preview.originalName) ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={rawUrl(preview.id, true)} alt={preview.originalName} className="max-w-full max-h-[80vh] object-contain rounded" />
            ) : (storyFileInlineType(preview.originalName) || '').startsWith('audio/') ? (
              <audio src={rawUrl(preview.id, true)} controls autoPlay className="w-[min(480px,90vw)]" />
            ) : (
              <video src={rawUrl(preview.id, true)} controls autoPlay className="max-w-full max-h-[80vh] rounded" />
            )}
          </div>
          {preview.note && <p className="text-[11px] text-text-warm/70 max-w-xl text-center" onClick={e => e.stopPropagation()}>{preview.note}</p>}
        </div>
      )}
    </div>
  );
}
