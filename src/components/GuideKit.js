import React from 'react';
import Link from 'next/link';

// Shared building blocks for the plain-language guide pages (/kitchen, /relics, /tools).
// Same look as /sakagura, /wildlife, /gouw and /dragons, which still carry their own copies.

export function Hero({ icon: Icon, kicker, title, children, nav, tint = 'from-amber-950/20' }) {
  return (
    <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
      <div className={`absolute inset-0 bg-gradient-to-br ${tint} via-black to-sp-charcoal/40`} />
      <div className="relative z-10 space-y-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
          <Icon className="w-3.5 h-3.5" /> {kicker}
        </div>
        <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">{title}</h1>
        <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">{children}</p>
        {nav && (
          <nav className="flex flex-wrap justify-center gap-2 pt-2 text-xs">
            {nav.map(([h, l]) => (
              <a key={h} href={h} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold hover:border-sp-gold">{l}</a>
            ))}
          </nav>
        )}
      </div>
    </section>
  );
}

export function Section({ id, icon: Icon, kicker, title, children }) {
  return (
    <section id={id} className="scroll-mt-24 bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 sm:p-8 steampunk-glass space-y-5">
      <div>
        <p className="text-[11px] uppercase tracking-widest text-sp-copper/80 mb-1">{kicker}</p>
        <h2 className="flex items-center gap-3 font-serif text-2xl text-sp-gold font-bold uppercase tracking-wide">
          <Icon className="w-6 h-6 text-sp-copper" /> {title}
        </h2>
      </div>
      {children}
    </section>
  );
}

export function Card({ title, stat, children }) {
  return (
    <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4 sm:p-5 space-y-2">
      <div className="min-w-0">
        <p className="font-serif text-lg text-sp-gold leading-tight">{title}</p>
        {stat && <p className="text-[11px] text-sp-copper uppercase tracking-wider">{stat}</p>}
      </div>
      <div className="text-sm text-text-warm/80 space-y-1.5">{children}</div>
    </div>
  );
}

export function Table({ head, rows }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-[11px] uppercase tracking-widest text-sp-gold/70 border-b border-sp-bronze/30">
            {head.map(h => <th key={h} className="py-2 pr-4">{h}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-b border-sp-bronze/10 align-top">
              {r.map((c, j) => <td key={j} className={`py-2 pr-4 ${j === 0 ? 'text-sp-gold/90 font-medium whitespace-nowrap' : 'text-text-warm/80'}`}>{c}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function WikiButtons({ links }) {
  return (
    <div className="flex flex-wrap justify-center gap-3 pt-2 text-sm">
      {links.map(([href, label, Icon]) => (
        <Link key={href} href={href} className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><Icon className="w-4 h-4" /> {label}</Link>
      ))}
    </div>
  );
}
