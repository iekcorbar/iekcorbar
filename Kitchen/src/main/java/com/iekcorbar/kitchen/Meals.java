package com.iekcorbar.kitchen;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Meals put down on a block and eaten bite by bite, with chopsticks (see MealType). */
final class Meals implements Listener {
    static final class Meal {
        final World world;
        final int x, y, z;
        final MealType type;
        final BlockFace facing;
        int bites;
        ItemDisplay base, chopsticks;
        final ItemDisplay[] pieces;
        Interaction hitbox;
        long busyUntil;

        Meal(World world, int x, int y, int z, MealType type, BlockFace facing, int bites) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.facing = facing;
            this.bites = bites;
            this.pieces = new ItemDisplay[type.pieces.length];
        }

        long key() {
            return Prop.key(x, y, z);
        }

        Location center() {
            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }
    }

    private final KitchenPlugin plugin;
    private final NamespacedKey dataKey, displayTag, mealTag;
    private final Map<UUID, Map<Long, Meal>> meals = new HashMap<>();
    private final Map<UUID, Meal> byHitbox = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();

    Meals(KitchenPlugin plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "meals");
        displayTag = new NamespacedKey(plugin, "display");
        mealTag = new NamespacedKey(plugin, "meal");
    }

    Meal at(Block b) {
        Map<Long, Meal> m = meals.get(b.getWorld().getUID());
        return m == null ? null : m.get(Prop.key(b));
    }

    int count() {
        int n = 0;
        for (Map<Long, Meal> m : meals.values()) n += m.size();
        return n;
    }

    private void markDirty(Meal m) {
        dirty.add(m.world.getUID() + ":" + (m.x >> 4) + ":" + (m.z >> 4));
    }

    // ------------------------------------------------------------ geometry

    private static float yaw(BlockFace f) {
        return switch (f) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    /** Piece offset turned with the meal. */
    private static Vector offset(Meal m, double dx, double dz) {
        double a = Math.toRadians(yaw(m.facing));
        double cos = Math.cos(a), sin = Math.sin(a);
        return new Vector(dx * cos + dz * sin, 0, -dx * sin + dz * cos);
    }

    private Location piecePos(Meal m, int i) {
        MealType.Piece p = m.type.pieces[i];
        return m.center().add(offset(m, p.dx(), p.dz()));
    }

    private Location restPos(Meal m) {
        return m.center().add(offset(m, 0.36, 0)).add(0, 0, 0);
    }

    private ItemStack look(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kitchen", model));
        return it;
    }

    private ItemDisplay display(Meal m, Location at, String model) {
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(yaw(m.facing)));
        at.setYaw(0);
        at.setPitch(0);
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(displayTag, PersistentDataType.BYTE, (byte) 1);
            d.setItemStack(look(model));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(1, 1, 1), new Quaternionf()));
        });
    }

    private void draw(Meal m) {
        erase(m);
        m.base = display(m, m.center(), m.type.base);
        int eaten = m.type.pieces.length - m.bites;
        for (int i = eaten; i < m.type.pieces.length; i++) m.pieces[i] = display(m, piecePos(m, i).add(0, 0.004, 0), m.type.pieces[i].model()); // lifted a hair: the piece's bottom face must not share the plate's plane
        if (m.type.chopsticks) m.chopsticks = display(m, restPos(m).add(0, 0.006, 0), "meal_chopsticks");
        m.hitbox = m.world.spawn(new Location(m.world, m.x + 0.5, m.y, m.z + 0.5), Interaction.class, h -> {
            h.setPersistent(false);
            h.setInteractionWidth(0.8f);
            h.setInteractionHeight(0.4f);
            h.setResponsive(true);
            h.getPersistentDataContainer().set(mealTag, PersistentDataType.BYTE, (byte) 1);
        });
        byHitbox.put(m.hitbox.getUniqueId(), m);
    }

    private void erase(Meal m) {
        if (m.base != null) m.base.remove();
        if (m.chopsticks != null) m.chopsticks.remove();
        for (int i = 0; i < m.pieces.length; i++) {
            if (m.pieces[i] != null) m.pieces[i].remove();
            m.pieces[i] = null;
        }
        if (m.hitbox != null) {
            byHitbox.remove(m.hitbox.getUniqueId());
            m.hitbox.remove();
        }
        m.base = m.chopsticks = null;
        m.hitbox = null;
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
                String[] f = line.split("\\|");
                Meal m = new Meal(c.getWorld(), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        MealType.valueOf(f[0]), BlockFace.valueOf(f[4]), Integer.parseInt(f[5]));
                if (m.bites <= 0) continue;
                if (at(m.world.getBlockAt(m.x, m.y, m.z)) != null) continue; // already loaded
                meals.computeIfAbsent(m.world.getUID(), k -> new HashMap<>()).put(m.key(), m);
                draw(m);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping bad meal entry in chunk " + c.getX() + "," + c.getZ());
            }
        }
    }

    void unloadChunk(Chunk c) {
        save(c);
        Map<Long, Meal> m = meals.get(c.getWorld().getUID());
        if (m == null) return;
        m.values().removeIf(meal -> {
            if (meal.x >> 4 != c.getX() || meal.z >> 4 != c.getZ()) return false;
            erase(meal);
            return true;
        });
    }

    private void save(Chunk c) {
        StringBuilder sb = new StringBuilder();
        Map<Long, Meal> m = meals.get(c.getWorld().getUID());
        if (m != null) {
            for (Meal meal : m.values()) {
                if (meal.x >> 4 != c.getX() || meal.z >> 4 != c.getZ()) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(meal.type.name()).append('|').append(meal.x).append('|').append(meal.y).append('|').append(meal.z)
                        .append('|').append(meal.facing.name()).append('|').append(meal.bites);
            }
        }
        if (sb.isEmpty()) c.getPersistentDataContainer().remove(dataKey);
        else c.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, sb.toString());
        dirty.remove(c.getWorld().getUID() + ":" + c.getX() + ":" + c.getZ());
    }

    void saveAll() {
        for (String d : new ArrayList<>(dirty)) {
            String[] p = d.split(":");
            World w = Bukkit.getWorld(UUID.fromString(p[0]));
            int x = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            if (w != null && w.isChunkLoaded(x, z)) save(w.getChunkAt(x, z));
            else dirty.remove(d);
        }
    }

    void removeAllDisplays() {
        for (Map<Long, Meal> m : meals.values()) for (Meal meal : m.values()) erase(meal);
    }

    // ------------------------------------------------------------ putting down / picking up

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getBlockFace() != BlockFace.UP) return;
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        ItemStack hand = e.getItem();
        MealType type = MealType.of(plugin.items().typeOf(hand));
        if (type == null) return;
        e.setCancelled(true);
        Block below = e.getClickedBlock();
        Block target = below.getRelative(BlockFace.UP);
        if (!target.getType().isAir() || at(target) != null || !below.getType().isSolid()) return;
        if (!plugin.canBuild(p, target, below, hand)) return;
        Meal m = new Meal(target.getWorld(), target.getX(), target.getY(), target.getZ(), type, p.getFacing().getOppositeFace(), type.pieces.length);
        meals.computeIfAbsent(m.world.getUID(), k -> new HashMap<>()).put(m.key(), m);
        draw(m);
        markDirty(m);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        m.world.playSound(m.center(), Sound.BLOCK_DECORATED_POT_PLACE, 0.8f, 1.3f);
        p.swingMainHand();
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent e) {
        Meal m = byHitbox.get(e.getRightClicked().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        bite(m, e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeftClick(PrePlayerAttackEntityEvent e) {
        Meal m = byHitbox.get(e.getAttacked().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
        if (plugin.tick < m.busyUntil || !plugin.canBreak(e.getPlayer(), m.world.getBlockAt(m.x, m.y, m.z))) return;
        pickUp(m, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    /** Untouched: the meal item comes back; started: only what is left over (plate/bowl). */
    private void pickUp(Meal m, boolean drop) {
        remove(m);
        Location at = m.center();
        if (drop) {
            if (m.bites == m.type.pieces.length) m.world.dropItemNaturally(at, plugin.items().create(m.type.item, 1));
            else dropLeftover(m);
        }
        m.world.playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1f);
    }

    private void dropLeftover(Meal m) {
        if (m.type.leftover != null) m.world.dropItemNaturally(m.center(), plugin.items().create(m.type.leftover, 1));
        else if (m.type == MealType.RAMEN) m.world.dropItemNaturally(m.center(), new ItemStack(Material.BOWL));
    }

    private void remove(Meal m) {
        Map<Long, Meal> map = meals.get(m.world.getUID());
        if (map != null) map.remove(m.key());
        erase(m);
        markDirty(m);
    }

    // ------------------------------------------------------------ eating

    private void bite(Meal m, Player p) {
        if (plugin.tick < m.busyUntil || m.bites <= 0) return;
        boolean drink = m.type == MealType.TEA_SET;
        if (!drink && p.getFoodLevel() >= 20 && p.getGameMode() != GameMode.CREATIVE) {
            p.sendActionBar(Component.text("You're full.", NamedTextColor.GRAY));
            return;
        }
        int i = m.type.pieces.length - m.bites;
        ItemDisplay piece = m.pieces[i];
        if (piece == null) return;
        m.busyUntil = plugin.tick + 14;
        plugin.adv().grant(p, "shared");
        Location from = piecePos(m, i);
        Vector toPlayer = p.getEyeLocation().toVector().subtract(from.toVector()).setY(0);
        if (toPlayer.lengthSquared() > 0) toPlayer.normalize().multiply(0.22);
        Location lift = from.clone().add(toPlayer).add(0, drink ? 0.3 : 0.38, 0);
        ItemDisplay sticks = m.chopsticks;
        if (sticks != null) {
            sticks.setTeleportDuration(4);
            sticks.teleport(from.clone().add(0, 0.05, 0));
            m.world.playSound(from, "kitchen:chopsticks.clack", 0.6f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!piece.isValid()) return;
            piece.setTeleportDuration(5);
            piece.teleport(lift);
            if (drink) {
                piece.setInterpolationDelay(0);
                piece.setInterpolationDuration(5);
                Transformation t = piece.getTransformation();
                piece.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation().rotateX((float) Math.toRadians(-35)), t.getScale(), t.getRightRotation()));
            }
            if (sticks != null && sticks.isValid()) {
                sticks.setTeleportDuration(5);
                sticks.teleport(lift.clone().add(0, 0.04, 0));
            }
        }, 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            piece.remove();
            m.pieces[i] = null;
            m.bites--;
            markDirty(m);
            if (p.isOnline()) {
                p.setFoodLevel(Math.min(20, p.getFoodLevel() + m.type.nutrition));
                p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + m.type.saturation));
                if (drink) plugin.items().applyEffects(p, KItem.GREEN_TEA);
                if (m.type == MealType.RAMEN && m.bites == 0) plugin.items().applyEffects(p, KItem.RAMEN);
            }
            m.world.spawnParticle(Particle.ITEM, lift, 6, 0.05, 0.05, 0.05, 0.03, look(m.type.pieces[i].model()));
            if (m.type == MealType.RAMEN) m.world.playSound(lift, "kitchen:eat.slurp", 0.8f, 1f);
            else m.world.playSound(lift, drink ? Sound.ENTITY_GENERIC_DRINK : Sound.ENTITY_GENERIC_EAT, 0.8f, 1.1f);
            if (sticks != null && sticks.isValid()) {
                sticks.setTeleportDuration(6);
                sticks.teleport(restPos(m));
            }
            if (m.bites <= 0) {
                m.busyUntil = plugin.tick + 40;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (at(m.world.getBlockAt(m.x, m.y, m.z)) != m) return;
                    remove(m);
                    dropLeftover(m);
                    m.world.playSound(m.center(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1f);
                }, 30L);
            }
        }, 11L);
    }

    // ------------------------------------------------------------ keep it sane

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (at(e.getBlockPlaced()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSupportBreak(BlockBreakEvent e) {
        Meal m = at(e.getBlock().getRelative(BlockFace.UP));
        if (m != null) pickUp(m, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Meal m = at(e.getToBlock());
        if (m != null) pickUp(m, true);
    }

    /** All meal entities that might be left from a crash (not persistent, so normally none). */
    static boolean isOurs(Entity e, NamespacedKey tag) {
        return e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    List<Meal> all() {
        List<Meal> out = new ArrayList<>();
        for (Map<Long, Meal> m : meals.values()) out.addAll(m.values());
        return out;
    }
}
