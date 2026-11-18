'use client';
import { useEffect, useState } from 'react';
import { BarChart3, RefreshCw, Eye, Users, Download, Smartphone, Bell, BellOff, Globe } from 'lucide-react';

// Website-/app-analyse, doorgeliezen via /api/site-analytics naar de site.
// Het oude Analytics-tabblad van het website-staffpaneel, hierher verhuisd
// omdat staff-gereedschap in StaffHub hoort (spelerswebsite = voor spelers).

const RANGES = ['7', '30', '90'];
const fmt = (n) => new Intl.NumberFormat('nl-NL').format(n || 0);
const dag = (d) => (d ? new Date(d).toLocaleDateString('nl-NL', { day: 'numeric', month: 'short' }) : '—');

function Bars({ daily, metric, color }) {
  if (!daily?.length) return null;
  const max = Math.max(1, ...daily.map((d) => d[metric] || 0));
  const w = 100 / daily.length;
  return (
    <svg viewBox="0 0 100 30" preserveAspectRatio="none" className="w-full h-24">
      {daily.map((d, i) => {
        const h = ((d[metric] || 0) / max) * 28;
        return <rect key={i} x={i * w + w * 0.15} width={w * 0.7} y={30 - h} height={h} fill={color} opacity="0.8" />;
      })}
    </svg>
  );
}

function List({ title, rows, valueKey = 'views' }) {
  if (!rows?.length) return null;
  return (
    <div className="bg-white/5 rounded-lg p-3">
      <div className="text-xs font-bold text-gold/80 uppercase mb-2">{title}</div>
      {rows.slice(0, 8).map((r, i) => (
        <div key={i} className="flex justify-between text-xs text-gray-300 py-0.5 border-b border-white/5 last:border-0">
          <span className="truncate pr-2">{r.label || '—'}</span>
          <span className="text-gold shrink-0">{fmt(r[valueKey])}</span>
        </div>
      ))}
    </div>
  );
}

export default function SiteAnalytics({ onClose }) {
  const [days, setDays] = useState('30');
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(true);
  const [updated, setUpdated] = useState(null);

  async function load(d = days) {
    setLoading(true);
    try {
      const r = await fetch(`/api/site-analytics?days=${d}`);
      const j = await r.json();
      if (j.error) setErr(j.error); else { setErr(''); setData(j); setUpdated(new Date()); }
    } catch (e) { setErr(e.message); }
    setLoading(false);
  }
  useEffect(() => { load(days); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [days]);

  const t = data?.totals || {};

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
      <div className="bg-[#1a1a2e] border border-gold/30 rounded-xl max-w-5xl w-full max-h-[90vh] overflow-y-auto p-6">
        <div className="flex justify-between items-center mb-4 gap-2 flex-wrap">
          <h2 className="text-2xl font-bold text-gold flex items-center gap-2">
            <BarChart3 size={24} /> Website-analyse
          </h2>
          <div className="flex items-center gap-2">
            {RANGES.map((r) => (
              <button key={r} onClick={() => setDays(r)}
                className={`px-3 py-1 rounded-lg text-xs font-bold ${days === r ? 'bg-gold text-black' : 'bg-white/10 text-gold/70 hover:text-gold'}`}>
                {r} dagen
              </button>
            ))}
            <button onClick={() => load()} title="Verversen" className="p-1.5 rounded hover:bg-white/10 text-gold/70 hover:text-gold">
              <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
            </button>
            <button onClick={onClose} className="text-gold/60 hover:text-gold text-2xl ml-2">&times;</button>
          </div>
        </div>
        <div className="text-[11px] text-gray-500 mb-4 flex items-center gap-1">
          <Globe size={12} /> Cijfers van iekcorbar.com + de app (5 min cache)
          {updated && <> · opgehaald {updated.toLocaleTimeString('nl-NL')}</>}
        </div>

        {err && <div className="mb-4 p-3 rounded-lg bg-red-900/20 border border-red-500/30 text-red-300 text-sm">{err}</div>}
        {loading && !data && <div className="text-gold/60 p-4">Laden...</div>}

        {data && (
          <>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
              <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
                <Eye size={18} className="mx-auto mb-1 text-gold/70" />
                <div className="text-xl font-bold text-gold">{fmt(t.pageviews)}</div>
                <div className="text-xs text-gray-400">Paginaweergaven</div>
              </div>
              <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
                <Users size={18} className="mx-auto mb-1 text-gold/70" />
                <div className="text-xl font-bold text-gold">{fmt(t.activePlayers)}</div>
                <div className="text-xs text-gray-400">Actieve spelers</div>
              </div>
              <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
                <Smartphone size={18} className="mx-auto mb-1 text-gold/70" />
                <div className="text-xl font-bold text-gold">{fmt(t.appUsers)}</div>
                <div className="text-xs text-gray-400">App-gebruikers</div>
              </div>
              <div className="bg-white/5 rounded-lg px-4 py-3 text-center">
                <Download size={18} className="mx-auto mb-1 text-gold/70" />
                <div className="text-xl font-bold text-gold">{fmt(t.downloads)}</div>
                <div className="text-xs text-gray-400">App-downloads</div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-6">
              <div className="bg-white/5 rounded-lg p-3">
                <div className="text-xs font-bold text-gold/80 uppercase mb-1">Bezoekers per dag</div>
                <Bars daily={data.daily} metric="visitors" color="#d4af37" />
                <div className="flex justify-between text-[10px] text-gray-500">
                  <span>{dag(data.daily?.[0]?.day)}</span>
                  <span>vandaag {fmt(t.visitorsToday)}</span>
                  <span>{dag(data.daily?.[data.daily.length - 1]?.day)}</span>
                </div>
              </div>
              <div className="bg-white/5 rounded-lg p-3">
                <div className="text-xs font-bold text-gold/80 uppercase mb-1">Weergaven per dag</div>
                <Bars daily={data.daily} metric="pageviews" color="#b87333" />
                <div className="flex justify-between text-[10px] text-gray-500">
                  <span>{dag(data.daily?.[0]?.day)}</span>
                  <span>vandaag {fmt(t.pageviewsToday)}</span>
                  <span>{dag(data.daily?.[data.daily.length - 1]?.day)}</span>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-6">
              <List title="Meest bezochte pagina's" rows={data.topPages} />
              <List title="Platforms" rows={data.platforms} />
              <List title="Browsers" rows={data.browsers} />
              <List title="Besturingssystemen" rows={data.oses} />
              <List title="Verwijzers" rows={data.referrers} />
              <List title="App-versies" rows={data.appVersions} valueKey="users" />
            </div>

            <div className="bg-white/5 rounded-lg p-3">
              <div className="text-xs font-bold text-gold/80 uppercase mb-2 flex items-center gap-2">
                Spelersactiviteit
                <span className="normal-case font-normal text-gray-500">
                  <Bell size={10} className="inline" /> {fmt(t.androidPushPlayers)} android-push · {fmt(t.webPushDevices)} web-push
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-left text-gold/60 border-b border-white/10">
                      <th className="py-1 pr-2">Speler</th>
                      <th className="py-1 pr-2">Weergaven</th>
                      <th className="py-1 pr-2">Platforms</th>
                      <th className="py-1 pr-2">Voor het laatst</th>
                      <th className="py-1">Meldingen</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(data.players || []).slice(0, 40).map((p, i) => (
                      <tr key={i} className="border-b border-white/5 text-gray-300">
                        <td className="py-1 pr-2 font-semibold text-white">{p.player}</td>
                        <td className="py-1 pr-2">{fmt(p.views)}</td>
                        <td className="py-1 pr-2">{(p.platforms || []).join(', ') || '—'}{p.appVersion ? ` (${p.appVersion})` : ''}</td>
                        <td className="py-1 pr-2">{dag(p.lastSeen)}</td>
                        <td className="py-1">
                          {p.notificationsOff
                            ? <span className="text-gray-500 flex items-center gap-1"><BellOff size={12} /> uit</span>
                            : <span className="text-green-400/80 flex items-center gap-1"><Bell size={12} /> aan</span>}
                          {(p.androidPush || (p.webPush || []).length > 0) && (
                            <span className="text-gray-500"> · {[p.androidPush && 'Android', ...(p.webPush || [])].filter(Boolean).join(', ')}</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
