package com.iekcorbar.wildlife;

import org.bukkit.event.player.PlayerInputEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
    private final NamespacedKey tamerKey, sittingKey;
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
    }

    // ------------------------------------------------------------ right-click (from Animals.onInteract)

    /** True when the click is consumed here; false lets Breeding.use() do its love trick. */
    boolean interact(Animals.Animal a, Player p, ItemStack hand, boolean sneaking) {
        LivingEntity e = a.entity;
        Material held = hand.getType();
        if (held == Material.COAL || held == Material.CHARCOAL) {
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
            if (sit) plugin.fmm().play(e, "sit", true); else plugin.fmm().stop(e);
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
        if (e.getEntity() instanceof Player p) inputs.remove(p.getUniqueId());
    }

    /** Every tick: steer the dragons that carry a rider, keep the tamed ones near their owner. */
    void tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Animals.Animal a : plugin.animals().all()) {
            if (a.species != Species.DRAGON || !a.attached) continue;
            LivingEntity e = a.entity;
            Player rider = riderOf(e);
            if (rider != null) {
                steer(e, rider, r);
                if (e.getLocation().getY() > 100) plugin.adv().grant(rider, "dragon_flight");
            } else if (tamer(e) == null) {                                 // wild: roar at anyone too close
                long now = e.getWorld().getFullTime();
                Player near = nearest(e, 4.0);
                if (near != null && !near.isSneaking() && now - lastRoar.getOrDefault(e.getUniqueId(), -9999L) > 400) {
                    lastRoar.put(e.getUniqueId(), now);
                    plugin.fmm().play(e, "roar", false);
                    e.getWorld().playSound(e.getLocation(), "wildlife:dragon.ambient", 1f, 0.5f);
                }
            } else {
                follow(e);
            }
        }
    }

    private void steer(LivingEntity e, Player rider, ThreadLocalRandom r) {
        Input in = inputs.get(rider.getUniqueId());
        Location look = rider.getLocation();
        Vector fwd = new Vector(-Math.sin(Math.toRadians(look.getYaw())), 0, Math.cos(Math.toRadians(look.getYaw())));
        Vector right = new Vector(fwd.getZ(), 0, -fwd.getX());
        double speed = plugin.getConfig().getDouble("dragon.ride-speed", 0.40) * (in != null && in.sprint ? 1.35 : 1);
        Vector want = new Vector();
        if (in != null) {
            if (in.forward) want.add(fwd.clone().multiply(speed));
            if (in.back) want.add(fwd.clone().multiply(-speed * 0.55));
            if (in.left) want.add(right.clone().multiply(speed * 0.5));
            if (in.right) want.add(right.clone().multiply(-speed * 0.5));
        }
        Vector v = e.getVelocity();
        v.setX(v.getX() + (want.getX() - v.getX()) * 0.18);
        v.setZ(v.getZ() + (want.getZ() - v.getZ()) * 0.18);
        double vy = v.getY();
        if (in != null && in.jump) vy = Math.min(vy + 0.03, 0.42);
        else if (in != null && in.back) vy = Math.max(vy - 0.02, -0.42);
        else vy += (-0.02 - vy) * 0.08;                                    // no keys: hold a slow hover
        v.setY(vy);
        e.setVelocity(v);
        float cur = e.getLocation().getYaw();                              // the body turns where the rider looks
        float d = ((look.getYaw() - cur) % 360 + 540) % 360 - 180;
        e.setRotation(cur + (float) Math.max(-9, Math.min(9, d * 0.3f)), 0);
        if (Math.abs(vy) > 0.08 && r.nextInt(3) == 0)                      // steam off the neck vents while climbing
            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, e.getHeight() * 0.85, 0), 1, 0.1, 0.05, 0.1, 0.008);
    }

    /** A tamed dragon keeps itself near its owner and teleports like a wolf when it fell behind. */
    private void follow(LivingEntity e) {
        if (isSitting(e) || !e.getPassengers().isEmpty()) return;
        UUID id = tamer(e);
        Player owner = id == null ? null : Bukkit.getPlayer(id);
        if (owner == null || owner.getWorld() != e.getWorld()) return;
        if (owner.getLocation().distanceSquared(e.getLocation()) > 34 * 34) {
            Location to = owner.getLocation().add(0, 1.2, 0);
            e.teleport(to);
            e.getWorld().spawnParticle(Particle.CLOUD, to, 6, 0.3, 0.2, 0.3, 0.01);
        }
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
