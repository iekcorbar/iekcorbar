package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorative building blocks (DecorType): real blocks drawn by the
 * Steamworks-blocks resource pack on reserved note-block states. Placing the
 * item sets the state, breaking drops the item again, and digging uses the
 * block's own hardness and tool (pickaxe or axe) instead of the note block's.
 * Ores.java keeps every reserved state from changing (physics, tuning, notes).
 */
final class Decor implements Listener {
    private final SteamworksPlugin plugin;
    private final NamespacedKey decorKey;

    Decor(SteamworksPlugin plugin) {
        this.plugin = plugin;
        decorKey = new NamespacedKey(plugin, "decor");
    }

    static BlockData data(DecorType t) {
        NoteBlock nb = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        nb.setInstrument(Instrument.CUSTOM_HEAD);
        nb.setPowered(t.key < 25);
        nb.setNote(new Note(t.key % 25));
        return nb;
    }

    /** Reserved state key of a note block (0-49), or -1 for a normal note block / other block. */
    static int key(BlockData d) {
        if (!(d instanceof NoteBlock nb) || nb.getInstrument() != Instrument.CUSTOM_HEAD) return -1;
        return nb.getNote().getId() + (nb.isPowered() ? 0 : 25);
    }

    static DecorType of(Block b) {
        if (b.getType() != Material.NOTE_BLOCK) return null;
        int k = key(b.getBlockData());
        return k < 0 ? null : DecorType.byKey(k);
    }

    ItemStack item(DecorType t, int amount) {
        ItemStack item = new ItemStack(Material.NOTE_BLOCK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Steamworks building block", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(new NamespacedKey("steamworks", t.id()));
        meta.getPersistentDataContainer().set(decorKey, PersistentDataType.STRING, t.id());
        item.setItemMeta(meta);
        return item;
    }

    DecorType itemType(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(decorKey, PersistentDataType.STRING);
        return id == null ? null : DecorType.byId(id);
    }

    void registerRecipes() {
        for (DecorType t : DecorType.values())
            plugin.items().addRecipe("decor_" + t.id(), item(t, 4), new Object[]{4, new String[]{"BB", "BA"}, 'B', t.base, 'A', t.accent});
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        DecorType t = itemType(e.getItemInHand());
        if (t != null) e.getBlockPlaced().setBlockData(data(t), false);
    }

    /** Runs after Ores' handler (which clears any old speed modifier first). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDig(BlockDamageEvent e) {
        DecorType t = of(e.getBlock());
        if (t == null || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        e.setInstaBreak(false);
        ItemStack tool = e.getItemInHand();
        double speed = toolSpeed(tool, t.pickaxe);
        double wanted = t.hardness * 30 / speed;
        plugin.ores().applySpeed(e.getPlayer(), Ores.noteBlockTicks(tool) / Math.max(1, wanted));
    }

    private static double toolSpeed(ItemStack tool, boolean pickaxe) {
        if (tool == null) return 1;
        String n = tool.getType().name();
        if (!n.endsWith(pickaxe ? "_PICKAXE" : "_AXE")) return 1;
        double s = n.startsWith("WOODEN") ? 2 : n.startsWith("STONE") ? 4 : n.startsWith("COPPER") ? 5 : n.startsWith("IRON") ? 6
                : n.startsWith("GOLDEN") ? 12 : n.startsWith("DIAMOND") ? 8 : n.startsWith("NETHERITE") ? 9 : 1;
        int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        return eff > 0 ? s + eff * eff + 1 : s;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        DecorType t = of(e.getBlock());
        if (t == null) return;
        e.setDropItems(false);
        Block b = e.getBlock();
        b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), t.pickaxe ? Sound.BLOCK_STONE_BREAK : Sound.BLOCK_WOOD_BREAK, 0.8f, 1f);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(t, 1));
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
            DecorType t = of(b);
            if (t == null) continue;
            blocks.remove(b);
            b.setType(Material.AIR, false);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(t, 1));
        }
    }
}
