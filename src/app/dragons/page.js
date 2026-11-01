import React from 'react';
import Link from 'next/link';
import { Flame, Heart, Wind, Backpack, Palette, Home, Terminal, Lightbulb, BookOpen, ListOrdered } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';

export const metadata = {
  title: 'Steam Dragons, explained — Iekcorbar',
  description: 'Everything about the Steam Dragon on Iekcorbar: finding and taming one, riding, the Dragon Whistle, saddlebags, hunger, dyes, eggs, nests and every command. No mods needed.',
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
  ['#find', 'Find & tame'],
  ['#ride', 'Riding'],
  ['#whistle', 'Whistle'],
  ['#care', 'Care'],
  ['#bags', 'Saddlebags'],
  ['#dyes', 'Dyes'],
  ['#nests', 'Eggs & nests'],
  ['#commands', 'Commands'],
  ['#faq', 'FAQ'],
];

export default function DragonsPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      {/* ---------- hero ---------- */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-950/30 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <Flame className="w-3.5 h-3.5" /> Wildlife · The dragon guide
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">
            Steam Dragons
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">
            Brass over an iron frame, with a furnace ticking behind its ribs. A Steam Dragon lives deep in the
            big caverns, and once it trusts you it becomes your <b>companion</b>, your <b>mount</b> and your
            <b> pack animal</b>. This page covers everything, from the first lump of coal to a nest of your own.
            It is part of the <Link href="/wildlife" className="text-sp-gold underline">Wildlife</Link> plugin,
            and the <Link href="/wiki?page=wildlife-dragons" className="text-sp-gold underline">wiki</Link> keeps
            the short reference. No mods needed.
          </p>
          <nav className="flex flex-wrap justify-center gap-2 pt-2 text-xs">
            {NAV.map(([h, l]) => (
              <a key={h} href={h} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold hover:border-sp-gold">{l}</a>
            ))}
          </nav>
        </div>
      </section>

      {/* ---------- find & tame ---------- */}
      <Section id="find" icon={Flame} kicker="Deep underground" title="Finding & taming">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Finding one" stat="Heard, not seen">
            <p>You do not stumble over a dragon, you hear it: follow the hiss through the <b>large caves, far underground</b>. They are rare on purpose.</p>
            <p>Dragons come in five metal looks (brass, Sakagura, Kitchen, Kura and verdigris) and a rare <b>gilded</b> one.</p>
          </Card>
          <Card title="Taming" stat="Coal or charcoal">
            <p>Right-click it with <b>coal or charcoal</b>. Each lump has a <b>35%</b> chance; the furnace decides when it trusts you.</p>
            <p>A wild dragon that is not yours roars if you come too close. Sneaking helps.</p>
          </Card>
        </div>
        <p className="text-sm text-text-warm/80">
          A tamed dragon follows you, circles at a distance and lands a few blocks away when you stand still.
          It also flies around obstacles instead of into them, so a cave is no problem.
        </p>
      </Section>

      {/* ---------- riding ---------- */}
      <Section id="ride" icon={Wind} kicker="Take to the sky" title="Riding">
        <p className="text-sm text-text-warm/80">
          Right-click your dragon with an <b>empty hand</b> to climb on. You sit on a saddle above its back.
        </p>
        <Table
          head={['Key', 'What it does']}
          rows={[
            ['W / A / S / D', 'Steer where you look'],
            ['Space', 'Climb'],
            ['Back / down', 'Descend'],
            ['Sprint', 'Fly faster'],
            ['No input', 'Hover in place'],
          ]}
        />
        <p className="text-sm text-text-warm/80">
          You take no fall or wall damage on the saddle, and stepping off in mid-air gives you a soft landing
          (Slow Falling for a few seconds). A well fed dragon (hunger 90 and up) flies a little faster; a hungry one is slow.
        </p>
      </Section>

      {/* ---------- whistle ---------- */}
      <Section id="whistle" icon={Wind} kicker="Never lose it again" title="The Dragon Whistle">
        <p className="text-sm text-text-warm/80">
          Craft it from a <b>copper ingot, an iron nugget and a coal</b>. Right-click it and every tamed dragon of
          yours in the same world flies to you, even one far away in an unloaded part of the world. There are four
          seconds between whistles. A dragon in another world (such as the Nether) cannot come.
        </p>
        <p className="text-sm text-text-warm/80">
          If you teleport far away (home, warp, tpa), your dragon follows by itself if it was within about 40 blocks
          and not sitting.
        </p>
      </Section>

      {/* ---------- care ---------- */}
      <Section id="care" icon={Heart} kicker="Keep it happy" title="Hunger, healing & name tags">
        <div className="grid sm:grid-cols-3 gap-4">
          <Card title="Hunger" stat="Feed coal">
            <p>A dragon you keep gets hungry over time. Below half it flies slower, and a starving one is very slow. Right-click with <b>coal</b> to feed it.</p>
          </Card>
          <Card title="Healing" stat="Also coal">
            <p>Coal also heals a hurt dragon. It cannot suffocate, drown or take fall damage, but fire and lava still hurt.</p>
          </Card>
          <Card title="Name tags" stat="Look at it">
            <p>A tame dragon shows its name (or &quot;Owner&apos;s Dragon&quot;) above the model. Look straight at it and the action bar shows its hunger and whether it has a nest.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- saddlebags ---------- */}
      <Section id="bags" icon={Backpack} kicker="Pack animal" title="Saddlebags">
        <p className="text-sm text-text-warm/80">
          Right-click your dragon with a <b>chest</b> to fit saddlebags: <b>nine slots</b> that stay with the dragon
          through restarts. Open them with <code>/wildlife pet bag</code> or from the <code>/wildlife pet</code> menu while
          the dragon is within eight blocks. If the dragon dies, everything drops where it fell, so do not fly into lava
          with your whole stash.
        </p>
      </Section>

      {/* ---------- dyes ---------- */}
      <Section id="dyes" icon={Palette} kicker="A new coat of paint" title="Dyes & looks">
        <p className="text-sm text-text-warm/80">Right-click your dragon with a dye to change its look:</p>
        <Table
          head={['Dye', 'Look']}
          rows={[
            ['Orange', 'Brass (the original)'],
            ['Red', 'Sakagura'],
            ['Yellow', 'Kitchen'],
            ['Blue', 'Kura'],
            ['Green', 'Verdigris'],
          ]}
        />
        <p className="text-sm text-text-warm/80">
          Its name, saddlebags, hunger and owner stay. Rare looks (the gilded dragon) are not affected by dyes,
          and a dyed dragon loses its rare look.
        </p>
      </Section>

      {/* ---------- eggs & nests ---------- */}
      <Section id="nests" icon={Home} kicker="A family of your own" title="Eggs & nests">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Eggs" stat="Two tame dragons + coal">
            <p>Two tamed dragons fed coal at the same time lay a <b>Dragon Egg</b>. Carry it in your inventory: after about eight minutes it hatches into a baby dragon that belongs to you. Only the owner of the parents can hatch it.</p>
            <p>Or keep it warm: <b>sneak + right-click a hay block</b> with the egg. It rests on the straw and hatches while its keeper is around. Sneak + right-click the straw with an empty hand takes it back.</p>
          </Card>
          <Card title="Dragon Nests" stat="Relics prop">
            <p>Craft a <b>Dragon Nest</b>: two hay blocks, two sticks, a copper ingot and a coal. It is a warm straw nest with a brass egg in it.</p>
            <p>A <b>tame dragon claims a nest</b> by landing on it while you rest nearby. One nest per dragon and one dragon per nest, so they never fight over one.</p>
          </Card>
        </div>
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li><b>Wild dragons</b> that find a free nest within 64 blocks settle down near it.</li>
          <li>Your dragon rests on its nest when you stand still close by, and <b>eggs are laid in your own nest</b> when two of your dragons breed near it.</li>
          <li>Right-click a nest to see who lives there. <b>Sneak + right-click with an empty hand</b> lets it go (owner or staff).</li>
          <li>A claim ends when the dragon dies or is set free, when the nest is broken, or after 14 days without a visit.</li>
          <li>A grown-up baby keeps its owner and saddlebags.</li>
        </ul>
      </Section>

      {/* ---------- commands ---------- */}
      <Section id="commands" icon={Terminal} kicker="Quick reference" title="Commands">
        <p className="text-sm text-text-warm/80">Look at your dragon, or stand close, and use:</p>
        <Table
          head={['Command', 'What it does']}
          rows={[
            ['/wildlife pet', 'A small menu: sit or stand, come here, rename, saddlebags and set free'],
            ['/wildlife pet list', 'Your dragons and where they are'],
            ['/wildlife pet sit | stand | come', 'The same as the menu buttons'],
            ['/wildlife pet name <name>', 'Give it a name (24 characters)'],
            ['/wildlife pet bag', 'Open the saddlebags'],
            ['/wildlife pet release confirm', 'Set it free for good'],
            ['/wildlife tamed', 'List all animals you keep'],
          ]}
        />
        <p className="text-sm text-text-warm/80">
          Sneak + right-click with an empty hand also makes your dragon sit or stand.
        </p>
      </Section>

      {/* ---------- faq ---------- */}
      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">I cannot find a dragon.</b> They are rare and live far underground in big caverns. Listen for the hiss, and bring torches and patience.</li>
          <li><b className="text-sp-gold">It does not follow me.</b> It may be sitting (sneak + right-click to stand it up), or in another world. Use the whistle or <code>/wildlife pet come</code>.</li>
          <li><b className="text-sp-gold">Can everyone do all of this?</b> Yes, taming, riding, saddlebags, dyes and nests are open to all players. Staff can switch single actions off per player.</li>
          <li><b className="text-sp-gold">Do I need mods?</b> No. The server sends the models and sounds itself.</li>
          <li><b className="text-sp-gold">Is there more to find?</b> The advancement screen has a Wildlife tab with a few hidden dragon feats. Whistle, build a nest, repaint, fill the saddlebags.</li>
        </ul>
        <div className="flex flex-wrap justify-center gap-3 pt-2 text-sm">
          <Link href="/wildlife" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><BookOpen className="w-4 h-4" /> Wildlife Guide</Link>
          <Link href="/wiki?page=wildlife-dragons" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><ListOrdered className="w-4 h-4" /> Dragon Wiki</Link>
        </div>
        <p className="text-xs text-text-warm/50 text-center">Numbers on this page match the live plugin (Wildlife 0.9.8). Exact details are always shown in-game.</p>
      </Section>
      <BuiltBy what="The dragon guide" />
    </div>
  );
}
