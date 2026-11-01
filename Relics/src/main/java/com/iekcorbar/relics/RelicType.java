package com.iekcorbar.relics;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The relics, in collections:
 * <ul>
 *   <li>"Shoutlander's Shadow" (Halloween 2026): remains of Shoutlander's creatures and of the
 *   First Settlement they destroyed, relics of the old world and of the Guild. Texts only use
 *   the lore's known history.</li>
 *   <li>"Xmas Town" (Christmas 2026, for the Xmas Town region): a cosy Christmas village with
 *   steampunk and Japanese touches, with working pieces (presents, advent calendar, music box,
 *   snow globe, cookie plate, sleigh to sit in).</li>
 *   <li>"Valentine's" (14 February): the town in rose and brass - wishing well, heart arch,
 *   love locks, candy jar and a clockwork heart that keeps beating.</li>
 * </ul>
 * Models: relics:&lt;id&gt; (+ _wall / _ceiling / _hanging variants) from tools/make_pack.py.
 */
enum RelicType {
    // ---------------------------------------------------------------- Shoutlander's Shadow
    CREATURE_SKULL(Collection.SHADOW, "Creature Skull", "One of Shoutlander's creatures. Something still burns behind the eyes.",
            Base.SOLID, 1, 0, "creature_skull", "creature_skull_wall", null, Effect.SKULL, Use.NONE, 1.5f, false),
    CREATURE_RIBCAGE(Collection.SHADOW, "Creature Ribcage", "Whatever this was, it was big - and it died among the ruins.",
            Base.NONE, 3, 0, "creature_ribcage", null, null, Effect.NONE, Use.NONE, 3f, false),
    TORN_SETTLEMENT_BANNER(Collection.SHADOW, "Torn Settlement Banner", "The banner of the First Settlement. The claw marks are still there.",
            Base.NONE, 3, 0, "torn_settlement_banner", "torn_settlement_banner_wall", null, Effect.SWAY, Use.NONE, 3f, false),
    CHARRED_OUTPOST_LANTERN("Charred Outpost Lantern", "Pulled from the ashes of the first outpost. It never quite goes out.",
            Base.LIGHT, 1, 11, "charred_outpost_lantern", "charred_outpost_lantern_wall", "charred_outpost_lantern_hanging", Effect.FLAME),
    VIGIL_CANDLES("Vigil Candles", "Lit for the settlers who did not make it to the Guild.",
            Base.LIGHT, 1, 9, "vigil_candles", null, null, Effect.FLAME),
    SETTLERS_GRAVE(Collection.SHADOW, "Settler's Grave", "A settler of the First Settlement. The stone remembers the last to fall on this server.",
            Base.NONE, 3, 0, "settlers_grave", null, null, Effect.GRAVE, Use.NONE, 3f),
    RUIN_MONOLITH("Ruin Monolith", "A pillar of the world before the Devastation. The glyphs are not quite dead.",
            Base.SOLID, 2, 0, "ruin_monolith", null, null, Effect.GLYPHS),
    SPECIMEN_JAR("Specimen Jar", "From the Guild archive: a claw, kept for study. It is better in there.",
            Base.SOLID, 1, 0, "specimen_jar", null, null, Effect.BUBBLES),
    CREATURE_EGG_SAC("Creature Egg Sac", "Found in the ruins. It is warm. It is moving.",
            Base.NONE, 1, 0, "creature_egg_sac", null, "creature_egg_sac_ceiling", Effect.PULSE),
    GIBBET_CAGE("Gibbet Cage", "Someone was left in here. The bones did not leave.",
            Base.NONE, 1, 0, "gibbet_cage", null, "gibbet_cage_hanging", Effect.CAGE),
    ASH_PILE("Ash Pile", "What the creatures left of the first outpost.",
            Base.NONE, 1, 0, "ash_pile", null, null, Effect.ASH),
    WARDING_TOTEM("Warding Totem", "Guild work. Its lens wakes when creatures come close.",
            Base.SOLID, 2, 0, "warding_totem", null, null, Effect.TOTEM),
    SHATTERED_PORTAL_SHARD("Shattered Portal Shard", "A piece of the way out - the arch the survivors took to the Guild.",
            Base.SOLID, 1, 0, "shattered_portal_shard", null, null, Effect.PORTAL),

    // ---------------------------------------------------------------- Xmas Town
    CHRISTMAS_TREE(Collection.XMAS, "Christmas Tree", "Brass gears and glass baubles, and a star that never dims.",
            Base.SOLID, 3, 0, "christmas_tree", null, null, Effect.TWINKLE, Use.NONE, 1.5f),
    STRING_LIGHTS(Collection.XMAS, "String Lights", "Hang them on a wall or from a ceiling.",
            Base.LIGHT, 1, 7, "string_lights_ceiling", "string_lights", "string_lights_ceiling", Effect.TWINKLE, Use.NONE, 1f),
    ADVENT_WREATH(Collection.XMAS, "Advent Wreath", "One more candle burns every Sunday of Advent.",
            Base.LIGHT, 1, 3, "advent_wreath_4", "advent_wreath_wall_4", null, Effect.ADVENT, Use.NONE, 1f),
    STEAM_LANTERN(Collection.XMAS, "Brass Steam Lantern", "Warm light and a little puff of steam.",
            Base.LIGHT, 1, 14, "steam_lantern", "steam_lantern_wall", "steam_lantern_hanging", Effect.STEAM, Use.NONE, 1f),
    SNOW_LANTERN(Collection.XMAS, "Snow Lantern", "A yukimi stone lantern, capped with snow.",
            Base.LIGHT, 1, 12, "snow_lantern", null, null, Effect.NONE, Use.NONE, 1f),
    GIANT_CANDY_CANE(Collection.XMAS, "Giant Candy Cane", "Two blocks of peppermint. Right-click it with a dye to repaint the stripes.",
            Base.SOLID, 2, 0, "candy_cane_giant", null, null, Effect.NONE, Use.CANE, 1f),
    KADOMATSU(Collection.XMAS, "Kadomatsu", "Bamboo and pine to welcome the new year.",
            Base.NONE, 1, 0, "kadomatsu", null, null, Effect.NONE, Use.NONE, 1f),
    HEARTH(Collection.XMAS, "Hearth", "Stockings on the mantle and a fire that keeps the snow outside.",
            Base.SOLID, 3, 0, "hearth", null, null, Effect.HEARTH, Use.NONE, 1.5f),
    SNOWMAN(Collection.XMAS, "Snowman", "Top hat, brass goggles, carrot nose.",
            Base.SOLID, 2, 0, "snowman", null, null, Effect.SNOW, Use.NONE, 1f),
    SLEIGH(Collection.XMAS, "Sleigh", "Right-click to sit in it.",
            Base.NONE, 1, 0, "sleigh", null, null, Effect.NONE, Use.SEAT, 1f),
    TOY_TRAIN(Collection.XMAS, "Clockwork Toy Train", "Round and round it goes.",
            Base.NONE, 1, 0, "toy_train", null, null, Effect.TRAIN, Use.NONE, 1f),
    GIFT_BOX(Collection.XMAS, "Gift Box", "Right-click to fill it with presents, lock it for one player, and only they can open it when the gift window is open (24-26 December by default).",
            Base.NONE, 1, 0, "gift_box", null, null, Effect.NONE, Use.GIFT, 1.5f),
    ADVENT_CALENDAR(Collection.XMAS, "Advent Calendar", "From 1 to 24 December: open one door a day.",
            Base.NONE, 1, 0, "advent_calendar", "advent_calendar", null, Effect.NONE, Use.CALENDAR, 1f),
    MUSIC_BOX(Collection.XMAS, "Music Box", "Right-click to play the carol playlist on loop, sneak + right-click for songs, shuffle and pause.",
            Base.NONE, 1, 0, "music_box", null, null, Effect.NONE, Use.MUSIC, 1f),
    SNOW_GLOBE(Collection.XMAS, "Snow Globe", "Shake it with a right-click.",
            Base.NONE, 1, 0, "snow_globe", null, null, Effect.NONE, Use.GLOBE, 1f),
    MARKET_STALL(Collection.XMAS, "Christmas Market Stall", "Ginger, greens and gold for sale.",
            Base.SOLID, 2, 0, "market_stall", null, null, Effect.NONE, Use.NONE, 1.7f),
    COOKIE_PLATE(Collection.XMAS, "Cookie Plate", "Take a cookie with a right-click; refill it with gingerbread.",
            Base.NONE, 1, 0, "cookie_plate", null, null, Effect.NONE, Use.COOKIES, 1f),

    // ---------------------------------------------------------------- New Year
    FIREWORK_LAUNCHER(Collection.NEW_YEAR, "Firework Launcher", "Right-click for a little show. At midnight on New Year it fires by itself.",
            Base.SOLID, 1, 0, "firework_launcher", null, null, Effect.NONE, Use.LAUNCH, 1f),
    COUNTDOWN_CLOCK(Collection.NEW_YEAR, "Countdown Clock", "Right-click to see how long until the New Year. It strikes twelve at midnight.",
            Base.SOLID, 2, 0, "countdown_clock", null, null, Effect.CLOCK, Use.CLOCK, 1f),
    TEMPLE_BELL(Collection.NEW_YEAR, "Temple Bell", "Joya no kane: ring it 108 times to see the old year out.",
            Base.SOLID, 2, 0, "temple_bell", null, null, Effect.NONE, Use.BELL, 1f),
    KAGAMI_MOCHI(Collection.NEW_YEAR, "Kagami Mochi", "Two mochi and a bitter orange - luck for the year ahead.",
            Base.NONE, 1, 0, "kagami_mochi", null, null, Effect.NONE, Use.NONE, 1f),
    SHIMENAWA(Collection.NEW_YEAR, "Shimenawa", "A sacred rope with paper streamers. Hang it on a wall or ceiling.",
            Base.NONE, 1, 0, "shimenawa_ceiling", "shimenawa", "shimenawa_ceiling", Effect.NONE, Use.NONE, 1f),
    RED_PAPER_LANTERN(Collection.NEW_YEAR, "Red Paper Lantern", "A chochin for the festival nights.",
            Base.LIGHT, 1, 12, "red_paper_lantern", null, "red_paper_lantern_hanging", Effect.SWAY, Use.NONE, 1f),
    CONFETTI_CANNON(Collection.NEW_YEAR, "Confetti Cannon", "Right-click. Stand back.",
            Base.NONE, 1, 0, "confetti_cannon", null, null, Effect.NONE, Use.CONFETTI, 1f),
    CHAMPAGNE_TOWER(Collection.NEW_YEAR, "Sparkling Cider Tower", "Take a glass with a right-click; refill it with Sparkling Cider.",
            Base.NONE, 1, 0, "champagne_tower", null, null, Effect.NONE, Use.TOWER, 1f),
    NEW_YEAR_GARLAND(Collection.NEW_YEAR, "New Year Garland", "Gold stars and streamers for the wall.",
            Base.NONE, 1, 0, "new_year_garland", "new_year_garland", null, Effect.TWINKLE, Use.NONE, 1f),

    // ---------------------------------------------------------------- Valentine's
    HEART_BALLOON(Collection.VALENTINE, "Heart Balloon", "One balloon redder than the sky it floats under. It bobs, but never leaves.",
            Base.NONE, 1, 0, "heart_balloon", null, null, Effect.HOP, Use.NONE, 1f),
    ROSE_UNDER_BELL(Collection.VALENTINE, "Rose Under a Bell", "A single rose under brass and glass. Someone is trying very hard.",
            Base.SOLID, 1, 0, "rose_bell", null, null, Effect.NONE, Use.NONE, 1f),
    LOVE_LOCK_RAIL(Collection.VALENTINE, "Love Lock Rail", "Clip a padlock on, drop the key in the well, walk on.",
            Base.NONE, 1, 0, "love_lock_rail_wall", "love_lock_rail_wall", null, Effect.NONE, Use.NONE, 1f),
    HEART_ARCH(Collection.VALENTINE, "Heart Arch", "Walk through it together and the town counts it official.",
            Base.NONE, 2, 0, "heart_arch", null, null, Effect.HEART, Use.NONE, 2f),
    CUPID_STATUE(Collection.VALENTINE, "Cupid Statue", "His marble arrow has never hit anyone. People still dodge.",
            Base.SOLID, 1, 0, "cupid_statue", null, null, Effect.NONE, Use.NONE, 1.3f),
    WISHING_WELL(Collection.VALENTINE, "Wishing Well", "Right-click with a gold nugget: one coin, one wish, five seconds of believing.",
            Base.SOLID, 1, 0, "wishing_well", null, null, Effect.NONE, Use.WISH, 1f),
    SWAN_LANTERN(Collection.VALENTINE, "Twin Swan Lantern", "Two swans, one lamp. They pair for life - the lamp knows it.",
            Base.LIGHT, 1, 12, "swan_lantern", null, null, Effect.FLAME, Use.NONE, 1f),
    VALENTINE_BANNER(Collection.VALENTINE, "Valentine Banner", "Deep rose velvet with a stitched heart. The seamstress sewed it twice.",
            Base.NONE, 1, 0, "valentine_banner_wall", "valentine_banner_wall", null, Effect.SWAY, Use.NONE, 1f),
    HEART_LANTERN(Collection.VALENTINE, "Heart Lantern", "A paper lantern with a heart cut in. On the tenth of February the town is all glow.",
            Base.LIGHT, 1, 13, "heart_lantern", "heart_lantern_wall", "heart_lantern_hanging", Effect.TWINKLE, Use.NONE, 1f),
    TEDDY_BEAR(Collection.VALENTINE, "Teddy Bear", "Button eyes, stitched smile. Right-click to change his pose - arms up means hug.",
            Base.NONE, 1, 0, "teddy_bear", null, null, Effect.NONE, Use.POSE, 1f),
    CANDY_HEART_JAR(Collection.VALENTINE, "Candy Heart Jar", "Take one with a right-click; refill it with Chocolate Hearts.",
            Base.NONE, 1, 0, "candy_heart_jar", null, null, Effect.NONE, Use.JAR, 1f),
    BOUQUET_POPPER(Collection.VALENTINE, "Bouquet Popper", "Right-click and step back - it is confetti AND petals.",
            Base.NONE, 1, 0, "bouquet_popper", null, null, Effect.NONE, Use.CONFETTI, 1f),
    ROSE_PLANTER(Collection.VALENTINE, "Rose Planter", "A trough of roses for the bench to look at.",
            Base.NONE, 1, 0, "rose_planter", null, null, Effect.NONE, Use.NONE, 1f),
    LOVE_SONGBIRD(Collection.VALENTINE, "Love Songbird", "Right-click and it sings. A brass heart goes along with every verse.",
            Base.NONE, 1, 0, "love_songbird", null, null, Effect.NONE, Use.BIRD, 1f),
    SWEETHEART_TOPIARY(Collection.VALENTINE, "Sweetheart Topiary", "Years of shearing until the bush holds its shape - a heart holds better.",
            Base.SOLID, 2, 0, "sweetheart_topiary", null, null, Effect.NONE, Use.NONE, 1.6f),
    LOVE_POTION_SHELF(Collection.VALENTINE, "Love Potion Shelf", "Three bottles, no labels. The brewery swears they are only fizz.",
            Base.NONE, 1, 0, "love_potion_shelf", "love_potion_shelf", null, Effect.BUBBLES, Use.NONE, 1f),
    CLOCKWORK_HEART(Collection.VALENTINE, "Clockwork Heart", "Brass, glass and a steady beat. The inventor kept it for herself.",
            Base.SOLID, 1, 0, "clockwork_heart", null, null, Effect.PULSE, Use.NONE, 1f),
    LOVERS_BENCH(Collection.VALENTINE, "Lovers' Bench", "Wide enough for two, warm enough for one. Right-click to sit.",
            Base.NONE, 1, 0, "lovers_bench", null, null, Effect.NONE, Use.SEAT, 1f),
    HEART_WINDOW(Collection.VALENTINE, "Heart Window", "Stained glass that only shows its heart when the sun is behind it.",
            Base.LIGHT, 1, 6, "heart_window", "heart_window", null, Effect.NONE, Use.NONE, 1f),
    AMORE_MACHINE(Collection.VALENTINE, "Amore Automatron", "Right-click with a gold nugget: two chocolate hearts for one coin. It never makes change.",
            Base.SOLID, 1, 0, "amore_machine", null, null, Effect.NONE, Use.VEND, 1f),

    COUNTDOWN_BOARD(Collection.NEW_YEAR, "New Year Countdown Board", "A big scoreboard that counts down to the next New Year, zone by zone. Sneak + right-click (placer or staff) to change its time zone.",
            Base.NONE, 2, 0, "countdown_board", "countdown_board_wall", null, Effect.NONE, Use.BOARD, 2.7f),

    // ---------------------------------------------------------------- Easter
    EASTER_EGG(Collection.EASTER, "Easter Egg", "Hide it for an egg hunt: everyone can find each egg once.",
            Base.NONE, 1, 0, "easter_egg", null, null, Effect.NONE, Use.EGG, 1f),
    EGG_BASKET(Collection.EASTER, "Egg Basket", "A wicker basket of painted eggs.",
            Base.NONE, 1, 0, "egg_basket", null, null, Effect.NONE, Use.NONE, 1f),
    CLOCKWORK_BUNNY(Collection.EASTER, "Clockwork Bunny", "Wound up and hopping.",
            Base.NONE, 1, 0, "clockwork_bunny", null, null, Effect.HOP, Use.NONE, 1f),
    BLOSSOM_VASE(Collection.EASTER, "Blossom Vase", "A cherry branch in a porcelain vase.",
            Base.NONE, 1, 0, "blossom_vase", null, null, Effect.NONE, Use.NONE, 1f),
    EGG_TREE(Collection.EASTER, "Egg Tree", "Bare branches hung with painted eggs.",
            Base.NONE, 1, 0, "egg_tree", null, null, Effect.NONE, Use.NONE, 1f),

    // ---------------------------------------------------------------- St. Patrick's Day
    POT_OF_GOLD(Collection.PATRICK, "Pot of Gold", "Right-click once a day for a little luck.",
            Base.SOLID, 1, 0, "pot_of_gold", null, null, Effect.RAINBOW, Use.GOLD, 1f),
    RAINBOW_ARCH(Collection.PATRICK, "Rainbow Arch", "There must be gold at the end of it.",
            Base.NONE, 1, 0, "rainbow_arch", null, null, Effect.RAINBOW, Use.NONE, 1f),
    SHAMROCK_PLANTER(Collection.PATRICK, "Shamrock Planter", "Three leaves each - keep looking for the fourth.",
            Base.NONE, 1, 0, "shamrock_planter", null, null, Effect.NONE, Use.NONE, 1f),
    LEPRECHAUN_HAT(Collection.PATRICK, "Leprechaun Hat", "Somebody left in a hurry.",
            Base.NONE, 1, 0, "leprechaun_hat", null, null, Effect.NONE, Use.NONE, 1f),
    CELTIC_HARP(Collection.PATRICK, "Celtic Harp", "Right-click to play on repeat (again to stop), sneak + right-click to pick the tune.",
            Base.NONE, 2, 0, "celtic_harp", null, null, Effect.NONE, Use.HARP, 1.7f),
    GREEN_ALE_KEG(Collection.PATRICK, "Green Ale Keg", "Everything is green today.",
            Base.SOLID, 1, 0, "green_ale_keg", null, null, Effect.NONE, Use.NONE, 1f),

    // ---------------------------------------------------------------- Curiosities (steampunk + Japanese, all year)
    BRASS_TELESCOPE(Collection.CURIO, "Brass Telescope", "Right-click to look through it.",
            Base.NONE, 1, 0, "brass_telescope", null, null, Effect.NONE, Use.TELESCOPE, 1f),
    STEAM_GAUGE_PANEL(Collection.CURIO, "Steam Gauge Panel", "The needles never sit still.",
            Base.NONE, 1, 0, "steam_gauge_panel", "steam_gauge_panel", null, Effect.SWAP, Use.NONE, 1f),
    TESLA_COIL(Collection.CURIO, "Tesla Coil", "Crackles, hums and lights the room.",
            Base.LIGHT, 1, 10, "tesla_coil", null, null, Effect.SPARK, Use.NONE, 1f),
    BONE_XYLOPHONE(Collection.HALLOWEEN, "Bone Xylophone", "Right-click to play on repeat (again to stop), sneak + right-click to pick the tune.",
            Base.NONE, 1, 0, "bone_xylophone", null, null, Effect.NONE, Use.BONES, 2.25f),
    GRAMOPHONE(Collection.CURIO, "Gramophone", "Right-click to play an old record.",
            Base.NONE, 1, 0, "gramophone", null, null, Effect.NONE, Use.GRAMOPHONE, 1.5f),
    TORII_GATE(Collection.CURIO, "Torii Gate", "Walk through it into somewhere a little calmer.",
            Base.NONE, 1, 0, "torii_gate", null, null, Effect.NONE, Use.NONE, 1.5f),
    MANEKI_NEKO(Collection.CURIO, "Maneki-neko", "The beckoning cat waves luck your way.",
            Base.NONE, 1, 0, "maneki_neko", null, null, Effect.SWAP, Use.NONE, 1f),
    SHISHI_ODOSHI(Collection.CURIO, "Shishi-odoshi", "Fills, tips and clacks - the sound of a quiet garden.",
            Base.NONE, 1, 0, "shishi_odoshi", null, null, Effect.SHISHI, Use.NONE, 1f),
    BONSAI(Collection.CURIO, "Bonsai", "Decades of patience in a little pot.",
            Base.NONE, 1, 0, "bonsai", null, null, Effect.NONE, Use.NONE, 1f),
    BYOBU_SCREEN(Collection.CURIO, "Byobu Screen", "A folding paper screen.",
            Base.NONE, 1, 0, "byobu_screen", null, null, Effect.NONE, Use.NONE, 1f),
    WAGASA_STAND(Collection.CURIO, "Wagasa Stand", "Paper umbrellas, ready for the rain.",
            Base.NONE, 1, 0, "wagasa_stand", null, null, Effect.NONE, Use.NONE, 1f),
    ONSEN_TUB(Collection.ONSEN, "Hinoki Bath Tub", "A cypress tub, steaming gently. Nobody remembers filling it.",
            Base.NONE, 1, 0, "onsen_tub", null, null, Effect.STEAM, Use.NONE, 1f),
    ONSEN_LANTERN(Collection.ONSEN, "Snow-Viewing Lantern", "A stone lantern with a wide roof, built to hold the snow and the light.",
            Base.LIGHT, 1, 12, "onsen_lantern", null, null, Effect.FLAME, Use.NONE, 1f),
    KOTATSU(Collection.ONSEN, "Kotatsu", "A low heated table under a heavy blanket. Nobody has ever left it willingly.",
            Base.NONE, 1, 0, "kotatsu", null, null, Effect.NONE, Use.NONE, 1f),
    SNOW_BUNNY(Collection.ONSEN, "Snow Bunny", "Two berries for eyes, two leaves for ears. Right-click to perk the ears up.",
            Base.NONE, 1, 0, "snow_bunny", null, null, Effect.NONE, Use.POSE, 1f),
    WASH_SET(Collection.ONSEN, "Wash Stool and Bucket", "Wash first, soak after. The rule is older than the tub.",
            Base.NONE, 1, 0, "wash_set", null, null, Effect.NONE, Use.NONE, 1f),
    TOWEL_RACK(Collection.ONSEN, "Towel Rack", "Two towels, always one damp.",
            Base.NONE, 1, 0, "towel_rack", null, null, Effect.SWAY, Use.NONE, 1f),
    SAKE_TRAY(Collection.ONSEN, "Floating Sake Tray", "A tokkuri and two cups, drifting on the water. Only the first cup is polite.",
            Base.NONE, 1, 0, "sake_tray", null, null, Effect.BUBBLES, Use.NONE, 1f),
    SNOW_PINE(Collection.ONSEN, "Snow-Laden Pine", "A small pine under a winter's worth of snow. It is built small and drawn big.",
            Base.NONE, 2, 0, "snow_pine", null, null, Effect.NONE, Use.NONE, 1.5f),
    NOTICE_BOARD(Collection.CURIO, "Notice Board", "A village notice board. Sneak + right-click (placer or staff) to pin a notice; everyone can read it.",
            Base.NONE, 1, 0, "vanilla:oak_sign", null, null, Effect.NONE, Use.NOTICE, 1f),
    WISH_TREE(Collection.CURIO, "Wish Tree", "A little cherry tree for tanabata wishes. Right-click with a sheet of paper and type your wish; it hangs in the branches.",
            Base.NONE, 2, 0, "vanilla:cherry_sapling", null, null, Effect.NONE, Use.TANABATA, 1f),
    PILLAR_BOX(Collection.CURIO, "Pillar Box", "A British pillar box in engine red, re-riveted in brass. Nobody has seen a postman - yet it is always empty.",
            Base.SOLID, 2, 0, "pillar_box", null, null, Effect.STEAM, Use.NONE, 1f),

    DRAGON_NEST(Collection.CURIO, "Dragon Nest", "A wide nest of woven straw with a bed of embers and a brass egg. Dragons go looking for places like this: wild ones settle nearby, tame ones rest here and lay their eggs in it.",
            Base.LIGHT, 1, 6, "dragon_nest", null, null, Effect.FLAME, Use.NONE, 1.5f),

    // ---------------------------------------------------------------- Brewery & wooden helpers (0.3.9, shop/event stock: NOT craftable by default)
    SUGI_DAMA(Collection.CURIO, "Sugidama", "The cedar ball of a sakagura: hung up, the new brew is ready.",
            Base.NONE, 1, 0, "sugi_dama", null, null, Effect.NONE, Use.NONE, 1f, false),
    KOJI_CROCK(Collection.CURIO, "Koji Crock", "Where the rice meets the mould and the magic starts.",
            Base.SOLID, 1, 0, "koji_crock", null, null, Effect.NONE, Use.NONE, 1f, false),
    BOTTLING_CRATE(Collection.CURIO, "Bottling Crate", "Fresh bottles, packed in straw and ready for the shop.",
            Base.SOLID, 1, 0, "bottling_crate", null, null, Effect.NONE, Use.NONE, 1f, false),
    BARREL_STACK(Collection.CURIO, "Barrel Stack", "Two lying, one standing - a brewer's pyramid.",
            Base.NONE, 1, 0, "barrel_stack", null, null, Effect.NONE, Use.NONE, 1f, false),
    STEAM_PIPE(Collection.CURIO, "Loose Steam Pipe", "A spare line from the still. It was never quite shut off.",
            Base.NONE, 1, 0, "steam_pipe", null, null, Effect.STEAM, Use.NONE, 1f, false),
    TASTING_COUNTER(Collection.CURIO, "Tasting Counter", "Three cups, one opinion, no argument.",
            Base.NONE, 1, 0, "tasting_counter", null, null, Effect.NONE, Use.NONE, 1f, false),
    NOREN(Collection.CURIO, "Noren", "The brewery's cloth gate. Under it, you are a guest.",
            Base.NONE, 1, 0, "noren", null, null, Effect.SWAY, Use.NONE, 1f, false),
    BREW_KETTLE(Collection.CURIO, "Brew Kettle", "Charcoal under copper - tea for the distilling crew.",
            Base.LIGHT, 1, 9, "brew_kettle", null, null, Effect.HEARTH, Use.NONE, 1f, false),
    BOTTLE_RACK(Collection.CURIO, "Bottle Rack", "Tokkuri at attention, waiting to be filled.",
            Base.NONE, 1, 0, "bottle_rack", null, null, Effect.NONE, Use.NONE, 1f, false),
    DRUNKARD_BARREL(Collection.CURIO, "Drunkard in a Barrel", "He meant to guard the stock. Right-click to change his pose.",
            Base.NONE, 1, 0, "drunkard_barrel", null, null, Effect.DOLL, Use.POSE, 1f, false),
    GREETER_DOLL(Collection.CURIO, "Greeter Doll", "The friendliest staff you will ever have. Right-click to change his pose.",
            Base.NONE, 1, 0, "greeter_doll", null, null, Effect.DOLL, Use.POSE, 1f, false),
    SWEEPER_DOLL(Collection.CURIO, "Sweeper Doll", "Still sweeping at closing time. Right-click to change his pose.",
            Base.NONE, 1, 0, "sweeper_doll", null, null, Effect.DOLL, Use.POSE, 1f, false),

    // ---------------------------------------------------------------- Blood Moon (0.6.0)
    /**
     * Coin of the crimson hour - a real relic (placeable, floor + wall), shop/event stock:
     * NOT craftable. A plain one comes from the Lucky crate; the four event crates roll a
     * Blessed one (gold veins, red glow) about one time in ten. Placing a token while the
     * blood moon stands above the horizon (see BloodMoon) blesses it on the spot.
     */
    BLOOD_TOKEN(Collection.HALLOWEEN, "Blood Token", "Minted under a crimson moon. The Guild keeps the dies - the night keeps the rest.",
            Base.NONE, 1, 0, "blood_token", "blood_token_wall", null, Effect.NONE, Use.NONE, 1f, false);

    enum Collection {
        SHADOW("Shoutlander's Shadow"), XMAS("Xmas Town"), NEW_YEAR("New Year"), EASTER("Easter"), PATRICK("St. Patrick's Day"),
        VALENTINE("Valentine's"), CURIO("Curiosities"), HALLOWEEN("Halloween Party"), ONSEN("Winter Onsen");

        final String title;

        Collection(String title) {
            this.title = title;
        }

        /** Short id for commands: shadow, xmas, new_year, easter, patrick, curio. */
        String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Collection byId(String id) {
            for (Collection c : values()) if (c.id().equals(id.toLowerCase(java.util.Locale.ROOT).replace("newyear", "new_year"))) return c;
            return null;
        }

        int size() {
            int n = 0;
            for (RelicType t : RelicType.values()) if (t.collection == this) n++;
            return n;
        }
    }

    /** SOLID = barrier (you bump into it); LIGHT = invisible light block; NONE = nothing (walk through). */
    enum Base { SOLID, LIGHT, NONE }

    enum Effect { NONE, SKULL, SWAY, FLAME, GRAVE, GLYPHS, BUBBLES, PULSE, CAGE, ASH, TOTEM, PORTAL, TWINKLE, ADVENT, STEAM, HEARTH, SNOW, TRAIN, CLOCK, HOP, HEART, RAINBOW, SWAP, SPARK, SHISHI, DOLL }

    /** What a right-click does (NONE = show the lore line). POSE = wooden dolls swap their pose model. */
    enum Use { NONE, SEAT, GIFT, CALENDAR, MUSIC, GLOBE, COOKIES, LAUNCH, CLOCK, BELL, CONFETTI, TOWER, EGG, GOLD, HARP, GRAMOPHONE, BONES, TELESCOPE, CANE, BOARD, POSE, WISH, JAR, BIRD, VEND, NOTICE, TANABATA }

    final Collection collection;
    final String displayName, lore;
    final Base base;
    final int height, light;
    final String floor, wall, ceiling;
    final Effect effect;
    final Use use;
    /** The model is drawn this much bigger (models can't be taller than two blocks; the tree is built small). */
    final float scale;
    /** Default for config "crafting.<id>": false = shop/event stock, no recipe is registered unless the config says otherwise. */
    final boolean craftableByDefault;

    RelicType(String displayName, String lore, Base base, int height, int light, String floor, String wall, String ceiling, Effect effect) {
        // 0.5.0: the Shoutlander's Shadow set became shop/event stock - one collection per soul,
        // via the shop's "Shadow of the Settlement" crate. config crafting.<id> can still flip any of them.
        this(Collection.SHADOW, displayName, lore, base, height, light, floor, wall, ceiling, effect, Use.NONE, 1f, false);
    }

    RelicType(Collection collection, String displayName, String lore, Base base, int height, int light, String floor, String wall,
              String ceiling, Effect effect, Use use, float scale) {
        this(collection, displayName, lore, base, height, light, floor, wall, ceiling, effect, use, scale, true);
    }

    RelicType(Collection collection, String displayName, String lore, Base base, int height, int light, String floor, String wall,
              String ceiling, Effect effect, Use use, float scale, boolean craftableByDefault) {
        this.collection = collection;
        this.displayName = displayName;
        this.lore = lore;
        this.base = base;
        this.height = height;
        this.light = light;
        this.floor = floor;
        this.wall = wall;
        this.ceiling = ceiling;
        this.effect = effect;
        this.use = use;
        this.scale = scale;
        this.craftableByDefault = craftableByDefault;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    Material baseMaterial() {
        return switch (base) {
            case SOLID -> Material.BARRIER;
            case LIGHT -> Material.LIGHT;
            case NONE -> Material.AIR;
        };
    }

    static RelicType byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
