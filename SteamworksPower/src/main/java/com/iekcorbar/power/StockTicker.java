package com.iekcorbar.power;

import com.iekcorbar.steamworks.api.CustomNode;
import com.iekcorbar.steamworks.api.CustomPart;
import com.iekcorbar.steamworks.api.CustomVisual;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock Ticker: a small brass counter against a chest, barrel or tank. It counts what is inside
 * and shows the busiest stacks on a floating plate: "Cobblestone x128  |  Flint x40".
 */
final class StockTicker implements CustomPart {
    private static final BlockFace[] AROUND = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};
    private final PowerPlugin plugin;

    StockTicker(PowerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String id() { return "pow_stock_ticker"; }
    @Override public String displayName() { return "Stock Ticker"; }
    @Override public String[] lore() {
        return new String[]{"Counts the container it leans against,", "on a floating plate. No rotation needed."};
    }
    @Override public String ownerPlugin() { return "SteamworksPower"; }
    @Override public Material particleBlock() { return Material.COPPER_BLOCK; }

    @Override public EnumSet<BlockFace> ports(org.bukkit.Axis axis) { return EnumSet.noneOf(BlockFace.class); }
    @Override public boolean rotatable() { return false; }
    @Override public java.util.List<com.iekcorbar.steamworks.api.CustomVisual> visuals(CustomNode n) {
        return List.of(CustomVisual.statik("pow_stock_ticker"));
    }
    @Override public org.bukkit.Axis initialAxis(BlockFace face, org.bukkit.entity.Player p) { return org.bukkit.Axis.Y; }

    @Override
    public void tick(CustomNode n, long tick) {
        if (tick % 40 != 0) return;
        boolean seen = !n.world().getNearbyPlayers(n.center(), 24).isEmpty();
        TextDisplay plate = (TextDisplay) n.data();
        if (!seen) {
            if (plate != null) {
                plate.remove();
                n.data(null);
            }
            return;
        }
        Container c = findContainer(n);
        if (c == null) {
            if (plate != null) {
                plate.remove();
                n.data(null);
            }
            return;
        }
        String line = read(c.getInventory());
        if (plate == null || !plate.isValid()) {
            Location at = n.center().add(0, 0.75, 0);
            plate = n.world().spawn(at, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(TextDisplay.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            n.data(plate);
        }
        plate.text(Component.text(line, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
    }

    private Container findContainer(CustomNode n) {
        Block here = n.block();
        for (BlockFace f : AROUND) {
            Block b = here.getRelative(f);
            if (b.getState(false) instanceof Container c) return c;
        }
        return null;
    }

    private static String read(Inventory inv) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        int slots = 0;
        for (ItemStack s : inv.getContents()) {
            if (s == null || s.isEmpty()) continue;
            slots++;
            String name = s.getItemMeta() != null && s.getItemMeta().hasDisplayName()
                    ? "•" : s.getType().name();
            long[] v = counts.computeIfAbsent(name, k -> new long[1]);
            v[0] += s.getAmount();
        }
        if (counts.isEmpty()) return "empty  ·  " + inv.getSize() + " slots";
        String[] top = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(2)
                .map(e -> nice(e.getKey()) + " x" + e.getValue()[0])
                .toArray(String[]::new);
        return String.join("  |  ", top) + "  ·  " + slots + "/" + inv.getSize();
    }

    private static String nice(String material) {
        if (material.equals("•")) return "named items";
        StringBuilder sb = new StringBuilder();
        boolean word = true;
        for (char ch : material.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (ch == '_') {
                word = true;
                continue;
            }
            sb.append(word ? Character.toUpperCase(ch) : ch);
            word = false;
        }
        return sb.toString();
    }

    @Override
    public void onRemove(CustomNode n) {
        if (n.data() instanceof TextDisplay t) t.remove();
        n.data(null);
    }
}
