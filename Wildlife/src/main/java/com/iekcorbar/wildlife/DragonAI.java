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
    private final NamespacedKey tamerKey, sittingKey, dragonsKey;
    private final Map<UUID, Input> inputs = new HashMap<>();
    private final Map<UUID, Long> lastRoar = new HashMap<>();

    /** Brain's Control goal asks: is this dragon too busy (sitting / carrying) for its own brain? */
    static boolean blocked(Animals.Animal a) {
        return a.species == Species.DRAGON && (isSitting(a.entity) || !a.entity.getPassengers().isEmpty());
    }

    static boolean isSitting(LivingEntity e) {
        Byte b = e.getPersistentDataContainer().get(Keeps.SIT, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    static UUID tamer(LivingEntity e) {
        String s = e.getPersistentDataContainer().get(Keeps.TAMER, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
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
    }

    // ------------------------------------------------------------ right-click (from Animals.onInteract)

    /** True when the click is consumed here; false lets Breeding.use() do its love trick. */
    boolean interact(Animals.Animal a, Player p, ItemStack hand, boolean sneaking) {
        LivingEntity e = a.entity;
        Material held = hand.getType();
        if (held == Material.COAL || held == Material.CHARCOAL) {
            if (tamer(e) != null && tamer(e).equals(p.getUniqueId())) plugin.adv().grant(p, "dragon_feed");
            if (a.baby || tamer(e) != null) return false;                  // babies grow on it, the tamed breed on it
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight(), 0), 3, 0.3, 0.2, 0.3, 0);
            if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                tame(a, p);
                return true;
            }
            p.sendActionBar(Component.text("It eyes the coal. Not yet.", NamedTextColor.GRAY));
            return true;
        }
        if (!held.isAir() || a.baby || !p.getUniqueId().equals(tamer(e))) return false;   // strangers just look
        if (sneaking) {
            boolean sit = !isSitting(e);
            e.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) (sit ? 1 : 0));
            if (sit) {
                plugin.fmm().play(e, "sit", true);
                plugin.adv().grant(p, "dragon_sit");
            } else plugin.fmm().stop(e);
            p.sendActionBar(Component.text(sit ? "The dragon sits." : "The dragon gets up.", NamedTextColor.GRAY));
            return true;
        }
        if (isSitting(e)) {                                                // mounting gets it back on its feet
            e.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) 0);
            plugin.fmm().stop(e);
        }
        if (!e.addPassenger(p)) {
            p.sendActionBar(Component.text("There is no room on the saddle.", NamedTextColor.RED));
            return true;
        }
        plugin.adv().grant(p, "dragon_rider");
        e.getWorld().playSound(e.getLocation(), "wildlife:dragon.ambient", 0.9f, 0.8f);
        return true;
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
        var pdc = p.getPersistentDataContainer();
        int n = 1 + (pdc.get(dragonsKey, PersistentDataType.INTEGER) == null ? 0 : pdc.get(dragonsKey, PersistentDataType.INTEGER));
        pdc.set(dragonsKey, PersistentDataType.INTEGER, n);
        if (n >= 2) plugin.adv().grant(p, "dragon_hoarder");
    }

    // ------------------------------------------------------------ input + physics

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        if (!(e.getPlayer().getVehicle() instanceof LivingEntity v)) return;
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
            if (e.getDismounted() instanceof LivingEntity d0) minds.remove(d0.getUniqueId());
            if (e.getDismounted() instanceof LivingEntity le) {
                Animals.Animal a = plugin.animals().get(le);
                if (a != null && a.species == Species.DRAGON) plugin.adv().grant(p, "dragon_landing");
            }
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
                companion(e, m, owner, now, r);
            }
        }
    }

    // ---- shared movement helpers

    /** Smoothly steer towards a wanted velocity, turn the body into the direction of travel, dodge walls. */
    private void glide(LivingEntity e, Vector want, double accel) {
        Vector v = e.getVelocity();
        Vector dir = want.clone();
        if (dir.lengthSquared() > 1.0e-4) {                                // look 4 blocks ahead; a wall means climb and sidestep
            RayTraceResult hit = e.getWorld().rayTraceBlocks(e.getLocation().add(0, e.getHeight() * 0.5, 0), dir.clone().normalize(),
                    4.0, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) {
                want.setY(want.getY() + 0.14);
                double yaw = Math.atan2(dir.getZ(), dir.getX()) + 0.9;
                want.setX(want.getX() * 0.35 + Math.cos(yaw) * 0.08);
                want.setZ(want.getZ() * 0.35 + Math.sin(yaw) * 0.08);
            }
        }
        v.add(want.subtract(v).multiply(accel));
        e.setVelocity(v);
        if (Math.hypot(v.getX(), v.getZ()) > 0.03)
            turn(e, (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())), 6f);
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
                if (descend(e, m)) {
                    m.mode = Mode.ROOST;
                    m.until = 0;
                }
            }
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
        if (dist > 48) {                                                   // fell behind: catch up like a wolf does
            Location to = me.clone().add(me.getDirection().setY(0).multiply(-2.5)).add(0, 1.5, 0);
            e.teleport(to);
            e.getWorld().spawnParticle(Particle.CLOUD, to, 6, 0.3, 0.2, 0.3, 0.01);
            plugin.adv().grant(owner, "dragon_reunion");
            m.mode = Mode.FLIGHT;
            return;
        }
        if (m.ownerAt != null && m.ownerAt.getWorld() == me.getWorld() && m.ownerAt.distanceSquared(me) < 0.04) m.still++;
        else m.still = 0;
        m.ownerAt = me.clone();
        boolean resting = m.still > 80 && dist < 7;                        // owner has stood still for 4 s: come down beside them
        if (resting) {
            if (m.mode != Mode.ROOST) m.mode = Mode.LAND;
            if (dist > 3.5) {
                Vector to = me.toVector().subtract(at.toVector());
                to.setY(0);
                if (to.lengthSquared() > 0.01) glide(e, to.normalize().multiply(0.12), 0.1);
            }
            if (descend(e, m)) m.mode = Mode.ROOST;
            return;
        }
        m.mode = Mode.FLIGHT;
        anim(e, m, true);
        if (dist > 9) {                                                    // fly to the owner
            Vector to = me.toVector().add(new Vector(0, 3.0, 0)).subtract(at.toVector());
            glide(e, to.normalize().multiply(Math.min(0.5, 0.12 + dist * 0.018)), 0.09);
        } else {                                                           // lazy circles round the owner
            m.orbit += 0.035;
            Vector p = me.toVector().add(new Vector(Math.cos(m.orbit) * 5, 3.0 + Math.sin(m.orbit * 0.7) * 1.2, Math.sin(m.orbit) * 5));
            glide(e, p.subtract(at.toVector()).multiply(0.06), 0.09);
        }
    }

    // ---- riding: the rider is the pilot

    private void steer(LivingEntity e, Player rider, ThreadLocalRandom r, Mind m) {
        Input in = inputs.get(rider.getUniqueId());
        Location look = rider.getLocation();
        double speed = plugin.getConfig().getDouble("dragon.ride-speed", 0.55) * (in != null && in.sprint ? 1.5 : 1);
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
        turn(e, look.getYaw(), 5f);
        boolean flyingNow = Math.abs(v.getY()) > 0.02 || Math.hypot(v.getX(), v.getZ()) > 0.05;
        anim(e, m, flyingNow);
        if ((Math.abs(v.getY()) > 0.12 || (in != null && in.sprint)) && r.nextInt(3) == 0)   // steam from the neck vents
            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, e.getHeight() * 0.85, 0), 1, 0.1, 0.05, 0.1, 0.008);
    }

    private static Player riderOf(LivingEntity e) {
        for (Entity p : e.getPassengers())
            if (p instanceof Player pl) return pl;
        return null;
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
