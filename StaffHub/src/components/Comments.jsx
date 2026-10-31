'use client';
import { useEffect, useState } from 'react';
import { MessageSquare, Send, Trash2 } from 'lucide-react';

export default function Comments({ node, onClose }) {
  const [comments, setComments] = useState([]);
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');

  async function load() {
    try {
      const r = await fetch('/api/comments/' + node.id);
      const j = await r.json();
      setComments(j.comments || []);
    } catch (e) { setErr(e.message); }
    setLoading(false);
  }

  useEffect(() => { load(); }, [node.id]);

  async function submit() {
    if (!text.trim()) return;
    try {
      await fetch('/api/comments/' + node.id, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ text }),
      });
      setText('');
      load();
    } catch (e) { setErr(e.message); }
  }

  async function remove(cid) {
    if (!confirm('Reactie verwijderen?')) return;
    await fetch('/api/comments/' + node.id + '?cid=' + cid, { method: 'DELETE' });
    load();
  }

  if (loading) return <div className="text-gold/60 p-4">Laden...</div>;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <MessageSquare size={24} /> Reacties
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        {err && <p className="text-red-400 text-sm mb-3">{err}</p>}

        {/* Comment list */}
        <div className="grid gap-3 mb-4 max-h-96 overflow-y-auto">
          {comments.length === 0 ? (
            <p className="text-gray-500 text-sm">Nog geen reacties.</p>
          ) : (
            comments.map((c) => (
              <div key={c.id} className="bg-white/5 rounded-lg px-4 py-3">
                <div className="flex justify-between items-start">
                  <div className="text-xs text-gold/70 mb-1">{c.who} {c.team ? `(${c.team})` : ''} — {new Date(c.created).toLocaleString()}</div>
                  <button onClick={() => remove(c.id)} className="text-gray-500 hover:text-red-400"><Trash2 size={14} /></button>
                </div>
                <p className="text-sm text-gray-300">{c.text}</p>
              </div>
            ))
          )}
        </div>

        {/* New comment */}
        <div className="flex gap-2">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Schrijf een reactie..."
            className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white resize-none"
            rows={2}
          />
          <button onClick={submit} className="px-3 py-2 bg-gold/20 text-gold rounded-lg hover:bg-gold/30">
            <Send size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}