import React from 'react';
import Link from 'next/link';
import { Sparkles, ArrowRight } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';
import { WHATS_NEW } from '@/lib/whats-new';

export const metadata = {
  title: "What's new — Iekcorbar",
  description: 'The latest changes on Iekcorbar in plain language: new features, guides and updates, newest first.',
};

function fmt(d) {
  return new Date(`${d}T12:00:00Z`).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
}

export default function WhatsNewPage() {
  return (
    <div className="space-y-6 pb-12 max-w-3xl mx-auto">
      <section className="text-center space-y-3 pt-2">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
          <Sparkles className="w-3.5 h-3.5" /> Changelog
        </div>
        <h1 className="font-serif text-4xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">What&apos;s New</h1>
        <p className="text-text-warm/70 text-sm max-w-xl mx-auto">
          The latest changes in plain language, newest first. Announcements and events live on the{' '}
          <Link href="/news" className="text-sp-gold underline">news page</Link>, and the{' '}
          <Link href="/calendar" className="text-sp-gold underline">calendar</Link> shows what is coming.
        </p>
      </section>

      <ol className="space-y-5">
        {WHATS_NEW.map((e, i) => (
          <li key={i} className="bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 steampunk-glass space-y-3">
            <p className="flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-widest text-sp-copper/80">
              <time dateTime={e.date}>{fmt(e.date)}</time>
              <span aria-hidden="true">·</span>
              <span>{e.tag}</span>
            </p>
            <h2 className="font-serif text-xl text-sp-gold font-bold">{e.title}</h2>
            <ul className="list-disc pl-5 text-sm text-text-warm/80 space-y-1.5">
              {e.items.map((t, j) => <li key={j}>{t}</li>)}
            </ul>
            {e.link && (
              <Link href={e.link.href} className="inline-flex items-center gap-1.5 text-sm text-sp-gold hover:text-white underline underline-offset-2">
                {e.link.label} <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            )}
          </li>
        ))}
      </ol>
      <BuiltBy what="This page" />
    </div>
  );
}
