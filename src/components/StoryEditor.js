'use client';

import { useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import {
  Bold, Italic, Strikethrough, List, ListOrdered, Link as LinkIcon, Undo, Redo,
  Heading2, Heading3, Heading4, Quote, Minus, BookMarked
} from 'lucide-react';

// Story Room chapter/lore editor — same Tiptap setup as RichTextEditor.js
// (MIT core, output limited to the nodes its own schema defines, so pasted
// markup can't smuggle in scripts) with the extra blocks long-form writing
// needs (H2-H4, quotes, dividers) and a "Lore link" picker that inserts
// [[Name]] for an entry from the Story Room library.
//
// Tiptap only reads `value` once, at creation. The parent remounts this
// component with a new `key` whenever the document itself changes (another
// chapter opened, a revision restored), which keeps typing free of
// cursor-jumping value echoes.
function ToolbarButton({ onClick, active, disabled, title, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      aria-label={title}
      className={`p-1.5 rounded-lg border transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
        active ? 'border-sp-gold bg-sp-gold/15 text-sp-gold' : 'border-sp-bronze/20 text-text-warm/60 hover:text-white hover:border-sp-bronze/40'
      }`}
    >
      {children}
    </button>
  );
}

export default function StoryEditor({ value, onChange, editable = true, loreNames = [], onSelectionText, minHeight = 'min-h-[420px]' }) {
  const [loreOpen, setLoreOpen] = useState(false);
  const [loreQuery, setLoreQuery] = useState('');

  const editor = useEditor({
    immediatelyRender: false,
    editable,
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3, 4] } }),
      Link.configure({ openOnClick: !editable, autolink: true })
    ],
    content: value || '',
    editorProps: {
      attributes: {
        class: `max-w-none text-sm leading-relaxed text-white focus:outline-hidden ${minHeight} px-4 py-3 font-serif ` +
          '[&_p]:my-2.5 [&_a]:text-sp-gold [&_a]:underline [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6 ' +
          '[&_h2]:text-xl [&_h2]:font-bold [&_h2]:text-sp-gold [&_h2]:mt-6 [&_h2]:mb-2 ' +
          '[&_h3]:text-lg [&_h3]:font-bold [&_h3]:text-sp-gold [&_h3]:mt-5 [&_h3]:mb-1.5 ' +
          '[&_h4]:text-base [&_h4]:font-bold [&_h4]:text-sp-copper [&_h4]:mt-4 [&_h4]:mb-1 ' +
          '[&_blockquote]:border-l-2 [&_blockquote]:border-sp-copper [&_blockquote]:pl-4 [&_blockquote]:italic [&_blockquote]:text-text-warm/80 ' +
          '[&_hr]:my-6 [&_hr]:border-sp-bronze/40'
      }
    },
    onUpdate: ({ editor }) => onChange?.(editor.getHTML()),
    onSelectionUpdate: ({ editor }) => {
      if (!onSelectionText) return;
      const { from, to } = editor.state.selection;
      onSelectionText(from === to ? '' : editor.state.doc.textBetween(from, to, ' ').trim());
    }
  });

  if (!editor) return null;

  if (!editable) {
    return <EditorContent editor={editor} />;
  }

  const setLink = () => {
    const previousUrl = editor.getAttributes('link').href;
    const url = window.prompt('Link URL', previousUrl || 'https://');
    if (url === null) return;
    if (url === '') {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  };

  const insertLore = (name) => {
    editor.chain().focus().insertContent(`[[${name}]]`).run();
    setLoreOpen(false);
    setLoreQuery('');
  };

  const q = loreQuery.trim().toLowerCase();
  const loreMatches = loreNames.filter(n => !q || n.toLowerCase().includes(q)).slice(0, 30);

  return (
    <div className="border border-sp-bronze/30 rounded-lg bg-neutral-950/60">
      <div className="flex flex-wrap items-center gap-1 border-b border-sp-bronze/20 p-1.5 sticky top-0 bg-neutral-950/95 z-10 rounded-t-lg">
        <ToolbarButton onClick={() => editor.chain().focus().toggleBold().run()} active={editor.isActive('bold')} title="Bold">
          <Bold className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleItalic().run()} active={editor.isActive('italic')} title="Italic">
          <Italic className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleStrike().run()} active={editor.isActive('strike')} title="Strikethrough">
          <Strikethrough className="w-3.5 h-3.5" />
        </ToolbarButton>
        <div className="w-px h-4 bg-sp-bronze/20 mx-1" />
        <ToolbarButton onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} active={editor.isActive('heading', { level: 2 })} title="Heading">
          <Heading2 className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} active={editor.isActive('heading', { level: 3 })} title="Subheading">
          <Heading3 className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleHeading({ level: 4 }).run()} active={editor.isActive('heading', { level: 4 })} title="Small heading">
          <Heading4 className="w-3.5 h-3.5" />
        </ToolbarButton>
        <div className="w-px h-4 bg-sp-bronze/20 mx-1" />
        <ToolbarButton onClick={() => editor.chain().focus().toggleBulletList().run()} active={editor.isActive('bulletList')} title="Bullet list">
          <List className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleOrderedList().run()} active={editor.isActive('orderedList')} title="Numbered list">
          <ListOrdered className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleBlockquote().run()} active={editor.isActive('blockquote')} title="Quote / dialogue">
          <Quote className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().setHorizontalRule().run()} title="Scene break">
          <Minus className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={setLink} active={editor.isActive('link')} title="Web link">
          <LinkIcon className="w-3.5 h-3.5" />
        </ToolbarButton>
        <div className="relative">
          <ToolbarButton onClick={() => setLoreOpen(o => !o)} active={loreOpen} title="Insert lore link [[Name]]">
            <BookMarked className="w-3.5 h-3.5" />
          </ToolbarButton>
          {loreOpen && (
            <div className="absolute left-0 top-full mt-1 w-56 bg-sp-dark border border-sp-bronze/40 rounded-lg shadow-xl p-2 z-20 space-y-1.5">
              <input
                autoFocus
                value={loreQuery}
                onChange={e => setLoreQuery(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Escape') setLoreOpen(false);
                  if (e.key === 'Enter' && loreQuery.trim()) { e.preventDefault(); insertLore(loreMatches[0] || loreQuery.trim()); }
                }}
                placeholder="Search lore..."
                className="w-full bg-neutral-950/60 border border-sp-bronze/25 text-white text-xs rounded px-2 py-1 focus:outline-hidden focus:border-sp-gold"
              />
              <div className="max-h-48 overflow-y-auto">
                {loreMatches.map(name => (
                  <button key={name} type="button" onClick={() => insertLore(name)} className="block w-full text-left text-xs text-text-warm/80 hover:text-sp-gold hover:bg-sp-gold/10 rounded px-2 py-1 cursor-pointer">
                    {name}
                  </button>
                ))}
                {loreMatches.length === 0 && (
                  <p className="text-[10px] text-text-warm/40 italic px-2 py-1">
                    {loreQuery.trim() ? <>No entry yet — press Enter to link "{loreQuery.trim()}" anyway.</> : 'The lore library is empty.'}
                  </p>
                )}
              </div>
            </div>
          )}
        </div>
        <div className="w-px h-4 bg-sp-bronze/20 mx-1" />
        <ToolbarButton onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()} title="Undo">
          <Undo className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()} title="Redo">
          <Redo className="w-3.5 h-3.5" />
        </ToolbarButton>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}
