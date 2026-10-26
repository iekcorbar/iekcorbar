'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { BarChart3, RefreshCw, Smartphone, Users, Eye, Download, Bell, Search, Table2 } from 'lucide-react';

// Staff Panel → Analytics (website + app usage). Data: api/admin/analytics.
// One chart at a time (metric toggle) so there's never a second y-axis;
// every chart has a table view for accessibility.

const RANGES = [7, 30, 90];
const METRICS = [
  { key: 'visitors', label: 'Visitors', help: 'Unique visitors per day' },
  { key: 'pageviews', label: 'Page views', help: 'Pages opened per day' },
  { key: 'downloads', label: 'App downloads', help: 'Android app downloads per day' }
];
const PLATFORM_LABELS = {
  'android-app': 'Android app',
  'iphone-homescreen': 'iPhone (home screen)',
  'homescreen-other': 'Home screen app',
  'mobile-browser': 'Mobile browser',
  desktop: 'Desktop'
};
const platformLabel = (p) => PLATFORM_LABELS[p] || p;
const fmtNum = (n) => new Intl.NumberFormat('en-GB').format(n || 0);
const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }) : '—');
const fmtDateTime = (d) => (d ? new Date(d).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');

function StatTile({ icon: Icon, label, value, sub }) {
  return (
    <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-4">
      <p className="text-[10px] uppercase tracking-wider font-bold text-text-warm/50 flex items-center gap-1.5">
        <Icon className="w-3.5 h-3.5 text-sp-copper" /> {label}
      </p>
      <p className="font-serif text-2xl font-bold text-white mt-1 tabular-nums">{value}</p>
      {sub && <p className="text-[10px] text-text-warm/45 mt-0.5">{sub}</p>}
    </div>
  );
}

function DailyChart({ series, metric, days }) {
  const [hover, setHover] = useState(null);
  const [showTable, setShowTable] = useState(false);
  const values = series.map(d => d[metric]);
  const max = Math.max(1, ...values);
  const niceMax = (() => {
    const pow = Math.pow(10, Math.floor(Math.log10(max)));
    const n = max / pow;
    return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * pow;
  })();
  const ticks = [niceMax, niceMax / 2, 0];
  const labelEvery = days <= 7 ? 1 : days <= 30 ? 5 : 15;
  const metricMeta = METRICS.find(m => m.key === metric);

  return (
    <div>
      <div className="flex">
        {/* y-axis labels */}
        <div className="flex flex-col justify-between text-[9px] text-text-warm/40 tabular-nums pr-2 h-44 text-right w-10 shrink-0">
          {ticks.map(t => <span key={t}>{fmtNum(Math.round(t))}</span>)}
        </div>
        <div className="relative flex-1 h-44">
          {/* recessive gridlines */}
          {ticks.map((t, i) => (
            <div key={t} className="absolute left-0 right-0 border-t border-sp-bronze/15" style={{ top: `${(i / (ticks.length - 1)) * 100}%` }} />
          ))}
          <div className="absolute inset-0 flex items-end gap-[2px]" onMouseLeave={() => setHover(null)}>
            {series.map((d, i) => {
              const h = (d[metric] / niceMax) * 100;
              return (
                <div
                  key={d.day}
                  className="relative flex-1 h-full flex items-end cursor-default"
                  onMouseEnter={() => setHover(i)}
                  onTouchStart={() => setHover(i)}
                >
                  <div
                    className={`w-full rounded-t-[4px] transition-colors ${hover === i ? 'bg-white' : 'bg-sp-gold'}`}
                    style={{ height: d[metric] > 0 ? `max(${h}%, 2px)` : '0' }}
                  />
                </div>
              );
            })}
          </div>
          {hover !== null && series[hover] && (
            <div
              className="absolute -top-2 z-10 pointer-events-none bg-neutral-950 border border-sp-bronze/50 rounded-md px-2.5 py-1.5 text-[10px] shadow-xl whitespace-nowrap"
              style={{ left: `${((hover + 0.5) / series.length) * 100}%`, transform: `translate(${hover > series.length / 2 ? '-100%' : '0'}, -100%)` }}
            >
              <p className="text-text-warm/60">{new Date(series[hover].day).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })}</p>
              <p className="text-white font-bold tabular-nums">{fmtNum(series[hover][metric])} {metricMeta.label.toLowerCase()}</p>
            </div>
          )}
        </div>
      </div>
      {/* x-axis labels */}
      <div className="flex ml-10 mt-1 gap-[2px]">
        {series.map((d, i) => (
          <span key={d.day} className="flex-1 text-[9px] text-text-warm/40 text-center overflow-visible whitespace-nowrap">
            {i % labelEvery === 0 ? fmtDate(d.day) : ''}
          </span>
        ))}
      </div>
      <button
        type="button"
        onClick={() => setShowTable(s => !s)}
        className="mt-3 text-[10px] text-sp-copper hover:text-sp-gold flex items-center gap-1"
      >
        <Table2 className="w-3 h-3" /> {showTable ? 'Hide table' : 'Show as table'}
      </button>
      {showTable && (
        <div className="mt-2 max-h-60 overflow-y-auto border border-sp-bronze/20 rounded-lg">
          <table className="w-full text-xs">
            <thead className="bg-neutral-900/60 text-[10px] uppercase text-text-warm/50 sticky top-0">
              <tr><th className="text-left px-3 py-1.5">Day</th><th className="text-right px-3 py-1.5">Visitors</th><th className="text-right px-3 py-1.5">Page views</th><th className="text-right px-3 py-1.5">Downloads</th></tr>
            </thead>
            <tbody>
              {[...series].reverse().map(d => (
                <tr key={d.day} className="border-t border-sp-bronze/10">
                  <td className="px-3 py-1 text-text-warm/80">{fmtDate(d.day)}</td>
                  <td className="px-3 py-1 text-right tabular-nums">{fmtNum(d.visitors)}</td>
                  <td className="px-3 py-1 text-right tabular-nums">{fmtNum(d.pageviews)}</td>
                  <td className="px-3 py-1 text-right tabular-nums">{fmtNum(d.downloads)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// Ranked list with a thin inline bar showing each row's share.
function RankTable({ title, rows, labelFn = (x) => x, valueKey = 'visits', valueLabel = 'Visits', extraKey = 'views', extraLabel = 'Views', empty = 'No data yet' }) {
  const max = Math.max(1, ...rows.map(r => r[valueKey] || 0));
  return (
    <div className="bg-neutral-900/30 border border-sp-bronze/30 rounded-xl p-4">
      <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80 mb-2">{title}</h4>
      {rows.length === 0 ? (
        <p className="text-xs text-text-warm/40 italic">{empty}</p>
      ) : (
        <table className="w-full text-xs">
          <thead>
            <tr className="text-[10px] uppercase text-text-warm/40">
              <th className="text-left font-semibold pb-1"> </th>
              <th className="text-right font-semibold pb-1 w-16">{valueLabel}</th>
              {extraKey && <th className="text-right font-semibold pb-1 w-14">{extraLabel}</th>}
            </tr>
          </thead>
          <tbody>
            {rows.map(r => (
              <tr key={r.label} className="border-t border-sp-bronze/10">
                <td className="py-1.5 pr-2">
                  <div className="text-text-warm/85 truncate max-w-[220px]" title={labelFn(r.label)}>{labelFn(r.label)}</div>
                  <div className="h-[3px] mt-1 rounded-full bg-sp-gold/80" style={{ width: `${Math.max(2, ((r[valueKey] || 0) / max) * 100)}%` }} />
                </td>
                <td className="py-1.5 text-right tabular-nums text-white">{fmtNum(r[valueKey])}</td>
                {extraKey && <td className="py-1.5 text-right tabular-nums text-text-warm/60">{fmtNum(r[extraKey])}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function AnalyticsTab() {
  const [days, setDays] = useState(30);
  const [metric, setMetric] = useState('visitors');
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [playerSearch, setPlayerSearch] = useState('');

  const load = async (range = days) => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch(`/api/admin/analytics?days=${range}`);
      const json = await res.json();
      if (!res.ok) throw new Error(json.error || 'Could not load analytics');
      setData(json);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(days); }, [days]); // eslint-disable-line react-hooks/exhaustive-deps

  // Fill days without any events with zeros so the chart has one bar per day.
  const series = useMemo(() => {
    if (!data) return [];
    const byDay = new Map(data.daily.map(d => [d.day, d]));
    const out = [];
    for (let i = data.days - 1; i >= 0; i--) {
      const dt = new Date();
      dt.setDate(dt.getDate() - i);
      const key = `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
      out.push(byDay.get(key) || { day: key, visitors: 0, pageviews: 0, downloads: 0 });
    }
    return out;
  }, [data]);

  const players = useMemo(() => {
    if (!data) return [];
    const q = playerSearch.trim().toLowerCase();
    return q ? data.players.filter(p => p.player.toLowerCase().includes(q)) : data.players;
  }, [data, playerSearch]);

  const t = data?.totals;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-sp-bronze/25 pb-3">
        <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-sp-copper" /> Analytics
        </h3>
        <div className="flex items-center gap-2">
          <div className="flex rounded-md border border-sp-bronze/40 overflow-hidden">
            {RANGES.map(r => (
              <button
                key={r}
                type="button"
                onClick={() => setDays(r)}
                className={`px-3 py-1 text-[10px] font-bold uppercase tracking-wider ${days === r ? 'bg-sp-gold text-black' : 'text-text-warm/70 hover:text-white'}`}
              >
                {r} days
              </button>
            ))}
          </div>
          <button type="button" onClick={() => load()} className="p-1.5 text-text-warm/60 hover:text-sp-gold" title="Refresh">
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {error && <div className="bg-crimson/20 border border-crimson text-rose-200 text-sm px-3 py-2 rounded-lg">{error}</div>}

      {t && (
        <>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <StatTile icon={Eye} label="Visitors today" value={fmtNum(t.visitorsToday)} sub={`${fmtNum(t.pageviewsToday)} page views today`} />
            <StatTile icon={Users} label={`Visits (${data.days}d)`} value={fmtNum(t.visits)} sub={`${fmtNum(t.pageviews)} page views · ~${fmtNum(Math.round(t.visits / data.days))}/day`} />
            <StatTile icon={Users} label={`Active players (${data.days}d)`} value={fmtNum(t.activePlayers)} sub="Logged-in accounts" />
            <StatTile icon={Smartphone} label={`App users (${data.days}d)`} value={fmtNum(t.appUsers)} sub="Android app + iPhone home screen" />
            <StatTile icon={Download} label={`APK downloads (${data.days}d)`} value={fmtNum(t.downloads)} sub="Via iekcorbar.com/app" />
            <StatTile icon={Bell} label="Android app push" value={fmtNum(t.androidPushPlayers)} sub="Players with the app linked" />
            <StatTile icon={Bell} label="Browser / iPhone push" value={fmtNum(t.webPushDevices)} sub="Devices with notifications on" />
            <StatTile icon={Users} label="Notifications off" value={fmtNum(data.players.filter(p => p.notificationsOff).length)} sub="Players who turned all off" />
          </div>

          <div className="bg-neutral-900/30 border border-sp-bronze/30 rounded-xl p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80">
                {METRICS.find(m => m.key === metric).help} — last {data.days} days
              </h4>
              <div className="flex rounded-md border border-sp-bronze/40 overflow-hidden">
                {METRICS.map(m => (
                  <button
                    key={m.key}
                    type="button"
                    onClick={() => setMetric(m.key)}
                    className={`px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${metric === m.key ? 'bg-sp-gold text-black' : 'text-text-warm/70 hover:text-white'}`}
                  >
                    {m.label}
                  </button>
                ))}
              </div>
            </div>
            <DailyChart series={series} metric={metric} days={data.days} />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <RankTable title="Top pages" rows={data.topPages} />
            <RankTable title="Where visitors come from" rows={data.referrers} empty="No external referrers yet (direct visits and the app aren't listed)" />
            <RankTable title="Devices" rows={data.platforms} labelFn={platformLabel} />
            <RankTable title="Browsers" rows={data.browsers} />
            <RankTable title="Operating systems" rows={data.oses} />
            <RankTable title="Android app versions" rows={data.appVersions} valueKey="users" valueLabel="Users" extraKey={null} labelFn={v => `v${v}`} empty="No app visits yet" />
          </div>

          <div className="bg-neutral-900/30 border border-sp-bronze/30 rounded-xl p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="text-xs font-bold uppercase tracking-wider text-sp-gold/80">Players ({players.length})</h4>
              <div className="relative">
                <Search className="w-3 h-3 text-text-warm/40 absolute left-2 top-2" />
                <input
                  type="text"
                  value={playerSearch}
                  onChange={e => setPlayerSearch(e.target.value)}
                  placeholder="Find a player..."
                  className="bg-neutral-950/40 border border-sp-bronze/25 text-white text-[11px] rounded pl-6 pr-2 py-1 w-40 focus:outline-hidden focus:border-sp-gold"
                />
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs min-w-[720px]">
                <thead>
                  <tr className="text-[10px] uppercase text-text-warm/45 border-b border-sp-bronze/20">
                    <th className="text-left py-1.5 pr-2">Player</th>
                    <th className="text-left py-1.5 pr-2">Used on</th>
                    <th className="text-left py-1.5 pr-2">App version</th>
                    <th className="text-right py-1.5 pr-2">Page views</th>
                    <th className="text-left py-1.5 pr-2">First seen</th>
                    <th className="text-left py-1.5 pr-2">Last active</th>
                    <th className="text-left py-1.5">Push</th>
                  </tr>
                </thead>
                <tbody>
                  {players.map(p => (
                    <tr key={p.player + (p.lastSeen || '')} className="border-t border-sp-bronze/10 align-top">
                      <td className="py-1.5 pr-2 text-white font-semibold">{p.player}</td>
                      <td className="py-1.5 pr-2">
                        <div className="flex flex-wrap gap-1">
                          {p.platforms.length === 0 && <span className="text-text-warm/35">—</span>}
                          {p.platforms.map(pl => (
                            <span key={pl} className="px-1.5 py-0.5 rounded bg-neutral-800 border border-sp-bronze/25 text-[10px] text-text-warm/80">{platformLabel(pl)}</span>
                          ))}
                        </div>
                      </td>
                      <td className="py-1.5 pr-2 text-text-warm/80 tabular-nums">{p.appVersion ? `v${p.appVersion}` : '—'}</td>
                      <td className="py-1.5 pr-2 text-right tabular-nums">{fmtNum(p.views)}</td>
                      <td className="py-1.5 pr-2 text-text-warm/70">{fmtDate(p.firstSeen)}</td>
                      <td className="py-1.5 pr-2 text-text-warm/70">{fmtDateTime(p.lastSeen)}</td>
                      <td className="py-1.5 text-[10px] text-text-warm/75">
                        {p.notificationsOff ? <span className="text-rose-300">Turned off</span> : (
                          [p.androidPush && 'Android app', ...p.webPush].filter(Boolean).join(', ') || <span className="text-text-warm/35">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                  {players.length === 0 && (
                    <tr><td colSpan={7} className="py-4 text-center text-text-warm/40 italic">No logged-in activity in this period yet</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <p className="text-[10px] text-text-warm/40 leading-relaxed">
              Anonymous visitors are counted per day without cookies and can't be traced back to a person. Data older than 90 days is removed automatically.
            </p>
          </div>
        </>
      )}

      {!t && loading && <p className="text-xs text-text-warm/40 italic animate-pulse py-10 text-center">Loading analytics...</p>}
    </div>
  );
}
