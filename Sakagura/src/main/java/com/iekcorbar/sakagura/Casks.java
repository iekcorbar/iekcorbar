package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sake casks: a real vanilla barrel (so it keeps its inventory, hoppers and protection)
 * marked with its wood in the barrel's own data and covered by our cask model. Drinks age
 * in real time: when a bottle goes in it remembers the moment ("since"); when the cask is
 * opened or the bottle leaves, the time is added as years (config: minutes-per-year).
 */
final class Casks implements Listener {
    private final Sakagura plugin;
    private final NamespacedKey woodKey, tagKey, facingKey;
    private final Map<Location, ItemDisplay> covers = new HashMap<>();

    Casks(Sakagura plugin) {
        this.plugin = plugin;
        woodKey = new NamespacedKey(plugin, "cask");
        tagKey = new NamespacedKey(plugin, "cover");
        facingKey = new NamespacedKey(plugin, "cover_facing");
    }

    private long yearMillis() {
        return (long) (Math.max(0.1, plugin.getConfig().getDouble("aging.minutes-per-year", 20)) * 60_000);
    }

    Woods woodOf(Block b) {
        if (b.getType() != Material.BARREL || !(b.getState(false) instanceof Barrel barrel)) return null;
        return Woods.byId(barrel.getPersistentDataContainer().get(woodKey, PersistentDataType.STRING));
    }

    private Woods woodOf(Inventory inv) {
        return inv.getHolder(false) instanceof Barrel b ? woodOf(b.getBlock()) : null;
    }

    // ------------------------------------------------------------ ageing

    /** Adds the time since the bottle went in; keepAgeing = it stays in the cask (new "since" = now). */
    ItemStack settle(ItemStack it, Woods wood, boolean keepAgeing) {
        Brew b = plugin.goods().brewOf(it);
        if (b == null) return it;
        long now = System.currentTimeMillis();
        if (b.since > 0 && wood != null) {
            double added = (now - b.since) / (double) yearMillis();
            if (added > 0.001) {
                if (b.wood.isEmpty() || added > b.years) b.wood = wood.id();
                b.years += added;
                b.step('A');
            }
        }
        b.since = keepAgeing ? now : 0;
        return plugin.goods().bottle(b, it.getAmount());
    }

    private void settleAll(Inventory inv, Woods wood, boolean keepAgeing) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (plugin.goods().brewOf(it) != null) inv.setItem(i, settle(it, wood, keepAgeing));
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        Woods w = woodOf(e.getInventory());
        if (w != null) {
            settleAll(e.getInventory(), w, true);
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), "sakagura:cask.open", 0.6f, 1f);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Woods w = woodOf(e.getInventory());
        if (w == null) return;
        // bottles left inside start (or keep) ageing; bottles taken out stop
        for (int i = 0; i < e.getInventory().getSize(); i++) {
            ItemStack it = e.getInventory().getItem(i);
            Brew b = plugin.goods().brewOf(it);
            if (b != null && b.since == 0) e.getInventory().setItem(i, settle(it, w, true));
        }
        Inventory own = e.getPlayer().getInventory();
        for (int i = 0; i < own.getSize(); i++) {
            ItemStack it = own.getItem(i);
            Brew b = plugin.goods().brewOf(it);
            if (b != null && b.since > 0) own.setItem(i, settle(it, w, false));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        Woods into = woodOf(e.getDestination()), from = woodOf(e.getSource());
        if (plugin.goods().brewOf(e.getItem()) == null) return;
        if (from != null) e.setItem(settle(e.getItem(), from, false));
        if (into != null) e.setItem(settle(e.getItem(), into, true));
    }

    // ------------------------------------------------------------ placing / breaking

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        Woods w = plugin.goods().caskWood(e.getItemInHand());
        if (w == null) return;
        Block b = e.getBlockPlaced();
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(BlockFace.UP);
            b.setBlockData(d, false);
        }
        if (b.getState(false) instanceof Barrel barrel) {
            CoverTransform.storeFacing(barrel, facingKey, CoverTransform.horizontalFacing(e.getPlayer()));
            barrel.getPersistentDataContainer().set(woodKey, PersistentDataType.STRING, w.id());
            barrel.customName(net.kyori.adventure.text.Component.text(w.label() + " Sake Cask"));
            barrel.update();
        }
        cover(b, w);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Woods w = woodOf(e.getBlock());
        if (w == null) return;
        if (e.getBlock().getState(false) instanceof Barrel barrel) settleAll(barrel.getInventory(), w, false);
        uncover(e.getBlock());
    }

    /** The barrel drops as our cask again. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        BlockState st = e.getBlockState();
        if (st.getType() != Material.BARREL || !(st instanceof Barrel barrel)) return;
        Woods w = Woods.byId(barrel.getPersistentDataContainer().get(woodKey, PersistentDataType.STRING));
        if (w == null) return;
        for (Item it : e.getItems()) {
            ItemStack st2 = it.getItemStack();
            // only the barrel block itself (one plain barrel item), not barrels that were stored inside
            if (st2.getType() == Material.BARREL && st2.getAmount() == 1 && plugin.goods().caskWood(st2) == null) {
                it.setItemStack(plugin.goods().cask(w, 1));
                break;
            }
        }
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
        for (Block b : blocks) {
            Woods w = woodOf(b);
            if (w == null) continue;
            if (b.getState(false) instanceof Barrel barrel) settleAll(barrel.getInventory(), w, false);
            uncover(b);
        }
    }

    // ------------------------------------------------------------ the cask model over the barrel

    private void cover(Block b, Woods w) {
        uncover(b);
        ItemStack look = new ItemStack(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "cask_" + w.id()));
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay d = b.getWorld().spawn(at, ItemDisplay.class, en -> {
            en.setPersistent(false);
            en.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            en.setItemStack(look);
            en.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            en.setTransformation(CoverTransform.rotated(CoverTransform.facingOf(b, facingKey)));
        });
        light(d, b);
        covers.put(b.getLocation(), d);
    }

    /** The display sits inside a solid block (no light there), so it takes the light of the block above. */
    private static void light(ItemDisplay d, Block b) {
        Block up = b.getRelative(BlockFace.UP);
        d.setBrightness(new Display.Brightness(Math.max(up.getLightFromBlocks(), 2), up.getLightFromSky()));
    }

    private void uncover(Block b) {
        ItemDisplay d = covers.remove(b.getLocation());
        if (d != null) d.remove();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
    }

    void loadChunk(Chunk c) {
        for (BlockState st : c.getTileEntities(b -> b.getType() == Material.BARREL, false)) {
            Woods w = woodOf(st.getBlock());
            if (w != null && !covers.containsKey(st.getLocation())) cover(st.getBlock(), w);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        covers.entrySet().removeIf(en -> {
            Location l = en.getKey();
            if (l.getWorld() != e.getWorld() || l.getBlockX() >> 4 != e.getChunk().getX() || l.getBlockZ() >> 4 != e.getChunk().getZ()) return false;
            en.getValue().remove();
            return true;
        });
    }

    /** Every 30 s: follow day/night light. */
    void relight() {
        for (Map.Entry<Location, ItemDisplay> en : covers.entrySet()) if (en.getValue().isValid()) light(en.getValue(), en.getKey().getBlock());
    }

    void removeAll() {
        for (ItemDisplay d : covers.values()) d.remove();
        covers.clear();
        for (org.bukkit.World w : plugin.getServer().getWorlds())
            for (Entity en : w.getEntities())
                if (en.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) en.remove();
    }
}
