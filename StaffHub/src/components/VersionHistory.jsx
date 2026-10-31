'use client';
import { useEffect, useState } from 'react';
import { History, RotateCcw, Eye, X } from 'lucide-react';

export default function VersionHistory({ node, onClose, onRestore }) {
  const [versions, setVersions] = useState([]);
  const [selected, setSelected] = useState(null);
  const [diff, setDiff] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const r = await fetch('/api/page/' + node.id + '/versions');
        const j = await r.json();
        setVersions(j.versions || []);
      } catch (e) { /* ignore */ }
      setLoading(false);
    }
    load();
  }, [node.id]);

  async function viewVersion(v) {
    const r = await fetch('/api/page/' + node.id + '/versions/' + v.id);
    const j = await r.json();
    setSelected(j);
  }

  async function restore(v) {
    if (!confirm('Terugdraaien naar deze versie?')) return;
    await fetch('/api/page/' + node.id, {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ html: selected?.html }),
    });
    if (onRestore) onRestore();
    onClose();
  }

  async function saveVersion() {
    await fetch('/api/page/' + node.id + '/versions', { method: 'POST' });
    // Reload
    const r = await fetch('/api/page/' + node.id + '/versions');
    const j = await r.json();
    setVersions(j.versions || []);
  }

  if (loading) return <div className="text-gold/60 p-4">Laden...</div>;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-3xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <History size={24} /> Versiegeschiedenis
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        <div className="mb-4">
          <button onClick={saveVersion} className="px-3 py-1.5 bg-gold/20 text-gold rounded hover:bg-gold/30 text-sm">
            Huidige versie opslaan
          </button>
        </div>

        {versions.length === 0 ? (
          <p className="text-gray-500 text-sm">Nog geen versies opgeslagen. Klik op "Huidige versie opslaan" om een snapshot te maken.</p>
        ) : (
          <div className="grid gap-2">
            {versions.map((v) => (
              <div key={v.id} className="bg-white/5 rounded-lg px-4 py-3 flex items-center justify-between">
                <div>
                  <div className="text-sm text-white">
                    Versie #{v.id} — {new Date(v.created).toLocaleString()}
                  </div>
                  <div className="text-xs text-gray-500">
                    door {v.who || 'onbekend'} — {v.size} tekens
                  </div>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => viewVersion(v)} className="p-1.5 rounded hover:bg-white/10 text-gold/70" title="Bekijken">
                    <Eye size={16} />
                  </button>
                  <button onClick={() => restore(v)} className="p-1.5 rounded hover:bg-white/10 text-gold/70" title="Terugdraaien">
                    <RotateCcw size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Version preview */}
        {selected && (
          <div className="mt-6 border-t border-white/10 pt-4">
            <div className="flex justify-between items-center mb-2">
              <h3 className="text-lg font-semibold text-gold/80">Versie #{selected.id}</h3>
              <button onClick={() => setSelected(null)} className="text-gold/60 hover:text-gold"><X size={18} /></button>
            </div>
            <div className="bg-white/5 rounded-lg p-4 max-h-60 overflow-y-auto text-sm text-gray-300" dangerouslySetInnerHTML={{ __html: selected.html }} />
          </div>
        )}
      </div>
    </div>
  );
}