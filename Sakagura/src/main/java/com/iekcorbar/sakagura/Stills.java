package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The Steam Still: distilling as our own block instead of betting on a Paper potion mix in a
 * brewing stand (that never actually ran on the live server). The still is a real barrel carrying
 * our PDC tag - contents persist, hoppers work and protection plugins see a normal container.
 * With heat under it (same blocks a cauldron accepts) it distills every `distilling.seconds-per-run`
 * seconds: one glowstone dust spent, one run for up to `distilling.bottles-per-run` bottle stacks
 * inside. The brew state stays exactly what it was (distills++ and step 'D'), so recipes.yml and
 * the quality scoring needed no change.
 */
final class Stills implements Listener {
    private final Sakagura plugin;
    private final NamespacedKey coverKey;
    private final Map<Location, ItemDisplay> covers = new HashMap<>();
    private final Map<Location, Integer> progress = new HashMap<>();

    Stills(Sakagura plugin) {
        this.plugin = plugin;
        coverKey = new NamespacedKey(plugin, "still_cover");
    }

    private int secondsPerRun() {
        return Math.max(2, plugin.getConfig().getInt("distilling.seconds-per-run", 20));
    }

    private int bottlesPerRun() {
        return Math.max(1, plugin.getConfig().getInt("distilling.bottles-per-run", 3));
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("distilling.debug", false);
    }

    boolean isStill(Block b) {
        if (b.getType() != Material.BARREL) return false;
        if (!(b.getState(false) instanceof Barrel barrel)) return false;
        return barrel.getPersistentDataContainer().has(plugin.goods().stillKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ placing, covering, breaking

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.goods().isStillItem(e.getItemInHand())) return;
        Block b = e.getBlockPlaced();
        if (b.getState(false) instanceof Barrel barrel) {
            barrel.getPersistentDataContainer().set(plugin.goods().stillKey, PersistentDataType.BYTE, (byte) 1);
            barrel.customName(Component.text("Steam Still"));
            barrel.update();
        }
        cover(b);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (isStill(e.getBlock())) uncover(e.getBlock());
    }

    /** The barrel drops as our Steam Still item again. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        BlockState st = e.getBlockState();
        if (!(st instanceof Barrel barrel)) return;
        if (!barrel.getPersistentDataContainer().has(plugin.goods().stillKey, PersistentDataType.BYTE)) return;
        for (Item it : e.getItems()) {
            ItemStack s = it.getItemStack();
            // only the still block itself (one plain barrel item), not barrels stored inside
            if (s.getType() == Material.BARREL && s.getAmount() == 1 && !plugin.goods().isStillItem(s)) {
                it.setItemStack(plugin.goods().still(1));
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> {
            if (!isStill(b)) return false;
            uncover(b);
            return true; // the still itself does not blow up (the barrel inside does, like a cask)
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> {
            if (!isStill(b)) return false;
            uncover(b);
            return true;
        });
    }

    // ------------------------------------------------------------ the running distill

    /** Called once a second from the main tick loop; only loaded stills are in `covers`. */
    void tick() {
        for (Map.Entry<Location, ItemDisplay> en : new HashMap<>(covers).entrySet()) {
            Location at = en.getKey();
            Block b = at.getBlock();
            if (!(b.getState(false) instanceof Barrel barrel)) {
                removeCover(at);
                continue;
            }
            Inventory inv = barrel.getInventory();
            int bottles = 0, glow = 0;
            for (ItemStack it : inv.getContents()) {
                if (it == null) continue;
                if (plugin.goods().brewOf(it) != null) bottles++;
                else if (it.getType() == Material.GLOWSTONE_DUST) glow += it.getAmount();
            }
            boolean hot = Cauldrons.heated(b);
            if (bottles == 0 || glow == 0 || !hot) {
                progress.remove(at);
                continue;
            }
            int p = progress.merge(at, 1, Integer::sum);
            if (debug()) plugin.getLogger().info("[distill] still " + at + ": " + bottles + " bottle stack(s), " + glow + " glowstone, hot=true, " + p + "/" + secondsPerRun() + " s");
            if (p % 4 == 0) b.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0.5, 1.2, 0.5), 1, 0.1, 0.05, 0.1, 0.01);
            if (p < secondsPerRun()) continue;
            progress.remove(at);
            run(inv, b);
        }
    }

    /** One distill run: spend a glowstone, give the bottles inside their ++. */
    private void run(Inventory inv, Block b) {
        ItemStack[] content = inv.getContents();
        boolean spent = false;
        for (int i = 0; i < content.length; i++) {
            ItemStack it = content[i];
            if (it != null && it.getType() == Material.GLOWSTONE_DUST) {
                if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1);
                else inv.setItem(i, null);
                spent = true;
                break;
            }
        }
        if (!spent) {
            progress.remove(b.getLocation());
            return;
        }
        int done = 0;
        content = inv.getContents();
        for (int i = 0; i < content.length && done < bottlesPerRun(); i++) {
            ItemStack it = content[i];
            Brew br = plugin.goods().brewOf(it);
            if (br == null) continue;
            br.distills++;
            br.step('D');
            inv.setItem(i, plugin.goods().bottle(br, it.getAmount()));
            done++;
        }
        if (debug()) plugin.getLogger().info("[distill] still " + b.getLocation() + " run done on " + done + " bottle stack(s)");
        b.getWorld().playSound(b.getLocation().add(0.5, 1.0, 0.5), "sakagura:distill.drip", 0.6f, 1f);
        b.getWorld().spawnParticle(Particle.SPLASH, b.getLocation().add(0.5, 1.1, 0.5), 6, 0.2, 0.05, 0.2, 0);
    }

    // ------------------------------------------------------------ status when opening

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Barrel barrel)) return;
        if (!isStill(barrel.getBlock())) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        int bottles = 0, glow = 0;
        for (ItemStack it : barrel.getInventory().getContents()) {
            if (it == null) continue;
            if (plugin.goods().brewOf(it) != null) bottles++;
            else if (it.getType() == Material.GLOWSTONE_DUST) glow += it.getAmount();
        }
        boolean hot = Cauldrons.heated(barrel.getBlock());
        Component msg;
        if (bottles == 0) msg = Component.text("Steam Still: bottles in, a glowstone dust with them, fire below.", NamedTextColor.GOLD);
        else if (glow == 0) msg = Component.text("Steam Still: " + bottles + " bottle stack(s) waiting for a glowstone dust.", NamedTextColor.GOLD);
        else if (!hot) msg = Component.text("Steam Still: no heat below it (fire, lava, campfire...).", NamedTextColor.RED);
        else msg = Component.text("Steam Still: distilling, next run in " + Math.max(1, secondsPerRun() - progress.getOrDefault(barrel.getBlock().getLocation(), 0)) + " s.", NamedTextColor.GREEN);
        p.sendActionBar(msg);
        p.getWorld().playSound(p.getLocation(), "sakagura:cask.open", 0.5f, 1.2f);
    }

    // ------------------------------------------------------------ display

    private void cover(Block b) {
        uncover(b);
        ItemStack look = new ItemStack(Material.PAPER);
        look.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "still"));
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay d = b.getWorld().spawn(at, ItemDisplay.class, en -> {
            en.setPersistent(false);
            en.getPersistentDataContainer().set(coverKey, PersistentDataType.BYTE, (byte) 1);
            en.setItemStack(look);
            en.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            en.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.01f, 1.01f, 1.01f), new Quaternionf()));
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
        removeCover(b.getLocation());
    }

    private void removeCover(Location l) {
        ItemDisplay d = covers.remove(l);
        if (d != null) d.remove();
        progress.remove(l);
    }

    // ------------------------------------------------------------ chunk load / unload / disable

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
    }

    void loadChunk(Chunk c) {
        for (BlockState st : c.getTileEntities(b -> b.getType() == Material.BARREL, false))
            if (isStill(st.getBlock()) && !covers.containsKey(st.getLocation())) cover(st.getBlock());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk c = e.getChunk();
        World w = c.getWorld();
        covers.entrySet().removeIf(en -> {
            Location l = en.getKey();
            if (l.getWorld() != w || l.getBlockX() >> 4 != c.getX() || l.getBlockZ() >> 4 != c.getZ()) return false;
            en.getValue().remove();
            return true;
        });
        progress.keySet().removeIf(l -> l.getWorld() == w && l.getBlockX() >> 4 == c.getX() && l.getBlockZ() >> 4 == c.getZ());
    }

    /** Every 30 s: follow day/night light (same as the cask covers). */
    void relight() {
        for (Map.Entry<Location, ItemDisplay> en : covers.entrySet())
            if (en.getValue().isValid()) light(en.getValue(), en.getKey().getBlock());
    }

    void removeAll() {
        for (ItemDisplay d : covers.values()) d.remove();
        covers.clear();
        progress.clear();
        for (World w : plugin.getServer().getWorlds())
            for (Entity en : w.getEntities())
                if (en.getPersistentDataContainer().has(coverKey, PersistentDataType.BYTE)) en.remove();
    }
}
