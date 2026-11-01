import React from 'react';
import Link from 'next/link';
import { Compass, MapPin, ScrollText, Search, Cog, PawPrint, Wine, Flame, ThumbsUp, Lightbulb } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';

export const metadata = {
  title: 'Your first hours on Iekcorbar',
  description: 'A step-by-step route for new players: claim land, find your way with recipes and quests, then pick a path - farming and cooking, brewing, machines or wildlife.',
};

function Step({ n, icon: Icon, title, children }) {
  return (
    <section className="bg-neutral-900/60 border-2 border-sp-bronze/40 rounded-2xl p-6 steampunk-glass flex gap-4">
      <div className="shrink-0 w-10 h-10 rounded-full bg-sp-gold text-black font-black flex items-center justify-center">{n}</div>
      <div className="space-y-2 min-w-0">
        <h2 className="flex items-center gap-2 font-serif text-xl text-sp-gold font-bold uppercase tracking-wide">
          <Icon className="w-5 h-5 text-sp-copper" /> {title}
        </h2>
        <div className="text-sm text-text-warm/80 space-y-2 leading-relaxed">{children}</div>
      </div>
    </section>
  );
}

function Path({ href, icon: Icon, title, children }) {
  return (
    <Link href={href} className="group rounded-xl border border-sp-bronze/30 bg-black/40 p-4 space-y-1.5 hover:border-sp-gold transition-colors block">
      <p className="flex items-center gap-2 font-serif text-lg text-sp-gold leading-tight"><Icon className="w-5 h-5 text-sp-copper" /> {title}</p>
      <p className="text-sm text-text-warm/80">{children}</p>
      <p className="text-[11px] uppercase tracking-widest text-sp-copper group-hover:text-sp-gold">Read the guide →</p>
    </Link>
  );
}

export default function StartPage() {
  return (
    <div className="space-y-6 pb-12 max-w-4xl mx-auto">
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-950/20 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <Compass className="w-3.5 h-3.5" /> New here?
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">
            Your First Hours
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">
            Iekcorbar has a lot going on, and none of it needs mods. This is the short route: five steps to get
            settled, then pick the path that sounds most fun. Everything links to a full guide when you want more.
          </p>
        </div>
      </section>

      <Step n={1} icon={ScrollText} title="Join and say hello">
        <p>
          Create your account, accept the rules and link your Minecraft name on the{' '}
          <Link href="/welcome" className="text-sp-gold underline">welcome page</Link>. Then join the server and look around.
          The <Link href="/map" className="text-sp-gold underline">live map</Link> shows where everyone has built.
        </p>
      </Step>

      <Step n={2} icon={MapPin} title="Claim a place of your own">
        <p>
          Find a spot you like, stand on it and type <code>/gouw claim</code>. That chunk is yours: nobody else can build in
          it, open your chests or hurt your animals. Typing <code>/gouw</code> alone opens a menu. Want friends to
          build with you? <code>/gouw trust &lt;name&gt;</code>. See the <Link href="/gouw" className="text-sp-gold underline">Gouw guide</Link>.
        </p>
      </Step>

      <Step n={3} icon={Search} title="Learn to look things up">
        <p>
          Not sure how to craft something? Type <code>/zukan</code> in game: it is a recipe browser covering vanilla and every
          custom item of the server. The website has the same recipes on the{' '}
          <Link href="/recipes" className="text-sp-gold underline">recipes page</Link>, and the{' '}
          <Link href="/wiki" className="text-sp-gold underline">wiki</Link> explains the plugins and the rules of the world.
        </p>
      </Step>

      <Step n={4} icon={ScrollText} title="Talk to the people in town">
        <p>
          Villagers and merchants around the spawn offer <b>quests</b>: bring them items and earn money and rewards. Open
          your quest book with <code>/questbook</code>. Several quest lines teach a whole system step by step, which is the
          friendliest way to learn it.
        </p>
      </Step>

      <Step n={5} icon={ThumbsUp} title="Vote, and stay in touch">
        <p>
          <Link href="/vote" className="text-sp-gold underline">Voting</Link> for the server gives rewards and helps new players find us.
          Join the Discord for news and events; the <Link href="/news" className="text-sp-gold underline">news page</Link> and{' '}
          <Link href="/events" className="text-sp-gold underline">events</Link> show what is coming up.
        </p>
      </Step>

      <section className="space-y-3">
        <div className="text-center space-y-1 pt-2">
          <p className="text-[11px] uppercase tracking-widest text-sp-copper/80">Then pick a path</p>
          <h2 className="font-serif text-2xl text-sp-gold font-bold uppercase tracking-wide">What sounds fun?</h2>
        </div>
        <div className="grid sm:grid-cols-2 gap-4">
          <Path href="/steamworks" icon={Cog} title="Build machines">Gears, steam, trains, ores and oil. Rotation-powered contraptions for people who like to engineer.</Path>
          <Path href="/sakagura" icon={Wine} title="Brew drinks">Grow a garden, cook in cauldrons, distil in a Steam Still and age your drinks in casks.</Path>
          <Path href="/wildlife" icon={PawPrint} title="Watch wildlife">Find new animals, fill your Field Journal, raise young and build a zoo.</Path>
          <Path href="/dragons" icon={Flame} title="Tame a dragon">Deep in the caves lives a Steam Dragon. Tame it, ride it and give it a nest.</Path>
        </div>
      </section>

      <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-5 text-sm text-text-warm/80 flex gap-3">
        <Lightbulb className="w-5 h-5 text-sp-copper shrink-0 mt-0.5" />
        <p>
          Stuck, or found something broken? Ask in the Discord or ping a staff member in game. Bugs go on the{' '}
          <Link href="/report-bug" className="text-sp-gold underline">report a bug</Link> page.
        </p>
      </div>
      <BuiltBy what="This guide" />
    </div>
  );
}
