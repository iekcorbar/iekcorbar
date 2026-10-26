package com.iekcorbar.kura;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds networks and keeps them cached. A network starts at an Inventory Connector:
 * storage touching it (and touching that storage, or a trim) joins, cables carry it
 * further and Cable Connectors attach the container they face. Every member block is
 * indexed, so a block change next to a member only marks that one network for a
 * rebuild; nothing scans the world on a timer.
 */
final class Networks implements Listener {
    record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    /** May the search load this chunk (remote terminals)? */
    interface Loader {
        boolean load(World w, int cx, int cz);
    }

    private record Node(PartType.Kind kind, BlockFace facing) { }

    static final BlockFace[] FACES = {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST};

    private final KuraPlugin plugin;
    private final Map<Pos, Network> index = new HashMap<>();
    /** Distinct cached networks. */
    private final Set<Network> nets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Blocks recently searched without finding a connector: tick when that may be tried again. */
    private final Map<Pos, Long> noNetwork = new HashMap<>();
    private final Set<Material> storage = new HashSet<>();
    private final List<NamespacedKey> excluded = new ArrayList<>();
    final NamespacedKey cfgKey, cfgFilterKey, partKey;
    private int maxBlocks;

    Networks(KuraPlugin plugin) {
        this.plugin = plugin;
        cfgKey = new NamespacedKey(plugin, "cfg");
        cfgFilterKey = new NamespacedKey(plugin, "cfg_filter");
        partKey = new NamespacedKey(plugin, "part");
        load();
    }

    void load() {
        storage.clear();
        for (String pat : plugin.getConfig().getStringList("storage-blocks")) {
            String p = pat.toUpperCase(Locale.ROOT).replace("*", ".*");
            for (Material m : Material.values()) if (m.isBlock() && !m.isLegacy() && m.name().matches(p)) storage.add(m);
        }
        storage.remove(Material.ENDER_CHEST);
        excluded.clear();
        for (String k : plugin.getConfig().getStringList("excluded-container-keys")) {
            NamespacedKey key = NamespacedKey.fromString(k);
            if (key != null) excluded.add(key);
        }
        maxBlocks = Math.max(64, plugin.getConfig().getInt("network.max-blocks", 4096));
        clear();
    }

    void clear() {
        index.clear();
        nets.clear();
        noNetwork.clear();
    }

    boolean isStorageType(Material m) {
        return storage.contains(m);
    }

    /** A vanilla container we may use as storage (not one of our barrels, not a sake cask...). */
    boolean isStorage(Block b) {
        if (!storage.contains(b.getType())) return false;
        if (!(b.getState(false) instanceof TileState ts)) return false;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        if (pdc.has(partKey)) return false;
        for (NamespacedKey k : excluded) if (pdc.has(k)) return false;
        return true;
    }

    // ------------------------------------------------------------ container settings (Inventory Configurator)

    /** priority (-2..2), skip, keepLast - stored in the container's own data. */
    int[] settings(Block b) {
        if (!(b.getState(false) instanceof TileState ts)) return new int[]{0, 0, 0};
        String s = ts.getPersistentDataContainer().get(cfgKey, PersistentDataType.STRING);
        if (s == null) return new int[]{0, 0, 0};
        String[] f = s.split(",");
        try {
            return new int[]{Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])};
        } catch (Exception e) {
            return new int[]{0, 0, 0};
        }
    }

    org.bukkit.inventory.ItemStack settingsFilter(Block b) {
        if (!(b.getState(false) instanceof TileState ts)) return null;
        return Part.decode(ts.getPersistentDataContainer().get(cfgFilterKey, PersistentDataType.STRING));
    }

    void saveSettings(Block b, int[] s, org.bukkit.inventory.ItemStack filter) {
        if (!(b.getState() instanceof TileState ts)) return;
        if (s[0] == 0 && s[1] == 0 && s[2] == 0 && filter == null) {
            ts.getPersistentDataContainer().remove(cfgKey);
            ts.getPersistentDataContainer().remove(cfgFilterKey);
        } else {
            ts.getPersistentDataContainer().set(cfgKey, PersistentDataType.STRING, s[0] + "," + s[1] + "," + s[2]);
            if (filter == null) ts.getPersistentDataContainer().remove(cfgFilterKey);
            else ts.getPersistentDataContainer().set(cfgFilterKey, PersistentDataType.STRING, Part.encode(filter));
        }
        ts.update(true, false);
        touch(b);
    }

    // ------------------------------------------------------------ search

    private Node node(Block b) {
        Part p = plugin.parts().at(b);
        if (p != null) {
            return p.type.kind == PartType.Kind.DEVICE ? null : new Node(p.type.kind, p.facing);
        }
        if (isStorage(b) && settings(b)[1] == 0) return new Node(PartType.Kind.STORE, null);
        return null;
    }

    private static boolean half(Node x, Node y, BlockFace dir) {
        return switch (x.kind) {
            case CONNECTOR -> true;
            case STORE, TRIM -> y.kind == PartType.Kind.STORE || y.kind == PartType.Kind.TRIM || y.kind == PartType.Kind.CONNECTOR
                    || y.kind == PartType.Kind.CABLE_CONNECTOR;
            case CABLE -> y.kind == PartType.Kind.CABLE || y.kind == PartType.Kind.CABLE_CONNECTOR || y.kind == PartType.Kind.CONNECTOR;
            case CABLE_CONNECTOR -> y.kind == PartType.Kind.CABLE || y.kind == PartType.Kind.CABLE_CONNECTOR || y.kind == PartType.Kind.CONNECTOR
                    || (dir == x.facing && (y.kind == PartType.Kind.STORE || y.kind == PartType.Kind.TRIM));
            case DEVICE -> false;
        };
    }

    private static boolean links(Node a, Node b, BlockFace dir) {
        return half(a, b, dir) && half(b, a, dir.getOppositeFace());
    }

    /** Searches from {@code start}; the result has no roots when no connector was found. */
    private Network search(Block start, Loader loader) {
        Node first = node(start);
        if (first == null) return null;
        World w = start.getWorld();
        Network net = new Network(w.getUID());
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Map<Pos, Node> seen = new HashMap<>();
        Set<Location> invs = new HashSet<>();
        queue.add(start);
        seen.put(Pos.of(start), first);
        while (!queue.isEmpty()) {
            Block b = queue.poll();
            Pos pos = Pos.of(b);
            Node n = seen.get(pos);
            net.members.add(pos);
            net.chunks.add(Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4));
            if (n.kind == PartType.Kind.CONNECTOR) net.roots.add(pos);
            if (n.kind == PartType.Kind.STORE) addStore(net, b, invs);
            if (seen.size() >= maxBlocks) {
                net.truncated = true;
                continue;
            }
            for (BlockFace f : FACES) {
                int nx = b.getX() + f.getModX(), nz = b.getZ() + f.getModZ();
                if (!w.isChunkLoaded(nx >> 4, nz >> 4)) {
                    if (loader == null || !loader.load(w, nx >> 4, nz >> 4)) {
                        net.frontier.add(Chunk.getChunkKey(nx >> 4, nz >> 4));
                        continue;
                    }
                }
                Block nb = b.getRelative(f);
                Pos np = Pos.of(nb);
                if (seen.containsKey(np)) continue;
                Node m = node(nb);
                if (m == null || !links(n, m, f)) continue;
                seen.put(np, m);
                queue.add(nb);
            }
        }
        net.sortStores();
        return net;
    }

    private void addStore(Network net, Block b, Set<Location> invs) {
        Part p = plugin.parts().at(b);
        if (p != null) {
            if (p.type == PartType.FILING_CABINET) net.stores.add(plugin.parts().cabinetStore(p));
            return;
        }
        if (!(b.getState(false) instanceof org.bukkit.block.Container c)) return;
        Inventory inv = c.getInventory();
        Location key = inv.getLocation();
        if (key != null && !invs.add(key)) return; // other half of a double chest
        Store.Container s = new Store.Container(b, inv);
        int[] cfg = settings(b);
        s.priority = cfg[0];
        s.keepLast = cfg[2] == 1;
        s.filter = Filter.of(plugin.items(), settingsFilter(b));
        net.stores.add(s);
    }

    /** Builds (and indexes) the network containing {@code start}, if it reaches a connector. */
    Network build(Block start, Loader loader) {
        Network net = search(start, loader);
        if (net == null || net.roots.isEmpty()) {
            noNetwork.put(Pos.of(start), plugin.tick + 100);
            return null;
        }
        // replace whatever networks these blocks belonged to
        Set<Network> old = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Pos p : net.members) {
            Network o = index.put(p, net);
            if (o != null && o != net) old.add(o);
        }
        for (Network o : old) {
            for (Pos p : o.members) index.remove(p, o);
            nets.remove(o);
        }
        nets.add(net);
        for (Pos p : net.members) noNetwork.remove(p);
        return net;
    }

    /** The network {@code b} is a member of (rebuilt first if something changed), or null. */
    Network at(Block b, Loader loader) {
        Network net = index.get(Pos.of(b));
        if (net != null && !net.dirty) return net;
        if (net != null) {
            for (Pos p : net.members) index.remove(p, net);
            nets.remove(net);
            for (Pos root : net.roots) {
                Block rb = b.getWorld().getBlockAt(root.x(), root.y(), root.z());
                if (!b.getWorld().isChunkLoaded(root.x() >> 4, root.z() >> 4)) continue;
                Network fresh = build(rb, loader);
                if (fresh != null && fresh.members.contains(Pos.of(b))) return fresh;
            }
        }
        Long until = noNetwork.get(Pos.of(b));
        if (until != null && until > plugin.tick) return null;
        if (node(b) == null) return null;
        return build(b, loader);
    }

    /**
     * The network a device (terminal, hopper side, emitter, interface) uses: a
     * network touching it (the {@code preferred} side first), else the single
     * container it sits against.
     */
    Network around(Block device, BlockFace preferred, Loader loader) {
        List<BlockFace> order = new ArrayList<>(6);
        if (preferred != null) order.add(preferred);
        for (BlockFace f : FACES) if (f != preferred) order.add(f);
        for (BlockFace f : order) {
            Block n = device.getRelative(f);
            if (!loaded(n)) continue;
            Network net = at(n, loader);
            if (net != null) return net;
        }
        for (BlockFace f : order) {
            Block n = device.getRelative(f);
            if (!loaded(n)) continue;
            Network single = single(n);
            if (single != null) return single;
        }
        return null;
    }

    /** One container (or Filing Cabinet) on its own, not indexed. */
    Network single(Block b) {
        Node n = node(b);
        if (n == null || n.kind != PartType.Kind.STORE) return null;
        Network net = new Network(b.getWorld().getUID());
        net.members.add(Pos.of(b));
        addStore(net, b, new HashSet<>());
        return net.stores.isEmpty() ? null : net;
    }

    private static boolean loaded(Block b) {
        return b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4);
    }

    // ------------------------------------------------------------ change tracking

    /** Something changed at {@code b}: the networks around it need a rebuild. */
    void touch(Block b) {
        UUID w = b.getWorld().getUID();
        mark(new Pos(w, b.getX(), b.getY(), b.getZ()));
        for (BlockFace f : FACES) mark(new Pos(w, b.getX() + f.getModX(), b.getY() + f.getModY(), b.getZ() + f.getModZ()));
    }

    private void mark(Pos p) {
        Network n = index.get(p);
        if (n != null) n.dirty = true;
        noNetwork.remove(p);
    }

    private boolean relevant(Block b) {
        return storage.contains(b.getType()) || plugin.parts().at(b) != null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        if (relevant(e.getBlockPlaced())) touch(e.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof KuraPlugin.ProbeBreakEvent) return;
        if (relevant(e.getBlock())) touch(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBurn(BlockBurnEvent e) {
        if (relevant(e.getBlock())) touch(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) if (relevant(b)) touch(b);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) if (relevant(b)) touch(b);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        chunkChanged(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        chunkChanged(e.getChunk());
    }

    private void chunkChanged(Chunk c) {
        if (nets.isEmpty()) return;
        UUID w = c.getWorld().getUID();
        long key = c.getChunkKey();
        for (Network n : nets) {
            if (n.world.equals(w) && (n.chunks.contains(key) || n.frontier.contains(key))) n.dirty = true;
        }
    }

    /** Distinct cached networks (for /kura list). */
    int cachedNetworks() {
        return nets.size();
    }

}
