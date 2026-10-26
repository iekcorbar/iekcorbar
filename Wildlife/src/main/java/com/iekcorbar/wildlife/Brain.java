package com.iekcorbar.wildlife;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One calm, pig-like brain for every ground animal: wander around on its own, run away when hurt or when a hostile mob
 * comes near. The goals of the base mob itself (horse rearing, an ocelot hunting chickens, a turtle going home, ...)
 * are removed; only swimming and looking around stay. Dangerous species (bear, boar) keep their vanilla AI, and
 * flying species keep theirs.
 */
final class Brain {
    static final Set<Species> DANGEROUS = EnumSet.of(Species.BEAR, Species.BOAR);

    private final WildlifePlugin plugin;
    private final GoalKey<Mob> wanderKey, panicKey;

    Brain(WildlifePlugin plugin) {
        this.plugin = plugin;
        wanderKey = GoalKey.of(Mob.class, new NamespacedKey(plugin, "wander"));
        panicKey = GoalKey.of(Mob.class, new NamespacedKey(plugin, "panic"));
    }

    /** Only adult bears and boars fight back; their young are as calm as everything else. */
    static boolean dangerous(Animals.Animal a) {
        return !a.baby && DANGEROUS.contains(a.species);
    }

    static boolean tame(Animals.Animal a) {
        return a.species.habitat != Species.Habitat.AIR && !dangerous(a);
    }

    void apply(Animals.Animal a) {
        if (!(a.entity instanceof Mob m) || a.species.habitat == Species.Habitat.POND) return; // fish keep their own swim brain
        MobGoals g = Bukkit.getMobGoals();
        if (!tame(a)) {
            // AIR species keep their (flying) base brain, but these vanilla habits do not fit a wild model:
            // parrots land on the player's shoulder and beelines to hives/flowers will empty the scene.
            if (a.species == Species.SONGBIRD) strip(g, m, VanillaGoal.PARROT_LAND_ON_OWNERS_SHOULDER);
            if (m instanceof org.bukkit.entity.Bee) {
                strip(g, m, VanillaGoal.BEE_LOCATE_HIVE);
                strip(g, m, VanillaGoal.BEE_GO_TO_HIVE);
                strip(g, m, VanillaGoal.BEE_ENTER_HIVE);
                strip(g, m, VanillaGoal.BEE_GO_TO_KNOWN_FLOWER);
                strip(g, m, VanillaGoal.BEE_POLLINATE);
                strip(g, m, VanillaGoal.BEE_GROW_CROP);
                strip(g, m, VanillaGoal.BEE_VALIDATE_FLOWER);
                strip(g, m, VanillaGoal.BEE_VALIDATE_HIVE);
                strip(g, m, VanillaGoal.BEE_BECOME_ANGRY);
                strip(g, m, VanillaGoal.BEE_ATTACK);
            }
            return;
        }
        if (g.hasGoal(m, wanderKey)) return;
        for (Goal<Mob> goal : new ArrayList<>(g.getAllGoals(m))) {
            GoalKey<Mob> k = goal.getKey();
            if (k.equals(VanillaGoal.FLOAT) || k.equals(VanillaGoal.LOOK_AT_PLAYER) || k.equals(VanillaGoal.RANDOM_LOOK_AROUND)) continue;
            g.removeGoal(m, goal);
        }
        g.removeAllGoals(m, GoalType.TARGET); // an ocelot base would otherwise hunt our ducks
        g.addGoal(m, 1, new Panic(m, a));
        g.addGoal(m, 5, new Wander(m, a));
    }

    // ------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static void strip(MobGoals g, Mob m, com.destroystokyo.paper.entity.ai.GoalKey<?> key) {
        g.removeGoal(m, (com.destroystokyo.paper.entity.ai.GoalKey<Mob>) key);
    }

    private static boolean unsafeGround(Block ground) {
        Material t = ground.getType();
        return t == Material.LAVA || t == Material.MAGMA_BLOCK || t == Material.CACTUS || t == Material.FIRE || t == Material.CAMPFIRE
                || t == Material.SOUL_CAMPFIRE || t == Material.SWEET_BERRY_BUSH || t == Material.POWDER_SNOW || t == Material.WITHER_ROSE;
    }

    /** A standing spot around x/z near the animal's height, or null. wantWater: the surface of water, else dry land. */
    private static Location spot(World w, double x, double z, double y, boolean wantWater) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return null;
        int hy = w.getHighestBlockYAt(bx, bz);
        if (Math.abs(hy - y) > 5) return null;
        Block top = w.getBlockAt(bx, hy, bz);
        if (unsafeGround(top)) return null;
        boolean water = top.getType() == Material.WATER || top.getType() == Material.BUBBLE_COLUMN;
        if (wantWater) return water ? new Location(w, bx + 0.5, hy + 0.9, bz + 0.5) : null;
        if (water || top.isLiquid() || !top.getType().isSolid()) return null;
        if (!w.getBlockAt(bx, hy + 1, bz).isPassable() || !w.getBlockAt(bx, hy + 2, bz).isPassable()) return null;
        return new Location(w, bx + 0.5, hy + 1, bz + 0.5);
    }

    // ------------------------------------------------------------ goals

    private final class Wander implements Goal<Mob> {
        private final Mob m;
        private final Animals.Animal a;
        private long nextAt, endAt;
        private Location target;

        Wander(Mob m, Animals.Animal a) {
            this.m = m;
            this.a = a;
        }

        private double radius() {
            return switch (a.species) {
                case DEER -> 14;
                case ELEPHANT -> 16;
                case ZEBRA -> 12;
                case DUCK -> 12;
                case CAPYBARA, LIZARD -> 8;
                case HEDGEHOG, TORTOISE, CRAB -> 6;
                case SNAIL -> 3;
                default -> 10;
            };
        }

        private long idle() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return switch (a.species) {
                case SNAIL, TORTOISE -> 200 + r.nextInt(400);
                case ELEPHANT -> 120 + r.nextInt(300);
                case DUCK, CRAB -> 40 + r.nextInt(120);
                default -> 80 + r.nextInt(240);
            };
        }

        @Override
        public boolean shouldActivate() {
            if (!m.isValid() || m.isInsideVehicle()) return false;
            long now = m.getWorld().getGameTime();
            if (now < nextAt) return false;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            // cranes stalk the SHORE of the water (dry ground beside it), ducks/capys use the water surface itself
            boolean wantWaterSurface = a.species.habitat == Species.Habitat.WATER && a.species != Species.CRANE;
            boolean water = wantWaterSurface && r.nextInt(100) < 75;
            Location here = m.getLocation();
            double rad = radius();
            for (int i = 0; i < 10; i++) {
                double ang = r.nextDouble() * Math.PI * 2, d = 2 + r.nextDouble() * (rad - 2);
                Location l = spot(m.getWorld(), here.getX() + Math.cos(ang) * d, here.getZ() + Math.sin(ang) * d, here.getY(), water);
                if (l != null) {
                    target = l;
                    return true;
                }
            }
            nextAt = now + 40;
            return false;
        }

        @Override
        public boolean shouldStayActive() {
            return m.isValid() && m.getWorld().getGameTime() < endAt && m.getPathfinder().hasPath();
        }

        @Override
        public void start() {
            endAt = m.getWorld().getGameTime() + 20 * 15;
            m.getPathfinder().moveTo(target, 1.0);
        }

        @Override
        public void stop() {
            m.getPathfinder().stopPathfinding();
            nextAt = m.getWorld().getGameTime() + idle();
        }

        @Override
        public GoalKey<Mob> getKey() {
            return wanderKey;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE);
        }
    }

    private final class Panic implements Goal<Mob> {
        private final Mob m;
        private final Animals.Animal a;
        private long lastPick;

        Panic(Mob m, Animals.Animal a) {
            this.m = m;
            this.a = a;
        }

        @Override
        public boolean shouldActivate() {
            return m.isValid() && a.panicUntil > plugin.tick;
        }

        @Override
        public boolean shouldStayActive() {
            return m.isValid() && a.panicUntil > plugin.tick;
        }

        @Override
        public void start() {
            lastPick = Long.MIN_VALUE;
            run();
        }

        @Override
        public void tick() {
            long now = m.getWorld().getGameTime();
            if (now - lastPick > 20 || !m.getPathfinder().hasPath()) run();
        }

        private void run() {
            lastPick = m.getWorld().getGameTime();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Location here = m.getLocation();
            double baseAng = a.threat != null && a.threat.getWorld() == here.getWorld()
                    ? Math.atan2(here.getZ() - a.threat.getZ(), here.getX() - a.threat.getX()) : r.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 8; i++) {
                double ang = baseAng + (r.nextDouble() - 0.5) * 1.2, d = 8 + r.nextDouble() * 6;
                boolean water = a.species.habitat == Species.Habitat.WATER && m.isInWater();
                Location l = spot(m.getWorld(), here.getX() + Math.cos(ang) * d, here.getZ() + Math.sin(ang) * d, here.getY(), water);
                if (l != null) {
                    m.getPathfinder().moveTo(l, 1.8);
                    return;
                }
            }
            m.getPathfinder().moveTo(here.clone().add(Math.cos(baseAng) * 8, 0, Math.sin(baseAng) * 8), 1.8);
        }

        @Override
        public void stop() {
            m.getPathfinder().stopPathfinding();
        }

        @Override
        public GoalKey<Mob> getKey() {
            return panicKey;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE);
        }
    }
}
