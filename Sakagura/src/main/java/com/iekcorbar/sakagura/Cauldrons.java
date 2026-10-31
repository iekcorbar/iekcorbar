package com.iekcorbar.sakagura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Brewing cauldrons: a full water cauldron with ingredients thrown in (right-click, one at
 * a time). With heat below (fire, campfire, lava, magma, a lit furnace) it cooks; without
 * heat it mixes. Minutes count in real time while the chunk is loaded. A glass bottle
 * draws one brew (three per cauldron). State is saved in the chunk's data.
 */
final class Cauldrons implements Listener {
    private static final Set<Material> HEAT = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.MAGMA_BLOCK);
    private static final Set<Material> LIT = Set.of(Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FURNACE, Material.SMOKER, Material.BLAST_FURNACE);

    static final class Pot {
        final Brew brew = new Brew();
        final World world;
        final int x, y, z;

        Pot(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Block block() {
            return world.getBlockAt(x, y, z);
        }
    }

    private final Sakagura plugin;
    private final NamespacedKey dataKey;
    private final Map<UUID, Map<Long, Pot>> pots = new HashMap<>();

    Cauldrons(Sakagura plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "cauldrons");
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    Pot at(Block b) {
        Map<Long, Pot> m = pots.get(b.getWorld().getUID());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    private void remove(Pot p) {
        Map<Long, Pot> m = pots.get(p.world.getUID());
        if (m != null) m.remove(key(p.x, p.y, p.z));
        save(p.world, p.x >> 4, p.z >> 4);
    }

    static boolean heated(Block cauldron) {
        Block b = cauldron.getRelative(BlockFace.DOWN);
        if (HEAT.contains(b.getType())) return true;
        return LIT.contains(b.getType()) && b.getBlockData() instanceof Lightable l && l.isLit();
    }

    // ------------------------------------------------------------ using

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.WATER_CAULDRON) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        Pot pot = at(b);
        if (hand == null || hand.isEmpty()) {
            if (pot != null) {
                e.setCancelled(true);
                p.sendActionBar(Component.text(describe(pot), NamedTextColor.GOLD));
            }
            return;
        }
        if (hand.getType() == Material.GLASS_BOTTLE) {
            if (pot == null) return; // plain water bottle as usual
            e.setCancelled(true);
            draw(pot, p, hand);
            return;
        }
        if (hand.getType() == Material.WATER_BUCKET || hand.getType() == Material.BUCKET || hand.getType() == Material.POTION && plugin.goods().brewOf(hand) == null)
            return;
        if (!(b.getBlockData() instanceof Levelled lv) || lv.getLevel() < lv.getMaximumLevel()) {
            p.sendActionBar(Component.text("Fill the cauldron with water first.", NamedTextColor.GRAY));
            e.setCancelled(true);
            return;
        }
        String key = Ingredients.of(hand, plugin);
        if (key == null || hand.getType().isBlock() && !hand.getType().isAir() && plugin.goods().idOf(hand) == null && !plain(hand.getType())) return;
        e.setCancelled(true);
        if (pot == null) {
            pot = new Pot(b.getWorld(), b.getX(), b.getY(), b.getZ());
            pots.computeIfAbsent(b.getWorld().getUID(), k -> new HashMap<>()).put(key(b.getX(), b.getY(), b.getZ()), pot);
            // joke: brewing with the great outdoors as your tap water — terroir!
            String biome = b.getBiome().getKey().getKey();
            if ((biome.contains("river") || biome.contains("ocean") || biome.contains("beach"))
                    && !plugin.adv().has(p, "terroir")) {
                p.sendActionBar(Component.text("Bold choice. Bold label.", NamedTextColor.GOLD));
                plugin.adv().grant(p, "terroir");
            }
        }
        int total = 0;
        for (int v : pot.brew.ingredients.values()) total += v;
        if (total >= 64) {
            p.sendActionBar(Component.text("The cauldron is full of ingredients.", NamedTextColor.GRAY));
            return;
        }
        pot.brew.ingredients.merge(key, 1, Integer::sum);
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (hand.getType() == Material.MILK_BUCKET) p.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            else hand.setAmount(hand.getAmount() - 1);
        }
        Location top = b.getLocation().add(0.5, 0.9, 0.5);
        b.getWorld().spawnParticle(Particle.SPLASH, top, 8, 0.2, 0, 0.2, 0);
        b.getWorld().playSound(top, "sakagura:cauldron.add", 0.6f, 1f);
        p.swingMainHand();
        save(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
    }

    /** Block items that are still ingredients (grass, flowers, plants). */
    private static boolean plain(Material m) {
        String n = m.name();
        return n.endsWith("GRASS") || n.endsWith("FERN") || n.endsWith("ORCHID") || n.endsWith("PETALS") || n.endsWith("TULIP")
                || n.endsWith("DANDELION") || n.endsWith("POPPY") || n.endsWith("ALLIUM") || n.endsWith("BLUET") || n.endsWith("DAISY")
                || n.endsWith("CORNFLOWER") || n.endsWith("LILY_OF_THE_VALLEY") || n.equals("SUGAR_CANE") || n.equals("CACTUS")
                || n.endsWith("MUSHROOM") || n.equals("SNOW_BLOCK") || n.equals("PUMPKIN") || n.equals("MELON") || n.equals("GLASS")
                || n.equals("HONEY_BLOCK") || n.endsWith("SAPLING") || n.equals("KELP") || n.equals("BAMBOO") || n.endsWith("LEAVES");
    }

    private void draw(Pot pot, Player p, ItemStack hand) {
        Block b = pot.block();
        if (!(b.getBlockData() instanceof Levelled lv)) return;
        Brew brew = pot.brew.copy();
        brew.seq = "";
        brew.step(brew.cook >= brew.mix ? 'C' : 'M');
        ItemStack bottle = plugin.goods().bottle(brew, 1);
        plugin.adv().grant(p, "cauldron");
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        for (ItemStack left : p.getInventory().addItem(bottle).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        int level = lv.getLevel() - 1;
        if (level <= 0) {
            b.setType(Material.CAULDRON);
            remove(pot);
        } else {
            lv.setLevel(level);
            b.setBlockData(lv);
            save(b.getWorld(), b.getX() >> 4, b.getZ() >> 4);
        }
        b.getWorld().playSound(b.getLocation(), "minecraft:item.bottle.fill", 1f, 0.9f);
    }

    private String describe(Pot pot) {
        StringJoiner sj = new StringJoiner(", ");
        for (Map.Entry<String, Integer> e : pot.brew.ingredients.entrySet()) sj.add(e.getValue() + " " + Ingredients.label(e.getKey(), plugin));
        boolean hot = heated(pot.block());
        double t = hot ? pot.brew.cook : pot.brew.mix;
        return (hot ? "Cooking " : "Mixing ") + Math.round(t) + " min: " + sj;
    }

    /** Water taken out some other way (bucket, evaporation): the brew is gone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLevel(CauldronLevelChangeEvent e) {
        Pot pot = at(e.getBlock());
        if (pot == null) return;
        if (e.getNewState().getType() != Material.WATER_CAULDRON) remove(pot);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Pot pot = at(e.getBlock());
        if (pot != null) remove(pot);
    }

    // ------------------------------------------------------------ cooking (every second)

    void tick(long tick) {
        for (Map<Long, Pot> m : pots.values()) {
            List<Pot> dead = new ArrayList<>();
            for (Pot pot : m.values()) {
                if (!pot.world.isChunkLoaded(pot.x >> 4, pot.z >> 4)) continue;
                Block b = pot.block();
                if (b.getType() != Material.WATER_CAULDRON) {
                    dead.add(pot); // cauldron gone (exploded or broken without our break event) - drop the ghost brew
                    continue;
                }
                boolean hot = heated(b);
                // co-op: 2+ players near the cauldron cook/mix twice as fast
                int nearby = (int) b.getWorld().getNearbyPlayers(b.getLocation().add(0.5, 0.5, 0.5), 4).size();
                double speed = nearby >= 2 ? 2.0 : 1.0;
                if (hot) pot.brew.cook += speed / 60.0;
                else pot.brew.mix += speed / 60.0;
                Location top = b.getLocation().add(0.5, 0.95, 0.5);
                if (hot) {
                    pot.world.spawnParticle(Particle.BUBBLE_POP, top, 2, 0.2, 0, 0.2, 0);
                    Recipes.Match guess = plugin.recipes().match(trial(pot));
                    Color c = guess != null ? guess.recipe().color : Color.fromRGB(110, 100, 80);
                    if (tick % 3 == 0) pot.world.spawnParticle(Particle.DUST, top, 2, 0.2, 0.02, 0.2, 0, new Particle.DustOptions(c, 1f));
                    if (tick % 4 == 0) pot.world.playSound(top, "sakagura:cauldron.bubble", 0.35f, 1f);
                }
            }
            for (Pot pot : dead) remove(pot);
            if (tick % 30 == 0) {
                for (Pot pot : m.values()) save(pot.world, pot.x >> 4, pot.z >> 4);
            }
        }
    }

    private static Brew trial(Pot pot) {
        Brew b = pot.brew.copy();
        b.seq = "";
        b.step(b.cook >= b.mix ? 'C' : 'M');
        return b;
    }

    // ------------------------------------------------------------ saving

    private void save(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx, cz)) return;
        Chunk c = w.getChunkAt(cx, cz);
        StringBuilder sb = new StringBuilder();
        Map<Long, Pot> m = pots.get(w.getUID());
        if (m != null) {
            for (Pot p : m.values()) {
                if (p.x >> 4 != cx || p.z >> 4 != cz) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(p.x).append(' ').append(p.y).append(' ').append(p.z).append(' ').append(p.brew.format());
            }
        }
        if (sb.isEmpty()) c.getPersistentDataContainer().remove(dataKey);
        else c.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, sb.toString());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        load(e.getChunk());
    }

    void load(Chunk c) {
        String data = c.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (data == null) return;
        for (String line : data.split("\n")) {
            try {
                String[] p = line.split(" ", 4);
                Pot pot = new Pot(c.getWorld(), Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                Brew b = Brew.parse(p[3]);
                pot.brew.ingredients.putAll(b.ingredients);
                pot.brew.cook = b.cook;
                pot.brew.mix = b.mix;
                pots.computeIfAbsent(c.getWorld().getUID(), k -> new HashMap<>()).put(key(pot.x, pot.y, pot.z), pot);
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk c = e.getChunk();
        save(c.getWorld(), c.getX(), c.getZ());
        Map<Long, Pot> m = pots.get(c.getWorld().getUID());
        if (m != null) m.values().removeIf(p -> p.x >> 4 == c.getX() && p.z >> 4 == c.getZ());
    }

    void saveAll() {
        for (Map<Long, Pot> m : pots.values()) for (Pot p : m.values()) save(p.world, p.x >> 4, p.z >> 4);
    }
}
