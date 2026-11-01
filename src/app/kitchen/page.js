import React from 'react';
import Link from 'next/link';
import { ChefHat, Sprout, Utensils, Flame, Users, Armchair, Lightbulb, BookOpen, ListOrdered } from 'lucide-react';
import BuiltBy from '@/components/BuiltBy';
import { Hero, Section, Card, Table, WikiButtons } from '@/components/GuideKit';

export const metadata = {
  title: 'Kitchen, explained — Iekcorbar',
  description: 'Farming and cooking on Iekcorbar: ten crops, the cutting board, cooking pot and kamado stove, shared meals and kitchen furniture. No mods needed.',
};

const NAV = [
  ['#idea', 'The idea'],
  ['#crops', 'Crops'],
  ['#stations', 'Stations'],
  ['#meals', 'Shared meals'],
  ['#decor', 'Furniture'],
  ['#friends', 'Works with'],
  ['#faq', 'FAQ'],
];

export default function KitchenPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      <Hero icon={ChefHat} kicker="Kitchen · Farming & cooking" title="Cooking, Explained" nav={NAV} tint="from-orange-950/20">
        Kitchen turns farming and food into a hobby of its own. Plant rice in a flooded field, harvest tea in a cherry grove,
        cut fish on a cutting board and cook a bowl of ramen over a fire. Then put the dish on the table and let your friends
        eat from it, bite by bite. Everything works in the normal game; the{' '}
        <Link href="/wiki?page=kitchen" className="text-sp-gold underline">wiki</Link> is the quick reference.
      </Hero>

      <Section id="idea" icon={ChefHat} kicker="Start here" title="The big idea">
        <div className="grid sm:grid-cols-4 gap-4">
          <Card title="Grow"><p>Ten new crops grow wild and on your farmland. Right-click a ripe crop to pick it; it grows back.</p></Card>
          <Card title="Prepare"><p>Cut ingredients with a knife on the Cutting Board.</p></Card>
          <Card title="Cook"><p>Combine up to six ingredients in the Cooking Pot over a fire.</p></Card>
          <Card title="Share"><p>Put the dish on a table. Every bite is a little animation.</p></Card>
        </div>
        <p className="text-sm text-text-warm/80">
          Food is more than decoration: dishes give good effects, and a shared meal feeds a whole table.
        </p>
      </Section>

      <Section id="crops" icon={Sprout} kicker="Seeds & fields" title="Ten crops">
        <Table
          head={['Crop', 'Plant', 'Harvest', 'Grows wild in']}
          rows={[
            ['Rice', 'Rice', '2-4 Rice', 'swamps, rivers'],
            ['Soybean', 'Soybeans', '2-4 Soybeans', 'plains'],
            ['Napa Cabbage', 'Napa Cabbage Seeds', '1 cabbage + seeds', 'beaches'],
            ['Green Onion', 'Green Onion', '2-3 Green Onions', 'forests, taiga'],
            ['Daikon', 'Daikon Seeds', '1 Daikon + seeds', 'meadows, flower fields'],
            ['Tea', 'Tea Seeds', '2-3 Tea Leaves', 'cherry groves, jungles'],
            ['Corn', 'Corn', '2-4 Corn', 'badlands, savanna'],
            ['Tomato', 'Tomato', '1-3 Tomatoes', 'cultivated only'],
            ['Chili Pepper', 'Chili Pepper', '1-3 Chili Peppers', 'cultivated only'],
            ['Sesame', 'Sesame Seeds', '2-3 Sesame Seeds', 'cultivated only'],
          ]}
        />
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>Crops grow on <b>farmland</b> (or Rich Soil) like wheat: they need light and only grow while the area is loaded. Bone meal speeds them up.</li>
          <li><b>Pick a ripe crop with a right-click</b> on the soil under it. It drops the harvest and starts growing again.</li>
          <li><b>Rice</b> needs wet farmland (water within four blocks). <b>Tea</b> is a bush that drops back to a young bush. <b>Corn</b> cuts into cornmeal.</li>
          <li>With a knife, breaking tall grass sometimes gives <b>straw</b> for bales, mats and compost.</li>
        </ul>
      </Section>

      <Section id="stations" icon={Flame} kicker="Where the work happens" title="Knives & stations">
        <div className="grid sm:grid-cols-3 gap-4">
          <Card title="Cutting Board" stat="Stick + 2 planks">
            <p>Put an ingredient on it, right-click with a <b>knife</b> and the pieces pop out. Every cut wears the knife a little.</p>
          </Card>
          <Card title="Cooking Pot" stat="On a heat source">
            <p>Six ingredient slots, a container slot (bowls or bottles) and a result slot. The clock shows progress or what is missing. It keeps cooking while nobody watches.</p>
          </Card>
          <Card title="Kamado Stove" stat="Light with flint & steel">
            <p>Heats a pot on top of it. Without a pot it is a <b>grill</b>: raw food cooks twice as fast as on a campfire.</p>
          </Card>
        </div>
        <Table
          head={['Knife', 'Recipe']}
          rows={[
            ['Flint Knife', 'flint + stick'],
            ['Iron Hocho', 'iron ingot + stick'],
            ['Diamond Hocho', 'diamond + stick'],
            ['Netherite Hocho', 'Diamond Hocho + netherite ingot in a smithing table'],
          ]}
        />
        <p className="text-sm text-text-warm/80">
          Heat for the pot comes from a campfire, fire, lava, a magma block, a lit furnace or smoker, or a lit Kamado Stove.
          Every cutting and cooking recipe is in <code>/zukan</code> and on the <Link href="/recipes" className="text-sp-gold underline">recipes page</Link>.
        </p>
      </Section>

      <Section id="meals" icon={Users} kicker="Better together" title="Shared meals">
        <p className="text-sm text-text-warm/80">
          <b>Sneak + right-click the top of a block</b> to put a dish down. Anyone can then right-click it to take a bite:
          the chopsticks pick up one piece, you eat it, and the next person takes the next piece.
        </p>
        <Table
          head={['Meal', 'How to make it', 'Bites']}
          rows={[
            ['Sushi Platter', 'Plate + 2 Salmon Nigiri + 2 Tuna Nigiri + 2 Maki Rolls', '6'],
            ['Ramen', 'From the Cooking Pot', '4'],
            ['Bento Box', 'Wooden slab + Steamed Rice + Tamagoyaki + Kabocha Tempura + Takuan + Salmon Sashimi', '4'],
            ['Dango Plate', 'Plate + 3 Dango', '3'],
            ['Tea Set', 'Plate + 4 Green Tea', '4 cups'],
          ]}
        />
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li>You cannot take a bite when you are full, except tea: you can always drink tea, and each cup gives Speed.</li>
          <li>Hit a meal to pick it up. An untouched meal comes back whole; a started one only gives the plate back.</li>
        </ul>
      </Section>

      <Section id="decor" icon={Armchair} kicker="Make it yours" title="Kitchen furniture">
        <p className="text-sm text-text-warm/80">
          Tansu cabinets, bamboo baskets that catch items, nine kinds of crates, straw bales, tatami mats and zabuton cushions
          (sit on them) make the kitchen feel like a kitchen. Organic compost turns into <b>Rich Soil</b>, which makes crops
          grow faster. The wiki has the recipes for every piece.
        </p>
      </Section>

      <Section id="friends" icon={Users} kicker="The rest of the server" title="Works together with">
        <ul className="list-disc pl-6 text-sm text-text-warm/80 space-y-1.5">
          <li><Link href="/sakagura" className="text-sp-gold underline">Sakagura</Link>: drinks use Kitchen ingredients such as steamed rice and tea.</li>
          <li><Link href="/steamworks" className="text-sp-gold underline">Steamworks</Link>: machines turn rice into flour and steamed rice into mochi in bulk.</li>
          <li><Link href="/tools" className="text-sp-gold underline">Kura</Link>: put your crates in a storage network.</li>
        </ul>
      </Section>

      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">Where do I get seeds?</b> Find wild patches in the biomes listed above and pick them. Tomato, chili and sesame are cultivated only.</li>
          <li><b className="text-sp-gold">The pot does nothing.</b> It needs heat underneath and a container if the dish is a soup or drink. The clock in the middle says what is missing.</li>
          <li><b className="text-sp-gold">Can I see all recipes?</b> Yes: <code>/zukan</code> in game or the recipes page.</li>
        </ul>
        <WikiButtons links={[['/wiki?page=kitchen', 'Kitchen Wiki', ListOrdered], ['/recipes', 'All Recipes', BookOpen]]} />
      </Section>
      <BuiltBy what="The Kitchen guide" />
    </div>
  );
}
