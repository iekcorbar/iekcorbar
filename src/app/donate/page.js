'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { HeartHandshake, Server, Sparkles, Scale, ExternalLink, Loader2 } from 'lucide-react';

// Donations page (added 2026-10-26). Deliberately a voluntary-support page,
// NOT a store: real money never buys anything here or in the Shop — the Shop
// only spends in-game currencies (Vault balance, Elite coins, website Gears).
// The PayPal.me link itself is a staff-editable setting (donate_paypal_url,
// General Settings) rather than hardcoded, so it can be changed or emptied
// without a redeploy; empty = no button, just the "not open right now" note.
export default function DonatePage() {
  const [paypalUrl, setPaypalUrl] = useState(null); // null = still loading, '' = not configured

  useEffect(() => {
    fetch('/api/settings')
      .then(r => r.json())
      .then(d => setPaypalUrl(d.settings?.donate_paypal_url || ''))
      .catch(() => setPaypalUrl(''));
  }, []);

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-sp-gold tracking-wide flex items-center gap-2 uppercase">
          <HeartHandshake className="w-8 h-8 text-sp-copper" /> Support iekcorbar
        </h1>
        <p className="text-sm text-text-warm/70 mt-2">
          iekcorbar is a free, independent hobby project run by its community team. Nothing here
          costs money to play — but the server does.
        </p>
      </div>

      <div className="bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3">
        <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2 border-b border-sp-bronze/20 pb-2">
          <Server className="w-5 h-5 text-sp-copper" /> Where your support goes
        </h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          Running the server, hosting the website and keeping everything online costs real money
          every month. Donations go to covering those costs first — and what&apos;s left over can
          go toward rewarding the team that builds and runs the project. We&apos;ll always be
          transparent about that split.
        </p>
      </div>

      <div className="bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3">
        <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2 border-b border-sp-bronze/20 pb-2">
          <Sparkles className="w-5 h-5 text-sp-copper" /> What you get for it
        </h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          Honestly: our thanks, and nothing else that affects the game. Donating gives you{" "}
          <strong>no gameplay advantage, no currency and no items</strong> — the{" "}
          <Link href="/shop" className="text-sp-gold hover:text-white underline">Shop</Link>{" "}
          is entirely driven by in-game currencies you earn by playing, and that separation is
          by design. If we ever add donor-only cosmetics, they will be purely visual, announced
          openly, and still never required to play.
        </p>
      </div>

      <div className="bg-neutral-900/30 border border-sp-bronze/40 p-6 rounded-xl space-y-3">
        <h2 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2 border-b border-sp-bronze/20 pb-2">
          <Scale className="w-5 h-5 text-sp-copper" /> The fine print, in plain words
        </h2>
        <ul className="list-disc pl-5 space-y-2 text-sm text-text-warm/85 leading-relaxed">
          <li>Donations are <strong>100% voluntary</strong> — never an obligation, never a purchase of in-game goods.</li>
          <li>Payments go through <strong>PayPal</strong>; we never see or store your bank or card details. Your name and email become known to PayPal and to the account holder for that transaction.</li>
          <li>Because a donation is a gift and not a sale, there is no statutory right of withdrawal or refund — if something went wrong anyway (a mistaken amount, for example), just talk to us on Discord and we&apos;ll make it right.</li>
          <li>iekcorbar is a private community project, not a registered company; it has no KvK number or BTW/VAT obligation on voluntary gifts.</li>
        </ul>
      </div>

      <div className="text-center pt-2 space-y-3">
        {paypalUrl === null ? (
          <Loader2 className="w-5 h-5 animate-spin inline text-sp-gold" />
        ) : paypalUrl ? (
          <a
            href={paypalUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 px-8 py-4 rounded-xl bg-sp-gold hover:bg-white text-black font-serif font-bold uppercase tracking-wider text-sm transition-all shadow-lg shadow-sp-gold/20"
          >
            <HeartHandshake className="w-5 h-5" /> Donate via PayPal <ExternalLink className="w-4 h-4 opacity-60" />
          </a>
        ) : (
          <p className="text-xs text-text-warm/50 italic">Donations aren&apos;t open right now — check back later or ask on Discord.</p>
        )}
        <p className="text-[10px] text-text-warm/40">
          Prefer to just play? That works too — every vote, journal entry and hour online keeps this project going.{' '}
          <Link href="/terms" className="text-sp-gold hover:text-white underline">Terms</Link> ·{' '}
          <Link href="/privacy" className="text-sp-gold hover:text-white underline">Privacy</Link>
        </p>
      </div>
    </div>
  );
}
