'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useUser } from '../providers';
import { Check, Copy, Gamepad2, HeartHandshake, Link2, UserPlus } from 'lucide-react';

// Bring-a-friend page. The code is derived from the Minecraft UUID by the
// /api/invite route, exactly like the FriendInvite plugin does in-game.
// Nothing here is required to play: the in-game path (/friend code) stays
// the authoritative fallback when no UUID is known on the website.

export default function InvitePage() {
  const { user, setIsLoginOpen } = useUser();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState('');

  useEffect(() => {
    if (!user) return;
    let alive = true;
    fetch('/api/invite', { cache: 'no-store' })
      .then((r) => r.json())
      .then((d) => { if (alive) { setData(d); setError(d.error || ''); } })
      .catch(() => { if (alive) setError('Could not load your invite code.'); });
    return () => { alive = false; };
  }, [user]);

  const copy = useCallback((value, key) => {
    if (!value) return;
    navigator.clipboard?.writeText(value).then(() => {
      setCopied(key);
      setTimeout(() => setCopied(''), 1800);
    });
  }, []);

  if (!user) {
    return (
      <div className="max-w-2xl mx-auto py-14 text-center space-y-4">
        <title>Invite a friend — Iekcorbar</title>
        <HeartHandshake className="w-10 h-10 mx-auto text-sp-gold" />
        <h1 className="font-serif text-3xl font-black text-sp-gold uppercase tracking-widest">Invite a friend</h1>
        <p className="text-text-warm/70 text-sm">Log in to get your personal invite code.</p>
        <button onClick={() => setIsLoginOpen(true)} className="px-5 py-2 rounded-lg bg-sp-gold text-black text-xs font-bold uppercase tracking-wider cursor-pointer">Log in</button>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto py-10 space-y-6">
      <title>Invite a friend — Iekcorbar</title>
      <div className="text-center space-y-2">
        <h1 className="font-serif text-3xl sm:text-4xl font-black text-sp-gold uppercase tracking-widest steampunk-glow">Bring a friend</h1>
        <p className="text-text-warm/70 text-sm">New players get a small starter reward — and so do you, once they have played 30 minutes.</p>
      </div>

      <div className="p-6 rounded-xl border border-sp-gold/50 bg-neutral-900/60 space-y-5">
        {data?.code ? (
          <>
            <div className="text-center space-y-2">
              <p className="text-[11px] uppercase tracking-widest text-text-warm/50">Your invite code</p>
              <button onClick={() => copy(data.code, 'code')} className="inline-flex items-center gap-3 px-5 py-2.5 rounded-lg bg-black/60 border border-sp-bronze/50 font-mono text-2xl tracking-[0.35em] text-white hover:border-sp-gold cursor-pointer">
                {data.code} {copied === 'code' ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4 text-sp-copper" />}
              </button>
              <p className="text-xs text-text-warm/50">Same code as <code className="font-mono text-sp-gold">/friend code</code> in-game.</p>
            </div>
            <div className="space-y-2">
              <p className="text-[11px] uppercase tracking-widest text-text-warm/50">Share this link</p>
              <button onClick={() => copy(data.shareUrl, 'link')} className="w-full flex items-center justify-between gap-3 px-3 py-2 rounded-lg bg-black/50 border border-sp-bronze/40 text-left cursor-pointer hover:border-sp-gold">
                <span className="font-mono text-xs text-white truncate">{data.shareUrl}</span>
                {copied === 'link' ? <Check className="w-4 h-4 text-emerald-400 shrink-0" /> : <Copy className="w-4 h-4 text-sp-copper shrink-0" />}
              </button>
            </div>
          </>
        ) : (
          <div className="text-center space-y-3">
            <p className="text-sm text-text-warm/70">
              {error || 'We could not match your account to a Minecraft UUID yet.'}
            </p>
            <p className="text-xs text-text-warm/50">No problem — join the server and use <code className="font-mono text-sp-gold">/friend code</code> to get your code in-game.</p>
          </div>
        )}
      </div>

      <div className="p-5 rounded-xl border border-sp-bronze/30 bg-neutral-900/30 space-y-3">
        <p className="font-semibold text-white flex items-center gap-2"><UserPlus className="w-4 h-4 text-sp-copper" /> How it works</p>
        <ol className="list-decimal list-inside space-y-2 text-sm text-text-warm/80">
          <li>Share your code or link with someone who has never played here.</li>
          <li>They register on the website, join the server and use <code className="font-mono text-sp-gold">/friend claim &lt;code&gt;</code> once.</li>
          <li>After 30 minutes of playtime you both get a cosmetic relic and a small starter kit. The bot announces it in our Discord.</li>
        </ol>
        <p className="text-xs text-text-warm/50">You can invite as many friends as you like. One claim per new player; no self-invites.</p>
      </div>

      <div className="flex flex-wrap gap-2 text-xs">
        <Link href="/welcome" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">New here? Start guide</Link>
        <Link href="/friends" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Website friends</Link>
        <a href="https://discord.gg/G9P2khU7F4" target="_blank" rel="noopener noreferrer" className="px-3 py-1.5 rounded-full border border-sp-bronze/40 hover:border-sp-gold text-sp-gold">Discord</a>
      </div>
      <p className="flex items-center gap-2 text-text-warm/50 text-xs"><Gamepad2 className="w-3.5 h-3.5" /> Java Edition server: <code className="font-mono text-sp-gold">play.iekcorbar.com</code> <Link2 className="w-3.5 h-3.5" /></p>
    </div>
  );
}
