'use client';
import { useEffect, useState } from 'react';
import { Server, Users, HardDrive, Cpu, Activity, Wifi, WifiOff, Package } from 'lucide-react';

export default function ServerStatus({ onClose }) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      const r = await fetch('/api/server-status');
      const j = await r.json();
      setData(j);
    } catch (e) { setErr(e.message); }
    setLoading(false);
  }

  useEffect(() => {
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, []);

  if (loading) return <div className="text-gold/60 p-4">Laden...</div>;
  if (err) return <div className="text-red-400 p-4">{err}</div>;
  if (!data) return null;

  const ramPct = data.ram?.percent || 0;
  const diskPct = data.disk?.percent || 0;

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <Server size={24} /> Server Status
          </h2>
          <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl">&times;</button>
        </div>

        {/* Online/Offline indicator */}
        <div className={`mb-6 p-4 rounded-lg flex items-center gap-3 ${data.online ? 'bg-green-900/20 border border-green-500/30' : 'bg-red-900/20 border border-red-500/30'}`}>
          {data.online ? <Wifi size={24} className="text-green-400" /> : <WifiOff size={24} className="text-red-400" />}
          <div>
            <div className="font-semibold text-white">{data.online ? 'Server Online' : 'Server Offline'}</div>
            {data.lastUpdate && <div className="text-xs text-gray-400">Laatste update: {new Date(data.lastUpdate).toLocaleString()}</div>}
            {data.error && <div className="text-xs text-red-400 mt-1">{data.error}</div>}
          </div>
        </div>

        {/* Stats grid */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
          <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
            <Users size={20} className="mx-auto mb-1 text-gold/70" />
            <div className="text-2xl font-bold text-gold">{data.players || 0}</div>
            <div className="text-xs text-gray-400">Spelers online</div>
          </div>
          <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
            <Activity size={20} className="mx-auto mb-1 text-gold/70" />
            <div className="text-2xl font-bold text-gold">{data.tps || 20}</div>
            <div className="text-xs text-gray-400">TPS</div>
          </div>
          <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
            <Cpu size={20} className="mx-auto mb-1 text-gold/70" />
            <div className="text-2xl font-bold text-gold">{data.cpu || 0}%</div>
            <div className="text-xs text-gray-400">CPU</div>
          </div>
          <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
            <Package size={20} className="mx-auto mb-1 text-gold/70" />
            <div className="text-2xl font-bold text-gold">{data.version || '26.2'}</div>
            <div className="text-xs text-gray-400">Minecraft</div>
          </div>
        </div>

        {/* RAM bar */}
        <div className="mb-4">
          <div className="flex justify-between text-sm mb-1">
            <span className="text-gray-400 flex items-center gap-1"><HardDrive size={14} /> RAM</span>
            <span className="text-white">{data.ram?.used || 0} / {data.ram?.total || 0} MB ({ramPct}%)</span>
          </div>
          <div className="w-full bg-white/10 rounded-full h-2">
            <div className={`h-2 rounded-full transition-all ${ramPct > 90 ? 'bg-red-500' : ramPct > 70 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${ramPct}%` }} />
          </div>
        </div>

        {/* Disk bar */}
        <div className="mb-6">
          <div className="flex justify-between text-sm mb-1">
            <span className="text-gray-400 flex items-center gap-1"><HardDrive size={14} /> Schijf</span>
            <span className="text-white">{data.disk?.used || 0} / {data.disk?.total || 0} GB ({diskPct}%)</span>
          </div>
          <div className="w-full bg-white/10 rounded-full h-2">
            <div className={`h-2 rounded-full transition-all ${diskPct > 90 ? 'bg-red-500' : diskPct > 70 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${diskPct}%` }} />
          </div>
        </div>

        {/* Plugin versions */}
        {data.plugins && data.plugins.length > 0 && (
          <section className="mb-6">
            <h3 className="text-lg font-semibold text-gold/80 mb-3">Plugin Versies</h3>
            <div className="grid gap-1">
              {data.plugins.map((p, i) => (
                <div key={i} className="flex justify-between bg-white/5 rounded px-3 py-1.5 text-sm">
                  <span className="text-white">{p.name}</span>
                  <span className="text-gold/60">v{p.version}</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* DB stats */}
        {data.db && (
          <section>
            <h3 className="text-lg font-semibold text-gold/80 mb-3">StaffHub Database</h3>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {Object.entries(data.db).map(([key, val]) => (
                <div key={key} className="bg-white/5 rounded px-3 py-2 text-center">
                  <div className="text-lg font-bold text-gold">{val}</div>
                  <div className="text-xs text-gray-400 capitalize">{key}</div>
                </div>
              ))}
            </div>
          </section>
        )}

        <div className="text-xs text-gray-600 mt-4 text-center">Auto-refresh elke 30 seconden</div>
      </div>
    </div>
  );
}