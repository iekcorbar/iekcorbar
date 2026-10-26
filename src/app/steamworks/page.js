import React from 'react';
import Link from 'next/link';
import { Cog, Gauge, Waves, Flame, Pickaxe, Droplet, Blocks, Wrench, ListOrdered, Train, Zap, Link2, Lightbulb, CircleHelp } from 'lucide-react';
import SteamworksPlayground from '@/components/SteamworksPlayground';

export const metadata = {
  title: 'Steamworks, explained — Iekcorbar',
  description: 'The full, plain-language guide to the Steamworks machines on Iekcorbar: rotation, speed, stress, power sources, processing, automation, trains, ores and oil. No mods, no prior knowledge needed.',
};

const IMG = (id) => `/recipes/steamworks/${id}.png`;

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

function Stat({ label, value }) {
  return (
    <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-sp-charcoal/80 border border-sp-bronze/40 text-[12px]">
      <span className="text-text-warm/60 uppercase tracking-wider text-[10px]">{label}</span>
      <span className="text-sp-gold font-semibold">{value}</span>
    </span>
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
  ['#rotation', 'Rotation'],
  ['#speed', 'Speed'],
  ['#stress', 'Stress'],
  ['#sources', 'Power'],
  ['#gears', 'Gears'],
  ['#machines', 'Machines'],
  ['#automation', 'Automation'],
  ['#trains', 'Trains'],
  ['#ores', 'Ores'],
  ['#oil', 'Oil'],
  ['#build', 'First build'],
  ['#faq', 'FAQ'],
];

const CHAIN = ['copper_plate', 'brass_ingot', 'brass_plate', 'gearbox', 'locomotive'];

export default function SteamworksExplainedPage() {
  return (
    <div className="space-y-8 pb-12 max-w-5xl mx-auto">
      {/* ---------- hero ---------- */}
      <section className="relative rounded-2xl overflow-hidden border-2 border-sp-gold p-10 text-center shadow-xl shadow-sp-gold/5 bg-black">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-950/30 via-black to-sp-charcoal/40" />
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-sp-copper/40 bg-amber-900/20 text-sp-gold text-xs font-semibold uppercase tracking-wider">
            <Cog className="w-3.5 h-3.5 animate-spin-gear-cw" /> Steamworks · The full explanation
          </div>
          <h1 className="font-serif text-4xl sm:text-5xl font-black text-sp-gold tracking-wider uppercase steampunk-glow">
            Making Things Turn
          </h1>
          <p className="text-text-warm/70 max-w-2xl mx-auto text-sm leading-relaxed">
            Steamworks is the machine-building side of Iekcorbar: water wheels that grind flour, presses that fold
            metal sheets, conveyor belts across town, programmable robot arms, and trains that run on ordinary rails.
            This page explains <b>everything</b> from zero - no mods, no downloads, and no experience with any tech
            mod ever required. It is the long version; the <Link href="/wiki?page=steamworks" className="text-sp-gold underline">wiki</Link> stays your quick reference.
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
          Every Steamworks machine runs on <b>rotation</b>. A power source - a water wheel, a windmill, a steam engine,
          or just your own hand on a crank - turns an invisible axle, and anything connected to that axle turns with it.
          You spend that rotation: a millstone grinds with it, a hammer presses with it, a belt carries with it.
          That is the entire model. There is no electricity, no fuel tanks inside machines and no GUI full of numbers -
          just spinning parts, and a simple budget of <b>speed</b> and <b>load</b> per network.
        </p>
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-4 space-y-2">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">The three rules</p>
          <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1">
            <li><b>Connected parts share rotation.</b> Everything touching - shafts, cogs, machines - is one network.</li>
            <li><b>Speed multiplies through gears.</b> A big cog driving a small one doubles the small one&apos;s speed.</li>
            <li><b>Load must fit the source.</b> Too many machines on one axle and the network jams and stops - gently, without breaking anything.</li>
          </ol>
        </div>
        <div className="rounded-xl border border-sp-copper/40 bg-amber-950/20 p-4">
          <p className="text-sm text-text-warm/80 leading-relaxed">
            <b className="text-sp-gold">Heard the word &quot;Create&quot;?</b> Create is a famous PC mod with similar spinning machines,
            and players sometimes recognise it here. Steamworks is our own take on that idea, built as a server plugin:
            <b> nobody needs to install anything</b> - if you can join the server with a vanilla launcher, you can build
            all of this. The behaviour, the numbers and the models are ours and tuned for Iekcorbar.
          </p>
        </div>
      </Section>

      {/* ---------- rotation ---------- */}
      <Section id="rotation" icon={Zap} kicker="Rule 1" title="Rotation: the axle nobody sees">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          A <b>shaft</b> is an axle of pure rotation. Place one and it connects to whatever it touches: power sources,
          machines, other shafts end to end. Everything on the same connected pile turns at speeds derived from the
          source, which is called a <b>network</b>. You never &quot;power&quot; a machine with items or fuel - you just
          let it share an axle with something that turns.
        </p>
        <div className="grid sm:grid-cols-3 gap-4">
          <Card img="shaft" title="Shaft" stat="the wire of rotation">
            <p>Carries turn along its length, straight through blocks. Machines take rotation from a shaft touching their input side - usually <b>from below</b> or <b>through the side</b>, per machine.</p>
          </Card>
          <Card img="hand_crank" title="Hand Crank" stat="16 RPM · 128 Tq">
            <p>A power source you <em>are</em>: hold right-click and you turn it (sneak for the other way). Great for testing and small jobs - it just makes you hungry.</p>
          </Card>
          <Card img="speed_gauge" title="Right-click = truth" stat="the inspector">
            <p>Right-click any part with an empty hand and it tells you its exact speed, load and what is wrong. A Speed Gauge is a shaft with a permanent dial. Stuck? Inspect first.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- speed ---------- */}
      <Section id="speed" icon={Gauge} kicker="Rule 2" title="Speed (RPM): gears make the difference">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Speed is measured in <b>RPM</b> - revolutions per minute. A water wheel pushes 8 RPM; a steam engine pushes 24.
          Faster is better for most machines: they finish jobs sooner. You change speed by meshing <b>cogwheels</b> -
          that is all gears do here:
        </p>
        <Table
          head={['Setup', 'Result']}
          rows={[
            ['Small cog ← driven by → large cog', 'the small one turns twice as fast'],
            ['Large cog ← driven by → small cog', 'the large one turns half as fast (more load fits)'],
            ['Two small cogs, side by side', 'same speed, opposite direction'],
            ['Two large cogs at a right angle', 'rotation turns the corner, same speed'],
          ]}
        />
        <div className="flex items-center justify-center py-2 select-none">
          <img src={IMG('large_cogwheel')} alt="large cogwheel turning" className="h-28 w-28 object-contain animate-spin-gear-cw" style={{ animation: 'spin-gear-cw 12s linear infinite' }} />
          <img src={IMG('cogwheel')} alt="small cogwheel, two turns to the large one's one, the other way" className="h-16 w-16 object-contain -ml-7 -mr-2" style={{ animation: 'spin-gear-ccw 6s linear infinite' }} />
          <img src={IMG('shaft')} alt="and the fast axle beyond" className="h-10 w-4 object-contain opacity-80" />
        </div>
        <p className="text-center text-xs text-text-warm/60 -mt-1">
          Watch: the little one makes <b className="text-sp-gold">two turns to the big one&apos;s one</b> - the opposite way. That is the whole trick
          of gearing: trade speed for load, or load for speed.
        </p>
        <p className="text-xs text-text-warm/50">
          Careful: a part that would turn faster than <b>192 RPM</b> snaps its breaker pin and drops as an item - the
          network protects itself. Keep a belt between two big cogs and you are safe.
        </p>
      </Section>

      {/* ---------- stress ---------- */}
      <Section id="stress" icon={Waves} kicker="Rule 3" title="Stress (Tq): the load budget">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Think of the network as a team pulling a rope. Every source contributes <b>capacity</b> (in Tq), and every
          running machine takes <b>load</b> (also Tq). A millstone costs 4 Tq per RPM it turns at, a crushing wheel 8.
          When the load exceeds the capacity, the whole network <b>jams</b>: it slows to a stop and puffs a bit of steam.
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card img="stress_gauge" title="Stress Gauge" stat="your fuel light">
            <p>A dial on the axle showing how much of the budget is spent. Needle climbing to the red = add a power source or unplug a machine. It is the only &quot;meter&quot; you will ever need.</p>
          </Card>
          <Card title="Nothing ever explodes" stat="safe by design">
            <p>A jammed network just stops. Parts are never destroyed, items are never lost - except the deliberate 192 RPM breaker pin, which simply drops the part as an item so you can re-plan.</p>
          </Card>
        </div>
        <SteamworksPlayground />
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-4 text-sm text-text-warm/80 space-y-1">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70 mb-1">Other ways a network stops (all fixable)</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>Two sources fighting each other (turning opposite ways).</li>
            <li>Gears locked in a closed loop.</li>
            <li>The source itself ran out - a windmill blocked by blocks, a boiler out of fuel or water.</li>
          </ul>
        </div>
      </Section>

      {/* ---------- power sources ---------- */}
      <Section id="sources" icon={Flame} kicker="Choose your engine" title="Power sources: from thumbs to turbines">
        <p className="text-sm text-text-warm/80">Every source has a speed and a load budget. They get stronger along exactly one path: hands → river → wind → steam.</p>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card img="hand_crank" title="Hand Crank" stat="16 RPM · 128 Tq · free">
            <p>Hold right-click to turn. Sneak to reverse.</p>
            <p className="text-xs text-text-warm/60">Drives: 2 millstones, 1 hammer, 2 fans or 1 pumpjack. You get hungry - it runs on food.</p>
          </Card>
          <Card img="water_wheel" title="Water Wheel" stat="up to 8 RPM · 256 Tq · set and forget">
            <p>Needs <b>flowing</b> water past its paddles - a river, or a water channel. Each water side adds 4 RPM; use one side, or the paddles get pulled both ways and it stops.</p>
            <p className="text-xs text-text-warm/60">The classic &quot;build it once, forget about it&quot; power.</p>
          </Card>
          <Card img="windmill" title="Windmill" stat="4–12 RPM · 64 Tq per RPM · weather-boosted">
            <p>Huge sails (±10 blocks). Higher up = faster: 4 below Y64, 6 up to Y100, 8 above - plus 2 in rain, 4 in thunder. Needs a clear circle around the sails, so build it on a tower.</p>
            <p className="text-xs text-text-warm/60">Best raw capacity before steam: 512 Tq on a stormy day.</p>
          </Card>
          <Card img="steam_engine" title="Steam Boiler + Steam Engine" stat="24 RPM · 1536 Tq · the big one">
            <p>The boiler drinks from a water source block beside it and burns coal (or lava, or <b>fuel</b> piped from your refinery). The engine sits <b>on top</b> of the boiler and turns its shaft while it burns. One engine drives a whole factory district.</p>
            <p className="text-xs text-text-warm/60">Coal ≈ 80 s; 1000 mB fuel ≈ 5 min.</p>
          </Card>
        </div>
        <div className="flex justify-center gap-6">
          <Img id="steam_boiler" size="h-16 w-16" alt="steam boiler" />
          <span className="text-sp-copper text-2xl self-center">+</span>
          <Img id="steam_engine" size="h-16 w-16" alt="steam engine" />
          <span className="text-sp-copper text-2xl self-center">=</span>
          <span className="text-sp-gold self-center font-serif text-lg">1536 Tq</span>
        </div>
      </Section>

      {/* ---------- gears / transmission ---------- */}
      <Section id="gears" icon={Link2} kicker="The plumbing of rotation" title="Getting the turn where you need it">
        <p className="text-sm text-text-warm/80">A shaft is straight and blunt. These are the tools that route, switch and steer rotation through a build:</p>
        <div className="grid sm:grid-cols-3 gap-4">
          <Card img="gearbox" title="Gearbox" stat="turn the corner">
            <p>Rotation in one side, out of any of the four others (not top/bottom). Opposite sides spin opposite ways - two gearboxes can even flip direction twice.</p>
          </Card>
          <Card img="clutch" title="Clutch" stat="redstone switch">
            <p>A shaft until it gets a redstone signal - then it disconnects and the far side coasts to a stop. Its lamp goes red. Gates, farms, emergency stops.</p>
          </Card>
          <Card img="gearshift" title="Gearshift" stat="redstone reverse">
            <p>Passes rotation through; with redstone, the far side turns the other way. Perfect for flipping a conveyor belt&apos;s direction or a drill&apos;s bite.</p>
          </Card>
          <Card img="sprocket" title="Chain Sprocket" stat="spin across space">
            <p>Chain one sprocket to another up to 12 blocks away with iron chains - rotation hops gaps and corners without a single shaft.</p>
          </Card>
          <Card img="shaft_half" title="Shaft" stat="the straight pipe">
            <p>Connects end to end, through blocks. Place against a face to point that way.</p>
          </Card>
          <Card img="speed_gauge" title="Gauges" stat="the instruments">
            <p><b>Speed Gauge:</b> RPM on the axle. <b>Stress Gauge:</b> network load. Both cost a compass/clock on a shaft - and save hours of guessing.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- machines ---------- */}
      <Section id="machines" icon={Wrench} kicker="Spend the rotation" title="Processing machines: the factory floor">
        <p className="text-sm text-text-warm/80">These do the actual work. Every one takes items in and gives results out - by hand, by hopper, or by belt.</p>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card img="millstone" title="Millstone" stat="grinds · 4 Tq/RPM · power from below">
            <p>Wheat → <b>flour</b> (the Kitchen starts here), cobble → gravel → sand, bones → meal, wool → string, sugar cane → sugar, glowstone → dust, blaze rod → powder.</p>
            <p className="text-xs text-text-warm/60">Right-click items in, empty hand out. A hopper on top feeds it; a chest beside it collects.</p>
          </Card>
          <Card img="steam_hammer" title="Steam Hammer" stat="presses · power from the side">
            <p>Iron/copper/gold ingots → <b>plates</b> (the currency of all Steamwork), sugar cane → paper, clay → balls, stone → cracked variants. Drop an item on the block <b>under</b> it - one strike per item, a hopper below collects.</p>
          </Card>
          <Card img="crushing_wheel" title="Crushing Wheels" stat="double ore yield · 8 Tq/RPM per wheel">
            <p>Two wheels facing each other on one axle. Throw ores on top: iron/gold ore → 2-3 raw, copper → 7-10, diamond → 1-2. Mine with <b>Silk Touch</b> and crush - it beats Fortune.</p>
            <p>Then crush the raw ore itself: 1 raw → <b>2 metal dust</b> (iron, copper, gold, zinc, tin), and every dust smelts to <b>one ingot</b>. That is Create's doubling: two ingots from one raw ore.</p>
            <p className="text-xs text-text-warm/60">Results fall out underneath; put a hopper there.</p>
          </Card>
          <Card img="bellows_fan" title="Bellows Fan" stat="processes in the breeze">
            <p>Blows air 4–12 blocks. Items that pass through it are transformed after 5 s: lava stream = smelting, fire = smoking, <b>water = washing</b> (concrete powder becomes concrete, gravel gives flint), soul fire = haunting.</p>
            <p className="text-xs text-text-warm/60">Also pushes items, mobs and... you.</p>
          </Card>
          <Card img="kettle" title="Kettle + Mixer" stat="brews and cooks · mixer powered from above">
            <p>Kettle holds ingredients + water, the Mixer stirs with heat below. Copper + zinc → <b>brass</b>, flour + water → bread, flour + cocoa → cookies, cake batter, mushroom stew, stone recipes (diorite, granite), gunpowder from sulfur.</p>
            <p className="text-xs text-text-warm/60">This is the only place metal alloys are made.</p>
          </Card>
          <Card img="belt" title="Conveyor Belt" stat="carries · 0.5 Tq/RPM per piece">
            <p>Pieces join into one belt; power from the side. Runs the way the rollers turn - a reversed axle (sneak-crank or gearshift) reverses the belt. Items ride into chests, hoppers or kettles; mobs and players can ride too (sneak to hold on).</p>
            <p className="text-xs text-text-warm/60">Under a steam hammer, items queue up to be pressed. That is a factory line.</p>
          </Card>
        </div>
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-4">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70 mb-2">The material ladder</p>
          <div className="flex items-center justify-center flex-wrap gap-3 sm:gap-5">
            {CHAIN.map((id, i) => (
              <React.Fragment key={id}>
                <div className="flex flex-col items-center gap-1">
                  <Img id={id} size="h-12 w-12" />
                  <span className="text-[10px] text-text-warm/60 capitalize">{id.replace(/_/g, ' ')}</span>
                </div>
                {i < CHAIN.length - 1 && <span className="text-sp-copper text-xl pb-4">→</span>}
              </React.Fragment>
            ))}
          </div>
          <p className="text-xs text-text-warm/60 text-center pt-2">
            Mine copper &amp; zinc → smelt → mix into brass → press plates → build gearboxes, pumps and locomotives.
          </p>
        </div>
      </Section>

      {/* ---------- automation ---------- */}
      <Section id="automation" icon={ListOrdered} kicker="Let the town work for itself" title="Automation: drill, saw, belt and the Karakuri Arm">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card img="drill" title="Mechanical Drill" stat="breaks · power from behind">
            <p>Mines the block in front of it - stone in ~2 s at crank speed, obsidian in about a minute. Builds mob grinders and tunnels; drops what an iron pickaxe would.</p>
          </Card>
          <Card img="saw" title="Mechanical Saw" stat="fells forests">
            <p>Cuts wood, leaves and crops only. Aim it at the base of a tree trunk and it harvests the <b>whole tree above it</b>. Replant with a placer arm and you have a sawmill loop.</p>
          </Card>
          <Card img="arm" title="Karakuri Arm" stat="a programmable robot · power from below">
            <p>Teach it up to 8 points, give it a gripper / placer / drill head, and write it a tiny program in a book - then it sorts chests, feeds machines, farms trees, everything.</p>
            <p className="text-xs text-text-warm/60">
              Programs copy between arms on <b>Punch Cards</b>. Full language, examples and an online checker:{' '}
              <Link href="/steamworks/arm" className="text-sp-gold underline">Karakuri Arm Guide</Link>.
            </p>
          </Card>
          <Card img="punch_card" title="Fair-play automation" stat="protection">
            <p>Drills, saws and arms remember <b>who placed them</b> and only break or place where that player is allowed to build. While the owner is offline they pause. No grief-bots, no unclaimed land stripped bare.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- trains ---------- */}
      <Section id="trains" icon={Train} kicker="Logistics with a whistle" title="Trains on ordinary rails">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Steamworks trains run on <b>normal Minecraft rails</b> - straight, curves, slopes - but they are proper
          machines, not minecarts. A brass locomotive pulls passenger and cargo wagons, and a clock in the cab turns
          it into an <b>autopilot shuttle</b> that stops at your stations, waits at red signals and turns around at
          dead ends. Cargo wagons are 27-slot chests that survive restarts.
        </p>
        <div className="grid sm:grid-cols-3 gap-4">
          <Card img="locomotive" title="Locomotive" stat="W = steam · S = brake · jump = whistle">
            <p>Right-click a rail to place, right-click wagons onto the train. Burn coal for steam minutes (only while driving - coasting is free).</p>
          </Card>
          <Card img="station" title="Stations & Signals" stat="railway infrastructure">
            <p>Rename a station and riders see &quot;Next stop&quot;. Signals turn red while a train sits in the section beyond them - automatic, and trains genuinely wait.</p>
          </Card>
          <Card img="cargo_wagon" title="Autopilot" stat="one clock in the cab">
            <p>Fuelled, nobody aboard: the train runs the line, stops 6 s at every station, obeys signals, reverses at the end of track. Two stations = a working bus service between towns.</p>
          </Card>
        </div>
        <div className="flex justify-center gap-4">
          <Img id="locomotive" size="h-12 w-12" alt="" />
          <Img id="passenger_wagon" size="h-12 w-12" alt="" />
          <Img id="cargo_wagon" size="h-12 w-12" alt="" />
        </div>
      </Section>

      {/* ---------- ores ---------- */}
      <Section id="ores" icon={Pickaxe} kicker="Raw materials" title="New ores: zinc, tin & sulfur">
        <p className="text-sm text-text-warm/80">
          Brass and canisters need metals the overworld never had. Three new ores generate worldwide (even in already
          explored areas, from the first time a chunk reloads). They mine like real ores - and crushing them doubles the yield.
        </p>
        <Table
          head={['Ore', 'Where', 'Drops']}
          rows={[
            ['Zinc (also deepslate)', 'Y -48 → 72, common', 'Raw Zinc → smelt to Zinc Ingot'],
            ['Tin (also deepslate)', 'Y -24 → 96', 'Raw Tin → smelt to Tin Ingot'],
            ['Sulfur (also deepslate)', 'deep, Y -64 → 0', '2-4 Sulfur (iron pick or better)'],
            ['Nether Sulfur', 'the Nether', '2-5 Sulfur (stone pick or better)'],
          ]}
        />
        <div className="flex justify-center gap-4">
          {['raw_zinc', 'raw_tin', 'sulfur', 'zinc_ingot', 'tin_ingot'].map(id => <Img key={id} id={id} size="h-12 w-12" alt="" />)}
        </div>
        <p className="text-xs text-text-warm/60">
          Zinc + copper go into the kettle-mixer for <b>brass</b>; tin presses into <b>canisters</b> for the oil trade;
          sulfur + charcoal becomes gunpowder. Silk Touch the ore block and drop it on crushing wheels.
        </p>
      </Section>

      {/* ---------- oil ---------- */}
      <Section id="oil" icon={Droplet} kicker="The black gold" title="Oil: find it, pump it, refine it (cleanly)">
        <p className="text-sm text-text-warm/80 leading-relaxed">
          Roughly 1 in 12 overworld chunks hides an oil field (more in deserts, badlands and oceans - and they run dry
          if you drain them). The industry is one chain, from dowsing rod to boiler:
        </p>
        <div className="flex flex-col items-center gap-1 py-2">
          <div className="flex items-center justify-center flex-wrap gap-3 sm:gap-5">
            {['dowsing_rod', 'pumpjack', 'pipe', 'tank', 'refinery', 'fuel_canister'].map((id, i, arr) => (
              <React.Fragment key={id}>
                <div className="flex flex-col items-center gap-1">
                  <Img id={id} size="h-12 w-12" />
                </div>
                {i < arr.length - 1 && <span className="text-sp-copper text-xl pb-5">→</span>}
              </React.Fragment>
            ))}
          </div>
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">find → pump → pipe → store → refine → drive</p>
        </div>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="The chain" stat="fluids need no power to flow">
            <p className="space-y-1">
              <span className="block">• <b>Dowsing rod:</b> right-click - it reports the oil below and nearby, plus pollution in the chunk.</span>
              <span className="block">• <b>Pumpjack</b> (8 Tq/RPM per 32 mB/s) nods on the field, pipes carry crude to <b>tanks</b> (16 buckets, colour-coded) and the <b>refinery</b>.</span>
              <span className="block">• <b>Refinery</b> (heated): 100 mB crude → 60 % <b>fuel</b> + 40 % <b>chemical waste</b>. Fuel feeds steam boilers (5 min per canister) and locomotives.</span>
            </p>
          </Card>
          <Card title="The waste - please handle it" stat="pollution is real">
            <p>Waste has nowhere to go if you do not give it a tank: it dumps into the chunk. Over 300 pollution, grass dies; over 600 players get poisoned; over 1500 also nauseous. A heated kettle with 4 bone meal and water neutralises a canister of waste. Green haze = you already did it.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- building blocks + first build ---------- */}
      <Section id="build" icon={Blocks} kicker="Put it together" title="A first build & a roadmap">
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-5 space-y-3">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">Your first machine in 5 minutes</p>
          <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1.5">
            <li>Craft <b>2 shafts</b> (2 copper stacked each) and a <b>hand crank</b> (planks + shaft).</li>
            <li>Place any block, a shaft on top, and the <b>millstone</b> on that shaft; break the block again.</li>
            <li>Stick the <b>crank</b> against the underside of the shaft and hold right-click.</li>
            <li>Put cobblestone in (right-click it in) - gravel falls out below. Swap wheat in for <b>flour</b> and bake with the Kitchen.</li>
            <li>Replace the crank with a <b>water wheel</b> in flowing river water, and never think about it again.</li>
          </ol>
          <div className="flex justify-center gap-4 pt-1">
            <Img id="shaft" size="h-10 w-10" alt="" />
            <Img id="hand_crank" size="h-10 w-10" alt="" />
            <Img id="millstone" size="h-10 w-10" alt="" />
            <Img id="flour" size="h-10 w-10" alt="" />
            <Img id="water_wheel" size="h-10 w-10" alt="" />
          </div>
        </div>
        <div className="rounded-xl border border-sp-bronze/30 bg-black/30 p-5 space-y-2">
          <p className="text-[11px] uppercase tracking-widest text-sp-gold/70">The road onward, in order of fun</p>
          <ol className="list-decimal pl-6 text-sm text-text-warm/80 space-y-1.5">
            <li><b>Windmill</b> on a tower - serious capacity (512 Tq).</li>
            <li><b>Conveyor belts + steam hammer</b> - your first production line; add crushing wheels for double ore.</li>
            <li><b>Kettle + mixer</b> - brass! Now the fancy machines unlock: gearboxes, pumps, arms, locomotives.</li>
            <li><b>Boiler + steam engine</b> - 1536 Tq; whole districts on one axle.</li>
            <li><b>Karakuri Arm</b> - teach, program, automate the warehouse. <Link href="/steamworks/arm" className="text-sp-gold underline">The Arm Guide</Link> has a checker for your programs.</li>
            <li><b>Trains</b> - rails, stations, autopilot shuttles between towns.</li>
            <li><b>Oil</b> - find, pump, refine... and keep the waste out of the groundwater.</li>
          </ol>
        </div>
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="32 building blocks, 4 styles" stat="riveted copper · brass · lacquer · tatami">
            <p>Steamwork your <em>walls</em>: metal plates, gear panels, shoji screens, kawara roof tiles, tatami... all real blocks with stairs and slabs, crafted 3+1 in a 2×2 → 4. No vanilla block changes.</p>
            <p className="text-xs"><Link href="/wiki?page=steamworks-building-blocks" className="text-sp-gold underline">Full block list in the wiki</Link></p>
          </Card>
          <Card title="Recipes & references" stat="never memorise anything">
            <p>Every recipe is in your <b>recipe book</b> in-game, in <b>/zukan</b> (the item browser) and on <Link href="/recipes" className="text-sp-gold underline">the recipes page</Link>. The compact <Link href="/wiki?page=steamworks" className="text-sp-gold underline">wiki</Link> mirrors this page in reference form - and <code className="text-emerald-300">/sw help</code> links back to this one.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- fresh ---------- */}
      <Section id="fresh" icon={Cog} kicker="Fresh from the foundry" title="What landed on 26 October">
        <div className="grid sm:grid-cols-2 gap-4">
          <Card title="Create-true crafting" stat="alloy → casing → everything">
            <p>The whole recipe tree now matches <b>Create 6</b> itself: andesite with nuggets becomes <b>Andesite Alloy</b>, alloy over a stripped log makes <b>Casings</b>, and the gear train, gauges and machines follow Create's exact grids. Brass is mixed 1 copper + 1 zinc in the kettle again. And the crushing wheels now double like Create's: raw ore → <b>metal dust</b> → twice the ingots. Your old habits from the mod translate one on one.</p>
          </Card>
          <Card title="7 new machines" stat="logistics & fluids">
            <p><b>Chute</b> and <b>Funnel</b> move and suck items, the <b>Depot</b> holds parcels for the <b>Weighted Ejector</b> (it really throws), the <b>Mechanical Pump</b> fills canisters straight from the pipe network and the <b>Fluid Valve</b> cuts the line when it stops turning. Plus a <b>Steam Whistle</b> for the joy of it - right-click.</p>
          </Card>
          <Card title="The Engineer's Manual" stat="/sw manual">
            <p>A living guide in your inventory: <b>clickable contents</b>, a <b>recipe button</b> for every part (jumps into /zukan), hands-on tips per chapter and <b>your own progress</b> - the manual notices what you placed, what you saw spin and what you crafted.</p>
          </Card>
          <Card title="New: Steamworks Additions" stat="atmosphere, no shafts needed">
            <p>The sister plugin: a <b>Steam Clock</b> that reads the real wall clock, strikes its bell every hour and answers a right-click with the exact time, a self-joining <b>Brass Railing</b> and a <b>Vent Grille</b> for every brewery facade. See <Link href="/wiki?page=additions-intro" className="text-sp-gold underline">Additions in the wiki</Link>.</p>
          </Card>
          <Card title="New: Steamworks Power" stat="industry for your river">
            <p>The muscle add-on, all on the same shafts: a <b>Water Turbine</b> that drinks the current for 768 Tq, a <b>Mechanical Press</b> that stamps nuggets to ingots, snow to ice and gives the honey back its bottle - and a <b>Stock Ticker</b> that counts your chests from across the yard. See <Link href="/wiki?page=power-intro" className="text-sp-gold underline">Power in the wiki</Link>.</p>
          </Card>
        </div>
      </Section>

      {/* ---------- faq ---------- */}
      <Section id="faq" icon={Lightbulb} kicker="When it misbehaves" title="FAQ & good to know">
        <ul className="space-y-3 text-sm text-text-warm/80">
          <li><b className="text-sp-gold">How do I pick a part back up?</b> Left-click it twice (once in creative) - like breaking, but you get the item with its contents.</li>
          <li><b className="text-sp-gold">It stopped. Now what?</b> Right-click anything on the network. It reports speed, load, and why. The Stress Gauge shows the budget; the cause is almost always too much load, a blocked windmill circle, or an unpowered source.</li>
          <li><b className="text-sp-gold">Do machines need fuel?</b> Only the steam boiler and the locomotive - and both take coal, lava or refined fuel. Everything else runs on rotation alone.</li>
          <li><b className="text-sp-gold">Is the load lost when it jams?</b> No. Add power or remove a machine and the network starts again by itself. Nothing explodes, ever.</li>
          <li><b className="text-sp-gold">Can machines grief my build?</b> Drills, saws and arms only act where their placer may build, and pause when they cannot. Your claim stays yours.</li>
          <li><b className="text-sp-gold">Does it work with the other plugins?</b> It is designed to: the Kitchen bakes millstone flour, Kura storage feeds belts, Wildlife farms use arms and fans, and TechCore and Zukan read every recipe.</li>
        </ul>
        <div className="flex flex-wrap justify-center gap-3 pt-2 text-sm">
          <Link href="/steamworks/arm" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><Cog className="w-4 h-4" /> Karakuri Arm Guide</Link>
          <Link href="/wiki?page=steamworks" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><ListOrdered className="w-4 h-4" /> Steamworks Wiki (quick reference)</Link>
          <Link href="/recipes" className="steampunk-btn px-4 py-2 rounded-lg text-white inline-flex items-center gap-2"><Wrench className="w-4 h-4" /> All Recipes</Link>
        </div>
        <p className="text-xs text-text-warm/50 text-center">Numbers on this page match the live plugin (Steamworks {`0.3.19`} + Additions {`0.1.2`} + Power {`0.1.0`}). For exact per-machine stats, always right-click the part in-game.</p>
      </Section>
    </div>
  );
}
