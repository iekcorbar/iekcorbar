package com.iekcorbar.relics;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Collector's Guide: a written book that rebuilds itself every time it is opened. A cover with overall progress,
 * a clickable contents page and one section per collection where every relic the reader has ever carried gets a
 * stamp. A finished collection gets a gold seal. The progress is the same data the browse menu uses (RelicsMenu).
 */
final class RelicGuide implements Listener {
    private static final int PER_PAGE = 8;
    private final RelicsPlugin plugin;
    private final NamespacedKey marker;

    RelicGuide(RelicsPlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "guide");
        ShapelessRecipe r = new ShapelessRecipe(new NamespacedKey(plugin, "collector_guide"), item());
        r.addIngredient(Material.BOOK);
        r.addIngredient(Material.INK_SAC);
        r.addIngredient(Material.FEATHER);
        Bukkit.removeRecipe(r.getKey(), false);
        Bukkit.addRecipe(r);
    }

    boolean isGuide(ItemStack it) {
        return it != null && it.getType() == Material.WRITTEN_BOOK && it.hasItemMeta()
                && "1".equals(it.getItemMeta().getPersistentDataContainer().get(marker, PersistentDataType.STRING));
    }

    /** The item itself: its pages are placeholders, the real ones are built for the reader when they open it. */
    ItemStack item() {
        WrittenBookContent.Builder b = WrittenBookContent.writtenBookContent("Collector's Guide", "The Relic Society").generation(0).resolved(true);
        b.addPage(Component.text("Right-click to read. Every relic you have carried gets a stamp.", NamedTextColor.DARK_GRAY));
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, b.build());
        ItemMeta m = book.getItemMeta();
        m.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "1");
        book.setItemMeta(m);
        return book;
    }

    /** A fresh copy of the guide with this reader's stamps in it. */
    ItemStack build(Player p) {
        Set<String> have = plugin.menu().owned(p);
        RelicType.Collection[] cols = RelicType.Collection.values();
        List<List<RelicType>> members = new ArrayList<>();
        int total = 0, got = 0, firstPage = 3;
        int[] start = new int[cols.length];
        for (int i = 0; i < cols.length; i++) {
            List<RelicType> l = new ArrayList<>();
            for (RelicType t : RelicType.values()) if (t.collection == cols[i]) l.add(t);
            members.add(l);
            total += l.size();
            for (RelicType t : l) if (have.contains(t.id())) got++;
            start[i] = firstPage;
            firstPage += Math.max(1, (l.size() + PER_PAGE - 1) / PER_PAGE);
        }

        WrittenBookContent.Builder b = WrittenBookContent.writtenBookContent("Collector's Guide", "The Relic Society").generation(0).resolved(true);
        int filled = total == 0 ? 0 : (int) Math.round(10.0 * got / total);
        b.addPage(Component.text()
                .append(Component.text("Collector's Guide\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .append(Component.text("Every relic you have ever carried earns a stamp.\n\n", NamedTextColor.GRAY))
                .append(Component.text("■".repeat(filled), NamedTextColor.DARK_GREEN))
                .append(Component.text("□".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text("\n" + got + " / " + total + " stamped\n\n", NamedTextColor.BLACK))
                .append(Component.text("[ Contents ]", NamedTextColor.BLUE).clickEvent(ClickEvent.changePage(2)))
                .build());

        TextComponent.Builder toc = Component.text().append(Component.text("Contents\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        for (int i = 0; i < cols.length; i++) {
            int n = 0;
            for (RelicType t : members.get(i)) if (have.contains(t.id())) n++;
            boolean done = n == members.get(i).size() && n > 0;
            toc.append(Component.text((done ? "★ " : "") + cols[i].title + " " + n + "/" + members.get(i).size() + "\n",
                    done ? NamedTextColor.GOLD : NamedTextColor.DARK_BLUE).clickEvent(ClickEvent.changePage(start[i])));
        }
        b.addPage(toc.build());

        for (int i = 0; i < cols.length; i++) {
            List<RelicType> l = members.get(i);
            int n = 0;
            for (RelicType t : l) if (have.contains(t.id())) n++;
            boolean done = n == l.size() && n > 0;
            int pages = Math.max(1, (l.size() + PER_PAGE - 1) / PER_PAGE);
            for (int pg = 0; pg < pages; pg++) {
                TextComponent.Builder page = Component.text()
                        .append(Component.text(cols[i].title + (pages > 1 ? " (" + (pg + 1) + "/" + pages + ")" : "") + "\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                        .append(done ? Component.text("★ COMPLETE ★\n", NamedTextColor.GOLD, TextDecoration.BOLD)
                                : Component.text(n + " / " + l.size() + " stamped\n", NamedTextColor.GRAY));
                for (int k = pg * PER_PAGE; k < Math.min(l.size(), (pg + 1) * PER_PAGE); k++) {
                    RelicType t = l.get(k);
                    boolean has = have.contains(t.id());
                    page.append(Component.text(has ? "✔ " : "· ", has ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY)
                            .append(Component.text(t.displayName + "\n", has ? NamedTextColor.BLACK : NamedTextColor.GRAY)
                                    .hoverEvent(HoverEvent.showText(Component.text(has ? t.lore : "Not collected yet.", NamedTextColor.GRAY)))));
                }
                page.append(Component.text("[ Contents ]", NamedTextColor.BLUE).clickEvent(ClickEvent.changePage(2)));
                b.addPage(page.build());
            }
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, b.build());
        return book;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isGuide(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.openBook(build(p));
    }
}
