'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { UserRound, Search, Eraser, RefreshCw } from 'lucide-react';

// Staff Panel > Crew & Sanctions: every profile bio players have written, with
// a "Clear" action. Clearing asks for an optional reason, which goes to the
// player (bell notification) and the audit log. API: /api/admin/profile-bios.
export default function ProfileBiosPanel() {
  const [bios, setBios] = useState([]);
  const [banners, setBanners] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('');
  const [clearing, setClearing] = useState(null); // username being confirmed
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/profile-bios', { cache: 'no-store' });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not load bios');
      setBios(data.bios || []);
      setBanners(data.banners || []);
      setError('');
    } catch (err) {
      setError(err.message || 'Could not load bios');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function confirmClear(username, what = 'bio') {
    setBusy(true);
    setError('');
    try {
      const params = new URLSearchParams({ username });
      if (what === 'banner') params.set('what', 'banner');
      if (reason.trim()) params.set('reason', reason.trim());
      const res = await fetch(`/api/admin/profile-bios?${params}`, { method: 'DELETE' });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Could not clear the bio');
      if (what === 'banner') setBanners(list => list.filter(b => b.mcUsername !== username));
      else setBios(list => list.filter(b => b.mcUsername !== username));
      setClearing(null);
      setReason('');
    } catch (err) {
      setError(err.message || 'Could not clear the bio');
    } finally {
      setBusy(false);
    }
  }

  const q = filter.trim().toLowerCase();
  const shown = q ? bios.filter(b => (b.mcUsername || '').toLowerCase().includes(q) || b.bio.toLowerCase().includes(q)) : bios;

  return (
    <div className="mt-8 bg-neutral-900/30 border border-sp-bronze/40 rounded-xl p-5 space-y-4">
      <div className="flex flex-wrap items-center gap-3 border-b border-sp-bronze/20 pb-3">
        <div className="mr-auto">
          <h3 className="font-serif text-base text-sp-gold uppercase tracking-wide flex items-center gap-2">
            <UserRound className="w-5 h-5 text-sp-copper" /> Profile bios &amp; banners ({bios.length})
          </h3>
          <p className="text-[11px] text-text-warm/50 mt-0.5">Free text players show on their public profile. Clear anything that breaks the rules — the player is told, and the removed text is kept in the Audit Log.</p>
        </div>
        <div className="relative">
          <Search className="w-3 h-3 text-text-warm/40 absolute left-2.5 top-2.5" />
          <input
            type="text"
            value={filter}
            onChange={e => setFilter(e.target.value)}
            placeholder="Search name or text..."
            className="bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg pl-7 pr-3 py-2 w-52 focus:outline-hidden focus:border-sp-gold"
          />
        </div>
        <button type="button" onClick={load} title="Refresh" className="p-2 rounded-lg border border-sp-bronze/40 text-text-warm/60 hover:text-sp-gold hover:border-sp-gold cursor-pointer">
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {error && <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">{error}</div>}

      {loading && bios.length === 0 ? (
        <p className="text-xs text-text-warm/40 text-center py-6">Loading…</p>
      ) : shown.length === 0 ? (
        <p className="text-xs text-text-warm/40 italic text-center py-6">{bios.length === 0 ? 'Nobody has written a bio yet.' : 'No bio matches.'}</p>
      ) : (
        <ul className="space-y-2">
          {shown.map(b => (
            <li key={b.mcUsername} className="bg-neutral-950/40 border border-sp-bronze/20 rounded-lg p-3">
              <div className="flex items-start gap-3">
                <img src={`https://mc-heads.net/avatar/${encodeURIComponent(b.mcUsername)}/32`} alt="" className="w-8 h-8 rounded shrink-0" />
                <div className="min-w-0 flex-1">
                  <Link href={`/members/${encodeURIComponent(b.mcUsername)}`} className="text-xs font-bold text-white hover:text-sp-gold">{b.mcUsername}</Link>
                  <p className="text-sm text-text-warm/85 whitespace-pre-wrap break-words mt-0.5">{b.bio}</p>
                </div>
                {clearing !== b.mcUsername && (
                  <button
                    type="button"
                    onClick={() => { setClearing(b.mcUsername); setReason(''); }}
                    className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-crimson/45 text-rose-300 hover:bg-crimson hover:text-white text-[10px] font-bold uppercase tracking-wider cursor-pointer transition-all"
                  >
                    <Eraser className="w-3.5 h-3.5" /> Clear
                  </button>
                )}
              </div>

              {clearing === b.mcUsername && (
                <div className="mt-3 pt-3 border-t border-sp-bronze/20 flex flex-wrap items-center gap-2">
                  <input
                    type="text"
                    autoFocus
                    value={reason}
                    onChange={e => setReason(e.target.value)}
                    maxLength={200}
                    placeholder="Reason (optional, shown to the player)"
                    className="flex-1 min-w-52 bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                  />
                  <button type="button" disabled={busy} onClick={() => confirmClear(b.mcUsername)} className="px-3 py-2 rounded-lg bg-crimson hover:bg-rose-600 disabled:opacity-50 text-white text-[10px] font-bold uppercase tracking-wider cursor-pointer">
                    {busy ? 'Clearing…' : 'Clear this bio'}
                  </button>
                  <button type="button" disabled={busy} onClick={() => setClearing(null)} className="px-3 py-2 rounded-lg border border-sp-bronze/40 text-text-warm/70 hover:text-white text-[10px] font-bold uppercase tracking-wider cursor-pointer">
                    Cancel
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {banners.length > 0 && (
        <div className="pt-4 border-t border-sp-bronze/20 space-y-3">
          <h4 className="text-[11px] uppercase font-bold tracking-wider text-sp-gold/80">Custom banners ({banners.length})</h4>
          <ul className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {banners.map(b => (
              <li key={`banner-${b.mcUsername}`} className="bg-neutral-950/40 border border-sp-bronze/20 rounded-lg p-3 space-y-2">
                <img src={b.bannerImage} alt={`${b.mcUsername}'s banner`} className="w-full h-24 object-cover rounded-md border border-sp-bronze/30" />
                <div className="flex items-center gap-2">
                  <Link href={`/members/${encodeURIComponent(b.mcUsername)}`} className="flex-1 min-w-0 truncate text-xs font-bold text-white hover:text-sp-gold">{b.mcUsername}</Link>
                  {clearing !== `banner:${b.mcUsername}` && (
                    <button type="button" onClick={() => { setClearing(`banner:${b.mcUsername}`); setReason(''); }} className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-crimson/45 text-rose-300 hover:bg-crimson hover:text-white text-[10px] font-bold uppercase tracking-wider cursor-pointer transition-all">
                      <Eraser className="w-3.5 h-3.5" /> Remove
                    </button>
                  )}
                </div>
                {clearing === `banner:${b.mcUsername}` && (
                  <div className="flex flex-wrap items-center gap-2">
                    <input type="text" autoFocus value={reason} onChange={e => setReason(e.target.value)} maxLength={200} placeholder="Reason (optional, shown to the player)" className="flex-1 min-w-40 bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold" />
                    <button type="button" disabled={busy} onClick={() => confirmClear(b.mcUsername, 'banner')} className="px-3 py-2 rounded-lg bg-crimson hover:bg-rose-600 disabled:opacity-50 text-white text-[10px] font-bold uppercase tracking-wider cursor-pointer">{busy ? 'Removing…' : 'Remove banner'}</button>
                    <button type="button" disabled={busy} onClick={() => setClearing(null)} className="px-3 py-2 rounded-lg border border-sp-bronze/40 text-text-warm/70 hover:text-white text-[10px] font-bold uppercase tracking-wider cursor-pointer">Cancel</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
