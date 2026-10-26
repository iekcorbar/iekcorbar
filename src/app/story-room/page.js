'use client';

import React, { useEffect, useState } from 'react';
import { ScrollText, KeyRound, LogOut } from 'lucide-react';
import StoryRoomTab from '@/components/StoryRoomTab';

// Story Room outside the Staff Panel — for writers WITHOUT a website
// account, who arrive through a personal guest link (/story-room?key=...,
// made in Story Room → Guest links). The key is swapped for a cookie once
// and then removed from the address bar, so it doesn't linger in history or
// get copied along with the URL. Staff with Story Room access can use this
// page too. See src/lib/story-access.js.

const LEVEL_LABELS = { edit: 'can edit', comment: 'can read and comment', read: 'can read' };

export default function StoryRoomPage() {
  const [state, setState] = useState('loading'); // loading | ready | denied
  const [access, setAccess] = useState(null);
  const [linkError, setLinkError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const key = new URLSearchParams(window.location.search).get('key');
      if (key) {
        window.history.replaceState(null, '', '/story-room');
        try {
          const res = await fetch('/api/story/guest-session', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key })
          });
          if (!res.ok) {
            const d = await res.json().catch(() => ({}));
            if (!cancelled) setLinkError(d.error || 'This guest link could not be used.');
          }
        } catch {
          if (!cancelled) setLinkError('Could not reach the website. Check your connection and open the link again.');
        }
      }
      try {
        const res = await fetch('/api/story/guest-session');
        const d = await res.json().catch(() => ({}));
        if (cancelled) return;
        if (res.ok && d.access) { setAccess(d.access); setState('ready'); }
        else setState('denied');
      } catch {
        if (!cancelled) setState('denied');
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const leave = async () => {
    if (!window.confirm('Leave the Story Room on this device? You can come back any time by opening your guest link again.')) return;
    await fetch('/api/story/guest-session', { method: 'DELETE' }).catch(() => {});
    window.location.href = '/';
  };

  if (state === 'loading') {
    return <div className="py-16 text-center text-xs text-text-warm/50 italic">Opening the Story Room…</div>;
  }

  if (state === 'denied') {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-sp-bronze p-8 rounded-xl text-center steampunk-border shadow-2xl space-y-4">
        <div className="w-14 h-14 bg-sp-gold/10 rounded-full flex items-center justify-center border border-sp-gold/50 text-sp-gold mx-auto">
          <KeyRound className="w-7 h-7" />
        </div>
        <h1 className="font-serif text-xl font-bold text-sp-gold">Story Room</h1>
        <p className="text-sm text-text-warm/75">
          {linkError || 'This is the workspace of the iekcorbar story team. To get in, open the personal guest link you were sent.'}
        </p>
        <p className="text-[11px] text-text-warm/45">
          Staff can also log in with their website account. No link, or did yours stop working? Ask the iekcorbar staff for a new one.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {linkError && (
        <div className="text-xs px-3 py-2 rounded-lg border bg-crimson/20 border-crimson text-rose-200">{linkError}</div>
      )}
      <div className="flex flex-wrap items-center gap-3 bg-neutral-900/40 border border-sp-bronze/30 rounded-xl px-4 py-2.5 text-[11px] text-text-warm/70">
        <ScrollText className="w-4 h-4 text-sp-copper shrink-0" />
        {access.isGuest ? (
          <span>
            You're working as <b className="text-white">{access.guestName}</b> (guest) — you {LEVEL_LABELS[access.level] || 'can read'}.
            {access.expiresAt && <> Your link works until {new Date(access.expiresAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })}.</>}
          </span>
        ) : (
          <span>You're logged in as staff — this is the same Story Room as in the Staff Panel.</span>
        )}
        <span className="flex-1" />
        {access.isGuest && (
          <button onClick={leave} className="flex items-center gap-1 text-text-warm/60 hover:text-white cursor-pointer">
            <LogOut className="w-3.5 h-3.5" /> Leave
          </button>
        )}
      </div>
      <StoryRoomTab guestMode={access.isGuest} isAdmin={!!access.isAdmin} canPublishToWiki={!!access.canPublish} />
    </div>
  );
}
