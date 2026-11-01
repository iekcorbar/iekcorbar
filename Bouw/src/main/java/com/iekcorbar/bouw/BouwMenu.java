package com.iekcorbar.bouw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /bouw - the creative menu. A server cannot add tabs to the real creative inventory (that screen is client-side),
 * so every piece lives in a chest menu instead: click = one piece, shift-click = a stack. Creative mode only
 * (permission bouw.creative, default op); survival players use the recipes (see /zukan).
 */
final class BouwMenu implements Listener {
    private final BouwPlugin plugin;

    BouwMenu(BouwPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        String family = "";
        int page;
        List<ItemStack> shown = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p, String family) {
        if (!p.hasPermission("bouw.creative") || p.getGameMode() != GameMode.CREATIVE) {
            p.sendMessage(Component.text("The Bouw menu is for creative mode (permission bouw.creative). Craft pieces in survival - see /zukan.", NamedTextColor.RED));
            return;
        }
        Holder h = new Holder();
        h.family = family;
        draw(p, h);
    }

    private void draw(Player p, Holder h) {
        h.shown.clear();
        for (DecorDef d : plugin.bouw.decor.defs.values())
            if (h.family.isEmpty() || h.family.equals(plugin.bouw.familyOf.get(d.id))) h.shown.add(plugin.bouw.decor.item(d, 1));
        for (Shapes.Line l : plugin.shapes.lines.values())
            if (h.family.isEmpty() || h.family.equals(l.id()))
                for (Shapes.Piece pc : Shapes.Piece.values()) h.shown.add(plugin.shapes.item(l, pc, 1));
        int pages = Math.max(1, (h.shown.size() + 44) / 45);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        h.inv = plugin.getServer().createInventory(h, 54, Component.text("Bouw " + (h.family.isEmpty() ? "- all" : "- " + h.family) + " (" + (h.page + 1) + "/" + pages + ")"));
        for (int i = 0; i < 45; i++) {
            int at = h.page * 45 + i;
            if (at >= h.shown.size()) break;
            h.inv.setItem(i, h.shown.get(at).clone());
        }
        h.inv.setItem(45, button(Material.ARROW, "Previous page"));
        h.inv.setItem(53, button(Material.ARROW, "Next page"));
        h.inv.setItem(49, button(Material.COMPASS, "Family: " + (h.family.isEmpty() ? "all" : h.family) + " (click to switch)"));
        p.openInventory(h.inv);
    }

    private static ItemStack button(Material m, String name) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        if (s == 45) { h.page--; draw(p, h); return; }
        if (s == 53) { h.page++; draw(p, h); return; }
        if (s == 49) {
            List<String> fams = new ArrayList<>();
            fams.add("");
            for (Shapes.Line l : plugin.shapes.lines.values()) fams.add(l.id());
            for (String[] f : plugin.bouw.families) fams.add(f[0]);
            h.family = fams.get((fams.indexOf(h.family) + 1) % fams.size());
            h.page = 0;
            draw(p, h);
            return;
        }
        int at = h.page * 45 + s;
        if (s >= 45 || at >= h.shown.size()) return;
        ItemStack give = h.shown.get(at).clone();
        give.setAmount(e.isShiftClick() ? 64 : 1);
        for (ItemStack left : p.getInventory().addItem(give).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
