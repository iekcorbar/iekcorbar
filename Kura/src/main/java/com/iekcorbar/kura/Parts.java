package com.iekcorbar.kura;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Places, draws, saves and runs the network parts (see PartType). */
final class Parts implements Listener {
    private final KuraPlugin plugin;
    private final NamespacedKey dataKey, displayTag;
    private final Map<UUID, Map<Long, Part>> parts = new HashMap<>();
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();
    private int breakHits, breakWindow, hopperInterval, hopperBatch, interfaceInterval, cabinetCapacity;

    Parts(KuraPlugin plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "parts");
        displayTag = new NamespacedKey(plugin, "display");
        load();
    }

    void load() {
        breakHits = Math.max(1, plugin.getConfig().getInt("parts.break-hits", 3));
        breakWindow = Math.max(20, plugin.getConfig().getInt("parts.break-window-ticks", 60));
        hopperInterval = Math.max(1, plugin.getConfig().getInt("inventory-hopper.interval-ticks", 10));
        hopperBatch = Math.max(1, plugin.getConfig().getInt("inventory-hopper.items-per-transfer", 8));
        interfaceInterval = Math.max(1, plugin.getConfig().getInt("inventory-interface.interval-ticks", 20));
        cabinetCapacity = Math.max(1, plugin.getConfig().getInt("filing-cabinet.capacity", 256));
    }

    NamespacedKey displayTag() {
        return displayTag;
    }

    // ------------------------------------------------------------ lookup

    Part at(Block b) {
        Map<Long, Part> m = parts.get(b.getWorld().getUID());
        return m == null ? null : m.get(Part.key(b));
    }

    Store.Cabinet cabinetStore(Part p) {
        return new Store.Cabinet(p, cabinetCapacity, () -> markDirty(p));
    }

    void markDirty(Part p) {
        dirty.computeIfAbsent(p.world.getUID(), k -> new HashSet<>()).add(Chunk.getChunkKey(p.x >> 4, p.z >> 4));
    }

    int count() {
        int n = 0;
        for (Map<Long, Part> m : parts.values()) n += m.size();
        return n;
    }

    /** The network a device uses (terminal: the block behind it first). */
    Network networkOf(Part p, Networks.Loader loader) {
        BlockFace back = switch (p.type) {
            case TERMINAL, CRAFTING_TERMINAL, LEVEL_EMITTER -> p.facing.getOppositeFace();
            case INTERFACE -> BlockFace.DOWN;
            default -> null;
        };
        if (p.type == PartType.FILING_CABINET) {
            Network n = plugin.networks().at(p.block(), loader);
            return n != null ? n : plugin.networks().single(p.block());
        }
        return plugin.networks().around(p.block(), back, loader);
    }

    /** Every terminal within this radius, nearest first (the wireless terminal's network browser). */
    List<Part> terminalsNear(Location from, int radius) {
        List<Part> out = new ArrayList<>();
        Map<Long, Part> m = parts.get(from.getWorld().getUID());
        if (m == null) return out;
        double r2 = (double) radius * radius;
        for (Part p : m.values()) {
            if (!p.type.terminal()) continue;
            if (p.center().distanceSquared(from) <= r2) out.add(p);
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.center().distanceSquared(from)));
        return out;
    }

    // ------------------------------------------------------------ chunks

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
    }

    void loadChunk(Chunk c) {
        String data = c.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return;
        boolean changed = false;
        for (String line : data.split("\n")) {
            try {
                Part p = Part.parse(c.getWorld(), line);
                Material now = p.block().getType();
                if (now != p.type.base && !(p.type == PartType.LEVEL_EMITTER && now == Material.REDSTONE_BLOCK)) {
                    changed = true; // removed by something else meanwhile
                    continue;
                }
                p.powered = now == Material.REDSTONE_BLOCK;
                if (at(p.block()) != null) continue;
                parts.computeIfAbsent(p.world.getUID(), k -> new HashMap<>()).put(p.key(), p);
                draw(p);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad part entry in chunk " + c.getX() + "," + c.getZ() + ": " + ex.getMessage());
            }
        }
        if (changed) dirty.computeIfAbsent(c.getWorld().getUID(), k -> new HashSet<>()).add(c.getChunkKey());
    }

    void unloadChunk(Chunk c) {
        save(c, true);
        Map<Long, Part> m = parts.get(c.getWorld().getUID());
        if (m == null) return;
        m.values().removeIf(p -> {
            if (p.x >> 4 != c.getX() || p.z >> 4 != c.getZ()) return false;
            erase(p);
            return true;
        });
    }

    private void save(Chunk c, boolean force) {
        Set<Long> d = dirty.get(c.getWorld().getUID());
        boolean was = d != null && d.remove(c.getChunkKey());
        if (!was && !force) return;
        StringBuilder sb = new StringBuilder();
        Map<Long, Part> m = parts.get(c.getWorld().getUID());
        if (m != null) {
            for (Part p : m.values()) {
                if (p.x >> 4 != c.getX() || p.z >> 4 != c.getZ()) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(p.serialize());
            }
        }
        if (sb.isEmpty()) c.getPersistentDataContainer().remove(dataKey);
        else c.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, sb.toString());
    }

    void saveAll(boolean force) {
        for (World w : plugin.getServer().getWorlds()) {
            Set<Long> keys = new HashSet<>();
            Set<Long> d = dirty.get(w.getUID());
            if (d != null) keys.addAll(d);
            if (force) {
                Map<Long, Part> m = parts.get(w.getUID());
                if (m != null) for (Part p : m.values()) keys.add(Chunk.getChunkKey(p.x >> 4, p.z >> 4));
            }
            for (long ck : keys) {
                int x = (int) ck, z = (int) (ck >> 32);
                if (w.isChunkLoaded(x, z)) save(w.getChunkAt(x, z), force);
                else if (d != null) d.remove(ck);
            }
        }
    }

    void removeAllDisplays() {
        for (Map<Long, Part> m : parts.values()) for (Part p : m.values()) erase(p);
    }

    // ------------------------------------------------------------ drawing

    private static ItemStack look(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kura", model));
        return it;
    }

    /** Turns a model whose front faces north to {@code f}. */
    static Quaternionf rotation(BlockFace f) {
        return switch (f) {
            case EAST -> new Quaternionf().rotateY((float) Math.toRadians(-90));
            case SOUTH -> new Quaternionf().rotateY((float) Math.toRadians(180));
            case WEST -> new Quaternionf().rotateY((float) Math.toRadians(90));
            case UP -> new Quaternionf().rotateX((float) Math.toRadians(90));
            case DOWN -> new Quaternionf().rotateX((float) Math.toRadians(-90));
            default -> new Quaternionf();
        };
    }

    private ItemDisplay spawn(Part p, ItemStack item, Quaternionf rot, float scale) {
        ItemDisplay d = p.world.spawn(p.center(), ItemDisplay.class, en -> {
            en.setPersistent(false);
            en.getPersistentDataContainer().set(displayTag, PersistentDataType.BYTE, (byte) 1);
            en.setItemStack(item);
            en.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            en.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        if (p.type.barrel()) light(d, p.block());
        return d;
    }

    /** Barrels are solid (no light inside): take the light of the block above. */
    private static void light(ItemDisplay d, Block b) {
        Block up = b.getRelative(BlockFace.UP);
        d.setBrightness(new Display.Brightness(Math.max(up.getLightFromBlocks(), 2), up.getLightFromSky()));
    }

    void draw(Part p) {
        erase(p);
        float scale = p.type.barrel() ? 1.01f : p.type == PartType.LEVEL_EMITTER ? 1.004f : 1.002f;
        if (p.paint != null && p.type.paintable()) {
            p.main = spawn(p, new ItemStack(p.paint), new Quaternionf(), 1.001f);
            return;
        }
        switch (p.type) {
            case CABLE -> p.main = spawn(p, look("cable_" + cableMask(p, null)), new Quaternionf(), 1f);
            case CABLE_CONNECTOR -> {
                p.main = spawn(p, look("cable_connector"), rotation(p.facing), 1f);
                int mask = cableMask(p, p.facing);
                if (mask != 0) p.extra = spawn(p, look("cable_" + mask), new Quaternionf(), 1f);
            }
            case LEVEL_EMITTER -> p.main = spawn(p, look(p.powered ? "level_emitter_on" : "level_emitter"), rotation(p.facing), scale);
            case INTERFACE -> p.main = spawn(p, look("inventory_interface"), new Quaternionf(), scale);
            default -> p.main = spawn(p, look(p.type.item.id()), p.type.directional ? rotation(p.facing) : new Quaternionf(), scale);
        }
    }

    private void erase(Part p) {
        if (p.main != null) p.main.remove();
        if (p.extra != null) p.extra.remove();
        p.main = p.extra = null;
    }

    /** Which sides a cable shows an arm on (bit = index in Networks.FACES). */
    private int cableMask(Part p, BlockFace skip) {
        int mask = 0;
        for (int i = 0; i < 6; i++) {
            BlockFace f = Networks.FACES[i];
            if (f == skip) continue;
            Block n = p.block().getRelative(f);
            Part q = at(n);
            boolean joins;
            if (q != null) {
                joins = q.type.kind != PartType.Kind.DEVICE || q.type != PartType.OPEN_CRATE;
            } else {
                // 0.1.11: the arm also points at a bare chest or barrel - that is the whole
                // rule now, and the model should say so out loud.
                joins = plugin.networks().isStorage(n);
            }
            if (joins) mask |= 1 << i;
        }
        return mask;
    }

    /** Redraw the cables around a block that started or stopped being part of a network. */
    void refreshAround(Block b) {
        for (BlockFace f : Networks.FACES) {
            Part n = at(b.getRelative(f));
            if (n != null && (n.type == PartType.CABLE || n.type == PartType.CABLE_CONNECTOR) && n.paint == null) draw(n);
        }
    }

    // ------------------------------------------------------------ placing / breaking

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() == Action.PHYSICAL) return;
        Part part = at(b);
        if (e.getHand() != EquipmentSlot.HAND) {
            if (part != null && e.getAction() == Action.RIGHT_CLICK_BLOCK && !part.type.barrel()) e.setCancelled(true);
            return;
        }
        Player p = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (part == null || part.block().getType() != Material.BARRIER) return; // barrels/redstone blocks break the vanilla way
            e.setCancelled(true);
            hit(part, p);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        ItemStack hand = e.getItem();
        KItem held = plugin.items().typeOf(hand);
        if (part != null) {
            // 0.1.11: parts sit on a barrier, which vanilla cannot mine, so survival players used to
            // see no break progress at all on a framed cable and gave up. Punching still works (hit()
            // below), but any part on a barrier now also comes off with an empty hand + shift - one
            // click, every game mode, no client mining packets needed. Barrels (Interface, Open Crate)
            // and the emitter's powered redstone block are real blocks and break the vanilla way.
            if (p.isSneaking() && (hand == null || hand.isEmpty()) && part.block().getType() == Material.BARRIER) {
                e.setCancelled(true);
                if (!plugin.canBreak(p, part.block())) return;   // claims and protection plugins still say no
                breakPart(part, p.getGameMode() != GameMode.CREATIVE);
                return;
            }
            if (use(part, p, hand, held)) {
                e.setCancelled(true);
                return;
            }
            if (part.type.barrel()) {
                if (PartType.of(held) == null || !p.isSneaking()) return; // vanilla opens the barrel
            } else {
                if (hand != null && hand.getType().isBlock() && !hand.getType().isAir() && held == null) return; // build against it
                e.setCancelled(true);
                if (PartType.of(held) == null) return;
            }
        } else if (held != null) {
            if (held == KItem.CONFIGURATOR && plugin.networks().isStorage(b)) {
                e.setCancelled(true);
                plugin.menus().openContainerSettings(p, b);
                return;
            }
            if (held == KItem.PAINT_KIT) {
                e.setCancelled(true);
                pickPaint(p, hand, b);
                return;
            }
        }
        PartType type = PartType.of(held);
        if (type == null) return;
        if (part == null && !p.isSneaking() && b.getType().isInteractable()) return; // open the chest/door as usual
        e.setCancelled(true);
        Block target = b.isReplaceable() && at(b) == null ? b : b.getRelative(e.getBlockFace());
        place(p, hand, type, target, b, e.getBlockFace());
    }

    private BlockFace facingFor(PartType type, Player p, BlockFace clicked) {
        BlockFace toPlayer = p.getFacing().getOppositeFace();
        return switch (type) {
            case CABLE_CONNECTOR, INVENTORY_HOPPER -> clicked.getOppositeFace();
            case OPEN_CRATE -> p.isSneaking() ? BlockFace.DOWN : toPlayer;
            case INTERFACE -> BlockFace.UP;
            case TERMINAL, CRAFTING_TERMINAL, LEVEL_EMITTER, FILING_CABINET -> toPlayer;
            default -> BlockFace.NORTH;
        };
    }

    private void place(Player p, ItemStack hand, PartType type, Block target, Block against, BlockFace clicked) {
        if (!target.isReplaceable() || at(target) != null) return;
        if (!target.getWorld().getNearbyEntities(BoundingBox.of(target), en -> en instanceof LivingEntity).isEmpty()) return;
        BlockState old = target.getState();
        target.setType(type.base, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(target, old, against, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            old.update(true, false);
            return;
        }
        Part part = new Part(target.getWorld(), target.getX(), target.getY(), target.getZ(), type, facingFor(type, p, clicked));
        if (type.barrel()) {
            if (target.getBlockData() instanceof Directional d) {
                d.setFacing(part.facing);
                target.setBlockData(d, false);
            }
            if (target.getState(false) instanceof Barrel barrel) {
                barrel.getPersistentDataContainer().set(plugin.networks().partKey, PersistentDataType.STRING, type.name());
                barrel.customName(Component.text(type.item.displayName));
                barrel.update(true, false);
            }
        }
        if (type == PartType.FILING_CABINET) {
            String s = plugin.items().string(hand, "contents");
            if (s != null) part.contents.addAll(Part.decodeList(s));
        }
        parts.computeIfAbsent(part.world.getUID(), k -> new HashMap<>()).put(part.key(), part);
        markDirty(part);
        draw(part);
        refreshAround(target);
        plugin.networks().touch(target);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        target.getWorld().playSound(part.center(), type.placeSound, 1f, 1f);
        p.swingMainHand();
    }

    private void hit(Part part, Player p) {
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (plugin.tick - part.lastHit > breakWindow) part.hits = 0;
            part.lastHit = plugin.tick;
            if (++part.hits < breakHits) {
                part.world.playSound(part.center(), Sound.BLOCK_COPPER_HIT, 0.7f, 0.9f);
                // the barrier never shows crack progress: say what the hits are doing, and how to
                // skip them (wires come off with an empty hand + shift in one click)
                p.sendActionBar(Component.text(part.type.item.displayName + ": " + part.hits + "/" + breakHits
                        + " hits - or sneak + right-click with an empty hand", NamedTextColor.GRAY));
                return;
            }
        }
        if (!plugin.canBreak(p, part.block())) return;
        breakPart(part, p.getGameMode() != GameMode.CREATIVE);
    }

    /** Removes the part, dropping its item (if asked) and whatever it holds. */
    void breakPart(Part part, boolean dropItem) {
        Map<Long, Part> m = parts.get(part.world.getUID());
        if (m != null) m.remove(part.key());
        markDirty(part);
        erase(part);
        plugin.menus().closeFor(part);
        Location at = part.center();
        Block b = part.block();
        if (part.type.barrel() && b.getState(false) instanceof Barrel barrel) {
            Inventory inv = barrel.getInventory();
            for (ItemStack it : inv.getContents()) if (it != null && !it.isEmpty()) part.world.dropItemNaturally(at, it);
            inv.clear();
        }
        if (dropItem) {
            ItemStack drop = plugin.items().create(part.type.item, 1);
            if (part.type == PartType.FILING_CABINET && !part.contents.isEmpty()) {
                plugin.items().setString(drop, "contents", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(part.contents)));
            }
            part.world.dropItemNaturally(at, drop);
        } else if (part.type == PartType.FILING_CABINET) {
            for (ItemStack it : part.contents) part.world.dropItemNaturally(at, it);
        }
        if (part.filter != null) part.world.dropItemNaturally(at, part.filter);
        b.setType(Material.AIR, true);
        part.world.playSound(at, Sound.BLOCK_COPPER_BREAK, 1f, 1f);
        part.world.spawnParticle(Particle.BLOCK, at, 20, 0.3, 0.3, 0.3, Material.COPPER_BLOCK.createBlockData());
        refreshAround(b);
        plugin.networks().touch(b);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof KuraPlugin.ProbeBreakEvent) return;
        Part part = at(e.getBlock());
        if (part == null) return;
        e.setCancelled(true);
        breakPart(part, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (at(b) != null) {
            e.setCancelled(true);
            return;
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
        for (Block b : new ArrayList<>(blocks)) {
            Part p = at(b);
            if (p == null) continue;
            blocks.remove(b);
            breakPart(p, true);
        }
    }

    /** Our items are paper underneath: keep them out of vanilla recipes (books, maps...). */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        org.bukkit.inventory.Recipe r = e.getRecipe();
        if (r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))) return;
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (plugin.items().typeOf(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------ using

    /** Right-click on a part. True = handled. */
    private boolean use(Part part, Player p, ItemStack hand, KItem held) {
        boolean empty = hand == null || hand.isEmpty();
        if (held == KItem.PAINT_KIT) {
            if (!part.type.paintable()) {
                p.sendActionBar(Component.text("Only trims and framed cables can be painted.", NamedTextColor.GRAY));
                return true;
            }
            if (p.isSneaking()) {
                part.paint = null;
            } else {
                String paint = plugin.items().string(hand, "paint");
                if (paint == null) {
                    p.sendActionBar(Component.text("Right-click a block first to pick its look.", NamedTextColor.GRAY));
                    return true;
                }
                part.paint = Material.matchMaterial(paint);
                if (p.getGameMode() != GameMode.CREATIVE) hand.damage(1, p);
            }
            draw(part);
            markDirty(part);
            part.world.playSound(part.center(), Sound.ITEM_BRUSH_BRUSHING_GENERIC, 1f, 1.2f);
            return true;
        }
        if (held == KItem.ADV_WIRELESS_TERMINAL && part.type.terminal() && p.isSneaking()) {
            plugin.items().setString(hand, "bound", part.world.getName() + "," + part.x + "," + part.y + "," + part.z);
            p.sendActionBar(Component.text("Bound to this terminal.", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
            return true;
        }
        if (held == KItem.CONFIGURATOR) {
            if (configurable(part)) plugin.menus().openPartSettings(p, part);
            else info(part, p);
            return true;
        }
        // holding another part: place it against this one (sneak on parts that have their own GUI)
        if (PartType.of(held) != null && (p.isSneaking() || !interactive(part.type))) return false;
        if (!empty && p.isSneaking() && hand.getType().isBlock() && held == null) return false; // build against it
        switch (part.type) {
            case TERMINAL, CRAFTING_TERMINAL -> {
                plugin.menus().openTerminal(p, part, false);
                return true;
            }
            case FILING_CABINET -> {
                if (!empty && held == null && !p.isSneaking()) {
                    // quick insert of the held stack
                    Store.Cabinet s = cabinetStore(part);
                    int n = s.insert(hand, hand.getAmount(), false);
                    if (n > 0) hand.setAmount(hand.getAmount() - n);
                    else p.sendActionBar(Component.text("This cabinet holds " + (part.contents.isEmpty() ? "nothing" : Items.pretty(part.contents.get(0).getType().name())) + " (" + part.stored() + "/" + cabinetCapacity + ").", NamedTextColor.GRAY));
                    return true;
                }
                plugin.menus().openCabinet(p, part);
                return true;
            }
            case INVENTORY_HOPPER, LEVEL_EMITTER -> {
                if (!empty && hand.getType().isBlock() && held == null) return false;
                plugin.menus().openPartSettings(p, part);
                return true;
            }
            case INTERFACE -> {
                if (empty && p.isSneaking()) {
                    plugin.menus().openPartSettings(p, part);
                    return true;
                }
                return false; // vanilla opens the barrel
            }
            case OPEN_CRATE -> {
                return false;
            }
            default -> {
                if (!empty && hand.getType().isBlock() && held == null) return false;
                info(part, p);
                return true;
            }
        }
    }

    private static boolean interactive(PartType t) {
        return t.terminal() || t == PartType.FILING_CABINET || t == PartType.INVENTORY_HOPPER || t == PartType.LEVEL_EMITTER || t.barrel();
    }

    private static boolean configurable(Part p) {
        return p.type == PartType.INVENTORY_HOPPER || p.type == PartType.LEVEL_EMITTER || p.type == PartType.INTERFACE;
    }

    /** Right-click on a cable or container part: what network is this? */
    private void info(Part part, Player p) {
        Network n = plugin.networks().at(part.block(), null);
        if (n == null || n.stores.isEmpty()) {
            // 0.1.11: a bare cable IS a network already, so say what is missing instead of counting to zero
            p.sendActionBar(Component.text("Nothing joined yet - touch a chest, barrel or shulker box with the cable.",
                    NamedTextColor.GRAY));
            return;
        }
        p.sendActionBar(Component.text("Network: " + n.stores.size() + " inventories, " + n.freeSlots() + "/" + n.slots() + " slots free"
                + (n.truncated ? " (too big - not everything is connected)" : ""), NamedTextColor.GOLD));
        plugin.menus().highlight(p, n);
    }

    private void pickPaint(Player p, ItemStack kit, Block b) {
        Material m = b.getType();
        if (!m.isOccluding() || at(b) != null || !m.isItem()) {
            p.sendActionBar(Component.text("Pick a full, solid block.", NamedTextColor.GRAY));
            return;
        }
        plugin.items().setString(kit, "paint", m.name());
        p.sendActionBar(Component.text("Paint: " + Items.pretty(m.name()), NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ITEM_DYE_USE, 0.8f, 1f);
    }

    // ------------------------------------------------------------ devices

    /** Every 10 ticks. */
    void tick() {
        boolean second = plugin.tick % 20 == 0;
        for (Map<Long, Part> m : parts.values()) {
            for (Part p : new ArrayList<>(m.values())) {
                if (p.main == null) continue; // display not up (chunk just loading)
                switch (p.type) {
                    case INVENTORY_HOPPER -> { if (plugin.tick % hopperInterval < 10) hopper(p); }
                    case INTERFACE -> { if (plugin.tick % interfaceInterval < 10) iface(p); }
                    case OPEN_CRATE -> crate(p);
                    case LEVEL_EMITTER -> { if (second) emitter(p); }
                    default -> { }
                }
                if (plugin.tick % 100 == 0 && p.type.barrel() && p.block().getType() != Material.BARREL) {
                    m.remove(p.key()); // broken by something that fired no event
                    erase(p);
                    markDirty(p);
                }
            }
        }
        if (plugin.tick % 100 == 0) relight();
        if (plugin.tick % 600 == 0) saveAll(false);
    }

    private void relight() {
        for (Map<Long, Part> m : parts.values()) for (Part p : m.values()) if (p.type.barrel() && p.main != null) light(p.main, p.block());
    }

    /** What sits at one side of an Inventory Hopper: a network, a container, or an Open Crate. */
    private Network side(Block b) {
        Part p = at(b);
        if (p != null) {
            return switch (p.type) {
                case CONNECTOR, TRIM, CABLE, FRAMED_CABLE, CABLE_CONNECTOR, FILING_CABINET -> {
                    Network n = plugin.networks().at(b, null);
                    yield n != null ? n : plugin.networks().single(b);
                }
                case TERMINAL, CRAFTING_TERMINAL -> networkOf(p, null);
                case INTERFACE -> barrelNetwork(b);
                default -> null;
            };
        }
        Network n = plugin.networks().at(b, null);
        if (n != null) return n;
        if (plugin.networks().isStorage(b)) return plugin.networks().single(b);
        if (b.getState(false) instanceof org.bukkit.block.Container c && (b.getType() == Material.HOPPER || b.getType() == Material.DROPPER
                || b.getType() == Material.DISPENSER)) {
            Network single = new Network(b.getWorld().getUID());
            single.stores.add(new Store.Container(b, c.getInventory()));
            return single;
        }
        return null;
    }

    private Network barrelNetwork(Block b) {
        if (!(b.getState(false) instanceof Barrel barrel)) return null;
        Network single = new Network(b.getWorld().getUID());
        single.stores.add(new Store.Container(b, barrel.getInventory()));
        return single;
    }

    private void hopper(Part p) {
        Block from = p.block().getRelative(p.facing.getOppositeFace()), to = p.block().getRelative(p.facing);
        if (!loaded(from) || !loaded(to)) return;
        Part toPart = at(to);
        Network src = side(from);
        if (src == null) return;
        Filter filter = Filter.of(plugin.items(), p.filter);
        if (toPart != null && toPart.type == PartType.OPEN_CRATE) {
            ItemStack moved = takeFirst(src, null, filter, hopperBatch);
            if (moved != null) dropFromCrate(toPart, moved);
            return;
        }
        Network dst = side(to);
        if (dst == null || dst == src) return;
        // first item (lowest priority store first) that passes the filter and fits
        for (int i = src.stores.size() - 1; i >= 0; i--) {
            Store s = src.stores.get(i);
            if (!s.valid()) continue;
            for (ItemStack it : s.contents()) {
                if (it == null || it.isEmpty() || (filter != null && !filter.test(it, null))) continue;
                int want = Math.min(hopperBatch, it.getAmount());
                int room = dst.room(it.asQuantity(want), null);
                if (room <= 0) continue;
                ItemStack taken = src.take(it.asOne(), room, null);
                if (taken == null) continue;
                int in = dst.insert(taken, null);
                if (in < taken.getAmount()) src.insert(taken.asQuantity(taken.getAmount() - in), null);
                return;
            }
        }
    }

    private static ItemStack takeFirst(Network src, Store except, Filter filter, int max) {
        for (int i = src.stores.size() - 1; i >= 0; i--) {
            Store s = src.stores.get(i);
            if (s == except || !s.valid()) continue;
            for (ItemStack it : s.contents()) {
                if (it == null || it.isEmpty() || (filter != null && !filter.test(it, null))) continue;
                return src.take(it.asOne(), Math.min(max, it.getAmount()), except);
            }
        }
        return null;
    }

    /** Inventory Interface: import = empty the barrel into the network; export = keep the filter items stocked. */
    private void iface(Part p) {
        if (!(p.block().getState(false) instanceof Barrel barrel)) return;
        Network net = networkOf(p, null);
        if (net == null || net.stores.isEmpty()) return;
        Inventory inv = barrel.getInventory();
        if (p.mode == 0) {
            Filter filter = Filter.of(plugin.items(), p.filter);
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.isEmpty() || (filter != null && !filter.test(it, null))) continue;
                int in = net.insert(it, null);
                if (in > 0) inv.setItem(i, it.getAmount() - in <= 0 ? null : it.asQuantity(it.getAmount() - in));
            }
            return;
        }
        // export: keep the wanted items in stock - p.amount stacks each (0/1 = the classic single stack)
        List<ItemStack> wanted = wanted(p.filter);
        if (wanted.isEmpty()) return;
        int stacks = Math.max(1, Math.min(27, p.amount));
        for (ItemStack w : wanted) {
            int have = 0;
            for (ItemStack it : inv.getContents()) if (it != null && it.isSimilar(w)) have += it.getAmount();
            int need = w.getMaxStackSize() * stacks - have;
            if (need <= 0) continue;
            ItemStack got = net.take(w.asOne(), need, null);
            if (got == null) continue;
            for (ItemStack left : inv.addItem(got).values()) net.insert(left, null);
        }
    }

    /** Items an export interface keeps in stock: the Item Filter's items, or the plain item. */
    private List<ItemStack> wanted(ItemStack filter) {
        List<ItemStack> out = new ArrayList<>();
        if (filter == null) return out;
        KItem t = plugin.items().typeOf(filter);
        if (t == KItem.ITEM_FILTER && !plugin.items().flag(filter, "filter_deny")) out.addAll(plugin.items().filterItems(filter));
        else if (t == null) out.add(filter.asOne());
        return out;
    }

    /** Open Crate: whatever hoppers/belts/arms put in falls out of the front. */
    private void crate(Part p) {
        if (!(p.block().getState(false) instanceof Barrel barrel)) return;
        Inventory inv = barrel.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.isEmpty()) continue;
            inv.setItem(i, null);
            dropFromCrate(p, it);
            return; // one stack per half second, so piles don't burst out at once
        }
    }

    void dropFromCrate(Part crate, ItemStack it) {
        Block front = crate.block().getRelative(crate.facing);
        Location at = front.getLocation().add(0.5, crate.facing == BlockFace.DOWN ? 0.7 : 0.3, 0.5);
        Item drop = crate.world.dropItem(at, it);
        drop.setVelocity(new Vector(crate.facing.getModX() * 0.08, 0, crate.facing.getModZ() * 0.08));
    }

    private void emitter(Part p) {
        boolean on = false;
        if (p.filter != null) {
            Network net = networkOf(p, null);
            if (net != null) {
                long n = net.count(p.filter.asOne());
                on = p.mode == 0 ? n > p.amount : n < p.amount;
            }
        }
        if (on == p.powered) return;
        p.powered = on;
        p.block().setType(p.base(), true);
        markDirty(p);
        draw(p);
    }

    private static boolean loaded(Block b) {
        return b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4);
    }

    /** All parts of one type (wireless lookups, /kura list). */
    List<Part> all() {
        List<Part> out = new ArrayList<>();
        for (Map<Long, Part> m : parts.values()) out.addAll(m.values());
        return out;
    }
}
