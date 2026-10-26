'use client';

import { useEffect } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import { Bold, Italic, List, ListOrdered, Link as LinkIcon, Undo, Redo, Heading2 } from 'lucide-react';

// Free, open-source WYSIWYG editor (Tiptap — MIT-licensed core, no paywall
// for anything used here) for the newsletter compose form. Deliberately
// small: StarterKit's bold/italic/headings/lists/paragraphs plus a Link
// extension — no images, tables, or arbitrary embeds, so the output HTML
// stays within a fixed, known set of tags email-templates.js's
// inlineEmailStyles() can safely style for real email clients. Safe by
// construction against injected markup: Tiptap only ever emits nodes its
// own schema defines, so getHTML() can't contain a <script> tag or similar
// even if someone pastes raw HTML into it.
function ToolbarButton({ onClick, active, disabled, title, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`p-1.5 rounded-lg border transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
        active ? 'border-sp-gold bg-sp-gold/15 text-sp-gold' : 'border-sp-bronze/20 text-text-warm/60 hover:text-white hover:border-sp-bronze/40'
      }`}
    >
      {children}
    </button>
  );
}

export default function RichTextEditor({ value, onChange, placeholder }) {
  const editor = useEditor({
    immediatelyRender: false, // avoids an SSR/client hydration mismatch warning
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3] } }),
      Link.configure({ openOnClick: false, autolink: true })
    ],
    content: value || '',
    editorProps: {
      attributes: {
        class: 'prose-invert max-w-none text-xs text-white focus:outline-hidden min-h-[160px] px-3 py-2 [&_p]:my-2 [&_a]:text-sp-gold [&_a]:underline [&_ul]:list-disc [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:pl-5 [&_h2]:text-sm [&_h2]:font-bold [&_h2]:text-sp-gold [&_h3]:text-xs [&_h3]:font-bold [&_h3]:text-sp-gold'
      }
    },
    onUpdate: ({ editor }) => onChange(editor.getHTML())
  });

  // Tiptap only reads `content` once, at creation — it isn't a normal
  // controlled input, so an external reset of `value` (e.g. clearing the
  // form after a successful send) wouldn't otherwise reach the editor at
  // all. Only push it in when it actually differs from what's already
  // there, or every keystroke's onUpdate would immediately get echoed back
  // in and fight the user's cursor position.
  useEffect(() => {
    if (!editor) return;
    if (value !== editor.getHTML()) {
      editor.commands.setContent(value || '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, editor]);

  if (!editor) return null;

  const setLink = () => {
    const previousUrl = editor.getAttributes('link').href;
    const url = window.prompt('Link URL', previousUrl || 'https://');
    if (url === null) return; // cancelled
    if (url === '') {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  };

  return (
    <div className="border border-sp-bronze/30 rounded-lg bg-neutral-950/60 overflow-hidden">
      <div className="flex flex-wrap items-center gap-1 border-b border-sp-bronze/20 p-1.5">
        <ToolbarButton onClick={() => editor.chain().focus().toggleBold().run()} active={editor.isActive('bold')} title="Bold">
          <Bold className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleItalic().run()} active={editor.isActive('italic')} title="Italic">
          <Italic className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} active={editor.isActive('heading', { level: 2 })} title="Heading">
          <Heading2 className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleBulletList().run()} active={editor.isActive('bulletList')} title="Bullet list">
          <List className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().toggleOrderedList().run()} active={editor.isActive('orderedList')} title="Numbered list">
          <ListOrdered className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={setLink} active={editor.isActive('link')} title="Link">
          <LinkIcon className="w-3.5 h-3.5" />
        </ToolbarButton>
        <div className="w-px h-4 bg-sp-bronze/20 mx-1" />
        <ToolbarButton onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()} title="Undo">
          <Undo className="w-3.5 h-3.5" />
        </ToolbarButton>
        <ToolbarButton onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()} title="Redo">
          <Redo className="w-3.5 h-3.5" />
        </ToolbarButton>
      </div>
      <EditorContent editor={editor} placeholder={placeholder} />
    </div>
  );
}
