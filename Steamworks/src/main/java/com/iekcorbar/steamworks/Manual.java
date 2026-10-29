package com.iekcorbar.steamworks;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The interactive Engineer's Manual: a written book that rebuilds itself every time it is
 * opened. Contents and chapter pages are clickable (vanilla change_page), every part links to
 * its recipe in Zukan, and per part the reader sees three dots: placed it / saw it turn /
 * crafted it - tracked from real gameplay in the player's persistent data.
 */
final class Manual implements Listener {
    private final SteamworksPlugin plugin;
    private final NamespacedKey marker, progress;

    /** A chapter: title, one-line intro, a hands-on tip and the parts it covers. */
    record Chapter(String title, String intro, String tip, PartType[] parts) {
    }

    static final Chapter[] CHAPTERS = {
        new Chapter("Shafts & Gears",
                "Everything in Steamworks turns through these.",
                "Tip: click a part with the Steam Wrench to turn it around the face you clicked. Sneak + click packs it back up.",
                new PartType[]{PartType.SHAFT, PartType.COGWHEEL, PartType.LARGE_COGWHEEL, PartType.GEARBOX,
                        PartType.SPROCKET, PartType.CLUTCH, PartType.GEARSHIFT}),
        new Chapter("Power",
                "Five ways to make a network spin.",
                "Tip: watch a Stress Gauge - too much load on one source and the whole line jams.",
                new PartType[]{PartType.HAND_CRANK, PartType.WATER_WHEEL, PartType.WINDMILL,
                        PartType.STEAM_BOILER, PartType.STEAM_ENGINE}),
        new Chapter("Working Machines",
                "Grind, press, stir, blow, crush and cut.",
                "Tip: the Millstone and the Crusher double ore yield; the Steam Hammer folds ingots into plates.",
                new PartType[]{PartType.MILLSTONE, PartType.STEAM_HAMMER, PartType.KETTLE, PartType.MIXER,
                        PartType.BELLOWS_FAN, PartType.CRUSHING_WHEEL, PartType.DRILL, PartType.SAW}),
        new Chapter("Moving Items",
                "From a chest to a machine, without a single hopper.",
                "Tip: a funnel eats whatever falls on its lid; point an ejector at a depot to throw the parcel onward.",
                new PartType[]{PartType.BELT, PartType.CHUTE, PartType.FUNNEL, PartType.DEPOT,
                        PartType.EJECTOR, PartType.ARM}),
        new Chapter("Fluids & Oil",
                "Pumpjacks drink from the oil fields under the grass.",
                "Tip: chemical waste pollutes the chunk it stands in - neutralise it in a heated kettle, or sell the worry to a tank.",
                new PartType[]{PartType.PIPE, PartType.TANK, PartType.PUMP, PartType.VALVE,
                        PartType.PUMPJACK, PartType.REFINERY}),
        new Chapter("Rails & Trains",
                "Steam rails follow the route you drive; trains do the rest.",
                "Tip: rename a Station in an anvil to name it. Click a locomotive with paper to pick its stops - the autopilot then runs that line back and forth.",
                new PartType[]{PartType.STATION, PartType.SIGNAL}),
        new Chapter("Reading the Machines",
                "Every network has a pulse - here is how to feel it.",
                "Tip: right-click any part to read speed and load. The Steam Wrench, dowsing rod and punch cards are Tools - look them up in Zukan too.",
                new PartType[]{PartType.SPEED_GAUGE, PartType.STRESS_GAUGE, PartType.WHISTLE}),
    };

    Manual(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "manual");
        this.progress = new NamespacedKey(plugin, "manual_progress");
    }

    // ---------------------------------------------------------------- giving & opening

    boolean isManual(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return false;
        return "manual".equals(item.getItemMeta().getPersistentDataContainer().get(marker, PersistentDataType.STRING));
    }

    /** A fresh copy for this reader, with their own progress in it. */
    ItemStack create(Player p) {
        WrittenBookContent.Builder b = WrittenBookContent.writtenBookContent("Engineer's Manual", "The Steam Guild")
                .generation(0).resolved(true);
        String seen = progress(p);

        // page numbers: 1 cover, 2 contents, 3 quick start, 4.. chapters, then materials, then progress
        int firstChapter = 4;
        b.addPage(Component.text()
                .append(Component.text("Engineer's Manual\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .append(Component.text("A living guide to gears, steam, rails and oil.\n\n", NamedTextColor.GRAY))
                .append(Component.text("The dots update as you build.", NamedTextColor.DARK_AQUA))
                .append(Component.text("\n\n\n\n\n")
                        .append(Component.text("[ Open the contents ]", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.changePage(2)))));

        Component contents = Component.text("Contents\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
        int page = firstChapter;
        for (Chapter c : CHAPTERS) {
            contents = contents.append(Component.text(c.title + "\n", NamedTextColor.BLACK)
                            .clickEvent(ClickEvent.changePage(page))
                            .hoverEvent(HoverEvent.showText(Component.text("Go to page " + page, NamedTextColor.GRAY))))
                    .append(countLine(seen, c));
            page++;
        }
        int materialsPage = page, progressPage = page + 1;
        contents = contents.append(Component.text("\nBuilding Materials\n", NamedTextColor.BLACK)
                        .clickEvent(ClickEvent.changePage(materialsPage)))
                .append(Component.text("Your Progress\n", NamedTextColor.BLACK)
                        .clickEvent(ClickEvent.changePage(progressPage)))
                .append(Component.text("\nO done  - still to do\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("(place · see it spin · craft)", NamedTextColor.GRAY));
        b.addPage(contents);

        b.addPage(Component.text("Quick Start\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .append(Component.text("1. Mine andesite + iron nuggets -> ", NamedTextColor.GRAY)
                        .append(recipeLink("Andesite Alloy")))
                .append(Component.text(" alloy. Alloy + a stripped log -> a Casing.\n", NamedTextColor.GRAY))
                .append(Component.text("2. Cogs and shafts spin side by side; a Gearbox turns the corner.\n", NamedTextColor.GRAY))
                .append(Component.text("3. Place a Hand Crank, hold right-click - feel the world turn.\n", NamedTextColor.GRAY))
                .append(Component.text("4. Millstone + wheat = flour; kettle + fire + mixer = bread.\n", NamedTextColor.GRAY))
                .append(Component.text("5. Then go big: water wheels, windmills, a steam engine. It scales.\n\n", NamedTextColor.GRAY))
                .append(Component.text("[ Contents ]", NamedTextColor.BLUE).clickEvent(ClickEvent.changePage(2))));

        for (int i = 0; i < CHAPTERS.length; i++) {
            b.addPage(chapterPage(i, seen, firstChapter));
        }

        b.addPage(Component.text("Building Materials\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .append(mat("Andesite Alloy", "andesite alloy", "andesite + nuggets, workbench"))
                .append(mat("Andesite Casing", "andesite casing", "alloy + stripped log"))
                .append(mat("Brass Casing", "brass casing", "brass ingot + stripped log"))
                .append(mat("Zinc Nugget", "zinc nugget", "unzinc a zinc ingot"))
                .append(mat("Iron / Copper / Golden / Brass Plate", "iron plate", "press an ingot in a Steam Hammer"))
                .append(mat("Brass Ingot", "brass ingot", "mixer: copper + zinc, heated"))
                .append(mat("Whisk", "whisk", "alloy + iron plates"))
                .append(mat("Propeller", "propeller", "alloy + iron plates"))
                .append(mat("Sulfur", "sulfur", "mine the yellow ore"))
                .append(mat("Flour", "flour", "Millstone, wheat or rice"))
                .append(mat("Canister", "canister", "tin plates, workbench"))
                .append(Component.text("\nRaw zinc, tin and sulfur hide deep underground - a dowsing rod points at oil.", NamedTextColor.DARK_GRAY))
                .append(Component.text("\n\n[ Contents ]", NamedTextColor.BLUE).clickEvent(ClickEvent.changePage(2))));

        b.addPage(progressPage(seen, firstChapter, progressPage));

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, b.build());
        ItemMeta meta = book.getItemMeta();
        meta.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "manual");
        book.setItemMeta(meta);
        plugin.items().showAs(book, "engineers_manual");
        return book;
    }

    /** Right-click the manual (in air or on anything): it re-reads itself up to date. Called from PartListener. */

    // ---------------------------------------------------------------- progress

    private String progress(Player p) {
        String s = p.getPersistentDataContainer().get(progress, PersistentDataType.STRING);
        return s == null ? "" : s;
    }

    private void mark(Player p, PartType t, char flag) {
        String cur = progress(p);
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String piece : cur.split(";")) {
            if (piece.isEmpty()) continue;
            int c = piece.indexOf(':');
            if (c > 0) m.put(piece.substring(0, c), piece.substring(c + 1));
        }
        String have = m.getOrDefault(t.id(), "");
        if (have.indexOf(flag) >= 0) return;
        m.put(t.id(), have + flag);
        StringBuilder sb = new StringBuilder();
        for (var en : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(en.getKey()).append(':').append(en.getValue());
        }
        p.getPersistentDataContainer().set(progress, PersistentDataType.STRING, sb.toString());
    }

    /** Called when a player places a part (PartListener.place). */
    void markPlaced(Player p, PartType t) {
        mark(p, t, 'P');
    }

    /** Called when a player reads a part that is actually turning (SteamworksPlugin.showInfo). */
    void markSpinning(Player p, PartType t) {
        mark(p, t, 'S');
    }

    /** Called when a player crafts a part item. */
    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        PartType t = plugin.items().typeOf(e.getInventory().getResult());
        if (t != null) mark(p, t, 'C');
    }

    // ---------------------------------------------------------------- page text

    private static boolean has(String seen, PartType t, char flag) {
        for (String piece : seen.split(";")) {
            int c = piece.indexOf(':');
            if (c > 0 && piece.substring(0, c).equals(t.id()) && piece.substring(c + 1).indexOf(flag) >= 0) return true;
        }
        return false;
    }

    private static Component dots(String seen, PartType t) {
        Component out = Component.text(" ", NamedTextColor.WHITE);
        for (char f : new char[]{'P', 'S', 'C'}) {
            out = out.append(has(seen, t, f)
                    ? Component.text("O", NamedTextColor.GREEN)
                    : Component.text("-", NamedTextColor.GRAY));
        }
        return out;
    }

    private static Component recipeLink(String display) {
        return Component.text("[" + display + "]", NamedTextColor.BLUE)
                .clickEvent(ClickEvent.runCommand("/zukan " + display))
                .hoverEvent(HoverEvent.showText(Component.text("Open this recipe in Zukan", NamedTextColor.GRAY)));
    }

    private Component countLine(String seen, Chapter c) {
        int done = 0;
        for (PartType t : c.parts) {
            if (has(seen, t, 'P') && has(seen, t, 'S') && has(seen, t, 'C')) done++;
        }
        return Component.text("   " + done + "/" + c.parts.length + " mastered\n", NamedTextColor.DARK_GRAY);
    }

    private Component chapterPage(int i, String seen, int firstChapter) {
        Chapter c = CHAPTERS[i];
        Component page = Component.text(c.title + "\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .append(Component.text(c.intro + "\n\n", NamedTextColor.GRAY));
        for (PartType t : c.parts) {
            page = page.append(Component.text(t.displayName + " ", NamedTextColor.BLACK))
                    .append(dots(seen, t))
                    .append(Component.text("  ", NamedTextColor.WHITE))
                    .append(Component.text("[recipe]", NamedTextColor.BLUE)
                            .clickEvent(ClickEvent.runCommand("/zukan " + t.displayName))
                            .hoverEvent(HoverEvent.showText(Component.text("Open this recipe in Zukan", NamedTextColor.GRAY))))
                    .append(Component.text("\n", NamedTextColor.WHITE));
        }
        page = page.append(Component.text("\n" + c.tip, NamedTextColor.DARK_AQUA))
                .append(Component.text("\n\n[ Contents ]", NamedTextColor.BLUE).clickEvent(ClickEvent.changePage(2)));
        return page;
    }

    private Component mat(String display, String id, String how) {
        return Component.text(display, NamedTextColor.BLACK)
                .append(Component.text(" - " + how + "\n", NamedTextColor.GRAY));
    }

    private Component progressPage(String seen, int firstChapter, int thisPage) {
        int all = 0, done = 0;
        for (Chapter c : CHAPTERS) {
            for (PartType t : c.parts) {
                all++;
                if (has(seen, t, 'P')) done++;
            }
        }
        return Component.text("Your Progress\n\n", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .append(Component.text("Parts placed: " + done + " of " + all + ".\n\n", NamedTextColor.BLACK))
                .append(Component.text("The dots mean:\n", NamedTextColor.GRAY))
                .append(Component.text("O ", NamedTextColor.GREEN)).append(Component.text("placed it yourself\n", NamedTextColor.GRAY))
                .append(Component.text("O ", NamedTextColor.GREEN)).append(Component.text("saw it spin (right-click a turning part)\n", NamedTextColor.GRAY))
                .append(Component.text("O ", NamedTextColor.GREEN)).append(Component.text("crafted it\n\n", NamedTextColor.GRAY))
                .append(Component.text("Complete a chapter and the guild salutes you. In-game.", NamedTextColor.DARK_AQUA))
                .append(Component.text("\n\n[ Contents ]", NamedTextColor.BLUE).clickEvent(ClickEvent.changePage(2)));
    }
}
