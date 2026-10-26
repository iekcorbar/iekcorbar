package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom ores as note blocks in reserved states (instrument custom_head,
 * powered, note = ore id) - see OreType. Keeps those states from changing
 * (physics, tuning, playing), gives them ore-like mining (pickaxe tier,
 * hardness, Fortune/Silk Touch) by scaling the player's block-break-speed
 * attribute while they dig one, and scatters veins into chunks the first
 * time each chunk loads (marked in the chunk's data, so only once).
 */
final class Ores implements Listener {
    private static final Set<Material> STONE_HOSTS = EnumSet.of(Material.STONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE);
    private static final Set<Material> DEEP_HOSTS = EnumSet.of(Material.DEEPSLATE, Material.TUFF);

    record Vein(OreType stone, OreType deep, World.Environment env, int minY, int maxY, int perChunk, int size) {}

    private final SteamworksPlugin plugin;
    private final NamespacedKey oreKey, genKey, speedKey;
    private final List<Vein> veins = new ArrayList<>();
    private boolean existingChunks;

    Ores(SteamworksPlugin plugin) {
        this.plugin = plugin;
        oreKey = new NamespacedKey(plugin, "ore");
        genKey = new NamespacedKey(plugin, "ores_v1");
        speedKey = new NamespacedKey(plugin, "ore_mining");
    }

    void load(ConfigurationSection c) {
        veins.clear();
        existingChunks = c == null || c.getBoolean("generate-in-existing-chunks", true);
        veins.add(vein(c, "zinc", OreType.ZINC_ORE, OreType.DEEPSLATE_ZINC_ORE, World.Environment.NORMAL, -48, 72, 7, 7));
        veins.add(vein(c, "tin", OreType.TIN_ORE, OreType.DEEPSLATE_TIN_ORE, World.Environment.NORMAL, -24, 96, 6, 6));
        veins.add(vein(c, "sulfur", OreType.SULFUR_ORE, OreType.DEEPSLATE_SULFUR_ORE, World.Environment.NORMAL, -64, 0, 3, 6));
        veins.add(vein(c, "nether-sulfur", OreType.NETHER_SULFUR_ORE, OreType.NETHER_SULFUR_ORE, World.Environment.NETHER, 10, 118, 7, 7));
        veins.removeIf(v -> v.perChunk() <= 0);
    }

    private static Vein vein(ConfigurationSection c, String key, OreType stone, OreType deep, World.Environment env, int minY, int maxY, int per, int size) {
        ConfigurationSection s = c == null ? null : c.getConfigurationSection(key);
        if (s == null) return new Vein(stone, deep, env, minY, maxY, per, size);
        return new Vein(stone, deep, env, s.getInt("min-y", minY), s.getInt("max-y", maxY), s.getInt("veins-per-chunk", per), s.getInt("vein-size", size));
    }

    // ------------------------------------------------------------ block states

    static BlockData data(OreType t) {
        NoteBlock nb = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        nb.setInstrument(Instrument.CUSTOM_HEAD);
        nb.setNote(new Note(t.noteId));
        nb.setPowered(true);
        return nb;
    }

    static OreType of(Block b) {
        if (b.getType() != Material.NOTE_BLOCK) return null;
        return of(b.getBlockData());
    }

    static OreType of(BlockData d) {
        if (!(d instanceof NoteBlock nb) || !nb.isPowered() || nb.getInstrument() != Instrument.CUSTOM_HEAD) return null;
        return OreType.byNote(nb.getNote().getId());
    }

    // ------------------------------------------------------------ items

    ItemStack item(OreType t, int amount) {
        ItemStack item = new ItemStack(Material.NOTE_BLOCK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Steamworks", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(new NamespacedKey("steamworks", t.id()));
        meta.getPersistentDataContainer().set(oreKey, PersistentDataType.STRING, t.id());
        item.setItemMeta(meta);
        return item;
    }

    OreType itemType(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(oreKey, PersistentDataType.STRING);
        return id == null ? null : OreType.byId(id);
    }

    // ------------------------------------------------------------ keep the states stable

    /** Any reserved note-block state: ores and decorative blocks (see Decor.key). */
    private static boolean reserved(Block b) {
        return b.getType() == Material.NOTE_BLOCK && Decor.key(b.getBlockData()) >= 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (reserved(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent e) {
        if (reserved(e.getBlock())) e.setCancelled(true);
    }

    /** Right-click would tune it (= change the note = another ore). Placing blocks against it still works. */
    @EventHandler(priority = EventPriority.LOW)
    public void onTune(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && reserved(e.getClickedBlock()))
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.NOTE_BLOCK) return;
        OreType t = itemType(e.getItemInHand());
        if (t != null) {
            e.getBlockPlaced().setBlockData(data(t), false);
        } else if (plugin.decor().itemType(e.getItemInHand()) == null && reserved(e.getBlockPlaced())) {
            // a plain note block that lands in a reserved state (a head above it): give it a normal instrument
            NoteBlock nb = (NoteBlock) e.getBlockPlaced().getBlockData();
            nb.setInstrument(Instrument.PIANO);
            e.getBlockPlaced().setBlockData(nb, false);
        }
    }

    // ------------------------------------------------------------ mining

    private record Pick(int tier, double speed) {}

    private static Pick pick(ItemStack tool) {
        if (tool == null) return null;
        String n = tool.getType().name();
        if (!n.endsWith("_PICKAXE")) return null;
        return switch (n) {
            case "WOODEN_PICKAXE" -> new Pick(0, 2);
            case "STONE_PICKAXE" -> new Pick(1, 4);
            case "COPPER_PICKAXE" -> new Pick(1, 5);
            case "IRON_PICKAXE" -> new Pick(2, 6);
            case "GOLDEN_PICKAXE" -> new Pick(0, 12);
            case "DIAMOND_PICKAXE" -> new Pick(3, 8);
            case "NETHERITE_PICKAXE" -> new Pick(4, 9);
            default -> new Pick(1, 4);
        };
    }

    /** Break ticks vanilla would use for a note block with this tool (decorative blocks scale against it). */
    static double noteBlockTicks(ItemStack tool) {
        return vanillaTicks(tool);
    }

    /** Scales the player's break speed for the block being dug (removed again when they stop). */
    void applySpeed(Player p, double factor) {
        clearSpeed(p);
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null) a.addTransientModifier(new AttributeModifier(speedKey, factor - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    /** What vanilla thinks of a note block: axes are the right tool (hardness 0.8). */
    private static double vanillaTicks(ItemStack tool) {
        double speed = 1;
        if (tool != null && tool.getType().name().endsWith("_AXE")) {
            String n = tool.getType().name();
            speed = n.startsWith("WOODEN") ? 2 : n.startsWith("STONE") ? 4 : n.startsWith("COPPER") ? 5 : n.startsWith("IRON") ? 6
                    : n.startsWith("GOLDEN") ? 12 : n.startsWith("DIAMOND") ? 8 : n.startsWith("NETHERITE") ? 9 : 1;
            int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
            if (eff > 0) speed += eff * eff + 1;
        }
        return 0.8 * 30 / speed;
    }

    private static double wantedTicks(OreType t, ItemStack tool) {
        Pick p = pick(tool);
        if (p == null || p.tier() < t.tier) return t.hardness * 100; // wrong tool: slow and nothing drops
        double speed = p.speed();
        int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (eff > 0) speed += eff * eff + 1;
        return t.hardness * 30 / speed;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDig(BlockDamageEvent e) {
        OreType t = of(e.getBlock());
        clearSpeed(e.getPlayer());
        if (t == null || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        e.setInstaBreak(false);
        ItemStack tool = e.getItemInHand();
        double factor = vanillaTicks(tool) / wantedTicks(t, tool);
        AttributeInstance a = e.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null) a.addTransientModifier(new AttributeModifier(speedKey, factor - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent e) {
        clearSpeed(e.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        clearSpeed(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clearSpeed(e.getPlayer());
    }

    private void clearSpeed(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null) a.removeModifier(speedKey);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        clearSpeed(e.getPlayer());
        OreType t = of(e.getBlock());
        if (t == null) return;
        e.setDropItems(false);
        e.setExpToDrop(0);
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Pick pk = pick(tool);
        if (pk == null || pk.tier() < t.tier) return;
        var at = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            e.getBlock().getWorld().dropItemNaturally(at, item(t, 1));
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int count = t.minDrop + r.nextInt(t.maxDrop - t.minDrop + 1);
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        if (fortune > 0) count *= Math.max(1, r.nextInt(fortune + 2));
        e.getBlock().getWorld().dropItemNaturally(at, plugin.items().createMaterial(t.drop, count));
        if (t.xp > 0) e.setExpToDrop(r.nextInt(t.xp + 1));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        explode(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        explode(e.blockList());
    }

    private void explode(List<Block> blocks) {
        for (Block b : new ArrayList<>(blocks)) {
            OreType t = of(b);
            if (t == null) continue;
            blocks.remove(b);
            b.setType(Material.AIR, false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), plugin.items().createMaterial(t.drop, t.minDrop));
        }
    }

    // ------------------------------------------------------------ world generation

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!existingChunks && !e.isNewChunk()) return;
        Chunk c = e.getChunk();
        if (c.getPersistentDataContainer().has(genKey, PersistentDataType.BYTE)) return;
        generate(c);
        c.getPersistentDataContainer().set(genKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void generate(Chunk c) {
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Vein v : veins) {
            if (w.getEnvironment() != v.env()) continue;
            int minY = Math.max(v.minY(), w.getMinHeight()), maxY = Math.min(v.maxY(), w.getMaxHeight() - 1);
            if (maxY <= minY) continue;
            for (int i = 0; i < v.perChunk(); i++) {
                int x = 2 + r.nextInt(12), y = minY + r.nextInt(maxY - minY), z = 2 + r.nextInt(12);
                for (int k = 0; k < v.size(); k++) {
                    Block b = c.getBlock(x, y, z);
                    Material m = b.getType();
                    OreType t = null;
                    if (v.env() == World.Environment.NETHER) {
                        if (m == Material.NETHERRACK) t = v.stone();
                    } else if (STONE_HOSTS.contains(m)) {
                        t = v.stone();
                    } else if (DEEP_HOSTS.contains(m)) {
                        t = v.deep();
                    }
                    if (t != null) b.setBlockData(data(t), false);
                    // random walk to a neighbour inside the chunk
                    switch (r.nextInt(6)) {
                        case 0 -> x = Math.min(15, x + 1);
                        case 1 -> x = Math.max(0, x - 1);
                        case 2 -> y = Math.min(maxY, y + 1);
                        case 3 -> y = Math.max(minY, y - 1);
                        case 4 -> z = Math.min(15, z + 1);
                        default -> z = Math.max(0, z - 1);
                    }
                }
            }
        }
    }

}
