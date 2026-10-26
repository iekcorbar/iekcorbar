package com.iekcorbar.wildlife;

import java.util.Map;

/**
 * The journal's learning element (0.6.0): one genuinely obscure - and checked - fact per species,
 * plus one myth-buster, shown ONLY on the journal page of animals you actually recorded. That is the
 * reward for the hunt: the trivia never leaks through the wiki or Zukan to species you have not
 * found yourself. Strings stay short; the book page wraps them.
 */
final class Facts {
    /** id -> { fact, myth } */
    private static final Map<String, String[]> FACTS = Map.ofEntries(
        Map.entry("deer", new String[]{"Antlers are the fastest-growing tissue in the animal kingdom - some deer grow more than 2 cm in a single day.",
                "Deer cannot swim - they are strong swimmers, and rivers were even named after their crossing places."}),
        Map.entry("bear", new String[]{"A brown bear sprints at up to 48 km/h - faster than the fastest human alive.",
                "A bear rising on its hind legs is about to charge - usually it is curious and just wants a better look."}),
        Map.entry("boar", new String[]{"A boar's tusks keep growing its whole life and sharpen themselves against each other like a honing stone.",
                "Wild boars cannot turn quickly - they can, and they solve problems in experiments better than expected."}),
        Map.entry("duck", new String[]{"After moulting, a drake mallard is almost voiceless for weeks - his windpipe gets rebuilt.",
                "A duck's quack doesn't echo - it echoes just fine, only softly; acousticians have measured it."}),
        Map.entry("butterfly", new String[]{"A monarch tastes with its feet and navigates with a sun compass built into its antennae.",
                "Butterflies live only a day - most live weeks, and the migrating generation almost nine months."}),
        Map.entry("firefly", new String[]{"Firefly light is almost 100% efficient: a normal bulb wastes ~90% of its energy as heat, a glow-worm does not.",
                "The flashes are random - they are species-specific code; females answer males of their own kind."}),
        Map.entry("snail", new String[]{"A garden snail carries thousands of microscopic teeth on its tongue - it rasps leaves like a file.",
                "The shell is a house the snail carries - it grows as living part of the body, like bone."}),
        Map.entry("hedgehog", new String[]{"A hedgehog whips new smells into foam and smears it over its spines - 'self-anointing', still not fully explained.",
                "Hedgehogs carry apples on their spines in autumn - they like apples, but never transport them."}),
        Map.entry("capybara", new String[]{"Capybaras use one shared toilet in the water - the droppings feed the algae meadow and mark the territory.",
                "A capybara is a large rat - its closest relative is the guinea pig."}),
        Map.entry("tortoise", new String[]{"The oldest known tortoise lived past 180 - its age is read from growth rings in the shell, like a tree.",
                "Tortoises are dim - they remember routes for decades and learn from each other."}),
        Map.entry("lizard", new String[]{"A wall lizard regrows not just its tail but also its eardrum - though the new tail is cartilage, not bone.",
                "The dropped tail crawls away on its own - it wiggles to distract you and dies without the lizard."}),        Map.entry("crab", new String[]{"Shore crabs have blue blood - copper-based, like horseshoe crabs, not iron-based like ours.",
                "Hermit crabs are born with their shell - they must find and trade houses their whole life."}),
        Map.entry("songbird", new String[]{"A jay that hid food re-hides it when it notices another bird was watching - it understands being seen.",
                "Only male birds sing - females sing too in many species; cardinals often carry the better tune."}),
        Map.entry("dragonfly", new String[]{"With a catch rate near 95% the dragonfly is the world's most successful hunter - a lion manages about 25%.",
                "Dragonflies sting - they cannot; they are harmless, and the tail is a rudder."}),
        Map.entry("elephant", new String[]{"Elephants talk in infrasound - herds 'hear' each other kilometres away through the ground, felt in their feet.",
                "Elephants fear mice - they only startle at sudden small movements near the trunk."}),
        Map.entry("zebra", new String[]{"No two zebra stripe suits are alike - and an embryonic zebra is black with white stripes, not the other way round.",
                "Zebras are untameable horses - people broke them in repeatedly; the stripes mainly confuse horseflies."}),
        Map.entry("kangaroo", new String[]{"A mother kangaroo nurses two joeys at once with two different milk recipes - one per calf.",
                "Kangaroos box for fun - fights are about rank, and the tail doubles as a third leg for a lethal kick."}),
        Map.entry("ostrich", new String[]{"An ostrich eye is bigger than its brain - and one kick can kill a lion.",
                "Ostriches bury their heads in the sand - they turn their eggs with their beak, which looks like it from afar."}),
        Map.entry("crocodile", new String[]{"A crocodile bites like a vice but opens its jaw weakly enough to hold shut with a rubber band.",
                "Alligators and crocodiles are the same - a croc shows its lower teeth when shut (V-snout); an alligator (U-snout) hides them."}),
        Map.entry("koala", new String[]{"Koala fingerprints are nearly identical to human ones - they have confused forensic investigators.",
                "Koalas are little bears - they are marsupials, more closely related to wombats."}),
        Map.entry("mole", new String[]{"A mole tunnels some 20 metres of earth a day and must eat its own body weight - its worm prey dries out fast.",
                "Moles are fully blind - they see light and movement and feel worms vibrate through their snout."}),
        Map.entry("rhino", new String[]{"A rhino horn is keratin - the same material as your nails, just packed solid; there is no bone core.",
                "Rhinos charge out of anger - they are nearly short-sighted; sound and smell decide what they do."}),
        Map.entry("penguin", new String[]{"Emperor fathers incubate the egg on their feet for two months of polar winter without eating - losing half their weight.",
                "All penguins live at the ice - the Galapagos penguin lives right at the equator."}),
        Map.entry("orca", new String[]{"Each pod speaks its own dialect, and hunting tricks (like stranding for seals) are taught to the calves like tradition.",
                "An orca is a whale - it is the world's largest dolphin."}),
        Map.entry("piranha", new String[]{"Most piranhas eat mostly plants - snails, seeds and fish leftovers; they are no large-animal hunters.",
                "One drop of blood drives them berserk - they are shy; real danger is only in the dry season's shrinking pools."}),
        Map.entry("macaw", new String[]{"A macaw can outlive its owner - 60 to 80 years - and uses its beak as a third leg when climbing.",
                "Parrots only mimic meaninglessly - they learn words and concepts, like the famous parrot Alex."}),
        Map.entry("tanuki", new String[]{"Tanukis spread seeds of over 40 plant species in their droppings - gardeners of the Japanese forest.",
                "Tanukis are raccoons - they look alike, but are a separate Asian genus (raccoon dogs)."}),
        Map.entry("crane", new String[]{"Red-crowned cranes dance in duet - leaps, bows and calls - and cranes without a partner dance along.",
                "Cranes only dance in breeding season - they show off year-round, apparently for the joy of it."}),
        Map.entry("koi", new String[]{"The oldest recorded koi reached 226 - its age is read from growth rings in the scales, like a tree.",
                "Koi have a three-second memory - they learn and remember for months to years."}),
        Map.entry("macaque", new String[]{"Sweet-potato washing started with one female, Imo, in 1953 - the whole troop copied: documented animal culture.",
                "Snow monkeys bathe in hot springs by nature - it is learned; troops without springs never do it."}));

    private Facts() { }

    /** { fact, myth } or null when this species has none yet. */
    static String[] of(Species s) {
        return FACTS.get(s.id());
    }
}
