package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared prop engine (copied into each plugin by propkit/sync.py, edit it only there).
 * A prop is a hidden base block (barrier / light / nothing, one or more blocks tall) plus an
 * ItemDisplay with its model; props you can walk through get an Interaction hitbox for clicks.
 * Saved as text lines in the chunk data; displays respawn with the chunk. Fences join their
 * neighbours (model mask), gates open and close, seats carry one player.
 */
final class Decor implements Listener {
    /** Fired to ask protection plugins whether a player may break here (ignored by our own break handler). */
    static final class ProbeBreakEvent extends BlockBreakEvent {
        ProbeBreakEvent(Block block, Player player) {
            super(block, player);
        }
    }

    static final class Prop {
        final World world;
        final int x, y, z;
        final DecorDef def;
        BlockFace facing;
        boolean wall;
        String variant = "";
        boolean open;
        UUID owner;
        /** Free text of the plugin (kiosk price, zoo name, ...). */
        String data = "";
        int mask;
        long closeAt, lastHit;
        int hits;
        ItemDisplay display;
        Interaction hitbox;
        /** Spare slots for the plugin's own transient state (not saved). */
        final Map<String, Object> mem = new HashMap<>();

        Prop(World world, int x, int y, int z, DecorDef def, BlockFace facing) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.def = def;
            this.facing = facing;
        }

        Block block() {
            return world.getBlockAt(x, y, z);
        }

        Location center() {
            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }

        int height() {
            return wall ? 1 : def.height;
        }

        String model() {
            String m = variant.isEmpty() ? def.model : variant;
            if (def.connect) return m + "_" + mask;
            if (def.openable && open) return m + "_open";
            return m;
        }

        String line() {
            return def.id + "|" + x + "|" + y + "|" + z + "|" + facing.name() + "|" + (wall ? "w" : "f") + "|" + (variant.isEmpty() ? "-" : variant)
                    + "|" + (open ? 1 : 0) + "|" + (owner == null ? "-" : owner) + "|"
                    + (data.isEmpty() ? "-" : Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
        }
    }

    final JavaPlugin plugin;
    final String ns;
    final Map<String, DecorDef> defs = new LinkedHashMap<>();
    final NamespacedKey itemKey;
    private final NamespacedKey dataKey, tagKey;
    private final Map<UUID, Map<Long, Prop>> props = new HashMap<>();
    private final Map<UUID, Prop> byHitbox = new HashMap<>();
    private final Map<UUID, Prop> seats = new HashMap<>();
    final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final int breakHits;
    long tick;

    Decor(JavaPlugin plugin, String ns, List<DecorDef> list, int breakHits) {
        this.plugin = plugin;
        this.ns = ns;
        for (DecorDef d : list) defs.put(d.id, d);
        itemKey = new NamespacedKey(plugin, "decor");
        dataKey = new NamespacedKey(plugin, "decor_props");
        tagKey = new NamespacedKey(plugin, "decor_display");
        this.breakHits = Math.max(1, breakHits);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    Prop at(Block b) {
        Map<Long, Prop> m = props.get(b.getWorld().getUID());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    Set<Prop> all() {
        Set<Prop> s = new HashSet<>();
        for (Map<Long, Prop> m : props.values()) s.addAll(m.values());
        return s;
    }

    List<Prop> near(Location l, double r) {
        List<Prop> out = new ArrayList<>();
        for (Prop p : all()) if (p.world == l.getWorld() && p.center().distanceSquared(l) <= r * r) out.add(p);
        return out;
    }

    int count() {
        return all().size();
    }

    // ------------------------------------------------------------ items + recipes

    ItemStack item(DecorDef d, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(d.name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(d.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.text(plugin.getName(), NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, d.id);
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key(ns, d.connect ? d.model + "_0" : d.model));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        return it;
    }

    DecorDef defOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id == null ? null : defs.get(id);
    }

    List<ItemStack> allItems() {
        List<ItemStack> out = new ArrayList<>();
        for (DecorDef d : defs.values()) out.add(item(d, 1));
        return out;
    }

    @SuppressWarnings("unchecked")
    void registerRecipes() {
        unregisterRecipes();
        for (DecorDef d : defs.values()) {
            if (d.recipe.isEmpty()) continue;
            NamespacedKey k = new NamespacedKey(plugin, "decor_" + d.id);
            ShapelessRecipe r = new ShapelessRecipe(k, item(d, d.recipeAmount));
            for (Object o : d.recipe) {
                if (o instanceof Material mat) r.addIngredient(mat);
                else if (o instanceof org.bukkit.Tag<?> t) r.addIngredient(new RecipeChoice.MaterialChoice((org.bukkit.Tag<Material>) t));
                else if (o instanceof RecipeChoice c) r.addIngredient(c);
            }
            Bukkit.addRecipe(r, false);
            recipeKeys.add(k);
        }
    }

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    /** Prop items (paper underneath) never count as paper in other recipes. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))
                && k.getKey().getKey().startsWith("decor_")) return;
        for (ItemStack i : e.getInventory().getMatrix()) if (defOf(i) != null) e.getInventory().setResult(null);
    }

    // ------------------------------------------------------------ placing

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Prop hit = at(b);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && hit != null) {
            e.setCancelled(true);
            hit(hit, p);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        DecorDef d = defOf(e.getItem());
        if (hit != null && d == null) {
            e.setCancelled(true);
            use(hit, p, e.getItem());
            return;
        }
        if (d == null) return;
        e.setCancelled(true);
        BlockFace face = e.getBlockFace();
        boolean onWall = d.wall && face != BlockFace.UP && face != BlockFace.DOWN;
        Block target = b.isReplaceable() && at(b) == null ? b : b.getRelative(face);
        if (!onWall && !target.getRelative(BlockFace.DOWN).getType().isSolid() && at(target.getRelative(BlockFace.DOWN)) == null) {
            p.sendActionBar(Component.text("This needs solid ground.", NamedTextColor.GRAY));
            return;
        }
        BlockFace facing = onWall ? face : p.getFacing().getOppositeFace();
        place(p, e.getItem(), d, target, b, onWall, facing);
    }

    private void place(Player p, ItemStack hand, DecorDef d, Block target, Block against, boolean onWall, BlockFace facing) {
        int h = onWall ? 1 : d.height;
        for (int i = 0; i < h; i++) {
            Block part = target.getRelative(0, i, 0);
            if (!part.isReplaceable() || at(part) != null) return;
        }
        DecorDef.Base base = onWall && d.base == DecorDef.Base.SOLID ? DecorDef.Base.NONE : d.base;
        if (base == DecorDef.Base.SOLID
                && !target.getWorld().getNearbyEntities(BoundingBox.of(target).expand(0, h - 1, 0).shift(0, (h - 1) / 2.0, 0), en -> en instanceof LivingEntity).isEmpty()) return;
        BlockState old = target.getState();
        setBase(target, base, h, d.light);
        BlockPlaceEvent ev = new BlockPlaceEvent(target, old, against, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            for (int i = h - 1; i >= 1; i--) target.getRelative(0, i, 0).setType(Material.AIR, false);
            old.update(true, false);
            return;
        }
        Prop r = new Prop(target.getWorld(), target.getX(), target.getY(), target.getZ(), d, facing);
        r.wall = onWall;
        r.owner = p.getUniqueId();
        if (d.variants.length > 0) r.variant = d.variants[ThreadLocalRandom.current().nextInt(d.variants.length)];
        index(r);
        r.mask = mask(r);
        draw(r);
        refreshNeighbours(r);
        save(r);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        r.world.playSound(r.center(), d.placeSound, 0.8f, 1f);
        p.swingMainHand();
        if (d.use != null && d.use instanceof Placed pl) pl.placed(this, r, p);
    }

    /** Optional: a Use that also wants to hear about being placed (set up price, name, ...). */
    interface Placed {
        void placed(Decor d, Prop p, Player by);
    }

    private static void setBase(Block b, DecorDef.Base base, int h, int light) {
        for (int i = 0; i < h; i++) {
            Block part = b.getRelative(0, i, 0);
            if (base == DecorDef.Base.LIGHT && i == 0) {
                Light l = (Light) Material.LIGHT.createBlockData();
                l.setLevel(Math.max(1, Math.min(15, light)));
                part.setBlockData(l, false);
            } else if (base == DecorDef.Base.SOLID && light > 0 && i == h - 1) { // a post with a lamp on top
                Light l = (Light) Material.LIGHT.createBlockData();
                l.setLevel(Math.max(1, Math.min(15, light)));
                part.setBlockData(l, false);
            } else if (base == DecorDef.Base.SOLID) {
                part.setType(Material.BARRIER, false);
            }
        }
    }

    private void index(Prop r) {
        Map<Long, Prop> m = props.computeIfAbsent(r.world.getUID(), k -> new HashMap<>());
        for (int i = 0; i < r.height(); i++) m.put(key(r.x, r.y + i, r.z), r);
    }

    private void unindex(Prop r) {
        Map<Long, Prop> m = props.get(r.world.getUID());
        if (m != null) for (int i = 0; i < r.height(); i++) m.remove(key(r.x, r.y + i, r.z));
    }

    // ------------------------------------------------------------ connections (fences)

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private int mask(Prop r) {
        if (!r.def.connect) return 0;
        int m = 0;
        for (int i = 0; i < 4; i++) {
            Prop n = at(r.block().getRelative(SIDES[i]));
            if (n != null && n != r && n.def.group.equals(r.def.group)) m |= 1 << i;
        }
        return m;
    }

    private void refreshNeighbours(Prop r) {
        for (BlockFace f : SIDES) {
            Prop n = at(r.block().getRelative(f));
            if (n == null || n == r || !n.def.connect) continue;
            int m = mask(n);
            if (m != n.mask) {
                n.mask = m;
                draw(n);
            }
        }
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

    private Transformation transform(Prop r) {
        float ts = r.def.scale;
        float s = ts * 1.001f;
        Quaternionf q = new Quaternionf();
        // an item display shows the model turned 180 degrees about Y (north face towards the south), so every prop gets
        // a half turn on top of its facing: the model's front (north, -Z) then really points the way the prop faces
        q.rotateY((float) Math.toRadians(r.def.connect ? 180 : yaw(r.facing) + 180));
        Vector3f up = new Vector3f(0, (ts - 1) * 0.5f, 0);
        if (r.wall) up.add(new Vector3f(r.facing.getDirection().toVector3f()).mul(-0.0f));
        return new Transformation(up, q, new Vector3f(s, s, s), new Quaternionf());
    }

    void draw(Prop r) {
        erase(r);
        ItemStack look = new ItemStack(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key(ns, r.model()));
        r.display = r.world.spawn(r.center(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            d.setItemStack(look);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform(r));
        });
        boolean walkable = r.wall ? r.def.base == DecorDef.Base.SOLID || r.def.base == DecorDef.Base.NONE : r.def.base == DecorDef.Base.NONE;
        if (walkable || (r.def.openable && r.open)) {
            float w = r.def.hitW, hh = r.wall ? 1f : r.def.hitH;
            r.hitbox = r.world.spawn(new Location(r.world, r.x + 0.5, r.y, r.z + 0.5), Interaction.class, i -> {
                i.setPersistent(false);
                i.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                i.setInteractionWidth(w);
                i.setInteractionHeight(hh);
                i.setResponsive(true);
            });
            byHitbox.put(r.hitbox.getUniqueId(), r);
        }
    }

    private void erase(Prop r) {
        if (r.display != null) r.display.remove();
        if (r.hitbox != null) {
            byHitbox.remove(r.hitbox.getUniqueId());
            r.hitbox.remove();
        }
        r.display = null;
        r.hitbox = null;
    }

    void redraw(Prop r) {
        draw(r);
    }

    // ------------------------------------------------------------ open / close

    void open(Prop r, int ticks) {
        if (!r.def.openable) return;
        if (!r.open) {
            r.open = true;
            for (int i = 0; i < r.height(); i++) {
                Block part = r.world.getBlockAt(r.x, r.y + i, r.z);
                if (part.getType() == Material.BARRIER) part.setType(Material.AIR, false);
            }
            draw(r);
            r.world.playSound(r.center(), "minecraft:block.iron_door.open", 0.7f, 1.2f);
        }
        r.closeAt = tick + ticks;
    }

    void close(Prop r) {
        if (!r.open) return;
        for (Player pl : r.world.getNearbyPlayers(r.center(), 1.2)) { // never lock someone inside
            if (pl.getBoundingBox().overlaps(BoundingBox.of(r.block()).expand(0, r.height() - 1, 0))) {
                r.closeAt = tick + 10;
                return;
            }
        }
        r.open = false;
        if (r.def.base == DecorDef.Base.SOLID) for (int i = 0; i < r.height(); i++) r.world.getBlockAt(r.x, r.y + i, r.z).setType(Material.BARRIER, false);
        draw(r);
        r.world.playSound(r.center(), "minecraft:block.iron_door.close", 0.7f, 1.2f);
    }

    // ------------------------------------------------------------ use / seats

    private void use(Prop r, Player p, ItemStack hand) {
        if (r.def.use != null && r.def.use.use(this, r, p, hand)) return;
        if (r.def.seat) sit(r, p);
    }

    private void sit(Prop r, Player p) {
        if (p.isInsideVehicle()) return;
        for (Prop s : seats.values()) if (s == r) return; // taken
        Location at = r.center().add(0, r.def.seatY - 0.5, 0);
        at.setYaw(switch (r.facing) { case EAST -> -90; case SOUTH -> 0; case WEST -> 90; default -> 180; } + 180);
        ItemDisplay seat = r.world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        });
        seat.addPassenger(p);
        seats.put(seat.getUniqueId(), r);
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

    // ------------------------------------------------------------ breaking

    private boolean canBreak(Player p, Block b) {
        ProbeBreakEvent ev = new ProbeBreakEvent(b, p);
        plugin.getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    private void hit(Prop r, Player p) {
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (tick - r.lastHit > 40) r.hits = 0;
            r.lastHit = tick;
            if (++r.hits < breakHits) {
                r.world.playSound(r.center(), r.def.placeSound, 0.7f, 0.8f);
                return;
            }
        }
        if (!canBreak(p, r.block())) return;
        breakProp(r, p.getGameMode() != GameMode.CREATIVE);
    }

    void breakProp(Prop r, boolean drop) {
        unindex(r);
        erase(r);
        save(r);
        for (int i = 0; i < r.height(); i++) {
            Block part = r.world.getBlockAt(r.x, r.y + i, r.z);
            if (part.getType() == Material.BARRIER || part.getType() == Material.LIGHT) part.setType(Material.AIR, true);
        }
        if (drop) r.world.dropItemNaturally(r.center(), item(r.def, 1));
        for (UUID seat : new ArrayList<>(seats.keySet())) if (seats.get(seat) == r) {
            Entity en = Bukkit.getEntity(seat);
            if (en != null) {
                en.eject();
                en.remove();
            }
            seats.remove(seat);
        }
        refreshNeighbours(r);
        r.world.playSound(r.center(), r.def.placeSound, 1f, 0.7f);
        r.world.spawnParticle(Particle.CLOUD, r.center(), 6, 0.25, 0.25, 0.25, 0.01);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Prop r = byHitbox.get(e.getAttacked().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        hit(r, e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent e) {
        Prop r = byHitbox.get(e.getRightClicked().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) use(r, e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof ProbeBreakEvent) return;
        Prop r = at(e.getBlock());
        if (r != null) {
            e.setCancelled(true);
            breakProp(r, e.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        Prop above = at(e.getBlock().getRelative(BlockFace.UP)); // standing prop loses its ground
        if (above != null && !above.wall && above.y == e.getBlock().getY() + 1) breakProp(above, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (at(e.getBlockPlaced()) != null) e.setCancelled(true); // nothing may be built into a prop
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

    /** Saves the chunk the prop is in (props change rarely, so right away). */
    void save(Prop r) {
        saveChunk(r.world, r.x >> 4, r.z >> 4);
    }

    private void saveChunk(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx, cz)) return;
        Chunk c = w.getChunkAt(cx, cz);
        StringBuilder sb = new StringBuilder();
        Map<Long, Prop> m = props.get(w.getUID());
        Set<Prop> done = new HashSet<>();
        if (m != null) {
            for (Prop r : m.values()) {
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
        List<Prop> loaded = new ArrayList<>();
        for (String line : data.split("\n")) {
            try {
                String[] f = line.split("\\|");
                DecorDef d = defs.get(f[0]);
                if (d == null) continue;
                Prop r = new Prop(c.getWorld(), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), d, BlockFace.valueOf(f[4]));
                r.wall = f[5].equals("w");
                r.variant = f[6].equals("-") ? "" : f[6];
                r.open = f[7].equals("1");
                r.owner = f[8].equals("-") ? null : UUID.fromString(f[8]);
                r.data = f[9].equals("-") ? "" : new String(Base64.getDecoder().decode(f[9]), StandardCharsets.UTF_8);
                if (at(r.block()) != null) continue;
                index(r);
                loaded.add(r);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad prop entry in chunk " + c.getX() + "," + c.getZ());
            }
        }
        for (Prop r : loaded) {
            r.mask = mask(r);
            if (r.open) r.closeAt = tick + 20; // was open when the chunk unloaded: close again soon
            draw(r);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk c = e.getChunk();
        Map<Long, Prop> m = props.get(c.getWorld().getUID());
        if (m == null) return;
        Set<Prop> gone = new HashSet<>();
        for (Prop r : m.values()) if (r.x >> 4 == c.getX() && r.z >> 4 == c.getZ()) gone.add(r);
        for (Prop r : gone) {
            erase(r);
            unindex(r);
        }
    }

    void removeAllDisplays() {
        for (Map<Long, Prop> m : props.values()) for (Prop r : new HashSet<>(m.values())) erase(r);
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities())
                if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) e.remove();
    }

    // ------------------------------------------------------------ ticking (call every 10 game ticks)

    void tick(long gameTick) {
        tick = gameTick;
        long step = gameTick / 10;
        for (Prop r : all()) {
            if (r.display == null || !r.display.isValid()) continue;
            if (r.open && r.closeAt != 0 && tick >= r.closeAt) close(r);
            if (r.def.tick != null && step % Math.max(1, r.def.tickEvery) == 0 && !r.world.getNearbyPlayers(r.center(), 24).isEmpty()) {
                r.def.tick.tick(this, r, tick);
            }
        }
    }
}
