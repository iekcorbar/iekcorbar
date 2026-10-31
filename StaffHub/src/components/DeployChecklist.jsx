'use client';
import { useEffect, useState } from 'react';
import { ClipboardList, CheckCircle, Circle, Package } from 'lucide-react';

export default function DeployChecklist({ onClose }) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');

  async function load() {
    try {
      const r = await fetch('/api/deploy');
      const j = await r.json();
      setData(j);
    } catch (e) { setErr(e.message); }
  }

  useEffect(() => { load(); }, []);

  async function toggle(item) {
    await fetch('/api/deploy', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ id: item.id, done: !item.done }),
    });
    load();
  }

  if (err) return <div className="text-red-400 p-4">{err}</div>;
  if (!data) return <div className="text-gold/60 p-4">Laden...</div>;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <ClipboardList size={24} /> Deploy Checklist
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        <div className="mb-4 text-sm text-gray-400">
          {data.done} / {data.total} afgevinkt
        </div>

        <div className="grid gap-2">
          {data.items.map((item) => (
            <div key={item.id} className={`rounded-lg px-4 py-3 border ${item.done ? 'bg-green-900/20 border-green-500/30' : 'bg-white/5 border-white/10'}`}>
              <div className="flex items-start gap-3">
                <button onClick={() => toggle(item)} className="mt-1">
                  {item.done ? <CheckCircle size={20} className="text-green-400" /> : <Circle size={20} className="text-gray-500" />}
                </button>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-gold font-mono text-xs">#{item.num}</span>
                    <span className="font-semibold text-white">{item.plugin} {item.version}</span>
                  </div>
                  <div className="text-sm text-gray-300 mt-1">{item.action}</div>
                  {item.test && <div className="text-xs text-gray-500 mt-1">Test: {item.test}</div>}
                  {item.done && item.done_by && (
                    <div className="text-xs text-green-400 mt-1">✓ {item.done_by} — {new Date(item.done_at).toLocaleString()}</div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}