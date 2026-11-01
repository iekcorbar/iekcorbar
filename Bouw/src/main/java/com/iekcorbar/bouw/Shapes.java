package com.iekcorbar.bouw;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The block lines: a whole family of building pieces per material (block, stairs, slab, wall, fence, gate, trapdoor,
 * door, button, pressure plate). Each piece is a real vanilla block (stone bricks / oak planks / ...) - so placing,
 * collision, redstone, doors opening, fence joining all come from the game itself - covered by item displays that
 * show our models (a hair bigger than the vanilla shape). The displays follow the vanilla block's state and are
 * re-checked a few times a second near players. Positions live in the chunk's data ("x,y,z,line,piece;...").
 */
final class Shapes implements Listener {
    enum Piece { BLOCK, STAIRS, SLAB, WALL, FENCE, GATE, TRAPDOOR, DOOR, BUTTON, PLATE;
        String id() { return name().toLowerCase(Locale.ROOT); }
        static Piece of(String s) { for (Piece p : values()) if (p.id().equals(s)) return p; return null; }
        String label() { return switch (this) { case BLOCK -> "Block"; case STAIRS -> "Stairs"; case SLAB -> "Slab"; case WALL -> "Wall";
            case FENCE -> "Fence"; case GATE -> "Fence Gate"; case TRAPDOOR -> "Trapdoor"; case DOOR -> "Door"; case BUTTON -> "Button"; case PLATE -> "Pressure Plate"; }; }
    }

    record Line(String id, String name, boolean stone, Material a, Material b) {}

    private static final class Shape {
        final Line line;
        final Piece piece;
        final UUID world;
        final int x, y, z;
        final List<ItemDisplay> displays = new ArrayList<>();
        String sig = "";

        Shape(Line line, Piece piece, World w, int x, int y, int z) {
            this.line = line;
            this.piece = piece;
            this.world = w.getUID();
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record Part(String model, Quaternionf rot, float dy) {}

    private final BouwPlugin plugin;
    private final NamespacedKey itemKey, chunkKey, tagKey;
    final Map<String, Line> lines = new LinkedHashMap<>();
    final List<Recipe> recipes = new ArrayList<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final Map<UUID, Map<Long, Shape>> shapes = new HashMap<>();
    private float cover = 1.002f;
    private float flip = 180f;

    Shapes(BouwPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "piece");
        chunkKey = new NamespacedKey(plugin, "shapes");
        tagKey = new NamespacedKey(plugin, "cover");
        loadLines();
        reloadConfig();
        registerRecipes();
    }

    void reloadConfig() {
        flip = (float) plugin.getConfig().getDouble("blocks.cover-flip-degrees", 180);
        cover = (float) plugin.getConfig().getDouble("blocks.cover-scale", 1.002);
        for (Map<Long, Shape> m : shapes.values()) for (Shape s : m.values()) s.sig = "";
    }

    private void loadLines() {
        try (var in = plugin.getResource("lines.json")) {
            JsonObject root = new Gson().fromJson(new InputStreamReader(in), JsonObject.class);
            for (JsonElement e : root.getAsJsonArray("lines")) {
                JsonObject o = e.getAsJsonObject();
                Material a = Material.matchMaterial(o.get("a").getAsString()), b = Material.matchMaterial(o.get("b").getAsString());
                lines.put(o.get("id").getAsString(), new Line(o.get("id").getAsString(), o.get("name").getAsString(),
                        o.get("kind").getAsString().equals("stone"), a == null ? Material.STONE : a, b == null ? Material.STICK : b));
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("lines.json missing or broken: " + ex);
        }
    }

    // ------------------------------------------------------------ items

    static Material base(Line l, Piece p) {
        boolean s = l.stone();
        return switch (p) {
            case BLOCK -> s ? Material.STONE_BRICKS : Material.OAK_PLANKS;
            case STAIRS -> s ? Material.STONE_BRICK_STAIRS : Material.OAK_STAIRS;
            case SLAB -> s ? Material.STONE_BRICK_SLAB : Material.OAK_SLAB;
            case WALL -> Material.STONE_BRICK_WALL;
            case FENCE -> s ? Material.NETHER_BRICK_FENCE : Material.OAK_FENCE;
            case GATE -> Material.OAK_FENCE_GATE;
            case TRAPDOOR -> Material.OAK_TRAPDOOR;
            case DOOR -> Material.OAK_DOOR;
            case BUTTON -> s ? Material.STONE_BUTTON : Material.OAK_BUTTON;
            case PLATE -> s ? Material.STONE_PRESSURE_PLATE : Material.OAK_PRESSURE_PLATE;
        };
    }

    ItemStack item(Line l, Piece p, int amount) {
        ItemStack it = new ItemStack(base(l, p), Math.max(1, amount));
        it.editMeta(m -> {
            m.displayName(Component.text(l.name() + " " + p.label(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Bouw building block", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
            m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, l.id() + ":" + p.id());
        });
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("bouw", l.id() + "_" + p.id()));
        return it;
    }

    /** "<line>_<piece>" -> item (for /bouw give), or null. */
    ItemStack itemById(String id, int amount) {
        for (Line l : lines.values()) {
            if (!id.startsWith(l.id() + "_")) continue;
            Piece p = Piece.of(id.substring(l.id().length() + 1));
            if (p != null) return item(l, p, amount);
        }
        return null;
    }

    List<ItemStack> allItems() {
        List<ItemStack> out = new ArrayList<>();
        for (Line l : lines.values()) for (Piece p : Piece.values()) out.add(item(l, p, 1));
        return out;
    }

    /** {Line, Piece} of one of our piece items, or null. */
    private Object[] parse(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String v = it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (v == null) return null;
        String[] p = v.split(":");
        Line l = lines.get(p[0]);
        Piece pc = p.length > 1 ? Piece.of(p[1]) : null;
        return l == null || pc == null ? null : new Object[]{l, pc};
    }

    boolean isOurs(ItemStack it) {
        return parse(it) != null;
    }

    // ------------------------------------------------------------ recipes

    private void add(String id, Recipe r, NamespacedKey k) {
        org.bukkit.Bukkit.addRecipe(r, false);
        recipes.add(r);
        recipeKeys.add(k);
    }

    private void shaped(Line l, Piece p, int amount, String[] shape, Object... ing) {
        NamespacedKey k = new NamespacedKey(plugin, l.id() + "_" + p.id());
        ShapedRecipe r = new ShapedRecipe(k, item(l, p, amount));
        r.shape(shape);
        for (int i = 0; i < ing.length; i += 2) {
            char c = (Character) ing[i];
            Object o = ing[i + 1];
            if (o instanceof Material m) r.setIngredient(c, m);
            else r.setIngredient(c, (RecipeChoice) o);
        }
        add(l.id(), r, k);
    }

    void registerRecipes() {
        unregisterRecipes();
        for (Line l : lines.values()) {
            RecipeChoice x = new RecipeChoice.ExactChoice(item(l, Piece.BLOCK, 1));
            Material s = l.stone() ? Material.IRON_NUGGET : Material.STICK;
            shaped(l, Piece.BLOCK, 4, new String[]{"AA", "AB"}, 'A', l.a(), 'B', l.b());
            shaped(l, Piece.STAIRS, 4, new String[]{"X  ", "XX ", "XXX"}, 'X', x);
            shaped(l, Piece.SLAB, 6, new String[]{"XXX"}, 'X', x);
            shaped(l, Piece.WALL, 6, new String[]{"XXX", "XXX"}, 'X', x);
            shaped(l, Piece.FENCE, 3, new String[]{"XSX", "XSX"}, 'X', x, 'S', s);
            shaped(l, Piece.GATE, 1, new String[]{"SXS", "SXS"}, 'X', x, 'S', s);
            shaped(l, Piece.TRAPDOOR, 2, new String[]{"XXX", "XXX"}, 'X', x);
            shaped(l, Piece.DOOR, 3, new String[]{"XX", "XX", "XX"}, 'X', x);
            shaped(l, Piece.PLATE, 1, new String[]{"XX"}, 'X', x);
            NamespacedKey k = new NamespacedKey(plugin, l.id() + "_button");
            ShapelessRecipe b = new ShapelessRecipe(k, item(l, Piece.BUTTON, 1));
            b.addIngredient(x);
            add(l.id(), b, k);
        }
    }

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) org.bukkit.Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
        recipes.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    /** Our pieces are stone bricks / planks underneath: keep them out of every recipe that is not ours. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) return;
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isOurs(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------ the look

    private static int yawOf(BlockFace f) {
        return switch (f) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    private Quaternionf yaw(int yDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yDeg + flip));
    }

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private List<Part> parts(Shape s, Block b) {
        List<Part> out = new ArrayList<>();
        BlockData d = b.getBlockData();
        switch (s.piece) {
            case BLOCK -> out.add(new Part("block", yaw(0), 0));
            case SLAB -> {
                if (!(d instanceof Slab sl)) return null;
                out.add(new Part(switch (sl.getType()) { case TOP -> "slab_top"; case DOUBLE -> "block"; default -> "slab_bottom"; }, yaw(0), 0));
            }
            case STAIRS -> {
                if (!(d instanceof Stairs st)) return null;
                Stairs.Shape sh = st.getShape();
                String m = switch (sh) { case STRAIGHT -> "stairs_straight_bottom"; case INNER_LEFT, INNER_RIGHT -> "stairs_inner_bottom"; default -> "stairs_outer_bottom"; };
                int y = switch (st.getFacing()) { case SOUTH -> 90; case WEST -> 180; case NORTH -> 270; default -> 0; };
                boolean top = st.getHalf() == Bisected.Half.TOP;
                boolean left = sh == Stairs.Shape.INNER_LEFT || sh == Stairs.Shape.OUTER_LEFT;
                boolean right = sh == Stairs.Shape.INNER_RIGHT || sh == Stairs.Shape.OUTER_RIGHT;
                if (!top && left) y -= 90;
                if (top && right) y += 90;
                Quaternionf q = yaw(y);
                if (top) q.rotateX((float) Math.PI);
                out.add(new Part(m, q, 0));
            }
            case WALL -> {
                if (!(d instanceof Wall w)) return null;
                if (w.isUp()) out.add(new Part("wall_post", yaw(0), 0));
                for (BlockFace f : SIDES) {
                    Wall.Height h = w.getHeight(f);
                    if (h == Wall.Height.NONE) continue;
                    out.add(new Part(h == Wall.Height.TALL ? "wall_arm_tall" : "wall_arm_low", yaw(yawOf(f)), 0));
                }
            }
            case FENCE -> {
                if (!(d instanceof Fence fe)) return null;
                out.add(new Part("fence_post", yaw(0), 0));
                for (BlockFace f : SIDES) if (fe.hasFace(f)) out.add(new Part("fence_arm", yaw(yawOf(f)), 0));
            }
            case GATE -> {
                if (!(d instanceof Gate g)) return null;
                out.add(new Part(g.isOpen() ? "gate_open" : "gate", yaw(yawOf(g.getFacing())), g.isInWall() ? -0.1875f : 0));
            }
            case TRAPDOOR -> {
                if (!(d instanceof org.bukkit.block.data.type.TrapDoor t)) return null;
                if (t.isOpen()) out.add(new Part("trapdoor_open", yaw(yawOf(t.getFacing())), 0));
                else out.add(new Part(t.getHalf() == Bisected.Half.TOP ? "trapdoor_top" : "trapdoor_bottom", yaw(0), 0));
            }
            case DOOR -> {
                if (!(d instanceof Door dr)) return null;
                int y = switch (dr.getFacing()) { case SOUTH -> 90; case WEST -> 180; case NORTH -> 270; default -> 0; };
                String m = !dr.isOpen() ? "door" : dr.getHinge() == Door.Hinge.LEFT ? "door_open_left" : "door_open_right";
                out.add(new Part(m, yaw(y), 0));
            }
            case BUTTON -> {
                if (!(d instanceof FaceAttachable fa) || !(d instanceof Directional dir) || !(d instanceof Powerable po)) return null;
                Quaternionf q = yaw(yawOf(dir.getFacing()));
                switch (fa.getAttachedFace()) {
                    case WALL -> q.rotateX((float) Math.toRadians(-90));
                    case CEILING -> q.rotateX((float) Math.PI);
                    default -> { }
                }
                out.add(new Part(po.isPowered() ? "button_pressed" : "button", q, 0));
            }
            case PLATE -> {
                if (!(d instanceof Powerable po)) return null;
                out.add(new Part(po.isPowered() ? "plate_pressed" : "plate", yaw(0), 0));
            }
        }
        return out;
    }

    private void draw(Shape s, Block b) {
        String sig = b.getBlockData().getAsString();
        if (sig.equals(s.sig) && !s.displays.isEmpty() && s.displays.stream().allMatch(ItemDisplay::isValid)) return;
        List<Part> ps = parts(s, b);
        if (ps == null) return;
        s.sig = sig;
        for (ItemDisplay d : s.displays) d.remove();
        s.displays.clear();
        for (Part p : ps) {
            ItemStack look = new ItemStack(Material.PAPER);
            look.setData(DataComponentTypes.ITEM_MODEL, Key.key("bouw", s.line.id() + "_c_" + p.model()));
            ItemDisplay d = b.getWorld().spawn(b.getLocation().add(0.5, 0.5 + p.dy(), 0.5), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(look);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setViewRange(1.0f);
                e.setTransformation(new Transformation(new Vector3f(), p.rot(), new Vector3f(cover, cover, cover), new Quaternionf()));
                e.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            });
            s.displays.add(d);
        }
    }

    private Block blockOf(Shape s) {
        World w = plugin.getServer().getWorld(s.world);
        return w == null ? null : w.getBlockAt(s.x, s.y, s.z);
    }

    // ------------------------------------------------------------ registry

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private Shape at(Block b) {
        Map<Long, Shape> m = shapes.get(b.getWorld().getUID());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    private Shape put(Block b, Line l, Piece p) {
        Shape s = new Shape(l, p, b.getWorld(), b.getX(), b.getY(), b.getZ());
        shapes.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>()).put(key(s.x, s.y, s.z), s);
        draw(s, b);
        return s;
    }

    private void forget(Block b) {
        Map<Long, Shape> m = shapes.get(b.getWorld().getUID());
        Shape s = m == null ? null : m.remove(key(b.getX(), b.getY(), b.getZ()));
        if (s != null) for (ItemDisplay d : s.displays) d.remove();
    }

    /** Poll: redraw shapes near players whose vanilla state changed; drop shapes whose block is gone. */
    void tick() {
        Set<Chunk> dirty = new HashSet<>();
        for (World w : plugin.getServer().getWorlds()) {
            Map<Long, Shape> m = shapes.get(w.getUID());
            if (m == null || m.isEmpty() || w.getPlayers().isEmpty()) continue;
            List<Shape> gone = new ArrayList<>();
            for (Shape s : new ArrayList<>(m.values())) {
                boolean near = false;
                for (Player p : w.getPlayers()) {
                    double dx = p.getLocation().getX() - s.x, dz = p.getLocation().getZ() - s.z;
                    if (dx * dx + dz * dz < 64 * 64) { near = true; break; }
                }
                if (!near || !w.isChunkLoaded(s.x >> 4, s.z >> 4)) continue;
                Block b = w.getBlockAt(s.x, s.y, s.z);
                if (b.getType() != base(s.line, s.piece)) {
                    gone.add(s);
                    continue;
                }
                draw(s, b);
            }
            for (Shape s : gone) {
                forget(w.getBlockAt(s.x, s.y, s.z));
                dirty.add(w.getChunkAt(s.x >> 4, s.z >> 4));
            }
        }
        for (Chunk c : dirty) save(c);
    }

    // ------------------------------------------------------------ saving

    private void save(Chunk c) {
        Map<Long, Shape> m = shapes.get(c.getWorld().getUID());
        StringBuilder sb = new StringBuilder();
        if (m != null) {
            for (Shape s : m.values()) {
                if (s.x >> 4 != c.getX() || s.z >> 4 != c.getZ()) continue;
                if (!sb.isEmpty()) sb.append(';');
                sb.append(s.x).append(',').append(s.y).append(',').append(s.z).append(',').append(s.line.id()).append(',').append(s.piece.id());
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
                Line l = lines.get(p[3]);
                Piece pc = Piece.of(p[4]);
                if (l == null || pc == null || b.getType() != base(l, pc)) {
                    lost = true;
                    continue;
                }
                if (at(b) == null) put(b, l, pc);
            } catch (RuntimeException e) {
                lost = true;
            }
        }
        if (lost) save(c);
    }

    void loadAll() {
        for (World w : plugin.getServer().getWorlds()) for (Chunk c : w.getLoadedChunks()) loadChunk(c);
    }

    void removeAllDisplays() {
        for (Map<Long, Shape> m : shapes.values()) for (Shape s : m.values()) for (ItemDisplay d : s.displays) d.remove();
        shapes.clear();
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
        m.values().removeIf(s -> {
            if (s.x >> 4 != c.getX() || s.z >> 4 != c.getZ()) return false;
            for (ItemDisplay d : s.displays) d.remove();
            return true;
        });
    }

    // ------------------------------------------------------------ players

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        Object[] it = parse(e.getItemInHand());
        Shape here = at(b);
        if (here != null) {
            // a slab put into one of our slabs: only the same line may make it double
            if (it == null || it[0] != here.line || here.piece != Piece.SLAB || it[1] != Piece.SLAB) e.setCancelled(true);
            return;
        }
        if (it == null) return;
        Line l = (Line) it[0];
        Piece p = (Piece) it[1];
        if (p == Piece.SLAB && e.getBlockReplacedState().getBlockData() instanceof Slab) {
            e.setCancelled(true);          // never cover half of a vanilla slab
            return;
        }
        put(b, l, p);
        if (p == Piece.DOOR) {             // the upper half exists a moment later
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Block up = b.getRelative(BlockFace.UP);
                if (up.getBlockData() instanceof Door && at(up) == null) put(up, l, Piece.DOOR);
                save(b.getChunk());
            });
        }
        save(b.getChunk());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Shape s = at(b);
        if (s == null) return;
        e.setDropItems(false);
        int count = s.piece == Piece.SLAB && b.getBlockData() instanceof Slab sl && sl.getType() == Slab.Type.DOUBLE ? 2 : 1;
        boolean drop = e.getPlayer().getGameMode() != GameMode.CREATIVE;
        if (s.piece == Piece.DOOR && b.getBlockData() instanceof Door dr) {
            Block other = b.getRelative(dr.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
            forget(other);                  // one drop for the whole door, paid out below
            save(other.getChunk());
        }
        if (drop) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(s.line, s.piece, count));
        forget(b);
        save(b.getChunk());
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
            if (!(s.piece == Piece.DOOR && b.getRelative(BlockFace.DOWN).getBlockData() instanceof Door))
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(s.line, s.piece, 1));
            forget(b);
            touched.add(b.getChunk());
        }
        for (Chunk c : touched) save(c);
    }

    /** Pistons would leave the drawing behind: they don't move our pieces. */
    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) e.setCancelled(true);
    }
}
