'use client';

// Handleiding-bewerker: TipTap met een kompakte werkbalk. Afbeeldingen worden
// naar dezelfde map ge-upload en als /api/file-URL ingevoegd.
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import { useEffect, useRef, useState } from 'react';
import { Bold, Italic, Strikethrough, Heading1, Heading2, Heading3, List, ListOrdered, Quote, Code, Undo, Redo, Link2, ImagePlus, Save } from 'lucide-react';
import { useLang, trServer } from '@/lib/i18n';
import { GidsWeergave } from '@/components/Vertaal';
import TagSuggester from '@/components/TagSuggester';

const BTN = 'p-1.5 rounded hover:bg-white/10 text-parch/70';

export default function PageEditor({ node, onRenamed }) {
  const { t, locale } = useLang();
  const [dirty, setDirty] = useState(false);
  const [savedAt, setSavedAt] = useState(null);
  const [err, setErr] = useState('');
  const imgRef = useRef();
  // Zet tijdens het INLADEN van een document op true: setContent zelf vuurt
  // ook onUpdate af, en zonder deze vlag zou een vers geopend document meteen
  // als "niet opgeslagen" boven de knop staan.
  const loading = useRef(true);
  // Taal-tabbladen boven een handleiding. `taal === null` is de BRON (die is te
  // bewerken); een gekozen taal is een bestaande vertaling en dus alleen te
  // lezen. Zo hoeft TipTap niet op "alleen-lezen" te schakelen en kan er niets
  // per ongeluk in een vertaling worden teruggezet.
  const [varianten, setVarianten] = useState([]);
  const [taal, setTaal] = useState(null);
  const [vertaald, setVertaald] = useState('');
  const [deeld, setDeeld] = useState(false);   // korte bevestiging na een deel-link

  const editor = useEditor({
    extensions: [StarterKit, Link.configure({ openOnClick: false }), Image],
    content: '',
    editable: true,
    // Puur client-side component, dus de editor meteen neerzetten in plaats van
    // in een effect: zonder deze regel draait de allereerste render ZONDER
    // editorinstance en was het laad-effect hieronder te vroeg.
    immediatelyRender: true,
    onUpdate: () => { if (!loading.current) setDirty(true); },
  });

  // Document laden. `editor` staat in de deps: is hij in deze render nog niet
  // klaar, dan draait dit effect opnieuw zodra hij er wél is. Voorheen werd het
  // document wél opgehaald en `editor.commands.setContent(...)` vervolgens
  // tegen een null-editor aangelopen, waarna een lege .catch(() => {}) de fout
  // inslikte: het eerste aanklikken van een handleiding leeg, en pas na een
  // paar keer rondklikken (nieuw effect, editor intussen klaar) verscheen hij.
  useEffect(() => {
    if (!editor) return undefined;
    let live = true;
    loading.current = true;
    setDirty(false); setErr(''); setTaal(null);
    fetch('/api/page/' + node.id)
      .then((r) => r.json().then((j) => ({ ok: r.ok, j })).catch(() => ({ ok: false, j: null })))
      .then(({ ok, j }) => {
        if (!live) return;
        if (!ok || j?.error) { setErr(trServer(j?.error || 'mislukt')); return; }
        setVarianten(j.varianten || []);
        editor.commands.setContent(j.html || '<h1>' + node.name + '</h1><p></p>');
        setDirty(false);
      })
      .catch(() => { if (live) setErr(t('err.load')); })
      .finally(() => { loading.current = false; });
    return () => { live = false; };
  }, [node.id, editor]); // eslint-disable-line

  // De gekozen vertaling halen op. Apart effect: de editor blijft gewoon op de
  // bron staan, dit is alleen wat er in de ruit getoond wordt.
  useEffect(() => {
    if (!taal) { setVertaald(''); return undefined; }
    let live = true;
    fetch('/api/page/' + node.id + '?lang=' + encodeURIComponent(taal))
      .then((r) => (r.ok ? r.json() : { html: '' }))
      .then((j) => { if (live) setVertaald(j.html || ''); })
      .catch(() => { if (live) setVertaald(''); });
    return () => { live = false; };
  }, [node.id, taal]);

  // Seintje uit het AI-bord: er is zojuist een vertaling bijgekomen, dus de
  // tabbladen moeten ververst - anders moet men eerst een andere map in en uit.
  useEffect(() => {
    const op = () => fetch('/api/page/' + node.id)
      .then((r) => (r.ok ? r.json() : {}))
      .then((j) => { if (j.varianten) setVarianten(j.varianten); })
      .catch(() => { });
    window.addEventListener('staffhub:vertaling', op);
    return () => window.removeEventListener('staffhub:vertaling', op);
  }, [node.id]);

  async function save() {
    try {
      await fetch('/api/page/' + node.id, { method: 'PUT', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ html: editor.getHTML() }) });
      setDirty(false); setSavedAt(new Date()); setErr('');
      onRenamed?.();
    } catch (e) { setErr(t('ed.err_save') + ': ' + e.message); }
  }

  useEffect(() => {
    const h = (e) => { if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); save(); } };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }); // eslint-disable-line

  async function insertImage(file) {
    const fd = new FormData();
    if (node.parent) fd.set('parent', String(node.parent));
    fd.append('files', file, file.name);
    const j = await (await fetch('/api/upload', { method: 'POST', body: fd })).json();
    const made = (j.made || [])[0];
    if (made?.id) editor.chain().focus().setImage({ src: '/api/file/' + made.id, alt: made.name }).run();
  }

  function addLink() {
    const prev = editor.getAttributes('link').href;
    const url = window.prompt(t('ed.link'), prev || 'https://');
    if (url === null) return;
    if (url === '') { editor.chain().focus().extendMarkRange('link').unsetLink().run(); return; }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  }

  if (!editor) return null;
  const I = ({ icon: Ic, ...p }) => <button type="button" {...p}><Ic className="size-4" /></button>;

  // Deel-knopje: plakt ?open=<id>-link van DIT document op het klembord. Een
  // handleiding had geen eigen URL - "kijk eens in de deploy-gids" was toen een
  // rondklik-workout. De link werkt voor iedereen met rechten; ACL checkt de
  // server, niet de link.
  async function deelLink() {
    const url = window.location.origin + window.location.pathname + '?open=' + node.id;
    try { await navigator.clipboard.writeText(url); }
    catch {
      const t = window.prompt('URL', url);      // klembord dicht (http, old webkit): handmatig
      if (t === null) return;
    }
    setDeeld(true); setTimeout(() => setDeeld(false), 2500);
  }
  const deelKnop = (
    <button onClick={deelLink} title={t('tok.copy')} aria-label={t('tok.copy')}
      className="p-1.5 rounded hover:bg-white/10 text-parch/60 shrink-0">
      {deeld ? <span className="text-[10px] text-emerald-300 font-bold px-1">✓</span> : <Link2 className="size-4" />}
    </button>
  );

  // Tabstrip: potlood = de bron (bewerkbaar), taalcodes = een bestaande
  // vertaling (alleen lezen). ⚠ = het origineel is later nog bewerkt.
  const tabbalk = varianten.length > 0 && (
    <div className="px-3 pt-2 flex items-center gap-1 flex-wrap">
      <button onClick={() => setTaal(null)} title="✎"
        className={`rounded px-2 py-0.5 text-[11px] border ${!taal ? 'bg-gold/20 border-gold/50 text-gold' : 'bg-white/5 border-bronze/30 text-parch/60 hover:border-gold/60'}`}>✎</button>
      {varianten.map((v) => (
        <button key={v.lang} onClick={() => setTaal(v.lang)} title={new Date(v.updated).toISOString().slice(0, 10) + (v.verouderd ? ' ⚠' : '')}
          className={`rounded px-2 py-0.5 text-[11px] border ${taal === v.lang ? 'bg-gold/20 border-gold/50 text-gold' : 'bg-white/5 border-bronze/30 text-parch/60 hover:border-gold/60'}`}>
          {String(v.lang).toUpperCase()}{v.verouderd ? ' ⚠' : ''}
        </button>
      ))}
    </div>
  );

  // Lees-stand: eigen.return zónder werkbalk en zónder opslaanknop. Die knop
  // schrijft namelijk altíjd naar de BRON - wie in een vertaling zou "bewerken"
  // en opslaan, overschrijft het origineel met een vertaling. Dat mag niet kunnen.
  if (taal) return (
    <div className="flex flex-col h-full">
      <div className="px-3 pt-3 flex items-center gap-2">
        <h3 className="text-sm font-bold text-gold truncate flex-1">{node.name}</h3>
        <span className="text-[10px] text-parch/45">{taal.toUpperCase()}</span>
        {deelKnop}
      </div>
      {tabbalk}
      <div className="flex-1 overflow-auto px-3 py-2">
        <GidsWeergave html={vertaald} klas="min-h-full" />
      </div>
    </div>
  );

  return (
    <div className="flex flex-col h-full">
      <div className="px-3 pt-3 flex items-center gap-2">
        <h3 className="text-sm font-bold text-gold truncate flex-1">{node.name}</h3>
        {deelKnop}
        <button onClick={save} className={`flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-bold ${dirty ? 'bg-gold/90 text-ink' : 'bg-white/5 text-parch/50 border border-bronze/30'}`}>
          <Save className="size-3.5" /> {dirty ? t('ed.save') : t('ed.saved')}
        </button>
      </div>
      {tabbalk}
      <div className="px-3 pt-2 flex flex-wrap gap-0.5 border-b border-bronze/20">
        <I icon={Bold} className={`${BTN} ${editor.isActive('bold') ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleBold().run()} />
        <I icon={Italic} className={`${BTN} ${editor.isActive('italic') ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleItalic().run()} />
        <I icon={Strikethrough} className={BTN} onClick={() => editor.chain().focus().toggleStrike().run()} />
        <span className="w-px bg-bronze/20 mx-1" />
        <I icon={Heading1} className={`${BTN} ${editor.isActive('heading', { level: 1 }) ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()} />
        <I icon={Heading2} className={`${BTN} ${editor.isActive('heading', { level: 2 }) ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} />
        <I icon={Heading3} className={`${BTN} ${editor.isActive('heading', { level: 3 }) ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} />
        <span className="w-px bg-bronze/20 mx-1" />
        <I icon={List} className={`${BTN} ${editor.isActive('bulletList') ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleBulletList().run()} />
        <I icon={ListOrdered} className={`${BTN} ${editor.isActive('orderedList') ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleOrderedList().run()} />
        <I icon={Quote} className={`${BTN} ${editor.isActive('blockquote') ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleBlockquote().run()} />
        <I icon={Code} className={`${BTN} ${editor.isActive('code') ? 'text-gold' : ''}`} onClick={() => editor.chain().focus().toggleCode().run()} />
        <span className="w-px bg-bronze/20 mx-1" />
        <I icon={Link2} className={`${BTN} ${editor.isActive('link') ? 'text-gold' : ''}`} onClick={addLink} />
        <I icon={ImagePlus} className={BTN} onClick={() => imgRef.current?.click()} />
        <input ref={imgRef} type="file" accept="image/*" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) insertImage(f); e.target.value = ''; }} />
        <span className="w-px bg-bronze/20 mx-1" />
        <I icon={Undo} className={BTN} onClick={() => editor.chain().focus().undo().run()} />
        <I icon={Redo} className={BTN} onClick={() => editor.chain().focus().redo().run()} />
      </div>
      {err && <p className="text-red-300 text-xs px-3 pt-2">{err}</p>}
      {node?.id && <div className="px-3 pt-2"><TagSuggester node={node.id} text={editor?.getText() || ''} name={node.name || ''} /></div>}
      <div className="flex-1 overflow-auto px-3 py-2">
        <EditorContent editor={editor} className="prose-hub min-h-full" />
      </div>
      <p className="text-[9px] text-parch/30 px-3 pb-2">{savedAt ? t('ed.foot_saved', { time: savedAt.toLocaleTimeString(locale) }) : t('ed.foot_edit')}{t('ed.foot_hint')}</p>
    </div>
  );
}
