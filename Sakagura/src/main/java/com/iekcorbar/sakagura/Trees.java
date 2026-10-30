package com.iekcorbar.sakagura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.block.BlockFace;
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
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchard trees (0.5.0): right-click a sapling on grass/dirt to plant. A tree is one text
 * line in the chunk PDC ("tid|x|y|z|stage|ready", like the props of Decor) and is drawn as a
 * stack of ItemDisplays, one model per block level, with a barrier at the trunk and one
 * Interaction over the column for clicks and punches (break-hits, like Decor does).
 * Fruiting trees show fruit on the top canopy when ready; picking drops the matching
 * garden produce and starts the ripening again. Sakura sheds petals, maple red leaves.
 */
final class Trees implements Listener {
    private static final float SCALE = 1f;

    private static final class Rec {
        World world;
        int x, y, z;
        Tree type;
        int stage;
        boolean ready;
        ItemDisplay[] displays = new ItemDisplay[0];
        Interaction hitbox;
        int hits;
        long lastHit;

        Rec(Tree type, World w, int x, int y, int z, int stage, boolean ready) {
            this.type = type;
            this.world = w;
            this.x = x;
            this.y = y;
            this.z = z;
            this.stage = stage;
            this.ready = ready;
        }

        String line() {
            return type.id() + "|" + x + "|" + y + "|" + z + "|" + stage + "|" + (ready ? 1 : 0);
        }
    }

    private final Sakagura plugin;
    private final NamespacedKey dataKey, tagKey, hitKey;
    private final Map<UUID, Map<Long, Rec>> trees = new HashMap<>();
    private final Map<UUID, Rec> byHitbox = new HashMap<>();
    private final double stageChance, fruitChance;
    private final int breakHits;
    private long tick;

    Trees(Sakagura plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "trees");
        tagKey = new NamespacedKey(plugin, "tree_display");
        hitKey = new NamespacedKey(plugin, "tree_hitbox");
        stageChance = 1 / (60.0 * Math.max(1, plugin.getConfig().getInt("trees.minutes-per-stage", 20)));
        fruitChance = 1 / (60.0 * Math.max(1, plugin.getConfig().getInt("trees.fruit-minutes", 20)));
        breakHits = Math.max(1, plugin.getConfig().getInt("trees.break-hits", 3));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    Rec at(Block b) {
        Map<Long, Rec> m = trees.get(b.getWorld().getUID());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    boolean free(World w, int x, int y, int z) {
        return at(w.getBlockAt(x, y, z)) == null;
    }

    // ------------------------------------------------------------ planting

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = e.getItem();
        Tree t = Tree.bySapling(plugin.goods().idOf(hand));
        if (t == null) return;
        Block soil = e.getClickedBlock();
        if (soil == null || !isSoil(soil.getType())) return;
        Block b = soil.getRelative(BlockFace.UP);
        if (!b.getType().isAir()) return;
        // room to grow: two more air levels and no neighbour closer than two blocks
        if (!b.getRelative(BlockFace.UP, 2).getType().isAir() || !b.getRelative(BlockFace.UP, 3).getType().isAir()) {
            e.getPlayer().sendActionBar(Component.text("The tree needs four blocks of air above it.", NamedTextColor.RED));
            return;
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int level = 0; level < 4; level++) {
                Block near = b.getRelative(dx, level, dz);
                Rec r = at(near);
                if (r != null && (dx != 0 || dz != 0 || level != 0)) {
                    e.getPlayer().sendActionBar(Component.text("Too close to another tree.", NamedTextColor.RED));
                    return;
                }
            }
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        var old = b.getState();
        b.setType(Material.BARRIER, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(b, old, soil, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            b.setType(Material.AIR, false);
            old.update(true, false);
            return;
        }
        Rec r = new Rec(t, b.getWorld(), b.getX(), b.getY(), b.getZ(), 0, false);
        trees.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>()).put(key(r.x, r.y, r.z), r);
        draw(r);
        saveChunk(b.getWorld(), r.x >> 4, r.z >> 4);
        plugin.adv().grant(p, "sapling");
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        b.getWorld().playSound(b.getLocation(), "minecraft:block.sapling.place", 1f, 0.9f);
        p.swingMainHand();
    }

    private static boolean isSoil(Material m) {
        return m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.PODZOL
                || m == Material.MOSS_BLOCK || m == Material.COARSE_DIRT || m == Material.DIRT_PATH;
    }

    // ------------------------------------------------------------ drawing

    private void draw(Rec r) {
        erase(r);
        String[] models = r.type.levels(r.stage, r.ready);
        r.displays = new ItemDisplay[models.length];
        for (int i = 0; i < models.length; i++) {
            ItemStack look = new ItemStack(Material.PAPER);
            look.setData(DataComponentTypes.ITEM_MODEL, Key.key("sakagura", "block/" + models[i]));
            Block at = r.world.getBlockAt(r.x, r.y + i, r.z);
            Location loc = new Location(r.world, r.x + 0.5, r.y + i + 0.5, r.z + 0.5);
            r.displays[i] = r.world.spawn(loc, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                d.setItemStack(look);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(SCALE, SCALE, SCALE), new Quaternionf()));
                d.setBrightness(new Display.Brightness(Math.max(at.getLightFromBlocks(), 4), at.getLightFromSky()));
            });
        }
        r.hitbox = r.world.spawn(new Location(r.world, r.x + 0.5, r.y, r.z + 0.5), Interaction.class, i -> {
            i.setPersistent(false);
            i.getPersistentDataContainer().set(hitKey, PersistentDataType.BYTE, (byte) 1);
            i.setInteractionWidth(1.2f);
            i.setInteractionHeight(r.type.hitHeight(r.stage));
            i.setResponsive(true);
        });
        byHitbox.put(r.hitbox.getUniqueId(), r);
    }

    private void erase(Rec r) {
        for (ItemDisplay d : r.displays) if (d != null) d.remove();
        r.displays = new ItemDisplay[0];
        if (r.hitbox != null) {
            byHitbox.remove(r.hitbox.getUniqueId());
            r.hitbox.remove();
            r.hitbox = null;
        }
    }

    /** Follow day/night light like the cask covers do. */
    void relight() {
        for (Map<Long, Rec> m : trees.values()) for (Rec r : m.values()) {
            for (int i = 0; i < r.displays.length; i++) {
                ItemDisplay d = r.displays[i];
                if (d == null || !d.isValid()) continue;
                Block at = r.world.getBlockAt(r.x, r.y + i, r.z);
                d.setBrightness(new Display.Brightness(Math.max(at.getLightFromBlocks(), 4), at.getLightFromSky()));
            }
        }
    }

    // ------------------------------------------------------------ growth + ambience (once a second)

    void tick() {
        tick++;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Map<Long, Rec> m : new ArrayList<>(trees.values())) {
            for (Rec r : m.values()) {
                if (!r.world.isChunkLoaded(r.x >> 4, r.z >> 4)) continue;
                if (r.stage < Tree.STAGES - 1) {
                    if (rnd.nextDouble() < stageChance && headroom(r)) {
                        r.stage++;
                        draw(r);
                        saveChunk(r.world, r.x >> 4, r.z >> 4);
                        r.world.playSound(new Location(r.world, r.x + 0.5, r.y + 1, r.z + 0.5), "minecraft:block.budding_amethyst.place", 0.4f, 1.4f);
                    }
                } else if (!r.ready && r.type.fruit() != null && rnd.nextDouble() < fruitChance) {
                    r.ready = true;
                    draw(r);
                    saveChunk(r.world, r.x >> 4, r.z >> 4);
                }
                if ((r.type.petals || r.type.leafFall) && !r.world.getNearbyPlayers(new Location(r.world, r.x + 0.5, r.y + 1.5, r.z + 0.5), 16).isEmpty() && rnd.nextInt(14) == 0) {
                    double px = r.x + 0.5 + rnd.nextDouble(0.7) - 0.35, pz = r.z + 0.5 + rnd.nextDouble(0.7) - 0.35;
                    double py = r.y + 2.2 + rnd.nextDouble(0.8);
                    // cherry_leaves (roze) voor de sakura; spore-blossom (goudgroen, dataloos) voor de herfstesdoorn
                    r.world.spawnParticle(r.type.petals ? Particle.CHERRY_LEAVES : Particle.FALLING_SPORE_BLOSSOM, px, py, pz, 1, 0.1, 0.0, 0.1, 0);
                }
            }
        }
    }

    /** The block above must still be free when a tree wants to get one level taller. */
    private boolean headroom(Rec r) {
        int need = r.stage == 0 ? 2 : 3;
        for (int i = 1; i < need; i++) {
            Block up = r.world.getBlockAt(r.x, r.y + i, r.z);
            if (!up.getType().isAir()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ clicking

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent e) {
        Rec r = byHitbox.get(e.getRightClicked().getUniqueId());
        if (r == null || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() == Material.BONE_MEAL && r.stage < Tree.STAGES - 1 && headroom(r)) {
            r.stage++;
            draw(r);
            saveChunk(r.world, r.x >> 4, r.z >> 4);
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            r.world.spawnParticle(Particle.HAPPY_VILLAGER, new Location(r.world, r.x + 0.5, r.y + 1.2, r.z + 0.5), 8, 0.3, 0.3, 0.3);
            r.world.playSound(new Location(r.world, r.x + 0.5, r.y + 1, r.z + 0.5), "minecraft:item.bone_meal.use", 1f, 1f);
            p.swingMainHand();
            return;
        }
        if (r.stage == Tree.STAGES - 1 && r.type.fruit() != null) {
            if (!r.ready) {
                p.sendActionBar(Component.text("The fruit on the " + r.type.displayName.toLowerCase(java.util.Locale.ROOT) + " tree is still ripening.", NamedTextColor.GRAY));
                return;
            }
            if (!plugin.canBreak(p, r.world.getBlockAt(r.x, r.y, r.z))) return;
            Fruit f = r.type.fruit();
            int n = f.minDrop + ThreadLocalRandom.current().nextInt(f.maxDrop - f.minDrop + 1);
            Location at = new Location(r.world, r.x + 0.5, r.y + 2.6, r.z + 0.5);
            r.world.dropItemNaturally(at, plugin.goods().item(f.produce, n));
            r.ready = false;
            draw(r);
            saveChunk(r.world, r.x >> 4, r.z >> 4);
            plugin.adv().grant(p, "tree_harvest");
            r.world.playSound(at, "minecraft:block.sweet_berry_bush.pick_berries", 1f, 1f);
            r.world.spawnParticle(Particle.HAPPY_VILLAGER, at, 4, 0.3, 0.2, 0.3);
            p.swingMainHand();
            return;
        }
        if (r.stage == Tree.STAGES - 1 && r.type.fruit() == null)
            p.sendActionBar(Component.text(r.type.displayName + " - decoration only, enjoy the colours.", NamedTextColor.GRAY));
        else p.sendActionBar(Component.text("A young " + r.type.displayName.toLowerCase(java.util.Locale.ROOT) + " tree - it keeps growing.", NamedTextColor.GRAY));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPunch(PrePlayerAttackEntityEvent e) {
        Rec r = byHitbox.get(e.getAttacked().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) {
            fell(r, true);
            return;
        }
        if (tick - r.lastHit > 4) r.hits = 0;
        r.lastHit = tick;
        if (++r.hits < breakHits) {
            r.world.playSound(new Location(r.world, r.x + 0.5, r.y + 0.5, r.z + 0.5), "minecraft:entity.item.pickup", 0.5f, 0.7f);
            return;
        }
        if (!plugin.canBreak(p, r.world.getBlockAt(r.x, r.y, r.z))) return;
        fell(r, true);
    }

    private void fell(Rec r, boolean drop) {
        Map<Long, Rec> m = trees.get(r.world.getUID());
        if (m != null) m.remove(key(r.x, r.y, r.z));
        erase(r);
        saveChunk(r.world, r.x >> 4, r.z >> 4);
        Block base = r.world.getBlockAt(r.x, r.y, r.z);
        if (base.getType() == Material.BARRIER) base.setType(Material.AIR, true);
        if (drop) {
            Location at = new Location(r.world, r.x + 0.5, r.y + 0.5, r.z + 0.5);
            r.world.dropItemNaturally(at, plugin.goods().item(r.type.sapling(), 1));
            Fruit f = r.type.fruit();
            if (f != null && r.ready) r.world.dropItemNaturally(at, plugin.goods().item(f.produce, f.minDrop));
        }
        r.world.playSound(new Location(r.world, r.x + 0.5, r.y + 0.5, r.z + 0.5), "minecraft:block.big_dripleaf.break", 0.9f, 0.9f);
        r.world.spawnParticle(Particle.CLOUD, new Location(r.world, r.x + 0.5, r.y + 1, r.z + 0.5), 8, 0.3, 0.3, 0.3, 0.02);
    }

    // ------------------------------------------------------------ keep the spot safe

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof Sakagura.ProbeBreakEvent) return;
        Rec r = at(e.getBlock());
        if (r != null) {
            e.setCancelled(true);
            fell(r, e.getPlayer().getGameMode() != GameMode.CREATIVE);
            return;
        }
        Rec below = at(e.getBlock().getRelative(BlockFace.UP)); // a trunk sitting on the broken block
        if (below != null && below.y == e.getBlock().getY() + 1) fell(below, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (at(e.getToBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> at(b) != null);
    }

    // ------------------------------------------------------------ chunk persistence (Decor-style lines)

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
                String[] f = line.split("\\|");
                Tree t = Tree.byId(f[0]);
                if (t == null) continue;
                int x = Integer.parseInt(f[1]), y = Integer.parseInt(f[2]), z = Integer.parseInt(f[3]);
                Rec r = new Rec(t, c.getWorld(), x, y, z, Integer.parseInt(f[4]), f[5].equals("1"));
                if (r.stage >= Tree.STAGES) continue;
                if (m.containsKey(key(x, y, z))) continue;
                m.put(key(x, y, z), r);
                draw(r);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad tree entry in chunk " + c.getX() + "," + c.getZ());
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
                erase(r);
                gone.add(en.getKey());
            }
        }
        for (Long k : gone) m.remove(k);
    }

    void removeAllDisplays() {
        for (Map<Long, Rec> m : trees.values()) for (Rec r : m.values()) erase(r);
        for (World w : plugin.getServer().getWorlds())
            for (Entity en : w.getEntities())
                if (en.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)
                        || en.getPersistentDataContainer().has(hitKey, PersistentDataType.BYTE)) en.remove();
    }

    int count() {
        int n = 0;
        for (Map<Long, Rec> m : trees.values()) n += m.size();
        return n;
    }
}
