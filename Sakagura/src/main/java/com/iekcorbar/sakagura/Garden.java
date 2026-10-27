package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Tripwire;
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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The garden: fruit and crops. A growing plant is a structure void above farmland - invisible,
 * no block states for vanilla to tamper with - while the type and stage live in the chunk's
 * plant list (entry bits: position 0-16, Fruit ordinal 17-24, stage 25-27) and the plant you
 * see is a small non-persistent ItemDisplay with the sakagura:block/&lt;crop&gt;_stage&lt;n&gt;
 * model, the same trick the props use. Old tripwire-state plants migrate when their chunk loads.
 */
final class Garden implements Listener {
    private static final int POS_MASK = (1 << 17) - 1, TYPE_SHIFT = 17, STAGE_SHIFT = 25;
    /** tune here if the plant model floats or sinks in the ground */
    static final float DISPLAY_SCALE = 1f, DISPLAY_Y = 0.5f;
    /** the clickable box over the plant's block (see show()) - a touch smaller than the block */
    static final float HIT_W = 0.9f, HIT_H = 0.95f;

    record Info(Fruit fruit, int stage) {
        boolean ripe() {
            return stage == Fruit.STAGES - 1;
        }
    }

    private static final class Entry {
        Fruit fruit;
        int stage;
        ItemDisplay display;
        Interaction hitbox;

        Entry(Fruit fruit, int stage) {
            this.fruit = fruit;
            this.stage = stage;
        }

        Info info() {
            return new Info(fruit, stage);
        }
    }

    private final Sakagura plugin;
    private final NamespacedKey posKey, wildKey, tagKey, hitKey;
    private final Map<UUID, Map<Long, Map<Integer, Entry>>> plants = new HashMap<>();
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();
    private double wildChance;

    Garden(Sakagura plugin) {
        this.plugin = plugin;
        posKey = new NamespacedKey(plugin, "crops");
        wildKey = new NamespacedKey(plugin, "wild_v1");
        tagKey = new NamespacedKey(plugin, "garden_display");
        hitKey = new NamespacedKey(plugin, "garden_hitbox");
        wildChance = plugin.getConfig().getDouble("garden.wild-chance-per-chunk", 0.1);
    }

    // ------------------------------------------------------------ entries

    private static int encode(int pos, Entry e) {
        return pos | e.fruit.ordinal() << TYPE_SHIFT | e.stage << STAGE_SHIFT;
    }

    /** Reads the OLD tripwire bit encoding (garden plants lived at index 30 and up). */
    private static Info legacyInfo(Block b) {
        if (b.getType() != Material.TRIPWIRE || !(b.getBlockData() instanceof Tripwire t) || !t.isDisarmed()) return null;
        int i = (t.hasFace(BlockFace.NORTH) ? 1 : 0) | (t.hasFace(BlockFace.EAST) ? 2 : 0) | (t.hasFace(BlockFace.SOUTH) ? 4 : 0)
                | (t.hasFace(BlockFace.WEST) ? 8 : 0) | (t.isAttached() ? 16 : 0) | (t.isPowered() ? 32 : 0);
        if (i < 30 || (i - 30) / 3 >= Fruit.values().length) return null;
        int s = (i - 30) % 3;
        return s == 3 ? null : new Info(Fruit.values()[(i - 30) / 3], s);
    }

    Info info(Block b) {
        if (b.getType() != Material.STRUCTURE_VOID) return null;
        Map<Integer, Entry> m = entries(b);
        if (m == null) return null;
        Entry e = m.get(pack(b));
        return e == null ? null : e.info();
    }

    private Map<Integer, Entry> entries(Block b) {
        Map<Long, Map<Integer, Entry>> w = plants.get(b.getWorld().getUID());
        if (w == null) return null;
        return w.get(Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4));
    }

    // ------------------------------------------------------------ index

    private static int pack(Block b) {
        return ((b.getY() - b.getWorld().getMinHeight()) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private static Block unpack(Chunk c, int p) {
        return c.getWorld().getBlockAt((c.getX() << 4) | ((p >> 4) & 15), (p >> 8) + c.getWorld().getMinHeight(), (c.getZ() << 4) | (p & 15));
    }

    private void add(Block b, Entry e) {
        long ck = Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4);
        plants.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>())
                .computeIfAbsent(ck, k -> new HashMap<>()).put(pack(b), e);
        dirty.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>()).add(ck);
        show(b, e);
    }

    void loadChunk(Chunk c) {
        int[] arr = c.getPersistentDataContainer().get(posKey, PersistentDataType.INTEGER_ARRAY);
        if (arr == null || arr.length == 0) return;
        Map<Integer, Entry> map = new HashMap<>();
        boolean converted = false;
        for (int e : arr) {
            int pos = e & POS_MASK;
            Block b = unpack(c, pos);
            Entry en;
            if (b.getType() == Material.STRUCTURE_VOID) {
                int ord = (e >>> TYPE_SHIFT) & 255, stage = (e >>> STAGE_SHIFT) & 7;
                if (ord >= Fruit.values().length || stage >= Fruit.STAGES) continue;
                en = new Entry(Fruit.values()[ord], stage);
            } else if (b.getType() == Material.TRIPWIRE) {
                Info i = legacyInfo(b);
                if (i == null) continue;
                en = new Entry(i.fruit(), i.stage());
                b.setType(Material.STRUCTURE_VOID, false);
                converted = true;
            } else continue;
            map.put(pos, en);
        }
        if (map.isEmpty()) {
            c.getPersistentDataContainer().remove(posKey);
            return;
        }
        plants.computeIfAbsent(c.getWorld().getUID(), k -> new HashMap<>()).put(c.getChunkKey(), map);
        if (converted) dirty.computeIfAbsent(c.getWorld().getUID(), k -> new HashSet<>()).add(c.getChunkKey());
        for (Map.Entry<Integer, Entry> en : map.entrySet())
            show(unpack(c, en.getKey()), en.getValue());
    }

    void unloadChunk(Chunk c) {
        save(c);
        Map<Long, Map<Integer, Entry>> w = plants.get(c.getWorld().getUID());
        if (w == null) return;
        Map<Integer, Entry> map = w.remove(c.getChunkKey());
        if (map != null) for (Entry e : map.values()) clear(e);
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) {
        unloadChunk(e.getChunk());
    }

    private static void clear(Entry e) {
        if (e.display != null) e.display.remove();
        if (e.hitbox != null) e.hitbox.remove();
        e.display = null;
        e.hitbox = null;
    }

    private void save(Chunk c) {
        Set<Long> d = dirty.get(c.getWorld().getUID());
        if (d == null || !d.remove(c.getChunkKey())) return;
        Map<Long, Map<Integer, Entry>> w = plants.get(c.getWorld().getUID());
        Map<Integer, Entry> m = w == null ? null : w.get(c.getChunkKey());
        if (m == null || m.isEmpty()) c.getPersistentDataContainer().remove(posKey);
        else {
            int[] arr = new int[m.size()];
            int i = 0;
            for (Map.Entry<Integer, Entry> en : m.entrySet()) arr[i++] = encode(en.getKey(), en.getValue());
            c.getPersistentDataContainer().set(posKey, PersistentDataType.INTEGER_ARRAY, arr);
        }
    }

    void saveAll() {
        for (World w : plugin.getServer().getWorlds()) {
            Set<Long> d = dirty.get(w.getUID());
            if (d == null) continue;
            for (Long ck : new ArrayList<>(d)) {
                int x = (int) (long) ck, z = (int) (ck >> 32);
                if (w.isChunkLoaded(x, z)) save(w.getChunkAt(x, z));
            }
        }
    }

    void removeAllDisplays() {
        for (Map<Long, Map<Integer, Entry>> w : plants.values())
            for (Map<Integer, Entry> m : w.values())
                for (Entry e : m.values())
                    clear(e);
    }

    // ------------------------------------------------------------ the plant you see

    private ItemStack look(Entry e) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "block/" + e.fruit.id() + "_stage" + e.stage));
        return it;
    }

    private void show(Block b, Entry e) {
        if (e.display != null && e.display.isValid()) {
            e.display.setItemStack(look(e));
        } else {
            Location at = b.getLocation().add(0.5, DISPLAY_Y, 0.5);
            e.display = b.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                d.setItemStack(look(e));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
                d.setBrightness(new Display.Brightness(Math.max(b.getLightFromBlocks(), 4), b.getLightFromSky()));
            });
        }
        // The plant you SEE is only an ItemDisplay and the structure void it marks has no
        // outline of its own, so without a hitbox the only way to click a plant is the farmland
        // under it - aim at the plant's face and the ray travels past it to whatever block
        // stands behind. One Interaction over the plant's block makes the plant itself
        // clickable (bone meal / harvest) and punchable (break), like the walkable props
        // in Decor do.
        if (e.hitbox == null || !e.hitbox.isValid()) {
            e.hitbox = b.getWorld().spawn(new Location(b.getWorld(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5), Interaction.class, i -> {
                i.setPersistent(false);
                i.getPersistentDataContainer().set(hitKey, PersistentDataType.BYTE, (byte) 1);
                i.setInteractionWidth(HIT_W);
                i.setInteractionHeight(HIT_H);
                i.setResponsive(true);
            });
        }
    }

    private void setStage(Block b, Entry e, int stage) {
        e.stage = stage;
        long ck = Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4);
        dirty.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>()).add(ck);
        show(b, e);
    }

    // ------------------------------------------------------------ growth (every second)

    void tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World w : plugin.getServer().getWorlds()) {
            Map<Long, Map<Integer, Entry>> map = plants.get(w.getUID());
            if (map == null) continue;
            for (Map.Entry<Long, Map<Integer, Entry>> en : map.entrySet()) {
                long ck = en.getKey();
                int cx = (int) ck, cz = (int) (ck >> 32);
                if (!w.isChunkLoaded(cx, cz)) continue;
                Chunk c = null;
                for (Iterator<Map.Entry<Integer, Entry>> it = en.getValue().entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, Entry> pe = it.next();
                    double roll = r.nextDouble();
                    if (roll >= 1 / (14.0 * 60)) continue; // fastest plant: 2 steps in 14 min
                    if (c == null) c = w.getChunkAt(cx, cz);
                    Block b = unpack(c, pe.getKey());
                    Entry e = pe.getValue();
                    if (b.getType() != Material.STRUCTURE_VOID) {
                        it.remove();
                        clear(e);
                        dirty.computeIfAbsent(w.getUID(), k -> new HashSet<>()).add(ck);
                        continue;
                    }
                    if (e.stage == Fruit.STAGES - 1) continue;
                    double chance = (Fruit.STAGES - 1) / (e.fruit.minutes * 60.0);
                    if (roll >= chance) continue;
                    Block soil = b.getRelative(BlockFace.DOWN);
                    if (soil.getType() != Material.FARMLAND) continue;
                    if (e.fruit == Fruit.GRAPE || e.fruit == Fruit.HOPS) {
                        if (!(soil.getBlockData() instanceof Farmland f) || f.getMoisture() < f.getMaximumMoisture()) continue; // thirsty vines
                    }
                    if (Math.max(b.getLightFromSky(), b.getLightFromBlocks()) < 9) continue;
                    setStage(b, e, e.stage + 1);
                }
            }
            Set<Long> d = dirty.get(w.getUID());
            if (d != null && plugin.tick % 600 == 0) {
                for (Long ck : new ArrayList<>(d)) {
                    int x = (int) (long) ck, z = (int) (ck >> 32);
                    if (w.isChunkLoaded(x, z)) save(w.getChunkAt(x, z));
                    else d.remove(ck);
                }
            }
        }
    }

    // ------------------------------------------------------------ planting / picking

    List<ItemStack> drops(Info i) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemStack> out = new ArrayList<>();
        Fruit f = i.fruit();
        if (i.ripe()) {
            out.add(plugin.goods().item(f.produce, f.minDrop + r.nextInt(f.maxDrop - f.minDrop + 1)));
            if (f.seed != null && r.nextInt(3) == 0) out.add(plugin.goods().item(f.seed, 1));
        } else {
            out.add(plugin.goods().item(f.plantItem(), 1));
        }
        return out;
    }

    /**
     * Bone-meal or harvest the plant in this structure void. Reached from two clicks:
     * right-clicking the farmland under a plant (as always) and, since 0.3.5, right-
     * clicking the plant's own Interaction hitbox - players aim at the plant they see,
     * not at the soil two blocks further down the ray.
     */
    private void pick(Block b, Player p, ItemStack hand) {
        Map<Integer, Entry> m = entries(b);
        Entry en = m == null ? null : m.get(pack(b));
        if (en == null) return;
        Info info = en.info();
        if (hand != null && hand.getType() == Material.BONE_MEAL && !info.ripe()) {
            setStage(b, en, en.stage + 1);
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.4, 0.5), 8, 0.3, 0.2, 0.3);
            b.getWorld().playSound(b.getLocation(), "minecraft:item.bone_meal.use", 1f, 1f);
        } else if (info.ripe()) {
            if (!plugin.canBreak(p, b.getRelative(BlockFace.DOWN))) return;
            Location at = b.getLocation().add(0.5, 0.3, 0.5);
            for (ItemStack it : drops(info)) b.getWorld().dropItemNaturally(at, it);
            plugin.adv().grant(p, "harvest");
            setStage(b, en, en.fruit.regrows ? 1 : 0);
            b.getWorld().playSound(at, "minecraft:block.sweet_berry_bush.pick_berries", 1f, 1f);
        }
        p.swingMainHand();
    }

    /** The plant block an Interaction hitbox spawned by show() stands in, or null. */
    private Block plantAt(Entity en) {
        if (!(en instanceof Interaction) || !en.getPersistentDataContainer().has(hitKey, PersistentDataType.BYTE)) return null;
        Block b = en.getWorld().getBlockAt(en.getLocation());
        return info(b) == null ? null : b;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHitboxUse(PlayerInteractEntityEvent e) {
        Block b = plantAt(e.getRightClicked());
        if (b == null) return;
        e.setCancelled(true);                                        // never also use the hand item on it
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        pick(b, p, p.getInventory().getItemInMainHand());
    }

    /** Punch the plant = break it (drop it), like a vanilla crop; protects via the soil probe. */
    @EventHandler(ignoreCancelled = true)
    public void onHitboxPunch(PrePlayerAttackEntityEvent e) {
        Block b = plantAt(e.getAttacked());
        if (b == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || plugin.canBreak(p, b.getRelative(BlockFace.DOWN)))
            breakPlant(b, p.getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block soil = e.getClickedBlock();
        if (soil == null || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = soil.getRelative(BlockFace.UP);                     // the structure void (if any)
        Info info = info(b);
        ItemStack hand = e.getItem();
        Player p = e.getPlayer();
        if (info != null) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            pick(b, p, hand);
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND || e.getBlockFace() != BlockFace.UP || soil.getType() != Material.FARMLAND) return;
        Fruit f = Fruit.byPlantItem(plugin.goods().idOf(hand));
        if (f == null || !b.getType().isAir()) return;
        e.setCancelled(true);
        BlockState old = b.getState();
        b.setType(Material.STRUCTURE_VOID, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(b, old, soil, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            old.update(true, false);
            return;
        }
        add(b, new Entry(f, 0));
        plugin.adv().grant(p, "planted");
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        b.getWorld().playSound(b.getLocation(), "minecraft:item.crop.plant", 1f, 1f);
        p.swingMainHand();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof Sakagura.ProbeBreakEvent) return;            // our own canBreak probe: picking drops in onInteract
        Block b = e.getBlock();
        Info info = info(b);
        if (info != null) {
            e.setDropItems(false);
            breakPlant(b, e.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        Block up = b.getRelative(BlockFace.UP);
        if (info(up) != null) breakPlant(up, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    /** Removes the plant and drops what it gives. */
    void breakPlant(Block b, boolean drop) {
        Info i = info(b);
        if (i == null) return;
        Map<Integer, Entry> m = entries(b);
        Entry e = m == null ? null : m.remove(pack(b));
        if (e != null) clear(e);
        dirty.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>())
                .add(Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4));
        b.setType(Material.AIR, false);
        if (drop)
            for (ItemStack it : drops(i)) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), it);
        b.getWorld().playSound(b.getLocation().add(0.5, 0.2, 0.5), "minecraft:block.crop.break", 0.8f, 1f);
    }

    // ------------------------------------------------------------ keep the spot safe

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (info(e.getToBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (info(b) != null || info(b.getRelative(BlockFace.UP)) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (e.getBlock().getType() == Material.FARMLAND && info(e.getBlock().getRelative(BlockFace.UP)) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTrample(EntityChangeBlockEvent e) {
        if (e.getBlock().getType() != Material.FARMLAND || e.getTo() == Material.FARMLAND) return;
        if (info(e.getBlock().getRelative(BlockFace.UP)) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        explode(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        explode(e.blockList());
    }

    private void explode(List<Block> blocks) {
        for (Block b : new ArrayList<>(blocks)) {
            if (info(b) != null) breakPlant(b, true);
            else {
                Block up = b.getRelative(BlockFace.UP);
                if (info(up) != null) breakPlant(up, true);
            }
        }
    }

    // ------------------------------------------------------------ wild bushes

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
        Chunk c = e.getChunk();
        if (c.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (c.getPersistentDataContainer().has(wildKey, PersistentDataType.BYTE)) return;
        c.getPersistentDataContainer().set(wildKey, PersistentDataType.BYTE, (byte) 1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= wildChance) return;
        int x = 4 + r.nextInt(8), z = 4 + r.nextInt(8);
        Block top = c.getWorld().getHighestBlockAt((c.getX() << 4) + x, (c.getZ() << 4) + z);
        if (top.getType() != Material.GRASS_BLOCK && top.getType() != Material.PODZOL) return;
        String biome = top.getBiome().getKey().getKey();
        List<Fruit> fit = new ArrayList<>();
        for (Fruit f : Fruit.values()) for (String bi : f.wildBiomes) if (biome.contains(bi)) fit.add(f);
        if (fit.isEmpty()) return;
        Fruit f = fit.get(r.nextInt(fit.size()));
        for (int i = 0; i < 8; i++) {
            Block g = c.getWorld().getHighestBlockAt(top.getX() + r.nextInt(5) - 2, top.getZ() + r.nextInt(5) - 2);
            if (g.getType() != Material.GRASS_BLOCK && g.getType() != Material.PODZOL) continue;
            Block up = g.getRelative(BlockFace.UP);
            if (!up.getType().isAir()) continue;
            up.setType(Material.STRUCTURE_VOID, false);              // wild bushes start ripe
            add(up, new Entry(f, Fruit.STAGES - 1));
        }
    }

    int count() {
        int n = 0;
        for (Map<Long, Map<Integer, Entry>> w : plants.values()) for (Map<Integer, Entry> m : w.values()) n += m.size();
        return n;
    }
}
