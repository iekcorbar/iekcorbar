package com.iekcorbar.kura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Copper golem couriers: right-click a copper golem with an Inventory Configurator and it is bound to the nearest
 * Inventory Interface (the home). From then on it fetches items from the supply points around the home and carries them
 * into the storage network behind that interface. Supply points are copper chests and - with Steamworks installed -
 * the containers that Steamworks machines deliver into (under a steam hammer, under/beside a kettle or millstone, at
 * the end of a conveyor belt). The owner has to be online and allowed to break the supply container (protection
 * plugins are asked with a probe event). Searching is done on the tile entities of the nearby chunks, at most every
 * five seconds per idle golem. The golem's own vanilla chest sorting is left alone for golems that are not bound.
 */
final class Couriers implements Listener {
    private enum Stage { IDLE, FETCH, DELIVER }

    /** Owners who already heard the joke line this session. */
    private final java.util.Set<UUID> dobbied = new java.util.HashSet<>();

    private static final class Courier {
        final CopperGolem golem;
        final UUID owner;
        final Location home;
        Stage stage = Stage.IDLE;
        Block source;
        long since, nextSearch;
        int failures;
        /** Optional: only these items may be carried (Item/Tag/Polymorphic Filter or a plain item). */
        ItemStack filterItem;
        Filter filter;

        Courier(CopperGolem golem, UUID owner, Location home) {
            this.golem = golem;
            this.owner = owner;
            this.home = home;
        }
    }

    private final KuraPlugin plugin;
    private final NamespacedKey homeKey, ownerKey, filterKey;
    private final Map<UUID, Courier> couriers = new HashMap<>();

    Couriers(KuraPlugin plugin) {
        this.plugin = plugin;
        this.homeKey = new NamespacedKey(plugin, "courier_home");
        this.ownerKey = new NamespacedKey(plugin, "courier_owner");
        this.filterKey = new NamespacedKey(plugin, "courier_filter");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("couriers.enabled", true);
    }

    private int radius() {
        return Math.max(4, Math.min(24, plugin.getConfig().getInt("couriers.radius", 12)));
    }

    // ------------------------------------------------------------ binding

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!enabled() || e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof CopperGolem g)) return;
        Player p = e.getPlayer();
        if (plugin.items().typeOf(p.getInventory().getItemInMainHand()) != KItem.CONFIGURATOR) return;
        e.setCancelled(true);
        var pdc = g.getPersistentDataContainer();
        if (pdc.has(homeKey, PersistentDataType.STRING)) {
            if (p.isSneaking()) {   // sneak + click a bound courier: choose what it may carry
                plugin.menus().openCourierSettings(p, g.getUniqueId(),
                    Part.decode(pdc.get(filterKey, PersistentDataType.STRING)));
                return;
            }
            Courier c = couriers.remove(g.getUniqueId());
            pdc.remove(homeKey);
            pdc.remove(ownerKey);
            pdc.remove(filterKey);
            if (c != null) drop(c);
            p.sendActionBar(Component.text("The copper golem is free again.", NamedTextColor.GRAY));
            return;
        }
        Part home = nearestInterface(g.getLocation());
        if (home == null) {
            p.sendActionBar(Component.text("No Inventory Interface within " + radius() + " blocks of this golem.", NamedTextColor.RED));
            return;
        }
        if (plugin.parts().networkOf(home, null) == null) {
            p.sendActionBar(Component.text("That Inventory Interface is not connected to a network.", NamedTextColor.RED));
            return;
        }
        Location at = new Location(home.world, home.x, home.y, home.z);
        pdc.set(homeKey, PersistentDataType.STRING, home.world.getUID() + "|" + home.x + "|" + home.y + "|" + home.z);
        pdc.set(ownerKey, PersistentDataType.STRING, p.getUniqueId().toString());
        couriers.put(g.getUniqueId(), new Courier(g, p.getUniqueId(), at));
        p.playSound(g.getLocation(), Sound.BLOCK_COPPER_PLACE, 1f, 1.3f);
        plugin.adv().grant(p, "courier");
        p.sendActionBar(Component.text("Courier bound to the interface at " + home.x + " " + home.y + " " + home.z
                + ". It fetches from copper chests and Steamworks machine outputs. Sneak + right-click: what to carry; right-click again: free it.", NamedTextColor.GREEN));
    }

    /** Choose what a bound courier may carry (null = everything); kept in the golem's own data. */
    void setFilter(UUID golem, ItemStack filter) {
        Courier c = couriers.get(golem);
        if (c == null) return;
        c.filterItem = filter == null ? null : filter.asOne();
        c.filter = Filter.of(plugin.items(), c.filterItem);
        var pdc = c.golem.getPersistentDataContainer();
        if (c.filterItem == null) pdc.remove(filterKey);
        else pdc.set(filterKey, PersistentDataType.STRING, Part.encode(c.filterItem));
    }

    private Part nearestInterface(Location from) {
        Part best = null;
        double bd = Double.MAX_VALUE;
        int r = radius();
        World w = from.getWorld();
        for (int cx = (from.getBlockX() - r) >> 4; cx <= (from.getBlockX() + r) >> 4; cx++)
            for (int cz = (from.getBlockZ() - r) >> 4; cz <= (from.getBlockZ() + r) >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                for (BlockState bs : w.getChunkAt(cx, cz).getTileEntities(b -> b.getType() == Material.BARREL, false)) {
                    Part p = plugin.parts().at(bs.getBlock());
                    if (p == null || p.type != PartType.INTERFACE) continue;
                    double d = bs.getLocation().distanceSquared(from);
                    if (d <= r * r && d < bd) {
                        bd = d;
                        best = p;
                    }
                }
            }
        return best;
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en instanceof CopperGolem g) restore(g);
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) if (en instanceof CopperGolem g) couriers.remove(g.getUniqueId());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof CopperGolem g) {
            couriers.remove(g.getUniqueId());   // the held item drops with the golem as usual
        }
    }

    void loadAll() {
        for (World w : Bukkit.getWorlds()) for (CopperGolem g : w.getEntitiesByClass(CopperGolem.class)) restore(g);
    }

    private void restore(CopperGolem g) {
        var pdc = g.getPersistentDataContainer();
        String h = pdc.get(homeKey, PersistentDataType.STRING), o = pdc.get(ownerKey, PersistentDataType.STRING);
        if (h == null || o == null) return;
        try {
            String[] f = h.split("\\|");
            World w = Bukkit.getWorld(UUID.fromString(f[0]));
            if (w == null) return;
            Courier c = new Courier(g, UUID.fromString(o),
                    new Location(w, Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])));
            c.filterItem = Part.decode(pdc.get(filterKey, PersistentDataType.STRING));
            c.filter = Filter.of(plugin.items(), c.filterItem);
            couriers.put(g.getUniqueId(), c);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Bad courier data on a copper golem: " + h);
        }
    }

    // ------------------------------------------------------------ work (every 20 ticks)

    void tick(long tick) {
        if (!enabled() || couriers.isEmpty()) return;
        for (Courier c : new ArrayList<>(couriers.values())) {
            if (!c.golem.isValid()) {
                couriers.remove(c.golem.getUniqueId());
                continue;
            }
            try {
                step(c, tick);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Courier step failed: " + ex);
                reset(c);
            }
        }
    }

    private void reset(Courier c) {
        c.stage = Stage.IDLE;
        c.source = null;
        c.failures = 0;
    }

    private void step(Courier c, long tick) {
        CopperGolem g = c.golem;
        World w = g.getWorld();
        if (c.home.getWorld() != w) return;
        Player owner = Bukkit.getPlayer(c.owner);
        if (owner == null) return;   // the owner's build rights are checked live: no owner online, no work
        Part home = plugin.parts().at(c.home.getBlock());
        if (home == null || home.type != PartType.INTERFACE) return;
        ItemStack held = g.getEquipment().getItemInMainHand();
        boolean carrying = held != null && !held.isEmpty();
        if (c.stage != Stage.IDLE && tick - c.since > 600) {   // stuck for half a minute: give up on this trip
            drop(c);
            reset(c);
        }
        switch (c.stage) {
            case IDLE -> {
                if (carrying) {
                    c.stage = Stage.DELIVER;
                    c.since = tick;
                    return;
                }
                if (tick < c.nextSearch) return;
                c.nextSearch = tick + 100;
                Block src = findSource(c, owner);
                if (src == null) return;
                c.source = src;
                c.stage = Stage.FETCH;
                c.since = tick;
                walk(g, src.getLocation().add(0.5, 0, 0.5));
            }
            case FETCH -> {
                Block src = c.source;
                if (src == null || !(src.getState(false) instanceof Container)) {
                    reset(c);
                    return;
                }
                Location target = src.getLocation().add(0.5, 0, 0.5);
                if (g.getLocation().distanceSquared(target) > 2.6 * 2.6) {
                    walk(g, target);
                    return;
                }
                if (!canBreak(owner, src)) {
                    reset(c);
                    return;
                }
                Inventory inv = ((Container) src.getState(false)).getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (it == null || it.isEmpty()) continue;
                    if (c.filter != null && !c.filter.test(it, null)) continue;
                    ItemStack take = it.clone();
                    inv.setItem(i, null);
                    g.getEquipment().setItemInMainHand(take);
                    g.getWorld().playSound(g.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                    c.stage = Stage.DELIVER;
                    c.since = tick;
                    c.failures = 0;
                    walk(g, home.center());
                    return;
                }
                reset(c);   // emptied in the meantime
            }
            case DELIVER -> {
                if (!carrying) {
                    reset(c);
                    return;
                }
                Location target = home.center();
                if (g.getLocation().distanceSquared(target) > 2.8 * 2.8) {
                    walk(g, target);
                    return;
                }
                Network net = plugin.parts().networkOf(home, null);
                int in = net == null ? 0 : net.insert(held, null);
                if (in >= held.getAmount()) {
                    g.getEquipment().setItemInMainHand(null);
                    g.getWorld().playSound(g.getLocation(), Sound.BLOCK_COPPER_PLACE, 0.6f, 1.4f);
                    if (g.customName() != null && PlainTextComponentSerializer.plainText().serialize(g.customName()).trim().equalsIgnoreCase("Dobby")
                            && dobbied.add(c.owner)) {
                        Player who = Bukkit.getPlayer(c.owner);
                        if (who != null) {
                            who.sendMessage(Component.text("<Dobby> Master has given Dobby a delivery!", NamedTextColor.GRAY));
                            plugin.adv().grant(who, "dobby");
                        }
                    }
                    reset(c);
                } else {
                    ItemStack left = held.clone();
                    left.setAmount(held.getAmount() - in);
                    g.getEquipment().setItemInMainHand(left);
                    if (++c.failures >= 5) {   // the network is full: put the rest down instead of standing there forever
                        drop(c);
                        reset(c);
                    }
                }
            }
        }
    }

    private void walk(CopperGolem g, Location to) {
        if (g instanceof Mob m) m.getPathfinder().moveTo(to, 1.0);
    }

    private void drop(Courier c) {
        ItemStack held = c.golem.getEquipment().getItemInMainHand();
        if (held != null && !held.isEmpty()) {
            c.golem.getWorld().dropItemNaturally(c.golem.getLocation(), held);
            c.golem.getEquipment().setItemInMainHand(null);
        }
    }

    // ------------------------------------------------------------ supply points

    private static boolean isCopperChest(Material m) {
        return m.name().endsWith("COPPER_CHEST");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate<Block> steamworks() {
        for (RegisteredServiceProvider<Predicate> reg : Bukkit.getServicesManager().getRegistrations(Predicate.class))
            if (reg.getPlugin().getName().equalsIgnoreCase("Steamworks")) return (Predicate<Block>) reg.getProvider();
        return null;
    }

    private Block findSource(Courier c, Player owner) {
        World w = c.home.getWorld();
        int r = radius();
        Predicate<Block> sw = steamworks();
        Block best = null;
        double bd = Double.MAX_VALUE;
        for (int cx = (c.home.getBlockX() - r) >> 4; cx <= (c.home.getBlockX() + r) >> 4; cx++)
            for (int cz = (c.home.getBlockZ() - r) >> 4; cz <= (c.home.getBlockZ() + r) >> 4; cz++) {
                if (!w.isChunkLoaded(cx, cz)) continue;
                Chunk ch = w.getChunkAt(cx, cz);
                for (BlockState bs : ch.getTileEntities(b -> b instanceof Container, false)) {
                    double d = bs.getLocation().distanceSquared(c.home);
                    if (d > r * r || d >= bd) continue;
                    Block b = bs.getBlock();
                    if (plugin.parts().at(b) != null || plugin.networks().at(b, null) != null) continue;   // never empty the network itself
                    boolean ok = isCopperChest(b.getType()) || (sw != null && sw.test(b));
                    if (!ok) continue;
                    Inventory inv = ((Container) bs).getInventory();
                    if (inv.isEmpty()) continue;
                    if (c.filter != null && !carries(inv, c.filter)) continue;   // nothing in there it may carry
                    bd = d;
                    best = b;
                }
            }
        if (best != null && !canBreak(owner, best)) return null;
        return best;
    }

    /** Does this container hold at least one item the courier's filter lets through? */
    private static boolean carries(Inventory inv, Filter f) {
        for (ItemStack it : inv.getContents()) if (it != null && !it.isEmpty() && f.test(it, null)) return true;
        return false;
    }

    private boolean canBreak(Player owner, Block b) {
        if (owner.getGameMode() == GameMode.SPECTATOR) return false;
        var ev = new KuraPlugin.ProbeBreakEvent(b, owner);
        Bukkit.getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }
}
