'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { Bug, CheckCircle2, XCircle, Clock, ArrowRight, AlertTriangle, ThumbsUp, Check } from 'lucide-react';
import { BUG_CATEGORIES, BUG_STATUS_LABELS, bugCategoryLabel } from '@/lib/bug-categories';
import { useUser } from '../providers';

const STATUS_STYLE = {
  open: { icon: Clock, className: 'bg-crimson/10 border-crimson/40 text-rose-400' },
  fixed: { icon: CheckCircle2, className: 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400' },
  wont_fix: { icon: XCircle, className: 'bg-neutral-700/30 border-neutral-500/40 text-text-warm/60' },
};

export default function KnownBugsPage() {
  const { user, setIsLoginOpen } = useUser();
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [category, setCategory] = useState('all');
  const [confirmingId, setConfirmingId] = useState(null);
  const [confirmedIds, setConfirmedIds] = useState([]);

  useEffect(() => {
    if (!user) return;
    fetch('/api/bug-reports?public=1')
      .then(res => res.json())
      .then(data => { if (data.reports) setReports(data.reports); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [user]);

  // "I have this too" straight from the list — no need to go compose a
  // new report just to confirm one you already see below. See
  // db.bumpBugReportAffectedCount (idempotent server-side either way).
  async function handleConfirm(id) {
    setConfirmingId(id);
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'bump_duplicate', id })
      });
      if (res.ok) {
        setConfirmedIds(prev => [...prev, id]);
        setReports(prev => prev.map(r => r.id === id ? { ...r, affectedCount: (r.affectedCount || 1) + 1 } : r));
      }
    } catch (err) {
      console.error('Failed to confirm "I have this too":', err);
    } finally {
      setConfirmingId(null);
    }
  }

  const filtered = category === 'all' ? reports : reports.filter(r => r.category === category);
  const openCount = reports.filter(r => r.status === 'open').length;

  // Members-only, per staff decision 2026-09-09 — bug descriptions can
  // contain detail staff don't want handed to people with no account here
  // at all, not just staff-only info (reporter identity/notes/the
  // staff↔reporter thread were already excluded, see db.getPublicBugReports).
  if (!user) {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-crimson p-8 rounded-xl text-center steampunk-border shadow-2xl space-y-6">
        <div className="w-16 h-16 bg-crimson/15 rounded-full flex items-center justify-center border border-crimson text-rose-400 mx-auto">
          <AlertTriangle className="w-8 h-8" />
        </div>
        <h2 className="font-serif text-2xl font-bold text-rose-300 uppercase tracking-wider">Access Restrained</h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          Known Bugs is visible to registered members only — sign in to see what staff already knows about.
        </p>
        <button
          onClick={() => setIsLoginOpen(true)}
          className="w-full py-2.5 rounded-lg text-xs font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white transition-all cursor-pointer font-serif border border-transparent shadow-md"
        >
          Sign In / Register
        </button>
        <Link href="/report-bug" className="block text-xs font-bold uppercase tracking-wide text-sp-gold hover:text-white transition-colors">
          Just want to report a bug? No account needed →
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* HEADER */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-sp-bronze pb-5">
        <div>
          <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
            <Bug className="w-8 h-8 text-sp-copper" /> Known Bugs
          </h1>
          <p className="text-xs text-text-warm/60 mt-1 max-w-xl leading-relaxed">
            What staff already know about — {openCount} currently open.
          </p>
        </div>

        <div className="flex flex-wrap gap-1.5">
          <button
            onClick={() => setCategory('all')}
            className={`px-3 py-1 rounded text-[10px] font-bold uppercase transition-all cursor-pointer ${
              category === 'all' ? 'bg-sp-gold text-black font-black' : 'bg-neutral-900/40 text-text-warm/60 border border-sp-bronze/25 hover:text-white'
            }`}
          >
            All
          </button>
          {BUG_CATEGORIES.filter(c => c.value !== 'other').map(c => (
            <button
              key={c.value}
              onClick={() => setCategory(c.value)}
              className={`px-3 py-1 rounded text-[10px] font-bold uppercase transition-all cursor-pointer ${
                category === c.value ? 'bg-sp-gold text-black font-black' : 'bg-neutral-900/40 text-text-warm/60 border border-sp-bronze/25 hover:text-white'
              }`}
            >
              {c.label}
            </button>
          ))}
        </div>
      </div>

      {/* How It Works — new players won't know this flow otherwise */}
      <div className="bg-neutral-900/40 border border-sp-bronze/30 rounded-xl p-5">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs text-text-warm/70">
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">1</div>
            <div><span className="block text-white font-semibold text-sm">Check before reporting</span>See something below that matches? Click "I have this too" on it instead of filing a duplicate.</div>
          </div>
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">2</div>
            <div><span className="block text-white font-semibold text-sm">Status updates live here</span>Open, Fixed, or Won&apos;t Fix — staff update it directly, and you get a bell notification too if you reported or confirmed it.</div>
          </div>
          <div className="flex gap-3 items-start">
            <div className="w-7 h-7 rounded-full bg-sp-gold/15 border border-sp-gold/30 flex items-center justify-center text-sp-gold font-bold text-xs shrink-0">3</div>
            <div><span className="block text-white font-semibold text-sm">Fixed ones fade out</span>Resolved reports drop off this list about two weeks after they're fixed, so it stays focused on what&apos;s actually still an issue.</div>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="py-20 text-center text-xs text-text-warm/40 italic animate-pulse">Loading known bugs...</div>
      ) : filtered.length === 0 ? (
        <div className="bg-neutral-900/10 border border-sp-bronze/20 rounded-xl p-12 text-center text-text-warm/30 italic">
          Nothing known in this category right now.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filtered.map(r => {
            const style = STATUS_STYLE[r.status] || STATUS_STYLE.open;
            const StatusIcon = style.icon;
            return (
              <div key={r.id} className="bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-5 space-y-2.5 text-left">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border flex items-center gap-1 ${style.className}`}>
                    <StatusIcon className="w-3 h-3" /> {BUG_STATUS_LABELS[r.status] || r.status}
                  </span>
                  <span className="text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border bg-sp-bronze/10 border-sp-bronze/30 text-text-warm/60">
                    {bugCategoryLabel(r.category, r.customCategory)}
                  </span>
                  {r.affectedCount > 1 && (
                    <span className="text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border bg-sp-gold/10 border-sp-gold/30 text-sp-gold">
                      {r.affectedCount} players affected
                    </span>
                  )}
                </div>
                <p className="text-sm font-bold text-white">{r.subject}</p>
                <p className="text-xs text-text-warm/70 leading-relaxed">{r.description}</p>
                <div className="flex items-center justify-between gap-2">
                  <p className="text-[9px] text-text-warm/30 font-mono">
                    Reported {new Date(r.submittedAt).toLocaleDateString()}
                    {r.resolvedAt && ` · resolved ${new Date(r.resolvedAt).toLocaleDateString()}`}
                  </p>
                  {r.status === 'open' && (
                    confirmedIds.includes(r.id) ? (
                      <span className="text-[10px] uppercase font-bold text-emerald-400 flex items-center gap-1 shrink-0">
                        <Check className="w-3 h-3" /> Marked
                      </span>
                    ) : (
                      <button
                        onClick={() => handleConfirm(r.id)}
                        disabled={confirmingId === r.id}
                        className="px-2.5 py-1 border border-sp-gold/40 hover:bg-sp-gold/15 rounded text-[9px] uppercase font-bold text-sp-gold shrink-0 cursor-pointer disabled:opacity-50 flex items-center gap-1"
                      >
                        <ThumbsUp className="w-3 h-3" /> I have this too
                      </button>
                    )
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className="bg-neutral-900/20 border border-dashed border-sp-bronze/30 rounded-xl p-5 text-center space-y-2">
        <p className="text-xs text-text-warm/60">Hit something that's not listed here?</p>
        <Link
          href="/profile#report-a-bug"
          className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wide text-sp-gold hover:text-white transition-colors"
        >
          Report it from your Profile <ArrowRight className="w-3.5 h-3.5" />
        </Link>
      </div>
    </div>
  );
}
