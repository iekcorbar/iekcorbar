package com.iekcorbar.kitchen;

import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.entity.Display;
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
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import io.papermc.paper.datacomponent.DataComponentTypes;
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
 * Kitchen crops. A crop is a structure void above farmland or Rich Soil: invisible, no block
 * states for vanilla to "repair", and unplaceable by players - the identity (type, stage) lives
 * in the chunk's crop list and the plant you see is a small non-persistent ItemDisplay with the
 * kitchen:block/&lt;crop&gt;_stage&lt;n&gt; model, the same trick the props use. Entry encoding in
 * the chunk data: position bits 0-16 (see pack), CropType ordinal bits 17-24, stage bits 25-27,
 * wild flag bit 28. Old tripwire-state crops are migrated the moment their chunk loads.
 */
final class Crops implements Listener {
    private static final int POS_MASK = (1 << 17) - 1, TYPE_SHIFT = 17, STAGE_SHIFT = 25, WILD_SHIFT = 28;
    /** tune here if the plant model floats or sinks in the ground */
    static final float DISPLAY_SCALE = 1f, DISPLAY_Y = 0.5f;

    record Info(CropType type, int stage, boolean wild) {
        boolean ripe() {
            return !wild && stage == CropType.STAGES - 1;
        }
    }

    private static final class Entry {
        CropType type;
        int stage;
        boolean wild;
        ItemDisplay display;

        Entry(CropType type, int stage, boolean wild) {
            this.type = type;
            this.stage = stage;
            this.wild = wild;
        }

        Info info() {
            return new Info(type, stage, wild);
        }
    }

    private final KitchenPlugin plugin;
    private final NamespacedKey posKey, wildKey, tagKey;
    /** world -> chunk key -> position -> entry. */
    private final Map<UUID, Map<Long, Map<Integer, Entry>>> crops = new HashMap<>();
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();
    private final Map<CropType, Double> chance = new HashMap<>();
    private double maxChance, richSpeed;
    private int minLight;
    private boolean wildEnabled, wildExisting;
    private double wildChance;

    Crops(KitchenPlugin plugin) {
        this.plugin = plugin;
        posKey = new NamespacedKey(plugin, "crops");
        wildKey = new NamespacedKey(plugin, "wild_v1");
        tagKey = new NamespacedKey(plugin, "crop_display");
        load();
    }

    void load() {
        var c = plugin.getConfig();
        richSpeed = Math.max(1, c.getDouble("crops.rich-soil-speed", 1.6));
        minLight = c.getInt("crops.min-light", 9);
        maxChance = 0;
        for (CropType t : CropType.values()) {
            double minutes = Math.max(1, c.getDouble("crops.growth-minutes." + t.id(), 20));
            double p = (CropType.STAGES - 1) / (minutes * 60); // stage steps per second
            chance.put(t, p);
            maxChance = Math.max(maxChance, p * richSpeed);
        }
        wildEnabled = c.getBoolean("wild-crops.enabled", true);
        wildChance = c.getDouble("wild-crops.chance-per-chunk", 0.12);
        wildExisting = c.getBoolean("wild-crops.generate-in-existing-chunks", true);
    }

    // ------------------------------------------------------------ entries

    private static int encode(int pos, Entry e) {
        return pos | e.type.ordinal() << TYPE_SHIFT | e.stage << STAGE_SHIFT | (e.wild ? 1 << WILD_SHIFT : 0);
    }

    /** Reads the OLD tripwire bit encoding (north 1, east 2, south 4, west 8, attached 16, powered 32). */
    private static Info legacyInfo(Block b) {
        if (b.getType() != Material.TRIPWIRE || !(b.getBlockData() instanceof Tripwire t) || !t.isDisarmed()) return null;
        int i = (t.hasFace(BlockFace.NORTH) ? 1 : 0) | (t.hasFace(BlockFace.EAST) ? 2 : 0) | (t.hasFace(BlockFace.SOUTH) ? 4 : 0)
                | (t.hasFace(BlockFace.WEST) ? 8 : 0) | (t.isAttached() ? 16 : 0) | (t.isPowered() ? 32 : 0);
        if (i < 0 || i / 5 >= CropType.values().length) return null;
        CropType type = CropType.values()[i / 5];
        int s = i % 5;
        return new Info(type, s == 4 ? 0 : s, s == 4);
    }

    /** The crop growing on this block, or null. Only structure voids carry a crop. */
    Info info(Block b) {
        if (b.getType() != Material.STRUCTURE_VOID) return null;
        Map<Integer, Entry> m = entries(b);
        if (m == null) return null;
        Entry e = m.get(pack(b));
        return e == null ? null : e.info();
    }

    private Map<Integer, Entry> entries(Block b) {
        Map<Long, Map<Integer, Entry>> w = crops.get(b.getWorld().getUID());
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

    /** Adds an entry and its display; the structure void block is up to the caller. */
    private void add(Block b, Entry e) {
        long ck = Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4);
        crops.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>())
                .computeIfAbsent(ck, k -> new HashMap<>()).put(pack(b), e);
        dirty.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>()).add(ck);
        show(b, e);
    }

    void loadChunk(Chunk c) {
        int[] arr = c.getPersistentDataContainer().get(posKey, PersistentDataType.INTEGER_ARRAY);
        if (arr == null || arr.length == 0) return;
        Map<Integer, Entry> existing = crops.get(c.getWorld().getUID()) == null ? null : crops.get(c.getWorld().getUID()).get(c.getChunkKey());
        if (existing != null && !existing.isEmpty()) return; // already loaded (chunk event + startup scan
                                                              // can race in the same tick; replacing the map
                                                              // here orphaned the first spawn of displays)
        Map<Integer, Entry> map = new HashMap<>();
        boolean converted = false;
        for (int e : arr) {
            int pos = e & POS_MASK;
            Block b = unpack(c, pos);
            Entry en;
            if (b.getType() == Material.STRUCTURE_VOID) {
                int ord = (e >>> TYPE_SHIFT) & 255, stage = (e >>> STAGE_SHIFT) & 7;
                if (ord >= CropType.values().length || stage >= CropType.STAGES) continue;   // nonsense - drop it
                en = new Entry(CropType.values()[ord], stage, ((e >>> WILD_SHIFT) & 1) != 0);
            } else if (b.getType() == Material.TRIPWIRE) {                                   // old crop: migrate
                Info i = legacyInfo(b);
                if (i == null) continue;
                en = new Entry(i.type(), i.stage(), i.wild());
                b.setType(Material.STRUCTURE_VOID, false);
                converted = true;
            } else continue;                                                                 // gone - forget it
            map.put(pos, en);
        }
        if (map.isEmpty()) {
            c.getPersistentDataContainer().remove(posKey);
            return;
        }
        crops.computeIfAbsent(c.getWorld().getUID(), k -> new HashMap<>()).put(c.getChunkKey(), map);
        if (converted) dirty.computeIfAbsent(c.getWorld().getUID(), k -> new HashSet<>()).add(c.getChunkKey());
        for (Map.Entry<Integer, Entry> en : map.entrySet())
            show(unpack(c, en.getKey()), en.getValue());
    }

    void unloadChunk(Chunk c) {
        save(c);
        Map<Long, Map<Integer, Entry>> w = crops.get(c.getWorld().getUID());
        if (w == null) return;
        Map<Integer, Entry> map = w.remove(c.getChunkKey());
        if (map != null) for (Entry e : map.values()) if (e.display != null) e.display.remove();
    }

    private void save(Chunk c) {
        Set<Long> d = dirty.get(c.getWorld().getUID());
        if (d == null || !d.remove(c.getChunkKey())) return;
        Map<Long, Map<Integer, Entry>> w = crops.get(c.getWorld().getUID());
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
        for (Map<Long, Map<Integer, Entry>> w : crops.values())
            for (Map<Integer, Entry> m : w.values())
                for (Entry e : m.values())
                    if (e.display != null) {
                        e.display.remove();
                        e.display = null;
                    }
    }

    // ------------------------------------------------------------ the plant you see

    private ItemStack look(Entry e) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kitchen", "block/" + e.type.id() + (e.wild ? "_wild" : "_stage" + e.stage)));
        return it;
    }

    private void show(Block b, Entry e) {
        if (e.display != null && e.display.isValid()) {
            e.display.setItemStack(look(e));
            return;
        }
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

    /** Updates stage bits + display + chunk data. */
    private void setStage(Block b, Entry e, int stage, boolean wild) {
        e.stage = stage;
        e.wild = wild;
        long ck = Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4);
        dirty.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>()).add(ck);
        show(b, e);
    }

    // ------------------------------------------------------------ growth

    /** Once a second. */
    void tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World w : plugin.getServer().getWorlds()) {
            Map<Long, Map<Integer, Entry>> map = crops.get(w.getUID());
            if (map == null) continue;
            for (Map.Entry<Long, Map<Integer, Entry>> en : map.entrySet()) {
                long ck = en.getKey();
                int cx = (int) ck, cz = (int) (ck >> 32);
                if (!w.isChunkLoaded(cx, cz)) continue;
                Chunk c = null;
                for (Iterator<Map.Entry<Integer, Entry>> it = en.getValue().entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, Entry> pe = it.next();
                    double roll = r.nextDouble();
                    if (roll >= maxChance) continue;
                    if (c == null) c = w.getChunkAt(cx, cz);
                    Block b = unpack(c, pe.getKey());
                    Entry e = pe.getValue();
                    if (b.getType() != Material.STRUCTURE_VOID) {
                        it.remove();   // the void is gone (explosion, edit): forget the crop
                        if (e.display != null) e.display.remove();
                        dirty.computeIfAbsent(w.getUID(), k -> new HashSet<>()).add(ck);
                        continue;
                    }
                    if (e.wild || e.stage == CropType.STAGES - 1) continue;
                    Block soil = b.getRelative(BlockFace.DOWN);
                    boolean rich = plugin.props().isRichSoil(soil);
                    if (roll >= chance.get(e.type) * (rich ? richSpeed : 1)) continue;
                    if (!soilOk(e.type, soil)) continue;
                    if (Math.max(b.getLightFromSky(), b.getLightFromBlocks()) < minLight) continue;
                    setStage(b, e, e.stage + 1, false);
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

    private boolean soilOk(CropType t, Block soil) {
        if (plugin.props().isRichSoil(soil)) return !t.needsWater || nearWater(soil);
        if (soil.getType() != Material.FARMLAND) return false;
        if (!t.needsWater) return true;
        return soil.getBlockData() instanceof Farmland f && f.getMoisture() >= f.getMaximumMoisture();
    }

    private static boolean nearWater(Block b) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (b.getRelative(dx, 0, dz).getType() == Material.WATER) return true;
        return false;
    }

    private boolean canPlantOn(Block soil) {
        return soil.getType() == Material.FARMLAND || plugin.props().isRichSoil(soil);
    }

    // ------------------------------------------------------------ drops

    List<ItemStack> drops(Info i) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemStack> out = new ArrayList<>();
        CropType t = i.type();
        if (i.wild()) {
            out.add(plugin.items().create(t.seed, 1 + r.nextInt(2)));
            if (t.produce != t.seed && r.nextBoolean()) out.add(plugin.items().create(t.produce, 1));
        } else if (i.ripe()) {
            out.add(plugin.items().create(t.produce, t.minProduce + r.nextInt(t.maxProduce - t.minProduce + 1)));
            if (t.extra != null) {
                int n = t.minExtra + r.nextInt(t.maxExtra - t.minExtra + 1);
                if (n > 0) out.add(plugin.items().create(t.extra, n));
            }
        } else {
            out.add(plugin.items().create(t.seed, 1));
        }
        return out;
    }

    /** Removes the crop and drops what it gives. */
    void breakCrop(Block b, boolean drop) {
        Info i = info(b);
        if (i == null) return;
        Map<Integer, Entry> m = entries(b);
        Entry e = m == null ? null : m.remove(pack(b));
        if (e != null && e.display != null) e.display.remove();
        dirty.computeIfAbsent(b.getWorld().getUID(), k -> new HashSet<>())
                .add(Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4));
        b.setType(Material.AIR, false);
        if (drop) {
            Location at = b.getLocation().add(0.5, 0.3, 0.5);
            for (ItemStack it : drops(i)) b.getWorld().dropItemNaturally(at, it);
        }
        b.getWorld().playSound(b.getLocation().add(0.5, 0.2, 0.5), Sound.BLOCK_CROP_BREAK, 0.8f, 1f);
    }

    // ------------------------------------------------------------ player actions

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block soil = e.getClickedBlock();
        if (soil == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = soil.getRelative(BlockFace.UP);                     // the structure void (if any)
        Info info = info(b);
        ItemStack hand = e.getItem();
        if (info != null) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            Player p = e.getPlayer();
            Entry en = entries(b).get(pack(b));
            if (hand != null && hand.getType() == Material.BONE_MEAL && !en.wild && !info.ripe()) {
                int stage = Math.min(CropType.STAGES - 1, en.stage + 1 + (ThreadLocalRandom.current().nextInt(3) == 0 ? 1 : 0));
                setStage(b, en, stage, false);
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.4, 0.5), 8, 0.3, 0.2, 0.3);
                b.getWorld().playSound(b.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1f, 1f);
                p.swingMainHand();
            } else if (info.ripe()) {
                // pick by hand: drops the harvest and starts again (tea regrows from a later stage)
                if (!plugin.canBreak(p, soil)) return;
                Location at = b.getLocation().add(0.5, 0.3, 0.5);
                for (ItemStack it : drops(info)) b.getWorld().dropItemNaturally(at, it);
                plugin.adv().grant(p, "harvest");
                int back = en.type.regrowStage >= 0 ? en.type.regrowStage : 0;
                setStage(b, en, back, false);
                b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
                p.swingMainHand();
            }
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND || e.getBlockFace() != BlockFace.UP) return;
        CropType t = CropType.bySeed(plugin.items().typeOf(hand));
        if (t == null || !canPlantOn(soil)) return;
        if (!b.getType().isAir()) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        plugin.adv().grant(p, "planted");
        BlockState old = b.getState();
        b.setType(Material.STRUCTURE_VOID, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(b, old, soil, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            old.update(true, false);
            return;
        }
        add(b, new Entry(t, 0, false));
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        b.getWorld().playSound(b.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 1f);
        p.swingMainHand();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof KitchenPlugin.ProbeBreakEvent) return;   // our own canBreak probe: the harvest drops in onInteract
        Block b = e.getBlock();
        Info info = info(b);
        if (info != null) {   // a hand at the void itself (creative break, /setblock undo...)
            e.setDropItems(false);
            breakCrop(b, e.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        Block above = b.getRelative(BlockFace.UP);
        if (info(above) != null) breakCrop(above, e.getPlayer().getGameMode() != GameMode.CREATIVE);
        // a knife cuts straw from grass
        if ((b.getType() == Material.SHORT_GRASS || b.getType() == Material.TALL_GRASS || b.getType() == Material.FERN)
                && plugin.items().typeOf(e.getPlayer().getInventory().getItemInMainHand()) != null
                && plugin.items().typeOf(e.getPlayer().getInventory().getItemInMainHand()).knife()
                && ThreadLocalRandom.current().nextDouble() < 0.35) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), plugin.items().create(KItem.STRAW, 1));
        }
    }

    // ------------------------------------------------------------ keep the spot safe

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        // water next to a crop is fine (farmland needs it); it just never flows into the crop's own block
        if (info(e.getToBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        if (touchesCrop(e.getBlocks()) || info(e.getBlock().getRelative(e.getDirection())) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        if (touchesCrop(e.getBlocks())) e.setCancelled(true);
    }

    private boolean touchesCrop(List<Block> blocks) {
        for (Block b : blocks) if (info(b) != null || info(b.getRelative(BlockFace.UP)) != null) return true;
        return false;
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
            if (info(b) != null) breakCrop(b, true);
            else {
                Block above = b.getRelative(BlockFace.UP);
                if (info(above) != null) breakCrop(above, true);
            }
        }
    }

    /** Dry farmland under a crop stays farmland (vanilla keeps it under wheat too). */
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (e.getBlock().getType() == Material.FARMLAND && info(e.getBlock().getRelative(BlockFace.UP)) != null) e.setCancelled(true);
    }

    /** Trampling: farmland under a crop is too busy growing to turn back into dirt. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTrample(EntityChangeBlockEvent e) {
        if (e.getBlock().getType() != Material.FARMLAND || e.getTo() == Material.FARMLAND) return;
        if (info(e.getBlock().getRelative(BlockFace.UP)) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------ wild crops

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
        if (!wildEnabled || (!wildExisting && !e.isNewChunk())) return;
        Chunk c = e.getChunk();
        if (c.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (c.getPersistentDataContainer().has(wildKey, PersistentDataType.BYTE)) return;
        c.getPersistentDataContainer().set(wildKey, PersistentDataType.BYTE, (byte) 1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= wildChance) return;
        int x = 4 + r.nextInt(8), z = 4 + r.nextInt(8);
        Block top = c.getWorld().getHighestBlockAt((c.getX() << 4) + x, (c.getZ() << 4) + z);
        CropType t = wildFor(top);
        if (t == null) return;
        int n = 2 + r.nextInt(4);
        for (int i = 0; i < n * 3 && n > 0; i++) {
            Block g = c.getWorld().getHighestBlockAt(top.getX() + r.nextInt(5) - 2, top.getZ() + r.nextInt(5) - 2);
            if (g.getX() >> 4 != c.getX() || g.getZ() >> 4 != c.getZ()) continue;
            if (!wildSoil(g.getType())) continue;
            Block above = g.getRelative(BlockFace.UP);
            if (!above.getType().isAir()) continue;
            above.setType(Material.STRUCTURE_VOID, false);
            add(above, new Entry(t, 0, true));
            n--;
        }
    }

    private static boolean wildSoil(Material m) {
        return m == Material.GRASS_BLOCK || m == Material.SAND || m == Material.MUD || m == Material.PODZOL;
    }

    private static CropType wildFor(Block top) {
        if (!wildSoil(top.getType())) return null;
        Biome b = top.getBiome();
        String k = b.getKey().getKey();
        if (k.contains("swamp") || k.equals("river")) return CropType.RICE;
        if (k.contains("cherry") || k.contains("jungle")) return CropType.TEA;
        if (k.contains("beach")) return CropType.NAPA_CABBAGE;
        if (k.contains("meadow") || k.contains("flower")) return CropType.DAIKON;
        if (k.contains("forest") || k.contains("taiga")) return CropType.GREEN_ONION;
        if (k.contains("badlands") || k.contains("savanna")) return CropType.CORN;
        if (k.contains("plains")) return CropType.SOYBEAN;
        return null;
    }

    /** All loaded crop positions (for /kitchen info). */
    int count() {
        int n = 0;
        for (Map<Long, Map<Integer, Entry>> w : crops.values()) for (Map<Integer, Entry> m : w.values()) n += m.size();
        return n;
    }

    static boolean isCropBlock(Material m) {
        return m == Material.STRUCTURE_VOID || Tag.CROPS.isTagged(m);
    }
}
