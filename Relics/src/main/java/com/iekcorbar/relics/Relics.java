package com.iekcorbar.relics;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places, draws, saves and animates relics. A relic is a hidden base block
 * (barrier / light / nothing, one or two blocks tall) plus an ItemDisplay with
 * its model; relics you can walk through get an Interaction hitbox for clicks.
 * Saved as text lines in the chunk's data ("relics"); displays respawn with
 * the chunk. Effects run in one tick, only for relics with a player nearby.
 */
final class Relics implements Listener {
    static final class Relic {
        final World world;
        final int x, y, z;
        final RelicType type;
        final String mount; // floor / wall / ceiling
        final BlockFace facing;
        ItemDisplay display;
        Interaction hitbox;
        /** Countdown board: the three text lines and what they show now. */
        List<org.bukkit.entity.TextDisplay> texts;
        String[] last = new String[3];
        boolean lit, twinkle;
        int hits, count, tune, candles = -1;
        long lastHit, busyUntil, lastUseMs;
        float angle;
        /** Gift box: colour model, who wrapped it, what is inside and who it is locked for. */
        String variant = "";
        UUID owner;
        final List<ItemStack> gifts = new ArrayList<>();
        boolean locked;
        UUID recipient;

        boolean staffPlaced;   // placed (or claimed) by staff: players can never break it

        Relic(World world, int x, int y, int z, RelicType type, String mount, BlockFace facing) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.mount = mount;
            this.facing = facing;
        }

        String model() {
            String m = switch (mount) {
                case "wall" -> type.wall;
                case "ceiling" -> type.ceiling;
                default -> type.floor;
            };
            if (m == null) m = type.floor; // no model of its own for this face: the floor model, tilted by transform()
            if (type == RelicType.WARDING_TOTEM && lit) m = "warding_totem_lit";
            if (type == RelicType.ADVENT_WREATH) m = (mount.equals("wall") ? "advent_wreath_wall_" : "advent_wreath_") + Math.max(0, candles);
            if ((type == RelicType.GIFT_BOX || type == RelicType.EASTER_EGG || type == RelicType.GIANT_CANDY_CANE) && !variant.isEmpty()) m = variant;
            if (type == RelicType.COOKIE_PLATE) m = count > 3 ? "cookie_plate" : count > 0 ? "cookie_plate_3" : "cookie_plate_0";
            if (type == RelicType.CHAMPAGNE_TOWER) m = count > 6 ? "champagne_tower" : count > 0 ? "champagne_tower_half" : "champagne_tower_empty";
            if (((type.effect == RelicType.Effect.TWINKLE || type.effect == RelicType.Effect.SWAP || type.effect == RelicType.Effect.SHISHI) && twinkle)
                    || (type.use == RelicType.Use.POSE && twinkle)) m += "_b";
            return m;
        }

        Location center() {
            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }

        /** Music props keep the next tune in the saved count field, so it survives chunk reloads and restarts. */
        private static boolean remembersTune(RelicType t) {
            return t.use == RelicType.Use.HARP || t.use == RelicType.Use.BONES || t.use == RelicType.Use.MUSIC;
        }

        String line() {
            return type.name() + "|" + x + "|" + y + "|" + z + "|" + mount + "|" + facing.name() + "|" + (variant.isEmpty() ? "-" : variant)
                    + "|" + (remembersTune(type) ? tune : count) + "|" + (owner == null ? "-" : owner) + "|" + encodeGift()
                    + "|" + (staffPlaced ? 1 : 0);
        }

        /** The gift field of a saved line: "V2;<locked>;<recipient>;<base64 item>;<...>", or "-" when empty. */
        String encodeGift() {
            if (gifts.isEmpty() && !locked && recipient == null) return "-";
            StringBuilder sb = new StringBuilder("V2;").append(locked ? 1 : 0).append(';').append(recipient == null ? "-" : recipient).append(';');
            for (ItemStack it : gifts) sb.append(';').append(java.util.Base64.getEncoder().encodeToString(it.serializeAsBytes()));
            return sb.toString();
        }

        void readGift(String g) {
            gifts.clear();
            locked = false;
            recipient = null;
            if (g.equals("-") || g.isEmpty()) return;
            if (!g.startsWith("V2;")) {   // an old box with one wrapped present
                gifts.add(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(g)));
                return;
            }
            String[] p = g.split(";", 4);
            locked = p.length > 1 && p[1].equals("1");
            if (p.length > 2 && !p[2].equals("-")) recipient = UUID.fromString(p[2]);
            if (p.length > 3 && !p[3].isEmpty())
                for (String b : p[3].split(";")) gifts.add(ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(b)));
        }
    }

    private final RelicsPlugin plugin;
    final NamespacedKey itemKey;
    private final NamespacedKey dataKey, tagKey;
    /** world -> block key -> relic (every block a tall relic occupies points to it). */
    private final Map<UUID, Map<Long, Relic>> relics = new HashMap<>();
    private final Map<UUID, Relic> byHitbox = new HashMap<>();
    final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private int breakHits;
    private NewYearBoards boards;
    private Jukebox jukebox;
    private Gifts giftBoxes;

    Jukebox jukebox() {
        return jukebox;
    }

    Gifts giftBoxes() {
        return giftBoxes;
    }

    NewYearBoards boards() {
        return boards;
    }

    Relics(RelicsPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "item");
        dataKey = new NamespacedKey(plugin, "relics");
        tagKey = new NamespacedKey(plugin, "display");
        graveLoad();
        breakHits = Math.max(1, plugin.getConfig().getInt("break-hits", 3));
        boards = new NewYearBoards(plugin, this, tagKey);
        jukebox = new Jukebox(plugin, this);
        giftBoxes = new Gifts(plugin, this);
    }

    java.util.Set<Relic> all() {
        java.util.Set<Relic> out = new HashSet<>();
        for (Map<Long, Relic> m : relics.values()) out.addAll(m.values());
        return out;
    }

    List<Relic> launchersNear(Location at, double r) {
        List<Relic> out = new ArrayList<>();
        for (Relic l : all()) if (l.type == RelicType.FIREWORK_LAUNCHER && l.world == at.getWorld() && l.center().distanceSquared(at) < r * r) out.add(l);
        return out;
    }

    void save(Relic r) {
        markDirty(r);
    }

    void reloadBoards() {
        boards.load();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    Relic at(Block b) {
        Map<Long, Relic> m = relics.get(b.getWorld().getUID());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    int count() {
        Set<Relic> s = new HashSet<>();
        for (Map<Long, Relic> m : relics.values()) s.addAll(m.values());
        return s.size();
    }

    // ------------------------------------------------------------ items + recipes

    ItemStack item(RelicType t, int amount) {
        // the Creature Skull is a dragon head item so it can be worn (right-click in the air); placing it is cancelled in onInteract
        ItemStack it = new ItemStack(t == RelicType.CREATURE_SKULL ? Material.DRAGON_HEAD : Material.PAPER, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        boolean xmas = t.collection != RelicType.Collection.SHADOW;
        m.displayName(Component.text(t.displayName, xmas ? NamedTextColor.GOLD : NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(t.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.text((xmas ? "" : "Relic - ") + t.collection.title, xmas ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("relics", t.floor));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        return it;
    }

    RelicType typeOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        return RelicType.byId(it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    void registerRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
        recipe(RelicType.CREATURE_SKULL, 1, Material.BONE_BLOCK, Material.BONE, Material.BONE, Material.COAL);
        recipe(RelicType.CREATURE_RIBCAGE, 1, Material.BONE_BLOCK, Material.BONE, Material.BONE, Material.BONE, Material.BONE, Material.BONE, Material.BONE);
        recipe(RelicType.TORN_SETTLEMENT_BANNER, 1, Tag.ITEMS_BANNERS, Material.CHARCOAL, Material.STRING);
        recipe(RelicType.CHARRED_OUTPOST_LANTERN, 1, Material.LANTERN, Material.CHARCOAL);
        recipe(RelicType.VIGIL_CANDLES, 1, Tag.CANDLES, Tag.CANDLES, Tag.CANDLES, Material.BONE_MEAL);
        recipe(RelicType.SETTLERS_GRAVE, 1, Material.MOSSY_COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.IRON_NUGGET);
        recipe(RelicType.RUIN_MONOLITH, 1, Material.CRACKED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.GLOW_INK_SAC);
        recipe(RelicType.SPECIMEN_JAR, 1, Material.GLASS_BOTTLE, Material.SLIME_BALL, Material.SPIDER_EYE, Material.COPPER_INGOT);
        recipe(RelicType.CREATURE_EGG_SAC, 1, Material.SLIME_BALL, Material.SLIME_BALL, Material.FERMENTED_SPIDER_EYE, Material.BLACK_DYE);
        recipe(RelicType.GIBBET_CAGE, 1, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_CHAIN, Material.BONE);
        recipe(RelicType.ASH_PILE, 2, Material.CHARCOAL, Material.BONE_MEAL, Material.GRAVEL);
        recipe(RelicType.WARDING_TOTEM, 1, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT, Material.REDSTONE, Material.AMETHYST_SHARD);
        recipe(RelicType.SHATTERED_PORTAL_SHARD, 1, Material.OBSIDIAN, Material.AMETHYST_SHARD, Material.ENDER_PEARL);
        // Xmas Town
        recipe(RelicType.CHRISTMAS_TREE, 1, Material.SPRUCE_SAPLING, Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES,
                Material.GOLD_NUGGET, Material.GLOWSTONE_DUST);
        recipe(RelicType.STRING_LIGHTS, 4, Material.STRING, Material.STRING, Material.GLOWSTONE_DUST, Material.RED_DYE);
        recipe(RelicType.GIANT_CANDY_CANE, 1, Material.SUGAR, Material.SUGAR, Material.SUGAR, Material.SUGAR, Material.RED_DYE, Material.WHITE_DYE);
        recipe(RelicType.ADVENT_WREATH, 1, Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES, Tag.CANDLES, Tag.CANDLES, Tag.CANDLES, Tag.CANDLES);
        recipe(RelicType.STEAM_LANTERN, 1, Material.LANTERN, Material.COPPER_INGOT, Material.GOLD_NUGGET);
        recipe(RelicType.SNOW_LANTERN, 1, Material.STONE, Material.STONE, Material.SNOWBALL, Material.TORCH);
        recipe(RelicType.KADOMATSU, 1, Material.BAMBOO, Material.BAMBOO, Material.BAMBOO, Material.SPRUCE_LEAVES, Material.RED_DYE);
        recipe(RelicType.HEARTH, 1, Material.BRICKS, Material.BRICKS, Material.BRICKS, Material.CAMPFIRE, Material.SPRUCE_LEAVES);
        recipe(RelicType.SNOWMAN, 1, Material.SNOW_BLOCK, Material.SNOW_BLOCK, Material.SNOW_BLOCK, Material.COAL, Material.CARROT, Material.STICK);
        recipe(RelicType.SLEIGH, 1, Tag.PLANKS, Tag.PLANKS, Tag.PLANKS, Material.RED_DYE, Material.GOLD_INGOT, Material.IRON_INGOT, Material.IRON_INGOT);
        recipe(RelicType.TOY_TRAIN, 1, Material.IRON_NUGGET, Material.IRON_NUGGET, Material.IRON_NUGGET, Material.REDSTONE, Material.RED_DYE, Tag.PLANKS);
        // the Gift Box is deliberately NOT craftable: staff hands boxes out (/relics secret, /relics give <p> gift_box)
        recipe(RelicType.ADVENT_CALENDAR, 1, Tag.PLANKS, Tag.PLANKS, Material.PAPER, Material.PAPER, Material.RED_DYE, Material.GREEN_DYE);
        recipe(RelicType.MUSIC_BOX, 1, Tag.PLANKS, Material.NOTE_BLOCK, Material.GOLD_NUGGET, Material.PAPER);
        recipe(RelicType.SNOW_GLOBE, 1, Material.GLASS, Material.SNOWBALL, Tag.PLANKS, Material.GOLD_NUGGET);
        recipe(RelicType.MARKET_STALL, 1, Tag.PLANKS, Tag.PLANKS, Tag.PLANKS, Tag.PLANKS, Material.RED_WOOL, Material.WHITE_WOOL, Material.STICK, Material.STICK);
        RecipeChoice cookie = plugin.treats().exact(Treats.Treat.GINGERBREAD_COOKIE);
        recipe(RelicType.COOKIE_PLATE, 1, Material.CLAY_BALL, cookie, cookie, cookie, cookie, cookie, cookie);
        // New Year
        recipe(RelicType.FIREWORK_LAUNCHER, 1, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.GUNPOWDER, Material.GOLD_INGOT);
        recipe(RelicType.COUNTDOWN_BOARD, 1, Tag.PLANKS, Tag.PLANKS, Material.CLOCK, Material.CLOCK, Material.GOLD_INGOT, Material.GLOWSTONE_DUST, Material.BLACK_DYE);
        recipe(RelicType.COUNTDOWN_CLOCK, 1, Tag.PLANKS, Tag.PLANKS, Tag.PLANKS, Material.CLOCK, Material.GOLD_INGOT);
        recipe(RelicType.TEMPLE_BELL, 1, Material.BELL, Tag.LOGS, Tag.LOGS, Material.RED_DYE);
        recipe(RelicType.KAGAMI_MOCHI, 1, Material.SNOWBALL, Material.SNOWBALL, Material.SWEET_BERRIES, Tag.PLANKS);
        recipe(RelicType.SHIMENAWA, 2, Material.WHEAT, Material.WHEAT, Material.WHEAT, Material.PAPER);
        recipe(RelicType.RED_PAPER_LANTERN, 2, Material.PAPER, Material.PAPER, Material.RED_DYE, Material.TORCH);
        recipe(RelicType.CONFETTI_CANNON, 1, Material.PAPER, Material.GUNPOWDER, Material.RED_DYE, Material.LIGHT_BLUE_DYE, Material.PINK_DYE);
        recipe(RelicType.CHAMPAGNE_TOWER, 1, Material.GLASS, Material.GLASS, Material.GLASS, Material.GOLD_NUGGET);
        recipe(RelicType.NEW_YEAR_GARLAND, 2, Material.STRING, Material.GOLD_NUGGET, Material.GOLD_NUGGET, Material.PINK_DYE);
        // Easter
        recipe(RelicType.EASTER_EGG, 4, Material.EGG, Material.PINK_DYE, Material.LIGHT_BLUE_DYE, Material.YELLOW_DYE);
        recipe(RelicType.EGG_BASKET, 1, Material.WHEAT, Material.WHEAT, Material.WHEAT, Material.EGG, Material.EGG);
        recipe(RelicType.CLOCKWORK_BUNNY, 1, Material.GOLD_INGOT, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.RABBIT_HIDE);
        recipe(RelicType.BLOSSOM_VASE, 1, Material.FLOWER_POT, Material.CHERRY_LEAVES, Material.PINK_PETALS);
        recipe(RelicType.EGG_TREE, 1, Material.STICK, Material.STICK, Material.STICK, Material.EGG, Material.EGG, Material.PINK_DYE);
        // St. Patrick's Day
        recipe(RelicType.POT_OF_GOLD, 1, Material.CAULDRON, Material.GOLD_INGOT, Material.GOLD_INGOT);
        recipe(RelicType.RAINBOW_ARCH, 1, Material.RED_DYE, Material.ORANGE_DYE, Material.YELLOW_DYE, Material.LIME_DYE, Material.BLUE_DYE,
                Material.PURPLE_DYE, Material.WHITE_WOOL);
        recipe(RelicType.SHAMROCK_PLANTER, 1, Tag.PLANKS, Tag.PLANKS, Material.DIRT, Material.FERN, Material.FERN);
        recipe(RelicType.LEPRECHAUN_HAT, 1, Material.GREEN_WOOL, Material.GREEN_WOOL, Material.GOLD_NUGGET, Material.BLACK_DYE);
        recipe(RelicType.CELTIC_HARP, 1, Tag.PLANKS, Tag.PLANKS, Material.STRING, Material.STRING, Material.STRING, Material.GOLD_INGOT);
        recipe(RelicType.GREEN_ALE_KEG, 1, Material.BARREL, Material.GREEN_DYE, Material.IRON_INGOT);
        // Curiosities
        recipe(RelicType.BRASS_TELESCOPE, 1, Material.SPYGLASS, Material.GOLD_INGOT, Material.GOLD_INGOT);
        recipe(RelicType.STEAM_GAUGE_PANEL, 1, Material.CLOCK, Material.COPPER_INGOT, Material.COPPER_INGOT, Tag.PLANKS);
        recipe(RelicType.TESLA_COIL, 1, Material.COPPER_BLOCK, Material.LIGHTNING_ROD, Material.REDSTONE, Material.REDSTONE);
        recipe(RelicType.GRAMOPHONE, 1, Material.JUKEBOX, Material.GOLD_INGOT);
        recipe(RelicType.BONE_XYLOPHONE, 1, Material.BONE, Material.BONE, Material.BONE, Material.BONE, Material.BONE, Material.STICK, Material.STICK);
        recipe(RelicType.TORII_GATE, 1, Tag.LOGS, Tag.LOGS, Tag.LOGS, Tag.LOGS, Material.RED_DYE, Material.RED_DYE, Material.BLACK_DYE);
        recipe(RelicType.MANEKI_NEKO, 1, Material.CLAY_BALL, Material.CLAY_BALL, Material.CLAY_BALL, Material.GOLD_NUGGET, Material.RED_DYE);
        recipe(RelicType.SHISHI_ODOSHI, 1, Material.BAMBOO, Material.BAMBOO, Material.BAMBOO, Material.BAMBOO, Material.STONE, Material.WATER_BUCKET);
        recipe(RelicType.BONSAI, 1, Material.FLOWER_POT, Tag.SAPLINGS);
        recipe(RelicType.BYOBU_SCREEN, 1, Material.PAPER, Material.PAPER, Material.PAPER, Material.PAPER, Tag.PLANKS, Tag.PLANKS, Material.BLACK_DYE);
        recipe(RelicType.WAGASA_STAND, 1, Material.PAPER, Material.PAPER, Material.PAPER, Material.STICK, Material.STICK, Material.STICK, Material.RED_DYE);
        // Brewery & wooden helpers (Curiosities) - NOT craftable unless config crafting.<id> says true (shop/event stock)
        recipe(RelicType.SUGI_DAMA, 1, Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES, Material.HAY_BLOCK, Material.STRING);
        recipe(RelicType.KOJI_CROCK, 1, Material.CLAY_BALL, Material.CLAY_BALL, Material.CLAY_BALL, Material.HAY_BLOCK);
        recipe(RelicType.BOTTLING_CRATE, 1, Tag.PLANKS, Tag.PLANKS, Tag.PLANKS, Material.GLASS_BOTTLE, Material.GLASS_BOTTLE, Material.HAY_BLOCK);
        recipe(RelicType.BARREL_STACK, 1, Material.BARREL, Material.BARREL, Material.BARREL);
        recipe(RelicType.STEAM_PIPE, 1, Material.COPPER_INGOT, Material.COPPER_INGOT, Material.IRON_INGOT, Material.LEVER);
        recipe(RelicType.TASTING_COUNTER, 1, Tag.PLANKS, Tag.PLANKS, Tag.PLANKS, Material.GLASS, Material.GOLD_NUGGET);
        recipe(RelicType.NOREN, 1, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.BLUE_DYE, Material.BLUE_DYE, Material.STICK);
        recipe(RelicType.BREW_KETTLE, 1, Material.CAULDRON, Material.COPPER_INGOT, Material.COAL, Material.GOLD_NUGGET);
        recipe(RelicType.BOTTLE_RACK, 1, Tag.PLANKS, Tag.PLANKS, Material.GLASS_BOTTLE, Material.GLASS_BOTTLE, Material.GLASS_BOTTLE);
        recipe(RelicType.DRUNKARD_BARREL, 1, Material.BARREL, Material.STICK, Material.STICK, Material.GLASS_BOTTLE);
        recipe(RelicType.GREETER_DOLL, 1, Material.STICK, Material.STICK, Material.STICK, Tag.PLANKS, Material.PAPER);
        recipe(RelicType.SWEEPER_DOLL, 1, Material.STICK, Material.STICK, Material.STICK, Material.HAY_BLOCK, Tag.PLANKS);
        plugin.treats().registerRecipes(recipeKeys);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipes(recipeKeys);
    }

    @SuppressWarnings("unchecked")
    private void recipe(RelicType t, int amount, Object... ing) {
        if (!plugin.craftable(t)) return;   // shop/event stock: config crafting.<id> = false (or the type's default)
        NamespacedKey k = new NamespacedKey(plugin, t.id());
        Bukkit.removeRecipe(k, false);
        ShapelessRecipe r = new ShapelessRecipe(k, item(t, amount));
        for (Object o : ing) {
            if (o instanceof Material m) r.addIngredient(m);
            else if (o instanceof RecipeChoice c) r.addIngredient(c);
            else r.addIngredient(new RecipeChoice.MaterialChoice((Tag<Material>) o));
        }
        Bukkit.addRecipe(r);
        recipeKeys.add(k);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    /** Relic items (paper underneath) never count as paper in other recipes. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(plugin.namespace())) return;
        for (ItemStack i : e.getInventory().getMatrix()) if (typeOf(i) != null) e.getInventory().setResult(null);
    }

    // ------------------------------------------------------------ placing

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Relic hit = at(b);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && hit != null) {
            e.setCancelled(true);
            hit(hit, p);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (hit != null && typeOf(e.getItem()) == null) {
            e.setCancelled(true);
            use(hit, p, e.getItem());
            return;
        }
        RelicType t = typeOf(e.getItem());
        if (t == null) return;
        e.setCancelled(true);
        BlockFace face = e.getBlockFace();
        String mount = face == BlockFace.UP ? "floor" : face == BlockFace.DOWN ? "ceiling" : "wall";
        Block target = b.isReplaceable() && at(b) == null ? b : b.getRelative(face);
        if (mount.equals("floor") && !target.getRelative(BlockFace.DOWN).getType().isSolid()) {
            p.sendActionBar(Component.text("This relic needs solid ground.", NamedTextColor.GRAY));
            return;
        }
        BlockFace facing = mount.equals("wall") ? face : p.getFacing().getOppositeFace();
        place(p, e.getItem(), t, target, b, mount, facing);
    }

    private void place(Player p, ItemStack hand, RelicType t, Block target, Block against, String mount, BlockFace facing) {
        int h = mount.equals("floor") ? t.height : 1;
        for (int i = 0; i < h; i++) {
            Block part = target.getRelative(0, i, 0);
            if (!part.isReplaceable() || at(part) != null) return;
        }
        if (t.base == RelicType.Base.SOLID
                && !target.getWorld().getNearbyEntities(BoundingBox.of(target).expand(0, h - 1, 0).shift(0, (h - 1) / 2.0, 0), en -> en instanceof LivingEntity).isEmpty()) return;
        BlockState old = target.getState();
        setBase(target, t, h, t.light);
        BlockPlaceEvent ev = new BlockPlaceEvent(target, old, against, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            for (int i = h - 1; i >= 1; i--) target.getRelative(0, i, 0).setType(Material.AIR, false);
            old.update(true, false);
            return;
        }
        Relic r = new Relic(target.getWorld(), target.getX(), target.getY(), target.getZ(), t, mount, facing);
        r.staffPlaced = p.hasPermission("relics.admin"); // what staff places, players can never break
        if (t == RelicType.COOKIE_PLATE) r.count = 6;
        if (t == RelicType.CHAMPAGNE_TOWER) r.count = 14;
        if (t == RelicType.EASTER_EGG) {
            String[] eggs = {"easter_egg", "easter_egg_blue", "easter_egg_yellow"};
            r.variant = eggs[ThreadLocalRandom.current().nextInt(eggs.length)];
            r.owner = p.getUniqueId();
        }
        if (t == RelicType.COUNTDOWN_BOARD) r.owner = p.getUniqueId(); // the placer may change its zone
        if (t == RelicType.GIANT_CANDY_CANE) r.variant = CANE_COLOURS[ThreadLocalRandom.current().nextInt(CANE_COLOURS.length)];
        if (t == RelicType.ADVENT_WREATH) r.candles = adventCandles();
        index(r);
        draw(r);
        markDirty(r);
        plugin.adv().grant(p, "first");
        plugin.adv().grant(p, t.collection.id());
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        r.world.playSound(r.center(), "minecraft:block.bone_block.place", 0.8f, 0.7f);
        p.swingMainHand();
    }

    private static void setBase(Block b, RelicType t, int h, int light) {
        for (int i = 0; i < h; i++) {
            Block part = b.getRelative(0, i, 0);
            if (t.base == RelicType.Base.LIGHT && i == 0) {
                Light l = (Light) Material.LIGHT.createBlockData();
                l.setLevel(Math.max(1, Math.min(15, light)));
                part.setBlockData(l, false);
            } else if (t.base == RelicType.Base.SOLID) {
                part.setType(Material.BARRIER, false);
            }
        }
    }

    private int height(Relic r) {
        return r.mount.equals("floor") ? r.type.height : 1;
    }

    private void index(Relic r) {
        Map<Long, Relic> m = relics.computeIfAbsent(r.world.getUID(), k -> new HashMap<>());
        for (int i = 0; i < height(r); i++) m.put(key(r.x, r.y + i, r.z), r);
    }

    private void unindex(Relic r) {
        Map<Long, Relic> m = relics.get(r.world.getUID());
        if (m != null) for (int i = 0; i < height(r); i++) m.remove(key(r.x, r.y + i, r.z));
    }

    // ------------------------------------------------------------ drawing

    private static float yaw(BlockFace f) {
        return switch (f) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    /** True when the floor model is tilted onto a wall or flipped onto a ceiling (the relic has no model of its own for that face). */
    private static boolean tilted(Relic r) {
        return (r.mount.equals("wall") && r.type.wall == null) || (r.mount.equals("ceiling") && r.type.ceiling == null);
    }

    private Quaternionf rotation(Relic r) {
        // plus a half turn: an item display shows its model turned 180 degrees about Y (see Decor.transform)
        return rotationRaw(r).rotateY((float) Math.PI);
    }

    private Quaternionf rotationRaw(Relic r) {
        Quaternionf q = new Quaternionf();
        if (!tilted(r)) return q.rotateY((float) Math.toRadians(yaw(r.facing)));
        if (r.mount.equals("wall")) {
            // bottom of the model against the wall, model "up" pointing out into the room
            return q.rotateY((float) Math.toRadians(yaw(r.facing) + 180)).rotateX((float) Math.PI / 2);
        }
        return q.rotateY((float) Math.toRadians(yaw(r.facing))).rotateX((float) Math.PI);
    }

    /** Tilted tall props are shrunk to fit their single block. */
    private static float fit(Relic r) {
        return tilted(r) ? 1f / Math.max(1, r.type.height) : 1f;
    }

    /** Transform for rotation q: scaled (big relics, tilted tall ones), with the bottom kept on the ground / wall / ceiling. */
    private Transformation build(Relic r, Quaternionf q, float mul) {
        float ts = r.type.scale * fit(r) * mul;
        float s = ts * 1.001f;
        Vector3f up = new Vector3f(0, 1, 0).rotate(rotationRaw(r)).mul((ts - 1) * 0.5f);
        if (r.mount.equals("wall") && r.type.wall != null) {
            // own wall model (back at z = 16): the bigger model grows out into the room, the back stays on the wall
            up = new Vector3f(0, (ts - 1) * 0.5f, 0).add(new Vector3f(0, 0, -(ts - 1) * 0.5f).rotate(rotationRaw(r)));
        }
        return new Transformation(up, q, new Vector3f(s, s, s), new Quaternionf());
    }

    /** Model transform: turned to its facing (+ extra turn), scaled up for big relics with the bottom kept on the ground. */
    private Transformation transform(Relic r, float extraYaw) {
        return build(r, rotation(r).rotateY(extraYaw), 1f);
    }

    private ItemStack look(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("relics", model));
        return it;
    }

    /** Redraw the item on the display after the variant or contents changed (gift box wrapping). */
    void relook(Relic r) {
        if (r.display != null) r.display.setItemStack(look(r.model()));
    }

    void draw(Relic r) {
        erase(r);
        ItemStack look = new ItemStack(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key("relics", r.model()));
        r.display = r.world.spawn(r.center(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            d.setItemStack(look);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform(r, 0));
            if (r.type == RelicType.WARDING_TOTEM && r.lit) d.setBrightness(new Display.Brightness(15, 15));
            if (r.type.effect == RelicType.Effect.TWINKLE) d.setBrightness(new Display.Brightness(12, 15)); // lights glow
        });
        if (r.type.base != RelicType.Base.SOLID) {
            float w = r.type == RelicType.SETTLERS_GRAVE && r.mount.equals("floor") ? 2.2f : r.type == RelicType.CREATURE_RIBCAGE && r.mount.equals("floor") ? 2.7f : r.type == RelicType.COUNTDOWN_BOARD ? 2.6f : r.type == RelicType.TORII_GATE ? 3.0f : r.type == RelicType.BONE_XYLOPHONE && r.mount.equals("floor") ? 1.9f : r.type == RelicType.GRAMOPHONE && r.mount.equals("floor") ? 1.1f : r.type == RelicType.GIFT_BOX && r.mount.equals("floor") ? 1.2f : r.type == RelicType.CREATURE_RIBCAGE || r.type == RelicType.ASH_PILE || !r.mount.equals("floor") ? 0.9f : 0.7f;
            float hh = r.type == RelicType.SETTLERS_GRAVE && r.mount.equals("floor") ? 2.9f : r.type == RelicType.CREATURE_RIBCAGE && r.mount.equals("floor") ? 2.4f : r.type == RelicType.COUNTDOWN_BOARD && r.mount.equals("wall") ? 2.0f : r.type == RelicType.COUNTDOWN_BOARD ? 4.8f : !r.mount.equals("floor") ? 1.0f : switch (r.type) {
                case ASH_PILE, TORII_GATE -> 0.25f;
                case VIGIL_CANDLES -> 0.6f;
                case CREATURE_RIBCAGE, SETTLERS_GRAVE, TORN_SETTLEMENT_BANNER, GIBBET_CAGE -> 0.95f;
                case GRAMOPHONE -> 1.3f;
                case GIFT_BOX -> 1.2f;
                case BONE_XYLOPHONE -> 1.5f;
                default -> 0.7f;
            };
            r.hitbox = r.world.spawn(new Location(r.world, r.x + 0.5, r.y, r.z + 0.5), Interaction.class, i -> {
                i.setPersistent(false);
                i.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                i.setInteractionWidth(w);
                i.setInteractionHeight(hh);
                i.setResponsive(true);
            });
            byHitbox.put(r.hitbox.getUniqueId(), r);
        }
        if (r.type == RelicType.COUNTDOWN_BOARD) boards.spawn(r);
        if (r.type == RelicType.SETTLERS_GRAVE && r.mount.equals("floor")) graveSpawn(r);
    }

    private void erase(Relic r) {
        boards.remove(r);
        graveRemove(r);
        if (r.display != null) r.display.remove();
        if (r.hitbox != null) {
            byHitbox.remove(r.hitbox.getUniqueId());
            r.hitbox.remove();
        }
        r.display = null;
        r.hitbox = null;
    }

    // ------------------------------------------------------------ breaking

    private void hit(Relic r, Player p) {
        // staff-placed relics: players cannot break them at all, not even 3 hits deep
        if (r.staffPlaced && !p.hasPermission("relics.admin")) {
            p.sendActionBar(Component.text("This relic was placed by staff - only staff can remove it.", NamedTextColor.GRAY));
            return;
        }
        long now = plugin.tick;
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (now - r.lastHit > 20) r.hits = 0;
            r.lastHit = now;
            if (++r.hits < breakHits) {
                r.world.playSound(r.center(), "minecraft:block.bone_block.hit", 0.8f, 0.8f);
                return;
            }
        }
        /** The Gift Box is frozen: nobody breaks it outside the gift window, not even the recipient, staff aside. */
        if (r.type == RelicType.GIFT_BOX && r.locked && !p.hasPermission("relics.build")) {
            if (!giftBoxes.christmasNow() || !p.getUniqueId().equals(r.recipient)) {
                p.sendActionBar(Component.text("This box is locked for " + Gifts.name(r.recipient) + " - it opens " + giftBoxes.window() + ".", NamedTextColor.RED));
                return;
            }
        }
        if (!plugin.canBreak(p, r.world.getBlockAt(r.x, r.y, r.z))) return;
        breakRelic(r, p.getGameMode() != GameMode.CREATIVE);
    }

    /** Staff: removes every relic within {@code radius} blocks of the location (drops the items); returns how many. */
    int removeNear(Location at, double radius) {
        Set<Relic> found = new HashSet<>();
        Map<Long, Relic> m = relics.get(at.getWorld().getUID());
        if (m != null) for (Relic r : m.values()) if (r.center().distanceSquared(at) <= radius * radius) found.add(r);
        for (Relic r : found) breakRelic(r, true);
        return found.size();
    }

    /** Staff: all relics within radius of a location, closest first (each relic only once, however many blocks it fills). */
    List<Relic> near(Location at, double radius) {
        Set<Relic> found = new HashSet<>();
        Map<Long, Relic> m = relics.get(at.getWorld().getUID());
        if (m != null) for (Relic r : m.values()) if (r.center().distanceSquared(at) <= radius * radius) found.add(r);
        List<Relic> out = new ArrayList<>(found);
        out.sort(java.util.Comparator.comparingDouble(r -> r.center().distanceSquared(at)));
        return out;
    }

    /** Staff: the relic a player looks at - of the relics within reach, the one whose centre lies closest to the sight ray. */
    Relic lookingAt(Player p, double reach) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Relic best = null;
        double bestOff = Double.MAX_VALUE;
        for (Relic r : near(eye, reach)) {
            Vector v = r.center().toVector().subtract(eye.toVector());
            double along = v.dot(dir);
            if (along < 0.5) continue;                       // behind the player
            double off = Math.sqrt(Math.max(0, v.lengthSquared() - along * along));
            if (off <= 0.7 + along * 0.03 && off < bestOff) { // 0.7 blocks of slop on the ray, a little more for far props
                bestOff = off;
                best = r;
            }
        }
        return best;
    }

    void breakRelic(Relic r, boolean drop) {
        jukebox.stop(r);
        unindex(r);
        erase(r);
        markDirty(r);
        for (int i = 0; i < height(r); i++) {
            Block part = r.world.getBlockAt(r.x, r.y + i, r.z);
            if (part.getType() == Material.BARRIER || part.getType() == Material.LIGHT) part.setType(Material.AIR, true);
        }
        if (drop) r.world.dropItemNaturally(r.center(), item(r.type, 1));
        for (ItemStack it : new ArrayList<>(r.gifts)) r.world.dropItemNaturally(r.center(), it);
        for (UUID seat : new ArrayList<>(seats.keySet())) if (seats.get(seat) == r) {
            Entity en = Bukkit.getEntity(seat);
            if (en != null) {
                en.eject();
                en.remove();
            }
            seats.remove(seat);
        }
        r.world.playSound(r.center(), "minecraft:block.bone_block.break", 1f, 0.7f);
        r.world.spawnParticle(Particle.SMOKE, r.center(), 12, 0.3, 0.3, 0.3, 0.02);
    }

    // not ignoreCancelled: protection plugins may cancel attacks on entities, hit() asks them about the block itself
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Relic r = byHitbox.get(e.getAttacked().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        hit(r, e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent e) {
        Relic r = byHitbox.get(e.getRightClicked().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) use(r, e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof RelicsPlugin.ProbeBreakEvent) return;
        Relic r = at(e.getBlock());
        if (r != null) {
            e.setCancelled(true);
            if (r.staffPlaced && !e.getPlayer().hasPermission("relics.admin")) {
                e.getPlayer().sendActionBar(Component.text("This relic was placed by staff - only staff can remove it.", NamedTextColor.GRAY));
                return;
            }
            breakRelic(r, e.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        Relic above = at(e.getBlock().getRelative(BlockFace.UP));       // standing relic loses its ground
        if (above != null && above.mount.equals("floor") && above.y == e.getBlock().getY() + 1) {
            if (above.staffPlaced && !e.getPlayer().hasPermission("relics.admin")) {   // the relic just floats on
                e.getPlayer().sendActionBar(Component.text("The relic above keeps floating - it was placed by staff.", NamedTextColor.GRAY));
            } else breakRelic(above, true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (at(e.getBlockPlaced()) != null) e.setCancelled(true); // nothing may be built into a relic
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (at(e.getToBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> at(b) != null);
    }

    // ------------------------------------------------------------ saving

    private void markDirty(Relic r) {
        saveChunk(r.world, r.x >> 4, r.z >> 4); // relics change rarely: save right away
    }

    private void saveChunk(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx, cz)) return;
        Chunk c = w.getChunkAt(cx, cz);
        StringBuilder sb = new StringBuilder();
        Map<Long, Relic> m = relics.get(w.getUID());
        Set<Relic> done = new HashSet<>();
        if (m != null) {
            for (Relic r : m.values()) {
                if (r.x >> 4 != cx || r.z >> 4 != cz || !done.add(r)) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(r.line());
            }
        }
        if (sb.isEmpty()) c.getPersistentDataContainer().remove(dataKey);
        else c.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, sb.toString());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
    }

    void loadChunk(Chunk c) {
        String data = c.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return;
        for (String line : data.split("\n")) {
            try {
                String[] f = line.split("\\|");
                Relic r = new Relic(c.getWorld(), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        RelicType.valueOf(f[0]), f[4], BlockFace.valueOf(f[5]));
                if (f.length > 9) {
                    r.variant = f[6].equals("-") ? "" : f[6];
                    r.count = Integer.parseInt(f[7]);
                    if (Relic.remembersTune(r.type)) r.tune = Math.max(0, r.count);
                    if (r.type.use == RelicType.Use.POSE) r.twinkle = r.count == 1;   // the dolls keep their pose across reloads
                    r.owner = f[8].equals("-") ? null : UUID.fromString(f[8]);
                    r.readGift(f[9]);
                }
                if (f.length > 10) r.staffPlaced = f[10].equals("1");
                if (r.type == RelicType.ADVENT_WREATH) r.candles = adventCandles();
                if (at(r.world.getBlockAt(r.x, r.y, r.z)) != null) continue;
                index(r);
                draw(r);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad relic entry in chunk " + c.getX() + "," + c.getZ());
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk c = e.getChunk();
        Map<Long, Relic> m = relics.get(c.getWorld().getUID());
        if (m == null) return;
        Set<Relic> gone = new HashSet<>();
        for (Relic r : m.values()) if (r.x >> 4 == c.getX() && r.z >> 4 == c.getZ()) gone.add(r);
        for (Relic r : gone) {
            erase(r);
            unindex(r);
        }
    }

    void removeAllDisplays() {
        for (Map<Long, Relic> m : relics.values()) for (Relic r : new HashSet<>(m.values())) erase(r);
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities())
                if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) e.remove();
    }

    // ------------------------------------------------------------ effects (every 10 ticks)

    void tick(long tick) {
        jukebox.tick(tick);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Set<Relic> all = new HashSet<>();
        for (Map<Long, Relic> m : relics.values()) all.addAll(m.values());
        for (Relic r : all) {
            // a relic that lost its display or hitbox (entity unloaded/killed while the chunk stayed loaded) could not be
            // hit or removed any more: draw it again
            boolean lost = r.display == null || !r.display.isValid() || (r.hitbox != null && !r.hitbox.isValid())
                    || (r.type == RelicType.COUNTDOWN_BOARD && boards.lost(r))
                    || (r.type == RelicType.SETTLERS_GRAVE && r.mount.equals("floor") && graveLost(r));
            if (lost) {
                if (r.world.isChunkLoaded(r.x >> 4, r.z >> 4)) {
                    erase(r);
                    draw(r);
                }
                continue;
            }
            Location c = r.center();
            if (r.world.getNearbyPlayers(c, 24).isEmpty()) continue;
            long time = r.world.getTime();
            boolean night = time > 13000 && time < 23000;
            Location front = c.clone().add(r.facing.getDirection().multiply(0.45));
            switch (r.type.effect) {
                case DOLL -> {
                    // wooden helpers with a little personality: a clink, a knock, a sweep - on average once every few minutes
                    if (rnd.nextInt(120) == 0) switch (r.type) {
                        case DRUNKARD_BARREL -> {
                            r.world.playSound(c, "minecraft:block.glass.place", 0.4f, 0.7f + rnd.nextFloat() * 0.4f);
                            if (rnd.nextBoolean())
                                r.world.spawnParticle(Particle.HAPPY_VILLAGER, front.clone().add(0, 1.2, 0), 3, 0.15, 0.1, 0.15, 0);
                        }
                        case GREETER_DOLL -> r.world.playSound(c, "minecraft:block.wood.chime", 0.5f, 0.9f + rnd.nextFloat() * 0.3f);
                        case SWEEPER_DOLL -> r.world.playSound(c, "minecraft:block.grass.break", 0.45f, 1.05f);
                        default -> {
                        }
                    };
                }
                case SKULL -> {
                    if (night || rnd.nextInt(4) == 0)
                        r.world.spawnParticle(Particle.DUST, front.clone().add(0, -0.1, 0), 2, 0.15, 0.03, 0.03, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 30, 20), 0.5f));
                    if (night && rnd.nextInt(200) == 0) r.world.playSound(c, "relics:skull.growl", 0.6f, 0.8f);
                }
                case FLAME -> {
                    Block b = r.world.getBlockAt(r.x, r.y, r.z);
                    if (b.getBlockData() instanceof Light l) {
                        l.setLevel(Math.max(1, r.type.light - rnd.nextInt(4)));
                        b.setBlockData(l, false);
                    }
                    if (rnd.nextInt(3) == 0) r.world.spawnParticle(Particle.SMALL_FLAME, c.clone().add(0, r.type == RelicType.VIGIL_CANDLES ? -0.05 : -0.25, 0), 1, 0.12, 0.05, 0.12, 0);
                    if (rnd.nextInt(30) == 0) r.world.playSound(c, "relics:flame.crackle", 0.4f, 1f);
                }
                case SWAY, CAGE -> {
                    if (tick % 30 == 0) {
                        float a = (float) Math.toRadians((rnd.nextBoolean() ? 1 : -1) * (2 + rnd.nextDouble() * 3));
                        r.display.setInterpolationDelay(0);
                        r.display.setInterpolationDuration(30);
                        r.display.setTransformation(build(r, rotation(r).rotateZ(a), 1f));
                    }
                    if (r.type.effect == RelicType.Effect.CAGE && rnd.nextInt(40) == 0) r.world.playSound(c, "relics:cage.creak", 0.5f, 1f);
                }
                case GRAVE -> {
                    if (night && rnd.nextInt(8) == 0) r.world.spawnParticle(Particle.SOUL, c.clone().add(0, -0.3, -0.2), 1, 0.2, 0.1, 0.2, 0.01);
                    if (night && rnd.nextInt(40) == 0 && !r.world.getNearbyPlayers(c, 10).isEmpty())
                        r.world.playSound(c, "relics:grave.whisper", 0.7f, 0.9f + rnd.nextFloat() * 0.2f);
                }
                case GLYPHS -> {
                    int glow = 4 + (int) Math.round(5 + 5 * Math.sin(tick / 40.0));
                    r.display.setBrightness(new Display.Brightness(Math.min(15, glow), r.world.getBlockAt(r.x, r.y + 2, r.z).getLightFromSky()));
                    if (rnd.nextInt(20) == 0) r.world.spawnParticle(Particle.ENCHANT, front.clone().add(0, 0.6, 0), 4, 0.2, 0.5, 0.05, 0.3);
                }
                case BUBBLES -> {
                    if (rnd.nextInt(3) == 0) r.world.spawnParticle(Particle.BUBBLE_POP, c.clone().add(0, 0.1, 0), 1, 0.12, 0.2, 0.12, 0);
                }
                case PULSE -> {
                    if (tick % 20 == 0) {
                        float s = (tick / 20) % 2 == 0 ? 1.06f : 1.0f;
                        r.display.setInterpolationDelay(0);
                        r.display.setInterpolationDuration(16);
                        r.display.setTransformation(build(r, rotation(r), s));
                    }
                    if (tick % 60 == 0 && !r.world.getNearbyPlayers(c, 6).isEmpty()) r.world.playSound(c, "relics:egg_sac.heartbeat", 0.6f, 1f);
                    if (rnd.nextInt(80) == 0) r.world.playSound(c, "relics:egg_sac.squelch", 0.5f, 1f);
                }
                case ASH -> {
                    if (rnd.nextInt(2) == 0) r.world.spawnParticle(Particle.WHITE_ASH, c.clone().add(0, -0.3, 0), 2, 0.4, 0.1, 0.4, 0);
                    if (rnd.nextInt(10) == 0) r.world.spawnParticle(Particle.SMALL_FLAME, c.clone().add(0, -0.35, 0), 1, 0.3, 0, 0.3, 0);
                }
                case TOTEM -> {
                    boolean danger = !r.world.getNearbyEntities(c, 16, 8, 16, en -> en instanceof Monster).isEmpty();
                    if (danger != r.lit) {
                        r.lit = danger;
                        draw(r);
                        if (danger) r.world.playSound(c, "relics:totem.alarm", 0.8f, 1f);
                    }
                    if (danger && rnd.nextInt(3) == 0)
                        r.world.spawnParticle(Particle.DUST, front.clone().add(0, 1.1, 0), 2, 0.08, 0.08, 0.02, 0, new Particle.DustOptions(Color.RED, 0.7f));
                }
                case PORTAL -> {
                    r.world.spawnParticle(Particle.PORTAL, c.clone().add(0, 0.2, 0), 3, 0.3, 0.5, 0.3, 0.2);
                    if (rnd.nextInt(12) == 0 && !r.world.getNearbyPlayers(c, 8).isEmpty()) r.world.playSound(c, "relics:portal.hum", 0.5f, 1f);
                }
                case TWINKLE -> {
                    if (tick % 20 == 0) {
                        r.twinkle = !r.twinkle;
                        r.display.setItemStack(look(r.model()));
                    }
                    if (r.type == RelicType.CHRISTMAS_TREE && rnd.nextInt(6) == 0)
                        r.world.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1.2, 0), 1, 0.8, 1.0, 0.8, 0);
                }
                case ADVENT -> {
                    if (tick % 200 == 0) {
                        int n = adventCandles();
                        if (n != r.candles) {
                            r.candles = n;
                            r.display.setItemStack(look(r.model()));
                        }
                    }
                    Block b = r.world.getBlockAt(r.x, r.y, r.z);
                    if (b.getBlockData() instanceof Light l) {
                        l.setLevel(r.candles <= 0 ? 0 : Math.max(1, 3 + 3 * r.candles - rnd.nextInt(2)));
                        b.setBlockData(l, false);
                    }
                    if (r.candles > 0 && rnd.nextInt(4) == 0) r.world.spawnParticle(Particle.SMALL_FLAME, c.clone().add(0, -0.05, 0), 1, 0.25, 0, 0.25, 0);
                }
                case STEAM -> {
                    if (rnd.nextInt(20) == 0) r.world.spawnParticle(Particle.CLOUD, c.clone().add(0, 0.3, 0), 1, 0.05, 0.05, 0.05, 0.01);
                }
                case HEARTH -> {
                    if (rnd.nextInt(2) == 0) r.world.spawnParticle(Particle.FLAME, front.clone().add(0, -0.25, 0), 1, 0.15, 0.05, 0.05, 0.005);
                    if (tick % 40 == 0) r.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.clone().add(0, 1.2, 0), 1, 0.1, 0, 0.1, 0.02);
                    if (rnd.nextInt(25) == 0) r.world.playSound(c, "relics:hearth.crackle", 0.5f, 1f);
                }
                case SNOW -> {
                    if (rnd.nextInt(3) == 0) r.world.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1.2, 0), 1, 0.6, 0.6, 0.6, 0);
                }
                case HOP -> {
                    long phase = (tick / 10) % 3;
                    if (phase == 0 || phase == 1) {
                        r.display.setInterpolationDelay(0);
                        r.display.setInterpolationDuration(phase == 0 ? 4 : 6);
                        Transformation tr = transform(r, 0);
                        r.display.setTransformation(new Transformation(new Vector3f(0, phase == 0 ? 0.15f : 0f, 0), tr.getLeftRotation(), tr.getScale(), tr.getRightRotation()));
                    }
                }
                case RAINBOW -> {
                    if (rnd.nextInt(3) == 0) {
                        Color[] rb = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.BLUE, Color.PURPLE};
                        r.world.spawnParticle(Particle.DUST, c.clone().add(0, 0.7, 0), 1, 0.4, 0.3, 0.4, 0, new Particle.DustOptions(rb[rnd.nextInt(rb.length)], 0.8f));
                    }
                }
                case SWAP -> {
                    if (tick % 20 == 0) {
                        r.twinkle = !r.twinkle;
                        r.display.setItemStack(look(r.model()));
                    }
                }
                case SHISHI -> {
                    if (tick % 120 == 0) {
                        r.twinkle = true;
                        r.display.setItemStack(look(r.model()));
                        r.world.playSound(c, "relics:shishi_odoshi.clack", 0.7f, 1f);
                    } else if (r.twinkle && tick % 120 == 20) {
                        r.twinkle = false;
                        r.display.setItemStack(look(r.model()));
                    }
                }
                case SPARK -> {
                    Block b = r.world.getBlockAt(r.x, r.y, r.z);
                    if (b.getBlockData() instanceof Light l) {
                        l.setLevel(Math.max(1, r.type.light - rnd.nextInt(5)));
                        b.setBlockData(l, false);
                    }
                    if (rnd.nextInt(2) == 0) r.world.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 0.8, 0), 4, 0.2, 0.15, 0.2, 0.05);
                    if (rnd.nextInt(12) == 0) r.world.playSound(c, "relics:tesla.zap", 0.5f, 1f);
                    for (Player pl : r.world.getNearbyPlayers(c, 4)) { // joke: stand next to the coil holding an umbrella stand
                        if (typeOf(pl.getInventory().getItemInMainHand()) != RelicType.WAGASA_STAND && typeOf(pl.getInventory().getItemInOffHand()) != RelicType.WAGASA_STAND) continue;
                        r.world.spawnParticle(Particle.ELECTRIC_SPARK, pl.getEyeLocation().add(0, 0.5, 0), 12, 0.3, 0.2, 0.3, 0.1);
                        r.world.playSound(c, "relics:tesla.zap", 0.8f, 1.4f);
                        pl.sendActionBar(Component.text("Rain or shine.", NamedTextColor.AQUA));
                        plugin.adv().grant(pl, "umbrella");
                    }
                }
                case CLOCK -> {
                    if (tick % 20 == 0 && !r.world.getNearbyPlayers(c, 6).isEmpty()) r.world.playSound(c, "relics:clock.tick", 0.25f, 1f);
                }
                case TRAIN -> {
                    r.angle -= (float) Math.toRadians(30);
                    r.display.setInterpolationDelay(0);
                    r.display.setInterpolationDuration(10);
                    r.display.setTransformation(transform(r, r.angle));
                    if (rnd.nextInt(6) == 0) r.world.playSound(c, "relics:toy_train.chuff", 0.3f, 1.2f);
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------ Xmas Town: what a right-click does

    private static final String[] CANE_COLOURS = {"candy_cane_giant", "candy_cane_giant_green", "candy_cane_giant_blue", "candy_cane_giant_pink", "candy_cane_giant_gold"};

    private static String caneColour(Material dye) {
        if (dye == null) return null;
        return switch (dye) {
            case RED_DYE -> CANE_COLOURS[0];
            case GREEN_DYE, LIME_DYE -> CANE_COLOURS[1];
            case BLUE_DYE, LIGHT_BLUE_DYE, CYAN_DYE -> CANE_COLOURS[2];
            case PINK_DYE, MAGENTA_DYE -> CANE_COLOURS[3];
            case YELLOW_DYE, ORANGE_DYE -> CANE_COLOURS[4];
            default -> null;
        };
    }

    private final Map<UUID, Relic> seats = new HashMap<>();

    private final Map<UUID, long[]> jokeClicks = new HashMap<>(); // count, last click (ms)

    /** Gramophone x100 and maneki-neko x7: little jokes. */
    private void jokes(Relic r, Player p) {
        if (r.type != RelicType.GRAMOPHONE && r.type != RelicType.MANEKI_NEKO) return;
        long now = System.currentTimeMillis();
        long[] c = jokeClicks.computeIfAbsent(p.getUniqueId(), k -> new long[]{0, now});
        boolean gram = r.type == RelicType.GRAMOPHONE;
        if (now - c[1] > (gram ? 5 * 60_000L : 10_000L)) c[0] = 0;
        c[1] = now;
        if (++c[0] < (gram ? 100 : 7)) return;
        c[0] = 0;
        if (gram) {
            p.sendActionBar(Component.text("Skip... skip... skip...", NamedTextColor.GRAY));
            for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> r.world.playSound(r.center(), "minecraft:block.note_block.hat", 1f, 1.8f), i * 3L);
            plugin.adv().grant(p, "skip");
        } else {
            p.sendActionBar(Component.text("Fortune smiles on you.", NamedTextColor.GOLD));
            r.world.playSound(r.center(), "minecraft:entity.experience_orb.pickup", 1f, 1.5f);
            r.world.spawnParticle(Particle.WAX_ON, r.center().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.1);
            plugin.adv().grant(p, "lucky_paw");
        }
    }

    private void use(Relic r, Player p, ItemStack hand) {
        Achievements.used(plugin.adv(), p, r.type);
        jokes(r, p);
        switch (r.type.use) {
            case SEAT -> sit(r, p);
            case BOARD -> boards.use(r, p);
            case CANE -> {
                String col = caneColour(hand == null ? null : hand.getType());
                if (col == null) {
                    p.sendActionBar(Component.text(r.type.lore, NamedTextColor.GRAY));
                    return;
                }
                if (col.equals(r.variant)) return;
                r.variant = col;
                r.display.setItemStack(look(r.model()));
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                r.world.playSound(r.center(), "minecraft:item.dye.use", 0.8f, 1f);
                markDirty(r);
            }
            case GIFT -> giftBoxes.use(r, p);
            case POSE -> {
                r.twinkle = !r.twinkle;
                r.count = r.twinkle ? 1 : 0;   // persisted in the line's count column
                r.display.setItemStack(look(r.model()));
                r.world.playSound(r.center(), "minecraft:block.wood.place", 0.7f, r.twinkle ? 1.3f : 0.9f);
                markDirty(r);
            }
            case CALENDAR -> advent(p);
            case MUSIC, BONES, HARP, GRAMOPHONE -> jukebox.use(r, p);
            case GLOBE -> {
                if (plugin.tick < r.busyUntil) return;
                r.busyUntil = plugin.tick + 60;
                r.world.spawnParticle(Particle.SNOWFLAKE, r.center().add(0, 0.2, 0), 20, 0.2, 0.2, 0.2, 0.01);
                p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 2.5, 0), 80, 3, 1, 3, 0.01);
                r.world.playSound(r.center(), "relics:snow_globe.chime", 0.7f, 1f);
            }
            case COOKIES -> {
                Treats.Treat held = plugin.treats().typeOf(hand);
                if (held == Treats.Treat.GINGERBREAD_COOKIE && r.count < 6) {
                    int add = Math.min(6 - r.count, hand.getAmount());
                    if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - add);
                    r.count += add;
                } else if (r.count > 0) {
                    r.count--;
                    for (ItemStack left : p.getInventory().addItem(plugin.treats().item(Treats.Treat.GINGERBREAD_COOKIE, 1)).values())
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    r.world.playSound(r.center(), "minecraft:entity.item.pickup", 0.5f, 1.2f);
                } else {
                    p.sendActionBar(Component.text("The plate is empty - refill it with Gingerbread Cookies.", NamedTextColor.GRAY));
                    return;
                }
                r.display.setItemStack(look(r.model()));
                markDirty(r);
            }
            case LAUNCH -> {
                if (plugin.tick < r.busyUntil) {
                    p.sendActionBar(Component.text("Reloading...", NamedTextColor.GRAY));
                    return;
                }
                r.busyUntil = plugin.tick + 400;
                show(r, 5);
            }
            case CLOCK -> {
                java.time.LocalDateTime now = java.time.LocalDateTime.now(RelicsPlugin.zone);
                java.time.LocalDateTime ny = java.time.LocalDate.of(now.getYear() + 1, 1, 1).atStartOfDay();
                long secs = java.time.Duration.between(now, ny).getSeconds();
                String left = secs / 86400 > 0 ? (secs / 86400) + " days, " + (secs % 86400) / 3600 + " hours" : (secs / 3600) + "h " + (secs % 3600) / 60 + "m " + secs % 60 + "s";
                p.sendActionBar(Component.text(left + " until " + (now.getYear() + 1), NamedTextColor.GOLD));
                r.world.playSound(r.center(), "relics:clock.tick", 0.6f, 1f);
            }
            case BELL -> {
                if (plugin.tick < r.busyUntil) return;
                r.busyUntil = plugin.tick + 40;
                String year = String.valueOf(java.time.LocalDate.now(RelicsPlugin.zone).getYear());
                if (!year.equals(r.variant)) {  // the 108 strikes count per year (year kept in the saved variant field)
                    r.variant = year;
                    r.count = 0;
                }
                r.count = Math.min(108, r.count + 1);
                r.world.playSound(r.center(), "relics:temple_bell.toll", 2.5f, 1f);
                p.sendActionBar(Component.text("Strike " + r.count + " of 108", NamedTextColor.GOLD));
                if (r.count == 108) r.world.spawnParticle(Particle.END_ROD, r.center().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.05);
                markDirty(r);
            }
            case CONFETTI -> {
                if (plugin.tick < r.busyUntil) return;
                r.busyUntil = plugin.tick + 40;
                Location top = r.center().add(0, 0.4, 0);
                for (Color col : new Color[]{Color.fromRGB(240, 110, 170), Color.fromRGB(70, 200, 220), Color.fromRGB(230, 190, 60), Color.RED})
                    r.world.spawnParticle(Particle.DUST, top, 40, 0.6, 1.5, 0.6, 0, new Particle.DustOptions(col, 1.1f));
                r.world.spawnParticle(Particle.FIREWORK, top, 25, 0.2, 0.2, 0.2, 0.15);
                r.world.playSound(top, "relics:confetti.pop", 1f, 1f);
            }
            case TOWER -> {
                Treats.Treat held = plugin.treats().typeOf(hand);
                if (held == Treats.Treat.SPARKLING_CIDER && r.count < 14) {
                    int add = Math.min(14 - r.count, hand.getAmount());
                    if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - add);
                    r.count += add;
                } else if (r.count > 0) {
                    r.count--;
                    for (ItemStack left : p.getInventory().addItem(plugin.treats().item(Treats.Treat.SPARKLING_CIDER, 1)).values())
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    r.world.playSound(r.center(), "minecraft:block.amethyst_block.chime", 0.6f, 1.6f);
                } else {
                    p.sendActionBar(Component.text("All glasses are empty - refill the tower with Sparkling Cider.", NamedTextColor.GRAY));
                    return;
                }
                r.display.setItemStack(look(r.model()));
                markDirty(r);
            }
            case EGG -> {
                if (p.getUniqueId().equals(r.owner)) {
                    p.sendActionBar(Component.text("You hid this egg - let the others find it.", NamedTextColor.GRAY));
                    return;
                }
                NamespacedKey k = new NamespacedKey(plugin, "eggs_found");
                String found = p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.STRING, "");
                String id = r.world.getName() + ":" + r.x + ":" + r.y + ":" + r.z;
                if (("," + found + ",").contains("," + id + ",")) {
                    p.sendActionBar(Component.text("You already found this egg.", NamedTextColor.GRAY));
                    return;
                }
                found = found.isEmpty() ? id : found + "," + id;
                p.getPersistentDataContainer().set(k, PersistentDataType.STRING, found);
                List<String> pool = plugin.getConfig().getStringList("easter.egg-rewards");
                if (!pool.isEmpty()) {
                    ItemStack it = plugin.reward(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
                    if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
                int n = found.split(",").length;
                r.world.spawnParticle(Particle.HAPPY_VILLAGER, r.center(), 12, 0.3, 0.3, 0.3, 0);
                p.playSound(r.center(), "relics:egg.found", 1f, 1f);
                p.sendActionBar(Component.text("Egg found! (" + n + " so far)", NamedTextColor.LIGHT_PURPLE));
            }
            case GOLD -> {
                NamespacedKey k = new NamespacedKey(plugin, "gold_day");
                String today = java.time.LocalDate.now(RelicsPlugin.zone).toString();
                if (today.equals(p.getPersistentDataContainer().get(k, PersistentDataType.STRING))) {
                    p.sendActionBar(Component.text("The leprechaun says: come back tomorrow.", NamedTextColor.GREEN));
                    return;
                }
                p.getPersistentDataContainer().set(k, PersistentDataType.STRING, today);
                for (String spec : plugin.getConfig().getStringList("st-patricks.pot-of-gold")) {
                    ItemStack it = plugin.reward(spec);
                    if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
                r.world.playSound(r.center(), "relics:gold.jingle", 1f, 1f);
                r.world.spawnParticle(Particle.WAX_ON, r.center().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.1);
            }
            case TELESCOPE -> {
                // looking through it zooms in for a few seconds (slowness narrows the view)
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 120, 6, true, false, false));
                long time = r.world.getTime();
                String when = time < 12000 ? "the sun is up" : time < 13800 ? "the sun is setting" : time < 22200 ? "the stars are out" : "dawn is coming";
                p.sendActionBar(Component.text("You look towards the " + p.getFacing().name().toLowerCase() + " - " + when + ".", NamedTextColor.GOLD));
            }
            default -> p.sendActionBar(Component.text(r.type.lore, NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        }
    }

    /** A small fireworks show from a launcher: `count` rockets, a few ticks apart. */
    void show(Relic r, int count) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Color[] colours = {Color.RED, Color.YELLOW, Color.AQUA, Color.FUCHSIA, Color.ORANGE, Color.LIME, Color.WHITE};
        org.bukkit.FireworkEffect.Type[] types = org.bukkit.FireworkEffect.Type.values();
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (r.display == null) return;
                Location at = r.center().add(rnd.nextDouble() * 0.6 - 0.3, 0.6, rnd.nextDouble() * 0.6 - 0.3);
                r.world.spawn(at, org.bukkit.entity.Firework.class, fw -> {
                    org.bukkit.inventory.meta.FireworkMeta m = fw.getFireworkMeta();
                    m.addEffect(org.bukkit.FireworkEffect.builder().with(types[rnd.nextInt(types.length)])
                            .withColor(colours[rnd.nextInt(colours.length)], colours[rnd.nextInt(colours.length)])
                            .withFade(colours[rnd.nextInt(colours.length)]).flicker(rnd.nextBoolean()).trail(rnd.nextBoolean()).build());
                    m.setPower(1 + rnd.nextInt(2));
                    fw.setFireworkMeta(m);
                });
            }, i * 12L);
        }
    }

    // ------------------------------------------------------------ the New Year moment

    private int lastYear = java.time.LocalDate.now(RelicsPlugin.zone).getYear();

    /** Every second: the last ten seconds are counted down near clocks, and at midnight the clocks strike and the launchers fire. */
    void newYearTick() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(RelicsPlugin.zone);
        Set<Relic> fired = new HashSet<>();
        Set<Relic> clocks = new HashSet<>();
        for (Map<Long, Relic> m : relics.values()) for (Relic r : m.values()) if (r.type == RelicType.COUNTDOWN_CLOCK) clocks.add(r);
        if (now.getMonthValue() == 12 && now.getDayOfMonth() == 31 && now.getHour() == 23 && now.getMinute() == 59 && now.getSecond() >= 50) {
            int n = 60 - now.getSecond();
            for (Relic c : clocks)
                for (Player p : c.world.getNearbyPlayers(c.center(), 48))
                    p.showTitle(net.kyori.adventure.title.Title.title(Component.text(String.valueOf(n), NamedTextColor.GOLD), Component.empty(),
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(100))));
        }
        boolean newYear = now.getYear() != lastYear;
        if (newYear) lastYear = now.getYear();
        if (newYear) celebrateHome(now, clocks, fired);
        boards.tick(fired);
        graveTick();
    }


    // ------------------------------------------------------------ the grave remembers the last death

    private String lastDeathName;
    private long lastDeathAt;
    private java.io.File deathFile;

    /** Reads the last death from plugins/Relics/lastdeath.yml (called once when the plugin starts). */
    void graveLoad() {
        deathFile = new java.io.File(plugin.getDataFolder(), "lastdeath.yml");
        if (!deathFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(deathFile);
        lastDeathName = y.getString("name");
        lastDeathAt = y.getLong("at");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        lastDeathName = e.getEntity().getName();
        lastDeathAt = System.currentTimeMillis();
        if (deathFile == null) return;
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        y.set("name", lastDeathName);
        y.set("at", lastDeathAt);
        final String name = lastDeathName;
        final long at = lastDeathAt;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { y.save(deathFile); } catch (java.io.IOException ex) { plugin.getLogger().warning("Could not save lastdeath.yml: " + ex.getMessage() + " (" + name + " " + at + ")"); }
        });
    }

    private static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) return "just now";
        long m = s / 60;
        if (m < 60) return m + (m == 1 ? " minute ago" : " minutes ago");
        long h = m / 60;
        if (h < 48) return h + (h == 1 ? " hour ago" : " hours ago");
        long d = h / 24;
        return d + " days ago";
    }

    private void graveSpawn(Relic r) {
        graveRemove(r);
        r.texts = new ArrayList<>();
        org.bukkit.util.Vector dir = r.facing.getDirection();
        // the stone face sits 1/16 behind the block centre, drawn 3x bigger; the text goes just in front of it
        double off = -0.1875 + 0.04;
        float[] y = {1.62f, 1.30f, 0.98f};
        float[] scale = {0.42f, 0.8f, 0.5f};
        for (int i = 0; i < 3; i++) {
            Location at = new Location(r.world, r.x + 0.5 + dir.getX() * off, r.y + y[i], r.z + 0.5 + dir.getZ() * off, graveYaw(r.facing), 0f);
            final float s = scale[i];
            r.texts.add(r.world.spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                d.setSeeThrough(false);
                d.setLineWidth(1000);
                d.setViewRange(0.5f);
                d.text(Component.empty());
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            }));
        }
        r.last = new String[3];
    }

    private static float graveYaw(BlockFace f) {
        return switch (f) {
            case EAST -> -90;
            case SOUTH -> 0;
            case WEST -> 90;
            default -> 180;
        };
    }

    private void graveRemove(Relic r) {
        if (r.type != RelicType.SETTLERS_GRAVE) return;
        if (r.texts != null) for (org.bukkit.entity.TextDisplay t : r.texts) t.remove();
        r.texts = null;
    }

    private boolean graveLost(Relic r) {
        if (r.texts == null || r.texts.size() < 3) return true;
        for (org.bukkit.entity.TextDisplay t : r.texts) if (!t.isValid()) return true;
        return false;
    }

    /** Every second: rewrite the three lines on every grave where something changed (only graves with a player near). */
    private void graveTick() {
        String l1 = "HERE LIES";
        String l2 = lastDeathName == null ? "No one yet" : lastDeathName;
        String l3 = lastDeathName == null ? "" : "died " + ago(System.currentTimeMillis() - lastDeathAt);
        NamedTextColor c2 = lastDeathName == null ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE;
        for (Map<Long, Relic> m : relics.values()) for (Relic r : m.values()) {
            if (r.type != RelicType.SETTLERS_GRAVE || r.texts == null || r.texts.size() < 3 || graveLost(r)) continue;
            if (r.world.getNearbyPlayers(r.center(), 32).isEmpty()) continue;
            String[] now = {l1, l2, l3};
            for (int i = 0; i < 3; i++) {
                if (now[i].equals(r.last[i])) continue;
                r.last[i] = now[i];
                r.texts.get(i).text(Component.text(now[i], i == 0 ? NamedTextColor.GRAY : i == 1 ? c2 : NamedTextColor.GOLD));
            }
        }
    }

    private void celebrateHome(java.time.LocalDateTime now, Set<Relic> clocks, Set<Relic> fired) {
        for (Player p : Bukkit.getOnlinePlayers())
            p.showTitle(net.kyori.adventure.title.Title.title(Component.text("Happy New Year!", NamedTextColor.GOLD),
                    Component.text(String.valueOf(now.getYear()), NamedTextColor.YELLOW)));
        for (Relic c : clocks) {
            for (int i = 0; i < 12; i++)
                Bukkit.getScheduler().runTaskLater(plugin, () -> c.world.playSound(c.center(), "relics:clock.strike", 2f, 1f), i * 30L);
            for (Map<Long, Relic> m : relics.values())
                for (Relic r : new HashSet<>(m.values()))
                    if (r.type == RelicType.FIREWORK_LAUNCHER && r.world == c.world && r.center().distanceSquared(c.center()) < 64 * 64 && fired.add(r))
                        show(r, 12);
        }
    }

    private void sit(Relic r, Player p) {
        if (p.isInsideVehicle()) return;
        for (Relic s : seats.values()) if (s == r) return; // taken
        Location at = r.center().add(0, -0.15, 0);
        at.setYaw(switch (r.facing) { case EAST -> -90; case SOUTH -> 0; case WEST -> 90; default -> 180; } + 180);
        ItemDisplay seat = r.world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        });
        seat.addPassenger(p);
        seats.put(seat.getUniqueId(), r);
        r.world.playSound(at, "relics:sleigh.bells", 0.7f, 1f);
    }

    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent e) {
        if (seats.remove(e.getDismounted().getUniqueId()) == null) return;
        Entity seat = e.getDismounted();
        Bukkit.getScheduler().runTask(plugin, seat::remove);
        if (e.getEntity() instanceof Player p) {
            Location up = seat.getLocation().add(0, 0.8, 0);
            up.setYaw(p.getLocation().getYaw());
            up.setPitch(p.getLocation().getPitch());
            Bukkit.getScheduler().runTask(plugin, () -> p.teleport(up));
        }
    }

    // ------------------------------------------------------------ advent

    /** Candles on the advent wreath: one per Advent Sunday reached (all four until Epiphany), else none. */
    static int adventCandles() {
        java.time.LocalDate today = java.time.LocalDate.now(RelicsPlugin.zone);
        int year = today.getMonthValue() == 1 ? today.getYear() - 1 : today.getYear();
        java.time.LocalDate christmas = java.time.LocalDate.of(year, 12, 25);
        java.time.LocalDate fourth = christmas.minusDays(christmas.getDayOfWeek().getValue() % 7 == 0 ? 7 : christmas.getDayOfWeek().getValue() % 7);
        java.time.LocalDate first = fourth.minusWeeks(3);
        java.time.LocalDate end = java.time.LocalDate.of(year + 1, 1, 6);
        if (today.isBefore(first) || today.isAfter(end)) return 0;
        return (int) Math.min(4, 1 + java.time.temporal.ChronoUnit.DAYS.between(first, today) / 7);
    }

    private void advent(Player p) {
        java.time.LocalDate today = java.time.LocalDate.now(RelicsPlugin.zone);
        if (today.getMonthValue() != 12 || today.getDayOfMonth() > 25) {
            p.sendActionBar(Component.text("The calendar opens on 1 December.", NamedTextColor.GRAY));
            return;
        }
        int day = today.getDayOfMonth();
        NamespacedKey k = new NamespacedKey(plugin, "advent_" + today.getYear());
        long opened = p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.LONG, 0L);
        if ((opened & (1L << day)) != 0) {
            p.sendActionBar(Component.text("You opened door " + day + " already - come back tomorrow!", NamedTextColor.GRAY));
            return;
        }
        p.getPersistentDataContainer().set(k, PersistentDataType.LONG, opened | (1L << day));
        List<String> rewards = plugin.getConfig().getStringList("advent-calendar.rewards." + day);
        for (String spec : rewards) {
            ItemStack it = plugin.reward(spec);
            if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.playSound(p.getLocation(), "relics:advent.open", 1f, 1f);
        p.sendMessage(Component.text(day == 25 ? "Merry Christmas! The last door is open." : "Door " + day + " is open - see you tomorrow!", NamedTextColor.GOLD));
    }
}
