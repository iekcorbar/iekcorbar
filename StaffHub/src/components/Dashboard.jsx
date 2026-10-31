'use client';
import { useEffect, useState } from 'react';
import { Server, Package, ClipboardList, BarChart3, CheckCircle, Clock } from 'lucide-react';

export default function Dashboard({ onClose }) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    fetch('/api/dashboard').then((r) => r.json()).then(setData).catch((e) => setErr(e.message));
  }, []);

  if (err) return <div className="text-red-400 p-4">{err}</div>;
  if (!data) return <div className="text-gold/60 p-4">Laden...</div>;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <BarChart3 size={24} /> Dashboard &amp; Overzicht
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        {/* Plugin versions */}
        <section className="mb-6">
          <h3 className="text-lg font-semibold text-gold/80 mb-3 flex items-center gap-2">
            <Package size={18} /> Plugin Versies
          </h3>
          <div className="grid gap-2">
            {data.plugins.map((p) => (
              <div key={p.name} className="flex items-center justify-between bg-white/5 rounded-lg px-4 py-2">
                <div>
                  <span className="font-semibold text-white">{p.name}</span>
                  <span className="ml-2 text-gold/60 text-sm">v{p.version}</span>
                </div>
                <div className="text-sm text-gray-400">{p.notes}</div>
              </div>
            ))}
          </div>
        </section>

        {/* Deploy checklist */}
        <section className="mb-6">
          <h3 className="text-lg font-semibold text-gold/80 mb-3 flex items-center gap-2">
            <ClipboardList size={18} /> Deploy Checklist
          </h3>
          <div className="grid gap-2">
            {data.deploy.map((item) => (
              <div key={item.id} className="bg-white/5 rounded-lg px-4 py-3">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-gold font-mono text-xs">#{item.id}</span>
                  <span className="font-semibold text-white">{item.plugin} {item.version}</span>
                </div>
                <div className="text-sm text-gray-300">{item.action}</div>
                <div className="text-xs text-gray-500 mt-1">Test: {item.test}</div>
              </div>
            ))}
          </div>
        </section>

        {/* Stats */}
        <section>
          <h3 className="text-lg font-semibold text-gold/80 mb-3 flex items-center gap-2">
            <Server size={18} /> Statistieken
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
            {Object.entries(data.stats).map(([key, val]) => (
              <div key={key} className="bg-white/5 rounded-lg px-4 py-3 text-center">
                <div className="text-2xl font-bold text-gold">{val}</div>
                <div className="text-xs text-gray-400 capitalize">{key}</div>
              </div>
            ))}
          </div>
          <div className="text-xs text-gray-600 mt-3">Gegenereerd: {new Date(data.generated).toLocaleString()}</div>
        </section>
      </div>
    </div>
  );
}