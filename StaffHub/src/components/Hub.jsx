'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Anchor, Folder, FolderPlus, FileText, File as FileIcon, Image as ImageIcon, Download, Trash2,
  Pencil, MoveRight, Search, LogOut, Upload, Lock, Globe, Users, KeyRound, History, Server, ArrowLeft,
  RefreshCw, Eye, EyeOff, Plus, X,
} from 'lucide-react';
import PageEditor from '@/components/PageEditor';
import Assistant from '@/components/Assistant';
import { useLang, useDt, trServer, LangToggle } from '@/lib/i18n';

const fmt = (n) => n == null ? '' : n > 1e9 ? (n / 1e9).toFixed(1) + ' GB' : n > 1e6 ? (n / 1e6).toFixed(1) + ' MB' : n > 1000 ? (n / 1000).toFixed(0) + ' KB' : n + ' B';

async function jget(url) { const r = await fetch(url); if (r.status === 401) throw new Error('login'); return (await r.json()).error ? null : r.json(); }
async function jsend(url, method, body) { const r = await fetch(url, { method, headers: body ? { 'content-type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined }); const j = await r.json().catch(() => ({})); if (!r.ok) throw new Error(trServer(j.error) || trServer('mislukt')); return j; }

const ICONS = { folder: Folder, page: FileText, file: FileIcon };

export default function Hub({ me, onLogout }) {
  const { t } = useLang();
  const dt = useDt();
  const [parent, setParent] = useState(null);
  const [trail, setTrail] = useState([]);
  const [kids, setKids] = useState([]);
  const [sel, setSel] = useState(null);           // geselecteerd node (rechts-voorbeeld)
  const [expanded, setExpanded] = useState({});   // boom: id -> kinderen
  const [q, setQ] = useState('');
  const [results, setResults] = useState(null);
  const [modal, setModal] = useState(null);       // 'users' | 'tokens' | 'dav'
  const [moveFrom, setMoveFrom] = useState(null);  // node die verplaatst wordt
  const [err, setErr] = useState('');
  const upRef = useRef();

  const load = useCallback(async (pid) => {
    try {
      const j = await (await fetch('/api/tree?parent=' + (pid ?? ''))).json();
      if (j.error) return;
      setParent(j.parent); setTrail(j.trail); setKids(j.kids);
    } catch (e) { setErr(t('err.load') + ': ' + e.message); }
  }, [t]);
  useEffect(() => { load(parent); }, [parent, load]);

  async function refresh() { await load(parent); }

  // ---------- uploaden (knop + drag&drop, ook met mappen) ----------
  async function uploadFiles(list) {
    const groups = new Map(); // dirPath -> File[]
    for (const { file, path } of list) {
      const key = path || '';
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(file);
    }
    for (const [path, files] of groups) {
      const fd = new FormData();
      if (parent) fd.set('parent', String(parent));
      if (path) fd.set('path', path);
      for (const f of files) fd.append('files', f, f.name);
      try { await (await fetch('/api/upload', { method: 'POST', body: fd })).json(); }
      catch (e) { setErr(t('err.upload') + ': ' + e.message); }
    }
    await refresh();
  }
  async function onDrop(e) {
    e.preventDefault();
    const items = [...(e.dataTransfer?.items || [])];
    const out = [];
    async function walk(entry, path) {
      if (entry.isFile) {
        const f = await new Promise((res) => entry.file(res));
        out.push({ file: f, path });
      } else if (entry.isDirectory) {
        const reader = entry.createReader();
        const kidsE = await new Promise((res) => reader.readEntries(res));
        for (const k of kidsE) await walk(k, path ? path + '/' + entry.name : entry.name);
      }
    }
    if (items.some((i) => i.kind === 'file')) {
      const entries = items.map((i) => i.webkitGetAsEntry?.()).filter(Boolean);
      if (entries.length) { for (const en of entries) await walk(en, ''); await uploadFiles(out); return; }
    }
    await uploadFiles([...(e.dataTransfer?.files || [])].map((f) => ({ file: f, path: '' })));
  }
  function onPick(e) {
    const files = [...e.target.files || []];
    uploadFiles(files.map((f) => ({ file: f, path: '' })));
    e.target.value = '';
  }

  // ---------- acties ----------
  async function newFolder() {
    const name = window.prompt(t('prompt.folder'));
    if (!name) return;
    try { await jsend('/api/folders', 'POST', { parent, name }); await refresh(); } catch (e) { setErr(e.message); }
  }
  async function newPage() {
    const name = window.prompt(t('prompt.pagetitle'), t('prompt.pagedefault'));
    if (!name) return;
    try { const j = await jsend('/api/pages', 'POST', { parent, name }); await load(parent); setSel({ id: j.id, name, type: 'page' }); } catch (e) { setErr(e.message); }
  }
  async function rename(n) {
    const name = window.prompt(t('prompt.rename'), n.name);
    if (!name || name === n.name) return;
    try { await jsend('/api/nodes/' + n.id, 'PATCH', { name }); await refresh(); if (sel?.id === n.id) setSel({ ...sel, name }); } catch (e) { setErr(e.message); }
  }
  async function toggleVis(n) {
    const order = [null, 'all', 'team', 'owner'];
    const next = order[(order.indexOf(n.vis ?? null) + 1) % order.length];
    try { await jsend('/api/nodes/' + n.id, 'PATCH', { vis: next }); await refresh(); } catch (e) { setErr(e.message); }
  }
  async function remove(n) {
    if (!window.confirm(t(n.type === 'folder' ? 'confirm.del_folder' : 'confirm.del', { name: n.name }))) return;
    try { await jsend('/api/nodes/' + n.id, 'DELETE'); await refresh(); if (sel?.id === n.id) setSel(null); } catch (e) { setErr(e.message); }
  }
  async function moveTo(targetId) {
    try { await jsend('/api/nodes/' + moveFrom.id, 'PATCH', { parent: targetId }); setMoveFrom(null); await refresh(); } catch (e) { setErr(e.message); }
  }

  // ---------- zoeken ----------
  useEffect(() => {
    if (!q || q.length < 2) { setResults(null); return; }
    const tm = setTimeout(async () => {
      try { const j = await (await fetch('/api/search?q=' + encodeURIComponent(q))).json(); setResults(j.results || []); } catch { }
    }, 300);
    return () => clearTimeout(tm);
  }, [q]);

  // ---------- mappenboom links ----------
  async function toggleDir(id) {
    if (expanded[id]) { const { [id]: _, ...rest } = expanded; setExpanded(rest); return; }
    try { const j = await (await fetch('/api/tree?parent=' + id)).json(); setExpanded((e) => ({ ...e, [id]: (j.kids || []).filter((k) => k.type === 'folder') })); } catch { }
  }
  function TreeRow({ n, depth }) {
    const open = !!expanded[n.id];
    return (
      <div>
        <div className="flex items-center gap-1 text-[12px] hover:bg-white/5 rounded px-1 cursor-pointer" style={{ paddingLeft: depth * 12 }}
          onClick={() => { setParent(n.id); setSel(null); }} onDoubleClick={() => toggleDir(n.id)}>
          <button onClick={(e) => { e.stopPropagation(); toggleDir(n.id); }} className="w-4 text-parch/40">{open ? '▾' : '▸'}</button>
          <Folder className="size-3.5 text-gold/70 shrink-0" />
          <span className="truncate">{n.name}</span>
          {n.vis === 'owner' && <Lock className="size-3 text-red-300/70 shrink-0" />}
          {n.vis === 'team' && <span className="text-[9px] text-copper shrink-0">{n.team}</span>}
        </div>
        {open && (expanded[n.id] || []).map((k) => <TreeRow key={k.id} n={k} depth={depth + 1} />)}
      </div>
    );
  }
  const rootDirs = kids.filter((k) => k.type === 'folder');

  const TypeIcon = (n) => (ICONS[n.type] || FileIcon);
  const visBadge = (n) => n.vis === 'owner' ? <Lock className="inline size-3 text-red-300/70" /> : n.vis === 'team' ? <span className="text-[9px] text-copper">{n.team || 'team'}</span> : n.vis === 'all' ? <Globe className="inline size-3 text-emerald-300/60" /> : null;

  return (
    <div className="h-screen flex flex-col">
      {/* topbalk */}
      <header className="flex items-center gap-3 px-4 py-2 border-b border-bronze/30 bg-panel">
        <span className="size-8 grid place-items-center rounded-full bg-gold/10 border border-gold/40"><Anchor className="size-4 text-gold" /></span>
        <h1 className="font-bold text-gold mr-2">StaffHub</h1>
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2.5 top-2 size-4 text-parch/40" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search.ph')}
            className="w-full bg-black/40 border border-bronze/30 rounded-lg pl-8 pr-3 py-1.5 text-xs outline-none focus:border-gold" />
          {results && (
            <div className="absolute top-9 left-0 right-0 bg-panel border border-bronze/40 rounded-lg shadow-2xl max-h-80 overflow-auto z-40">
              {results.length === 0 && <p className="p-3 text-xs text-parch/50">{t('search.none')}</p>}
              {results.map((r) => (
                <button key={r.id} className="w-full text-left px-3 py-2 text-xs hover:bg-white/5 flex items-center gap-2"
                  onClick={() => { setQ(''); setResults(null); setSel({ id: r.id, name: r.name, type: r.type }); }}>
                  {(() => { const I = TypeIcon(r); return <I className="size-3.5 text-gold/70" />; })()}{r.name}
                  <span className="text-parch/40 ml-auto">{t('why.' + r.why)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="ml-auto flex items-center gap-2 text-xs">
          <span className="text-parch/60 hidden sm:inline">{me.username}{me.team ? ` (${me.team})` : ''}{me.role === 'owner' ? ' ⚓' : ''}</span>
          <LangToggle />
          <button title={t('tip.disk')} onClick={() => setModal('dav')} className="p-1.5 rounded hover:bg-white/10"><Server className="size-4" /></button>
          <button title={t('tip.tokens')} onClick={() => setModal('tokens')} className="p-1.5 rounded hover:bg-white/10"><KeyRound className="size-4" /></button>
          {me.role === 'owner' && <button title={t('tip.accounts')} onClick={() => setModal('users')} className="p-1.5 rounded hover:bg-white/10"><Users className="size-4" /></button>}
          <button title={t('tip.logout')} onClick={async () => { await fetch('/api/auth/logout', { method: 'POST' }); onLogout(); }} className="p-1.5 rounded hover:bg-white/10"><LogOut className="size-4" /></button>
        </div>
      </header>

      {err && <div className="bg-red-900/40 text-red-100 text-xs px-4 py-1.5 flex items-center gap-2"><span>{err}</span><button onClick={() => setErr('')}><X className="size-3.5" /></button></div>}

      <div className="flex-1 grid grid-cols-[220px_1fr] md:grid-cols-[240px_1fr_380px] overflow-hidden">
        {/* boom */}
        <nav className="border-r border-bronze/20 bg-black/20 p-2 overflow-auto">
          <div className={`flex items-center gap-1 text-[12px] rounded px-1 cursor-pointer ${parent === null ? 'bg-gold/15 text-gold' : 'hover:bg-white/5'}`}
            onClick={() => { setParent(null); setSel(null); }}>
            <Folder className="size-3.5" /> <b>{t('nav.all')}</b>
            {moveFrom && <button className="ml-auto text-[9px] bg-copper text-ink rounded px-1" onClick={() => moveTo(null)}>{t('nav.here')}</button>}
          </div>
          {rootDirs.map((n) => <TreeRow key={n.id} n={n} depth={0} />)}
          {moveFrom && <p className="text-[10px] text-copper mt-2 px-1">{t('nav.move_hint_a')}<b>{moveFrom.name}</b>{t('nav.move_hint_b')}</p>}
        </nav>

        {/* lijst */}
        <main className="overflow-auto p-3" onDragOver={(e) => e.preventDefault()} onDrop={onDrop}>
          <div className="flex items-center gap-2 flex-wrap mb-3">
            {parent !== null && <button onClick={() => setParent(trail.length > 1 ? trail[trail.length - 2].id : null)} className="p-1.5 rounded hover:bg-white/10"><ArrowLeft className="size-4" /></button>}
            <div className="text-sm truncate">{trail.map((tr, i) => <span key={tr.id}>{i > 0 && <span className="text-parch/30"> / </span>}<button className="text-gold/80 hover:underline" onClick={() => setParent(tr.id)}>{tr.name}</button></span>)}{!trail.length && <span className="text-parch/50">{t('dir.welcome')}</span>}</div>
            <div className="ml-auto flex gap-2">
              <button onClick={newFolder} className="flex items-center gap-1 text-xs bg-white/5 border border-bronze/30 rounded-lg px-2.5 py-1.5 hover:bg-white/10"><FolderPlus className="size-3.5" /> {t('btn.map')}</button>
              <button onClick={newPage} className="flex items-center gap-1 text-xs bg-white/5 border border-bronze/30 rounded-lg px-2.5 py-1.5 hover:bg-white/10"><FileText className="size-3.5" /> {t('btn.page')}</button>
              <button onClick={() => upRef.current?.click()} className="flex items-center gap-1 text-xs bg-gold/90 text-ink rounded-lg px-2.5 py-1.5 font-bold hover:bg-gold"><Upload className="size-3.5" /> {t('btn.upload')}</button>
              <input ref={upRef} type="file" multiple hidden onChange={onPick} />
              <button onClick={refresh} className="p-1.5 rounded hover:bg-white/10" title={t('tip.refresh')}><RefreshCw className="size-4" /></button>
            </div>
          </div>
          <div
            onDragOver={(e) => e.preventDefault()} onDrop={onDrop}
            className="rounded-xl border border-dashed border-bronze/25 min-h-[60vh] p-1">
            {kids.length === 0 && <div className="grid place-items-center h-64 text-parch/40 text-sm">{t('dir.empty')}</div>}
            <table className="w-full text-xs">
              <tbody>
                {kids.map((n) => {
                  const I = TypeIcon(n);
                  return (
                    <tr key={n.id} className={`group cursor-pointer hover:bg-white/5 ${sel?.id === n.id ? 'bg-gold/10' : ''}`}
                      onClick={() => setSel(n)} onDoubleClick={() => n.type === 'folder' && setParent(n.id)}>
                      <td className="py-1.5 pl-2 w-6"><I className="size-4 text-gold/70" /></td>
                      <td className="py-1.5"><span className="truncate">{n.name}</span> {visBadge(n)}</td>
                      <td className="py-1.5 text-right text-parch/50 w-20">{n.size != null ? fmt(n.size) : ''}</td>
                      <td className="py-1.5 text-right text-parch/40 w-28 hidden md:table-cell">{dt(n.updated)}</td>
                      <td className="py-1.5 pr-2 w-32 text-right opacity-0 group-hover:opacity-100">
                        {n.type === 'file' && <a href={'/api/file/' + n.id} download title={t('tip.download')} className="p-1 hover:text-gold inline-block"><Download className="size-3.5" /></a>}
                        <button title={t('tip.rename')} onClick={(e) => { e.stopPropagation(); rename(n); }} className="p-1 hover:text-gold inline-block"><Pencil className="size-3.5" /></button>
                        <button title={t('tip.vis')} onClick={(e) => { e.stopPropagation(); toggleVis(n); }} className="p-1 hover:text-gold inline-block">{n.vis === 'owner' ? <Eye className="size-3.5" /> : <EyeOff className="size-3.5" />}</button>
                        <button title={t('tip.move')} onClick={(e) => { e.stopPropagation(); setMoveFrom(n); }} className="p-1 hover:text-gold inline-block"><MoveRight className="size-3.5" /></button>
                        <button title={t('tip.remove')} onClick={(e) => { e.stopPropagation(); remove(n); }} className="p-1 hover:text-red-300 inline-block"><Trash2 className="size-3.5" /></button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </main>

        {/* voorbeeld */}
        <aside className="hidden md:flex md:flex-col border-l border-bronze/20 bg-black/20 overflow-hidden">
          <Assistant onOpen={(n) => setSel(n)} />
          <div className="flex-1 overflow-auto min-h-0">
            {!sel && <p className="p-4 text-xs text-parch/40">{t('aside.hint')}</p>}
            {sel?.type === 'page' && <PageEditor node={sel} onRenamed={refresh} />}
            {sel?.type === 'file' && <FilePane n={sel} onChanged={refresh} />}
            {sel?.type === 'folder' && <FolderPane n={sel} open={() => { setParent(sel.id); setSel(null); }} />}
          </div>
        </aside>
      </div>

      {modal === 'users' && <UsersModal onClose={() => setModal(null)} />}
      {modal === 'tokens' && <TokensModal onClose={() => setModal(null)} />}
      {modal === 'dav' && <DavModal onClose={() => setModal(null)} />}
    </div>
  );
}

// ---------------------------------------------------------------- voorbeeld-ruiten

function FilePane({ n, onChanged }) {
  const { t } = useLang();
  const dt = useDt();
  const [versies, setVersies] = useState([]);
  const [coms, setComs] = useState([]);
  const [text, setText] = useState(null);
  const url = '/api/file/' + n.id;
  const ext = (n.name.split('.').pop() || '').toLowerCase();
  const isImg = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'avif'].includes(ext);
  const isPdf = ext === 'pdf';
  const isText = ['txt', 'md', 'log', 'json', 'yml', 'yaml', 'js', 'java', 'css', 'html', 'csv'].includes(ext);

  useEffect(() => {
    setVersies(null); setText(null);
    fetch('/api/versions/' + n.id).then((r) => r.json()).then((j) => setVersies(j.versions || [])).catch(() => setVersies([]));
    fetch('/api/comments/' + n.id).then((r) => r.json()).then((j) => setComs(j.comments || [])).catch(() => setComs([]));
    if (isText) fetch(url).then((r) => r.text()).then((txt) => setText(txt.slice(0, 40000))).catch(() => setText(t('fp.unreadable')));
  }, [n.id]); // eslint-disable-line

  async function restore(v) {
    await jsend('/api/versions/' + n.id, 'POST', { version: v });
    onChanged();
    fetch('/api/versions/' + n.id).then((r) => r.json()).then((j) => setVersies(j.versions || []));
  }
  async function addComment(t2) {
    if (!t2.trim()) return;
    await jsend('/api/comments/' + n.id, 'POST', { text: t2 });
    setComs(await (await fetch('/api/comments/' + n.id)).json().then((j) => j.comments || []));
  }
  return (
    <div className="p-3 space-y-3 text-xs">
      <h3 className="text-gold font-bold text-sm truncate" title={n.name}>{n.name}</h3>
      {isImg && <img src={url} alt="" className="max-h-72 w-full object-contain rounded-lg border border-bronze/30 bg-black/30" />}
      {isPdf && <iframe src={url} title="pdf" className="h-72 w-full rounded-lg border border-bronze/30 bg-white" />}
      {isText && !isPdf && <pre className="max-h-72 overflow-auto bg-black/40 border border-bronze/20 rounded-lg p-2 whitespace-pre-wrap">{text ?? '…'}</pre>}
      {!isImg && !isPdf && !isText && <div className="text-parch/50">{t('fp.nopreview')}</div>}
      <div className="flex gap-2">
        <a href={url} download className="flex items-center gap-1 bg-gold/90 text-ink rounded-lg px-2.5 py-1.5 font-bold"><Download className="size-3.5" /> {t('fp.download')}</a>
      </div>
      <section>
        <h4 className="flex items-center gap-1 text-parch/70 font-bold mb-1"><History className="size-3.5" /> {t('fp.versions')}</h4>
        <ul className="space-y-1 max-h-40 overflow-auto">
          {(versies || []).map((v, i) => (
            <li key={v.id} className="flex items-center gap-2 bg-black/30 border border-bronze/20 rounded px-2 py-1">
              <span className="text-parch/60">{dt(v.created)}</span><span>{fmt(v.size)}</span><span className="text-parch/40">{v.who}</span>
              {i === 0 ? <span className="ml-auto text-emerald-300/80">{t('fp.current')}</span>
                : <><a href={url + '?v=' + v.id} download className="ml-auto text-gold/80 hover:underline">{t('fp.fetch')}</a>
                  <button onClick={() => restore(v.id)} className="text-copper hover:underline">{t('fp.restore')}</button></>}
            </li>
          ))}
        </ul>
      </section>
      <Comments id={n.id} coms={coms} add={addComment} del={async (cid) => { await jsend('/api/comments/' + n.id + '?cid=' + cid, 'DELETE'); setComs(await (await fetch('/api/comments/' + n.id)).json().then((j) => j.comments || [])); }} />
    </div>
  );
}

function FolderPane({ n, open }) {
  const { t } = useLang();
  return (
    <div className="p-3 text-xs space-y-3">
      <h3 className="text-gold font-bold text-sm flex items-center gap-2"><Folder className="size-4" /> {n.name}</h3>
      <button onClick={open} className="bg-white/5 border border-bronze/30 rounded-lg px-2.5 py-1.5 hover:bg-white/10">{t('fp.open')}</button>
      <p className="text-parch/40">{t('fp.vis')} {n.vis === 'owner' ? t('vis.owner') : n.vis === 'team' ? `team ${n.team || '?'}` : n.vis === 'all' ? t('vis.all') : t('vis.inherit')}</p>
    </div>
  );
}

function Comments({ coms, add, del }) {
  const { t } = useLang();
  const dt = useDt();
  const [c, setC] = useState('');
  return (
    <section>
      <h4 className="text-parch/70 font-bold mb-1">{t('com.title')}</h4>
      <ul className="space-y-1 mb-2">
        {(coms || []).map((cm) => (
          <li key={cm.id} className="bg-black/30 border border-bronze/20 rounded px-2 py-1.5">
            <div className="flex gap-2 text-[10px] text-parch/50"><b className="text-gold/80">{cm.who}</b>{dt(cm.created)}<button onClick={() => del(cm.id)} className="ml-auto hover:text-red-300">×</button></div>
            <p className="whitespace-pre-wrap">{cm.text}</p>
          </li>
        ))}
      </ul>
      <div className="flex gap-1">
        <input value={c} onChange={(e) => setC(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { add(c); setC(''); } }} placeholder={t('com.ph')}
          className="flex-1 bg-black/40 border border-bronze/30 rounded px-2 py-1.5 text-xs outline-none focus:border-gold" />
        <button onClick={() => { add(c); setC(''); }} className="bg-white/5 border border-bronze/30 rounded px-2 hover:bg-white/10"><Plus className="size-3.5" /></button>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- modals

function Modal({ title, onClose, children, wide }) {
  return (
    <div className="fixed inset-0 bg-black/70 grid place-items-center z-50 p-4" onClick={onClose}>
      <div className={`bg-panel border border-bronze/40 rounded-2xl p-4 w-full ${wide ? 'max-w-2xl' : 'max-w-md'} max-h-[80vh] overflow-auto`} onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-bold text-gold">{title}</h3>
          <button onClick={onClose}><X className="size-4" /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

function UsersModal({ onClose }) {
  const { t } = useLang();
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ username: '', password: '', team: '', role: 'staff' });
  const [msg, setMsg] = useState('');
  useEffect(() => { fetch('/api/admin/users').then((r) => r.json()).then((j) => setUsers(j.users || [])); }, []);
  async function add() {
    setMsg('');
    try { await jsend('/api/admin/users', 'POST', form); setForm({ ...form, username: '', password: '' }); setUsers(await (await fetch('/api/admin/users')).json().then((j) => j.users || [])); }
    catch (e) { setMsg(e.message); }
  }
  async function del(u) { if (!confirm(t('usr.confirm', { name: u.username }))) return; await jsend('/api/admin/users?id=' + u.id, 'DELETE'); setUsers(await (await fetch('/api/admin/users')).json().then((j) => j.users || [])); }
  return (
    <Modal title="Accounts" onClose={onClose}>
      <div className="space-y-2 text-xs mb-4">
        {users.map((u) => (
          <div key={u.id} className="flex items-center gap-2 bg-black/30 border border-bronze/20 rounded px-2 py-1.5">
            <b className="text-parch">{u.username}</b><span className="text-parch/50">{u.role}</span><span className="text-copper">{u.team || ''}</span>
            <button onClick={() => del(u)} className="ml-auto hover:text-red-300"><Trash2 className="size-3.5" /></button>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-2 gap-2 text-xs">
        <input placeholder={t('usr.ph_user')} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5" />
        <input placeholder={t('usr.ph_pass')} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5" />
        <input placeholder={t('usr.ph_team')} value={form.team} onChange={(e) => setForm({ ...form, team: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5" />
        <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5">
          <option value="staff">staff</option><option value="owner">owner</option>
        </select>
        <button onClick={add} className="col-span-2 bg-gold/90 text-ink rounded-lg py-1.5 font-bold">{t('usr.create')}</button>
      </div>
      {msg && <p className="text-red-300 text-xs mt-2">{msg}</p>}
    </Modal>
  );
}

function TokensModal({ onClose }) {
  const { t } = useLang();
  const dt = useDt();
  const [tokens, setTokens] = useState([]);
  const [fresh, setFresh] = useState('');
  useEffect(() => { fetch('/api/tokens').then((r) => r.json()).then((j) => setTokens(j.tokens || [])); }, []);
  async function create() {
    const label = window.prompt(t('tok.prompt')) || 'token';
    const j = await jsend('/api/tokens', 'POST', { label });
    setFresh(j.token);
    setTokens(await (await fetch('/api/tokens')).json().then((x) => x.tokens || []));
  }
  async function del(tk) { await jsend('/api/tokens?token=' + encodeURIComponent(tk), 'DELETE'); setTokens(await (await fetch('/api/tokens')).json().then((j) => j.tokens || [])); }
  return (
    <Modal title={t('tip.tokens')} onClose={onClose}>
      <p className="text-xs text-parch/60 mb-2">{t('tok.intro')}</p>
      {fresh && <div className="bg-emerald-900/30 border border-emerald-500/40 rounded-lg p-2 text-xs mb-3 font-mono break-all select-all">{fresh}<button onClick={() => navigator.clipboard?.writeText(fresh)} className="ml-2 underline">{t('tok.copy')}</button></div>}
      <div className="space-y-2 text-xs mb-3">
        {tokens.map((tk) => (
          <div key={tk.token} className="flex items-center gap-2 bg-black/30 border border-bronze/20 rounded px-2 py-1.5">
            <b>{tk.label}</b><span className="font-mono text-parch/50">{tk.hint}</span><span className="text-parch/40 ml-auto">{dt(tk.created)}</span>
            <button onClick={() => del(tk.token)} className="hover:text-red-300"><Trash2 className="size-3.5" /></button>
          </div>
        ))}
      </div>
      <button onClick={create} className="w-full bg-gold/90 text-ink rounded-lg py-1.5 font-bold text-xs">{t('tok.new')}</button>
    </Modal>
  );
}

function DavModal({ onClose }) {
  const { t } = useLang();
  const host = typeof window !== 'undefined' ? window.location.host : 'staff.iekcorbar.com';
  const url = `https://${host}/dav`;
  return (
    <Modal title={t('dav.title')} onClose={onClose} wide>
      <div className="text-xs space-y-3 text-parch/80">
        <p>{t('dav.intro_a')}<b>{t('dav.explorer')}</b>{t('dav.intro_b')}<b>{t('dav.finder')}</b>{t('dav.intro_c')}<b>{t('dav.token')}</b>{t('dav.intro_d')}</p>
        <div>
          <h4 className="text-gold font-bold mb-1">{t('dav.win')}</h4>
          <p>{t('dav.win_a')}<b>{t('dav.win_b')}</b>{t('dav.win_c')}<i>{t('dav.win_d')}</i>{t('dav.win_e')}</p>
          <code className="block bg-black/40 border border-bronze/30 rounded p-2 my-1 break-all select-all">{url}</code>
          <p>{t('dav.win_f')}</p>
          <code className="block bg-black/40 border border-bronze/30 rounded p-2 my-1 break-all select-all">{`net use H: ${url} /user:jouwnaam JOUW-TOKEN`}</code>
        </div>
        <div>
          <h4 className="text-gold font-bold mb-1">{t('dav.mac')}</h4>
          <p>{t('dav.mac_a')}<i>{t('dav.mac_b')}</i>{t('dav.mac_c')}<code className="select-all">{url}</code>{t('dav.mac_d')}</p>
        </div>
        <div>
          <h4 className="text-gold font-bold mb-1">{t('dav.phone')}</h4>
          <p>{t('dav.phone_a')}<i>{t('dav.phone_b')}</i>{t('dav.phone_c')}<code>{url}</code>{t('dav.phone_d')}</p>
        </div>
        <p className="text-parch/50">{t('dav.note')}</p>
      </div>
    </Modal>
  );
}
