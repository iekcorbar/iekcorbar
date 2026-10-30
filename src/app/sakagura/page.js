import React from 'react';
import Link from 'next/link';
import { CircleHelp, Flame, Wine, Timer, Cog, Sprout, Trees, Droplets, Lightbulb, BookOpen, ListOrdered, Sparkles, AlertTriangle } from 'lucide-react';
import SakaguraPlayground from '@/components/SakaguraPlayground';

export const metadata = {
  title: 'Sakagura, explained — Iekcorbar',
  description: 'The full, plain-language guide to brewing on Iekcorbar: cauldrons, the Steam Still, sake casks, quality stars, the fruit garden and careful drinking. No mods, no prior knowledge needed.',
};

const IMG = (id) => `/recipes/sakagura/${id}.png`;

function Img({ id, size = 'h-16 w-16', alt }) {
  return <img src={IMG(id)} alt={alt ?? ''} loading="lazy" className={`${size} object-contain drop-shadow-[0_2px_6px_rgba(0,0,0,0.6)]`} />;
}

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

function Card({ img, title, stat, children }) {
  return (
    <div className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4 sm:p-5 space-y-2">
      <div className="flex items-center gap-3">
        {img && <Img id={img} />}
        <div className="min-w-0">
          <p className="font-serif text-lg text-sp-gold leading-tight">{title}</p>
          {stat && <p className="text-[11px] text-sp-copper uppercase tracking-wider">{stat}</p>}
        </div>
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
  ['#cauldron', 'Cauldron'],
  ['#bottling', 'Bottling'],
  ['#still', 'Steam Still'],
  ['#casks', 'Casks'],
  ['#quality', 'Quality'],
  ['#garden', 'Garden'],
  ['#orchard', 'Orchard'],
  ['#drinking', 'Drinking'],
  ['#first', 'First brew'],
  ['#seasonal', 'Seasonal'],
  ['#faq', 'FAQ'],
];

const CROPS = [
  ['barley', 'Barley', 'shochu, ale and the famous Steam Whisky'],
  ['hops', 'Hops', 'bitter and fresh Hop Ale'],
  ['grape', 'Grapes', 'proper Red Wine straight from your vine'],
  ['ume', 'Ume Plums', 'the sweet-tart Umeshu plum wine'],
  ['yuzu', 'Yuzu', 'sparkling Chuhai and bright Ramune'],
  ['peach', 'Peaches', 'blushing Momo Sake'],
  ['cherry', 'Cherries', 'Sakura Liqueur that smells of spring'],
  ['strawberry', 'Strawberries', 'Berry Wine and fresh eating'],
  ['blueberry', 'Blueberries', 'forest-y Berry Wine'],
  ['juniper', 'Juniper Berries', 'botanical Juniper Gin'],
  ['ginger', 'Ginger', 'the kick in Amazake and gin'],
];

// The garden grew (Sakagura 0.4/0.5): thirty-seven more crops. Plant = what goes
// into the ground; wild home = where ripe patches already grow. Faithful to
// docs/wiki-sakagura/02-garden.md - keep the two in sync.
const GARDEN_LATER = [
  ['Apples', 'Apple Pip', 'plains, meadows, forests'],
  ['Pears', 'Pear Pip', 'forests, flower forests'],
  ['Apricots', 'Apricot Kernel', 'savannas, badlands, plains'],
  ['Mikan Oranges', 'Mikan Seeds', 'jungles, bamboo'],
  ['Kiwis', 'Kiwi Seeds', 'jungles, forests'],
  ['Rice', 'Rice', 'swamps, rivers, jungles'],
  ['Rye', 'Rye', 'plains, taiga'],
  ['Maize', 'Maize', 'plains, savannas'],
  ['Tomatoes', 'Tomato Seeds', 'plains, sunflower plains'],
  ['Raspberries', 'Raspberries', 'forests, taiga'],
  ['Blackcurrants', 'Blackcurrants', 'forests, groves'],
  ['Elderberries', 'Elderberries', 'forests, dark forests'],
  ['Cranberries', 'Cranberries', 'swamps, groves'],
  ['Gooseberries', 'Gooseberries', 'forests'],
  ['Mulberries', 'Mulberries', 'plains, savannas'],
  ['Persimmons', 'Persimmon Pip', 'forests'],
  ['Loquats', 'Loquat Stone', 'jungles'],
  ['Pomegranates', 'Pomegranate Seeds', 'savannas, badlands'],
  ['Figs', 'Fig Cuttings', 'savannas, plains'],
  ['Quinces', 'Quince Pip', 'forests, flower forests'],
  ['Olives', 'Olive Stone', 'savannas, badlands'],
  ['Dates', 'Date Pit', 'badlands, jungles'],
  ['Lychees', 'Lychee Seed', 'jungles, bamboo'],
  ['Passion Fruits', 'Passionfruit Seeds', 'jungles'],
  ['Vanilla Pods', 'Vanilla Cuttings', 'jungles, bamboo'],
  ['Sudachi', 'Sudachi Seeds', 'jungles, bamboo'],
  ['Shiso Leaves', 'Shiso Leaves', 'forests, jungles'],
  ['Wasabi Roots', 'Wasabi Roots', 'swamps, rivers'],
  ['Daikon', 'Daikon', 'plains, flower forests'],
  ['Chili Peppers', 'Chili Seeds', 'jungles, savannas'],
  ['Mangoes', 'Mango Pit', 'jungles'],
  ['Papayas', 'Papaya Seeds', 'jungles'],
  ['Guavas', 'Guava Seeds', 'savannas, jungles'],
  ['Rambutan', 'Rambutan Seed', 'jungles'],
  ['Longans', 'Longan Seed', 'jungles'],
  ['Mangosteens', 'Mangosteen Seeds', 'jungles'],
  ['Dragon Fruits', 'Dragonfruit Seeds', 'swamps, jungles'],
];

export default function SakaguraExplainedPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      {/* ---------- hero ---------- */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-rose-950/20 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <Wine className="w-3.5 h-3.5" /> Sakagura · The full explanation
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">
            Brewing, Explained
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">
            A <b>sakagura</b> is a Japanese sake brewery - and ours is open to everyone. Grow the barley,
            fruit and herbs, cook or mix them in a cauldron, bottle the result, distil it in a copper Steam
            Still and age it in a sake cask, and a drink with up to five stars comes out. This page explains
            <b> everything</b> from zero - no mods, no downloads, no brewing knowledge required. The
            {' '}<Link href="/wiki?page=sakagura" className="text-sp-gold underline">wiki</Link> stays your quick reference.
          </p>
          <nav className="flex flex-wrap justify-center gap-2 pt-2 text-xs">
            {NAV.map(([h, l]) => (
              <a key={h} href={h} className="px-3 py-1 rounded-full border border-sp-bronze/40 text-text-warm/70 hover:text-sp-gold hover:border-sp-gold">{l}</a>
            ))}
          </nav>
        </div>
      </section>

      {/* ---------- the idea ---------- */}
      <Section id="idea" icon={CircleHelp} kicker="Start here" title="The big idea, in one paragraph">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          A drink on Iekcorbar is a <b>recipe you followed</b>. Every bottle remembers what went into it and
          what you did to it: the ingredients, the minutes it spent in the cauldron, how often it was
          distilled, how many years it aged and in which wood. When it is done, the server compares that
          story to every recipe it knows and grades you - up to five gold stars for a perfect match, a
          cloudy &quot;Murky Brew&quot; if you went off on your own. There are no hidden stats and no menus of
          numbers: the bottle itself tells you what it is and what step comes next.
        </p>
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-4 space-y-2">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">The three rules</p>
          <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1">
            <li><b>Real minutes, real time.</b> Cooking, distilling and ageing run on the wall clock - a cask keeps working while you sleep.</li>
            <li><b>The recipe is the score.</b> Deviate in ingredients, times, runs or wood and quality drops - gradually, not suddenly.</li>
            <li><b>The bottle is the manual.</b> Right-click anything for its status; every unfinished bottle names its next step.</li>
          </ol>
        </div>
        <div className="rounded-xl border border-sp-copper/40 bg-amber-950/20 p-4">
          <p className="text-sm text-text-warm/80 leading-relaxed">
            <b className="text-sp-gold">Heard the word &quot;Brewery&quot;?</b> The old server ran a plugin called Brewery with
            barrels and a GUI distillery. The Sakagura replaces it: the same hobby, rebuilt for us from
            scratch on vanilla blocks, with our own recipes, our still and our casks - and it pairs with the
            Kitchen and Steamworks.
            Nobody ever needs to install anything.
          </p>
        </div>
      </Section>

      {/* ---------- cauldron ---------- */}
      <Section id="cauldron" icon={Flame} kicker="Step one" title="The cauldron: where every drink starts">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Put a cauldron down and right-click it with a water bucket. Now it is either an oven or a cold
          kettle - and that choice is the whole machine: <b>heat underneath cooks, no heat mixes</b>. Every
          recipe&apos;s first step says which one it wants.
        </p>
        <Table
          head={['Setup', 'Mode', 'Used by']}
          rows={[
            ['Fire, lava, magma, campfire or a lit furnace below', 'Cook', 'sake, shochu, whiskey, wine, absinthe...'],
            ['Nothing below (cold)', 'Mix', 'umeshu, chuhai, sangria, mugicha...'],
          ]}
        />
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Throwing things in" stat="one at a time">
            <p>Right-click the cauldron with each ingredient - one per click, up to 64 in the pot. Flowers, fruit, sugar, rice, even a milk bucket. The cauldron must be full of water before it accepts anything.</p>
          </Card>
          <Card img="barley" title="Watching it work" stat="empty hand = truth">
            <p>Right-click with an empty hand and the cauldron tells you what is inside and how long it has been cooking or mixing. Particles give it away too: bubbles and steam mean cooking, calm water means mixing.</p>
          </Card>
        </div>
        <p className="text-xs text-text-warm/50">
          Take the water out (bucket, or letting it run dry) and the brew is gone - plan a little before bottling.
        </p>
      </Section>

      {/* ---------- bottling ---------- */}
      <Section id="bottling" icon={Droplets} kicker="Step two" title="Bottling: the clock starts in the glass">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          When the time feels right, right-click with a <b>glass bottle</b> - one cauldron fills three
          bottles - and the brew&apos;s entire history travels with the glass. Every bottle now speaks:
        </p>
        <div className="grid sm:grid-cols-3 gap-4">
          <Card title="A work in progress" stat="&quot;Unfinished ...&quot;">
            <p>A grey name means the recipe is not done: the lore line tells you the exact next step - <i>&quot;Next: distill it (2x)&quot;</i> or <i>&quot;age it 3 years in cherry&quot;</i>. You never lose track.</p>
          </Card>
          <Card title="A finished drink" stat="with a star rating">
            <p>Once every step has happened, the bottle gets its name, colour, stars and effects right there in the tooltip - no guessing whether it turned out well.</p>
          </Card>
          <Card title="The murky one" stat="&quot;Murky Brew&quot;">
            <p>A drink that matches no recipe - wrong ingredients, wildly wrong times - turns murky. One sip makes you see things. It is how you learn.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- steam still ---------- */}
      <Section id="still" icon={Cog} kicker="Step three (some recipes)" title="The Steam Still: distilling made visible">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Spirits like Shochu, Whiskey and the copper-casked Steam Whisky get distilled before (or instead of)
          ageing. Distilling happens in a machine you can actually build: the <b>Steam Still</b> - a copper
          boiler on legs, built around an ordinary barrel.
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card img="still" title="Crafting the still" stat="barrel · 2 iron · blaze powder · glowstone">
            <p>One shapeless craft: a barrel, two iron ingots, blaze powder and a glowstone dust together in any order. Place it and heat it from below like a cauldron - fire, lava, a campfire or a lit furnace.</p>
          </Card>
          <Card title="Running a batch" stat="one glowstone per run · 20 s">
            <p>Put bottles inside, add <b>glowstone dust</b> and make sure there is heat below. Every 20 seconds the still spends one dust and gives the bottles inside (up to three stacks at a time) one distill run. Right-click the still to see what it is doing.</p>
          </Card>
        </div>
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-4">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70 mb-1">Line it up</p>
          <p className="text-sm text-text-warm/80">
            The still is a real container: <b>hoppers feed bottles in and take them out</b>. A conveyor from
            the cauldron into the heated still, and the next one in line, is a distillery in the old
            sense of the word - no redstone clocks, no explosions.
          </p>
        </div>
      </Section>

      {/* ---------- casks ---------- */}
      <Section id="casks" icon={Timer} kicker="Step four (some recipes)" title="Sake casks: patience in ten woods">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Many drinks round off in wood. A cask is a crafted barrel - ten kinds, from the honest oak to the
          lacquered <b>copper cask</b> - and it ages what is put inside in real time, <b>even while you are
          offline</b>. Twenty minutes is one year. Whiskey wants eighteen; umeshu is happy with three.
        </p>
        <div className="grid sm:grid-cols-3 gap-4">
          <Card img="cask_oak" title="The usual suspects" stat="oak · spruce · birch · jungle · acacia">
            <p>Six planks around two iron nuggets, one wood per cask. Recipes name the wood they want.</p>
          </Card>
          <Card img="cask_cherry" title="Fancy woods" stat="cherry · mangrove · bamboo · dark oak">
            <p>Same shape, different flavour. Cherry casks are famous for the Sakura Liqueur.</p>
          </Card>
          <Card img="cask_copper" title="The copper cask" stat="steampunk, and it matters" >
            <p>Copper ingots and iron. A few recipes - Steam Whisky among them - specifically ask for it.</p>
          </Card>
        </div>
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1">
          <li>A bottle in a cask keeps ageing while the cask sits unloaded - when it comes out, the years are simply <i>there</i>.</li>
          <li>Hoppers work with casks too (they even age the bottles passing through).</li>
          <li>Wrong-but-familiar woods (oak instead of dark oak) still count, just a little less.</li>
          <li>Opening a cask shows its bottles mid-ageing; the lore says &quot;Ageing in a cask...&quot;.</li>
        </ul>
      </Section>

      {/* ---------- quality ---------- */}
      <Section id="quality" icon={Sparkles} kicker="How you are graded" title="Quality: 40 / 25 / 15 / 20">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          When every step of a recipe has been done, the server grades how closely your brew followed it.
          Four things are scored, each with a tolerance that shrinks on harder recipes:
        </p>
        <Table
          head={['Axis', 'Weight', 'What it looks at']}
          rows={[
            ['Ingredients', '40 %', 'the right things, in the right amounts - extras cost you'],
            ['Time', '25 %', 'how many real minutes it cooked or mixed'],
            ['Distill runs', '15 %', 'exactly the number the recipe wants (if any)'],
            ['Age + wood', '20 %', 'years in a cask, and the wood it was made of (if any)'],
          ]}
        />
        <p className="text-sm text-text-warm/80">
          The total becomes one of ten quality points - shown as up to five stars - and picks the
          drink&apos;s name (poor / normal / good band), its effects, and even how harsh the hangover is.
          <b> Harder recipes demand a steadier hand</b>: the same sloppiness costs more on an 8 than on a 2.
        </p>
        <SakaguraPlayground />
        <p className="text-xs text-text-warm/50">
          Tip: you can check any finished drink&apos;s exact recipe and steps again later with <b>/zukan</b> (or the
          recipe book), and the calculator above plays by exactly the numbers the plugin uses.
        </p>
      </Section>

      {/* ---------- garden ---------- */}
      <Section id="garden" icon={Sprout} kicker="Where drinks are grown" title="The garden: forty-eight crops worth drinking">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Eleven crops founded this garden; every one of them still earns its place because a recipe asks
          for it. They grow on ordinary farmland in three stages; berry bushes and dwarf fruit trees
          <b>regrow</b> after picking, barley and ginger are dug up and replanted. And when you explore, keep
          your eyes open: wild patches grow ripened bushes from the first day, in the biomes each plant likes.
        </p>
        <div className="grid sm:grid-cols-3 gap-4">
          {CROPS.map(([id, name, use]) => (
            <Card key={id} img={id} title={name} stat={use} />
          ))}
        </div>
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Since then the garden grew to <b className="text-sp-gold">forty-eight</b> - and the bar kept up:
          apricots, kiwis, mikan, pears, tomatoes and chili moved behind the counter first, and with the
          grove set even mango, rambutan, dragon fruit, sudachi, lychee, passion fruit, pomegranate, date,
          mulberry, rye and vanilla pour their own drinks - four of them with effects the bar had never
          served: a chosen blindness, a queen&apos;s tired hands, a dolphin&apos;s grace and one rye joke that
          lifts you off the ground. What still asks for nothing is grown for the joy of the orchard - or
          for eating, warm, on the step.
        </p>
        <Table
          head={['Crop', 'Plant', 'Found wild in']}
          rows={GARDEN_LATER}
        />
        <p className="text-xs text-text-warm/50">
          Seeds come from the fruit itself - one fruit in a crafting grid makes a couple of seeds (barley,
          strawberries, blueberries, juniper and ginger plant straight from the harvest).
        </p>
      </Section>

      {/* ---------- orchard ---------- */}
      <Section id="orchard" icon={Trees} kicker="The 0.5.0 grove" title="Orchard trees: a spot of their own">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          The garden finally stood up straight. A sapling goes in with a right-click on grass or dirt -
          not farmland, trees take a spot of their own - and grows in three stages: a sprout, a young tree,
          a full tree. Each step takes about twenty minutes of real time; bone meal on the trunk skips one.
        </p>
        <Table
          head={['Tree', 'Fruit', 'Grown from', 'Good to know']}
          rows={[
            ['Sakura', '2-4 Cherries', 'Cherry + 2 Sticks', 'pink blossom; petals drift off the canopy'],
            ['Ume Plum', '2-3 Ume Plums', 'Ume Plums + 2 Sticks', 'the sake fruit, off a tree instead of a bush'],
            ['Apple', '2-4 Apples', 'Apples + 2 Sticks', 'a classic, with bigger yields than the bush'],
            ['Orange', '2-4 Mikan Oranges', 'Mikan Oranges + 2 Sticks', 'glossy green canopy'],
            ['Red Maple', 'none', '2 Red Mushrooms + 2 Sticks', 'pure decoration - golden sparkles in autumn colours'],
          ]}
        />
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Picking and felling" stat="the canopy regrows">
            <p>A ripe tree shows fruit dots in its top canopy. Right-click the trunk and 2-4 pieces drop -
            the canopy ripens again in about twenty minutes. Punch the trunk three times and the tree comes
            down; you get the sapling back, plus whatever hung in it.</p>
          </Card>
          <Card title="Same fruit, same bar" stat="bush or tree, it counts">
            <p>Tree fruit is the very same fruit the bush version gives, so every drink recipe works with
            either - and vice versa. Trees want four blocks of air above them and a little space from their
            neighbours. Wild trees do not spawn: an orchard is something you plant.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- drinking ---------- */}
      <Section id="drinking" icon={Wine} kicker="With a little respect" title="Effects, and what alcohol does">
        <p className="text-sm text-text-warm/80">
          A good drink gives real effects - speed, strength, regeneration, night vision, luck - and better
          quality makes the good effects stronger and the rough ones milder. But alcohol is a debt:
        </p>
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1">
          <li>Drunkenness piles up with every glass (check yourself with <code className="text-emerald-300">/brew drunk</code>) and fades slowly.</li>
          <li>Tipsy: the ground sways and you stumble. Drunker: your chat slurs. Worse: you lose your lunch.</li>
          <li>At the limit you pass out and wake up at your spawn point with a hangover - slowness, weakness and a splitting head.</li>
          <li>Weak drinks barely register; a five-star Advocaat hits differently than a murky one. Quality is also self-care.</li>
        </ul>
        <div className="rounded-xl border border-red-900/50 bg-red-950/20 p-4">
          <p className="text-sm text-text-warm/80 flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
            <span>Two friends sneaking next to each other with drinks in hand will cheers - <i className="text-sp-gold">&quot;Kanpai!&quot;</i> is worth a toast on its own. Pace yourself.</span>
          </p>
        </div>
      </Section>

      {/* ---------- first brew ---------- */}
      <Section id="first" icon={ListOrdered} kicker="Put it together" title="Your first brew & the road onward">
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-5 space-y-3">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">Nigori Sake in ten minutes</p>
          <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1.5">
            <li>Find <b>barley</b> (wild patches in plains and meadows) or steamed rice from the Kitchen.</li>
            <li>Place a cauldron, right-click it full of water, throw in <b>5 rice + 2 sugar</b> - one click each.</li>
            <li>Put a block with <b>fire or lava underneath</b> and let it cook <b>6 minutes</b> (empty-hand right-click to check).</li>
            <li>Right-click with a <b>glass bottle</b>: out comes Nigori Sake - three stars and up if your time was honest.</li>
            <li>Taste it somewhere safe. Cloudy sake is meant to be drunk with friends, not discovered alone at dawn.</li>
          </ol>
          <div className="flex justify-center gap-4 pt-1">
            <Img id="barley" size="h-10 w-10" alt="" />
            <Img id="ume" size="h-10 w-10" alt="" />
            <Img id="still" size="h-10 w-10" alt="" />
            <Img id="cask_oak" size="h-10 w-10" alt="" />
          </div>
        </div>
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-5 space-y-2">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">The road onward, in order of stars</p>
          <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1.5">
            <li><b>Umeshu</b> - your first mix, your first cherry-cask age, and a beautiful drink.</li>
            <li><b>Shochu</b> - meet the Steam Still: two honest distillations.</li>
            <li><b>Red Wine & Hop Ale</b> - a garden vineyard and a hop field, four years in oak.</li>
            <li><b>Whiskey</b> - ten minutes of cooking, two runs, eighteen years in spruce. Patience pays.</li>
            <li><b>Steam Whisky</b> - the copper cask; the showpiece every brewer builds toward.</li>
            <li>Then run a still line for the whole town, or become the best Absinthe brewer on the server. (You have been warned about the Absinthe.)</li>
          </ol>
        </div>
      </Section>

      {/* ---------- seasonal shelf ---------- */}
      <Section id="seasonal" icon={Sparkles} kicker="Twelve times a year" title="The seasonal shelf">
        <p className="text-sm text-text-warm/80">
          Since late October the bar keeps a <b>seasonal shelf</b>: twelve holiday brews that join the
          regular menu. And yes — since then the garden did grow, from its founding eleven to forty-eight
          crops and five orchard trees. Still, these seasonal drinks ask for the stranger corners of the
          world as much as for the garden: <b>glow berries</b> from caves, a <b>sea
          pickle</b> from warm reefs, <b>turtle eggs</b> from the beach, the odd rabbit&apos;s foot after
          a lucky hunt, and one Relics treat used for the very first time in a cauldron.
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          {[
            ['🍀', "St. Patrick's", "Shamrock Shake (lucky, and you glow while it lasts) · Crock O' Gold (a gold nugget distilled into potato gold) · Bog-Kelp Collins (gills included)"],
            ['🎃', 'Halloween', "Head of the Hollow (fire resistance from the inside) · Widow's Kiss (drink it and the spiders never see you) · Sculk Swill (aged in silence — and you float a little)"],
            ['🎄', 'Christmas', "Yule Log Reserve (mulled red wine with cocoa and fir) · Reindeer Run (four percent, all sugar and speed) · Bishop's Knog (eggnog from the wrong kind of egg)"],
            ['🐰', 'Easter', "Torchflower Spritz (the spring's first bloom, pickable and bitter-fresh) · Warren Weizen (a rabbit's foot in the mash — hop high)"],
            ['💘', "Valentine's", 'Chocolate Heart Flip — a Relics chocolate heart melted into the foam. Absorption for whoever drinks it; the recipe to the heart is the point.'],
          ].map(([emoji, name, drinks]) => (
            <div key={name} className="rounded-xl border border-sp-bronze/30 bg-black/40 p-4 space-y-1.5">
              <p className="font-serif text-lg text-sp-gold leading-tight">{emoji} {name}</p>
              <p className="text-sm text-text-warm/80">{drinks}</p>
            </div>
          ))}
        </div>
        <p className="text-sm text-text-warm/80">
          The whole live menu — all ninety-two drinks with steps and alcohol — is on the{' '}
          <Link href="/recipes" className="text-sp-gold underline">recipes page</Link> under the{' '}
          <b>Sakagura</b> tab, and your in-game <b>Brewer&apos;s Manual</b> always carries the same list,
          rebuilt fresh every time a copy leaves the shelf.
        </p>
      </Section>

      {/* ---------- faq ---------- */}
      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">The still does nothing.</b> It needs all three: bottles <i>and</i> a glowstone dust inside, heat directly below, and 20 seconds. Right-click it - the action bar says exactly what is missing.</li>
          <li><b className="text-sp-gold">My drink says &quot;Unfinished&quot; forever.</b> It is not finished: the lore names the missing step, and the still or cask will tell you when it is done.</li>
          <li><b className="text-sp-gold">Two identical bottles will not stack.</b> Different history in the glass means different item. Brewed together, they do stack - so batch your runs.</li>
          <li><b className="text-sp-gold">Do casks keep ageing while offline?</b> Yes - that is the point. Twenty minutes of real time is one year.</li>
          <li><b className="text-sp-gold">Can I break a cauldron I forgot about?</b> Right-click an empty hand anywhere: the action bar always reports the truth about what is inside.</li>
          <li><b className="text-sp-gold">Does it work with the other plugins?</b> It is grown from them: the Kitchen supplies steamed rice and tea, Steamworks plates shine on the still, and every recipe is readable in <b>/zukan</b>, the recipe book and the recipes page.</li>
        </ul>
        <div className="flex flex-wrap justify-center gap-3 pt-2 text-sm">
          <Link href="/wiki?page=sakagura" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><ListOrdered className="w-4 h-4" /> Sakagura Wiki (quick reference)</Link>
          <Link href="/recipes" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><BookOpen className="w-4 h-4" /> All Recipes</Link>
        </div>
        <p className="text-xs text-text-warm/50 text-center">Numbers on this page match the live plugin (Sakagura {`0.5.0`}). Exact times are always shown in-game: right-click anything, or use /zukan.</p>
      </Section>
    </div>
  );
}
