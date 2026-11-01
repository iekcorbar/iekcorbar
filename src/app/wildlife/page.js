import React from 'react';
import Link from 'next/link';
import { CircleHelp, Binoculars, PawPrint, Egg, Flame, Landmark, Ghost, Trophy, Lightbulb, ListOrdered } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';

export const metadata = {
  title: 'Wildlife, explained — Iekcorbar',
  description: 'Thirty-two new creatures live on Iekcorbar: find them, watch them through a spyglass, fill your Field Journal, raise their young, build a zoo and tame a steam dragon. No mods needed.',
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

function Card({ title, children }) {
  return (
    <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4 sm:p-5 space-y-2">
      <p className="font-serif text-lg text-sp-gold leading-tight">{title}</p>
      <div className="text-sm text-text-warm/80 space-y-1.5">{children}</div>
    </div>
  );
}

const NAV = [
  ['#idea', 'The idea'],
  ['#journal', 'Field Journal'],
  ['#animals', 'The animals'],
  ['#behaviour', 'Behaviour'],
  ['#young', 'Young & breeding'],
  ['#zoo', 'Zoo'],
  ['#dragon', 'Steam Dragon'],
  ['#wraith', 'Wraith'],
  ['#faq', 'FAQ'],
];

const GROUPS = [
  ['Forest, plains & countryside', 'Red deer, brown bear, wild boar, hedgehog, wall lizard, tortoise, garden snail, European mole (look for its molehills), mallard, songbirds, monarch butterfly, emperor dragonfly and the fireflies that only come out at night.'],
  ['Water & shore', 'Capybara, shore crab, crocodile, red-crowned crane, koi in ponds, orca in the deep sea, red-bellied piranha shoals in warm shallows and emperor penguins on the ice.'],
  ['Savanna, jungle & the far east', 'African elephant, plains zebra, white rhinoceros, red kangaroo, common ostrich, koala, scarlet macaw, tanuki at dusk and Japanese macaque troops.'],
  ['Deep caves & the night', 'The Steam Dragon, heard rather than found, and the Wraith that drifts through graveyards after dark.'],
];

export default function WildlifeExplainedPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-emerald-950/20 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <PawPrint className="w-3.5 h-3.5" /> Wildlife · The full explanation
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">
            Wildlife, Explained
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">
            Thirty-two new creatures now share Iekcorbar with the cows and sheep you know. They are
            shy and rare, and each has a home of its own. This is about <b>finding, watching and
            recording</b> - not catching or fighting. No mods, no downloads. The{' '}
            <Link href="/wiki?page=wildlife" className="text-sp-gold underline">wiki</Link> stays your quick reference.
          </p>
          <nav className="flex flex-wrap justify-center gap-2 pt-2 text-xs">
            {NAV.map(([h, l]) => (
              <a key={h} href={h} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold hover:border-sp-gold">{l}</a>
            ))}
          </nav>
        </div>
      </section>

      <Section id="idea" icon={CircleHelp} kicker="Start here" title="The big idea, in one paragraph">
        <p className="text-sm text-text-warm/80">
          Wildlife is only ever <b>added</b>: nothing from the normal game was removed or replaced.
          Animals are scarce on purpose, so if you see nothing, keep exploring or try another time of
          day. Walk slowly, look around, and write down what you find. Every species is an advancement in
          its own <b>Wildlife</b> tab, and most come with a rare colouring that counts as a special find.
        </p>
      </Section>

      <Section id="journal" icon={Binoculars} kicker="How you record an animal" title="Spyglass and Field Journal">
        <ol className="list-decimal pl-5 space-y-2 text-sm text-text-warm/80">
          <li><b>Craft a spyglass</b> (copper ingot and amethyst shard, as in vanilla).</li>
          <li><b>Find an animal</b> in its biome. Some only appear by day, fireflies only at night, snails like rain.</li>
          <li><b>Approach like a naturalist.</b> Deer, zebras, cranes and songbirds bolt from an upright or sprinting player; a sneaking, still one may get close. Tortoises hide and hedgehogs curl up.</li>
          <li><b>Watch it through the spyglass</b> until the dots fill: about a second for a calm species, up to four for a shy one. Far away goes twice as fast, right in front of it takes twice as long, and a bolted animal resets the meter.</li>
          <li><b>Read your journal</b> with <code>/wildlife journal</code>, or craft a <b>Field Journal</b> (book, feather, wheat seeds). Type <code>/wildlife</code> for the Wildlife menu: every species at a glance, with a click to find the nearest one. Unfound species show a hint; recorded ones show a true fact and a myth.</li>
        </ol>
        <p className="text-sm text-text-warm/80">
          Milestone advancements wait at 5 species, 10 species, every species, your first rare find and
          five rare finds. A separate <b>Zoo</b> tab rewards building and feeding your own.
        </p>
      </Section>

      <Section id="animals" icon={ListOrdered} kicker="Where to look" title="Thirty animals and two specials">
        <div className="grid sm:grid-cols-2 gap-4">
          {GROUPS.map(([t, d]) => (
            <Card key={t} title={t}><p>{d}</p></Card>
          ))}
        </div>
        <p className="text-sm text-text-warm/80">
          Every species has a rare colouring: an albino deer, a blue morpho butterfly, a capybara wearing
          a yuzu. The exact homes and times of day are in the{' '}
          <Link href="/wiki?page=wildlife-species" className="text-sp-gold underline">species list on the wiki</Link>,
          and <code>/wildlife find &lt;species&gt;</code> points you to the nearest one.
        </p>
      </Section>

      <Section id="behaviour" icon={PawPrint} kicker="Good to know" title="How they behave">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Shy and calm"><p>Deer and lizards run when you come close; hedgehogs and snails curl up; tortoises pull in their head and legs. Sneak to get near.</p></Card>
          <Card title="The only fighters"><p>Only adult <b>bears</b> and <b>boars</b> ever fight back. A bear is calm until hurt, sleeps at night, eats sweet berries and may fish a salmon from the river by day. Everything else is harmless, and so are the young.</p></Card>
          <Card title="Little jobs"><p>Butterflies ripen your wheat, carrots, potatoes and beetroots a step faster. Capybaras give ducks and songbirds a ride. Lizards can drop their tail; crabs sometimes drop a claw that gives you one block more reach in your off hand.</p></Card>
          <Card title="Keeping them"><p>A <b>name tag</b> or a <b>lead</b> keeps an animal where it is; unnamed wildlife wanders off when nobody is near.</p></Card>
          <Card title="Hunter's trophy"><p>An animal you have recorded first sometimes leaves a stone statue of itself when taken down, and rarer animals leave one more often. Observe first, then decide.</p></Card>
        </div>
      </Section>

      <Section id="young" icon={Egg} kicker="The next generation" title="Babies, breeding and growing up">
        <p className="text-sm text-text-warm/80">
          Right-click an adult with its favourite food and it enters love mode; two of the same kind
          close together raise a baby. Babies are half size, harmless and grow up after a while. A
          <b> golden dandelion</b> keeps a baby small for good, and a baby you have kept stays when the
          chunk unloads. Spawn eggs exist for every animal, and every species can be set in stone as a
          statue. The food per species is on the{' '}
          <Link href="/wiki?page=wildlife-breeding" className="text-sp-gold underline">breeding page</Link>.
        </p>
      </Section>

      <Section id="zoo" icon={Landmark} kicker="Build your own" title="The zoo">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Props & tickets"><p>Craftable decor for paths, signs, picnic tables, parasols, first-aid posts and enclosures, plus <b>ticket kiosks</b>, <b>turnstiles</b> and <b>gates</b> that turn a fenced field into a working zoo.</p></Card>
          <Card title="Feeding & ageing"><p>Animals you keep (name tag or lead) get hungry; wild ones feed themselves. A trough holds one kind of food and feeds the hungriest animal nearby that eats it. A starving animal slows down, smokes and drops nothing. They also age from baby to adult to senior, and old animals leave a little extra behind.</p></Card>
        </div>
        <p className="text-sm text-text-warm/80">The Zoo advancement tab tracks keeper, feeder, breeder and veterinarian milestones.</p>
      </Section>

      <Section id="dragon" icon={Flame} kicker="Deep underground" title="The Steam Dragon">
        <p className="text-sm text-text-warm/80">
          Brass over an iron frame, a furnace ticking behind its ribs. It is not found, it is heard:
          follow the hiss through the big caverns far down. Right-click it with <b>coal or charcoal</b>
          {' '}(it is a furnace) and each lump has a good chance to win it over. A tamed dragon follows you,
          sits when you sneak and right-click it with an empty hand, and lets you <b>ride</b> it through
          caves and sky, with no fall damage for either of you. <code>/wildlife tamed</code> lists yours.
        </p>
        <p className="text-sm text-text-warm/80">
          Dragons come in five metal looks to match the server&apos;s crafts, a rare gilded one - and a
          legend: roughly one in three hundred is something else entirely.
        </p>
        <p className="text-sm text-text-warm/80">
          Taming, riding, the whistle, saddlebags, dyes, eggs and nests all have their own page: the{' '}
          <Link href="/dragons" className="text-sp-gold underline">Dragon Guide</Link>.
        </p>
      </Section>

      <Section id="wraith" icon={Ghost} kicker="After dark" title="The Wraith">
        <p className="text-sm text-text-warm/80">
          Near graveyards (soul sand and soul soil) a wraith may drift past at night, shedding mist and
          moaning softly. It is atmosphere, not an animal: it does not fight, is not in the journal,
          cannot be bred and fades away at dawn.
        </p>
      </Section>

      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">I cannot find anything.</b> Animals are scarce by design. Try another biome or time of day, and use <code>/wildlife find</code>.</li>
          <li><b className="text-sp-gold">The meter keeps resetting.</b> The animal bolted. Sneak, stay still and try again.</li>
          <li><b className="text-sp-gold">Do I need mods?</b> No. The server sends the models and sounds itself.</li>
          <li><b className="text-sp-gold">Is it safe?</b> Yes, apart from a cornered boar and an angry bear.</li>
        </ul>
        <div className="flex flex-wrap justify-center gap-3 pt-2 text-sm">
          <Link href="/wiki?page=wildlife" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><ListOrdered className="w-4 h-4" /> Wildlife Wiki</Link>
          <Link href="/achievements" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><Trophy className="w-4 h-4" /> Achievements</Link>
        </div>
        <p className="text-xs text-text-warm/50 text-center">Numbers on this page match the live plugin (Wildlife {`0.9.0`}).</p>
      </Section>
      <BuiltBy what="Wildlife" />
    </div>
  );
}
