import Link from 'next/link';
import { Hammer } from 'lucide-react';

// A quiet credit line for pages where visitors admire the work (guides, home).
// Deliberately small: no box, no colour block, no animation - it only has to be there
// for the person who wonders "who made this?". Leads to /hire.
export default function BuiltBy({ what = 'This' }) {
  return (
    <p className="mt-10 pt-4 border-t border-sp-bronze/15 text-center text-xs text-text-warm/45 flex items-center justify-center gap-1.5 flex-wrap">
      <Hammer className="w-3 h-3 text-sp-copper/60" />
      <span>{what} was built by one person.</span>
      <Link href="/behind-the-scenes" className="hover:text-sp-gold underline underline-offset-2 transition-colors">See how it is made</Link>
      <span aria-hidden="true">&middot;</span>
      <Link href="/hire" className="text-sp-copper/80 hover:text-sp-gold underline underline-offset-2 transition-colors">
        Want something similar? Hire the builder
      </Link>
    </p>
  );
}
