package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stairs and slabs of the decorative blocks, without changing any vanilla
 * block: a real vanilla stair/slab (stone bricks for stone/metal styles, oak
 * for wood styles) gives the shape, collision and corner logic, and an item
 * display with our model, a hair bigger, covers it. The display follows the
 * vanilla block's state (facing, half, straight/inner/outer, single/double),
 * re-checked whenever something is placed or broken next to it. Positions are
 * kept in the chunk's data ("x,y,z,id;..."), displays are not saved and come
 * back when the chunk loads.
 */
final class DecorShapes implements Listener {
    // the display is a hair bigger than the carrier block; 1.02 made neighbouring drawings
    // interpenetrate (z-fighting flicker along the seams, owner 13-11), 1.002 is the Relics value
    private static final float COVER = 1.002f;

    private record Shape(DecorType type, boolean stairs, ItemDisplay display) {}

    private final SteamworksPlugin plugin;
    private final NamespacedKey itemKey, chunkKey;
    private final Map<UUID, Map<Long, Shape>> shapes = new HashMap<>();
    private final Set<Block> pending = new LinkedHashSet<>();

    DecorShapes(SteamworksPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "decor_shape");
        chunkKey = new NamespacedKey(plugin, "decor_shapes");
    }

    // ------------------------------------------------------------ items

    ItemStack item(DecorType t, boolean stairs, int amount) {
        Material base = stairs ? (t.pickaxe ? Material.STONE_BRICK_STAIRS : Material.OAK_STAIRS)
                : (t.pickaxe ? Material.STONE_BRICK_SLAB : Material.OAK_SLAB);
        ItemStack item = new ItemStack(base, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName + (stairs ? " Stairs" : " Slab"), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Steamworks building block", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(new NamespacedKey("steamworks", t.id() + (stairs ? "_stairs" : "_slab")));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, t.id() + (stairs ? ":stairs" : ":slab"));
        item.setItemMeta(meta);
        return item;
    }

    /** {type, stairs?} of a decorative stair/slab item, or null. */
    private Object[] itemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String v = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (v == null) return null;
        String[] p = v.split(":");
        DecorType t = DecorType.byId(p[0]);
        return t == null ? null : new Object[]{t, p.length > 1 && p[1].equals("stairs")};
    }

    void registerRecipes() {
        for (DecorType t : DecorType.values()) {
            plugin.items().addRecipe("decor_" + t.id() + "_stairs", item(t, true, 4),
                    new Object[]{4, new String[]{"B  ", "BB ", "BBB"}, 'B', t});
            plugin.items().addRecipe("decor_" + t.id() + "_slab", item(t, false, 6),
                    new Object[]{6, new String[]{"BBB"}, 'B', t});
        }
    }

    // ------------------------------------------------------------ the look

    private static long key(Block b) {
        return KNode.key(b.getX(), b.getY(), b.getZ());
    }

    private Shape at(Block b) {
        Map<Long, Shape> m = shapes.get(b.getWorld().getUID());
        return m == null ? null : m.get(key(b));
    }

    /** Model id for the vanilla block's current state: a baked variant per orientation (the pack
     *  applies vanilla's own stair model table), so corners can never mis-rotate again. */
    private void refresh(Block b, Shape s) {
        if (s.display() == null || !s.display().isValid()) return;
        BlockData d = b.getBlockData();
        String model;
        if (s.stairs() && d instanceof Stairs st) {
            String shape = st.getShape() == Stairs.Shape.STRAIGHT ? "straight"
                    : st.getShape() == Stairs.Shape.INNER_LEFT ? "inner_L"
                    : st.getShape() == Stairs.Shape.INNER_RIGHT ? "inner_R"
                    : st.getShape() == Stairs.Shape.OUTER_LEFT ? "outer_L" : "outer_R";
            model = "_draw_" + shape + "_" + st.getFacing().name().toLowerCase(Locale.ROOT)
                    + "_" + (st.getHalf() == org.bukkit.block.data.Bisected.Half.TOP ? "top" : "bottom");
        } else if (!s.stairs() && d instanceof Slab sl) {
            model = switch (sl.getType()) {
                case TOP -> "_draw_slab_top";
                case DOUBLE -> "_draw_slab_double";
                default -> "_draw_slab_bottom";
            };
        } else {
            return; // the block is no longer what we drew (WorldEdit etc.) - removed on the next break/load
        }
        ItemStack look = new ItemStack(Material.PAPER);
        ItemMeta meta = look.getItemMeta();
        meta.setItemModel(new NamespacedKey("steamworks", s.type().id() + model));
        look.setItemMeta(meta);
        s.display().setItemStack(look);
        s.display().setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(COVER, COVER, COVER), new Quaternionf()));
    }

    private Shape spawn(Block b, DecorType t, boolean stairs) {
        ItemDisplay d = b.getWorld().spawn(b.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setViewRange(1.0f);
            e.getPersistentDataContainer().set(plugin.visuals().tag, PersistentDataType.BYTE, (byte) 1);
        });
        Shape s = new Shape(t, stairs, d);
        shapes.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>()).put(key(b), s);
        refresh(b, s);
        return s;
    }

    private void forget(Block b) {
        Map<Long, Shape> m = shapes.get(b.getWorld().getUID());
        Shape s = m == null ? null : m.remove(key(b));
        if (s != null && s.display() != null) s.display().remove();
    }

    /** Neighbours of a changed block get their stair shapes re-checked next tick. */
    private void around(Block b) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    Block n = b.getRelative(dx, dy, dz);
                    if (at(n) != null) pending.add(n);
                }
    }

    void tick() {
        if (pending.isEmpty()) return;
        List<Block> todo = new ArrayList<>(pending);
        pending.clear();
        for (Block b : todo) {
            Shape s = at(b);
            if (s != null) refresh(b, s);
        }
    }

    // ------------------------------------------------------------ saving

    private void save(Chunk c) {
        Map<Long, Shape> m = shapes.get(c.getWorld().getUID());
        StringBuilder sb = new StringBuilder();
        if (m != null) {
            for (Map.Entry<Long, Shape> e : m.entrySet()) {
                long k = e.getKey();
                int x = (int) (k >> 38), y = (int) (k << 52 >> 52), z = (int) (k << 26 >> 38);
                if (x >> 4 != c.getX() || z >> 4 != c.getZ()) continue;
                if (!sb.isEmpty()) sb.append(';');
                sb.append(x).append(',').append(y).append(',').append(z).append(',').append(e.getValue().type().id())
                        .append(',').append(e.getValue().stairs() ? 's' : 'l');
            }
        }
        if (sb.isEmpty()) c.getPersistentDataContainer().remove(chunkKey);
        else c.getPersistentDataContainer().set(chunkKey, PersistentDataType.STRING, sb.toString());
    }

    void loadChunk(Chunk c) {
        String data = c.getPersistentDataContainer().get(chunkKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return;
        World w = c.getWorld();
        boolean lost = false;
        for (String entry : data.split(";")) {
            String[] p = entry.split(",");
            try {
                Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                DecorType t = DecorType.byId(p[3]);
                boolean stairs = p[4].equals("s");
                boolean ok = t != null && (stairs ? b.getBlockData() instanceof Stairs : b.getBlockData() instanceof Slab);
                if (!ok) {
                    lost = true;
                    continue;
                }
                if (at(b) == null) spawn(b, t, stairs);
            } catch (RuntimeException e) {
                lost = true;
            }
        }
        if (lost) save(c);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (e.getChunk().isLoaded()) loadChunk(e.getChunk());
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Map<Long, Shape> m = shapes.get(e.getWorld().getUID());
        if (m == null) return;
        Chunk c = e.getChunk();
        m.entrySet().removeIf(en -> {
            long k = en.getKey();
            int x = (int) (k >> 38), z = (int) (k << 26 >> 38);
            if (x >> 4 != c.getX() || z >> 4 != c.getZ()) return false;
            if (en.getValue().display() != null) en.getValue().display().remove();
            return true;
        });
    }

    // ------------------------------------------------------------ players

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        Object[] it = itemType(e.getItemInHand());
        Shape here = at(b);
        if (here != null) {
            // a slab placed into one of our slabs: only the same block may make it double
            if (it == null || it[0] != here.type() || here.stairs()) e.setCancelled(true);
            else pending.add(b);
            return;
        }
        if (it == null) return;
        boolean stairs = (boolean) it[1];
        if (!stairs && e.getBlockReplacedState().getBlockData() instanceof Slab) {
            e.setCancelled(true); // never cover half of a vanilla slab
            return;
        }
        spawn(b, (DecorType) it[0], stairs);
        save(b.getChunk());
        around(b);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAnyPlace(BlockPlaceEvent e) {
        around(e.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Shape s = at(b);
        if (s == null) {
            around(b);
            return;
        }
        e.setDropItems(false);
        int count = !s.stairs() && b.getBlockData() instanceof Slab sl && sl.getType() == Slab.Type.DOUBLE ? 2 : 1;
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(s.type(), s.stairs(), count));
        forget(b);
        save(b.getChunk());
        around(b);
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
        Set<Chunk> touched = new HashSet<>();
        for (Block b : new ArrayList<>(blocks)) {
            Shape s = at(b);
            if (s == null) continue;
            blocks.remove(b);
            b.setType(Material.AIR, false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(s.type(), s.stairs(), 1));
            forget(b);
            touched.add(b.getChunk());
        }
        for (Chunk c : touched) save(c);
    }

    /** Pistons would leave the drawing behind: they don't move our stairs and slabs. */
    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) e.setCancelled(true);
    }

    /** "<id>_stairs" / "<id>_slab" for /sw give. */
    ItemStack itemById(String id, int amount) {
        String low = id.toLowerCase(Locale.ROOT);
        boolean stairs = low.endsWith("_stairs");
        if (!stairs && !low.endsWith("_slab")) return null;
        DecorType t = DecorType.byId(low.substring(0, low.lastIndexOf('_')));
        return t == null ? null : item(t, stairs, amount);
    }
}
