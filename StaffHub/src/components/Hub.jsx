'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Anchor, Folder, FolderPlus, FileText, File as FileIcon, Image as ImageIcon, Download, Trash2,
  Pencil, MoveRight, Search, LogOut, Upload, Lock, Globe, Users, KeyRound, History, Server, ArrowLeft,
  RefreshCw, Eye, EyeOff, Plus, X, UserCog, Shield, RotateCcw, Bot, ChevronRight, BarChart3, ClipboardList, Star, MessageSquare,
} from 'lucide-react';
import PageEditor from '@/components/PageEditor';
import Dashboard from '@/components/Dashboard';
import DeployChecklist from '@/components/DeployChecklist';
import Navigation from '@/components/Navigation';
import VersionHistory from '@/components/VersionHistory';
import ServerStatus from '@/components/ServerStatus';
import MarkdownImport from '@/components/MarkdownImport';
import { MediaPlayer, Model3D, mediaKind } from '@/components/Media';
import Assistant from '@/components/Assistant';
import Vertaal from '@/components/Vertaal';
import { useLang, useDt, trServer, LangToggle, LANGS, setLeesbaar, leesbaarStand } from '@/lib/i18n';
import { may, CAPS, PRIV, effectieveCaps } from '@/lib/caps';

const fmt = (n) => n == null ? '' : n > 1e9 ? (n / 1e9).toFixed(1) + ' GB' : n > 1e6 ? (n / 1e6).toFixed(1) + ' MB' : n > 1000 ? (n / 1000).toFixed(0) + ' KB' : n + ' B';

async function jget(url) { const r = await fetch(url); if (r.status === 401) throw new Error('login'); return (await r.json()).error ? null : r.json(); }
async function jsend(url, method, body) { const r = await fetch(url, { method, headers: body ? { 'content-type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined }); const j = await r.json().catch(() => ({})); if (!r.ok) throw new Error(trServer(j.error) || trServer('mislukt')); return j; }

const ICONS = { folder: Folder, page: FileText, file: FileIcon };

export default function Hub({ me, onLogout }) {
  const { t, lang, setLang } = useLang();
  const dt = useDt();
  const [parent, setParent] = useState(null);
  const [trail, setTrail] = useState([]);
  const [kids, setKids] = useState([]);
  const [sel, setSel] = useState(null);           // geopend document (popup)
  const [kiesBestemming, setKiesBestemming] = useState(false);  // mapkiezer voor verplaatsen
  const [q, setQ] = useState('');
  const [results, setResults] = useState(null);
  const [modal, setModal] = useState(null);       // 'users' | 'admin' | 'tokens' | 'dav' | 'desktop' | 'profiel' | 'ai'
  const [moveFrom, setMoveFrom] = useState(null);  // node die verplaatst wordt
  const [err, setErr] = useState('');
  const [archief, setArchief] = useState(null);     // {plan, file} van een zip-dry-run
  const [aan, setAan] = useState(new Set());        // bulk-selectie: node-ids
  const [ton, setTon] = useState({ id: null, items: [] });   // prullenbak (id + inhoud met herkomst)
  const [laatste, setLaatste] = useState(null);     // {tekst, herstel:fn} voor de undo-balk
  const upRef = useRef();

  const load = useCallback(async (pid) => {
    try {
      const j = await (await fetch('/api/tree?parent=' + (pid ?? ''))).json();
      if (j.error) return;
      setParent(j.parent); setTrail(j.trail); setKids(j.kids);
    } catch (e) { setErr(t('err.load') + ': ' + e.message); }
  }, [t]);
  useEffect(() => { load(parent); }, [parent, load]);
// Track recent views
useEffect(() => {
  if (sel?.id) {
    fetch('/api/recent', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ node: sel.id }) }).catch(() => {});
  }
}, [sel?.id]);

  // ---------- prullenbak + bulk-selectie ----------
  async function verversTon() {
    try { const j = await (await fetch('/api/trash')).json(); if (j && j.id) setTon({ id: j.id, items: j.items || [] }); } catch { }
  }
  useEffect(() => { verversTon(); }, []);                 // map bestaat nog niet? GET maakt 'm
  useEffect(() => { setAan(new Set()); }, [parent]);      // andere map = schone lei voor de selectie
  const isTon = parent != null && parent === ton.id;
  function toggleSel(id) { const s = new Set(aan); if (s.has(id)) s.delete(id); else s.add(id); setAan(s); }
  function allesSel() {
    const s = new Set(aan);
    const alles = stuk.length > 0 && stuk.every((k) => s.has(k.id));
    for (const k of stuk) { if (alles) s.delete(k.id); else s.add(k.id); }
    setAan(s);
  }
  function laatUndo(l) { setLaatste(l); setTimeout(() => setLaatste((v) => (v === l ? null : v)), 15000); }
  async function trashActie(actie, ids) {
    try {
      const j = await jsend('/api/trash', 'POST', { actie, ids });
      setAan(new Set());
      await refresh(); await verversTon();
      return j;
    } catch (e) { setErr(e.message); return null; }
  }
  async function prullen(ids) {
    if (!ids.length) return;
    const j = await trashActie('prullen', ids);
    if (j?.gedaan) laatUndo({ tekst: t('msg.prull', { n: j.gedaan }), herstel: () => trashActie('herstel', ids) });
  }
  async function legeTon() {
    if (!window.confirm(t('confirm.lege'))) return;
    await trashActie('legenen', []);
  }

  // Gedeelde link: ?open=<id> opent één handleiding direct, ook voor iemand die
  // net inlogt en geen idee heeft in welke map iets staat. Vóór dit stukje was
  // "kijk eens in de deploy-gids" alleen te zeggen als iemand ook rondklikte.
  // Het blad wordt hierna schoongeveegd met replaceState, zodat een herladen
  // niet eindeloos hetzelfde venster terugzet.
  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('open'));
    if (!id) return;
    (async () => {
      try {
        const r = await fetch('/api/nodes/' + id);
        if (r.ok) {
          const n = (await r.json()).node;
          if (n.type === 'folder') setParent(n.id);
          else { if (n.parent != null) setParent(n.parent); setSel(n); }
        }
      } catch { /* kapotte link: gewoon de hub zoals altijd */ }
      const p = new URLSearchParams(window.location.search);
      p.delete('open');
      window.history.replaceState(null, '', window.location.pathname + (p.toString() ? '?' + p : ''));
    })();
  }, []);

  async function refresh() { await load(parent); }

  // ---------- taal = accountvoorzetting ----------
  // De keus hoort bij de persoon, niet bij de browser: opslaan in users.prefs
  // en bij het openen van de hub terughalen. Zo ziet een staff-lid op élk
  // toestel dezelfde taal - en het AI-bord volgt diezelfde voorkeur.
  async function kiesTaal(l) {
    setLang(l);
    try { await jsend('/api/profile', 'PATCH', { prefs: { taal: l } }); } catch { }
  }
  // Leesbare tekst: dezelfde accountvoorzetting, andere knop. De reden houdt
  // iedereen voor zich - het staat alleen als 'leesbaar' in de rij.
  async function kiesLeesbaar(aan) {
    setLeesbaar(aan);
    try { await jsend('/api/profile', 'PATCH', { prefs: { leesbaar: aan } }); } catch { }
  }
  useEffect(() => {
    fetch('/api/profile').then((r) => (r.ok ? r.json() : {}))
      .then((j) => {
        const p = j?.user?.prefs?.taal;
        if (p && LANGS.includes(p) && p !== lang) setLang(p);
        const lb = j?.user?.prefs?.leesbaar;
        if (typeof lb === 'boolean' && lb !== leesbaarStand()) setLeesbaar(lb);
      })
      .catch(() => { });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---------- uploaden (knop + drag&drop, ook met mappen) ----------
  // Eén los .zip-archief is geen gewoon bestand: dan toont de hub eerst een
  // UITPAKPLAN (wat is nieuw, wat wordt een revisie) en beslist de mens.
  // Meerdere bestanden tegelijk of een zip tussen mappenwerk: gewoon uploaden,
  // de mens wil soms gewoon een zip als bestand bewaren.
  async function uploadFiles(list) {
    if (list.length === 1 && !list[0].path && /\.zip$/i.test(list[0].file.name || '')) {
      await arPlannen(list[0].file);
      return;
    }
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
  // Dry-run van één zip: de server antwoordt met een plan, nog is er niets
  // gewijzigd. Het plan gaat in een venster; daar pas valt de knop.
  async function arPlannen(file) {
    setErr('');
    try {
      const fd = new FormData();
      fd.set('file', file, file.name);
      if (parent) fd.set('parent', String(parent));
      const r = await fetch('/api/archive', { method: 'POST', body: fd });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) { setErr(trServer(j.error || 'archief mislukt')); return; }
      setArchief({ plan: j, file });
    } catch (e) { setErr(t('err.upload') + ': ' + e.message); }
  }
  // "Bewaar als één bestand": de weg die de hub al kende - de zip zélf de hub
  // in, niet zijn inhoud. Some staff-leden willen dat zo; beslis hen niet weg.
  async function arBewaar(file) {
    const fd = new FormData();
    if (parent) fd.set('parent', String(parent));
    fd.append('files', file, file.name);
    try { await fetch('/api/upload', { method: 'POST', body: fd }); await refresh(); }
    catch (e) { setErr(t('err.upload') + ': ' + e.message); }
    setArchief(null);
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
    // 'owner' zit alleen in de cyclus als het lid het doorlaatrecht heeft (cap ownervis).
    const volgorde = may(me, 'ownervis') ? [null, 'all', 'team', 'owner'] : [null, 'all', 'team'];
    const next = volgorde[(volgorde.indexOf(n.vis ?? null) + 1) % volgorde.length];
    try { await jsend('/api/nodes/' + n.id, 'PATCH', { vis: next }); await refresh(); } catch (e) { setErr(e.message); }
  }
  async function remove(n) {
    // Uit de prullenbak = echt weg (onomkeerbaar, dus mét bevestiging).
    // Elders = naar de prullenbak: altijd terug te draaien via de undo-balk.
    if (isTon) {
      if (!window.confirm(t('confirm.purge', { name: n.name }))) return;
      try { await jsend('/api/nodes/' + n.id, 'DELETE'); await refresh(); await verversTon(); if (sel?.id === n.id) setSel(null); } catch (e) { setErr(e.message); }
      return;
    }
    await prullen([n.id]);
  }
  async function moveTo(targetId) {
    const ids = (moveFrom.ids && moveFrom.ids.length) ? moveFrom.ids : [moveFrom.id];
    const bron = ids.map((id) => { const k = kids.find((x) => x.id === id); return { id, van: k ? k.parent : null }; });
    let ok = 0;
    for (const b of bron) {
      try { await jsend('/api/nodes/' + b.id, 'PATCH', { parent: targetId }); ok++; }
      catch (e) { setErr(e.message); }
    }
    setMoveFrom(null); setAan(new Set()); await refresh();
    if (ok) laatUndo({ tekst: t('msg.verpl', { n: ok }), herstel: async () => {
      for (const b of bron) { try { await jsend('/api/nodes/' + b.id, 'PATCH', { parent: b.van }); } catch { } }
      await refresh();
    } });
  }

  // Slepen-and-neerzetten: een node pakken en in een map lostlaten. Het oude
  // klik-en-"hierheen" blijft staan, want op een telefoon werkt HTML5-slepen
  // niet - en de app moet voor staff op de vloer gewoon blijven werken.
  const [sleepNode, setSleepNode] = useState(null);   // wat getild wordt
  const [sleepOver, setSleepOver] = useState(null);   // map waar de muis boven hangt
  async function dropNaar(mapId) {
    const node = sleepNode;
    setSleepNode(null); setSleepOver(null);
    if (!node || node.id === mapId) return;           // in zijn eigen map laten: doet niets
    const pak = aan.has(node.id) && aan.size > 1 ? [...aan].filter((id) => id !== mapId) : [node.id];
    for (const id of pak) { try { await jsend('/api/nodes/' + id, 'PATCH', { parent: mapId }); } catch (e) { setErr(e.message); } }
    setAan(new Set());
    await refresh();                                  // cyclus/rechten: server meldt het netjes
  }

  // ---------- zoeken ----------
  useEffect(() => {
    if (!q || q.length < 2) { setResults(null); return; }
    const tm = setTimeout(async () => {
      try { const j = await (await fetch('/api/search?q=' + encodeURIComponent(q))).json(); setResults(j.results || []); } catch { }
    }, 300);
    return () => clearTimeout(tm);
  }, [q]);

  const TypeIcon = (n) => (ICONS[n.type] || FileIcon);
  const visBadge = (n) => n.vis === 'owner' ? <Lock className="inline size-3 text-red-300/70" /> : n.vis === 'team' ? <span className="text-[9px] text-copper">{n.team || 'team'}</span> : n.vis === 'all' ? <Globe className="inline size-3 text-emerald-300/60" /> : null;

  // Klantkantige blik op de caps (lib/caps) - alleen voor knoppen verbergen,
  // de echte poort staat in elke API-route.
  const kan = (cap) => may(me, cap);
  const mappen = kids.filter((k) => k.type === 'folder');
  const stuk = kids.filter((k) => k.type !== 'folder');

  // Esc: eerst de diepste laag dicht (kiezer > modal > popup), nooit alles tegelijk.
  useEffect(() => {
    const opKey = (e) => {
      if (e.key !== 'Escape') return;
      if (kiesBestemming) setKiesBestemming(false);
      else if (modal) setModal(null);
      else if (sel) setSel(null);
    };
    window.addEventListener('keydown', opKey);
    return () => window.removeEventListener('keydown', opKey);
  }, [kiesBestemming, modal, sel]);

  return (
    <div className="h-screen flex flex-col">
      {/* topbalk */}
      <header className="flex items-center gap-3 px-4 py-2 border-b border-bronze/30 bg-panel">
        <img src="/icon-192.png" width="32" height="32" alt="" className="size-8 rounded-lg" />
        <h1 className="font-bold text-parch mr-2 tracking-tight">Staff<span className="text-gold">Hub</span></h1>
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2.5 top-2 size-4 text-parch/40" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('search.ph')}
            className="w-full bg-black/40 border border-bronze/30 rounded-lg pl-8 pr-3 py-1.5 text-xs outline-none focus:border-gold" />
          {results && (
            <div className="absolute top-9 left-0 right-0 bg-panel border border-bronze/40 rounded-lg shadow-2xl max-h-80 overflow-auto z-40">
              {results.length === 0 && <p className="p-3 text-xs text-parch/50">{t('search.none')}</p>}
              {results.map((r) => (
                <button key={r.id} className="w-full text-left px-3 py-2 text-xs hover:bg-white/5 flex items-center gap-2"
                  onClick={() => { setQ(''); setResults(null); if (r.type === 'folder') setParent(r.id); else setSel({ id: r.id, name: r.name, type: r.type }); }}>
                  {(() => { const I = TypeIcon(r); return <I className="size-3.5 text-gold/70" />; })()}{r.name}
                  <span className="text-parch/40 ml-auto">{t('why.' + r.why)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="ml-auto flex items-center gap-2 text-xs">
          <span className="hidden sm:inline rounded-full bg-white/5 border border-bronze/30 px-2.5 py-1 text-parch/70">{me.username}{me.team ? ` · ${me.team}` : ''}</span>
          <LangToggle vast={kiesTaal} />
          <button title={t('tip.profile')} onClick={() => setModal('profiel')} className="p-1.5 rounded hover:bg-white/10"><UserCog className="size-4" /></button>
          <button title={t('tip.desktop')} onClick={() => setModal('desktop')} className="p-1.5 rounded hover:bg-white/10"><Download className="size-4" /></button>
          {kan('tokens') && <button title={t('tip.disk')} onClick={() => setModal('dav')} className="p-1.5 rounded hover:bg-white/10"><Server className="size-4" /></button>}
          {kan('tokens') && <button title={t('tip.tokens')} onClick={() => setModal('tokens')} className="p-1.5 rounded hover:bg-white/10"><KeyRound className="size-4" /></button>}
          {kan('admin') && <button title={t('adm.titel')} onClick={() => setModal('admin')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><Shield size={18} /></button>}
<button title="Dashboard" onClick={() => setModal('dashboard')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><BarChart3 size={18} /></button>
<button title="Deploy Checklist" onClick={() => setModal('deploy')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><ClipboardList size={18} /></button>
<button title="Navigatie" onClick={() => setModal('nav')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><Star size={18} /></button>
<button title="Server Status" onClick={() => setModal('server')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><Server size={18} /></button>
<button title="Versiegeschiedenis" onClick={() => setModal('versions')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><History size={18} /></button>
<button title="Markdown Importeren" onClick={() => setModal('mdimport')} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><FileText size={18} /></button>
<button title="Sync docs" onClick={async () => { const r = await fetch('/api/sync', { method: 'POST' }); const j = await r.json(); alert('Synced ' + (j.synced || 0) + ' files'); load(parent); }} className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold"><RefreshCw size={18} /></button>
          {kan('users') && <button title={t('tip.accounts')} onClick={() => setModal('users')} className="p-1.5 rounded hover:bg-white/10"><Users className="size-4" /></button>}
          <button title={t('tip.logout')} onClick={async () => { await fetch('/api/auth/logout', { method: 'POST' }); onLogout(); }} className="p-1.5 rounded hover:bg-white/10"><LogOut className="size-4" /></button>
        </div>
      </header>

      {err && <div className="bg-red-900/40 text-red-100 text-xs px-4 py-1.5 flex items-center gap-2"><span>{err}</span><button onClick={() => setErr('')}><X className="size-3.5" /></button></div>}

      {laatste && (
        <div className="bg-emerald-900/40 text-emerald-100 text-xs px-4 py-1.5 flex items-center gap-3">
          <span>{laatste.tekst}</span>
          <button className="font-bold underline underline-offset-2 hover:text-white" onClick={async () => { const h = laatste.herstel; setLaatste(null); await h(); await verversTon(); }}>{t('undo.btn')}</button>
          <button className="ml-auto" onClick={() => setLaatste(null)}><X className="size-3.5" /></button>
        </div>
      )}

      {/* Geen zijbalk meer (eigenaar 30-10): je navigeert door mappen te openen.
          Het broodkruimel bovenaan is het kompas, de AI heeft nu een eigen,
          volle kolom aan de rechterkant. */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-[1fr_430px] overflow-hidden">
        <main className="overflow-auto p-3" onDragOver={(e) => e.preventDefault()} onDrop={onDrop}>
          <div className="flex items-center gap-2 flex-wrap mb-3">
            {parent !== null && <button onClick={() => setParent(trail.length > 1 ? trail[trail.length - 2].id : null)} className="p-1.5 rounded hover:bg-white/10"><ArrowLeft className="size-4" /></button>}
            <button onClick={() => { setParent(null); setSel(null); }} className={`p-1.5 rounded hover:bg-white/10 ${parent === null ? 'text-gold' : 'text-parch/60'}`} title={t('nav.all')}><Folder className="size-4" /></button>
            <div className="text-sm truncate">{trail.map((tr, i) => <span key={tr.id}>{i > 0 && <span className="text-parch/30"> / </span>}<button className="text-gold/80 hover:underline" onClick={() => setParent(tr.id)}>{tr.name}</button></span>)}{!trail.length && <span className="text-parch/50">{t('dir.welcome')}</span>}</div>
            <div className="ml-auto flex gap-2">
              {moveFrom && <button onClick={() => setKiesBestemming(true)} className="flex items-center gap-1 text-xs bg-copper text-ink rounded-lg px-2.5 py-1.5 font-bold hover:brightness-110"><MoveRight className="size-3.5" /> {t('nav.here')}…</button>}
              {kan('upload') && <button onClick={newFolder} className="flex items-center gap-1 text-xs bg-white/5 border border-bronze/30 rounded-lg px-2.5 py-1.5 hover:bg-white/10"><FolderPlus className="size-3.5" /> {t('btn.map')}</button>}
              {kan('pages') && <button onClick={newPage} className="flex items-center gap-1 text-xs bg-white/5 border border-bronze/30 rounded-lg px-2.5 py-1.5 hover:bg-white/10"><FileText className="size-3.5" /> {t('btn.page')}</button>}
              {kan('upload') && <button onClick={() => upRef.current?.click()} className="flex items-center gap-1 text-xs bg-gold/90 text-ink rounded-lg px-2.5 py-1.5 font-bold hover:bg-gold"><Upload className="size-3.5" /> {t('btn.upload')}</button>}
              <input ref={upRef} type="file" multiple hidden onChange={onPick} />
              {isTon && kan('purge') && kids.length > 0 && <button onClick={legeTon} className="flex items-center gap-1 text-xs bg-white/5 border border-red-300/40 text-red-200/80 rounded-lg px-2.5 py-1.5 hover:bg-red-900/30"><Trash2 className="size-3.5" /> {t('btn.lege')}</button>}
              <button onClick={refresh} className="p-1.5 rounded hover:bg-white/10" title={t('tip.refresh')}><RefreshCw className="size-4" /></button>
            </div>
          </div>
          {moveFrom && <p className="text-[11px] text-copper mb-2 px-1">{t('nav.move_hint_a')}<b>{moveFrom.name}</b>{t('nav.move_hint_b')}</p>}
          {aan.size > 0 && (
            <div className="flex items-center gap-2 flex-wrap mb-2 px-2.5 py-1.5 rounded-lg bg-gold/10 border border-gold/30 text-xs">
              <b className="text-gold">{t('sel.n', { n: aan.size })}</b>
              <button className="text-parch/60 hover:text-parch underline underline-offset-2" onClick={() => setAan(new Set())}>{t('sel.geen')}</button>
              <span className="flex-1" />
              {kan('move') && <button onClick={() => setMoveFrom({ ids: [...aan], name: t('sel.n', { n: aan.size }) })} className="flex items-center gap-1 rounded-lg border border-bronze/40 px-2 py-1 hover:border-gold/60"><MoveRight className="size-3.5" /> {t('btn.moveSel')}</button>}
              {isTon
                ? kan('trash') && <button onClick={async () => { const j = await trashActie('herstel', [...aan]); if (j?.mislukt) setErr(t('msg.herstel-fout', { n: j.mislukt })); }} className="flex items-center gap-1 rounded-lg border border-bronze/40 px-2 py-1 hover:border-gold/60"><RotateCcw className="size-3.5" /> {t('btn.herstelSel')}</button>
                : kan('trash') && <button onClick={() => prullen([...aan])} className="flex items-center gap-1 rounded-lg border border-red-300/40 text-red-200/80 px-2 py-1 hover:bg-red-900/30"><Trash2 className="size-3.5" /> {t('btn.trashSel')}</button>}
              {isTon && kan('purge') && <button onClick={async () => { if (!window.confirm(t('confirm.purgeSel', { n: aan.size }))) return; for (const id of [...aan]) { try { await jsend('/api/nodes/' + id, 'DELETE'); } catch { } } setAan(new Set()); await refresh(); await verversTon(); }} className="flex items-center gap-1 rounded-lg border border-red-300/40 text-red-300 px-2 py-1 hover:bg-red-900/40"><X className="size-3.5" /> {t('btn.purgeSel')}</button>}
            </div>
          )}

          {/* mappen als tegels: groter, met hoeveelheid erin - eerst de ruimtes,
              dan de spullen. Een klik opent de map; slepen naar een tegel verplaatst. */}
          {mappen.length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-2 mb-4">
              {mappen.map((n) => (
                <div key={n.id}
                  draggable={!moveFrom && kan('move')}
                  onDragStart={(e) => { setSleepNode(n); e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/plain', String(n.id)); } catch { } }}
                  onDragEnd={() => { setSleepNode(null); setSleepOver(null); }}
                  onDragOver={(e) => { if (sleepNode && sleepNode.id !== n.id) { e.preventDefault(); e.stopPropagation(); setSleepOver(n.id); } }}
                  onDragLeave={() => setSleepOver((v) => (v === n.id ? null : v))}
                  onDrop={(e) => { e.preventDefault(); e.stopPropagation(); dropNaar(n.id); }}
                  onClick={() => { if (moveFrom) { moveTo(n.id); return; } setParent(n.id); }}
                  className={`group rounded-xl border p-3 cursor-pointer transition-colors ${sleepOver === n.id ? 'border-gold bg-gold/15' : moveFrom ? 'border-dashed border-copper/50 bg-black/25 hover:border-gold hover:bg-gold/10' : 'border-bronze/25 bg-black/25 hover:border-gold/60 hover:bg-white/5'}`}>
                  <div className="flex items-center gap-2 min-w-0">
                    <Folder className="size-5 text-gold/80 shrink-0" />
                    <span className="truncate text-[13px] font-semibold text-parch">{n.name}</span>
                    <span className="ml-auto shrink-0">{visBadge(n)}</span>
                  </div>
                  <p className="text-[10px] text-parch/45 mt-1.5 tabular-nums">{t('dir.items', { n: n.items ?? 0 })} · {dt(n.updated)}</p>
                  {moveFrom && <p className="text-[10px] text-copper mt-1 font-bold">{t('nav.here')} →</p>}
                </div>
              ))}
            </div>
          )}

          {/* bestanden en handleidingen onder de tegels */}
          <div
            onDragOver={(e) => e.preventDefault()} onDrop={onDrop}
            className="rounded-xl border border-dashed border-bronze/25 min-h-[40vh] p-1">
            {stuk.length === 0 && mappen.length === 0 && <div className="grid place-items-center h-64 text-parch/40 text-sm">{t('dir.empty')}</div>}
            {stuk.length > 0 && (
            <table className="w-full text-[13px]">
              <thead>
                <tr className="text-[10px] uppercase tracking-wider text-parch/40">
                  <th className="w-6 pl-2 py-1.5"><button title={t('tip.selectAll')} onClick={allesSel} className="text-[13px] leading-none text-parch/50 hover:text-gold">{stuk.length > 0 && stuk.every((k) => aan.has(k.id)) ? '☑' : '☐'}</button></th>
                  <th className="text-left font-semibold py-1.5 pl-2"></th>
                  <th className="text-left font-semibold py-1.5">{t('why.naam')}</th>
                  <th className="text-right font-semibold py-1.5 w-20">{t('col.grootte')}</th>
                  <th className="text-right font-semibold py-1.5 w-36 hidden md:table-cell">{t('col.datum')}</th>
                  <th className="w-32"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {stuk.map((n) => {
                  const I = TypeIcon(n);
                  return (
                    <tr key={n.id} draggable={kan('move')}
                      onDragStart={(e) => { setSleepNode(n); e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/plain', String(n.id)); } catch { } }}
                      onDragEnd={() => { setSleepNode(null); setSleepOver(null); }}
                      className={`group cursor-pointer hover:bg-white/5 ${sel?.id === n.id ? 'bg-gold/10' : ''} ${sleepNode?.id === n.id ? 'opacity-40' : ''}`}
                      onClick={() => setSel(n)}>
                      <td className="py-2 pl-2 w-6" onClick={(e) => e.stopPropagation()}>
                        <button title={t('tip.sel')} onClick={() => toggleSel(n.id)} className={`size-3.5 rounded border inline-block align-middle ${aan.has(n.id) ? 'bg-gold border-gold' : 'border-parch/40 hover:border-gold'}`} />
                      </td>
                      <td className="py-2 w-6"><I className="size-4 text-parch/55" /></td>
                      <td className="py-2"><span className="truncate">{n.name}</span>{isTon && ton.items.find((x) => x.id === n.id) && <span className="text-[10px] text-parch/40 ml-1.5">{t('trash.was', { pad: ton.items.find((x) => x.id === n.id).thuishaven })}</span>} {visBadge(n)}</td>
                      <td className="py-2 text-right text-parch/50 w-20 tabular-nums">{n.size != null ? fmt(n.size) : ''}</td>
                      <td className="py-2 text-right text-parch/40 w-36 hidden md:table-cell tabular-nums">{dt(n.updated)}</td>
                      <td className="py-2 pr-2 w-32 text-right opacity-0 group-hover:opacity-100">
                        {n.type === 'file' && <a href={'/api/file/' + n.id} download title={t('tip.download')} className="p-1 hover:text-gold inline-block"><Download className="size-3.5" /></a>}
                        {kan('move') && <button title={t('tip.rename')} onClick={(e) => { e.stopPropagation(); rename(n); }} className="p-1 hover:text-gold inline-block"><Pencil className="size-3.5" /></button>}
                        <button title={t('tip.vis')} onClick={(e) => { e.stopPropagation(); toggleVis(n); }} className="p-1 hover:text-gold inline-block">{n.vis === 'owner' ? <Eye className="size-3.5" /> : <EyeOff className="size-3.5" />}</button>
                        {kan('move') && <button title={t('tip.move')} onClick={(e) => { e.stopPropagation(); setMoveFrom(n); }} className="p-1 hover:text-gold inline-block"><MoveRight className="size-3.5" /></button>}
                        {isTon
                          ? kan('trash') && <button title={t('tip.restore')} onClick={(e) => { e.stopPropagation(); trashActie('herstel', [n.id]); }} className="p-1 hover:text-gold inline-block"><RotateCcw className="size-3.5" /></button>
                          : kan('trash') && <button title={t('tip.trash')} onClick={(e) => { e.stopPropagation(); remove(n); }} className="p-1 hover:text-red-300 inline-block"><Trash2 className="size-3.5" /></button>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            )}
          </div>
        </main>

        {/* De AI-man: uitgebreid, de volle hoogte van de hub. Bronnen klik je
            open - het document verschijnt dan als popup voor je neus. */}
        <aside className="hidden lg:flex flex-col border-l border-bronze/20 bg-black/20 overflow-hidden">
          <Assistant onOpen={(n) => setSel(n)} />
        </aside>

        {/* Telefoon/tablet: géén vaste kolom, maar een knop die de assistent
            fullscreen opent. */}
        <button onClick={() => setModal('ai')} title={t('ai.title')}
          className="lg:hidden fixed bottom-4 right-4 z-30 flex items-center gap-2 bg-gold text-ink rounded-full px-4 py-3 shadow-2xl font-bold text-xs">
          <Bot className="size-5" /> {t('ai.title')}
        </button>
      </div>

      {/* Documenten popup: het blad vult bijna het hele scherm. Dicht met Esc,
          het kruis of een klik buiten het blad. */}
      {sel && (
        <div className="fixed inset-0 z-40 bg-black/70 p-1.5 sm:p-4 lg:p-8" onClick={() => setSel(null)}>
          <div className="mx-auto h-full w-full max-w-5xl bg-panel border border-bronze/40 rounded-2xl flex flex-col overflow-hidden shadow-2xl" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center gap-2 px-3 py-2 border-b border-bronze/25 bg-black/30 shrink-0">
              {(() => { const I = TypeIcon(sel); return <I className="size-4 text-gold shrink-0" />; })()}
              <b className="text-parch text-sm truncate" title={sel.name}>{sel.name}</b>
              <span className="shrink-0">{visBadge(sel)}</span>
              <div className="ml-auto flex items-center gap-0.5 shrink-0">
                {sel.type === 'file' && <a href={'/api/file/' + sel.id} download title={t('tip.download')} className="p-1.5 rounded hover:bg-white/10"><Download className="size-4" /></a>}
                {kan('move') && <button title={t('tip.rename')} onClick={() => rename(sel)} className="p-1.5 rounded hover:bg-white/10"><Pencil className="size-4" /></button>}
                <button title={t('tip.vis')} onClick={() => toggleVis(sel)} className="p-1.5 rounded hover:bg-white/10">{sel.vis === 'owner' ? <Eye className="size-4" /> : <EyeOff className="size-4" />}</button>
                {kan('move') && <button title={t('tip.move')} onClick={() => { setMoveFrom(sel); }} className="p-1.5 rounded hover:bg-white/10"><MoveRight className="size-4" /></button>}
                {kan('trash') && (isTon
                  ? <button title={t('tip.restore')} onClick={() => { trashActie('herstel', [sel.id]); setSel(null); }} className="p-1.5 rounded hover:bg-white/10"><RotateCcw className="size-4" /></button>
                  : <button title={t('tip.trash')} onClick={() => { remove(sel); setSel(null); }} className="p-1.5 rounded hover:text-red-300"><Trash2 className="size-4" /></button>)}
                <button onClick={() => setSel(null)} title={t('dt.close')} className="p-1.5 rounded hover:bg-white/10 ml-1"><X className="size-4" /></button>
              </div>
            </div>
            <div className="flex-1 overflow-auto min-h-0">
              {sel.type === 'page' && <Vertaal node={sel} />}
              {sel.type === 'page' && <PageEditor key={sel.id} node={sel} onRenamed={refresh} />}
              {sel.type === 'file' && <FilePane key={sel.id} n={sel} onChanged={refresh} lijst={kids.filter((k) => k.type === 'file' && mediaKind(k.name) === mediaKind(sel.name))} />}
            </div>
          </div>
        </div>
      )}

      {/* Telefoon: de assistent als fullscreen-laag (desktop heeft de vaste kolom). */}
      {modal === 'ai' && (
        <div className="lg:hidden fixed inset-0 z-40 bg-panel flex flex-col">
          <div className="flex items-center gap-2 px-3 py-2 border-b border-bronze/25 shrink-0">
            <Bot className="size-4 text-gold" /><b className="text-gold">{t('ai.title')}</b>
            <button onClick={() => setModal(null)} title={t('dt.close')} className="ml-auto p-2 rounded hover:bg-white/10"><X className="size-4" /></button>
          </div>
          <Assistant onOpen={(n) => { setModal(null); setSel(n); }} />
        </div>
      )}

      {/* Verplaatsen zonder boom: een kiezer die door de mappen bladerde. */}
      {kiesBestemming && moveFrom && (
        <MoveModal
          afbreken={() => setKiesBestemming(false)}
          kies={(id) => { setKiesBestemming(false); moveTo(id); }} />
      )}

      {modal === 'users' && <UsersModal onClose={() => setModal(null)} me={me} />}
{modal === 'dashboard' && <Dashboard onClose={() => setModal(null)} />}
{modal === 'deploy' && <DeployChecklist onClose={() => setModal(null)} />}
{modal === 'nav' && <Navigation onClose={() => setModal(null)} onOpen={(n) => { setSel(n); setModal(null); }} />}
{modal === 'versions' && sel && <VersionHistory node={sel} onClose={() => setModal(null)} onRestore={() => { /* reload */ }} />}
{modal === 'server' && <ServerStatus onClose={() => setModal(null)} />}
{modal === 'mdimport' && <MarkdownImport parent={parent?.id} onDone={() => load(parent?.id)} onClose={() => setModal(null)} />}
      {modal === 'admin' && <AdminModal onClose={() => setModal(null)} me={me} openAccounts={() => setModal('users')} />}
      {modal === 'tokens' && <TokensModal onClose={() => setModal(null)} />}
      {archief && <ArchiveModal archief={archief} sluit={() => setArchief(null)} ververst={refresh} bewaar={arBewaar} />}
      {modal === 'dav' && <DavModal onClose={() => setModal(null)} />}
      {modal === 'desktop' && <DesktopModal onClose={() => setModal(null)} />}
      {modal === 'profiel' && <ProfiModal me={me} sluit={() => setModal(null)} kiesTaal={kiesTaal} kiesLeesbaar={kiesLeesbaar} />}
    </div>
  );
}

// ---------------------------------------------------------------- voorbeeld-ruiten

function FilePane({ n, onChanged, lijst = [] }) {
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
  const kind = mediaKind(n.name);           // 'audio' | 'video' | '3d' | null

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
      {!isImg && !isPdf && !isText && !kind && <div className="text-parch/50">{t('fp.nopreview')}</div>}
      {(kind === 'audio' || kind === 'video') && <MediaPlayer kind={kind} id={n.id} naam={n.name} lijst={lijst} />}
      {kind === '3d' && <Model3D url={url} naam={n.name} groot />}
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

// Bestemmingsvenster voor verplaatsen. De oudeste boom is weg, dus verplaatsen
// bladdert nu zélf door de mappen: in een tegel klikken = erin kijken, de gouden
// knop zet de spullen in de map waar je nu staat (root meegerekend).
function MoveModal({ afbreken, kies }) {
  const { t } = useLang();
  const [pid, setPid] = useState(null);
  const [spoor, setSpoor] = useState([]);
  const [mappen, setMappen] = useState([]);
  function laad(id) {
    fetch('/api/tree?parent=' + (id ?? '')).then((r) => r.json())
      .then((j) => { if (!j.error) { setPid(id); setSpoor(j.trail || []); setMappen((j.kids || []).filter((k) => k.type === 'folder')); } })
      .catch(() => { });
  }
  useEffect(() => { laad(null); }, []); // eslint-disable-line
  return (
    <Modal title={t('mv.title')} onClose={afbreken} wide>
      <div className="flex items-center gap-1 text-xs mb-3 flex-wrap">
        {spoor.length > 0 && <button onClick={() => laad(spoor.length > 1 ? spoor[spoor.length - 2].id : null)} className="p-1 rounded hover:bg-white/10"><ArrowLeft className="size-3.5" /></button>}
        <button onClick={() => laad(null)} className={`px-1.5 py-0.5 rounded ${pid === null ? 'bg-gold/15 text-gold' : 'text-parch/60 hover:bg-white/5'}`}>{t('nav.all')}</button>
        {spoor.map((s) => <span key={s.id}><span className="text-parch/30"> / </span><button className="px-1.5 py-0.5 rounded text-gold/80 hover:underline" onClick={() => laad(s.id)}>{s.name}</button></span>)}
        <button onClick={() => kies(pid)} className="ml-auto bg-gold/90 text-ink rounded-lg px-3 py-1.5 font-bold whitespace-nowrap">{t('nav.here')} →</button>
      </div>
      {mappen.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {mappen.map((n) => (
            <button key={n.id} onClick={() => laad(n.id)} className="flex items-center gap-2 rounded-xl border border-bronze/25 bg-black/25 p-2.5 text-left hover:border-gold/60 hover:bg-white/5">
              <Folder className="size-4 text-gold/80 shrink-0" />
              <span className="truncate text-xs text-parch">{n.name}</span>
              <span className="ml-auto text-[9px] text-parch/40 shrink-0">{n.items ?? 0}</span>
            </button>
          ))}
        </div>
      )}
      {!mappen.length && <p className="text-xs text-parch/40 py-6 text-center">{t('dir.empty')}</p>}
    </Modal>
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

// Het beheerpaneel van de eigenaar (sinds 0.4.7): daar stond tot nu toe alleen
// de API voor - dit is het venster erbij. Eén opgave van /api/admin/oversicht
// geeft tellingen, opslag, alle sessies én het audit-logboek; intrekken gaat
// op rowid, want een getoond token is een ingelogd account. Alleen eigenaars
// zien dit (knop noch data: de API weigert andere rollen met 403).
function AdminModal({ onClose, me, openAccounts }) {
  const { t } = useLang();
  const dt = useDt();
  const [ov, setOv] = useState(null);
  const [fout, setFout] = useState('');
  function laad() {
    fetch('/api/admin/oversicht').then((r) => r.json())
      .then((j) => { if (j.error) setFout(trServer(j.error)); else setOv(j); })
      .catch(() => setFout(t('err.load')));
  }
  useEffect(laad, []);
  async function trek(id) {
    try { await jsend('/api/admin/oversicht?sessie=' + id, 'DELETE'); laad(); }
    catch (e) { window.alert(e.message); }
  }
  const namen = {};
  if (ov) for (const g of ov.gebruikers) namen[g.id] = g.username;
  const tegel = (lab, Waarde) => (
    <div className="rounded-lg border border-bronze/30 bg-black/30 px-2 py-1.5 text-center">
      <div className="text-base font-bold text-parch tabular-nums">{Waarde}</div>
      <div className="text-[9px] uppercase tracking-wider text-parch/45">{lab}</div>
    </div>
  );
  return (
    <Modal title={t('adm.titel')} onClose={onClose} wide>
      {fout && <p className="text-red-300 text-xs mb-2">{fout}</p>}
      {!ov && !fout && <p className="text-parch/50 text-xs mb-2">…</p>}
      {ov && (
        <div className="space-y-4 text-xs">
          <section>
            <div className="flex items-center justify-between mb-1">
              <h4 className="text-gold font-bold">{t('adm.tellingen')}</h4>
              <button onClick={laad} title={t('tip.refresh')} className="p-1 rounded hover:bg-white/10 text-parch/60"><RefreshCw className="size-3.5" /></button>
            </div>
            <div className="grid grid-cols-4 md:grid-cols-8 gap-1.5">
              {tegel(t('tip.accounts'), ov.gebruikers.length)}
              {tegel(t('type.folder'), ov.telling.mappen)}
              {tegel(t('type.page'), ov.telling.documenten)}
              {tegel(t('type.file'), ov.telling.bestanden)}
              {tegel(t('fp.versions'), ov.telling.versies)}
              {tegel(t('com.title'), ov.telling.reacties)}
              {tegel(t('tip.tokens'), ov.telling.davtokens)}
              {tegel(t('adm.opslag'), fmt(ov.opslag.bytes))}
            </div>
          </section>
          <section>
            <h4 className="text-gold font-bold mb-1">{t('adm.sessies')}</h4>
            <div className="max-h-48 overflow-auto rounded-lg border border-bronze/25 bg-black/30">
              <table className="w-full">
                <tbody>
                  {ov.sessies.map((s) => (
                    <tr key={s.id} className={`border-b border-white/5 last:border-0 ${s.verlopen ? 'opacity-40' : ''}`}>
                      <td className="px-2 py-1 font-bold text-parch/80 w-28">{namen[s.user] || s.user}</td>
                      <td className="px-2 py-1 text-parch/60 truncate" title={s.agent}>{s.agent}</td>
                      <td className="px-2 py-1 text-parch/50 whitespace-nowrap tabular-nums">{dt(s.expires)}</td>
                      <td className="px-2 py-1 text-parch/40 whitespace-nowrap">{s.verlopen ? t('adm.verlopen') : s.over + ' d'}</td>
                      {me.role === 'owner' && <td className="px-2 py-1 text-right w-8"><button onClick={() => trek(s.id)} title={t('tip.remove')} className="hover:text-red-300"><X className="size-3.5" /></button></td>}
                    </tr>
                  ))}
                  {!ov.sessies.length && <tr><td className="px-2 py-2 text-parch/40">{t('adm.leeg')}</td></tr>}
                </tbody>
              </table>
            </div>
          </section>
          <section>
            <h4 className="text-gold font-bold mb-1">{t('adm.logboek')}</h4>
            <div className="max-h-44 overflow-auto rounded-lg border border-bronze/25 bg-black/30 p-1.5 space-y-0.5">
              {(ov.logboek || []).map((l, i) => (
                <div key={i} className="flex gap-2 text-[11px]">
                  <span className="text-parch/40 tabular-nums shrink-0">{dt(l.at)}</span>
                  <span className="text-copper shrink-0">{l.area}</span>
                  <span className="text-parch/80 shrink-0">{l.actie}</span>
                  <span className="text-parch/60 shrink-0">{l.wie || '—'}</span>
                  <span className="text-parch/40 truncate" title={l.detail || ''}>{l.detail || ''}</span>
                </div>
              ))}
              {!(ov.logboek || []).length && <p className="text-parch/40 text-[11px]">{t('adm.leeg')}</p>}
            </div>
          </section>
          <button onClick={openAccounts} className="w-full bg-gold/90 text-ink rounded-lg py-1.5 font-bold">{t('adm.beheren')}</button>
        </div>
      )}
    </Modal>
  );
}

// Accounts beheren. Sinds 0.5.0 het hart van dit venster: per lid aanvinken
// wat hij mag (caps, lib/caps). Drie lagen, en de UI laat ze zien:
//   - een lid ZONDER ingestelde permissies heeft de oude staffrechten;
//     de vinkjes bij zo'n rij weergeven die stilzwijgende standaard;
//   - de eigenaar zet élk vinkje, ook de vier vorendrechten (gearceerd);
//   - een lid met cap 'users' beheert wél accounts, maar kan per definitie
//     geen vorendrechten verlenen en geen eigenaars aanraken - dat filtert
//     de API (sanitizePerms); de knoppen zijn hier al half dicht.
function UsersModal({ onClose, me }) {
  const { t } = useLang();
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ username: '', password: '', team: '', role: 'staff' });
  const [msg, setMsg] = useState('');
  const [uit, setUit] = useState(null);          // opengeklapt account
  const [wacht, setWacht] = useState({});        // id -> nieuw wachtwoord
  const [team, setTeam] = useState({});          // id -> teamtekst
  const eigenaar = me.role === 'owner';
  const magBeheren = eigenaar || may(me, 'users');
  const herhaal = () => fetch('/api/admin/users').then((r) => r.json()).then((j) => setUsers(j.users || [])).catch(() => { });
  useEffect(() => { herhaal(); }, []); // eslint-disable-line
  async function add() {
    setMsg('');
    try {
      await jsend('/api/admin/users', 'POST', form);
      setForm({ ...form, username: '', password: '' });
      await herhaal();
    } catch (e) { setMsg(e.message); }
  }
  async function del(u) {
    if (!confirm(t('usr.confirm', { name: u.username }))) return;
    try { await jsend('/api/admin/users?id=' + u.id, 'DELETE'); await herhaal(); } catch (e) { setMsg(e.message); }
  }
  async function patch(id, v) {
    setMsg('');
    try { await jsend('/api/admin/users', 'PATCH', { id, ...v }); await herhaal(); }
    catch (e) { setMsg(e.message); }
  }
  function zetCap(u, cap, aan) {
    const set = new Set(effectieveCaps(u));
    if (aan) set.add(cap); else set.delete(cap);
    patch(u.id, { perms: [...set] });
  }
  return (
    <Modal title={t('adm.beheren')} onClose={onClose} wide>
      <div className="space-y-1.5 text-xs mb-4">
        {users.map((u) => {
          const caps = new Set(effectieveCaps(u));
          const raak = eigenaar || (magBeheren && u.role !== 'owner');   // eigenaars alleen door de eigenaar
          const open = uit === u.id;
          return (
            <div key={u.id} className="rounded-lg border border-bronze/20 bg-black/30">
              <div className="flex items-center gap-2 px-2 py-1.5">
                <button disabled={!raak} onClick={() => setUit(open ? null : u.id)} className={`p-0.5 text-parch/50 ${raak ? 'hover:text-gold' : 'opacity-30'} ${u.role === 'owner' ? 'invisible' : ''}`}><ChevronRight className={`size-3.5 transition-transform ${open ? 'rotate-90' : ''}`} /></button>
                <b className="text-parch">{u.username}</b>
                <span className={`text-[10px] rounded-full px-1.5 py-0.5 ${u.role === 'owner' ? 'bg-gold/20 text-gold' : 'bg-white/5 text-parch/60'}`}>{u.role}</span>
                <span className="text-copper text-[11px]">{u.team || ''}</span>
                <span className="text-parch/40 ml-auto text-[10px]">{u.perms == null ? t('usr.standaard') : caps.size + ' ' + t('usr.caps_kort')}</span>
                {raak && <button onClick={() => del(u)} className="hover:text-red-300 p-1"><Trash2 className="size-3.5" /></button>}
              </div>
              {open && raak && (
                <div className="border-t border-white/5 px-3 py-2 space-y-3">
                  <section>
                    <h4 className="text-gold/80 font-bold mb-1">{t('usr.caps_titel')}</h4>
                    <div className="grid sm:grid-cols-2 gap-x-3 gap-y-1">
                      {CAPS.map(([cap, en]) => {
                        const prive = PRIV.has(cap);
                        const kanZetten = eigenaar || !prive;
                        return (
                          <label key={cap} className={`flex items-start gap-2 text-[11px] ${kanZetten ? 'cursor-pointer' : 'opacity-45 cursor-not-allowed'}`} title={kanZetten ? '' : t('usr.priv_only')}>
                            <input type="checkbox" className="mt-0.5 accent-gold" checked={caps.has(cap)} disabled={!kanZetten} onChange={(e) => zetCap(u, cap, e.target.checked)} />
                            <span className="text-parch/85">{t('cap.' + cap)}{prive && <span className="text-copper"> · ★</span>}<span className="block text-[9px] text-parch/40">{en}</span></span>
                          </label>
                        );
                      })}
                    </div>
                    <p className="text-[10px] text-parch/45 mt-1.5">{u.perms == null ? t('usr.perms_hint_standaard') : t('usr.perms_hint_aangepast')}</p>
                    {u.perms != null && <button onClick={() => patch(u.id, { perms: null })} className="text-[10px] text-copper underline underline-offset-2 hover:text-gold">{t('usr.perms_reset')}</button>}
                  </section>
                  <section className="flex flex-wrap items-center gap-2">
                    <input placeholder={t('usr.ph_team')} value={team[u.id] ?? u.team ?? ''} onChange={(e) => setTeam({ ...team, [u.id]: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5 w-32" />
                    <button onClick={() => patch(u.id, { team: team[u.id] ?? '' })} className="bg-white/5 border border-bronze/30 rounded px-2 py-1.5 hover:bg-white/10">{t('usr.team_set')}</button>
                    <input placeholder={t('usr.pw_ph')} type="password" value={wacht[u.id] ?? ''} onChange={(e) => setWacht({ ...wacht, [u.id]: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5 w-40" />
                    <button disabled={(wacht[u.id] || '').length < 10} onClick={async () => { await patch(u.id, { password: wacht[u.id] }); setWacht({ ...wacht, [u.id]: '' }); }}
                      className="bg-white/5 border border-bronze/30 rounded px-2 py-1.5 hover:bg-white/10 disabled:opacity-40">{t('usr.pw_set')}</button>
                  </section>
                  {eigenaar && (
                    <section className="flex items-center gap-2">
                      <span className="text-parch/60">{t('usr.role')}:</span>
                      <select value={u.role} onChange={(e) => patch(u.id, { role: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1">
                        <option value="staff">staff</option><option value="owner">owner</option>
                      </select>
                    </section>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
      <h4 className="text-gold font-bold text-xs mb-2">{t('usr.create')}</h4>
      <div className="grid grid-cols-2 gap-2 text-xs">
        <input placeholder={t('usr.ph_user')} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5" />
        <input placeholder={t('usr.ph_pass')} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5" />
        <input placeholder={t('usr.ph_team')} value={form.team} onChange={(e) => setForm({ ...form, team: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5" />
        <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} className="bg-black/40 border border-bronze/30 rounded px-2 py-1.5">
          <option value="staff">staff</option>{eigenaar && <option value="owner">owner</option>}
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
  const [sessies, setSessies] = useState([]);
  useEffect(() => {
    fetch('/api/tokens').then((r) => r.json()).then((j) => setTokens(j.tokens || []));
    fetch('/api/profile').then((r) => (r.ok ? r.json() : {})).then((j) => setSessies(j.sessies || [])).catch(() => { });
  }, []);
  async function create() {
    const label = window.prompt(t('tok.prompt')) || 'token';
    const j = await jsend('/api/tokens', 'POST', { label });
    setFresh(j.token);
    setTokens(await (await fetch('/api/tokens')).json().then((x) => x.tokens || []));
  }
  async function del(tk) { await jsend('/api/tokens?token=' + encodeURIComponent(tk), 'DELETE'); setTokens(await (await fetch('/api/tokens')).json().then((j) => j.tokens || [])); }
  // Eén toestel uitschrijven. Intrekken van HET EIGEN blad is ook toegestaan:
  // de sessierij verdwijnt en het volgende verzoek valt terug op inloggen -
  // dus sluiten we hierna direct het venster, anders hangt er een dode modal.
  async function trekSessie(s) {
    try {
      await jsend('/api/profile?sessie=' + s.id, 'DELETE');
      if (s.nu) { onClose(); return; }
      const rest = await fetch('/api/profile').then((r) => (r.ok ? r.json() : {}));
      setSessies(rest.sessies || []);
    } catch (e) { window.alert(e.message); }
  }
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

      <h4 className="text-gold font-bold text-xs mt-4 mb-1">{t('tok.sessies')}</h4>
      <div className="space-y-1.5 text-xs">
        {sessies.map((s) => (
          <div key={s.id} className="flex items-center gap-2 bg-black/30 border border-bronze/20 rounded px-2 py-1.5">
            <span className="truncate flex-1" title={s.agent}>{s.agent}</span>
            {s.nu && <span className="text-emerald-300/80 text-[10px] shrink-0">{t('tok.nu')}</span>}
            <span className="text-parch/40 shrink-0">{dt(s.tot)}</span>
            <button onClick={() => trekSessie(s)} title={t('tip.remove')} aria-label={t('tip.remove')} className="hover:text-red-300 shrink-0"><X className="size-3.5" /></button>
          </div>
        ))}
        {sessies.length === 0 && <p className="text-parch/40">…</p>}
      </div>
    </Modal>
  );
}

// Zip-uitpakvenster: eerst KIJKEN, dan pas doen. De telling bovenaan is het
// antwoord op de enge vraag ("vervang ik per ongeluk dertig handleidingen?"):
// elke lijn zegt nieuw / revisie / botsing, en bij een botsing of onduidelijk
// plan is annuleren altijd nog een keuze. Uitgepakte bestanden krijgen hun
// normale revisie-gedrag: zelfde naam = nieuwe versie, oude blijven staan.
function ArchiveModal({ archief, sluit, ververst, bewaar }) {
  const { t } = useLang();
  const p = archief.plan;
  const [bezig, setBezig] = useState('');
  const AK = {
    nieuw: { lab: t('ar.nieuw'), klas: 'text-emerald-300' },
    versie: { lab: t('ar.versie'), klas: 'text-gold' },
    botsing: { lab: t('ar.botsing'), klas: 'text-red-300' },
  };
  async function uitpakken() {
    setBezig('uit');
    try {
      const r = await fetch('/api/archive', {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ token: p.token }),
      });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(trServer(j.error) || 'mislukt');
      ververst(); sluit();
    } catch (e) { window.alert(e.message); setBezig(''); }
  }
  return (
    <Modal title={t('ar.titel')} onClose={sluit} wide>
      <p className="text-xs text-parch/70 mb-2">
        <b className="text-parch">{p.ennaam}</b>{' · '}
        {t('ar.samenvat', { n: p.aantal, grootte: fmt(p.omvang) })}
      </p>
      <div className="flex flex-wrap gap-2 text-[11px] mb-3">
        <span className="rounded bg-emerald-900/30 border border-emerald-500/30 px-2 py-0.5 text-emerald-300">{t('ar.nieuw')}: {p.nieuw}</span>
        <span className="rounded bg-gold/10 border border-gold/40 px-2 py-0.5 text-gold">{t('ar.versie')}: {p.versies}</span>
        <span className="rounded bg-white/5 border border-bronze/40 px-2 py-0.5 text-parch/70">{t('ar.map')}: {p.mappen}</span>
        {p.botsingen > 0 && <span className="rounded bg-red-900/30 border border-red-500/40 px-2 py-0.5 text-red-300">{t('ar.botsing')}: {p.botsingen}</span>}
      </div>
      <div className="max-h-64 overflow-auto rounded-lg border border-bronze/25 bg-black/40 p-2 text-[11px] space-y-0.5 mb-3">
        {(p.regels || []).map((rg) => (
          <div key={rg.pad} className="flex items-center gap-2">
            <span className={`shrink-0 w-16 ${AK[rg.actie]?.klas}`}>{AK[rg.actie]?.lab || rg.actie}</span>
            <span className="truncate flex-1 text-parch/85" title={rg.pad}>{rg.pad}</span>
            <span className="shrink-0 text-parch/40">{fmt(rg.grootte)}</span>
          </div>
        ))}
        {p.afgekapt && <p className="text-parch/40 pt-1">{t('ar.afgekapt')}</p>}
      </div>
      <div className="flex flex-wrap gap-2">
        <button onClick={uitpakken} disabled={!!bezig}
          className="flex-1 bg-gold/90 text-ink rounded-lg py-1.5 font-bold text-xs disabled:opacity-50">
          {bezig === 'uit' ? t('ar.bezig') : t('ar.uitpakken')}
        </button>
        <button onClick={() => bewaar(archief.file)} disabled={!!bezig}
          className="rounded-lg border border-bronze/40 px-3 py-1.5 text-xs text-parch/70 hover:border-gold/60 disabled:opacity-50">
          {t('ar.bewaren')}
        </button>
        <button onClick={sluit} disabled={!!bezig}
          className="rounded-lg border border-bronze/40 px-3 py-1.5 text-xs text-parch/70 hover:border-gold/60 disabled:opacity-50">
          {t('dt.close')}
        </button>
      </div>
    </Modal>
  );
}

// Het profielvenster: de account-keuzes van een staff-lid. Taal is voor nu de
// hoofdzakelijke: die volgt het persoon naar élk toestel (users.prefs) en het
// AI-bord antwoordt erin. Wachtwoord en toestellen hebben straks ook hier hun
// plek; toestellen intrekken staat nu al in het tokenscherm.
function ProfiModal({ me, sluit, kiesTaal, kiesLeesbaar }) {
  const { t, lang } = useLang();
  const [klaar, setKlaar] = useState('');
  const [leesbaarAan, zetLeesbaarAan] = useState(() => leesbaarStand());
  const INHEEMS = { nl: 'Nederlands', en: 'English', it: 'Italiano', fi: 'Suomi' };
  async function kiezen(l) {
    if (l === lang) return;
    await kiesTaal(l);
    setKlaar(l); setTimeout(() => setKlaar(''), 2200);
  }
  async function zetLeesbaar() {
    const aan = !leesbaarAan;
    zetLeesbaarAan(aan);        // knopstand meteen, de provider-regel doet kiesLeesbaar hieronder
    await kiesLeesbaar(aan);
  }
  return (
    <Modal title={t('pf.titel')} onClose={sluit}>
      <div className="flex items-center gap-2 text-xs mb-4">
        <UserCog className="size-5 text-gold" />
        <b className="text-parch">{me.username}</b>
        <span className="text-parch/50">{me.role}{me.team ? ' · ' + me.team : ''}</span>
      </div>
      <h4 className="text-gold font-bold text-xs mb-2">{t('pf.taal')}</h4>
      <div className="grid grid-cols-2 gap-2 mb-3">
        {LANGS.map((l) => (
          <button key={l} onClick={() => kiezen(l)}
            className={`rounded-lg border px-3 py-2 text-left text-xs transition-colors ${lang === l ? 'bg-gold/15 border-gold/60 text-parch' : 'bg-black/30 border-bronze/30 text-parch/70 hover:border-gold/50'}`}>
            <b>{INHEEMS[l] || l.toUpperCase()}</b>
            <span className="block text-[10px] text-parch/45">{l.toUpperCase()}{lang === l && (klaar === l ? ' · ✓ ' + t('ed.saved') : ' · ✓')}</span>
          </button>
        ))}
      </div>
      <button onClick={zetLeesbaar} className={`mt-5 w-full rounded-lg border px-3 py-2 text-left text-xs transition-colors ${leesbaarAan ? 'bg-gold/15 border-gold/60 text-parch' : 'bg-black/30 border-bronze/30 text-parch/70 hover:border-gold/50'}`}>
        <span className="flex items-center gap-2">
          <span className={`size-4 rounded grid place-items-center text-[10px] font-bold ${leesbaarAan ? 'bg-gold text-ink' : 'border border-bronze/50 text-transparent'}`}>✓</span>
          <b>{t('pf.leesbaar')}</b>
        </span>
        <span className="block text-[10px] text-parch/45 mt-1">{t('pf.leesbaar_hint')}</span>
      </button>
      <p className="text-[11px] text-parch/50 mt-3">{t('pf.hint')}</p>
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

/** "StaffHub als app": Windows = de installateur van de server; Mac/iOS/Linux
 *  = dezelfde website als losse app (Dock/beginscherm/adresbalk-menu), want
 *  electron-builder kan die platformen vanaf Windows niet meebouwen. Zonder
 *  installateur op de server zegt de knop dat zelf - geen dode download. */
function DesktopModal({ onClose }) {
  const { t } = useLang();
  const dt = useDt();
  const [info, setInfo] = useState(null);
  useEffect(() => {
    fetch('/api/desktop?info=1')
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => j && setInfo(j))
      .catch(() => { });
  }, []);
  const rij = (naam, a, b, noot) => (
    <div className="bg-black/30 border border-bronze/20 rounded-lg px-3 py-2">
      <h4 className="text-gold font-bold mb-0.5">{naam}</h4>
      <p>{a}<b>{b}</b></p>
      {noot && <p className="text-parch/40 text-[10px] mt-1">{noot}</p>}
    </div>
  );
  return (
    <Modal title={t('dt.title')} onClose={onClose} wide>
      <div className="text-xs space-y-2 text-parch/80">
        <p className="text-parch/60">{t('dt.web')}</p>
        <div className="bg-black/30 border border-bronze/20 rounded-lg px-3 py-2">
          <h4 className="text-gold font-bold mb-0.5">{t('dt.win')}</h4>
          {info ? (
            <div className="flex items-center gap-2 flex-wrap">
              <a href="/api/desktop" download className="flex items-center gap-1 bg-gold/90 text-ink rounded-lg px-2.5 py-1.5 font-bold hover:bg-gold"><Download className="size-3.5" /> {t('dt.fetch')}{info.version ? ` ${info.version}` : ''}</a>
              <span className="text-parch/50">{fmt(info.size)} · {t('dt.updated')} {dt(info.updated)}</span>
            </div>
          ) : <p className="text-parch/50">{t('dt.none')}</p>}
          <p className="text-parch/40 text-[10px] mt-1">{t('dt.win_note')}</p>
        </div>
        {rij(t('dt.mac'), t('dt.mac_a'), t('dt.mac_b'))}
        {rij(t('dt.ios'), t('dt.ios_a'), t('dt.ios_b'))}
        {rij(t('dt.linux'), t('dt.linux_a'), t('dt.linux_b'))}
      </div>
    </Modal>
  );
}
