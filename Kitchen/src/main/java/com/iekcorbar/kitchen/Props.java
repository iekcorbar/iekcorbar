package com.iekcorbar.kitchen;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
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

/** Places, draws, saves and runs the Kitchen stations and props (see PropType). */
final class Props implements Listener {
    private static final Set<Material> HEAT = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.MAGMA_BLOCK);
    private static final Set<Material> LIT_HEAT = Set.of(Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FURNACE,
            Material.SMOKER, Material.BLAST_FURNACE);

    private final KitchenPlugin plugin;
    private final NamespacedKey dataKey, displayTag;
    private final Map<UUID, Map<Long, Prop>> props = new HashMap<>();
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();
    /** Seats on zabuton cushions: seat entity -> cushion. */
    private final Map<UUID, Prop> seats = new HashMap<>();
    // jokes: daikon cuts in a row per player, and when each zabuton sitter sat down
    private final Map<UUID, Integer> daikonCuts = new HashMap<>();
    private final Map<UUID, Long> sitSince = new HashMap<>();
    private final Map<UUID, Long> standSince = new HashMap<>();   // joke: standing ON the cushion like an idiot
    private final ItemStack filler;
    private int breakHits, compostSeconds;

    Props(KitchenPlugin plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "props");
        displayTag = new NamespacedKey(plugin, "display");
        filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.setData(DataComponentTypes.TOOLTIP_DISPLAY, io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        load();
    }

    void load() {
        breakHits = Math.max(1, plugin.getConfig().getInt("stations.break-hits", 3));
        compostSeconds = Math.max(10, plugin.getConfig().getInt("compost.minutes", 20) * 60);
    }

    // ------------------------------------------------------------ lookup

    Prop at(Block b) {
        Map<Long, Prop> m = props.get(b.getWorld().getUID());
        return m == null ? null : m.get(Prop.key(b));
    }

    boolean isRichSoil(Block b) {
        if (b.getType() != Material.BARRIER) return false;
        Prop p = at(b);
        return p != null && p.type == PropType.RICH_SOIL;
    }

    /** Heat source under a block (cooking pot). */
    boolean heated(Block pot) {
        Block below = pot.getRelative(BlockFace.DOWN);
        Material m = below.getType();
        if (HEAT.contains(m)) return true;
        if (LIT_HEAT.contains(m)) return below.getBlockData() instanceof Lightable l && l.isLit();
        if (m == Material.BARRIER) {
            Prop p = at(below);
            return p != null && p.type == PropType.KAMADO_STOVE && p.lit;
        }
        return false;
    }

    private void markDirty(Prop p) {
        dirty.computeIfAbsent(p.world.getUID(), k -> new HashSet<>()).add(Chunk.getChunkKey(p.x >> 4, p.z >> 4));
    }

    int count() {
        int n = 0;
        for (Map<Long, Prop> m : props.values()) n += m.size();
        return n;
    }

    // ------------------------------------------------------------ chunks

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
    }

    void loadChunk(Chunk c) {
        String data = c.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return;
        for (String line : data.split("\n")) {
            try {
                String[] f = Prop.fields(line);
                Prop p = new Prop(c.getWorld(), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        PropType.valueOf(f[0]), BlockFace.valueOf(f[4]));
                p.lit = f[5].equals("1");
                p.progress = Integer.parseInt(f[6]);
                ItemStack[] items = Prop.decodeItems(f[7]);
                makeInventory(p);
                if (p.type.behaviour == PropType.Behaviour.POT) {
                    for (int i = 0; i < Math.min(8, items.length); i++) {
                        int slot = i < 6 ? Prop.POT_INGREDIENTS[i] : i == 6 ? Prop.POT_CONTAINER : Prop.POT_RESULT;
                        p.inv.setItem(slot, items[i]);
                    }
                } else if (p.inv != null) {
                    for (int i = 0; i < Math.min(p.inv.getSize(), items.length); i++) p.inv.setItem(i, items[i]);
                } else {
                    for (int i = 0; i < Math.min(p.items.length, items.length); i++) p.items[i] = items[i];
                }
                if (f.length > 8 && !f[8].isEmpty()) {
                    String[] t = f[8].split(",");
                    for (int i = 0; i < Math.min(t.length, p.itemTicks.length); i++) p.itemTicks[i] = Integer.parseInt(t[i]);
                }
                if (p.block().getType() != p.type.base) continue; // removed by something else meanwhile
                if (at(p.block()) != null) continue; // already loaded (chunk event + startup scan)
                props.computeIfAbsent(p.world.getUID(), k -> new HashMap<>()).put(p.key(), p);
                draw(p);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad prop entry in chunk " + c.getX() + "," + c.getZ() + ": " + ex.getMessage());
            }
        }
    }

    void unloadChunk(Chunk c) {
        save(c, true);
        Map<Long, Prop> m = props.get(c.getWorld().getUID());
        if (m == null) return;
        m.values().removeIf(p -> {
            if (p.x >> 4 != c.getX() || p.z >> 4 != c.getZ()) return false;
            erase(p);
            if (p.inv != null) for (HumanEntity h : new ArrayList<>(p.inv.getViewers())) h.closeInventory();
            return true;
        });
    }

    private void save(Chunk c, boolean force) {
        Set<Long> d = dirty.get(c.getWorld().getUID());
        boolean was = d != null && d.remove(c.getChunkKey());
        if (!was && !force) return;
        StringBuilder sb = new StringBuilder();
        Map<Long, Prop> m = props.get(c.getWorld().getUID());
        if (m != null) {
            for (Prop p : m.values()) {
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
                Map<Long, Prop> m = props.get(w.getUID());
                if (m != null) for (Prop p : m.values()) keys.add(Chunk.getChunkKey(p.x >> 4, p.z >> 4));
            }
            for (long ck : keys) {
                int x = (int) ck, z = (int) (ck >> 32);
                if (w.isChunkLoaded(x, z)) save(w.getChunkAt(x, z), force);
                else if (d != null) d.remove(ck);
            }
        }
    }

    void removeAllDisplays() {
        for (Map<Long, Prop> m : props.values()) for (Prop p : m.values()) erase(p);
        for (UUID id : new ArrayList<>(seats.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        seats.clear();
    }

    // ------------------------------------------------------------ drawing

    private ItemStack look(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kitchen", model));
        return it;
    }

    private static float yaw(BlockFace f) {
        return switch (f) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    private ItemDisplay spawnDisplay(Location at, ItemStack item, Quaternionf rot, float scale, ItemDisplay.ItemDisplayTransform mode) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(displayTag, PersistentDataType.BYTE, (byte) 1);
            d.setItemStack(item);
            d.setItemDisplayTransform(mode);
            d.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    void draw(Prop p) {
        erase(p);
        String model = p.type.model() + (p.type == PropType.KAMADO_STOVE && p.lit ? "_lit" : "");
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(yaw(p.facing)));
        p.main = spawnDisplay(p.center(), look(model), rot, 1.002f, ItemDisplay.ItemDisplayTransform.NONE);
        if (p.type == PropType.KAMADO_STOVE && p.lit) p.main.setBrightness(new Display.Brightness(15, 15));
        drawItems(p);
    }

    /** Item on the cutting board / food on the grill. */
    private void drawItems(Prop p) {
        for (Entity e : p.extras) e.remove();
        p.extras.clear();
        if (p.type == PropType.CUTTING_BOARD && p.items[0] != null) {
            Quaternionf turn = new Quaternionf().rotateY((float) Math.toRadians(yaw(p.facing)));
            if (plugin.items().typeOf(p.items[0]) != null) {
                // our food models are already 3D and stand on their bottom (model y = 0)
                p.extras.add(spawnDisplay(new Location(p.world, p.x + 0.5, p.y + 0.125 + 0.5 * 0.7, p.z + 0.5), p.items[0].asOne(), turn, 0.7f,
                        ItemDisplay.ItemDisplayTransform.NONE));
            } else {
                Quaternionf flat = turn.rotateX((float) Math.toRadians(90));
                p.extras.add(spawnDisplay(new Location(p.world, p.x + 0.5, p.y + 0.16, p.z + 0.5), p.items[0].asOne(), flat, 0.5f,
                        ItemDisplay.ItemDisplayTransform.FIXED));
            }
        } else if (p.type == PropType.KAMADO_STOVE) {
            double[][] spots = {{0.28, 0.28}, {0.72, 0.28}, {0.28, 0.72}, {0.72, 0.72}};
            for (int i = 0; i < p.items.length; i++) {
                if (p.items[i] == null) continue;
                Quaternionf flat = new Quaternionf().rotateX((float) Math.toRadians(90));
                p.extras.add(spawnDisplay(new Location(p.world, p.x + spots[i][0], p.y + 1.03, p.z + spots[i][1]), p.items[i].asOne(), flat, 0.35f,
                        ItemDisplay.ItemDisplayTransform.FIXED));
            }
        }
    }

    private void erase(Prop p) {
        if (p.main != null) p.main.remove();
        p.main = null;
        for (Entity e : p.extras) e.remove();
        p.extras.clear();
    }

    private void makeInventory(Prop p) {
        switch (p.type.behaviour) {
            case POT -> {
                p.inv = Bukkit.createInventory(p, 27, Component.text("Cooking Pot"));
                for (int i = 0; i < 27; i++) p.inv.setItem(i, filler);
                for (int s : Prop.POT_INGREDIENTS) p.inv.setItem(s, null);
                p.inv.setItem(Prop.POT_CONTAINER, null);
                p.inv.setItem(Prop.POT_RESULT, null);
                p.inv.setItem(Prop.POT_PROGRESS, progressItem("Put ingredients in", NamedTextColor.GRAY));
            }
            case STORAGE -> p.inv = Bukkit.createInventory(p, 27, Component.text(p.type.item.displayName));
            case BASKET -> p.inv = Bukkit.createInventory(p, 9, Component.text(p.type.item.displayName));
            default -> { }
        }
    }

    private ItemStack progressItem(String text, NamedTextColor color) {
        ItemStack it = new ItemStack(Material.CLOCK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(text, color).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Bowl or bottle goes in the slot left of this.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ placing / breaking

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() == Action.PHYSICAL) return;
        Prop prop = at(b);
        if (e.getHand() != EquipmentSlot.HAND) {
            if (prop != null && e.getAction() == Action.RIGHT_CLICK_BLOCK) e.setCancelled(true);
            return;
        }
        Player p = e.getPlayer();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (prop == null || prop.type.flat()) return;
            e.setCancelled(true);
            hit(prop, p);
            return;
        }
        // already handled (crop planted on rich soil, meal put down) or denied by a protection plugin
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;
        ItemStack hand = e.getItem();
        if (prop != null) {
            if (use(prop, p, hand, e.getBlockFace())) {
                e.setCancelled(true);
                return;
            }
            // holding a normal block: let vanilla place it against the prop
            if (hand != null && hand.getType().isBlock() && !hand.getType().isAir() && plugin.items().typeOf(hand) == null) return;
            e.setCancelled(true);
            if (PropType.of(plugin.items().typeOf(hand)) == null) return;
        }
        PropType type = PropType.of(plugin.items().typeOf(hand));
        if (type == null) return;
        if (prop == null && !p.isSneaking() && b.getType().isInteractable() /* chests, doors */) return; // open the chest/door as usual
        e.setCancelled(true);
        Block target = b.isReplaceable() && at(b) == null ? b : b.getRelative(e.getBlockFace());
        place(p, hand, type, target, b);
    }

    private void place(Player p, ItemStack hand, PropType type, Block target, Block against) {
        if (!target.isReplaceable() || at(target) != null || plugin.crops().info(target) != null) return;
        if (type.flat() && !target.getRelative(BlockFace.DOWN).getType().isSolid()) return;
        BoundingBox box = BoundingBox.of(target);
        if (!type.flat() && !target.getWorld().getNearbyEntities(box, en -> en instanceof LivingEntity).isEmpty()) return;
        BlockState old = target.getState();
        target.setType(type.base, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(target, old, against, hand, p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) {
            old.update(true, false);
            return;
        }
        BlockFace facing = p.getFacing().getOppositeFace();
        if (type == PropType.TATAMI_MAT && (facing == BlockFace.SOUTH || facing == BlockFace.WEST)) facing = facing.getOppositeFace();
        Prop prop = new Prop(target.getWorld(), target.getX(), target.getY(), target.getZ(), type, facing);
        makeInventory(prop);
        props.computeIfAbsent(prop.world.getUID(), k -> new HashMap<>()).put(prop.key(), prop);
        markDirty(prop);
        draw(prop);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        target.getWorld().playSound(prop.center(), type.placeSound, 1f, 1f);
        p.swingMainHand();
    }

    private void hit(Prop prop, Player p) {
        long now = plugin.tick;
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (now - prop.lastHit > 20) prop.hits = 0;
            prop.lastHit = now;
            if (++prop.hits < breakHits) {
                prop.world.playSound(prop.center(), Sound.BLOCK_WOOD_HIT, 0.7f, 0.9f);
                return;
            }
        }
        if (!plugin.canBreak(p, prop.block())) return;
        breakProp(prop, p.getGameMode() != GameMode.CREATIVE);
    }

    /** Removes the prop, dropping its item (if asked) and everything stored in it. */
    void breakProp(Prop prop, boolean dropItem) {
        Map<Long, Prop> m = props.get(prop.world.getUID());
        if (m != null) m.remove(prop.key());
        markDirty(prop);
        erase(prop);
        Location at = prop.center();
        if (prop.inv != null) for (HumanEntity h : new ArrayList<>(prop.inv.getViewers())) h.closeInventory();
        for (ItemStack it : prop.saved()) if (it != null && !it.isEmpty()) prop.world.dropItemNaturally(at, it);
        if (dropItem) prop.world.dropItemNaturally(at, plugin.items().create(prop.type.item, 1));
        prop.block().setType(Material.AIR, true);
        prop.world.playSound(at, Sound.BLOCK_WOOD_BREAK, 1f, 1f);
        prop.world.spawnParticle(Particle.BLOCK, at, 20, 0.3, 0.3, 0.3, Material.SPRUCE_PLANKS.createBlockData());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e instanceof KitchenPlugin.ProbeBreakEvent) return;
        Prop prop = at(e.getBlock());
        if (prop == null) return;
        e.setCancelled(true);
        breakProp(prop, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    /** Flat props sit on a carpet: keep it from popping off or changing. */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Material m = e.getBlock().getType();
        if (m == Material.WHITE_CARPET && at(e.getBlock()) != null) e.setCancelled(true);
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
            Prop p = at(b);
            if (p == null) continue;
            blocks.remove(b);
            breakProp(p, true);
        }
    }

    // ------------------------------------------------------------ using

    /** Right-click on a prop. True = handled (vanilla must not act). */
    private boolean use(Prop prop, Player p, ItemStack hand, BlockFace face) {
        boolean empty = hand == null || hand.isEmpty();
        switch (prop.type.behaviour) {
            case BOARD -> {
                return board(prop, p, hand, empty);
            }
            case POT -> {
                if (!empty && hand.getType().isBlock() && p.isSneaking()) return false;
                p.openInventory(prop.inv);
                updateProgress(prop, null);
                return true;
            }
            case STORAGE, BASKET -> {
                if (!empty && hand.getType().isBlock() && p.isSneaking()) return false;
                p.openInventory(prop.inv);
                prop.world.playSound(prop.center(), prop.type == PropType.TANSU_CABINET ? Sound.BLOCK_BARREL_OPEN : Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.1f);
                return true;
            }
            case STOVE -> {
                return stove(prop, p, hand, empty);
            }
            case SEAT -> {
                if (!empty && hand.getType().isBlock()) return false;
                sit(prop, p);
                return true;
            }
            case SOIL, COMPOST, DECOR -> {
                return false;
            }
        }
        return false;
    }

    private boolean board(Prop prop, Player p, ItemStack hand, boolean empty) {
        KItem held = plugin.items().typeOf(hand);
        if (held != null && held.knife()) {
            if (prop.items[0] == null) {
                p.sendActionBar(Component.text("Put something on the board first.", NamedTextColor.GRAY));
                return true;
            }
            Recipes.Cut cut = plugin.recipes().cut(prop.items[0]);
            if (cut == null) {
                p.sendActionBar(Component.text("That can't be cut.", NamedTextColor.GRAY));
                return true;
            }
            plugin.adv().grant(p, "cut");
            ItemStack input = prop.items[0];
            if (plugin.items().typeOf(input) == KItem.DAIKON) {
                int n = daikonCuts.merge(p.getUniqueId(), 1, Integer::sum);
                if (n >= 10) {
                    daikonCuts.remove(p.getUniqueId());
                    p.getWorld().spawnParticle(Particle.FALLING_WATER, p.getEyeLocation(), 20, 0.25, 0.1, 0.25, 0);
                    p.sendActionBar(Component.text("Too many tears.", NamedTextColor.AQUA));
                    plugin.adv().grant(p, "tears");
                }
            } else {
                daikonCuts.remove(p.getUniqueId());
            }
            Location top = new Location(prop.world, prop.x + 0.5, prop.y + 0.3, prop.z + 0.5);
            for (Recipes.Output o : cut.outputs()) {
                ItemStack out = plugin.recipes().roll(o, input);
                if (out != null) prop.world.dropItem(top, out).setVelocity(new org.bukkit.util.Vector(0, 0.15, 0));
            }
            prop.world.spawnParticle(Particle.ITEM, top, 10, 0.15, 0.05, 0.15, 0.05, input.asOne());
            prop.world.playSound(top, "kitchen:knife.chop", 0.9f, 1f);
            input.setAmount(input.getAmount() - 1);
            prop.items[0] = input.getAmount() > 0 ? input : null;
            if (p.getGameMode() != GameMode.CREATIVE) hand.damage(1, p);
            p.swingMainHand();
            drawItems(prop);
            markDirty(prop);
            return true;
        }
        if (empty) {
            if (prop.items[0] == null) return false;
            give(p, prop.items[0]);
            prop.items[0] = null;
            drawItems(prop);
            markDirty(prop);
            prop.world.playSound(prop.center(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
            return true;
        }
        if (prop.items[0] != null || (hand.getType().isBlock() && plugin.items().typeOf(hand) == null && plugin.recipes().cut(hand) == null)) {
            return prop.items[0] != null && !hand.getType().isBlock();
        }
        prop.items[0] = hand.asOne();
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        drawItems(prop);
        markDirty(prop);
        prop.world.playSound(prop.center(), Sound.BLOCK_WOOD_PLACE, 0.6f, 1.4f);
        p.swingMainHand();
        return true;
    }

    private boolean stove(Prop prop, Player p, ItemStack hand, boolean empty) {
        if (!empty && (hand.getType() == Material.FLINT_AND_STEEL || hand.getType() == Material.FIRE_CHARGE)) {
            if (prop.lit) return true;
            prop.lit = true;
            if (p.getGameMode() != GameMode.CREATIVE) {
                if (hand.getType() == Material.FLINT_AND_STEEL) hand.damage(1, p);
                else hand.setAmount(hand.getAmount() - 1);
            }
            prop.world.playSound(prop.center(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f);
            prop.world.playSound(prop.center(), "kitchen:stove.ignite", 0.8f, 1f);
            draw(prop);
            markDirty(prop);
            p.swingMainHand();
            return true;
        }
        if (!empty && (hand.getType() == Material.WATER_BUCKET || hand.getType().name().endsWith("_SHOVEL"))) {
            if (!prop.lit) return false;
            prop.lit = false;
            prop.world.playSound(prop.center(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1f);
            draw(prop);
            markDirty(prop);
            p.swingMainHand();
            return true;
        }
        if (!empty && plugin.recipes().grill(hand) != null) {
            if (!prop.block().getRelative(BlockFace.UP).getType().isAir()) {
                p.sendActionBar(Component.text("The top of the stove is covered.", NamedTextColor.GRAY));
                return true;
            }
            for (int i = 0; i < prop.items.length; i++) {
                if (prop.items[i] != null) continue;
                prop.items[i] = hand.asOne();
                prop.itemTicks[i] = 0;
                if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                drawItems(prop);
                markDirty(prop);
                prop.world.playSound(prop.center(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.8f, 1f);
                p.swingMainHand();
                return true;
            }
            p.sendActionBar(Component.text("The grill is full.", NamedTextColor.GRAY));
            return true;
        }
        if (empty) {
            for (int i = prop.items.length - 1; i >= 0; i--) {
                if (prop.items[i] == null) continue;
                give(p, prop.items[i]);
                prop.items[i] = null;
                drawItems(prop);
                markDirty(prop);
                return true;
            }
            p.sendActionBar(Component.text(prop.lit ? "Grill food on top, or put a Cooking Pot on it." : "Light it with flint and steel.", NamedTextColor.GRAY));
            return true;
        }
        return false;
    }

    private static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    // ------------------------------------------------------------ zabuton

    private void sit(Prop prop, Player p) {
        if (p.isInsideVehicle()) return;
        plugin.adv().grant(p, "sit");
        Location at = new Location(prop.world, prop.x + 0.5, prop.y + 0.05, prop.z + 0.5, yaw(prop.facing) + 180, 0);
        for (Entity e : prop.world.getNearbyEntities(at, 0.3, 0.6, 0.3)) if (seats.containsKey(e.getUniqueId())) return; // taken
        ItemDisplay seat = prop.world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(displayTag, PersistentDataType.BYTE, (byte) 1);
        });
        seat.addPassenger(p);
        seats.put(seat.getUniqueId(), prop);
        sitSince.put(p.getUniqueId(), System.currentTimeMillis());
        standSince.remove(p.getUniqueId());   // Sitting is the correct way to use a zabuton.
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (seats.remove(e.getDismounted().getUniqueId()) == null) return;
        Entity seat = e.getDismounted();
        Bukkit.getScheduler().runTask(plugin, seat::remove);
        sitSince.remove(e.getEntity().getUniqueId());
        if (e.getEntity() instanceof Player p) {
            Location up = seat.getLocation().add(0, 0.6, 0);
            up.setYaw(p.getLocation().getYaw());
            up.setPitch(p.getLocation().getPitch());
            Bukkit.getScheduler().runTask(plugin, () -> p.teleport(up));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Entity v = e.getPlayer().getVehicle();
        if (v != null && seats.remove(v.getUniqueId()) != null) {
            v.eject();
            v.remove();
        }
    }

    // ------------------------------------------------------------ inventories

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Prop prop)) return;
        markDirty(prop);
        if (prop.type.behaviour != PropType.Behaviour.POT) return;
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < e.getInventory().getSize();
        if (!top) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack moving = e.getCurrentItem();
                if (moving == null || moving.isEmpty()) return;
                int[] targets = moving.getType() == Material.BOWL || moving.getType() == Material.GLASS_BOTTLE
                        ? new int[]{Prop.POT_CONTAINER} : Prop.POT_INGREDIENTS;
                for (int s : targets) {
                    ItemStack in = prop.inv.getItem(s);
                    if (in == null || in.isEmpty()) {
                        prop.inv.setItem(s, moving.clone());
                        moving.setAmount(0);
                        break;
                    }
                    if (in.isSimilar(moving) && in.getAmount() < in.getMaxStackSize()) {
                        int n = Math.min(moving.getAmount(), in.getMaxStackSize() - in.getAmount());
                        in.setAmount(in.getAmount() + n);
                        moving.setAmount(moving.getAmount() - n);
                        if (moving.getAmount() == 0) break;
                    }
                }
            } else if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
            }
            return;
        }
        if (!potSlot(raw)) {
            e.setCancelled(true);
            return;
        }
        if (raw == Prop.POT_RESULT) {
            // take only
            ItemStack cursor = e.getCursor();
            if ((cursor != null && !cursor.isEmpty()) || e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Prop prop)) return;
        markDirty(prop);
        if (prop.type.behaviour != PropType.Behaviour.POT) return;
        for (int raw : e.getRawSlots()) {
            if (raw < e.getInventory().getSize() && (!potSlot(raw) || raw == Prop.POT_RESULT)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private static boolean potSlot(int raw) {
        if (raw == Prop.POT_CONTAINER || raw == Prop.POT_RESULT) return true;
        for (int s : Prop.POT_INGREDIENTS) if (s == raw) return true;
        return false;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Prop prop) {
            markDirty(prop);
            if (prop.type == PropType.TANSU_CABINET) prop.world.playSound(prop.center(), Sound.BLOCK_BARREL_CLOSE, 0.7f, 1.1f);
        }
    }

    // ------------------------------------------------------------ ticking

    /** Every 10 ticks. */
    void tick() {
        boolean second = plugin.tick % 20 == 0;
        if (second && !sitSince.isEmpty()) {
            long now = System.currentTimeMillis();
            for (var en : new ArrayList<>(sitSince.entrySet())) {
                Player pl = Bukkit.getPlayer(en.getKey());
                if (pl == null || !pl.isInsideVehicle()) {
                    sitSince.remove(en.getKey());
                } else if (now - en.getValue() >= 300_000L) {
                    sitSince.remove(en.getKey()); // once per sitting
                    Location l = pl.getLocation().add(0.4, 0.3, 0.4);
                    pl.getWorld().spawnParticle(Particle.HEART, l, 4, 0.2, 0.1, 0.2, 0);
                    pl.getWorld().playSound(l, "minecraft:entity.cat.purr", 1f, 1f);
                    pl.sendActionBar(Component.text("A cat curls up next to you.", NamedTextColor.GOLD));
                    plugin.adv().grant(pl, "purr");
                }
            }
        }
        // joke: five minutes of standing on the cushion without sitting — the cat notices
        if (second) {
            long now = System.currentTimeMillis();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                org.bukkit.block.Block onder = pl.getLocation().getBlock().getRelative(BlockFace.DOWN);
                Prop z = at(onder);
                if (z != null && z.type == PropType.ZABUTON && !sitSince.containsKey(pl.getUniqueId())) {
                    Long t = standSince.putIfAbsent(pl.getUniqueId(), now);
                    if (t != null && now - t >= 300_000L && !plugin.adv().has(pl, "sitter")) {
                        standSince.remove(pl.getUniqueId());
                        pl.getWorld().playSound(pl.getLocation(), "minecraft:entity.cat.hiss", 0.9f, 1f);
                        pl.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,
                                pl.getLocation().add(0, 1.9, 0), 4, 0.25, 0.15, 0.25, 0);
                        pl.sendActionBar(Component.text("The cat disagrees.", NamedTextColor.GRAY));
                        plugin.adv().grant(pl, "sitter");
                    }
                } else {
                    standSince.remove(pl.getUniqueId());
                }
            }
        }
        for (Map<Long, Prop> m : props.values()) {
            for (Prop p : new ArrayList<>(m.values())) {
                switch (p.type.behaviour) {
                    case POT -> cook(p);
                    case STOVE -> grill(p);
                    case BASKET -> { if (second) catchItems(p); }
                    case COMPOST -> { if (second) compost(p); }
                    default -> { }
                }
            }
        }
        if (plugin.tick % 600 == 0) saveAll(false);
    }

    /** Exactly rice in the pot, nothing else. */
    private boolean onlyRice(ItemStack[] slots) {
        int rice = 0;
        for (ItemStack s : slots) {
            if (s == null || s.isEmpty()) continue;
            if (plugin.items().typeOf(s) != KItem.RICE) return false;
            rice++;
        }
        return rice > 0;
    }

    private boolean onlyWater(ItemStack[] slots) {
        int water = 0;
        for (ItemStack s : slots) {
            if (s == null || s.isEmpty()) continue;
            if (s.getType() != Material.WATER_BUCKET) return false;
            water++;
        }
        return water > 0;
    }

    private void cook(Prop p) {
        ItemStack[] slots = new ItemStack[6];
        for (int i = 0; i < 6; i++) slots[i] = p.inv.getItem(Prop.POT_INGREDIENTS[i]);
        int[] used = new int[6];
        Recipes.Cook r = plugin.recipes().cook(slots, used);
        if (r == null) {
            p.progress = 0;
            p.cooking = null;
            if (plugin.tick % 200 == 0 && onlyRice(slots)) {
                for (Player near : p.world.getNearbyPlayers(p.center(), 6)) {
                    near.sendActionBar(Component.text("Plain. Bold.", NamedTextColor.GRAY));
                    plugin.adv().grant(near, "plain");
                }
            }
            // joke: a pot of nothing but water, kept hot for five minutes, is patience itself
            if (onlyWater(slots) && heated(p.block())) {
                if (p.waterFor == 0) p.waterFor = System.currentTimeMillis();
                else if (System.currentTimeMillis() - p.waterFor >= 300_000L) {
                    p.waterFor = 0;
                    for (Player near : p.world.getNearbyPlayers(p.center(), 6)) {
                        near.sendActionBar(Component.text("Waiting for noodles.", NamedTextColor.GRAY));
                        plugin.adv().grant(near, "noodle");
                    }
                }
            } else {
                p.waterFor = 0;
            }
            updateProgress(p, slots[0] == null && isEmpty(slots) ? progressItem("Put ingredients in", NamedTextColor.GRAY)
                    : progressItem("No recipe with these ingredients", NamedTextColor.RED));
            return;
        }
        if (!r.id().equals(p.cooking)) {
            p.cooking = r.id();
            p.progress = 0;
        }
        ItemStack container = p.inv.getItem(Prop.POT_CONTAINER);
        if (r.container() != null && (container == null || container.getType() != r.container() || plugin.items().typeOf(container) != null)) {
            updateProgress(p, progressItem("Needs a " + (r.container() == Material.BOWL ? "bowl" : "glass bottle"), NamedTextColor.GOLD));
            return;
        }
        ItemStack result = plugin.recipes().make(r.result().spec(), r.result().count());
        ItemStack inResult = p.inv.getItem(Prop.POT_RESULT);
        if (inResult != null && !inResult.isEmpty() && (!inResult.isSimilar(result) || inResult.getAmount() + result.getAmount() > inResult.getMaxStackSize())) {
            updateProgress(p, progressItem("Take the finished food out", NamedTextColor.GOLD));
            return;
        }
        if (!heated(p.block())) {
            updateProgress(p, progressItem("Needs heat below the pot", NamedTextColor.GOLD));
            return;
        }
        p.progress += 10;
        Location top = p.center().add(0, 0.45, 0);
        p.world.spawnParticle(Particle.BUBBLE_POP, top, 3, 0.2, 0.05, 0.2, 0);
        if (plugin.tick % 40 == 0) {
            p.world.spawnParticle(Particle.CLOUD, top.clone().add(0, 0.2, 0), 2, 0.1, 0.05, 0.1, 0.01);
            p.world.playSound(top, "kitchen:pot.simmer", 0.45f, 1f);
        }
        if (p.progress < r.ticks()) {
            int pct = p.progress * 100 / r.ticks();
            updateProgress(p, progressItem("Cooking " + (plugin.items().typeOf(result) != null ? plugin.items().typeOf(result).displayName : result.getType().name().toLowerCase().replace("_", " ")) + ": " + pct + "%", NamedTextColor.GREEN));
            return;
        }
        // done: one item from each used slot, plus the container
        for (int i = 0; i < r.ingredients().size(); i++) {
            int slot = Prop.POT_INGREDIENTS[used[i]];
            ItemStack in = p.inv.getItem(slot);
            KItem t = plugin.items().typeOf(in);
            if (t != null && t.remainder == KItem.Rem.BOTTLE) p.world.dropItem(top, new ItemStack(Material.GLASS_BOTTLE));
            in.setAmount(in.getAmount() - 1);
            p.inv.setItem(slot, in.getAmount() > 0 ? in : null);
        }
        if (r.container() != null) container.setAmount(container.getAmount() - 1);
        if (inResult == null || inResult.isEmpty()) p.inv.setItem(Prop.POT_RESULT, result);
        else inResult.setAmount(inResult.getAmount() + result.getAmount());
        p.progress = 0;
        p.world.playSound(top, r.container() == Material.GLASS_BOTTLE ? "kitchen:pour" : "minecraft:block.brewing_stand.brew", 0.6f, r.container() == Material.GLASS_BOTTLE ? 1f : 1.3f);
        markDirty(p);
    }

    private static boolean isEmpty(ItemStack[] a) {
        for (ItemStack i : a) if (i != null && !i.isEmpty()) return false;
        return true;
    }

    private void updateProgress(Prop p, ItemStack item) {
        if (p.inv == null || p.inv.getViewers().isEmpty()) return;
        if (item != null) p.inv.setItem(Prop.POT_PROGRESS, item);
    }

    private void grill(Prop p) {
        if (!p.lit) return;
        if (plugin.tick % 40 == 0) {
            p.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.center().add(0, 0.6, 0), 1, 0.1, 0, 0.1, 0.01);
            for (ItemStack it : p.items) {
                if (it == null) continue;
                p.world.playSound(p.center().add(0, 0.6, 0), "kitchen:grill.sizzle", 0.45f, 1f); // food on the grill
                break;
            }
        }
        boolean changed = false;
        for (int i = 0; i < p.items.length; i++) {
            if (p.items[i] == null) continue;
            CampfireRecipe r = plugin.recipes().grill(p.items[i]);
            if (r == null) continue;
            p.itemTicks[i] += 10;
            if (plugin.tick % 20 == 0) p.world.spawnParticle(Particle.SMOKE, new Location(p.world, p.x + 0.5, p.y + 1.1, p.z + 0.5), 1, 0.25, 0, 0.25, 0);
            if (p.itemTicks[i] >= r.getCookingTime() / 2) { // twice as fast as a campfire
                p.world.dropItem(new Location(p.world, p.x + 0.5, p.y + 1.15, p.z + 0.5), r.getResult().clone());
                p.items[i] = null;
                p.itemTicks[i] = 0;
                changed = true;
            }
        }
        if (changed) {
            drawItems(p);
            markDirty(p);
        }
    }

    private void catchItems(Prop p) {
        BoundingBox box = new BoundingBox(p.x, p.y + 0.5, p.z, p.x + 1, p.y + 1.6, p.z + 1);
        for (Entity e : p.world.getNearbyEntities(box, en -> en instanceof Item)) {
            Item item = (Item) e;
            if (item.getPickupDelay() > 30) continue; // just thrown by a player standing on it
            ItemStack left = p.inv.addItem(item.getItemStack()).values().stream().findFirst().orElse(null);
            if (left == null) item.remove();
            else item.setItemStack(left);
            markDirty(p);
        }
    }

    private void compost(Prop p) {
        boolean water = false;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN, BlockFace.UP}) {
            if (p.block().getRelative(f).getType() == Material.WATER) water = true;
        }
        p.progress += water ? 2 : 1;
        if (plugin.tick % 200 == 0) p.world.spawnParticle(Particle.MYCELIUM, p.center().add(0, 0.55, 0), 4, 0.3, 0, 0.3, 0);
        if (p.progress % 60 == 0) markDirty(p);
        if (p.progress < compostSeconds) return;
        Map<Long, Prop> m = props.get(p.world.getUID());
        erase(p);
        Prop soil = new Prop(p.world, p.x, p.y, p.z, PropType.RICH_SOIL, p.facing);
        m.put(soil.key(), soil);
        draw(soil);
        markDirty(soil);
        p.world.playSound(p.center(), Sound.BLOCK_ROOTED_DIRT_PLACE, 0.8f, 1f);
    }
}
