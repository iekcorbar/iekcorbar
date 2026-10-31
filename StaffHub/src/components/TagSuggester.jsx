'use client';
import { useEffect, useState } from 'react';
import { Tag, Plus, Check, X } from 'lucide-react';

export default function TagSuggester({ node, text, name, onClose }) {
  const [suggested, setSuggested] = useState([]);
  const [allTags, setAllTags] = useState([]);
  const [currentTags, setCurrentTags] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const [sRes, tRes] = await Promise.all([
          fetch('/api/tags/suggest', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ text, name }),
          }).then((r) => r.json()),
          fetch('/api/tags?node=' + (node || '')).then((r) => r.json()),
        ]);
        setSuggested(sRes.suggested || []);
        setAllTags(sRes.allTags || []);
        setCurrentTags(tRes.tags || []);
      } catch (e) { /* ignore */ }
      setLoading(false);
    }
    load();
  }, [text, name, node]);

  async function toggleTag(tagId) {
    await fetch('/api/tags', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ node, tag: tagId }),
    });
    // Reload current tags
    const r = await fetch('/api/tags?node=' + node);
    const j = await r.json();
    setCurrentTags(j.tags || []);
  }

  if (loading) return <div className="text-gold/60 p-2 text-sm">Tags laden...</div>;

  return (
    <div className="bg-white/5 rounded-lg p-3 mb-3">
      <div className="flex items-center gap-2 mb-2">
        <Tag size={16} className="text-gold/70" />
        <span className="text-sm font-semibold text-gold/80">Tags</span>
      </div>

      {/* Suggested tags */}
      {suggested.length > 0 && (
        <div className="mb-2">
          <p className="text-xs text-gray-500 mb-1">Voorgesteld:</p>
          <div className="flex flex-wrap gap-1">
            {suggested.map((t) => {
              const isApplied = currentTags.some((ct) => ct.id === t.id);
              return (
                <button
                  key={t.id}
                  onClick={() => toggleTag(t.id)}
                  className={`px-2 py-0.5 rounded-full text-xs border transition ${isApplied ? 'bg-green-500/20 border-green-500/50 text-green-300' : 'bg-gold/10 border-gold/30 text-gold hover:bg-gold/20'}`}
                >
                  {isApplied ? <Check size={10} className="inline mr-1" /> : <Plus size={10} className="inline mr-1" />}
                  {t.name}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* All tags */}
      {allTags.length > suggested.length && (
        <div>
          <p className="text-xs text-gray-500 mb-1">Alle tags:</p>
          <div className="flex flex-wrap gap-1">
            {allTags.filter((t) => !suggested.some((s) => s.id === t.id)).map((t) => {
              const isApplied = currentTags.some((ct) => ct.id === t.id);
              return (
                <button
                  key={t.id}
                  onClick={() => toggleTag(t.id)}
                  className={`px-2 py-0.5 rounded-full text-xs border transition ${isApplied ? 'bg-green-500/20 border-green-500/50 text-green-300' : 'bg-white/5 border-white/10 text-gray-400 hover:bg-white/10'}`}
                >
                  {isApplied ? <Check size={10} className="inline mr-1" /> : <Plus size={10} className="inline mr-1" />}
                  {t.name}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Current tags */}
      {currentTags.length > 0 && (
        <div className="mt-2 pt-2 border-t border-white/10">
          <p className="text-xs text-gray-500 mb-1">Toegepast:</p>
          <div className="flex flex-wrap gap-1">
            {currentTags.map((t) => (
              <span key={t.id} className="px-2 py-0.5 rounded-full text-xs bg-green-500/20 border border-green-500/50 text-green-300">
                {t.name}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}