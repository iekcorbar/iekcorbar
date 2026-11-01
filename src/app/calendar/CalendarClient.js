'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { Calendar, Gift, Sparkles, Sprout, Globe } from 'lucide-react';

// Easter Sunday (Gregorian), the usual anonymous algorithm.
function easter(y) {
  const a = y % 19, b = Math.floor(y / 100), c = y % 100;
  const d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return { month, day };
}

// Seasonal prop collections. start/end are month-day in the year they begin; "open" is
// the stretch where the collection is the main thing going on.
function holidays(year) {
  const ea = easter(year);
  return [
    { name: "Valentine's", detail: 'Chocolate hearts, love locks and the wishing well.', month: 2, day: 14, endMonth: 2, endDay: 14 },
    { name: "St. Patrick's Day", detail: 'A pot of gold once a day, rainbow arches and green ale.', month: 3, day: 17, endMonth: 3, endDay: 17 },
    { name: 'Easter', detail: 'The egg hunt: every player can find each hidden egg once.', month: ea.month, day: ea.day, endMonth: ea.month, endDay: ea.day },
    { name: 'Halloween', detail: "Shoutlander's Shadow relics and the Halloween quest.", month: 10, day: 31, endMonth: 10, endDay: 31 },
    { name: 'Xmas Town', detail: 'The advent calendar opens one door a day from 1 to 25 December. Gift boxes can be opened on 24-26 December.', month: 12, day: 1, endMonth: 12, endDay: 26 },
    { name: 'New Year', detail: 'Countdown boards, the temple bell and fireworks at midnight.', month: 12, day: 31, endMonth: 12, endDay: 31 },
  ];
}

const DAY = 86400000;

const SEASONS = {
  SPRING: { name: 'Spring', emoji: '🌸', line: 'Cherry blossoms and fresh growth', crops: 'cherry, ume, peach, strawberry, apricot, mikan, kiwi, raspberry' },
  SUMMER: { name: 'Summer', emoji: '☀️', line: 'Lush growth and tropical fruits', crops: 'yuzu, mango, papaya, guava, dragon fruit, vanilla, chili, maize' },
  AUTUMN: { name: 'Autumn', emoji: '🍂', line: 'Harvest time and falling leaves', crops: 'grape, apple, pear, persimmon, fig, olive, barley, rye, hops' },
  WINTER: { name: 'Winter', emoji: '❄️', line: 'Dormant gardens and hardy crops', crops: 'ginger, wasabi, daikon, rice, juniper, blueberry' },
};
const ORDER = ['SPRING', 'SUMMER', 'AUTUMN', 'WINTER'];

function left(ms) {
  if (ms <= 0) return 'any moment now';
  const d = Math.floor(ms / DAY), h = Math.floor((ms % DAY) / 3600000), m = Math.floor((ms % 3600000) / 60000);
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m}m` : `${m}m`;
}

function nextOf(h, now) {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  for (const y of [now.getFullYear(), now.getFullYear() + 1]) {
    const hs = holidays(y).find(x => x.name === h.name);
    const start = new Date(y, hs.month - 1, hs.day);
    const end = new Date(y, hs.endMonth - 1, hs.endDay);
    if (end >= today) {
      const running = start <= today;
      return { ...hs, start, end, running, days: running ? 0 : Math.round((start - today) / DAY), endsIn: Math.round((end - today) / DAY) };
    }
  }
  return null;
}

function fmtDay(d) {
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long' });
}

export default function CalendarClient() {
  const [events, setEvents] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [now, setNow] = useState(null);
  const [season, setSeason] = useState(null);

  useEffect(() => {
    setNow(new Date());
    fetch('/api/events').then(r => r.json()).then(d => {
      setEvents(Array.isArray(d) ? d : (d.events || []));
    }).catch(() => {}).finally(() => setLoaded(true));
  }, []);

  useEffect(() => {
    fetch('/api/season').then(r => r.json()).then(d => setSeason(d.season || null)).catch(() => {});
  }, []);

  const upcoming = useMemo(() => {
    return events
      .map(e => ({ ...e, when: new Date(`${e.date}T${e.time}:00+02:00`) }))
      .filter(e => !isNaN(e.when))
      .sort((a, b) => a.when - b.when);
  }, [events]);

  const specials = useMemo(() => {
    if (!now) return [];
    return ['Valentine\'s', "St. Patrick's Day", 'Easter', 'Halloween', 'Xmas Town', 'New Year']
      .map(n => nextOf({ name: n }, now)).filter(Boolean)
      .sort((a, b) => a.days - b.days);
  }, [now]);

  return (
    <div className="space-y-6 pb-12 max-w-4xl mx-auto">
      <section className="text-center space-y-3 pt-2">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
          <Calendar className="w-3.5 h-3.5" /> What is coming
        </div>
        <h1 className="font-serif text-4xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">Calendar</h1>
        <p className="text-text-warm/70 text-sm max-w-xl mx-auto">
          Community events, the holidays the server celebrates and the rhythm of the garden, in one place.
        </p>
      </section>

      <section className="bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 steampunk-glass space-y-4">
        <h2 className="flex items-center gap-2 font-serif text-xl text-sp-gold font-bold uppercase tracking-wide"><Calendar className="w-5 h-5 text-sp-copper" /> Events</h2>
        {!loaded && <p className="text-sm text-text-warm/60">Loading…</p>}
        {loaded && upcoming.length === 0 && (
          <p className="text-sm text-text-warm/70">No events are planned right now. Check the <Link href="/news" className="text-sp-gold underline">news</Link> or the Discord.</p>
        )}
        <ul className="space-y-3">
          {upcoming.map(e => (
            <li key={e.id} className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4 space-y-1">
              <p className="font-serif text-lg text-sp-gold leading-tight">{e.title}</p>
              <p className="flex items-center gap-2 text-xs text-sp-copper" suppressHydrationWarning>
                <Globe className="w-3.5 h-3.5" /> {e.when.toLocaleString(undefined, { dateStyle: 'full', timeStyle: 'short' })} (your time)
              </p>
              <p className="text-sm text-text-warm/80 line-clamp-3">{e.description}</p>
            </li>
          ))}
        </ul>
        <Link href="/events" className="inline-block text-sm text-sp-gold underline">All events and sign-ups →</Link>
      </section>

      <section className="bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 steampunk-glass space-y-4">
        <h2 className="flex items-center gap-2 font-serif text-xl text-sp-gold font-bold uppercase tracking-wide"><Gift className="w-5 h-5 text-sp-copper" /> Holidays on the server</h2>
        <p className="text-sm text-text-warm/80">Each holiday has its own collection of props, treats and small surprises. Dates follow the real calendar.</p>
        <ul className="grid sm:grid-cols-2 gap-3">
          {specials.map(h => (
            <li key={h.name} className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4 space-y-1">
              <p className="font-serif text-lg text-sp-gold leading-tight">{h.name}</p>
              <p className="text-[11px] uppercase tracking-widest text-sp-copper" suppressHydrationWarning>
                {h.running
                  ? `Happening now${h.endsIn > 0 ? ` · ${h.endsIn} day${h.endsIn === 1 ? '' : 's'} left` : ' · last day'}`
                  : h.days === 1 ? 'Tomorrow' : `In ${h.days} days · ${fmtDay(h.start)}`}
              </p>
              <p className="text-sm text-text-warm/80">{h.detail}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 steampunk-glass space-y-3">
        <h2 className="flex items-center gap-2 font-serif text-xl text-sp-gold font-bold uppercase tracking-wide"><Sprout className="w-5 h-5 text-sp-copper" /> The garden&apos;s rhythm</h2>
        {season && now && SEASONS[season.season] && (() => {
          const cur = SEASONS[season.season];
          const nextKey = ORDER[(ORDER.indexOf(season.season) + 1) % 4];
          const rem = season.next - now.getTime();
          return (
            <div className="rounded-xl border border-sp-gold/50 bg-black/50 p-4 space-y-1" suppressHydrationWarning>
              <p className="text-[11px] uppercase tracking-widest text-sp-copper">Right now</p>
              <p className="font-serif text-2xl text-sp-gold leading-tight">{cur.emoji} {cur.name}</p>
              <p className="text-sm text-text-warm/80">{cur.line}. In season: {cur.crops}.</p>
              <p className="text-sm text-text-warm/80">
                {rem > 0 ? <>Next season, <b>{SEASONS[nextKey].name}</b>, starts in <b>{left(rem)}</b>.</> : <>The next season, <b>{SEASONS[nextKey].name}</b>, is about to start.</>}
              </p>
            </div>
          );
        })()}
        <p className="text-sm text-text-warm/80">
          The Sakagura garden changes season every <b>seven real days</b>: spring, summer, autumn, winter, and round again. A
          crop in its own season grows at full speed; out of season it grows 30-70% slower. The server announces each change, and{' '}
          <code>/brew season status</code> shows the current season and when the next one starts. Brewing competitions are announced
          on the news page and the Discord.
        </p>
        <Link href="/sakagura#seasons" className="inline-flex items-center gap-1.5 text-sm text-sp-gold underline"><Sparkles className="w-3.5 h-3.5" /> More about seasons</Link>
      </section>
    </div>
  );
}
