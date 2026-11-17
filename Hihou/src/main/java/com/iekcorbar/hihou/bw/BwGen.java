package com.iekcorbar.hihou.bw;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * One resource generator, ported from BedWars1058's OreGenerator with its stock
 * generators.yml "Default." values baked in (the original reads them from a config file;
 * making these tunable per-arena can ride along when the shop etappe needs them anyway).
 * Iron and gold belong to a team and only run when that team has players; diamond and
 * emerald are neutral, shared, and tick from game start. A spawn tick counts nearby
 * dropped ore first - the original's spawn-limit trick so logged-off hoards cannot
 * lag the arena.
 */
final class BwGen {
    enum Type {IRON, GOLD, DIAMOND, EMERALD}

    final Type type;
    final Location loc;
    final String teamName; // null for the shared diamond/emerald fields
    int delay;
    int amount;
    int spawnLimit;
    int cd; // seconds until the next spawn, started at launch()
    int tier = 1;

    BwGen(Type type, Location loc, String teamName) {
        this.type = type;
        this.teamName = teamName;
        // the original lifts diamond/emerald to block-centre +1.3y, iron/gold just +1.3y
        double cx = type == Type.DIAMOND || type == Type.EMERALD ? loc.getBlockX() + 0.5 : loc.getX();
        double cz = type == Type.DIAMOND || type == Type.EMERALD ? loc.getBlockZ() + 0.5 : loc.getZ();
        this.loc = new Location(loc.getWorld(), cx, loc.getBlockY() + 1.3, cz);
        switch (type) {
            case IRON -> { delay = 2; amount = 2; spawnLimit = 32; }
            case GOLD -> { delay = 6; amount = 2; spawnLimit = 7; }
            case DIAMOND -> { delay = 30; amount = 1; spawnLimit = 4; }
            case EMERALD -> { delay = 70; amount = 1; spawnLimit = 4; }
        }
    }

    void startTick() {
        cd = delay;
    }

    /** Upgrade to tier 2/3 at the timeline moment (original's OreGenerator.upgrade()). */
    void upgradeTo(int newTier) {
        tier = newTier;
        if (type == Type.DIAMOND) delay = newTier == 2 ? 20 : 15;
        if (type == Type.EMERALD) delay = newTier == 2 ? 50 : 30;
    }

    Material ore() {
        return switch (type) {
            case IRON -> Material.IRON_INGOT;
            case GOLD -> Material.GOLD_INGOT;
            case DIAMOND -> Material.DIAMOND;
            case EMERALD -> Material.EMERALD;
        };
    }

    /** Called once per second while the arena is PLAYING. With the Auto-Earn upgrade the ore goes
     *  straight into a team member's pockets instead of onto the pad (0.6.0). */
    void tick(BwArena a) {
        if (cd > 1) {
            cd--;
            return;
        }
        cd = delay;
        int auto = a.upgTeam(teamName, 5);   // -1 when there is no auto-earn for this gen
        if (spawnLimit > 0) {
            int onGround = 0;
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
                if (e instanceof Item it && it.getItemStack().getType() == ore()) {
                    if (++onGround >= spawnLimit) return;
                }
            }
        }
        Player pocket = null;
        if (auto > 0) {
            List<Player> on = a.onlineTeam(teamName);
            if (!on.isEmpty()) pocket = on.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(on.size()));
        }
        for (int i = 0; i < amount; i++) {
            if (pocket != null) {
                var left = pocket.getInventory().addItem(new ItemStack(ore()));
                for (ItemStack rem : left.values()) pocket.getWorld().dropItem(loc, rem);
            } else {
                Item it = loc.getWorld().dropItem(loc, new ItemStack(ore()));
                it.setVelocity(new Vector(0, 0.05, 0)); // a tiny lift so items don't sink into the pad
            }
        }
        // our steamworks-energie tint: a small spark at the pad when shared fields pay out
        if (type == Type.DIAMOND || type == Type.EMERALD)
            loc.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, loc, 6, 0.2, 0.3, 0.2, 0);
    }

    /** Team gens only run while their team has online members (original disables empty teams). */
    boolean shouldRun(BwArena a) {
        if (teamName == null) return true;
        BwTeam t = a.team(teamName);
        return t != null && !t.members().isEmpty();
    }
}
