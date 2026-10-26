'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { Ban, BellOff } from 'lucide-react';

// Profile > Account: everyone you blocked in chat and every conversation you
// muted, each with a one-click undo. Data from GET /api/chat?blocks=1; undo via
// POST /api/chat { action: 'unblock' | 'unmute', username }.
export default function BlockedMembersCard({ className = '' }) {
  const [blocked, setBlocked] = useState([]);
  const [muted, setMuted] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const res = await fetch('/api/chat?blocks=1', { cache: 'no-store' });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not load');
      setBlocked(data.blocked || []);
      setMuted(data.muted || []);
      setError('');
    } catch (err) {
      setError(err.message || 'Could not load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function undo(action, name) {
    setBusy(`${action}:${name}`);
    setError('');
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, username: name })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not save');
      if (action === 'unblock') setBlocked(list => list.filter(b => b.mcUsername !== name));
      else setMuted(list => list.filter(b => b.mcUsername !== name));
    } catch (err) {
      setError(err.message || 'Could not save');
    } finally {
      setBusy('');
    }
  }

  const row = (item, action, label) => (
    <li key={`${action}-${item.mcUsername}`} className="flex items-center gap-3 bg-neutral-950/40 border border-sp-bronze/20 rounded-lg px-3 py-2">
      <img src={`https://mc-heads.net/avatar/${encodeURIComponent(item.mcUsername)}/28`} alt="" className="w-7 h-7 rounded shrink-0" />
      <Link href={`/members/${encodeURIComponent(item.mcUsername)}`} className="flex-1 min-w-0 truncate text-xs font-mono font-bold text-white hover:text-sp-gold">{item.mcUsername}</Link>
      <button
        type="button"
        disabled={busy === `${action}:${item.mcUsername}`}
        onClick={() => undo(action, item.mcUsername)}
        className="px-3 py-1.5 rounded-lg border border-sp-bronze/40 text-[10px] font-bold uppercase tracking-wider text-text-warm/80 hover:text-sp-gold hover:border-sp-gold disabled:opacity-40 cursor-pointer transition-all"
      >
        {label}
      </button>
    </li>
  );

  return (
    <div className={`bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-5 text-left ${className}`}>
      <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
        <Ban className="w-5 h-5 text-sp-copper" /> Blocked &amp; muted in chat
      </h3>

      {error && <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-text-warm/40">Loading…</p>
      ) : (
        <>
          <div className="space-y-2">
            <h4 className="text-[10px] uppercase font-bold tracking-wider text-sp-gold/80 flex items-center gap-1.5"><Ban className="w-3.5 h-3.5" /> Blocked ({blocked.length})</h4>
            <p className="text-[10px] text-text-warm/40">Blocked members can&apos;t message you, and you can&apos;t message them.</p>
            {blocked.length === 0 ? <p className="text-xs text-text-warm/40 italic">You haven&apos;t blocked anyone.</p> : <ul className="space-y-1.5">{blocked.map(b => row(b, 'unblock', 'Unblock'))}</ul>}
          </div>
          <div className="space-y-2">
            <h4 className="text-[10px] uppercase font-bold tracking-wider text-sp-gold/80 flex items-center gap-1.5"><BellOff className="w-3.5 h-3.5" /> Muted conversations ({muted.length})</h4>
            <p className="text-[10px] text-text-warm/40">Muted conversations still deliver messages, but show no unread badge and send no notifications.</p>
            {muted.length === 0 ? <p className="text-xs text-text-warm/40 italic">No muted conversations.</p> : <ul className="space-y-1.5">{muted.map(b => row(b, 'unmute', 'Unmute'))}</ul>}
          </div>
        </>
      )}
    </div>
  );
}
