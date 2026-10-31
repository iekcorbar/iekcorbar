package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Scherm in de wereld" — de server-side zuster van spatial-gui's third-person
 * mode en van de Cinematic mod: opent iemand een scherm (ook de Custom GUI's van
 * Skript/CMI/onze plugins, die de mod níet kent), dan verschijnt boven z'n hoofd
 * een voor IEDERAZIEN zichtbaar drijvend raam: de volle inhoud als ItemDisplays
 * + titelbalk - en klikbaar: een klik op een vakje wordt doorgestuurd naar die
 * echte slot via een synthetic InventoryClickEvent, dus elke menu-logica werkt door.
 * Werkt op elke vanilla client, want display-entities zijn vanilla.
 *
 * Klik-regels: alleen de EIGENAR bedient zijn scherm; links = klik, sluip-klik =
 * shift-klik. Sleep-handvatten (double chest onder-)erven we bewust: daarvoor is
 * het echte 2D-scherm nog gewoon open.
 *
 * Twee-vingercrafts (2x2, geen container-event) krijgen een kort craft-flitsje.
 */
final class ScreenPanels implements Listener {

    private static final int MAX_VAKKEN = 28;

    private final AfkCinemaPlugin plugin;
    private final Map<UUID, Paneel> panels = new HashMap<>();
    private record Klik(UUID eigenaar, int rawSlot) { }
    private final Map<UUID, Klik> klikmap = new HashMap<>();
    private final Map<UUID, Long> flitsCooldown = new HashMap<>();
    private int teller;

    ScreenPanels(AfkCinemaPlugin plugin) { this.plugin = plugin; }

    private static final class Paneel {
        final UUID eigenaar;
        final InventoryView view;
        final TextDisplay titel;
        final List<ItemDisplay> vakken = new ArrayList<>();
        final List<Interaction> klikkers = new ArrayList<>();
        final List<Integer> indexes = new ArrayList<>();
        final List<ItemStack> laatst = new ArrayList<>();
        Location anker;

        Paneel(UUID eigenaar, InventoryView view, TextDisplay titel, Location anker) {
            this.eigenaar = eigenaar; this.view = view; this.titel = titel; this.anker = anker;
        }
    }

    // ---------- publiek ----------

    /** /cine screen: laat mij mijn geopende schermen in de wereld zien (of niet). */
    void toggle(Player p) {
        CineSession s = plugin.manager().sessie(p);
        s.schermZichtbaar = !s.schermZichtbaar;
        if (!s.schermZichtbaar) verwijder(p.getUniqueId());
        p.sendMessage("§8[§6Cinema§8] §7floating screen " + (s.schermZichtbaar ? "on" : "off")
            + " — others " + (s.schermZichtbaar ? "can" : "cannot") + " see your open menus"
            + (s.schermZichtbaar ? " §8· §eclick§8: left = use slot, sneak-click = shift-click" : ""));
    }

    void verwijder(UUID spelerId) {
        Paneel m = panels.remove(spelerId);
        if (m == null) return;
        for (ItemDisplay it : m.vakken) { try { it.remove(); } catch (Throwable ignored) { } }
        for (Interaction it : m.klikkers) { klikmap.remove(it.getUniqueId()); try { it.remove(); } catch (Throwable ignored) { } }
        try { m.titel.remove(); } catch (Throwable ignored) { }
    }

    void clearAlles() {
        for (UUID id : new ArrayList<>(panels.keySet())) verwijder(id);
        panels.clear();
        klikmap.clear();
    }

    /** Elke 2 ticks: ramen volgen + inhoud ververst. */
    void tick() {
        if (++teller % 2 != 0) return;
        for (UUID id : new ArrayList<>(panels.keySet())) {
            Paneel pn = panels.get(id);
            if (pn == null) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()
                    || !pn.view.getTopInventory().getViewers().stream()
                            .anyMatch(h -> h.getUniqueId().equals(id))
                    || p.getWorld() != pn.anker.getWorld()) { verwijder(id); continue; }
            follow(p, pn);
            refresh(p, pn);
        }
    }

    // ---------- events ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        verwijder(p.getUniqueId());                        // heropenen van ander scherm
        if (!plugin.manager().sessie(p).schermZichtbaar) return;
        String lbl = label(e.getView().getType());
        if (lbl == null) return;
        Paneel pn = maak(p, e.getView(), lbl);
        if (pn != null) panels.put(p.getUniqueId(), pn);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opSluit(InventoryCloseEvent e) {
        verwijder(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void opQuit(PlayerQuitEvent e) {
        verwijder(e.getPlayer().getUniqueId());
        flitsCooldown.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void opJoin(PlayerJoinEvent e) {
        plugin.manager().sessie(e.getPlayer()).schermZichtbaar =
                plugin.getConfig().getBoolean("cine.screen-default", true);
    }

    /** Klik op een zwevend vakje = diezelfde slot klikken in het echte scherm. */
    @EventHandler(ignoreCancelled = true)
    void opKlik(PlayerInteractAtEntityEvent e) {
        Klik k = klikmap.get(e.getRightClicked().getUniqueId());
        if (k == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(k.eigenaar)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.4f, 0.7f);
            return;                                        // alleen de eigenaar bedient zijn scherm
        }
        Paneel pn = panels.get(p.getUniqueId());
        if (pn == null) return;
        Inventory top = pn.view.getTopInventory();
        if (k.rawSlot < 0 || k.rawSlot >= top.getSize()) return;
        ClickType type = p.isSneaking() ? ClickType.SHIFT_LEFT : ClickType.LEFT;
        try {
            InventoryClickEvent ev = new InventoryClickEvent(pn.view, InventoryType.SlotType.CONTAINER,
                    k.rawSlot, type, p.isSneaking() ? InventoryAction.MOVE_TO_OTHER_INVENTORY : InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(ev);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, ev.isCancelled() ? 1.4f : 1.0f);
        } catch (Throwable t) {
            plugin.getLogger().warning("screen click forwarding failed: " + t);
        }
    }

    /** 2x2 craftje (handcraft): kort drijvend resultaat boven het hoofd. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getType() != InventoryType.CRAFTING) return;   // alleen de 2x2
        if (!plugin.manager().sessie(p).schermZichtbaar) return;
        if (panels.containsKey(p.getUniqueId())) return;                    // groot raam dekt dit al
        long now = System.currentTimeMillis();
        Long last = flitsCooldown.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        flitsCooldown.put(p.getUniqueId(), now);
        ItemStack res = e.getInventory().getResult();
        if (res == null || res.getType().isAir()) return;
        flits(p, res);
    }

    // ---------- opbouw ----------

    private Paneel maak(Player p, InventoryView view, String lbl) {
        try {
            Location anker = anker(p);
            TextDisplay titel = (TextDisplay) anker.getWorld().spawnEntity(
                    anker.clone().add(0, 1.15, 0), EntityType.TEXT_DISPLAY);
            titel.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§6" + lbl + " §8· §f" + p.getName()));
            titel.setBillboard(Display.Billboard.CENTER);
            titel.setAlignment(TextDisplay.TextAlignment.CENTER);
            titel.setBackgroundColor(org.bukkit.Color.fromARGB(178, 26, 18, 10));
            titel.setPersistent(false);
            Paneel pn = new Paneel(p.getUniqueId(), view, titel, anker);
            bouwVakken(pn, anker);
            return pn;
        } catch (Throwable t) {
            return null;
        }
    }

    private void bouwVakken(Paneel pn, Location anker) {
        for (ItemDisplay it : pn.vakken) { try { it.remove(); } catch (Throwable ignored) { } }
        for (Interaction it : pn.klikkers) { klikmap.remove(it.getUniqueId()); try { it.remove(); } catch (Throwable ignored) { } }
        pn.vakken.clear(); pn.klikkers.clear(); pn.indexes.clear();
        boolean klikbaar = plugin.getConfig().getBoolean("cine.screen-clicks", true);
        Inventory top = pn.view.getTopInventory();
        List<ItemStack> huidig = new ArrayList<>();
        List<Integer> indexen = new ArrayList<>();
        for (int i = 0; i < top.getSize() && huidig.size() < MAX_VAKKEN; i++) {
            ItemStack s = top.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            huidig.add(s);
            indexen.add(i);
        }
        pn.laatst.clear(); pn.laatst.addAll(huidig);
        pn.indexes.addAll(indexen);
        int n = huidig.size();
        Vector naarEigenaar = anker.getDirection().setY(0);
        if (naarEigenaar.lengthSquared() < 1e-6) naarEigenaar = new Vector(0, 0, -1);
        naarEigenaar.normalize().multiply(-1);            // kijkt van het paneel terug naar het hoofd
        for (int i = 0; i < n; i++) {
            Location plek = raster(anker, i, n);
            try {
                ItemDisplay d = (ItemDisplay) anker.getWorld().spawnEntity(plek, EntityType.ITEM_DISPLAY);
                ItemStack kopie = huidig.get(i).clone();
                kopie.setAmount(1);
                d.setItemStack(kopie);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                pn.vakken.add(d);
                if (klikbaar) {
                    Interaction it = (Interaction) anker.getWorld().spawnEntity(
                            plek.clone().add(naarEigenaar.clone().multiply(0.16)), EntityType.INTERACTION);
                    it.setInteractionWidth(0.30f);
                    it.setInteractionHeight(0.30f);
                    it.setResponsive(true);
                    it.setPersistent(false);
                    pn.klikkers.add(it);
                    klikmap.put(it.getUniqueId(), new Klik(pn.eigenaar, indexen.get(i)));
                }
            } catch (Throwable ignored) { }
        }
    }

    /** Raster van ronden in een boog voor het gezicht van de speler. */
    private Location raster(Location anker, int i, int n) {
        int cols = Math.min(5, Math.max(1, n));
        int col = i % cols;
        int row = i / cols;
        double breedte = (cols - 1) * 0.30;
        Vector vooruit = anker.getDirection().setY(0).normalize();
        Vector zijde = new Vector(-vooruit.getZ(), 0, vooruit.getX());
        return anker.clone()
                .add(0, 0.85 - row * 0.30, 0)
                .add(zijde.clone().multiply((col * 0.30) - breedte / 2.0))
                .add(vooruit.clone().multiply(0.55));
    }

    private void follow(Player p, Paneel pn) {
        Location nieuw = anker(p);
        if (nieuw.distanceSquared(pn.anker) < 0.09) return;         // <30 cm: laten staan
        pn.anker = nieuw;
        try { pn.titel.teleport(nieuw.clone().add(0, 1.15, 0)); } catch (Throwable ignored) { }
        Vector naarEigenaar = nieuw.getDirection().setY(0);
        if (naarEigenaar.lengthSquared() < 1e-6) naarEigenaar = new Vector(0, 0, -1);
        naarEigenaar.normalize().multiply(-0.16);
        for (int i = 0; i < pn.vakken.size(); i++) {
            Location plek = raster(nieuw, i, pn.vakken.size());
            try { pn.vakken.get(i).teleport(plek); } catch (Throwable ignored) { }
            if (i < pn.klikkers.size())
                try { pn.klikkers.get(i).teleport(plek.clone().add(naarEigenaar)); } catch (Throwable ignored) { }
        }
    }

    private void refresh(Player p, Paneel pn) {
        Inventory top = pn.view.getTopInventory();
        List<ItemStack> huidig = new ArrayList<>();
        for (int i = 0; i < top.getSize() && huidig.size() < MAX_VAKKEN; i++) {
            ItemStack s = top.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            huidig.add(s);
        }
        if (huidig.size() == pn.laatst.size()) {
            boolean zelfde = true;
            for (int i = 0; i < huidig.size(); i++) {
                ItemStack a = huidig.get(i), b = pn.laatst.get(i);
                if (!a.isSimilar(b)) { zelfde = false; break; }
            }
            if (zelfde) return;
        }
        bouwVakken(pn, pn.anker);
    }

    private Location anker(Player p) {
        Location oog = p.getEyeLocation();
        return new Location(oog.getWorld(), oog.getX(), oog.getY(), oog.getZ(), oog.getYaw(), 0f);
    }

    private void flits(Player p, ItemStack resultaat) {
        Location top = anker(p).add(0, 1.0, 0);
        try {
            ItemDisplay d = (ItemDisplay) top.getWorld().spawnEntity(top, EntityType.ITEM_DISPLAY);
            ItemStack kopie = resultaat.clone();
            int n = kopie.getAmount(); kopie.setAmount(1);
            d.setItemStack(kopie);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            TextDisplay t = (TextDisplay) top.getWorld().spawnEntity(
                    top.clone().add(0, -0.4, 0), EntityType.TEXT_DISPLAY);
            t.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§acrafted §f" + (n > 1 ? "×" + n + " " : "")
                            + resultaat.getType().name().toLowerCase().replace('_', ' ')));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try { d.remove(); t.remove(); } catch (Throwable ignored) { }
            }, 50L);
        } catch (Throwable ignored) { }
    }

    private static String label(InventoryType t) {
        switch (t) {
            case WORKBENCH:      return "⚒ Crafting";
            case CHEST:          return "▣ Chest";
            case BARREL:         return "▣ Barrel";
            case ENDER_CHEST:    return "▣ Ender Chest";
            case FURNACE:        return "♨ Furnace";
            case BLAST_FURNACE:  return "♨ Blast Furnace";
            case SMOKER:         return "♨ Smoker";
            case ANVIL:          return "⚒ Anvil";
            case SMITHING:       return "⚒ Smithing";
            case GRINDSTONE:     return "⚒ Grindstone";
            case LOOM:           return "⚒ Loom";
            case STONECUTTER:    return "⚒ Stonecutter";
            case CARTOGRAPHY:    return "⚒ Cartography";
            case SHULKER_BOX:    return "▣ Shulker Box";
            case BREWING:        return "⚗ Brewing";
            case MERCHANT:         return "⛃ Trading";
            case HOPPER:         return "▾ Hopper";
            case DROPPER:        return "▾ Dropper";
            case DISPENSER:      return "▾ Dispenser";
            default:             return null;
        }
    }
}
