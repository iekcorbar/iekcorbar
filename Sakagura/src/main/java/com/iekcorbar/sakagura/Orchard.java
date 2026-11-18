package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real orchard trees (0.8.0): what you plant grows into an actual tree of real log and leaf
 * blocks, pasted from the per-species shapes in trees.yml (tools/make_tree_structures.py).
 * Chopping is vanilla (an axe, real drops); the plugin only owns the pasted cells, the
 * growth stages and the fruit. Ripe fruit hangs under the canopy as a small ItemDisplay
 * with an Interaction to click; picking one drops the produce and that slot ripens again
 * on the shared cycle. Trees live as chunk-PDC lines "tid|x|y|z|stage|r1,r2" - only the
 * fruit entities and the record are the plugin's; the tree itself is the world.
 * The 0.5.0 display-stack trees (Trees.java) keep working for plantings made before this
 * update; new plantings always come here.
 */
final class Orchard implements Listener {
    private static final int STAGES = 3;

    /** One growth stage: the cells to paste, relative to the trunk base at (0,0,0). */
    private static final class Shape {
        final List<int[]> cells = new ArrayList<>();
        final List<Material> mats = new ArrayList<>();
        final List<int[]> fruit = new ArrayList<>();   // mature stage only: leaf cells that can hold fruit
        Material trunk;                                 // the (0,0,0) cell's material
        int maxY;
    }

    private static final class Form {
        final Shape[] stages = new Shape[STAGES];
    }

    private static final class Rec {
        Tree type;
        World world;
        int x, y, z;
        int stage;
        final Set<Integer> ripe = new HashSet<>();
        final Map<Integer, ItemDisplay> displays = new HashMap<>();
        final Map<Integer, Interaction> hits = new HashMap<>();

        Rec(Tree type, World w, int x, int y, int z, int stage) {
            this.type = type;
            this.world = w;
            this.x = x;
            this.y = y;
            this.z = z;
            this.stage = stage;
        }

        String line() {
            StringBuilder r = new StringBuilder();
            for (int s : ripe) r.append(r.isEmpty() ? "" : ",").append(s);
            return type.id() + "|" + x + "|" + y + "|" + z + "|" + stage + "|" + (r.isEmpty() ? "-" : r);
        }
    }

    private final Sakagura plugin;
    private final NamespacedKey dataKey, fruitKey, hitKey;
    private final Map<String, Form> forms = new HashMap<>();
    private final Map<UUID, Map<Long, Rec>> trees = new HashMap<>();
    private final Map<Long, Rec> cellOwner = new HashMap<>();   // every pasted cell -> its tree
    private final Map<UUID, Rec> byHitbox = new HashMap<>();    // fruit Interaction -> tree
    private final Map<UUID, Integer> slotByHitbox = new HashMap<>();
    private final double stageChance, fruitChance;
    private long tick;

    Orchard(Sakagura plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "orchard");
        fruitKey = new NamespacedKey(plugin, "orchard_fruit");
        hitKey = new NamespacedKey(plugin, "orchard_fruit_hit");
        stageChance = 1 / (60.0 * Math.max(1, plugin.getConfig().getInt("trees.minutes-per-stage", 20)));
        fruitChance = 1 / (60.0 * Math.max(1, plugin.getConfig().getInt("trees.fruit-minutes", 20)));
        load();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    // ------------------------------------------------------------ shapes (trees.yml)

    private void load() {
        var in = plugin.getResource("trees.yml");
        if (in == null) {
            plugin.getLogger().warning("trees.yml missing from the jar - real orchard trees are off.");
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (Tree t : Tree.values()) {
            Form f = new Form();
            for (int s = 0; s < STAGES; s++) {
                Shape sh = new Shape();
                for (Object row : yml.getList(t.id() + ".stage" + s, List.of())) {
                    if (!(row instanceof List<?> c) || c.size() < 4) continue;
                    Material m = Material.matchMaterial(String.valueOf(c.get(3)));
                    if (m == null) {
                        plugin.getLogger().warning("trees.yml: unknown block " + c.get(3) + " in " + t.id());
                        continue;
                    }
                    int[] d = new int[]{((Number) c.get(0)).intValue(), ((Number) c.get(1)).intValue(), ((Number) c.get(2)).intValue()};
                    sh.cells.add(d);
                    sh.mats.add(m);
                    if (d[0] == 0 && d[1] == 0 && d[2] == 0) sh.trunk = m;
                    sh.maxY = Math.max(sh.maxY, d[1]);
                }
                f.stages[s] = sh;
            }
            Shape top = f.stages[STAGES - 1];
            for (Object row : yml.getList(t.id() + ".fruit", List.of())) {
                if (row instanceof List<?> c && c.size() >= 3)
                    top.fruit.add(new int[]{((Number) c.get(0)).intValue(), ((Number) c.get(1)).intValue(), ((Number) c.get(2)).intValue()});
            }
            if (top.cells.isEmpty() || f.stages[0].cells.isEmpty() || f.stages[0].trunk == null || top.trunk == null) {
                plugin.getLogger().warning("trees.yml: incomplete shape for " + t.id());
                continue;
            }
            forms.put(t.id(), f);
        }
        plugin.getLogger().info("Orchard: " + forms.size() + " real tree shapes loaded.");
    }

    Form form(Tree t) {
        return forms.get(t.id());
    }

    // ------------------------------------------------------------ planting + bonemeal

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block soil = e.getClickedBlock();
        if (soil == null) return;
        Player p = e.getPlayer();

        // 1. bone meal on any cell of our tree advances it one stage (like vanilla saplings)
        Rec mine = recAt(soil);
        if (mine != null) {
            ItemStack hand = e.getItem();
            if (hand != null && hand.getType() == Material.BONE_MEAL && mine.stage < STAGES - 1) {
                e.setCancelled(true);
                Form f = form(mine.type);
                Block base = mine.world.getBlockAt(mine.x, mine.y, mine.z);
                if (fits(base, f.stages[mine.stage + 1], mine)) {
                    eraseFruit(mine);
                    clearCells(mine);
                    mine.stage++;
                    paste(base, f.stages[mine.stage], mine);
                    saveChunk(mine.world, mine.x >> 4, mine.z >> 4);
                    if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                    mine.world.spawnParticle(Particle.HAPPY_VILLAGER, new Location(mine.world, mine.x + 0.5, soil.getY() + 1.2, mine.z + 0.5), 8, 0.3, 0.3, 0.3);
                    mine.world.playSound(new Location(mine.world, mine.x + 0.5, mine.y + 1, mine.z + 0.5), "minecraft:item.bone_meal.use", 1f, 1f);
                    p.swingMainHand();
                } else {
                    p.sendActionBar(Component.text("Not enough room to grow.", NamedTextColor.RED));
                }
            }
            return;
        }

        // 2. a sapling item on soil plants a real tree
        Tree t = Tree.bySapling(plugin.goods().idOf(e.getItem()));
        if (t == null || !isSoil(soil.getType())) return;
        Form f = form(t);
        if (f == null) return;
        e.setCancelled(true);
        Block b = soil.getRelative(BlockFace.UP);
        if (!b.getType().isAir() && !replaceable(b.getType())) {
            p.sendActionBar(Component.text("There is no room to plant here.", NamedTextColor.RED));
            return;
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (cellOwner.containsKey(key(b.getX() + dx, b.getY(), b.getZ() + dz))
                    || plugin.trees().at(b.getRelative(dx, 0, dz)) != null) {
                p.sendActionBar(Component.text("Too close to another tree.", NamedTextColor.RED));
                return;
            }
        }
        if (!fits(b, f.stages[0], null)) {
            p.sendActionBar(Component.text("The tree needs clear air above it.", NamedTextColor.RED));
            return;
        }
        var old = b.getState();
        b.setType(f.stages[0].trunk, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(b, old, soil, e.getItem(), p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            b.setType(Material.AIR, false);
            old.update(true, false);
            return;
        }
        Rec rec = new Rec(t, b.getWorld(), b.getX(), b.getY(), b.getZ(), 0);
        trees.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>()).put(key(rec.x, rec.y, rec.z), rec);
        paste(b, f.stages[0], rec);
        saveChunk(b.getWorld(), rec.x >> 4, rec.z >> 4);
        plugin.adv().grant(p, "sapling");
        if (p.getGameMode() != GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
        b.getWorld().playSound(b.getLocation(), "minecraft:block.sapling.place", 1f, 0.9f);
        p.swingMainHand();
    }

    /** What a tree may grow through: the plants vanilla tree growers replace (grass, ferns, flowers, ...). */
    private static boolean replaceable(Material m) {
        return Tag.REPLACEABLE_BY_TREES.isTagged(m) || m == Material.SNOW;
    }

    private static boolean isSoil(Material m) {
        return m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.PODZOL
                || m == Material.MOSS_BLOCK || m == Material.COARSE_DIRT || m == Material.DIRT_PATH;
    }

    /** Every target cell must be air, replaceable plants, or this tree's own cell. */
    private boolean fits(Block base, Shape s, Rec self) {
        World w = base.getWorld();
        for (int i = 0; i < s.cells.size(); i++) {
            int[] d = s.cells.get(i);
            Block at = w.getBlockAt(base.getX() + d[0], base.getY() + d[1], base.getZ() + d[2]);
            long k = key(at.getX(), at.getY(), at.getZ());
            Rec owner = cellOwner.get(k);
            if (owner != null && owner == self) continue;               // our own cells are being replaced
            if (owner != null) return false;                            // someone else's tree stands here
            Material want = s.mats.get(i);
            if (at.getType() == want || at.getType().isAir() || replaceable(at.getType())) continue;
            return false;
        }
        return true;
    }

    private void paste(Block base, Shape s, Rec rec) {
        World w = base.getWorld();
        for (int i = 0; i < s.cells.size(); i++) {
            int[] d = s.cells.get(i);
            Block at = w.getBlockAt(base.getX() + d[0], base.getY() + d[1], base.getZ() + d[2]);
            at.setType(s.mats.get(i), false);
            cellOwner.put(key(at.getX(), at.getY(), at.getZ()), rec);
        }
    }

    private void clearCells(Rec r) {
        Form f = form(r.type);
        if (f == null) return;
        Shape s = f.stages[r.stage];
        for (int i = 0; i < s.cells.size(); i++) {
            int[] d = s.cells.get(i);
            Block at = r.world.getBlockAt(r.x + d[0], r.y + d[1], r.z + d[2]);
            long k = key(at.getX(), at.getY(), at.getZ());
            if (cellOwner.get(k) == r) cellOwner.remove(k);
            if (at.getType() == s.mats.get(i)) at.setType(Material.AIR, false);
        }
    }

    Rec recAt(Block b) {
        return cellOwner.get(key(b.getX(), b.getY(), b.getZ()));
    }

    // ------------------------------------------------------------ fruit entities

    private void showFruit(Rec r, int slot) {
        Form f = form(r.type);
        Fruit fr = r.type.fruit();
        if (f == null || fr == null) return;
        Shape s = f.stages[STAGES - 1];
        if (slot < 0 || slot >= s.fruit.size()) return;
        int[] d = s.fruit.get(slot);
        Block at = r.world.getBlockAt(r.x + d[0], r.y + d[1], r.z + d[2]);
        Location loc = new Location(r.world, r.x + d[0] + 0.5, r.y + d[1] + 0.25, r.z + d[2] + 0.5);
        ItemStack look = new ItemStack(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "block/fruit_" + fr.id()));
        ItemDisplay disp = r.world.spawn(loc, ItemDisplay.class, it -> {
            it.setPersistent(false);
            it.getPersistentDataContainer().set(fruitKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemStack(look);
            it.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            it.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(slot * 1.7f), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
            it.setBrightness(new Display.Brightness(Math.max(at.getLightFromBlocks(), 4), at.getLightFromSky()));
        });
        Interaction hit = r.world.spawn(new Location(r.world, r.x + d[0] + 0.5, r.y + d[1] - 0.1, r.z + d[2] + 0.5), Interaction.class, i -> {
            i.setPersistent(false);
            i.getPersistentDataContainer().set(hitKey, PersistentDataType.BYTE, (byte) 1);
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(0.8f);
            i.setResponsive(true);
        });
        r.displays.put(slot, disp);
        r.hits.put(slot, hit);
        byHitbox.put(hit.getUniqueId(), r);
        slotByHitbox.put(hit.getUniqueId(), slot);
        r.ripe.add(slot);
    }

    private void hideFruit(Rec r, int slot) {
        ItemDisplay d = r.displays.remove(slot);
        if (d != null) d.remove();
        Interaction i = r.hits.remove(slot);
        if (i != null) {
            byHitbox.remove(i.getUniqueId());
            slotByHitbox.remove(i.getUniqueId());
            i.remove();
        }
        r.ripe.remove(slot);
    }

    private void eraseFruit(Rec r) {
        for (int slot : new ArrayList<>(r.ripe)) hideFruit(r, slot);
    }

    // ------------------------------------------------------------ picking

    @EventHandler(ignoreCancelled = true)
    public void onPick(PlayerInteractEntityEvent e) {
        Rec r = byHitbox.get(e.getRightClicked().getUniqueId());
        if (r == null || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        int slot = slotByHitbox.getOrDefault(e.getRightClicked().getUniqueId(), -1);
        Player p = e.getPlayer();
        if (!r.world.isChunkLoaded(r.x >> 4, r.z >> 4) || !r.ripe.contains(slot)) return;
        Fruit f = r.type.fruit();
        if (f == null) return;
        if (!plugin.canBreak(p, r.world.getBlockAt(r.x, r.y, r.z))) return;
        Form form = form(r.type);
        int[] d = form.stages[STAGES - 1].fruit.get(slot);
        Location at = new Location(r.world, r.x + d[0] + 0.5, r.y + d[1] + 0.3, r.z + d[2] + 0.5);
        r.world.dropItemNaturally(at, plugin.goods().item(f.produce, 1));
        // Seeds from the canopy (owner 18-11): picked fruit rolls the same one-in-three
        // chance as a garden harvest, so ripe orchard trees are a real seed source too.
        if (f.seed != null && java.util.concurrent.ThreadLocalRandom.current().nextInt(3) == 0)
            r.world.dropItemNaturally(at, plugin.goods().item(f.seed, 1));
        hideFruit(r, slot);
        saveChunk(r.world, r.x >> 4, r.z >> 4);
        plugin.adv().grant(p, "tree_harvest");
        r.world.playSound(at, "minecraft:block.sweet_berry_bush.pick_berries", 1f, 1f);
        r.world.spawnParticle(Particle.HAPPY_VILLAGER, at, 3, 0.2, 0.15, 0.2);
        p.swingMainHand();
    }

    /** Right-click the trunk or leaves: a short status (the fruit itself is clicked where it hangs). */
    @EventHandler(priority = EventPriority.LOW)
    public void onTrunkInfo(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Rec r = recAt(e.getClickedBlock());
        if (r == null) return;
        ItemStack hand = e.getItem();
        if (hand != null && (hand.getType() == Material.BONE_MEAL || Tree.bySapling(plugin.goods().idOf(hand)) != null)) return;
        if (r.stage < STAGES - 1) {
            e.getPlayer().sendActionBar(Component.text("A young " + r.type.displayName.toLowerCase(java.util.Locale.ROOT)
                    + " tree - bone meal speeds it up.", NamedTextColor.GRAY));
            return;
        }
        if (r.type.fruit() == null) {
            e.getPlayer().sendActionBar(Component.text(r.type.displayName + " - decoration only, enjoy the colours.", NamedTextColor.GRAY));
            return;
        }
        if (r.ripe.isEmpty()) e.getPlayer().sendActionBar(Component.text("The fruit on the " + r.type.displayName.toLowerCase(java.util.Locale.ROOT)
                + " tree is still ripening.", NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------ chopping (vanilla; we just tidy the record)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof Sakagura.ProbeBreakEvent) return;
        Block b = e.getBlock();
        Rec r = cellOwner.get(key(b.getX(), b.getY(), b.getZ()));
        if (r == null) return;
        cellOwner.remove(key(b.getX(), b.getY(), b.getZ()));
        if (b.getX() == r.x && b.getY() == r.y && b.getZ() == r.z) {
            fell(r, e.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        // a broken fruit support takes its fruit with it
        Form f = form(r.type);
        for (int slot = 0; slot < f.stages[STAGES - 1].fruit.size(); slot++) {
            int[] d = f.stages[STAGES - 1].fruit.get(slot);
            if (r.x + d[0] == b.getX() && r.y + d[1] == b.getY() && r.z + d[2] == b.getZ()) hideFruit(r, slot);
        }
        saveChunk(r.world, r.x >> 4, r.z >> 4);
    }

    /** Leaves of our trees do not drop vanilla saplings or apples - fruit hangs where you can pick it. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Rec r = cellOwner.get(key(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()));
        if (r == null) return;
        e.getItems().removeIf(it -> {
            Material m = it.getItemStack().getType();
            return m.name().endsWith("_SAPLING") || m == Material.APPLE;
        });
    }

    /** Leaf decay (after the trunk was chopped) removes the fruit that hung there. */
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        Rec r = cellOwner.get(key(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()));
        if (r == null) return;
        Form f = form(r.type);
        for (int slot = 0; slot < f.stages[STAGES - 1].fruit.size(); slot++) {
            int[] d = f.stages[STAGES - 1].fruit.get(slot);
            if (r.x + d[0] == e.getBlock().getX() && r.y + d[1] == e.getBlock().getY() && r.z + d[2] == e.getBlock().getZ()) {
                hideFruit(r, slot);
                saveChunk(r.world, r.x >> 4, r.z >> 4);
            }
        }
    }

    /** Chopping the trunk block ends the tree: fruit drops, the sapling comes back, the rest rots like vanilla. */
    private void fell(Rec r, boolean drop) {
        Fruit f = r.type.fruit();
        Form form = form(r.type);
        Location at = new Location(r.world, r.x + 0.5, r.y + 0.5, r.z + 0.5);
        for (int slot : new ArrayList<>(r.ripe)) {
            if (f != null && drop && slot < form.stages[STAGES - 1].fruit.size()) {
                int[] d = form.stages[STAGES - 1].fruit.get(slot);
                r.world.dropItemNaturally(new Location(r.world, r.x + d[0] + 0.5, r.y + d[1], r.z + d[2] + 0.5),
                        plugin.goods().item(f.produce, 1));
            }
            hideFruit(r, slot);
        }
        Map<Long, Rec> m = trees.get(r.world.getUID());
        if (m != null) m.remove(key(r.x, r.y, r.z));
        saveChunk(r.world, r.x >> 4, r.z >> 4);
        if (drop) r.world.dropItemNaturally(at, plugin.goods().item(r.type.sapling(), 1));
        r.world.playSound(at, "minecraft:block.big_dripleaf.break", 0.9f, 0.9f);
    }

    // ------------------------------------------------------------ growth, ripening, ambience

    void tick() {
        tick++;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Map<Long, Rec> m : new ArrayList<>(trees.values())) {
            for (Rec r : new ArrayList<>(m.values())) {
                if (!r.world.isChunkLoaded(r.x >> 4, r.z >> 4)) continue;
                Form f = form(r.type);
                if (f == null) continue;
                Block base = r.world.getBlockAt(r.x, r.y, r.z);
                if (base.getType() != f.stages[r.stage].trunk) {   // trunk gone: explosion, grief, ...
                    fell(r, false);
                    continue;
                }
                if (r.stage < STAGES - 1) {
                    if (rnd.nextDouble() < stageChance && fits(base, f.stages[r.stage + 1], r)) {
                        eraseFruit(r);
                        clearCells(r);
                        r.stage++;
                        paste(base, f.stages[r.stage], r);
                        saveChunk(r.world, r.x >> 4, r.z >> 4);
                        r.world.playSound(new Location(r.world, r.x + 0.5, r.y + 1, r.z + 0.5), "minecraft:block.budding_amethyst.place", 0.4f, 1.4f);
                    }
                } else if (r.type.fruit() != null && r.ripe.isEmpty() && rnd.nextDouble() < fruitChance) {
                    Fruit fr = r.type.fruit();
                    Shape top = f.stages[STAGES - 1];
                    int want = Math.min(top.fruit.size(), fr.minDrop + rnd.nextInt(fr.maxDrop - fr.minDrop + 1));
                    List<Integer> free = new ArrayList<>();
                    for (int s = 0; s < top.fruit.size(); s++) free.add(s);
                    java.util.Collections.shuffle(free, rnd);
                    for (int i = 0; i < want && i < free.size(); i++) {
                        int[] d = top.fruit.get(free.get(i));
                        Block leaf = r.world.getBlockAt(r.x + d[0], r.y + d[1], r.z + d[2]);
                        if (!leaf.getType().isAir() && !replaceable(leaf.getType())) showFruit(r, free.get(i));
                    }
                    if (!r.ripe.isEmpty()) saveChunk(r.world, r.x >> 4, r.z >> 4);
                }
                if ((r.type.petals || r.type.leafFall) && r.stage == STAGES - 1
                        && !r.world.getNearbyPlayers(new Location(r.world, r.x + 0.5, r.y + 3, r.z + 0.5), 16).isEmpty()
                        && rnd.nextInt(10) == 0) {
                    double px = r.x + 0.5 + rnd.nextDouble(5) - 2.5, pz = r.z + 0.5 + rnd.nextDouble(5) - 2.5;
                    double py = r.y + f.stages[STAGES - 1].maxY + 1.2;
                    r.world.spawnParticle(r.type.petals ? Particle.CHERRY_LEAVES : Particle.FALLING_SPORE_BLOSSOM, px, py, pz, 1, 0.1, 0.0, 0.1, 0);
                }
            }
        }
    }

    void relight() {
        for (Map<Long, Rec> m : trees.values()) for (Rec r : m.values()) {
            Form f = form(r.type);
            if (f == null) continue;
            for (Map.Entry<Integer, ItemDisplay> en : r.displays.entrySet()) {
                ItemDisplay d = en.getValue();
                if (d == null || !d.isValid()) continue;
                int[] off = f.stages[STAGES - 1].fruit.get(en.getKey());
                Block at = r.world.getBlockAt(r.x + off[0], r.y + off[1], r.z + off[2]);
                d.setBrightness(new Display.Brightness(Math.max(at.getLightFromBlocks(), 4), at.getLightFromSky()));
            }
        }
    }

    // ------------------------------------------------------------ chunk persistence

    private void saveChunk(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx, cz)) return;
        Chunk c = w.getChunkAt(cx, cz);
        StringBuilder sb = new StringBuilder();
        Map<Long, Rec> m = trees.get(w.getUID());
        if (m != null) for (Rec r : m.values()) {
            if (r.x >> 4 != cx || r.z >> 4 != cz) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(r.line());
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
        Map<Long, Rec> m = trees.computeIfAbsent(c.getWorld().getUID(), k -> new HashMap<>());
        for (String line : data.split("\n")) {
            try {
                String[] parts = line.split("\\|");
                Tree t = Tree.byId(parts[0]);
                Form f = t == null ? null : form(t);
                if (f == null) continue;
                int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
                int stage = Integer.parseInt(parts[4]);
                if (stage < 0 || stage >= STAGES || m.containsKey(key(x, y, z))) continue;
                Shape s = f.stages[stage];
                if (c.getWorld().getBlockAt(x, y, z).getType() != s.trunk) continue;   // trunk gone
                Rec r = new Rec(t, c.getWorld(), x, y, z, stage);
                m.put(key(x, y, z), r);
                for (int i = 0; i < s.cells.size(); i++) {
                    int[] d = s.cells.get(i);
                    Block at = c.getWorld().getBlockAt(x + d[0], y + d[1], z + d[2]);
                    if (at.getType() == s.mats.get(i)) cellOwner.put(key(at.getX(), at.getY(), at.getZ()), r);
                }
                if (stage == STAGES - 1 && parts.length > 5 && !parts[5].equals("-")) for (String p : parts[5].split(",")) {
                    try {
                        int slot = Integer.parseInt(p);
                        if (slot >= 0 && slot < f.stages[STAGES - 1].fruit.size()) showFruit(r, slot);
                    } catch (NumberFormatException ignored) { }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad orchard entry in chunk " + c.getX() + "," + c.getZ());
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Map<Long, Rec> m = trees.get(e.getChunk().getWorld().getUID());
        if (m == null) return;
        List<Long> gone = new ArrayList<>();
        for (Map.Entry<Long, Rec> en : m.entrySet()) {
            Rec r = en.getValue();
            if (r.x >> 4 == e.getChunk().getX() && r.z >> 4 == e.getChunk().getZ()) {
                eraseFruit(r);
                removeCellOwner(r);
                gone.add(en.getKey());
            }
        }
        for (Long k : gone) m.remove(k);
    }

    private void removeCellOwner(Rec r) {
        cellOwner.entrySet().removeIf(en -> en.getValue() == r);
    }

    void removeAllDisplays() {
        for (Map<Long, Rec> m : trees.values()) for (Rec r : m.values()) eraseFruit(r);
        cellOwner.clear();
        for (World w : plugin.getServer().getWorlds())
            for (Entity en : w.getEntities())
                if (en.getPersistentDataContainer().has(fruitKey, PersistentDataType.BYTE)
                        || en.getPersistentDataContainer().has(hitKey, PersistentDataType.BYTE)) en.remove();
    }

    int count() {
        int n = 0;
        for (Map<Long, Rec> m : trees.values()) n += m.size();
        return n;
    }
}
