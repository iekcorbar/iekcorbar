import React from 'react';
import Link from 'next/link';
import { Gift, Sparkles, Music, Clock, Hammer, Lightbulb, BookOpen, Calendar } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';
import { Hero, Section, Card, WikiButtons } from '@/components/GuideKit';

export const metadata = {
  title: 'Relics & holiday props, explained — Iekcorbar',
  description: 'Placeable props for your builds: seasonal collections for Halloween, Christmas, New Year, Valentine, Easter and St. Patrick, plus all-year steampunk and Japanese curiosities.',
};

const NAV = [
  ['#idea', 'The idea'],
  ['#place', 'Placing'],
  ['#collections', 'Collections'],
  ['#alive', 'Props that do things'],
  ['#gifts', 'Gift boxes'],
  ['#faq', 'FAQ'],
];

const COLLECTIONS = [
  ['🎃', "Shoutlander's Shadow", 'Halloween', 'Skull lanterns, graves, monoliths and warding totems from the server lore. Some of them whisper at night or light up when monsters come near.'],
  ['🎄', 'Xmas Town', 'December', 'A cosy village with a steampunk and Japanese twist: a sleigh to sit in, an advent calendar, a music box with carols, a snow globe, a tree and wrapped gift boxes. Plus hot chocolate, mulled cider and gingerbread.'],
  ['🎆', 'New Year', 'Midnight', 'Countdown clocks and boards, a temple bell that strikes 108 times, fireworks launchers, confetti cannons, kagami mochi and a sparkling cider tower.'],
  ['💘', "Valentine's", '14 February', 'Heart balloons, a rose under glass, a love-lock rail, a wishing well, a teddy bear and chocolate hearts.'],
  ['🐰', 'Easter', 'Spring', 'Hidden eggs for the egg hunt, a basket, a hopping clockwork bunny, a blossom vase and chocolate bunnies.'],
  ['🍀', "St. Patrick's Day", '17 March', 'A pot of gold you can open once a day, a rainbow arch, a shamrock planter, a celtic harp and green ale.'],
  ['⚙️', 'Curiosities', 'All year', 'A brass telescope, a tesla coil, a gramophone, a torii gate, a shishi-odoshi, bonsai, folding screens, a pillar box and more steampunk and Japanese pieces.'],
];

export default function RelicsPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      <Hero icon={Gift} kicker="Relics · Props & holidays" title="Relics, Explained" nav={NAV} tint="from-rose-950/20">
        Relics are placeable props: lanterns, gramophones, gift boxes, whole holiday sets. They are not blocks, so they
        can be a pillar box, a tree or a tesla coil, and they look right from every side. Some do small things when you
        right-click them. Use them to make your builds feel alive. The{' '}
        <Link href="/calendar" className="text-sp-gold underline">calendar</Link> shows which holiday is next.
      </Hero>

      <Section id="idea" icon={Sparkles} kicker="Start here" title="The big idea">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          A relic is an item you place like a block. Each one belongs to a <b>collection</b>. The seasonal collections come with
          treats you can eat and small surprises that only work around their holiday. Some relics are craftable from normal
          materials; others are rewards, prizes or shop items. <code>/zukan</code> shows the recipe when one exists.
        </p>
      </Section>

      <Section id="place" icon={Hammer} kicker="How it works" title="Placing & removing">
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>Place a relic on <b>any face</b>: floor, wall or ceiling. Many have their own wall or ceiling version; others simply tilt.</li>
          <li>Right-click a relic to use it, if it does something. Sneak + right-click often opens a small menu (music props, countdown boards).</li>
          <li>To remove one, <b>hit it</b> a few times. It drops as an item again.</li>
          <li>Relics placed by staff, such as those in town, cannot be broken by players.</li>
          <li>Land protection applies: you can only place relics where you may build (see the <Link href="/gouw" className="text-sp-gold underline">Gouw guide</Link>).</li>
        </ul>
      </Section>

      <Section id="collections" icon={Gift} kicker="Seven sets" title="The collections">
        <div className="grid sm:grid-cols-2 gap-4">
          {COLLECTIONS.map(([emoji, name, when, text]) => (
            <Card key={name} title={`${emoji} ${name}`} stat={when}>
              <p>{text}</p>
            </Card>
          ))}
        </div>
        <p className="text-sm text-text-warm/80">
          Each holiday set also has its own food and drink. Hold a treat to see what it does. The Sakagura bar even uses the
          Valentine&apos;s chocolate heart in a drink.
        </p>
      </Section>

      <Section id="alive" icon={Music} kicker="More than decoration" title="Props that do things">
        <div className="grid sm:grid-cols-3 gap-4">
          <Card title="Music" stat="Gramophone, music box, harp"><p>Right-click to play a playlist on loop. Sneak + right-click for play/pause, shuffle, skip and off. The music carries across a whole town square.</p></Card>
          <Card title="Countdowns" stat="Boards & clocks"><p>The countdown board follows real time zones around the world and rings in each New Year, with a title and fireworks at midnight.</p></Card>
          <Card title="Little rewards" stat="Egg hunt, pot of gold, wishing well"><p>Find each hidden egg once, open the pot of gold once a day, or toss a gold nugget in the well and make a wish.</p></Card>
        </div>
      </Section>

      <Section id="gifts" icon={Clock} kicker="December" title="Gift boxes & the advent calendar">
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>A <b>gift box</b> holds nine presents. Fill it, pick a wrapping colour and <b>lock</b> it for one player by typing their name in chat.</li>
          <li>Only that player can open it, and only between <b>24 and 26 December</b>. You cannot open your own box, and a locked box cannot be broken by anyone but staff.</li>
          <li>The <b>advent calendar</b> has a door for each day from 1 to 25 December; each player can open each door once.</li>
          <li>Gift boxes are not craftable: they are handed out for events such as the Secret Santa.</li>
        </ul>
      </Section>

      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">I cannot find a recipe.</b> It may be a reward, shop or event item. Check <code>/zukan</code>.</li>
          <li><b className="text-sp-gold">The relic does not do anything.</b> Many are purely decoration. Try right-click, then sneak + right-click.</li>
          <li><b className="text-sp-gold">It will not break.</b> Staff-placed relics are protected. Your own break after a few hits.</li>
        </ul>
        <WikiButtons links={[['/calendar', 'Holiday Calendar', Calendar], ['/recipes', 'All Recipes', BookOpen]]} />
      </Section>
      <BuiltBy what="The Relics guide" />
    </div>
  );
}
