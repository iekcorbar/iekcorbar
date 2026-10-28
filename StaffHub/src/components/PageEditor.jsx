'use client';

// Handleiding-bewerker: TipTap met een kompakte werkbalk. Afbeeldingen worden
// naar dezelfde map ge-upload en als /api/file-URL ingevoegd.
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import { useEffect, useRef, useState } from 'react';
import { Bold, Italic, Strikethrough, Heading1, Heading2, Heading3, List, ListOrdered, Quote, Code, Undo, Redo, Link2, ImagePlus, Save } from 'lucide-react';
import { useLang } from '@/lib/i18n';

const BTN = 'p-1.5 rounded hover:bg-white/10 text-parch/70';

export default function PageEditor({ node, onRenamed }) {
  const { t, locale } = useLang();
  const [dirty, setDirty] = useState(false);
  const [savedAt, setSavedAt] = useState(null);
  const [err, setErr] = useState('');
  const imgRef = useRef();

  const editor = useEditor({
    extensions: [StarterKit, Link.configure({ openOnClick: false }), Image],
    content: '',
    editable: true,
    onUpdate: () => setDirty(true),
  });

  useEffect(() => {
    let live = true;
    setDirty(false); setErr('');
    fetch('/api/page/' + node.id).then((r) => r.json()).then((j) => {
      if (!live) return;
      editor.commands.setContent(j.html || '<h1>' + node.name + '</h1><p></p>');
      setDirty(false);
    }).catch(() => { });
    return () => { live = false; };
  }, [node.id]); // eslint-disable-line

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

  return (
    <div className="flex flex-col h-full">
      <div className="px-3 pt-3 flex items-center gap-2">
        <h3 className="text-sm font-bold text-gold truncate flex-1">{node.name}</h3>
        <button onClick={save} className={`flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-bold ${dirty ? 'bg-gold/90 text-ink' : 'bg-white/5 text-parch/50 border border-bronze/30'}`}>
          <Save className="size-3.5" /> {dirty ? t('ed.save') : t('ed.saved')}
        </button>
      </div>
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
      <div className="flex-1 overflow-auto px-3 py-2">
        <EditorContent editor={editor} className="prose-hub min-h-full" />
      </div>
      <p className="text-[9px] text-parch/30 px-3 pb-2">{savedAt ? t('ed.foot_saved', { time: savedAt.toLocaleTimeString(locale) }) : t('ed.foot_edit')}{t('ed.foot_hint')}</p>
    </div>
  );
}
