package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Small jokes with a hidden advancement each. */
final class EasterEggs {
    private final SteamworksPlugin plugin;
    private final Map<UUID, long[]> crank = new HashMap<>();          // start, last
    private final Map<UUID, ArrayDeque<Long>> whistles = new HashMap<>();
    private final Map<String, long[]> dryHammer = new HashMap<>();    // hammer-pos -> [start, last]
    private final Map<UUID, Long> sulking = new HashMap<>();          // player -> start

    EasterEggs(SteamworksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::alchemist, 200L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sulkWatch, 120L, 100L);
    }

    /** Cranking for 30 seconds without a break wears your arm out. */
    void crank(Player p) {
        long now = System.currentTimeMillis();
        long[] c = crank.computeIfAbsent(p.getUniqueId(), k -> new long[]{now, now});
        if (now - c[1] > 1500) c[0] = now;
        c[1] = now;
        if (now - c[0] >= 30_000) {
            c[0] = now;
            p.sendActionBar(Component.text("Your arm is tired.", NamedTextColor.GRAY));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 0));
            plugin.adv().grant(p, "tired_arm");
        }
    }

    /** Three whistles within ten seconds: a little tune. */
    void whistle(Player driver, Location at) {
        if (driver == null) return;
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = whistles.computeIfAbsent(driver.getUniqueId(), k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > 10_000) q.pollFirst();
        if (q.size() < 3) return;
        q.clear();
        float[] pitch = {0.75f, 0.9f, 1.12f, 1.0f, 1.12f, 1.5f};
        for (int i = 0; i < pitch.length; i++) {
            float pt = pitch[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> at.getWorld().playSound(at, "minecraft:block.note_block.flute", 2f, pt), 8L + i * 4L);
        }
        driver.sendActionBar(Component.text("Mind the gap!", NamedTextColor.GOLD));
        plugin.adv().grant(driver, "mind_the_gap");
    }

    /** Looking at a mixer that spins above an empty kettle. */
    void kettleLook(KNode kettle, Player p) {
        if (kettle.kettleWater > 0) return;
        KNode above = plugin.kinetics().at(kettle.world.getBlockAt(kettle.x, kettle.y + 1, kettle.z));
        if (above == null || above.type != PartType.MIXER || Math.abs(above.speed()) < 1) return;
        p.sendActionBar(Component.text("Dry run, brass monkey.", NamedTextColor.GOLD));
        plugin.adv().grant(p, "dry_run");
    }

    /** A steam hammer spinning over nothing for 30 seconds is just exercising. */
    void hammerDry(KNode n) {
        long now = System.currentTimeMillis();
        String k = n.world.getName() + ":" + n.x + ":" + n.y + ":" + n.z;
        long[] d = dryHammer.computeIfAbsent(k, x -> new long[]{now, now});
        if (now - d[1] > 2000) d[0] = now;
        d[1] = now;
        if (now - d[0] < 30_000) return;
        dryHammer.remove(k);
        Location l = n.center();
        for (Player p : l.getWorld().getNearbyPlayers(l, 8)) {
            p.sendActionBar(Component.text("It's just exercising.", NamedTextColor.GRAY));
            plugin.adv().grant(p, "anxiety");
        }
    }

    void hammerWorking(KNode n) { dryHammer.remove(n.world.getName() + ":" + n.x + ":" + n.y + ":" + n.z); }

    /** Standing next to a jammed network for 30 seconds: even machines need to sulk. */
    private void sulkWatch() {
        long now = System.currentTimeMillis();
        Set<UUID> near = new HashSet<>();
        for (Network net : plugin.kinetics().networks) {
            if (!net.jammed || net.nodes.isEmpty()) continue;
            int seen = 0;
            for (KNode n : net.nodes) {
                if (seen++ > 40) break;                       // cheap radius sweep
                Location c = n.center();
                if (c.getWorld() == null) continue;
                for (Player p : c.getWorld().getNearbyPlayers(c, 6)) near.add(p.getUniqueId());
            }
        }
        sulking.keySet().removeIf(id -> !near.contains(id));
        for (UUID id : near) {
            Long start = sulking.putIfAbsent(id, now);
            if (start == null || now - start < 30_000) continue;
            sulking.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.sendActionBar(Component.text("Even machines need to sulk.", NamedTextColor.GRAY));
            p.getWorld().playSound(p.getLocation(), "minecraft:block.note_block.bass", 1f, 0.7f);
            plugin.adv().grant(p, "sulk");
        }
    }

    /** Three water wheels on one network: rush hour. Checked shortly after placement. */
    void checkCommute(KNode placed) {
        if (placed.type != PartType.WATER_WHEEL) return;
        Network net = placed.network;
        if (net == null || net.nodes.size() < 3) return;
        int wheels = 0;
        for (KNode n : net.nodes) if (n.type == PartType.WATER_WHEEL) wheels++;
        if (wheels < 3) return;
        Player p = placed.owner == null ? null : Bukkit.getPlayer(placed.owner);
        if (p == null || plugin.adv().has(p, "commute")) return;
        p.sendActionBar(Component.text("You're not paying for all these lanes?", NamedTextColor.GOLD));
        plugin.adv().grant(p, "commute");
    }

    /** Raw zinc, raw tin and sulfur in one inventory make you sneeze. */
    private void alchemist() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean zinc = false, tin = false, sulfur = false;
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null || it.isEmpty()) continue;
                MaterialType m = plugin.items().materialOf(it);
                if (m == MaterialType.RAW_ZINC) zinc = true;
                else if (m == MaterialType.RAW_TIN) tin = true;
                else if (m == MaterialType.SULFUR) sulfur = true;
            }
            if (!(zinc && tin && sulfur) || plugin.adv().has(p, "sneeze")) continue;
            Location l = p.getEyeLocation();
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 12, 0.2, 0.2, 0.2, 0.02);
            p.getWorld().playSound(l, "minecraft:entity.horse.breathe", 1f, 1.6f);
            p.sendActionBar(Component.text("Achoo!", NamedTextColor.GRAY));
            plugin.adv().grant(p, "sneeze");
        }
    }
}
