package com.iekcorbar.power;

import com.iekcorbar.steamworks.SteamworksPlugin;

import com.iekcorbar.steamworks.api.CustomNode;
import com.iekcorbar.steamworks.api.CustomPart;
import com.iekcorbar.steamworks.api.CustomVisual;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sieve Funnel: a funnel with a brass mesh. Right-click it with any item and the mesh sifts
 * for THAT item only: what matches falls into the container under the sieve, what does not
 * match is tossed off the side and the line keeps running. Right-click empty to see the
 * filter, sneak + right-click empty to clear it (an open sieve lets everything through).
 */
public final class SieveFunnel implements CustomPart {
    private final SteamworksPlugin plugin;

    public SieveFunnel(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String id() { return "pow_sieve_funnel"; }
    @Override public String displayName() { return "Sieve Funnel"; }
    @Override public String[] lore() {
        return new String[]{"A funnel with a brass mesh.", "Right-click with an item: only that passes - the rest is tossed aside."};
    }
    @Override public String ownerPlugin() { return "SteamworksPower"; }
    @Override public Material particleBlock() { return Material.IRON_BLOCK; }

    @Override
    public List<CustomVisual> visuals(CustomNode n) {
        return List.of(CustomVisual.statik(id()));          // like the funnel: laid out, never turning
    }

    // ------------------------------------------------------------ the mesh

    /** Material + item model: enough to tell our plate items and plain vanilla apart. */
    static String signature(ItemStack it) {
        String model = "";
        try {
            var key = it.getData(DataComponentTypes.ITEM_MODEL);
            if (key != null) model = key.toString();
        } catch (Exception ignored) {
        }
        return it.getType().name() + "|" + model;
    }

    private static String filter(CustomNode n) {
        return n.data() instanceof String s ? s : null;
    }

    private static String pretty(String sig) {
        int c = sig.indexOf('|');
        String mat = sig.substring(0, c);
        String model = sig.substring(c + 1);
        String name = mat.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (mat.equals("PAPER") && !model.isEmpty()) name = name + " (" + model.substring(model.indexOf(':') + 1).replace('_', ' ') + ")";
        return name;
    }

    @Override
    public ItemStack handleItem(CustomNode n, ItemStack incoming) {
        String f = filter(n);
        if (f != null && !f.equals(signature(incoming))) {
            // not this sieve's item: bounce it off the running side and let the line flow on
            BlockFace end = CustomPart.positive(n.axis());
            Vector along = new Vector(end.getModX(), end.getModY(), end.getModZ())
                    .crossProduct(new Vector(0, 1, 0));
            if (n.speed() < 0) along.multiply(-1);
            n.world().dropItemNaturally(n.center().add(along.multiply(0.45)).add(0, -0.25, 0), incoming.clone());
            n.world().playSound(n.center(), "block.grindstone.use", 0.25f, 1.7f);
            return incoming.asQuantity(0);                  // the belt item is consumed by the bounce
        }
        Block below = n.block().getRelative(BlockFace.DOWN);
        if (!(below.getState(false) instanceof Container c)) return incoming;  // nowhere to go: hold on the belt
        Map<Integer, ItemStack> rest = c.getInventory().addItem(incoming.clone());
        int left = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
        return incoming.asQuantity(left);
    }

    // ------------------------------------------------------------ setting the filter

    @Override
    public boolean interact(CustomNode n, Player p, ItemStack hand) {
        if (hand != null && !hand.getType().isAir()) {
            String sig = signature(hand);
            n.data(sig);
            markDirty(n);
            n.world().playSound(n.center(), "block.note_block.pling", 0.5f, 1.4f);
            p.sendActionBar(Component.text("Sieve set: " + pretty(sig), NamedTextColor.GOLD));
            return true;
        }
        String f = filter(n);
        if (p.isSneaking()) {
            if (f == null) return false;
            n.data(null);
            markDirty(n);
            p.sendActionBar(Component.text("Sieve opened - everything passes.", NamedTextColor.YELLOW));
            return true;
        }
        p.sendActionBar(Component.text(f == null ? "Sieve: no filter - everything passes." : "Sieve: " + pretty(f),
                f == null ? NamedTextColor.GRAY : NamedTextColor.GOLD));
        return true;
    }

    private void markDirty(CustomNode n) {
        plugin.markDirty(n.block());                      // the filter lives in the chunk save
    }

    // ------------------------------------------------------------ persistence

    @Override
    public byte[] saveState(CustomNode n) {
        String f = filter(n);
        return f == null ? null : f.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void loadState(CustomNode n, byte[] data) {
        n.data(data == null || data.length == 0 ? null : new String(data, StandardCharsets.UTF_8));
    }
}
