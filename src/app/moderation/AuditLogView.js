'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { Search, ChevronLeft, ChevronRight, History, RefreshCw, Download } from 'lucide-react';

// Admin-only viewer for the staff audit log (Settings > Audit Log). Read-only:
// the API only offers GET. Entries are written by logAudit() in each Staff
// Panel API route (src/lib/audit.js).

const AREAS = {
  moderation: 'Sanctions',
  members: 'Members & roles',
  reports: 'Reports',
  bugs: 'Bug reports',
  applications: 'Staff applications',
  events: 'Events',
  polls: 'Polls',
  wiki: 'Wiki',
  updates: 'Updates',
  news: 'News',
  console: 'RCON console',
  settings_general: 'General settings',
  settings_carousel: 'Carousel',
  settings_guide: 'Starter guide',
  settings_rules: 'Rules',
  settings_tips: 'Tip of the day',
  settings_vote: 'Vote sites',
  settings_projects: 'Projects',
  settings_content: 'Site content',
  settings_cleanup: 'Account cleanup',
  roles: 'Roles & permissions',
  newsletter: 'Newsletter',
  files: 'Shared files',
  gdpr: 'Account deletion',
  shop: 'Shop',
  vote_rewards: 'Vote rewards',
  achievements: 'Achievements',
  brewing: 'Brewing',
  story: 'Story Room',
  'sign-in': 'Staff sign-ins',
  security: 'Account security'
};

const PAGE = 50;

function formatWhen(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString(undefined, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function AuditLogView() {
  const [entries, setEntries] = useState([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [area, setArea] = useState('');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [actor, setActor] = useState('');
  const [actorQuery, setActorQuery] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Debounce the free-text filters so typing doesn't fire a request per key.
  useEffect(() => {
    const t = setTimeout(() => { setQuery(search.trim()); setActorQuery(actor.trim()); setOffset(0); }, 300);
    return () => clearTimeout(t);
  }, [search, actor]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ limit: String(PAGE), offset: String(offset) });
      if (area) params.set('area', area);
      if (query) params.set('q', query);
      if (actorQuery) params.set('actor', actorQuery);
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      const res = await fetch(`/api/admin/audit?${params}`, { cache: 'no-store' });
      if (!res.ok) throw new Error(res.status === 403 ? 'Admin only' : 'Could not load the audit log');
      const data = await res.json();
      setEntries(data.entries || []);
      setTotal(data.total || 0);
      setError('');
    } catch (err) {
      setError(err.message || 'Could not load the audit log');
    } finally {
      setLoading(false);
    }
  }, [offset, area, query, actorQuery, from, to]);

  useEffect(() => { load(); }, [load]);

  const firstShown = total === 0 ? 0 : offset + 1;
  const lastShown = Math.min(offset + PAGE, total);

  // Same filters as the list, as a download (up to 5,000 rows).
  const exportParams = new URLSearchParams({ format: 'csv' });
  if (area) exportParams.set('area', area);
  if (query) exportParams.set('q', query);
  if (actorQuery) exportParams.set('actor', actorQuery);
  if (from) exportParams.set('from', from);
  if (to) exportParams.set('to', to);

  return (
    <div className="space-y-4">
      <div>
        <h4 className="font-serif text-base text-sp-gold uppercase tracking-wide flex items-center gap-2">
          <History className="w-4 h-4 text-sp-copper" /> Audit Log
        </h4>
        <p className="text-xs text-text-warm/55 mt-1">
          Who changed what in the Staff Panel. Read-only and admin-only. Sanctions, console commands, wiki, settings, roles, newsletters, brewing, Story Room, staff sign-ins and account-security changes are recorded from the moment this log went live — earlier changes are not in here.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <select
          value={area}
          onChange={e => { setArea(e.target.value); setOffset(0); }}
          className="bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
        >
          <option value="">All areas</option>
          {Object.entries(AREAS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select>
        <div className="relative">
          <Search className="w-3 h-3 text-text-warm/40 absolute left-2.5 top-2.5" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search target or detail..."
            className="bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg pl-7 pr-3 py-2 w-56 focus:outline-hidden focus:border-sp-gold"
          />
        </div>
        <input
          type="text"
          value={actor}
          onChange={e => setActor(e.target.value)}
          placeholder="Staff member (exact name)"
          className="bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 w-48 focus:outline-hidden focus:border-sp-gold"
        />
        <label className="flex items-center gap-1.5 text-[10px] uppercase font-bold text-text-warm/45">
          From
          <input type="date" value={from} max={to || undefined} onChange={e => { setFrom(e.target.value); setOffset(0); }} className="bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-2 py-1.5 focus:outline-hidden focus:border-sp-gold" />
        </label>
        <label className="flex items-center gap-1.5 text-[10px] uppercase font-bold text-text-warm/45">
          To
          <input type="date" value={to} min={from || undefined} onChange={e => { setTo(e.target.value); setOffset(0); }} className="bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-2 py-1.5 focus:outline-hidden focus:border-sp-gold" />
        </label>
        <a
          href={`/api/admin/audit?${exportParams}`}
          title="Download these results as a spreadsheet (CSV, up to 5,000 rows)"
          className="ml-auto flex items-center gap-1.5 px-3 py-2 rounded-lg border border-sp-bronze/40 text-[10px] font-bold uppercase tracking-wider text-text-warm/70 hover:text-sp-gold hover:border-sp-gold transition-all"
        >
          <Download className="w-3.5 h-3.5" /> Export CSV
        </a>
        <button
          type="button"
          onClick={load}
          title="Refresh"
          className="p-2 rounded-lg border border-sp-bronze/40 text-text-warm/60 hover:text-sp-gold hover:border-sp-gold transition-all cursor-pointer"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {error && <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">{error}</div>}

      <div className="bg-neutral-900/40 border border-sp-bronze/35 rounded-xl overflow-hidden">
        {loading && entries.length === 0 ? (
          <p className="p-6 text-center text-xs text-text-warm/40">Loading…</p>
        ) : entries.length === 0 ? (
          <p className="p-6 text-center text-xs text-text-warm/40 italic">No entries match.</p>
        ) : (
          <ul className="divide-y divide-sp-bronze/15">
            {entries.map(e => (
              <li key={e.id} className="px-4 py-2.5 flex flex-wrap items-start gap-x-3 gap-y-1 text-xs">
                <span className="text-[10px] text-text-warm/40 w-36 shrink-0 pt-0.5">{formatWhen(e.createdAt)}</span>
                <span className="min-w-0 flex-1">
                  <span className="text-white font-bold">{e.actor}</span>
                  {e.actorRole && e.actorRole !== 'admin' && <span className="text-[9px] text-text-warm/40"> ({e.actorRole})</span>}
                  <span className="text-text-warm/80"> {e.action}</span>
                  {e.target && <span className="text-sp-gold"> — {e.target}</span>}
                  {e.detail && <span className="block text-[11px] text-text-warm/45 break-words">{e.detail}</span>}
                </span>
                <span className="text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded border border-sp-bronze/30 text-text-warm/55 shrink-0">
                  {AREAS[e.area] || e.area}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex items-center justify-between text-[11px] text-text-warm/50">
        <span>{total === 0 ? 'No entries' : `${firstShown}–${lastShown} of ${total}`}</span>
        <div className="flex gap-1.5">
          <button
            type="button"
            disabled={offset === 0}
            onClick={() => setOffset(Math.max(0, offset - PAGE))}
            className="p-1.5 rounded border border-sp-bronze/40 disabled:opacity-30 hover:border-sp-gold cursor-pointer disabled:cursor-default"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <button
            type="button"
            disabled={offset + PAGE >= total}
            onClick={() => setOffset(offset + PAGE)}
            className="p-1.5 rounded border border-sp-bronze/40 disabled:opacity-30 hover:border-sp-gold cursor-pointer disabled:cursor-default"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
