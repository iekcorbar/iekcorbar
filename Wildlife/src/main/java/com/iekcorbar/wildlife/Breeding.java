package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Breeding and growing up, the same for every species (the base mobs' own breeding stays off, so it also works for the
 * parrot and the bees underneath). Feed an adult its food (right-click) and it falls in love for 30 seconds; two animals
 * of one kind in love within a few blocks of each other walk together and a baby appears. A baby is half the size and
 * grows up after a while - unless it was given a golden dandelion, which keeps it small until it gets another one.
 * Babies and parents are marked with world time in their own data, so all of this survives a restart.
 */
final class Breeding {
    private final WildlifePlugin plugin;
    private final Animals animals;

    Breeding(WildlifePlugin plugin, Animals animals) {
        this.plugin = plugin;
        this.animals = animals;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("breeding.enabled", true);
    }

    long growTicks() {
        return 20L * 60 * Math.max(1, plugin.getConfig().getInt("breeding.grow-minutes", 20));
    }

    private long cooldownTicks() {
        return 20L * 60 * Math.max(1, plugin.getConfig().getInt("breeding.cooldown-minutes", 5));
    }

    private static void say(Player p, String text, NamedTextColor c) {
        p.sendActionBar(Component.text(text, c));
    }

    private static void consume(Player p, ItemStack hand) {
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
    }

    private static void hearts(LivingEntity e, int n) {
        e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight() + 0.2, 0), n, 0.3, 0.2, 0.3, 0);
    }

    /** A player right-clicked animal `a` with `hand` (main hand). */
    void use(Animals.Animal a, Player p, ItemStack hand) {
        if (!enabled()) return;
        LivingEntity e = a.entity;
        long now = e.getWorld().getFullTime();
        if (hand.getType() == org.bukkit.Material.GOLDEN_DANDELION) {
            if (!a.baby) {
                say(p, "Only a baby can be kept small.", NamedTextColor.GRAY);
                return;
            }
            var pdc = e.getPersistentDataContainer();
            if (!a.frozen) {
                a.frozen = true;
                a.frozenLeft = Math.max(0, a.growAt - now);
                pdc.set(animals.frozenKey, PersistentDataType.LONG, a.frozenLeft);
                pdc.set(animals.keptKey, PersistentDataType.BYTE, (byte) 1);   // a pet now: it stays when its chunk unloads
                plugin.adv().grant(p, "forever_young");
                say(p, "It will stay a baby. Give it another golden dandelion to let it grow.", NamedTextColor.GOLD);
            } else {
                a.frozen = false;
                a.growAt = now + a.frozenLeft;
                pdc.remove(animals.frozenKey);
                pdc.set(animals.growKey, PersistentDataType.LONG, a.growAt);
                say(p, "It will grow up again.", NamedTextColor.GREEN);
            }
            consume(p, hand);
            e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getLocation().add(0, e.getHeight() * 0.6, 0), 8, 0.3, 0.3, 0.3, 0);
            return;
        }
        if (!a.species.isFood(hand.getType())) return;
        if (a.baby) {
            if (!a.frozen) {   // like the vanilla animals: food speeds a baby up by a tenth of what is left
                a.growAt -= Math.max(0, a.growAt - now) / 10;
                e.getPersistentDataContainer().set(animals.growKey, PersistentDataType.LONG, a.growAt);
                consume(p, hand);
                hearts(e, 3);
            }
            return;
        }
        if (now < a.breedAfter) {
            say(p, "It isn't ready for another baby yet.", NamedTextColor.GRAY);
            return;
        }
        if (a.loveUntil > plugin.tick) return;
        int cap = plugin.getConfig().getInt("breeding.max-near", 10);
        if (animals.countNear(e.getLocation(), 32, a.species) >= cap) {
            say(p, "Too crowded here for more " + a.species.displayName.toLowerCase() + "s.", NamedTextColor.GRAY);
            return;
        }
        a.loveUntil = plugin.tick + 600;
        plugin.adv().grant(p, "love");
        consume(p, hand);
        hearts(e, 6);
    }

    /** Called for every animal each 10 ticks: babies grow up, animals in love look for each other. */
    void tick(Animals.Animal a, long tick) {
        LivingEntity e = a.entity;
        if (a.baby) {
            if (!a.frozen && e.getWorld().getFullTime() >= a.growAt && !animals.grow(a)) a.growAt += 200;   // model missing: try again later
            return;
        }
        if (a.loveUntil <= tick) return;
        if (tick % 40 == 0) hearts(e, 1);
        Animals.Animal mate = null;
        double best = 64;   // within 8 blocks
        for (Animals.Animal o : animals.all()) {
            if (o == a || o.baby || o.species != a.species || o.loveUntil <= tick || o.entity.getWorld() != e.getWorld()) continue;
            double d = o.entity.getLocation().distanceSquared(e.getLocation());
            if (d < best) {
                best = d;
                mate = o;
            }
        }
        if (mate == null) return;
        if (best > 6) {
            if (e instanceof Mob m) m.getPathfinder().moveTo(mate.entity.getLocation(), 1.0);
            return;
        }
        birth(a, mate);
    }

    private void birth(Animals.Animal a, Animals.Animal b) {
        long now = a.entity.getWorld().getFullTime();
        for (Animals.Animal x : new Animals.Animal[]{a, b}) {
            x.loveUntil = 0;
            x.breedAfter = now + cooldownTicks();
            x.entity.getPersistentDataContainer().set(animals.breedKey, PersistentDataType.LONG, x.breedAfter);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean rare = a.rare || b.rare ? r.nextDouble() < 0.4 : r.nextDouble() < plugin.spawner().rareChance();
        Location at = a.entity.getLocation().add(b.entity.getLocation()).multiply(0.5);
        Animals.Animal child = animals.spawn(a.species, at, rare, true, null);
        hearts(a.entity, 5);
        hearts(b.entity, 5);
        if (child != null && (animals.kept(a.entity) || animals.kept(b.entity)))
            child.entity.getPersistentDataContainer().set(animals.keptKey, PersistentDataType.BYTE, (byte) 1);
        if (child != null) for (Player near : a.entity.getWorld().getNearbyPlayers(at, 24)) plugin.adv().grant(near, "baby");
        if (child != null) a.entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.add(0, 0.4, 0), 10, 0.4, 0.3, 0.4, 0);
    }

    /** All loved-up animals of one species (for a quick status in /wildlife info). */
    int inLove() {
        int n = 0;
        for (Animals.Animal a : new ArrayList<>(animals.all())) if (a.loveUntil > plugin.tick) n++;
        return n;
    }
}
