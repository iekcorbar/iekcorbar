import React from 'react';
import Link from 'next/link';
import { Boxes, Backpack, BookOpen, Search, Lightbulb, ListOrdered } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';
import { Hero, Section, Card, Table, WikiButtons } from '@/components/GuideKit';

export const metadata = {
  title: 'Storage, backpacks & the recipe book — Iekcorbar',
  description: 'Three tools every player ends up using on Iekcorbar: Kura storage networks, Satchel backpacks and the Zukan recipe browser. No mods needed.',
};

const NAV = [
  ['#zukan', 'Zukan'],
  ['#satchel', 'Backpacks'],
  ['#kura', 'Kura storage'],
  ['#faq', 'FAQ'],
];

export default function ToolsPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      <Hero icon={Boxes} kicker="Quality of life" title="Tools Every Player Needs" nav={NAV} tint="from-slate-800/30">
        Three things you will use every day: a recipe book that knows every item, backpacks that carry more than a slot,
        and a storage network that joins all your chests into one screen. All of them work in the normal game, no mods needed.
      </Hero>

      <Section id="zukan" icon={Search} kicker="Find any item" title="Zukan - the recipe book">
        <p className="text-sm text-text-warm/80">
          Zukan lists <b>every item</b> in the game, vanilla and everything the server adds, and shows how to <b>make</b> it
          and what it is <b>used for</b>.
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Opening it">
            <p><code>/zukan</code> (also <code>/recipes</code> and <code>/jei</code>) opens the browser.</p>
            <p>Or craft the <b>Zukan book</b> (a book and a compass), right-click it, or press <b>F</b> while holding it.</p>
          </Card>
          <Card title="Searching">
            <p><code>/zukan copper</code> opens it already searched. Hold an item and use <code>/zukan hand</code> for its recipes, or <code>/zukan uses</code> for what it is used for.</p>
            <p>In the search box: <b>#logs</b> for an item group, <b>@steamworks</b> for one plugin.</p>
          </Card>
        </div>
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li><b>Click</b> an item for how it is made, <b>right-click</b> for what it is used for, <b>shift-click</b> to bookmark it.</li>
          <li>Tabs along the top switch between crafting, smelting, brewing and every machine of the server.</li>
          <li>The same recipes are on the website's <Link href="/recipes" className="text-sp-gold underline">recipes page</Link>.</li>
        </ul>
      </Section>

      <Section id="satchel" icon={Backpack} kicker="Carry more" title="Satchel - backpacks">
        <p className="text-sm text-text-warm/80">
          A backpack sits in one inventory slot and opens like a chest when you right-click it. The contents live in the item
          itself, so if you die the backpack drops with everything in it.
        </p>
        <Table
          head={['Backpack', 'Storage', 'Upgrade slots']}
          rows={[
            ['Canvas', '18', '1'],
            ['Copper', '27', '2'],
            ['Iron', '36', '3'],
            ['Gold', '45', '3'],
            ['Diamond', '45', '4'],
            ['Netherite', '45', '5'],
          ]}
        />
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>Backpacks <b>cannot be crafted</b>: find them at wandering traders or at the shop NPC.</li>
          <li>The bottom row holds <b>upgrade slots</b>. Upgrades are crafted and add automatic pickup, a magnet for nearby items, feeding, refilling your hotbar and a backpack that never despawns.</li>
          <li>With a Deposit or Restock upgrade, sneak + right-click a chest to move matching items in or out.</li>
          <li>A backpack cannot go inside another backpack.</li>
        </ul>
      </Section>

      <Section id="kura" icon={Boxes} kicker="One screen for everything" title="Kura - storage networks">
        <p className="text-sm text-text-warm/80">
          Ten chests, three barrels and no idea where the iron went? Kura (Japanese for &quot;storehouse&quot;) joins your
          chests into one network, so a single terminal shows everything you own, with search and sorting.
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Connect" stat="Inventory Connector">
            <p>Put an Inventory Connector in the middle of your storage room. Every chest touching it joins, and so does everything touching those chests.</p>
          </Card>
          <Card title="Find and fetch" stat="Storage Terminal">
            <p>Open a terminal, search for an item and take it out. Click items in to put them back.</p>
          </Card>
          <Card title="Craft from storage" stat="Crafting Terminal">
            <p>It uses what is in your network and refills its grid after each craft. From here you can also look up a recipe in Zukan and fill the grid from the network.</p>
          </Card>
          <Card title="Reach it anywhere" stat="Wireless terminals">
            <p>Open your network from across the base, or from another world with the advanced version.</p>
          </Card>
        </div>
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>Hoppers, filters and level emitters keep machines supplied, and also work with Steamworks belts and arms.</li>
          <li>Sake casks from Sakagura, furnaces, hoppers and brewing stands are never used as storage, so your drinks keep ageing.</li>
        </ul>
      </Section>

      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">I cannot craft a backpack.</b> That is intended: they are sold, not crafted. Upgrades are crafted.</li>
          <li><b className="text-sp-gold">Zukan shows no crafting recipe for something.</b> Some items come from shops, events or quests. The item page says so when there is no recipe.</li>
          <li><b className="text-sp-gold">My storage network is not finding a chest.</b> It must touch the connector, or touch a chest that does. Parts and tools are listed in the wiki.</li>
        </ul>
        <WikiButtons links={[['/wiki?page=zukan', 'Zukan Wiki', BookOpen], ['/wiki?page=satchel-intro', 'Satchel Wiki', ListOrdered], ['/wiki?page=kura', 'Kura Wiki', ListOrdered]]} />
      </Section>
      <BuiltBy what="This guide" />
    </div>
  );
}
