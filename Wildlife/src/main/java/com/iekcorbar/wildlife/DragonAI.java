package com.iekcorbar.wildlife;

import org.bukkit.event.player.PlayerInputEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The steam dragon's own mind (0.8.0): everything a wolf does for a player, plus a saddle.
 *
 *  - Tame: right-click a wild dragon with coal or charcoal (it is a furnace). Each lump 35 %;
 *    hearts while it considers, happy villagers when it is yours. The PDC remembers the tamer
 *    and the parrot base is marked tamed too, so its own follow-the-owner brain kicks in.
 *  - Stay: the owner + empty hand + sneaking toggles sitting. Brain's Control goal occupies
 *    the MOVE slot while it sits, and FMM plays the sit pose.
 *  - Ride: the owner + empty hand mounts it. WASD steers where the rider looks, space climbs,
 *    back descends, no input holds a slow hover. Dragons simply fly: gravity is off in
 *    Animals.setup, so neither mount nor rider takes fall damage. Dismount = the usual sneak.
 *  - Wild dragons roar when you crowd them; climbing dragons puff steam from the neck vents.
 */
final class DragonAI implements Listener {
    private final WildlifePlugin plugin;
    private final NamespacedKey tamerKey, sittingKey, dragonsKey, lastKey;
    private final Map<UUID, Input> inputs = new HashMap<>();
    private final Map<UUID, Long> lastRoar = new HashMap<>();

    /** Brain's Control goal asks: is this dragon too busy (sitting / carrying) for its own brain? */
    static boolean blocked(Animals.Animal a) {
        return a.species == Species.DRAGON && (isSitting(a.entity) || ridden(a.entity));
    }

    static boolean isSitting(LivingEntity e) {
        Byte b = e.getPersistentDataContainer().get(Keeps.SIT, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    static UUID tamer(LivingEntity e) {
        String s = e.getPersistentDataContainer().get(Keeps.TAMER, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    /** A dragon permission (all default true); tells the player when it is missing. */
    static boolean allowed(Player p, String node) {
        if (p.hasPermission("wildlife.dragon." + node)) return true;
        p.sendActionBar(Component.text("You are not allowed to do that with a dragon.", NamedTextColor.RED));
        return false;
    }

    boolean isTamedBy(Animals.Animal a, Player p) {
        return tamer(a.entity) != null && tamer(a.entity).equals(p.getUniqueId());
    }

    private static final class Input {
        boolean forward, back, left, right, jump, sprint;
    }

    /** Keys shared with Brain; made once by the plugin's first DragonAI. */
    private static final class Keeps {
        static NamespacedKey TAMER, SIT;
    }

    DragonAI(WildlifePlugin plugin) {
        this.plugin = plugin;
        Keeps.TAMER = tamerKey = new NamespacedKey(plugin, "dragon_tamer");
        Keeps.SIT = sittingKey = new NamespacedKey(plugin, "dragon_sitting");
        dragonsKey = new NamespacedKey(plugin, "dragons_tamed");
        lastKey = new NamespacedKey(plugin, "dragons_last");
    }

    // ------------------------------------------------------------ right-click (from Animals.onInteract)

    /** True when the click is consumed here; false lets Breeding.use() do its love trick. */
    boolean interact(Animals.Animal a, Player p, ItemStack hand, boolean sneaking) {
        LivingEntity e = a.entity;
        Material held = hand.getType();
        String skin = switch (held) {                                      // dye = a new coat of paint (the five looks)
            case ORANGE_DYE -> "wl_dragon";
            case RED_DYE -> "wl_dragon_sakagura";
            case YELLOW_DYE -> "wl_dragon_kitchen";
            case BLUE_DYE -> "wl_dragon_kura";
            case GREEN_DYE -> "wl_dragon_verdigris";
            default -> null;
        };
        if (skin != null && p.getUniqueId().equals(tamer(e))) {
            if (!allowed(p, "paint")) return true;
            String cur = a.model.endsWith("_baby") ? a.model.substring(0, a.model.length() - 5) : a.model;
            if (cur.equals(skin)) {
                p.sendActionBar(Component.text("It already wears that colour.", NamedTextColor.GRAY));
                return true;
            }
            if (!plugin.animals().reskin(a, skin)) {
                p.sendActionBar(Component.text("The paint will not take (model missing).", NamedTextColor.RED));
                return true;
            }
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            p.sendActionBar(Component.text("A fresh coat of paint.", NamedTextColor.GOLD));
            plugin.adv().grant(p, "dragon_paint");
            return true;
        }
        var maxHp = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        boolean hurt = maxHp != null && e.getHealth() < maxHp.getValue() - 0.01;
        if ((held == Material.COAL || held == Material.CHARCOAL) && p.getUniqueId().equals(tamer(e)) && (a.hunger < 70 || hurt)) {
            a.hunger = Math.min(100, a.hunger + 30);                       // a hungry or hurt dragon is fed before it is bred
            if (hurt) e.setHealth(Math.min(maxHp.getValue(), e.getHealth() + Math.max(4.0, maxHp.getValue() * 0.25)));   // coal heals too
            e.getPersistentDataContainer().set(plugin.animals().hungerKey, PersistentDataType.INTEGER, a.hunger);
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, e.getHeight() * 0.6, 0), 8, 0.3, 0.2, 0.3, 0.02);
            e.getWorld().playSound(e.getLocation(), "wildlife:dragon.ambient", 0.8f, 1.1f);
            p.sendActionBar(Component.text("The furnace roars. Hunger " + a.hunger + "/100" + (maxHp != null ? ", health " + (int) Math.ceil(e.getHealth()) + "/" + (int) maxHp.getValue() : "") + ".", NamedTextColor.GOLD));
            return true;
        }
        if (held == Material.COAL || held == Material.CHARCOAL) {
            if (tamer(e) != null && tamer(e).equals(p.getUniqueId())) plugin.adv().grant(p, "dragon_feed");
            if (a.baby || tamer(e) != null) return false;                  // babies grow on it, the tamed breed on it
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight(), 0), 3, 0.3, 0.2, 0.3, 0);
            if (!allowed(p, "tame")) return true;
            if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                tame(a, p);
                return true;
            }
            p.sendActionBar(Component.text("It eyes the coal. Not yet.", NamedTextColor.GRAY));
            return true;
        }
        if (held == Material.CHEST && !a.baby && p.getUniqueId().equals(tamer(e))) return allowed(p, "bag") ? plugin.pets().installBag(p, e, hand) : true;
        if (!held.isAir() || a.baby || !p.getUniqueId().equals(tamer(e))) return false;   // strangers just look
        if (sneaking) {
            if (!allowed(p, "pet")) return true;
            setSitting(a, p, !isSitting(e));
            return true;
        }
        if (isSitting(e)) {                                                // mounting gets it back on its feet
            e.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) 0);
            plugin.fmm().stop(e);
        }
        if (!allowed(p, "ride")) return true;
        if (!seatRider(e, p)) {
            p.sendActionBar(Component.text("There is no room on the saddle.", NamedTextColor.RED));
            return true;
        }
        plugin.adv().grant(p, "dragon_rider");
        if (a.hunger <= 20) p.sendActionBar(Component.text("Your dragon is hungry and slow - feed it coal.", NamedTextColor.GRAY));
        e.getWorld().playSound(e.getLocation(), "wildlife:dragon.ambient", 0.9f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------ pet commands (/wildlife pet ...)

    /** Sit or stand up, same as sneak + right-click. */
    void setSitting(Animals.Animal a, Player p, boolean sit) {
        LivingEntity e = a.entity;
        e.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) (sit ? 1 : 0));
        if (sit) {
            plugin.fmm().play(e, "sit", true);
            plugin.adv().grant(p, "dragon_sit");
        } else plugin.fmm().stop(e);
        p.sendActionBar(Component.text(sit ? "The dragon sits." : "The dragon gets up.", NamedTextColor.GRAY));
    }

    /** The player's dragon: the one they look at (40 blocks), else the nearest loaded one of theirs. */
    Animals.Animal petOf(Player p) {
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTraceEntities(eye, eye.getDirection(), 40, 1.0, en -> en instanceof LivingEntity);
        if (hit != null && hit.getHitEntity() != null) {
            Animals.Animal a = plugin.animals().get(hit.getHitEntity());
            if (a != null && a.species == Species.DRAGON && isTamedBy(a, p)) return a;
        }
        Animals.Animal best = null;
        double bd = Double.MAX_VALUE;
        for (Animals.Animal a : plugin.animals().all()) {
            if (a.species != Species.DRAGON || !isTamedBy(a, p) || a.entity.getWorld() != p.getWorld()) continue;
            double d = a.entity.getLocation().distanceSquared(p.getLocation());
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    /** Call the dragon to the owner's side (a sitting one gets up first). */
    boolean come(Animals.Animal a, Player p) {
        LivingEntity e = a.entity;
        if (e.getWorld() != p.getWorld() || ridden(e)) return false;
        if (isSitting(e)) {
            e.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) 0);
            plugin.fmm().stop(e);
        }
        Location at = safeSpot(p, e);
        e.teleport(at);
        e.getWorld().spawnParticle(Particle.CLOUD, at, 6, 0.3, 0.2, 0.3, 0.01);
        return true;
    }

    /** Give the dragon back to the wild. */
    void release(Animals.Animal a, Player p) {
        LivingEntity e = a.entity;
        var pdc = e.getPersistentDataContainer();
        pdc.remove(tamerKey);
        forget(p, e.getUniqueId());
        pdc.set(sittingKey, PersistentDataType.BYTE, (byte) 0);
        plugin.fmm().stop(e);
        endRide(e);
        plugin.nests().release(e);
        if (e instanceof Parrot par) { par.setOwner(null); par.setTamed(false); }
        var pp = p.getPersistentDataContainer();
        Integer n = pp.get(dragonsKey, PersistentDataType.INTEGER);
        pp.set(dragonsKey, PersistentDataType.INTEGER, Math.max(0, (n == null ? 1 : n) - 1));
    }

    void rename(Animals.Animal a, String name) {
        a.entity.customName(name == null ? null : Component.text(name, NamedTextColor.GOLD));
        a.entity.setCustomNameVisible(false);
    }

    // ------------------------------------------------------------ the Dragon Whistle

    /** Player PDC: "uuid,world,x,y,z" per tamed dragon, ';' between them - where each was last seen, also when its chunk is gone. */
    private void remember(UUID owner, LivingEntity e) {
        Player p = Bukkit.getPlayer(owner);
        if (p == null) return;
        Location l = e.getLocation();
        String mine = e.getUniqueId() + "," + l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
        StringBuilder sb = new StringBuilder(mine);
        String old = p.getPersistentDataContainer().get(lastKey, PersistentDataType.STRING);
        int n = 1;
        if (old != null) for (String part : old.split(";")) {
            if (part.isEmpty() || part.startsWith(e.getUniqueId().toString()) || n >= 8) continue;
            sb.append(';').append(part);
            n++;
        }
        p.getPersistentDataContainer().set(lastKey, PersistentDataType.STRING, sb.toString());
    }

    private void forget(Player p, UUID dragon) {
        String old = p.getPersistentDataContainer().get(lastKey, PersistentDataType.STRING);
        if (old == null) return;
        StringBuilder sb = new StringBuilder();
        for (String part : old.split(";")) {
            if (part.isEmpty() || part.startsWith(dragon.toString())) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(part);
        }
        p.getPersistentDataContainer().set(lastKey, PersistentDataType.STRING, sb.toString());
    }

    /** A dragon's chunk unloads: write down where it was. */
    @EventHandler
    public void onEntitiesUnload(org.bukkit.event.world.EntitiesUnloadEvent ev) {
        for (Entity en : ev.getEntities()) {
            if (!(en instanceof LivingEntity le)) continue;
            UUID t = tamer(le);
            if (t != null && plugin.animals().get(le) != null) remember(t, le);
        }
    }

    private final Map<UUID, Long> whistled = new HashMap<>();

    /** Blow the whistle: every tamed dragon of this player in the same world flies to them, loaded or not. */
    void whistle(Player p) {
        long now = System.currentTimeMillis();
        Long last = whistled.get(p.getUniqueId());
        if (last != null && now - last < 4000) return;
        whistled.put(p.getUniqueId(), now);
        p.getWorld().playSound(p.getLocation(), "wildlife:dragon.whistle", org.bukkit.SoundCategory.PLAYERS, 0.9f, 1f);
        plugin.adv().grant(p, "dragon_whistle");
        String all = p.getPersistentDataContainer().get(lastKey, PersistentDataType.STRING);
        if (all == null || all.isEmpty()) {
            p.sendActionBar(Component.text("You have no dragon to call.", NamedTextColor.GRAY));
            return;
        }
        int called = 0, far = 0;
        for (String part : all.split(";")) {
            String[] f = part.split(",");
            if (f.length < 5) continue;
            UUID id;
            try { id = UUID.fromString(f[0]); } catch (IllegalArgumentException ex) { continue; }
            org.bukkit.World w = Bukkit.getWorld(f[1]);
            if (w == null || w != p.getWorld()) { far++; continue; }
            Entity en = Bukkit.getEntity(id);
            if (en instanceof LivingEntity le && le.isValid()) {
                if (summon(le, p)) called++;
                continue;
            }
            int cx, cz;
            try { cx = Integer.parseInt(f[2]) >> 4; cz = Integer.parseInt(f[4]) >> 4; } catch (NumberFormatException ex) { continue; }
            called++;
            w.getChunkAtAsync(cx, cz, true).thenAccept(ch -> {            // the chunk (and the dragon in it) comes back for a moment
                Entity again = Bukkit.getEntity(id);
                if (again instanceof LivingEntity le && le.isValid() && p.isOnline()) summon(le, p);
                else if (p.isOnline()) forget(p, id);                       // it is gone for good
            });
        }
        p.sendActionBar(Component.text(called > 0 ? "A shrill whistle - your dragon answers." : far > 0 ? "Your dragon is in another world." : "No answer.", called > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
    }

    private boolean summon(LivingEntity e, Player p) {
        if (!p.getUniqueId().equals(tamer(e)) || e.getWorld() != p.getWorld() || ridden(e)) return false;
        if (isSitting(e)) {
            e.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) 0);
            plugin.fmm().stop(e);
        }
        Location at = safeSpot(p, e);
        e.teleport(at);
        e.getWorld().spawnParticle(Particle.CLOUD, at, 8, 0.3, 0.2, 0.3, 0.01);
        remember(p.getUniqueId(), e);
        return true;
    }

    /** A hatchling: tamed from the first second, no coal needed. */
    void adopt(Animals.Animal a, Player p) {
        LivingEntity e = a.entity;
        e.getPersistentDataContainer().set(tamerKey, PersistentDataType.STRING, p.getUniqueId().toString());
        e.getPersistentDataContainer().set(plugin.animals().keptKey, PersistentDataType.BYTE, (byte) 1);
        if (e instanceof Parrot par) { par.setTamed(true); par.setOwner(p); }
        remember(p.getUniqueId(), e);
    }

    private void tame(Animals.Animal a, Player p) {
        LivingEntity e = a.entity;
        e.getPersistentDataContainer().set(tamerKey, PersistentDataType.STRING, p.getUniqueId().toString());
        e.getPersistentDataContainer().set(plugin.animals().keptKey, PersistentDataType.BYTE, (byte) 1);
        if (e instanceof Parrot par) {
            par.setTamed(true);
            par.setOwner(p);
        }
        e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getLocation().add(0, e.getHeight() * 0.7, 0), 18, 0.5, 0.4, 0.5, 0);
        e.getWorld().playSound(e.getLocation(), "wildlife:dragon.ambient", 1f, 0.65f);
        p.sendMessage(Component.text("The dragon folds its wings and bows its helmeted head - it is yours.", NamedTextColor.GOLD));
        plugin.adv().grant(p, "dragon_tame");
        remember(p.getUniqueId(), e);
        var pdc = p.getPersistentDataContainer();
        int n = 1 + (pdc.get(dragonsKey, PersistentDataType.INTEGER) == null ? 0 : pdc.get(dragonsKey, PersistentDataType.INTEGER));
        pdc.set(dragonsKey, PersistentDataType.INTEGER, n);
        if (n >= 2) plugin.adv().grant(p, "dragon_hoarder");
    }

    // ------------------------------------------------------------ input + physics

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        LivingEntity v = dragonOf(e.getPlayer().getVehicle());
        if (v == null) return;
        Animals.Animal a = plugin.animals().get(v);
        if (a == null || a.species != Species.DRAGON) return;
        Input in = inputs.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Input());
        in.forward = e.getInput().isForward();
        in.back = e.getInput().isBackward();
        in.left = e.getInput().isLeft();
        in.right = e.getInput().isRight();
        in.jump = e.getInput().isJump();
        in.sprint = e.getInput().isSprint();
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p) {
            inputs.remove(p.getUniqueId());
            LivingEntity le = dragonOf(e.getDismounted());
            if (le != null) {
                minds.remove(le.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> endRide(le));
                Animals.Animal a = plugin.animals().get(le);
                if (a != null && a.species == Species.DRAGON) {
                    plugin.adv().grant(p, "dragon_landing");
                    p.setFallDistance(0);                                  // stepping off mid-air: a soft landing, not a broken leg
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING, 20 * 8, 0, false, false, true));
                }
            }
        }
    }

    /** On the saddle the rider takes no damage from the ride itself: falls, flying into walls, being squeezed or stuck in blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onRiderHurt(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        LivingEntity v = dragonOf(p.getVehicle());
        if (v == null) return;
        Animals.Animal a = plugin.animals().get(v);
        if (a == null || a.species != Species.DRAGON) return;
        switch (e.getCause()) {
            case FALL, FLY_INTO_WALL, SUFFOCATION, CRAMMING -> e.setCancelled(true);
            default -> { }
        }
    }

    // ------------------------------------------------------------ the mind: one state machine per dragon

    private enum Mode { ROOST, TAKEOFF, FLIGHT, LAND }

    /** Transient per-dragon state (not saved: a reloaded dragon simply roosts again). */
    private static final class Mind {
        Mode mode = Mode.ROOST;
        long until;                       // game tick at which the current mode ends
        Vector wp;                        // current waypoint
        Location home;                    // wild: where it lives
        boolean flyAnim;
        Location ownerAt;                 // tamed: where the owner was a moment ago
        int still;                        // ticks the owner has stood still
        double orbit;                     // tamed: angle around the owner
        Location nest;                    // wild: a free nest it settles near
    }

    private final Map<UUID, Mind> minds = new HashMap<>();

    /** Every tick: steer the dragons that carry a rider, let every other one think (only near players). */
    void tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Animals.Animal a : plugin.animals().all()) {
            if (a.species != Species.DRAGON || !a.attached) continue;
            LivingEntity e = a.entity;
            Player rider = riderOf(e);
            Mind m = minds.computeIfAbsent(e.getUniqueId(), k -> new Mind());
            if (rider != null) {
                steer(e, rider, r, m);
                if (e.getLocation().getY() > 100) plugin.adv().grant(rider, "dragon_flight");
                else if (e.getLocation().getY() < 40) plugin.adv().grant(rider, "dragon_caver");
                Input in = inputs.get(rider.getUniqueId());
                if (in != null && in.sprint) plugin.adv().grant(rider, "dragon_speed");
                if (e.getWorld().getTime() > 13000 && e.getWorld().getTime() < 23000) plugin.adv().grant(rider, "dragon_night");
                continue;
            }
            if (nearest(e, 96) == null && tamer(e) == null) continue;      // nobody around: a wild dragon sleeps for free
            long now = e.getWorld().getFullTime();
            if (isSitting(e)) {
                settle(e, m);                                              // sat down in mid-air: come down first
            } else if (tamer(e) == null) {
                wild(a, e, m, now, r);
            } else {
                Player owner = Bukkit.getPlayer(tamer(e));
                if (owner != null) plugin.adv().grant(owner, "dragon_follow");
                if (owner != null && now % 100 == 0) remember(owner.getUniqueId(), e);
                companion(e, m, owner, now, r);
            }
        }
        moveSeats();
    }

    // ---- shared movement helpers

    /** Smoothly steer towards a wanted velocity, turn the body into the direction of travel, dodge walls. */
    private void glide(LivingEntity e, Vector want, double accel) {
        Vector v = e.getVelocity();
        double len = want.length();
        if (len > 0.02) {
            Vector dir = want.clone().normalize();
            Vector steer = pathAround(e, dir);
            if (steer != dir) {                                            // something in the way: take the clearest way round, slower
                want = steer.clone().normalize().multiply(len * 0.8);
            }
        }
        v.add(want.subtract(v).multiply(accel));
        e.setVelocity(v);
        if (Math.hypot(v.getX(), v.getZ()) > 0.03)
            turn(e, (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())), 8f);
    }

    // ---- pathfinding for a flier: feelers instead of a fixed sidestep

    private final Map<UUID, Integer> side = new HashMap<>();   // which way a dragon last swerved (+1 / -1): keeps it from dithering

    /** Free distance along dir for a body about 2.4 blocks wide and as tall as the dragon: five feelers (centre, left, right, up, down). */
    private static double clearance(LivingEntity e, Vector dir, double max) {
        Location c = e.getLocation().add(0, e.getHeight() * 0.5, 0);
        Vector n = dir.clone().normalize();
        Vector sideV = new Vector(-n.getZ(), 0, n.getX());
        if (sideV.lengthSquared() < 1.0e-6) sideV = new Vector(1, 0, 0);
        sideV.normalize();
        double w = Math.max(0.9, e.getWidth() * 0.6), h = Math.max(0.7, e.getHeight() * 0.45);
        Vector[] offs = {new Vector(), sideV.clone().multiply(w), sideV.clone().multiply(-w), new Vector(0, h, 0), new Vector(0, -h, 0)};
        double best = max;
        for (Vector o : offs) {
            RayTraceResult hit = e.getWorld().rayTraceBlocks(c.clone().add(o), n, max, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitPosition() != null) best = Math.min(best, hit.getHitPosition().distance(c.clone().add(o).toVector()));
        }
        return best;
    }

    /** The wanted direction when it is clear, otherwise the clearest nearby one (sideways first, then up), with a memory of the last swerve. */
    private Vector pathAround(LivingEntity e, Vector dir) {
        final double look = 7.0;
        double straight = clearance(e, dir, look);
        if (straight >= look) {
            side.remove(e.getUniqueId());
            return dir;
        }
        int bias = side.getOrDefault(e.getUniqueId(), 0);
        double yaw0 = Math.atan2(dir.getZ(), dir.getX());
        double pitch0 = Math.asin(Math.max(-1, Math.min(1, dir.getY())));
        double bestScore = straight - 1.0;                                 // staying put must be clearly worse than turning
        Vector best = null;
        int bestSide = 0;
        for (int sgn : new int[]{1, -1}) {
            for (double deg : new double[]{25, 50, 80, 110}) {
                for (double up : new double[]{0, 35}) {
                    double yaw = yaw0 + sgn * Math.toRadians(deg), pit = Math.min(Math.toRadians(70), pitch0 + Math.toRadians(up));
                    Vector cand = new Vector(Math.cos(yaw) * Math.cos(pit), Math.sin(pit), Math.sin(yaw) * Math.cos(pit));
                    double score = clearance(e, cand, look) - deg * 0.012 - up * 0.01 + (sgn == bias ? 0.8 : 0);
                    if (score > bestScore) { bestScore = score; best = cand; bestSide = sgn; }
                }
            }
        }
        if (best == null) {                                                // boxed in: go straight up
            Vector upV = new Vector(0, 1, 0);
            return clearance(e, upV, 3) > 1.5 ? upV : dir;
        }
        side.put(e.getUniqueId(), bestSide);
        if (straight < 2.0) best.setY(best.getY() + 0.25);                // very close: gain height as well
        return best;
    }

    private static void turn(LivingEntity e, float yaw, float maxStep) {
        float cur = e.getLocation().getYaw();
        float d = ((yaw - cur) % 360 + 540) % 360 - 180;
        e.setRotation(cur + Math.max(-maxStep, Math.min(maxStep, d)), 0);
    }

    /** Free height above this spot (up to cap blocks). */
    private static double headroom(Location l, double cap) {
        RayTraceResult r = l.getWorld().rayTraceBlocks(l, new Vector(0, 1, 0), cap, FluidCollisionMode.NEVER, true);
        return r == null || r.getHitPosition() == null ? cap : r.getHitPosition().getY() - l.getY();
    }

    /** Y of the floor straight below (null if none within max blocks). */
    private static Double floorBelow(Location l, double max) {
        RayTraceResult r = l.getWorld().rayTraceBlocks(l.clone().add(0, 0.3, 0), new Vector(0, -1, 0), max, FluidCollisionMode.NEVER, true);
        return r == null || r.getHitPosition() == null ? null : r.getHitPosition().getY();
    }

    private void anim(LivingEntity e, Mind m, boolean flying) {
        if (m.flyAnim == flying) return;
        m.flyAnim = flying;
        if (flying) plugin.fmm().play(e, "fly", true);
        else plugin.fmm().stop(e);                                         // FMM falls back to idle/walk on its own
    }

    /** Lower to the floor and stand there. Returns true once it stands. */
    private boolean descend(LivingEntity e, Mind m) {
        Double fy = floorBelow(e.getLocation(), 60);
        if (fy == null) {                                                  // nothing below: just hover
            glide(e, new Vector(0, 0, 0), 0.2);
            return false;
        }
        double h = e.getLocation().getY() - fy;
        if (h < 0.15) {
            Vector v = e.getVelocity();
            e.setVelocity(new Vector(v.getX() * 0.5, 0, v.getZ() * 0.5));
            anim(e, m, false);
            return true;
        }
        glide(e, new Vector(0, -Math.min(0.28, 0.04 + h * 0.06), 0), 0.16);
        return false;
    }

    private void settle(LivingEntity e, Mind m) {
        m.mode = Mode.ROOST;
        if (!descend(e, m)) anim(e, m, true);
    }

    // ---- wild dragons: roost, take off, patrol round the home cave, land again

    private void wild(Animals.Animal a, LivingEntity e, Mind m, long now, ThreadLocalRandom r) {
        Location at = e.getLocation();
        if (m.home == null) m.home = at.clone();
        if (now % 100 == 0 && (m.mode == Mode.ROOST || m.mode == Mode.FLIGHT)) {        // a free nest nearby draws a wild dragon in
            Location free = plugin.nests().freeNear(at, plugin.nests().range());
            m.nest = free;
            if (free != null && (m.home == null || m.home.getWorld() != free.getWorld() || m.home.distanceSquared(free) > 400)) m.home = free.clone();
        }
        Player near = nearest(e, 6.0);
        if (near != null && !near.isSneaking() && now - lastRoar.getOrDefault(e.getUniqueId(), -9999L) > 400) {
            lastRoar.put(e.getUniqueId(), now);
            plugin.fmm().play(e, "roar", false);
            e.getWorld().playSound(at, "wildlife:dragon.ambient", 1f, 0.5f);
            plugin.adv().grant(near, "dragon_wild");
            plugin.adv().grant(near, "dragon_roar");
            if (m.mode == Mode.ROOST) {                                    // crowded: it takes to the air
                m.mode = Mode.TAKEOFF;
                m.until = now + 60;
            }
        }
        switch (m.mode) {
            case ROOST -> {
                if (!descend(e, m)) return;
                if (m.until == 0) m.until = now + 400 + r.nextInt(900);    // 20-65 s on the floor
                if (now >= m.until && headroom(at, 8) > 5) {
                    m.mode = Mode.TAKEOFF;
                    m.until = now + 60;
                }
            }
            case TAKEOFF -> {
                anim(e, m, true);
                glide(e, new Vector(0, 0.2, 0), 0.12);
                if (now >= m.until || headroom(at, 6) < 2.5) {
                    m.mode = Mode.FLIGHT;
                    m.until = now + 500 + r.nextInt(700);                  // 25-60 s on the wing
                    m.wp = null;
                }
            }
            case FLIGHT -> {
                anim(e, m, true);
                if (m.wp == null || at.toVector().distanceSquared(m.wp) < 9) m.wp = pickWaypoint(e, m, r);
                if (m.wp == null) {
                    m.mode = Mode.LAND;
                    return;
                }
                Vector to = m.wp.clone().subtract(at.toVector());
                double dist = to.length();
                glide(e, to.normalize().multiply(Math.min(0.2, 0.06 + dist * 0.02)), 0.07);
                if (r.nextInt(220) == 0) e.getWorld().playSound(at, "wildlife:dragon.ambient", 0.7f, 0.9f);
                if (now >= m.until) m.mode = Mode.LAND;
            }
            case LAND -> {
                anim(e, m, true);
                if (m.nest != null && m.nest.getWorld() == at.getWorld()) {                // settle on the nest itself
                    Vector to = m.nest.toVector().subtract(at.toVector());
                    to.setY(0);
                    if (to.lengthSquared() > 4) glide(e, to.normalize().multiply(0.15), 0.1);
                }
                if (descend(e, m)) {
                    m.mode = Mode.ROOST;
                    m.until = 0;
                }
            }
        }
    }

    /** A spot next to the owner where the dragon fits (2 free blocks high, nothing solid): behind, beside, above, else the owner's own spot. */
    private Location safeSpot(Player owner, LivingEntity e) {
        Location me = owner.getLocation();
        Vector back = me.getDirection().setY(0);
        if (back.lengthSquared() < 1.0e-4) back = new Vector(0, 0, 1);
        back.normalize().multiply(-1);
        Vector side = new Vector(-back.getZ(), 0, back.getX());
        double[][] tries = {{2.5, 0, 1.5}, {2.5, 2.5, 1.5}, {2.5, -2.5, 1.5}, {0, 0, 3.0}, {4.0, 0, 2.5}, {0, 0, 1.0}};
        int h = (int) Math.ceil(e.getHeight()) + 1;
        for (double[] t : tries) {
            Location c = me.clone().add(back.clone().multiply(t[0])).add(side.clone().multiply(t[1])).add(0, t[2], 0);
            boolean free = true;
            for (int dy = 0; dy < h && free; dy++) if (!c.clone().add(0, dy, 0).getBlock().isPassable()) free = false;
            if (free) return c;
        }
        return me.clone().add(0, 1.0, 0);
    }

    /** The dragon is made of brass: it cannot suffocate, drown or take falls. (It died "in a wall" after a /home.) */
    @EventHandler(ignoreCancelled = true)
    public void onDragonHurt(org.bukkit.event.entity.EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof LivingEntity le)) return;
        Animals.Animal a = plugin.animals().get(le);
        if (a == null || a.species != Species.DRAGON) return;
        switch (ev.getCause()) {
            case SUFFOCATION, FALL, FLY_INTO_WALL, CRAMMING, DROWNING -> ev.setCancelled(true);
            default -> { }
        }
    }

    /** A tamed, standing dragon farther away than this from its owner flies to them (a wolf uses 12). */
    private static final double TELEPORT_DIST = 24;

    /** The owner teleports (home, warp, /tpa): the dragon that was nearby comes along, like a dog does. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerTeleport(org.bukkit.event.player.PlayerTeleportEvent ev) {
        Player p = ev.getPlayer();
        Location from = ev.getFrom();
        Location to = ev.getTo();
        if (to == null || from.getWorld() != to.getWorld() || from.distanceSquared(to) < TELEPORT_DIST * TELEPORT_DIST) return;
        for (Animals.Animal a : plugin.animals().all()) {
            if (a.species != Species.DRAGON || !a.attached || !isTamedBy(a, p)) continue;
            LivingEntity e = a.entity;
            if (isSitting(e) || ridden(e) || e.getWorld() != from.getWorld()) continue;
            if (e.getLocation().distanceSquared(from) > 40 * 40) continue;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!e.isValid() || !p.isOnline() || p.getWorld() != e.getWorld()) return;
                Location at = safeSpot(p, e);
                e.teleport(at);
                e.getWorld().spawnParticle(Particle.CLOUD, at, 6, 0.3, 0.2, 0.3, 0.01);
            }, 5L);
        }
    }

    /** A free point within 24 blocks of home, 2-9 blocks up; null when the cave is too cramped to fly. */
    private Vector pickWaypoint(LivingEntity e, Mind m, ThreadLocalRandom r) {
        Location at = e.getLocation().add(0, e.getHeight() * 0.5, 0);
        for (int i = 0; i < 8; i++) {
            Vector p = m.home.toVector().add(new Vector(r.nextDouble(-24, 24), r.nextDouble(2, 9), r.nextDouble(-24, 24)));
            Vector d = p.clone().subtract(at.toVector());
            double len = d.length();
            if (len < 6) continue;
            RayTraceResult hit = at.getWorld().rayTraceBlocks(at, d.clone().normalize(), len, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) continue;        // a wall in the way
            Location pl = p.toLocation(at.getWorld());
            if (pl.getBlock().isSolid() || pl.clone().add(0, 1, 0).getBlock().isSolid()) continue;
            return p;
        }
        return null;
    }

    // ---- tamed dragons without a rider: stay close, circle, land beside a resting owner

    private void companion(LivingEntity e, Mind m, Player owner, long now, ThreadLocalRandom r) {
        if (owner == null || owner.getWorld() != e.getWorld()) return;
        Location at = e.getLocation();
        Location me = owner.getLocation();
        double dist = at.distance(me);
        if (dist > TELEPORT_DIST) {                                        // fell behind: catch up like a wolf does
            Location to = safeSpot(owner, e);
            e.teleport(to);
            e.getWorld().spawnParticle(Particle.CLOUD, to, 6, 0.3, 0.2, 0.3, 0.01);
            plugin.adv().grant(owner, "dragon_reunion");
            m.mode = Mode.FLIGHT;
            return;
        }
        if (m.ownerAt != null && m.ownerAt.getWorld() == me.getWorld() && m.ownerAt.distanceSquared(me) < 0.04) m.still++;
        else m.still = 0;
        m.ownerAt = me.clone();
        boolean resting = m.still > 140 && dist < 14;                        // owner has stood still for 4 s: come down beside them
        if (now % 100 == 0) plugin.nests().touch(e);
        if (resting) {
            if (m.mode != Mode.ROOST) m.mode = Mode.LAND;
            Location own = plugin.nests().claimedBy(e);                    // home first; else a free nest close to the owner
            Location nest = own != null && own.getWorld() == me.getWorld() && own.distanceSquared(me) < 24 * 24 ? own
                    : own == null ? plugin.nests().freeNear(me, 20) : null;
            if (nest != null) {
                Vector to = nest.toVector().subtract(at.toVector());
                to.setY(0);
                if (to.lengthSquared() > 2.25) glide(e, to.normalize().multiply(0.14), 0.1);
                if (descend(e, m)) {
                    m.mode = Mode.ROOST;
                    if (own == null && to.lengthSquared() <= 9 && owner != null && owner.hasPermission("wildlife.dragon.nest")) plugin.nests().claim(e, tamer(e), nest);   // landed on a free nest: it is theirs
                }
                return;
            }
            if (dist > 7.0) {                                              // land a few blocks away, never on top of the owner
                Vector to = me.toVector().subtract(at.toVector());
                to.setY(0);
                if (to.lengthSquared() > 0.01) glide(e, to.normalize().multiply(0.12), 0.1);
            }
            if (descend(e, m)) m.mode = Mode.ROOST;
            return;
        }
        m.mode = Mode.FLIGHT;
        anim(e, m, true);
        if (dist > 16) {                                                   // fly to the owner
            Vector to = me.toVector().add(new Vector(0, 5.0, 0)).subtract(at.toVector());
            glide(e, to.normalize().multiply(Math.min(0.5, 0.12 + dist * 0.018)), 0.09);
        } else {                                                           // lazy circles round the owner
            m.orbit += 0.022;
            Vector p = me.toVector().add(new Vector(Math.cos(m.orbit) * 10, 5.0 + Math.sin(m.orbit * 0.7) * 1.5, Math.sin(m.orbit) * 10));
            glide(e, p.subtract(at.toVector()).multiply(0.06), 0.09);
        }
    }

    // ---- riding: the rider is the pilot

    private void steer(LivingEntity e, Player rider, ThreadLocalRandom r, Mind m) {
        Input in = inputs.get(rider.getUniqueId());
        Location look = rider.getLocation();
        Animals.Animal self = plugin.animals().get(e);
        double fed = self == null ? 1 : self.hunger <= 20 ? 0.6 : self.hunger <= 50 ? 0.85 : self.hunger >= 90 ? 1.1 : 1;   // a hungry furnace is slow
        double speed = plugin.getConfig().getDouble("dragon.ride-speed", 0.55) * fed * (in != null && in.sprint ? 1.5 : 1);
        double pitch = Math.max(-55, Math.min(55, look.getPitch()));
        double yawR = Math.toRadians(look.getYaw()), pitchR = Math.toRadians(pitch);
        Vector fwd = new Vector(-Math.sin(yawR) * Math.cos(pitchR), -Math.sin(pitchR), Math.cos(yawR) * Math.cos(pitchR));
        Vector flat = new Vector(-Math.sin(yawR), 0, Math.cos(yawR));
        Vector right = new Vector(flat.getZ(), 0, -flat.getX());
        Vector want = new Vector();
        boolean moving = false;
        if (in != null) {
            if (in.forward) {
                want.add(fwd.clone().multiply(speed));                     // look up to climb, down to dive
                moving = true;
            }
            if (in.back) want.add(flat.clone().multiply(-speed * 0.35));   // brake, then back up a little
            if (in.left) want.add(right.clone().multiply(speed * 0.4));
            if (in.right) want.add(right.clone().multiply(-speed * 0.4));
            if (in.jump) want.add(new Vector(0, speed * 0.55, 0));         // straight up, hovering
        }
        Vector v = e.getVelocity();
        v.add(want.subtract(v).multiply(moving ? 0.07 : 0.1));             // inertia: it needs a moment to speed up and to stop
        e.setVelocity(v);
        turn(e, look.getYaw(), 9f);
        boolean flyingNow = Math.abs(v.getY()) > 0.02 || Math.hypot(v.getX(), v.getZ()) > 0.05;
        anim(e, m, flyingNow);
        if ((Math.abs(v.getY()) > 0.12 || (in != null && in.sprint)) && r.nextInt(3) == 0)   // steam from the neck vents
            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, e.getHeight() * 0.85, 0), 1, 0.1, 0.05, 0.1, 0.008);
    }

    // ------------------------------------------------------------ the saddle: our own seat entity
    // The base mob (a parrot) is tiny, so a rider put straight on it ends up INSIDE the dragon. Like FMM's
    // own mounts we seat the rider on an invisible Interaction that follows the dragon at saddle height.

    private static final Map<UUID, UUID> SEAT_OF = new HashMap<>();   // dragon -> seat
    private static final Map<UUID, UUID> DRAGON_OF = new HashMap<>(); // seat -> dragon

    private double seatHeight(LivingEntity e) {
        var sc = e.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        double k = sc == null ? 1 : sc.getValue() / 1.5;                 // the height below is for an adult at scale 1.5
        return plugin.getConfig().getDouble("dragon.seat-height", 2.0) * k;
    }

    private Location seatSpot(LivingEntity e) {
        return e.getLocation().add(e.getVelocity()).add(0, seatHeight(e), 0);   // one tick ahead: the rider does not trail behind
    }

    static boolean ridden(LivingEntity e) {
        UUID id = SEAT_OF.get(e.getUniqueId());
        return id != null && Bukkit.getEntity(id) instanceof Entity s && !s.getPassengers().isEmpty();
    }

    private static Player riderOf(LivingEntity e) {
        UUID id = SEAT_OF.get(e.getUniqueId());
        if (id == null || !(Bukkit.getEntity(id) instanceof Entity s)) return null;
        for (Entity p : s.getPassengers()) if (p instanceof Player pl) return pl;
        return null;
    }

    /** The dragon a vehicle (our seat) belongs to, or null. */
    static LivingEntity dragonOf(Entity vehicle) {
        if (vehicle == null) return null;
        UUID d = DRAGON_OF.get(vehicle.getUniqueId());
        return d != null && Bukkit.getEntity(d) instanceof LivingEntity le ? le : null;
    }

    private boolean seatRider(LivingEntity e, Player p) {
        endRide(e);
        Entity seat = e.getWorld().spawn(seatSpot(e), org.bukkit.entity.Interaction.class, i -> {
            i.setInteractionWidth(0.1f);
            i.setInteractionHeight(0f);
            i.setPersistent(false);
            i.setInvulnerable(true);
            i.setResponsive(false);
        });
        if (!seat.addPassenger(p)) { seat.remove(); return false; }
        SEAT_OF.put(e.getUniqueId(), seat.getUniqueId());
        DRAGON_OF.put(seat.getUniqueId(), e.getUniqueId());
        return true;
    }

    /** Remove the seat (the rider, if any, drops off it). */
    void endRide(LivingEntity e) {
        UUID id = SEAT_OF.remove(e.getUniqueId());
        if (id == null) return;
        DRAGON_OF.remove(id);
        Entity s = Bukkit.getEntity(id);
        if (s != null) { s.eject(); s.remove(); }
    }

    void removeSeats() {
        for (UUID id : new java.util.ArrayList<>(DRAGON_OF.keySet())) {
            Entity s = Bukkit.getEntity(id);
            if (s != null) { s.eject(); s.remove(); }
        }
        SEAT_OF.clear();
        DRAGON_OF.clear();
    }

    /** Every tick: carry each seat along with its dragon; drop seats whose dragon is gone or whose rider got off. */
    private void moveSeats() {
        for (Map.Entry<UUID, UUID> en : new java.util.ArrayList<>(SEAT_OF.entrySet())) {
            Entity d = Bukkit.getEntity(en.getKey());
            Entity s = Bukkit.getEntity(en.getValue());
            if (!(d instanceof LivingEntity le) || !d.isValid() || s == null || !s.isValid() || s.getPassengers().isEmpty()) {
                SEAT_OF.remove(en.getKey());
                DRAGON_OF.remove(en.getValue());
                if (s != null) { s.eject(); s.remove(); }
                continue;
            }
            s.teleport(seatSpot(le), io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    private static Player nearest(LivingEntity e, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }
}
