'use client';
import { useRef, useState } from 'react';
import { Upload, FileText } from 'lucide-react';

export default function MarkdownImport({ parent, onDone, onClose }) {
  const fileRef = useRef();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function upload() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setBusy(true);
    setErr('');
    try {
      const fd = new FormData();
      fd.append('file', file);
      if (parent) fd.append('parent', parent);
      const r = await fetch('/api/import-md', { method: 'POST', body: fd });
      const j = await r.json();
      if (j.error) throw new Error(j.error);
      if (onDone) onDone(j);
      if (onClose) onClose();
    } catch (e) { setErr(e.message); }
    setBusy(false);
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-md w-full p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <Upload size={24} /> Markdown Importeren
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        {err && <p className="text-red-400 text-sm mb-3">{err}</p>}

        <div className="mb-4">
          <label className="block text-sm text-gray-400 mb-2">Kies een .md bestand:</label>
          <input ref={fileRef} type="file" accept=".md,.txt,.markdown" className="block w-full text-sm text-gray-300 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:bg-gold/20 file:text-gold hover:file:bg-gold/30" />
        </div>

        <button
          onClick={upload}
          disabled={busy}
          className="w-full px-4 py-2 bg-gold/20 text-gold rounded-lg hover:bg-gold/30 disabled:opacity-50"
        >
          {busy ? 'Importeren...' : 'Importeren'}
        </button>
      </div>
    </div>
  );
}