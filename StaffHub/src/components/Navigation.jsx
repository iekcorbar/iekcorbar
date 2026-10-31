'use client';
import { useEffect, useState } from 'react';
import { Star, Clock, Tag, X } from 'lucide-react';

export default function Navigation({ onOpen, onClose }) {
  const [favorites, setFavorites] = useState([]);
  const [recent, setRecent] = useState([]);
  const [tags, setTags] = useState([]);
  const [err, setErr] = useState('');

  async function load() {
    try {
      const [fRes, rRes, tRes] = await Promise.all([
        fetch('/api/favorites').then((r) => r.json()),
        fetch('/api/recent').then((r) => r.json()),
        fetch('/api/tags').then((r) => r.json()),
      ]);
      setFavorites(fRes.favorites || []);
      setRecent(rRes.recent || []);
      setTags(tRes.tags || []);
    } catch (e) { setErr(e.message); }
  }

  useEffect(() => { load(); }, []);

  if (err) return <div className="text-red-400 p-4">{err}</div>;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <Star size={24} /> Navigatie &amp; Bladwijzers
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        {/* Favorites */}
        <section className="mb-6">
          <h3 className="text-lg font-semibold text-gold/80 mb-3 flex items-center gap-2">
            <Star size={18} /> Favorieten
          </h3>
          {favorites.length === 0 ? (
            <p className="text-gray-500 text-sm">Nog geen favorieten. Klik op de ster bij een document.</p>
          ) : (
            <div className="grid gap-1">
              {favorites.map((f) => (
                <button key={f.id} onClick={() => { onOpen(f); onClose(); }} className="text-left px-3 py-2 rounded hover:bg-white/10 text-white text-sm">
                  {f.name}
                </button>
              ))}
            </div>
          )}
        </section>

        {/* Recent */}
        <section className="mb-6">
          <h3 className="text-lg font-semibold text-gold/80 mb-3 flex items-center gap-2">
            <Clock size={18} /> Recent bekeken
          </h3>
          {recent.length === 0 ? (
            <p className="text-gray-500 text-sm">Nog geen recent bekeken documenten.</p>
          ) : (
            <div className="grid gap-1">
              {recent.map((r) => (
                <button key={r.id} onClick={() => { onOpen(r); onClose(); }} className="text-left px-3 py-2 rounded hover:bg-white/10 text-white text-sm">
                  {r.name}
                </button>
              ))}
            </div>
          )}
        </section>

        {/* Tags */}
        <section>
          <h3 className="text-lg font-semibold text-gold/80 mb-3 flex items-center gap-2">
            <Tag size={18} /> Tags
          </h3>
          {tags.length === 0 ? (
            <p className="text-gray-500 text-sm">Nog geen tags aangemaakt.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {tags.map((t) => (
                <span key={t.id} className="px-3 py-1 rounded-full text-sm" style={{ backgroundColor: t.color + '33', color: t.color, border: `1px solid ${t.color}` }}>
                  {t.name}
                </span>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}