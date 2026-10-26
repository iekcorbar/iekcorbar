import { CheckCircle2, XCircle } from 'lucide-react';
import { db } from '@/lib/db';
import { verifyUnsubscribeToken } from '@/lib/unsubscribe-token';

// Public, unauthenticated one-click unsubscribe — the whole point of a
// mailed link like this is that clicking it (no login, no JS, no form
// submit) is enough, so this does the actual DB write directly during the
// server-rendered GET rather than requiring a separate API round-trip.
// Server component (not 'use client') for exactly that reason.
export const metadata = { title: 'Unsubscribe — Iekcorbar' };

export default async function NewsletterUnsubscribePage({ searchParams }) {
  const { email, token } = await searchParams;
  const valid = email && token && verifyUnsubscribeToken(email, token);

  let saved = false;
  if (valid) {
    saved = await db.setNewsletterOptOut(email, true);
  }

  return (
    <div className="max-w-md mx-auto py-24 px-4 text-center space-y-4">
      {valid && saved ? (
        <>
          <CheckCircle2 className="w-10 h-10 text-green-400 mx-auto" />
          <h1 className="font-serif text-xl text-sp-gold uppercase tracking-wide">You're Unsubscribed</h1>
          <p className="text-sm text-text-warm/70 leading-relaxed">
            {email} won't receive any more newsletter emails from iekcorbar. You can turn these back on any time from your Profile page.
          </p>
        </>
      ) : (
        <>
          <XCircle className="w-10 h-10 text-rose-400 mx-auto" />
          <h1 className="font-serif text-xl text-rose-300 uppercase tracking-wide">Link Not Valid</h1>
          <p className="text-sm text-text-warm/70 leading-relaxed">
            This unsubscribe link is invalid, expired, or already used. If you're still getting emails you'd rather not, log in and turn off newsletter emails from your Profile page instead.
          </p>
        </>
      )}
    </div>
  );
}
