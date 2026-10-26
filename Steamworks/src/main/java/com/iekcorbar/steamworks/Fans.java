package com.iekcorbar.steamworks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bellows fan: blows air out of its front (range grows with speed, stops at a
 * solid block). Entities in the stream are pushed. The first lava / fire /
 * water / soul fire in the stream turns it into a processing stream: items
 * past it get smelted (furnace recipes), smoked (smoker recipes), washed or
 * haunted (machines.yml) after a while, whole stack at once.
 */
final class Fans {
    enum Process {
        SMELTING("smelting", Particle.FLAME), SMOKING("smoking", Particle.SMOKE),
        WASHING("washing", Particle.SPLASH), HAUNTING("haunting", Particle.SOUL_FIRE_FLAME);

        final String label;
        final Particle particle;

        Process(String label, Particle particle) {
            this.label = label;
            this.particle = particle;
        }
    }

    private final SteamworksPlugin plugin;
    final MachineRecipes washing, haunting;
    private final NamespacedKey progressKey, kindKey;
    private Map<Material, Optional<ItemStack>> smeltCache = new HashMap<>(), smokeCache = new HashMap<>();
    private List<CookingRecipe<?>> furnace, smoker;

    Fans(SteamworksPlugin plugin) {
        this.plugin = plugin;
        washing = new MachineRecipes(plugin, "fan_washing");
        haunting = new MachineRecipes(plugin, "fan_haunting");
        progressKey = new NamespacedKey(plugin, "fan_progress");
        kindKey = new NamespacedKey(plugin, "fan_kind");
    }

    void load(File file) {
        washing.load(file);
        haunting.load(file);
        furnace = null;
        smoker = null;
        smeltCache = new HashMap<>();
        smokeCache = new HashMap<>();
    }

    static int range(Settings cfg, double speed) {
        return (int) Math.max(cfg.fanMinRange, Math.min(cfg.fanMaxRange, Math.round(speed / cfg.fanRpmPerBlock)));
    }

    /** What a block in the stream does: null = plain air, lets air through. */
    static Process catalyst(Block b) {
        Material m = b.getType();
        if (m == Material.LAVA) return Process.SMELTING;
        if (m == Material.WATER || m == Material.BUBBLE_COLUMN) return Process.WASHING;
        if (b.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged()) return Process.WASHING;
        if (m == Material.FIRE) return Process.SMOKING;
        if (m == Material.SOUL_FIRE) return Process.HAUNTING;
        if (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) {
            if (b.getBlockData() instanceof Lightable l && !l.isLit()) return null;
            return m == Material.CAMPFIRE ? Process.SMOKING : Process.HAUNTING;
        }
        return null;
    }

    private static boolean blocksAir(Block b) {
        Material m = b.getType();
        if (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) return false;
        return !b.isPassable();
    }

    /** Called every 5 ticks. */
    void tick(long tick) {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().fans) {
            double speed = Math.abs(n.speed());
            if (speed <= 0) continue;
            BlockFace dir = n.facing;
            int max = range(cfg, speed);
            int length = 0, catalystAt = -1;
            Process process = null;
            Block b = n.block();
            for (int i = 1; i <= max; i++) {
                b = b.getRelative(dir);
                if (blocksAir(b) || plugin.kinetics().at(b) != null) break;
                length = i;
                if (process == null) {
                    Process p = catalyst(b);
                    if (p != null) {
                        process = p;
                        catalystAt = i;
                    }
                }
            }
            if (length == 0) continue;

            Block first = n.block().getRelative(dir);
            Block last = n.block().getRelative(dir, length);
            BoundingBox box = BoundingBox.of(first, last);
            Vector push = Kinetics.vec(dir).multiply(cfg.fanPush * Math.min(2, speed / 16));
            for (Entity e : n.world.getNearbyEntities(box)) {
                if (e instanceof Player p && (!cfg.fanPushPlayers || p.getGameMode() == GameMode.SPECTATOR || p.isFlying())) continue;
                if (!(e instanceof Item) && !(e instanceof LivingEntity)) continue;
                Vector v = e.getVelocity();
                if (v.dot(Kinetics.vec(dir)) < 0.5) e.setVelocity(v.add(push));
                if (process != null && e instanceof Item item) {
                    int at = distance(n, item.getLocation(), dir);
                    if (at > catalystAt || (at == catalystAt && process == Process.WASHING)) work(item, process, cfg);
                }
            }
            streamParticles(n, dir, length, process, catalystAt, speed);
        }
    }

    /** Blocks between the fan and a point along the blow direction. */
    private static int distance(KNode n, Location l, BlockFace d) {
        return (l.getBlockX() - n.x) * d.getModX() + (l.getBlockY() - n.y) * d.getModY() + (l.getBlockZ() - n.z) * d.getModZ();
    }

    private void work(Item item, Process process, Settings cfg) {
        ItemStack stack = item.getItemStack();
        if (plugin.items().typeOf(stack) != null) return;
        if (!canProcess(stack, process)) return;
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        String kind = pdc.get(kindKey, PersistentDataType.STRING);
        int progress = process.name().equals(kind) ? pdc.getOrDefault(progressKey, PersistentDataType.INTEGER, 0) : 0;
        progress += 5;
        Location l = item.getLocation();
        if (progress % 20 == 0) item.getWorld().spawnParticle(process.particle, l.clone().add(0, 0.25, 0), 3, 0.1, 0.1, 0.1, 0.01);
        if (progress < cfg.fanProcessTicks) {
            pdc.set(kindKey, PersistentDataType.STRING, process.name());
            pdc.set(progressKey, PersistentDataType.INTEGER, progress);
            return;
        }
        List<ItemStack> out = results(stack, process);
        if (out == null) return;
        item.remove();
        for (ItemStack o : out) {
            Item drop = l.getWorld().dropItem(l, o);
            drop.setVelocity(item.getVelocity());
        }
        l.getWorld().spawnParticle(process.particle, l.clone().add(0, 0.25, 0), 10, 0.15, 0.15, 0.15, 0.02);
        l.getWorld().playSound(l, process == Process.WASHING ? Sound.ENTITY_GENERIC_SPLASH : Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.3f);
    }

    boolean canProcess(ItemStack stack, Process process) {
        return switch (process) {
            case SMELTING -> cooked(stack, true) != null;
            case SMOKING -> cooked(stack, false) != null;
            case WASHING -> custom(washing, stack) != null;
            case HAUNTING -> custom(haunting, stack) != null;
        };
    }

    /** Recipe for a washing/haunting item, skipping recipes that would give the same item back (e.g. white wool). */
    private static MachineRecipes.Recipe custom(MachineRecipes book, ItemStack stack) {
        MachineRecipes.Recipe r = book.find(stack);
        if (r == null) return null;
        if (r.outputs().size() == 1 && r.outputs().get(0).sw() == null && r.outputs().get(0).material() == stack.getType()) return null;
        return r;
    }

    private List<ItemStack> results(ItemStack stack, Process process) {
        int amount = stack.getAmount();
        List<ItemStack> out = new ArrayList<>();
        if (process == Process.SMELTING || process == Process.SMOKING) {
            ItemStack r = cooked(stack, process == Process.SMELTING);
            if (r == null) return null;
            int total = r.getAmount() * amount;
            while (total > 0) {
                int n = Math.min(total, r.getMaxStackSize());
                out.add(r.asQuantity(n));
                total -= n;
            }
            return out;
        }
        MachineRecipes.Recipe r = custom(process == Process.WASHING ? washing : haunting, stack);
        if (r == null) return null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (MachineRecipes.Output o : r.outputs()) {
            int count = 0;
            for (int i = 0; i < amount; i++) if (o.chance() >= 1 || rnd.nextDouble() < o.chance()) count += o.amount();
            while (count > 0) {
                ItemStack s = o.create(plugin.items());
                int n = Math.min(count, s.getMaxStackSize());
                out.add(s.asQuantity(n));
                count -= n;
            }
        }
        return out;
    }

    /** Vanilla furnace (smelting) or smoker result, cached per material. */
    private ItemStack cooked(ItemStack stack, boolean smelt) {
        if (plugin.items().typeOf(stack) != null || plugin.items().carOf(stack) != null) return null;
        if (furnace == null) {
            furnace = new ArrayList<>();
            smoker = new ArrayList<>();
            for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                Recipe r = it.next();
                if (r instanceof FurnaceRecipe f) furnace.add(f);
                else if (r instanceof SmokingRecipe s) smoker.add(s);
            }
        }
        List<CookingRecipe<?>> list = smelt ? furnace : smoker;
        if (plugin.items().materialOf(stack) != null) { // our raw ores: recipes match on the exact item, no per-material cache
            for (CookingRecipe<?> r : list) if (r.getInputChoice().test(stack.asOne())) return r.getResult().clone();
            return null;
        }
        Map<Material, Optional<ItemStack>> cache = smelt ? smeltCache : smokeCache;
        return cache.computeIfAbsent(stack.getType(), m -> {
            ItemStack probe = new ItemStack(m);
            for (CookingRecipe<?> r : list) if (r.getInputChoice().test(probe)) return Optional.of(r.getResult());
            return Optional.empty();
        }).map(ItemStack::clone).orElse(null);
    }

    /** Wind you can see: white puffs racing along the stream (faster fan = faster puffs), process particles past the catalyst. */
    private static void streamParticles(KNode n, BlockFace dir, int length, Process process, int catalystAt, double rpm) {
        Vector d = Kinetics.vec(dir);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location base = n.center();
        if (base.getWorld().getNearbyPlayers(base, 32).isEmpty()) return;
        double v = Math.min(0.6, 0.12 + rpm / 120);
        int count = Math.max(3, length);
        for (int i = 0; i < count; i++) {
            double t = 0.55 + rnd.nextDouble() * Math.max(0.5, length - 0.5);
            Location l = base.clone().add(d.clone().multiply(t))
                    .add(rnd.nextDouble() * 0.7 - 0.35, rnd.nextDouble() * 0.7 - 0.35, rnd.nextDouble() * 0.7 - 0.35);
            boolean past = process != null && t > catalystAt;
            Particle p = past ? process.particle : rnd.nextInt(3) == 0 ? Particle.CLOUD : Particle.WHITE_ASH;
            base.getWorld().spawnParticle(p, l, 0, d.getX(), d.getY(), d.getZ(), past ? v * 0.5 : v);
        }
        // a puff right at the grille, so it is clear which way it blows
        Location mouth = base.clone().add(d.clone().multiply(0.6));
        base.getWorld().spawnParticle(Particle.CLOUD, mouth, 0, d.getX(), d.getY(), d.getZ(), v * 0.8);
    }
}
