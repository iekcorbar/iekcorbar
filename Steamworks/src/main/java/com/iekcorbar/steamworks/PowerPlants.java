package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Fluid;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The big power sources.
 *  - Windmill: huge sails (a scaled display), speed from height and weather,
 *    stops when blocks stand in the circle the sails sweep.
 *  - Steam Boiler: burns fuel items (by hand or a hopper pointing into its
 *    side) or fuel from tanks through pipes, needs a water block beside it.
 *  - Steam Engine: on top of a burning boiler with water, the strongest source.
 */
final class PowerPlants {
    private static final int MAX_BURN = 20 * 60 * 160;
    private final SteamworksPlugin plugin;

    PowerPlants(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Burn ticks an item gives a boiler (0 = not a fuel). */
    int fuelFor(ItemStack i) {
        if (i == null || i.isEmpty()) return 0;
        if (plugin.items().materialOf(i) == MaterialType.FUEL_CANISTER) return plugin.settings().fuelCanisterBoilerTicks;
        if (plugin.items().isOurs(i)) return 0;
        return switch (i.getType()) {
            case COAL, CHARCOAL -> 1600;
            case COAL_BLOCK -> 16000;
            case BLAZE_ROD -> 2400;
            case LAVA_BUCKET -> 20000;
            case DRIED_KELP_BLOCK -> 4000;
            default -> 0;
        };
    }

    void tick(long tick) {
        Settings cfg = plugin.settings();
        for (KNode n : plugin.kinetics().plants) {
            switch (n.type) {
                case WINDMILL -> {
                    if (tick % 40 == 0) wind(n, cfg);
                }
                case STEAM_BOILER -> boiler(n, tick, cfg);
                case STEAM_ENGINE -> {
                    if (tick % 20 == 0) engine(n, cfg);
                    if (n.generated != 0 && n.speed() != 0 && tick % 12 == 0) {
                        Location top = n.center().add(0, 0.7, 0);
                        n.world.spawnParticle(Particle.CLOUD, top, 2, 0.1, 0.05, 0.1, 0.03);
                        n.world.playSound(top, "steamworks:engine.chuff", 0.5f, (float) Math.min(1.4, 0.8 + Math.abs(n.speed()) / 64));
                    }
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------ windmill

    private void wind(KNode n, Settings cfg) {
        n.blocked = sailsBlocked(n, cfg.windRadius);
        double rpm = 0;
        if (!n.blocked) {
            rpm = n.y < 64 ? cfg.windRpmLow : n.y <= 100 ? cfg.windRpmMid : cfg.windRpmHigh;
            if (n.world.isThundering()) rpm += cfg.windStormBonus;
            else if (n.world.hasStorm()) rpm += cfg.windRainBonus;
        }
        plugin.kinetics().setGenerated(n, rpm * Kinetics.sign(n.facing));
    }

    /** True if more than a few solid blocks stand in the disc the sails sweep (one block in front of the hub). */
    private static boolean sailsBlocked(KNode n, int r) {
        BlockFace f = n.facing;
        int solid = 0;
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (a * a + b * b > r * r || (a == 0 && b == 0)) continue;
                int x = n.x + f.getModX(), y = n.y + f.getModY(), z = n.z + f.getModZ();
                switch (Kinetics.axisOf(f)) {
                    case X -> { y += a; z += b; }
                    case Y -> { x += a; z += b; }
                    case Z -> { x += a; y += b; }
                }
                if (!n.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block blk = n.world.getBlockAt(x, y, z);
                if (!blk.isPassable() || org.bukkit.Tag.LEAVES.isTagged(blk.getType())) solid++;
                if (solid > 2) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ boiler + engine

    static boolean hasWater(KNode boiler) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block b = boiler.block().getRelative(f);
            var fd = b.getWorld().getFluidData(b.getX(), b.getY(), b.getZ());
            if (fd.getFluidType() == Fluid.WATER && fd.isSource()) return true;
        }
        return false;
    }

    private KNode engineOn(KNode boiler) {
        KNode e = plugin.kinetics().rel(boiler, 0, 1, 0);
        return e != null && e.type == PartType.STEAM_ENGINE ? e : null;
    }

    private void boiler(KNode n, long tick, Settings cfg) {
        if (tick % 20 != 0) return;
        boolean lit = n.burnTicks > 0;
        KNode engine = engineOn(n);
        if (engine != null && hasWater(n)) {
            if (n.burnTicks < 200) refuel(n, cfg);
            if (n.burnTicks > 0) n.burnTicks = Math.max(0, n.burnTicks - 20);
        }
        if (tick % 200 == 0) pullFromHoppers(n);
        if (lit != (n.burnTicks > 0)) {
            plugin.visuals().updateBoiler(n);
            plugin.saveSoon(n);
        }
        if (n.burnTicks > 0 && engine != null && tick % 40 == 0)
            n.world.spawnParticle(Particle.SMOKE, n.center().add(0, -0.2, 0), 2, 0.3, 0.1, 0.3, 0.01);
    }

    /** Fuel from tanks through pipes when the fire is low. */
    private void refuel(KNode n, Settings cfg) {
        int got = plugin.oil().pull(n, FluidType.FUEL, 100);
        if (got > 0) n.burnTicks += got * cfg.fuelCanisterBoilerTicks / Oil.CANISTER;
    }

    private void pullFromHoppers(KNode n) {
        if (n.burnTicks > MAX_BURN / 2) return;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block b = n.block().getRelative(f);
            if (b.getType() != Material.HOPPER || !(b.getBlockData() instanceof Directional d) || d.getFacing() != f.getOppositeFace()) continue;
            if (!(b.getState(false) instanceof Hopper h)) continue;
            Inventory inv = h.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                int burn = fuelFor(s);
                if (burn <= 0 || s.getType() == Material.LAVA_BUCKET || plugin.items().isOurs(s)) continue;
                n.burnTicks += burn;
                s.setAmount(s.getAmount() - 1);
                inv.setItem(i, s.getAmount() <= 0 ? null : s);
                return;
            }
        }
    }

    private void engine(KNode n, Settings cfg) {
        KNode boiler = plugin.kinetics().rel(n, 0, -1, 0);
        boolean on = boiler != null && boiler.type == PartType.STEAM_BOILER && boiler.burnTicks > 0 && hasWater(boiler);
        plugin.kinetics().setGenerated(n, on ? cfg.engineRpm : 0);
    }

    /** Right-click a boiler with fuel (sneak = the whole stack). */
    void feed(KNode n, Player p, ItemStack hand) {
        int burn = fuelFor(hand);
        if (burn <= 0) return;
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        boolean canister = plugin.items().materialOf(hand) == MaterialType.FUEL_CANISTER;
        boolean bucket = hand.getType() == Material.LAVA_BUCKET;
        int count = p.isSneaking() && !canister && !bucket ? hand.getAmount() : 1;
        count = Math.min(count, Math.max(0, (MAX_BURN - n.burnTicks) / burn));
        if (count <= 0) {
            p.sendActionBar(Component.text("The firebox is full.", NamedTextColor.YELLOW));
            return;
        }
        boolean wasLit = n.burnTicks > 0;
        n.burnTicks += burn * count;
        if (!creative) {
            if (canister) p.getInventory().setItemInMainHand(plugin.items().createMaterial(MaterialType.CANISTER, 1));
            else if (bucket) p.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            else hand.setAmount(hand.getAmount() - count);
        }
        if (!wasLit) plugin.visuals().updateBoiler(n);
        plugin.saveSoon(n);
        p.playSound(n.center(), Sound.ITEM_FIRECHARGE_USE, 0.5f, 0.8f);
        p.sendActionBar(info(n));
    }

    Component info(KNode n) {
        Component name = Component.text(n.type.displayName + ": ", NamedTextColor.GOLD);
        return switch (n.type) {
            case WINDMILL -> n.blocked
                    ? name.append(Component.text("sails blocked - clear " + plugin.settings().windRadius + " blocks round the hub", NamedTextColor.RED))
                    : name.append(Component.text(String.format("wind %.0f RPM%s", Math.abs(n.generated),
                            n.world.isThundering() ? " (storm!)" : n.world.hasStorm() ? " (rain)" : ""), NamedTextColor.WHITE));
            case STEAM_BOILER -> {
                int s = n.burnTicks / 20;
                Component c = Component.text(n.burnTicks > 0 ? String.format("burning, %d:%02d left", s / 60, s % 60) : "cold - add coal, charcoal or fuel",
                        n.burnTicks > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
                c = c.append(Component.text(hasWater(n) ? "  |  water ok" : "  |  NO WATER - put a water block beside it",
                        hasWater(n) ? NamedTextColor.GRAY : NamedTextColor.RED));
                c = c.append(Component.text(engineOn(n) != null ? "  |  engine on top" : "  |  put a Steam Engine on top", NamedTextColor.GRAY));
                yield name.append(c);
            }
            case STEAM_ENGINE -> {
                KNode b = plugin.kinetics().rel(n, 0, -1, 0);
                if (b == null || b.type != PartType.STEAM_BOILER) yield name.append(Component.text("needs a Steam Boiler under it", NamedTextColor.YELLOW));
                if (!hasWater(b)) yield name.append(Component.text("the boiler has no water beside it", NamedTextColor.RED));
                if (b.burnTicks <= 0) yield name.append(Component.text("the boiler is cold - feed it", NamedTextColor.YELLOW));
                yield name.append(Component.text(String.format("full steam, %.0f RPM", plugin.settings().engineRpm), NamedTextColor.GREEN));
            }
            default -> name;
        };
    }
}
