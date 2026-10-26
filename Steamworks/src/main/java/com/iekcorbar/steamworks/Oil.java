package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Oil: hidden oil fields under some chunks (fixed per world seed, used up by
 * pumping, what is left is kept in the chunk's data), pumpjacks that pump it
 * into pipes, tanks that store fluids as numbers, refineries that turn crude
 * oil into fuel + chemical waste, canisters to carry 1000 mB, a dowsing rod
 * to find fields, and pollution: waste with nowhere to go is dumped into the
 * chunk, which then kills grass and leaves and poisons players until it fades.
 */
final class Oil implements Listener {
    static final int CANISTER = 1000;
    private static final BlockFace[] FACES = Kinetics.FACES;

    private final SteamworksPlugin plugin;
    private final NamespacedKey oilKey, pollutionKey;
    /** Loaded chunks with pollution in them: world uid + chunk key -> level. */
    private final Map<String, Integer> pollution = new HashMap<>();
    private final Map<java.util.UUID, Long> rodCooldown = new HashMap<>();

    Oil(SteamworksPlugin plugin) {
        this.plugin = plugin;
        oilKey = new NamespacedKey(plugin, "oil_left");
        pollutionKey = new NamespacedKey(plugin, "pollution");
    }

    // ------------------------------------------------------------ oil fields

    /** Oil a chunk started with (mB), from the world seed: most chunks have none. */
    int initialOil(World w, int cx, int cz) {
        Settings cfg = plugin.settings();
        Random r = new Random(w.getSeed() ^ (cx * 341873128712L) ^ (cz * 132897987541L) ^ 0x5EA0115L);
        double chance = cfg.oilChance;
        if (w.isChunkLoaded(cx, cz)) {
            String biome = w.getBiome(cx * 16 + 8, 63, cz * 16 + 8).getKey().getKey();
            if (biome.contains("desert") || biome.contains("badlands") || biome.contains("ocean")) chance *= 2.5;
        }
        if (w.getEnvironment() != World.Environment.NORMAL || r.nextDouble() >= chance) return 0;
        return (int) (cfg.oilMin + r.nextDouble() * (cfg.oilMax - cfg.oilMin));
    }

    int oilLeft(World w, int cx, int cz) {
        if (w.isChunkLoaded(cx, cz)) {
            Integer v = w.getChunkAt(cx, cz).getPersistentDataContainer().get(oilKey, PersistentDataType.INTEGER);
            if (v != null) return v;
        }
        return initialOil(w, cx, cz);
    }

    private void setOilLeft(Chunk c, int v) {
        c.getPersistentDataContainer().set(oilKey, PersistentDataType.INTEGER, Math.max(0, v));
    }

    // ------------------------------------------------------------ fluid network

    static boolean fluidPart(KNode n) {
        return n != null && (n.type == PartType.PIPE || n.type == PartType.TANK || n.type == PartType.PUMPJACK || n.type == PartType.REFINERY
                || n.type == PartType.STEAM_BOILER);
    }

    /** Tanks reachable from a machine: directly beside it or through pipes. */
    private List<KNode> tanks(KNode start) {
        List<KNode> out = new ArrayList<>();
        Set<KNode> seen = new HashSet<>();
        ArrayDeque<KNode> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty() && seen.size() < 1024) {
            KNode n = q.poll();
            for (BlockFace f : FACES) {
                KNode m = plugin.kinetics().rel(n, f.getModX(), f.getModY(), f.getModZ());
                if (m == null || !seen.add(m)) continue;
                if (m.type == PartType.TANK) out.add(m);
                else if (m.type == PartType.PIPE) q.add(m);
            }
        }
        return out;
    }

    /** Room for a fluid in the tanks a machine reaches. */
    private int room(KNode start, FluidType f) {
        int cap = plugin.settings().tankCapacity, room = 0;
        for (KNode t : tanks(start)) if (t.fluid == null || t.fluid == f) room += cap - t.fluidAmount;
        return room;
    }

    /** Pushes fluid into reachable tanks (same fluid first, then empty ones). Returns how much went in. */
    private int push(KNode start, FluidType f, int amount) {
        int cap = plugin.settings().tankCapacity, left = amount;
        List<KNode> ts = tanks(start);
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (KNode t : ts) {
                if (left <= 0) break;
                if (pass == 0 ? t.fluid != f : t.fluid != null) continue;
                int move = Math.min(left, cap - t.fluidAmount);
                if (move <= 0) continue;
                t.fluid = f;
                t.fluidAmount += move;
                left -= move;
                tankChanged(t);
            }
        }
        return amount - left;
    }

    int pull(KNode start, FluidType f, int amount) {
        int left = amount;
        for (KNode t : tanks(start)) {
            if (left <= 0) break;
            if (t.fluid != f) continue;
            int move = Math.min(left, t.fluidAmount);
            t.fluidAmount -= move;
            if (t.fluidAmount <= 0) {
                t.fluidAmount = 0;
                t.fluid = null;
            }
            left -= move;
            tankChanged(t);
        }
        return amount - left;
    }

    private void tankChanged(KNode t) {
        plugin.visuals().updateTank(t);
        plugin.saveSoon(t);
    }

    /** A fluid part was placed or removed: pipes around it grow/lose their arms. */
    void refreshAround(KNode n) {
        if (n.alive && n.type == PartType.PIPE) plugin.visuals().updatePipe(n);
        for (BlockFace f : FACES) {
            KNode m = plugin.kinetics().rel(n, f.getModX(), f.getModY(), f.getModZ());
            if (m != null && m.type == PartType.PIPE) plugin.visuals().updatePipe(m);
        }
    }

    // ------------------------------------------------------------ ticking

    static boolean heated(KNode n) {
        Block b = n.block().getRelative(BlockFace.DOWN);
        Material m = b.getType();
        if (m == Material.FIRE || m == Material.SOUL_FIRE || m == Material.LAVA || m == Material.MAGMA_BLOCK) return true;
        if (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE) return b.getBlockData() instanceof Lightable l && l.isLit();
        return false;
    }

    void tick(long tick) {
        Settings cfg = plugin.settings();
        if (tick % 4 == 0) {
            for (KNode n : plugin.kinetics().pumpjacks) {
                double s = Math.abs(n.speed());
                if (s <= 0) continue;
                n.beamPhase += s * 0.012;
                plugin.visuals().beam(n, (float) (13 * Math.sin(n.beamPhase)), 4);
            }
        }
        if (tick % 20 == 0) {
            for (KNode n : plugin.kinetics().pumpjacks) pump(n, cfg);
            for (KNode n : plugin.kinetics().refineries) refine(n, cfg);
        }
        if (tick % 100 == 0) pollutionTick(cfg);
    }

    private void pump(KNode n, Settings cfg) {
        double s = Math.abs(n.speed());
        if (s <= 0) return;
        Chunk c = n.world.getChunkAt(n.x >> 4, n.z >> 4);
        int left = oilLeft(n.world, c.getX(), c.getZ());
        if (left <= 0) return;
        n.oilWork += cfg.pumpjackRate * s;
        int want = Math.min(left, (int) n.oilWork);
        if (want <= 0) return;
        int got = push(n, FluidType.CRUDE_OIL, want);
        n.oilWork -= want; // nothing builds up while the tanks are full
        if (got > 0) {
            setOilLeft(c, left - got);
            n.world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, n.center().add(0, -0.3, 0), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private void refine(KNode n, Settings cfg) {
        if (!heated(n)) return;
        int batch = cfg.refineryBatch;
        int fuel = (int) Math.round(batch * cfg.refineryFuelPart), waste = batch - fuel;
        if (room(n, FluidType.FUEL) < fuel) return; // fuel has to go somewhere, or it stops
        int crude = pull(n, FluidType.CRUDE_OIL, batch);
        if (crude <= 0) return;
        fuel = (int) Math.round(crude * cfg.refineryFuelPart);
        waste = crude - fuel;
        push(n, FluidType.FUEL, fuel);
        int stored = push(n, FluidType.CHEMICAL_WASTE, waste);
        Location top = n.center().add(0, 0.6, 0);
        n.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, top, 1, 0.1, 0.05, 0.1, 0.01);
        if (stored < waste) {
            pollute(n.world.getChunkAt(n.x >> 4, n.z >> 4), waste - stored);
            n.world.spawnParticle(Particle.DUST, top, 12, 0.4, 0.3, 0.4, new Particle.DustOptions(Color.fromRGB(110, 140, 40), 1.4f));
            n.world.playSound(top, Sound.BLOCK_LAVA_EXTINGUISH, 0.3f, 0.6f);
        }
    }

    // ------------------------------------------------------------ pollution

    private static String pkey(Chunk c) {
        return c.getWorld().getUID() + ":" + c.getX() + ":" + c.getZ();
    }

    void pollute(Chunk c, int amount) {
        if (amount <= 0) return;
        String k = pkey(c);
        int v = pollution.getOrDefault(k, c.getPersistentDataContainer().getOrDefault(pollutionKey, PersistentDataType.INTEGER, 0)) + amount;
        v = Math.min(v, 20000);
        pollution.put(k, v);
        c.getPersistentDataContainer().set(pollutionKey, PersistentDataType.INTEGER, v);
    }

    int pollutionAt(Chunk c) {
        return pollution.getOrDefault(pkey(c), c.getPersistentDataContainer().getOrDefault(pollutionKey, PersistentDataType.INTEGER, 0));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        int v = e.getChunk().getPersistentDataContainer().getOrDefault(pollutionKey, PersistentDataType.INTEGER, 0);
        if (v > 0) pollution.put(pkey(e.getChunk()), v);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        pollution.remove(pkey(e.getChunk()));
    }

    private void pollutionTick(Settings cfg) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<String, Integer> en : new ArrayList<>(pollution.entrySet())) {
            String[] p = en.getKey().split(":");
            World w = plugin.getServer().getWorld(java.util.UUID.fromString(p[0]));
            if (w == null) continue;
            int cx = Integer.parseInt(p[1]), cz = Integer.parseInt(p[2]);
            if (!w.isChunkLoaded(cx, cz)) {
                pollution.remove(en.getKey());
                continue;
            }
            Chunk c = w.getChunkAt(cx, cz);
            int v = Math.max(0, en.getValue() - cfg.pollutionDecay);
            if (v == 0) pollution.remove(en.getKey());
            else pollution.put(en.getKey(), v);
            c.getPersistentDataContainer().set(pollutionKey, PersistentDataType.INTEGER, v);
            if (v <= 0) continue;

            boolean watched = !w.getNearbyPlayers(new Location(w, cx * 16 + 8, 64, cz * 16 + 8), 64, 320).isEmpty();
            int puffs = Math.min(10, 1 + v / 150);
            for (int i = 0; i < puffs && watched; i++) {
                Block top = w.getHighestBlockAt(cx * 16 + r.nextInt(16), cz * 16 + r.nextInt(16));
                w.spawnParticle(Particle.DUST, top.getLocation().add(0.5, 1.2 + r.nextDouble(), 0.5), 3, 0.4, 0.3, 0.4,
                        new Particle.DustOptions(Color.fromRGB(100 + r.nextInt(30), 130 + r.nextInt(30), 30), 1.6f));
            }
            if (v > 300) {
                int kills = Math.min(6, v / 300);
                for (int i = 0; i < kills; i++) wither(w.getHighestBlockAt(cx * 16 + r.nextInt(16), cz * 16 + r.nextInt(16)));
            }
            if (v > 600) {
                for (Player pl : w.getPlayers()) {
                    if (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR) continue;
                    if (pl.getLocation().getBlockX() >> 4 != cx || pl.getLocation().getBlockZ() >> 4 != cz) continue;
                    pl.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, v > 2000 ? 1 : 0, true, true));
                    if (v > 1500) pl.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, true, false));
                    pl.sendActionBar(Component.text("The air here is toxic - chemical waste pollution", NamedTextColor.DARK_GREEN));
                }
            }
        }
    }

    /** Polluted ground: grass turns to dirt, leaves and plants die. */
    private static void wither(Block top) {
        Material m = top.getType();
        if (m == Material.GRASS_BLOCK || m == Material.PODZOL || m == Material.MYCELIUM) {
            top.setType(ThreadLocalRandom.current().nextBoolean() ? Material.COARSE_DIRT : Material.DIRT, false);
        } else if (Tag.LEAVES.isTagged(m) || Tag.FLOWERS.isTagged(m) || m == Material.SHORT_GRASS || m == Material.TALL_GRASS || m == Material.FERN) {
            top.setType(Material.AIR, false);
            top.getWorld().spawnParticle(Particle.ASH, top.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0);
        }
    }

    // ------------------------------------------------------------ players

    /** Right-click on a tank with a canister. Returns true if something happened. */
    boolean interact(KNode n, Player p, ItemStack hand) {
        if (n.type != PartType.TANK || hand == null || hand.isEmpty()) return false;
        MaterialType m = plugin.items().materialOf(hand);
        if (m == null) return false;
        int cap = plugin.settings().tankCapacity;
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        if (m == MaterialType.CANISTER) {
            if (n.fluid == null || n.fluidAmount < CANISTER) return false;
            FluidType f = n.fluid;
            n.fluidAmount -= CANISTER;
            if (n.fluidAmount <= 0) {
                n.fluidAmount = 0;
                n.fluid = null;
            }
            if (!creative) hand.setAmount(hand.getAmount() - 1);
            for (ItemStack left : p.getInventory().addItem(plugin.items().createMaterial(f.canister, 1)).values())
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            p.playSound(n.center(), Sound.ITEM_BUCKET_FILL, 0.6f, 0.8f);
            tankChanged(n);
            return true;
        }
        FluidType f = FluidType.byCanister(m);
        if (f == null) return false;
        if ((n.fluid != null && n.fluid != f) || n.fluidAmount + CANISTER > cap) return false;
        n.fluid = f;
        n.fluidAmount += CANISTER;
        if (!creative) p.getInventory().setItemInMainHand(plugin.items().createMaterial(MaterialType.CANISTER, 1));
        p.playSound(n.center(), Sound.ITEM_BUCKET_EMPTY, 0.6f, 0.8f);
        tankChanged(n);
        return true;
    }

    /** A tank broken with chemical waste in it spills into the chunk. */
    void broken(KNode n) {
        if (n.type == PartType.TANK && n.fluid == FluidType.CHEMICAL_WASTE && n.fluidAmount > 0) {
            pollute(n.world.getChunkAt(n.x >> 4, n.z >> 4), n.fluidAmount);
            n.world.spawnParticle(Particle.DUST, n.center(), 30, 0.6, 0.4, 0.6, new Particle.DustOptions(Color.fromRGB(110, 140, 40), 1.6f));
        }
    }

    Component info(KNode n) {
        Component name = Component.text(n.type.displayName + ": ", NamedTextColor.GOLD);
        return switch (n.type) {
            case TANK -> n.fluid == null ? name.append(Component.text("empty", NamedTextColor.GRAY))
                    : name.append(Component.text(String.format("%s %,d / %,d mB", n.fluid.displayName, n.fluidAmount, plugin.settings().tankCapacity), NamedTextColor.WHITE));
            case PUMPJACK -> {
                int left = oilLeft(n.world, n.x >> 4, n.z >> 4);
                double s = Math.abs(n.speed());
                Component c = left <= 0 ? Component.text("no oil under this chunk", NamedTextColor.RED)
                        : Component.text(String.format("%,d mB of oil left here", left), NamedTextColor.WHITE);
                if (s > 0 && left > 0) c = c.append(Component.text(String.format("  |  pumping %.0f mB/s", s * plugin.settings().pumpjackRate), NamedTextColor.GRAY));
                else if (left > 0) c = c.append(Component.text("  |  not turning", NamedTextColor.GRAY));
                yield name.append(c);
            }
            case REFINERY -> {
                if (!heated(n)) yield name.append(Component.text("cold - put fire, a campfire or lava under it", NamedTextColor.YELLOW));
                int crude = 0;
                for (KNode t : tanks(n)) if (t.fluid == FluidType.CRUDE_OIL) crude += t.fluidAmount;
                if (crude == 0) yield name.append(Component.text("no crude oil in the connected tanks", NamedTextColor.GRAY));
                if (room(n, FluidType.FUEL) < plugin.settings().refineryBatch) yield name.append(Component.text("no room for fuel - add a tank", NamedTextColor.RED));
                Component c = Component.text(String.format("refining (%,d mB crude waiting)", crude), NamedTextColor.GREEN);
                if (room(n, FluidType.CHEMICAL_WASTE) < plugin.settings().refineryBatch)
                    c = c.append(Component.text("  |  NO ROOM FOR WASTE - it is dumped and pollutes!", NamedTextColor.DARK_GREEN));
                yield name.append(c);
            }
            default -> name.append(Component.text("connects pumpjacks, tanks and refineries", NamedTextColor.GRAY));
        };
    }

    // ------------------------------------------------------------ dowsing rod

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRod(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.items().materialOf(e.getItem()) != MaterialType.DOWSING_ROD) return;
        Player p = e.getPlayer();
        long now = plugin.kinetics().tick;
        Long last = rodCooldown.get(p.getUniqueId());
        if (last != null && now - last < 20) return;
        rodCooldown.put(p.getUniqueId(), now);
        World w = p.getWorld();
        int cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
        int here = oilLeft(w, cx, cz);
        int bestD = Integer.MAX_VALUE, bx = 0, bz = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) continue;
                int d = dx * dx + dz * dz;
                if (d < bestD && oilLeft(w, cx + dx, cz + dz) > 0) {
                    bestD = d;
                    bx = dx;
                    bz = dz;
                }
            }
        }
        Component msg = here > 0 ? Component.text(String.format("Oil under you: %,d mB", here), NamedTextColor.GOLD)
                : Component.text("No oil under this chunk", NamedTextColor.GRAY);
        if (bestD != Integer.MAX_VALUE && here <= 0) {
            msg = msg.append(Component.text("  |  nearest field: " + direction(bx, bz) + ", ~" + Math.round(Math.sqrt(bestD)) + " chunk(s)", NamedTextColor.YELLOW));
        } else if (bestD == Integer.MAX_VALUE && here <= 0) {
            msg = msg.append(Component.text("  |  nothing within 6 chunks", NamedTextColor.GRAY));
        }
        p.sendActionBar(msg);
        p.playSound(p.getLocation(), here > 0 ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_WOOD_HIT, 0.6f, 1.2f);
        int poll = pollutionAt(w.getChunkAt(cx, cz));
        if (poll > 0) p.sendMessage(Component.text("Pollution in this chunk: " + poll, NamedTextColor.DARK_GREEN));
    }

    private static String direction(int dx, int dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(((a % 360) + 360) % 360 / 45) % 8];
    }

}
