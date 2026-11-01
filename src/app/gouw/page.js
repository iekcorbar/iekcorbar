import React from 'react';
import Link from 'next/link';
import { MapPin, Shield, Users, Terminal, Lightbulb, BookOpen, ListOrdered } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';

export const metadata = {
  title: 'Gouw land claims, explained — Iekcorbar',
  description: 'How to claim land on Iekcorbar with Gouw: claiming chunks, trusting friends, what is protected, the menu, limits per rank and every command.',
};

function Section({ id, icon: Icon, kicker, title, children }) {
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

function Card({ title, stat, children }) {
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

function Table({ head, rows }) {
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

const NAV = [
  ['#idea', 'The idea'],
  ['#claim', 'Claiming'],
  ['#protect', 'Protection'],
  ['#trust', 'Friends'],
  ['#limits', 'Limits'],
  ['#commands', 'Commands'],
  ['#faq', 'FAQ'],
];

export default function GouwPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-emerald-950/20 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <MapPin className="w-3.5 h-3.5" /> Gouw · Land claims
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">
            Claim Your Land
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">
            A <b>gouw</b> (Dutch for &quot;county&quot;) is a piece of land you claim, one chunk at a time. Inside it only you and
            the people you trust can build, open containers or fight. It is free, it takes one command, and
            your builds, chests and animals are safe while you are away. The{' '}
            <Link href="/wiki?page=gouw" className="text-sp-gold underline">wiki</Link> keeps the short reference.
          </p>
          <nav className="flex flex-wrap justify-center gap-2 pt-2 text-xs">
            {NAV.map(([h, l]) => (
              <a key={h} href={h} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold hover:border-sp-gold">{l}</a>
            ))}
          </nav>
        </div>
      </section>

      <Section id="idea" icon={MapPin} kicker="Start here" title="The big idea">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          The world is divided into <b>chunks</b>: squares of 16 × 16 blocks, from bedrock to sky. A gouw is a group of
          chunks that belongs to you. Stand in a chunk, run <code>/gouw claim</code>, and it is yours. Claiming costs
          nothing; you are only limited by how many chunks you may own. Everything that was claimed in the old Lands
          plugin was carried over automatically.
        </p>
      </Section>

      <Section id="claim" icon={MapPin} kicker="Your first plot" title="Claiming land">
        <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>Walk to the spot where you want to build.</li>
          <li>Type <code>/gouw border</code> first if you want to see where the chunk edges are. Gold dust outlines them for a moment.</li>
          <li>Type <code>/gouw claim</code>. The action bar confirms it.</li>
          <li>For a base in one go, use <code>/gouw autoclaim 2</code>: it claims a 5 × 5 area around you (radius 1 is 3 × 3).</li>
          <li>Typing just <code>/gouw</code> opens a <b>menu</b> with buttons for all of this, if you prefer clicking.</li>
        </ol>
        <p className="text-sm text-text-warm/80">
          Gave up on a plot? <code>/gouw unclaim</code> returns the chunk under your feet, and <code>/gouw unclaim all</code> gives
          back everything at once.
        </p>
      </Section>

      <Section id="protect" icon={Shield} kicker="What you get" title="What a gouw protects">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Blocks & buckets">
            <p>Outsiders cannot break or place blocks, and cannot empty or fill buckets.</p>
          </Card>
          <Card title="Chests, doors & buttons">
            <p>Containers, doors, buttons, beds, anvils, cauldrons and other interactive blocks stay closed to strangers.</p>
          </Card>
          <Card title="Animals & fights">
            <p>Outsiders cannot attack your animals or the mobs living there, and players cannot fight each other inside it.</p>
          </Card>
          <Card title="Explosions & pistons">
            <p>Creepers and TNT do not take claimed blocks. Pistons cannot drag blocks across the border of a gouw.</p>
          </Card>
        </div>
        <p className="text-sm text-text-warm/80">
          When someone walks in or out of a gouw that is not theirs, they see a message on the action bar and the
          border pulses once. Staff can step through any gouw to help, with a special bypass toggle.
        </p>
      </Section>

      <Section id="trust" icon={Users} kicker="Build together" title="Trusting friends">
        <p className="text-sm text-text-warm/80">
          <code>/gouw trust &lt;name&gt;</code> adds a member and <code>/gouw untrust &lt;name&gt;</code> removes them. Members may build,
          open containers, hurt mobs and fight, as you can. In the <code>/gouw</code> menu, four buttons
          (<b>Build, Open, Mobs, Players</b>) loosen or tighten those rights for <i>all</i> members at once. The menu can also
          add a member by typing their name in chat; they must have joined the server before.
        </p>
        <p className="text-sm text-text-warm/80">
          <code>/gouw members</code> lists everyone, and <code>/gouw leave</code> steps you out of a gouw where someone trusted you.
        </p>
      </Section>

      <Section id="limits" icon={Shield} kicker="How much land" title="Limits & ranks">
        <p className="text-sm text-text-warm/80">
          A new player may claim <b>16 chunks</b>. Your <b>play-time rank</b> raises that limit as you climb the ladder, up to a
          hundred chunks at the top, so there is always a reason to keep playing. <code>/gouw info</code> shows the gouw under
          your feet, <code>/gouw list</code> shows everything you own, and <code>/gouw near</code> shows other gouws around you so
          you know where you can still build.
        </p>
        <p className="text-sm text-text-warm/80">
          Claims do not expire right now. They stay yours even if you are away for a long time.
        </p>
      </Section>

      <Section id="commands" icon={Terminal} kicker="Quick reference" title="Commands">
        <p className="text-sm text-text-warm/80">One command does it all: <code>/gouw</code> (short <code>/g</code>, also <code>/claim</code>).</p>
        <Table
          head={['Command', 'What it does']}
          rows={[
            ['/gouw', 'Open the menu'],
            ['/gouw claim', 'Claim the chunk you stand in'],
            ['/gouw autoclaim <radius>', 'Claim a square of chunks around you'],
            ['/gouw unclaim [all]', 'Give back this chunk, or everything'],
            ['/gouw trust | untrust <name>', 'Add or remove a member'],
            ['/gouw members', 'List the members'],
            ['/gouw leave', 'Leave a gouw you were trusted in'],
            ['/gouw rename <name>', 'Give your gouw a name'],
            ['/gouw info | list | near', 'See the gouw here, all of yours, or those nearby'],
            ['/gouw border', 'Show the chunk edges with particles'],
          ]}
        />
      </Section>

      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">I cannot build in a place I did not claim.</b> Someone else probably owns it. <code>/gouw info</code> tells you whose it is, and <code>/gouw near</code> shows free land.</li>
          <li><b className="text-sp-gold">My friend cannot open my chests.</b> Trust them with <code>/gouw trust</code>, and check the <b>Open</b> button in the menu.</li>
          <li><b className="text-sp-gold">Can I have several gouwen?</b> No: one gouw per player, made of as many chunks as your limit allows.</li>
          <li><b className="text-sp-gold">An old Lands claim looks wrong.</b> Tell staff, who can inspect and fix it.</li>
        </ul>
        <div className="flex flex-wrap justify-center gap-3 pt-2 text-sm">
          <Link href="/wiki?page=gouw" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><ListOrdered className="w-4 h-4" /> Gouw Wiki</Link>
          <Link href="/wiki?page=gouw-commands" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><BookOpen className="w-4 h-4" /> Commands</Link>
        </div>
      </Section>
      <BuiltBy what="The Gouw guide" />
    </div>
  );
}
