'use client';

import React, { useState } from 'react';
import {
  Search, LayoutDashboard, ChevronDown, Activity, Clock, ArrowRight,
  UserX, Flag, Bug, BookOpen, RefreshCw, ShieldCheck, Terminal, Calendar, ScrollText, Users,
  Settings, ThumbsUp, Newspaper, Mail, FolderLock, ShoppingBag, Trophy
} from 'lucide-react';

// Presentation half of the Staff Panel shell (sidebar + overview). All state
// that matters (active tab, permissions, dashboard data) lives in
// moderation/page.js and arrives as props, so nothing here makes its own
// access decisions — it only renders what it is handed.

export function StaffSidebar({ groups, active, onSelect, search, setSearch }) {
  const [open, setOpen] = useState(false);
  // A nav item can be a "hub": it has `children`, each child being a real tab
  // (or, for Settings, a settings tool). A hub stays expanded while one of its
  // children is the active tab; clicking the hub header while it is active
  // collapses it. Hubs only group the navigation — every child keeps its own
  // permission, URL (?tab=) and API routes.
  const [collapsed, setCollapsed] = useState({});
  const flat = groups.flatMap(g => g.tabs);
  const q = search.trim().toLowerCase();
  const hubIsActive = (t) => (t.activeKeys ? t.activeKeys.includes(active) : active === t.key);
  const current = active === 'overview'
    ? { label: 'Overview', Icon: LayoutDashboard }
    : flat.find(t => t.key === active) || flat.flatMap(t => t.children || []).find(c => c.key === active) || flat.find(hubIsActive);
  const CurrentIcon = current?.Icon;

  const matches = (t) => t.label.toLowerCase().includes(q) || (t.children || []).some(c => c.label.toLowerCase().includes(q));
  const childList = (t) => (t.children || []).filter(c => !q || c.label.toLowerCase().includes(q) || t.label.toLowerCase().includes(q));

  const badgePill = (n, crimson) => n > 0 && (
    <span className={`min-w-5 text-center text-[10px] font-bold px-1.5 py-0.5 rounded-full ${crimson ? 'bg-crimson text-white' : 'bg-sp-gold text-black'}`}>
      {n > 99 ? '99+' : n}
    </span>
  );

  const item = (t) => {
    const isActive = hubIsActive(t);
    const Icon = t.Icon;

    if (t.children && t.children.length > 0) {
      const shown = childList(t);
      const expanded = q ? shown.length > 0 : (isActive && !collapsed[t.key]);
      const hubBadge = t.badge ?? t.children.reduce((n, c) => n + (c.badge || 0), 0);
      return (
        <div key={t.key} className="space-y-0.5">
          <button
            type="button"
            title={t.description}
            onClick={() => {
              if (isActive) setCollapsed(c => ({ ...c, [t.key]: !c[t.key] }));
              else { setCollapsed(c => ({ ...c, [t.key]: false })); onSelect(t.selectKey || t.key); }
            }}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-xs font-semibold transition-all cursor-pointer border ${
              isActive ? 'bg-sp-gold/10 border-sp-gold/50 text-sp-gold' : 'border-transparent text-text-warm/65 hover:bg-neutral-900/70 hover:text-white'
            }`}
          >
            {Icon && <Icon className="w-4 h-4 shrink-0" />}
            <span className="flex-1 truncate">{t.label}</span>
            {!expanded && badgePill(hubBadge, true)}
            <ChevronDown className={`w-3.5 h-3.5 shrink-0 transition-transform ${expanded ? 'rotate-180' : ''}`} />
          </button>
          {expanded && (
            <div className="ml-4 pl-2 border-l border-sp-bronze/30 space-y-0.5 py-0.5">
              {shown.map(c => {
                const CIcon = c.Icon;
                const childActive = isActive && (t.activeChild ?? active) === c.key;
                return (
                  <button
                    key={c.key}
                    type="button"
                    title={c.description}
                    onClick={() => { t.onSelectChild(c.key); setOpen(false); }}
                    className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-md text-left text-[11px] font-semibold transition-all cursor-pointer border ${
                      childActive
                        ? (c.crimson ? 'bg-crimson/15 border-crimson/50 text-rose-200' : 'bg-sp-gold/10 border-sp-gold/40 text-sp-gold')
                        : `border-transparent text-text-warm/55 hover:bg-neutral-900/70 ${c.crimson ? 'hover:text-rose-200' : 'hover:text-white'}`
                    }`}
                  >
                    {CIcon && <CIcon className="w-3.5 h-3.5 shrink-0" />}
                    <span className="flex-1 truncate">{c.label}</span>
                    {badgePill(c.badge, c.crimson)}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      );
    }

    return (
      <button
        key={t.key}
        type="button"
        title={t.description}
        onClick={() => { onSelect(t.key); setOpen(false); }}
        className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left text-xs font-semibold transition-all cursor-pointer border ${
          isActive
            ? (t.crimson ? 'bg-crimson/15 border-crimson/50 text-rose-200' : 'bg-sp-gold/10 border-sp-gold/50 text-sp-gold')
            : `border-transparent text-text-warm/65 hover:bg-neutral-900/70 ${t.crimson ? 'hover:text-rose-200' : 'hover:text-white'}`
        }`}
      >
        {Icon && <Icon className="w-4 h-4 shrink-0" />}
        <span className="flex-1 truncate">{t.label}</span>
        {badgePill(t.badge, t.crimson)}
      </button>
    );
  };

  return (
    <aside className="w-full lg:w-60 lg:shrink-0 lg:sticky lg:top-20 self-start">
      {/* Phone/tablet: collapsed behind a bar showing where you are */}
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className="lg:hidden w-full flex items-center gap-2 bg-neutral-900/60 border border-sp-bronze/40 rounded-lg px-3 py-2.5 text-xs font-bold uppercase tracking-wider text-sp-gold cursor-pointer"
      >
        {CurrentIcon && <CurrentIcon className="w-4 h-4" />}
        <span className="flex-1 text-left">{current?.label || 'Menu'}</span>
        <ChevronDown className={`w-4 h-4 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      <nav className={`${open ? 'block' : 'hidden'} lg:block mt-2 lg:mt-0 bg-neutral-950/50 border border-sp-bronze/35 rounded-xl p-2.5 space-y-3 lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto`}>
        <div className="relative">
          <Search className="w-3 h-3 text-text-warm/40 absolute left-2.5 top-2.5" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Find a tab..."
            className="w-full bg-neutral-950/60 border border-sp-bronze/25 text-white text-[11px] rounded-md pl-7 pr-2 py-1.5 focus:outline-hidden focus:border-sp-gold"
          />
        </div>

        {!q && item({ key: 'overview', label: 'Overview', description: 'What needs attention and what staff did recently.', Icon: LayoutDashboard })}

        {groups.map((g, gi) => {
          const tabs = g.tabs.filter(tab => hubIsActive(tab) || !q || matches(tab));
          if (tabs.length === 0) return null;
          return (
            <div key={g.name || gi} className="space-y-1">
              {g.name && <div className="px-3 pt-1 text-[9px] uppercase font-bold tracking-widest text-text-warm/30">{g.name}</div>}
              {tabs.map(tab => item(tab))}
            </div>
          );
        })}

        {q && !flat.some(matches) && (
          <p className="px-3 text-[10px] text-text-warm/30 italic">No tab matches "{search}".</p>
        )}
      </nav>
    </aside>
  );
}

const KIND_META = {
  moderation: { Icon: UserX, label: 'Sanctions', color: 'text-rose-300' },
  console: { Icon: Terminal, label: 'Console', color: 'text-emerald-300' },
  reports: { Icon: Flag, label: 'Reports', color: 'text-orange-300' },
  bugs: { Icon: Bug, label: 'Bugs', color: 'text-amber-300' },
  applications: { Icon: ShieldCheck, label: 'Applications', color: 'text-sky-300' },
  wiki: { Icon: BookOpen, label: 'Wiki', color: 'text-sp-gold' },
  updates: { Icon: RefreshCw, label: 'Updates', color: 'text-violet-300' },
  events: { Icon: Calendar, label: 'Events', color: 'text-teal-300' },
  story: { Icon: ScrollText, label: 'Story', color: 'text-pink-300' },
  settings: { Icon: Settings, label: 'Settings', color: 'text-sp-copper' },
  polls: { Icon: ThumbsUp, label: 'Polls', color: 'text-teal-300' },
  news: { Icon: Newspaper, label: 'News', color: 'text-violet-300' },
  members: { Icon: Users, label: 'Members', color: 'text-sky-300' },
  roles: { Icon: ShieldCheck, label: 'Roles', color: 'text-rose-300' },
  newsletter: { Icon: Mail, label: 'Newsletter', color: 'text-amber-300' },
  files: { Icon: FolderLock, label: 'Files', color: 'text-text-warm' },
  gdpr: { Icon: UserX, label: 'Account deletion', color: 'text-rose-300' },
  shop: { Icon: ShoppingBag, label: 'Shop', color: 'text-amber-300' },
  vote_rewards: { Icon: ThumbsUp, label: 'Vote rewards', color: 'text-teal-300' },
  achievements: { Icon: Trophy, label: 'Achievements', color: 'text-sp-gold' }
};

function timeAgo(iso) {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)}d ago`;
  return new Date(iso).toLocaleDateString();
}

export function StaffOverview({ dashboard, loading, error, onNavigate, userName }) {
  const [kindFilter, setKindFilter] = useState('all');
  const stats = dashboard?.stats || [];
  const activity = dashboard?.activity || [];
  const kinds = Array.from(new Set(activity.map(a => a.kind)));
  const shown = kindFilter === 'all' ? activity : activity.filter(a => a.kind === kindFilter);
  const attention = stats.filter(s => s.urgent && s.value > 0);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="font-serif text-xl font-bold text-sp-gold uppercase tracking-wide">
          Welcome back{userName ? `, ${userName}` : ''}
        </h2>
        <p className="text-xs text-text-warm/55 mt-1">
          {loading && !dashboard
            ? 'Checking the archives…'
            : attention.length > 0
              ? `${attention.reduce((n, s) => n + s.value, 0)} item${attention.reduce((n, s) => n + s.value, 0) === 1 ? '' : 's'} waiting for you.`
              : 'Nothing is waiting for you right now.'}
        </p>
      </div>

      {error && <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">{error}</div>}

      <div className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-3">
        {stats.map(s => {
          const clickable = !!s.tab;
          const Wrapper = clickable ? 'button' : 'div';
          const hot = s.urgent && s.value > 0;
          return (
            <Wrapper
              key={s.key}
              {...(clickable ? { type: 'button', onClick: () => onNavigate(s.tab) } : {})}
              className={`group text-left rounded-xl border p-4 transition-all ${clickable ? 'cursor-pointer hover:-translate-y-0.5' : ''} ${
                hot ? 'bg-crimson/10 border-crimson/50 hover:border-crimson' : 'bg-neutral-900/50 border-sp-bronze/35 hover:border-sp-gold/60'
              }`}
            >
              <div className={`font-serif text-3xl font-black ${hot ? 'text-rose-300' : 'text-sp-gold'}`}>{s.value}</div>
              <div className="mt-1 flex items-center gap-1 text-[10px] uppercase font-bold tracking-wider text-text-warm/55">
                {s.label}
                {clickable && <ArrowRight className="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity" />}
              </div>
            </Wrapper>
          );
        })}
      </div>

      <section className="bg-neutral-900/40 border border-sp-bronze/35 rounded-xl">
        <header className="flex flex-wrap items-center gap-2 px-4 py-3 border-b border-sp-bronze/25">
          <h3 className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-sp-gold mr-auto">
            <Activity className="w-4 h-4" /> Recent activity
          </h3>
          {kinds.length > 1 && (
            <div className="flex flex-wrap gap-1">
              {['all', ...kinds].map(k => (
                <button
                  key={k}
                  type="button"
                  onClick={() => setKindFilter(k)}
                  className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase cursor-pointer border transition-all ${
                    kindFilter === k ? 'bg-sp-gold text-black border-sp-gold' : 'border-sp-bronze/30 text-text-warm/55 hover:text-white'
                  }`}
                >
                  {k === 'all' ? 'All' : (KIND_META[k]?.label || k)}
                </button>
              ))}
            </div>
          )}
        </header>

        {loading && !dashboard ? (
          <p className="p-6 text-center text-xs text-text-warm/40">Loading…</p>
        ) : shown.length === 0 ? (
          <p className="p-6 text-center text-xs text-text-warm/40 italic">No recent activity.</p>
        ) : (
          <ul className="divide-y divide-sp-bronze/15 max-h-[560px] overflow-y-auto">
            {shown.map(a => {
              const meta = KIND_META[a.kind] || { Icon: Users, color: 'text-text-warm' };
              const Icon = meta.Icon;
              return (
                <li key={a.id}>
                  <button
                    type="button"
                    onClick={() => onNavigate(a.tab)}
                    className="w-full flex items-start gap-3 px-4 py-2.5 text-left hover:bg-neutral-900/60 transition-colors cursor-pointer"
                  >
                    <Icon className={`w-4 h-4 mt-0.5 shrink-0 ${meta.color}`} />
                    <span className="flex-1 min-w-0">
                      <span className="block text-xs text-text-warm/90">
                        {a.actor && <strong className="text-white">{a.actor} </strong>}{a.text}
                      </span>
                      {a.detail && <span className="block text-[11px] text-text-warm/45 truncate">{a.detail}</span>}
                    </span>
                    <span className="flex items-center gap-1 text-[10px] text-text-warm/35 shrink-0 mt-0.5">
                      <Clock className="w-3 h-3" /> {timeAgo(a.at)}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
