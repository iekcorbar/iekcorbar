import React, { Suspense } from 'react';
import SearchClient from './SearchClient';

export const metadata = {
  title: 'Search — Iekcorbar',
  description: 'Search the guides, the wiki, the news and every custom recipe of Iekcorbar.',
};

export default function SearchPage() {
  return (
    <Suspense fallback={<p className="text-center text-text-warm/60 py-12">Loading…</p>}>
      <SearchClient />
    </Suspense>
  );
}
