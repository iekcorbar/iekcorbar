import Link from 'next/link';
import { BookOpen } from 'lucide-react';
import { db } from '@/lib/db';

// Public, unauthenticated view of a single shared journal entry. Deliberately
// a server component (not 'use client' like the rest of this app's pages) so
// generateMetadata below can produce real Open Graph / Twitter card tags —
// that's what makes a pasted link actually render a title/description/image
// preview in Discord, WhatsApp, Twitter/X, etc. instead of a bare URL, which
// a client-only fetch could never do since those platforms read the HTML
// response directly rather than running JS.
const dateFormatter = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });

export async function generateMetadata({ params }) {
  const { token } = await params;
  const entry = await db.getJournalEntryByShareToken(token);
  if (!entry) {
    return { title: 'Logbook Page Not Found — Iekcorbar' };
  }
  const author = entry.mcUsername || 'A Player';
  const dateStr = dateFormatter.format(new Date(entry.entryDate));
  const preview = entry.content.length > 160 ? `${entry.content.slice(0, 157)}...` : entry.content;
  const title = `${author}'s Iekcorbar Logbook — ${dateStr}`;

  return {
    title: `${title} — Iekcorbar`,
    description: preview,
    openGraph: {
      title,
      description: preview,
      images: entry.imageUrl ? [entry.imageUrl] : undefined
    },
    twitter: {
      card: entry.imageUrl ? 'summary_large_image' : 'summary',
      title,
      description: preview,
      images: entry.imageUrl ? [entry.imageUrl] : undefined
    }
  };
}

export default async function SharedJournalPage({ params }) {
  const { token } = await params;
  const entry = await db.getJournalEntryByShareToken(token);

  if (!entry) {
    return (
      <div className="max-w-xl mx-auto py-24 text-center space-y-3">
        <BookOpen className="w-10 h-10 text-sp-copper/40 mx-auto" />
        <h1 className="font-serif text-xl text-sp-gold">Logbook Page Not Found</h1>
        <p className="text-sm text-text-warm/50">This link is invalid, or the page is no longer shared.</p>
        <Link href="/" className="inline-block text-sp-gold hover:underline text-sm">&larr; Back to Iekcorbar</Link>
      </div>
    );
  }

  const dateStr = dateFormatter.format(new Date(entry.entryDate));

  return (
    <div className="max-w-2xl mx-auto py-10 space-y-6">
      <div className="text-center space-y-1">
        <span className="text-[10px] uppercase tracking-widest text-text-warm/40 font-mono">Shared Logbook Page</span>
        <h1 className="font-serif text-2xl font-bold text-sp-gold flex items-center justify-center gap-2">
          <BookOpen className="w-6 h-6 text-sp-copper" /> {entry.mcUsername || 'A Player'}&apos;s Logbook
        </h1>
      </div>

      <div className="bg-sp-dark border-2 border-sp-gold rounded-xl p-6 steampunk-border shadow-xl space-y-4">
        <span className="font-serif text-sm text-sp-gold tracking-wide block">{dateStr}</span>
        {entry.imageUrl && (
          <img
            src={entry.imageUrl}
            alt="Logbook attachment"
            className="w-full max-h-96 object-cover rounded-lg border border-sp-bronze/30"
          />
        )}
        <p className="text-sm text-text-warm/85 leading-relaxed whitespace-pre-wrap font-serif">{entry.content}</p>
      </div>

      <div className="text-center">
        <Link href="/" className="text-xs text-sp-gold hover:underline">Visit Iekcorbar &rarr;</Link>
      </div>
    </div>
  );
}
