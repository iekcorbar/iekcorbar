package com.iekcorbar.afkcinema;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ruimtelijk bedieningspaneel in steampunk-stijl — het server-side antwoord op
 * de spatial-gui client-MOD: zwevende messing plaatjes (TextDisplay) met
 * onzichtbare klik-gebieden (Interaction) vóór de speler. Werkt op elke
 * vanilla client, zonder MOD. Alle teksten Engels.
 *
 * Het paneel is een persoonlijke afstandsbediening: klikken bedient het paneel
 * van de EIGENAR (ieder mag aan iemands kraan draaien, net als in de machinekamer).
 */
final class SpatialMenu implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Color KETEL = Color.fromARGB(190, 26, 16, 8);   // donker messing op stoomglas
    private static final double RIJHOOGTE = 0.33;

    private record Rijdef(String tekst, Consumer<Player> actie) { }
    private record Knop(Menu menu, Consumer<Player> actie) { }

    private final AfkCinemaPlugin plugin;
    private final Map<UUID, Menu> menus = new HashMap<>();
    private final Map<UUID, Knop> knoppen = new HashMap<>();
    private int verteller;

    SpatialMenu(AfkCinemaPlugin plugin) { this.plugin = plugin; }

    /** Eén zwevend paneel: anker + rijen. */
    private static final class Menu {
        final UUID eigenaar;
        final Location anker;
        final Vector richting;                      // paneel → speler (voor de klik-gebieden)
        final List<TextDisplay> regels = new ArrayList<>();
        final List<Interaction> klikkers = new ArrayList<>();
        long laatsteKlikMs;

        Menu(UUID eigenaar, Location anker, Vector richting) {
            this.eigenaar = eigenaar; this.anker = anker; this.richting = richting;
        }
    }

    // ---------- publiek ----------

    void toggle(Player p) {
        if (menus.containsKey(p.getUniqueId())) {
            verwijder(p.getUniqueId());
            p.sendMessage("§8[§6Cinema§8] §7steam panel cleared");
            return;
        }
        double afstand = Math.min(8.0, Math.max(2.0, plugin.getConfig().getDouble("cine.menu-distance", 4.0)));
        Location oog = p.getEyeLocation();
        Vector vooruit = oog.getDirection().clone().normalize();
        Location anker = oog.clone().add(vooruit.clone().multiply(afstand));
        Vector terug = vooruit.clone().multiply(-1);   // wijst van paneel terug naar de speler
        Menu m = new Menu(p.getUniqueId(), anker, terug);
        if (!bouw(m, p)) { p.sendMessage("§cthe panel doesn't fit here — step back and try again"); return; }
        menus.put(p.getUniqueId(), m);
        p.playSound(anker, Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        p.sendMessage("§8[§6Cinema§8] §7steam panel placed — §eright-click§7 the brass buttons "
            + "(not while a film is rolling: then you're a spectator and can't click)");
    }

    /** Elke 2 s: paneelteksten verversen (status, automaat-stand, nieuwe pads). */
    void tick() {
        if (++verteller % 40 != 0) return;
        for (UUID id : new ArrayList<>(menus.keySet())) {
            Player eigenaar = Bukkit.getPlayer(id);
            if (eigenaar == null) { verwijder(id); continue; }
            vernieuw(eigenaar);
        }
    }

    void verwijder(UUID spelerId) {
        Menu m = menus.remove(spelerId);
        if (m != null) wis(m);
    }

    void clearAlles() {
        for (UUID id : new ArrayList<>(menus.keySet())) verwijder(id);
        menus.clear();
        knoppen.clear();
    }

    // ---------- knop-afhandeling ----------

    @EventHandler
    void opKnop(PlayerInteractAtEntityEvent e) {
        Knop k = knoppen.get(e.getRightClicked().getUniqueId());
        if (k == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player klikt = e.getPlayer();
        Player eigenaar = Bukkit.getPlayer(k.menu.eigenaar);
        if (eigenaar == null) return;
        long nu = System.currentTimeMillis();
        if (nu - k.menu.laatsteKlikMs < 150) return;
        k.menu.laatsteKlikMs = nu;
        klikt.playSound(k.menu.anker, Sound.BLOCK_LEVER_CLICK, 0.9f, k.actie == null ? 1.5f : 1.0f);
        if (k.actie != null) k.actie.accept(eigenaar);
    }

    // ---------- paneel-inhoud (steampunk-kleurpalet) ----------

    private List<Rijdef> rijenVoor(Player p) {
        List<Rijdef> r = new ArrayList<>();
        CineSession s = plugin.manager().sessie(p);
        r.add(new Rijdef("§6⚙ C I N E M A ⚙", null));
        if (s.filmLoopt) {
            r.add(new Rijdef("§c▮ film rolling", null));
            r.add(new Rijdef("§e■ STOP", q -> plugin.manager().stopAlles(plugin.manager().sessie(q))));
        } else if (s.modus == CineSession.Modus.OPNAME) {
            String pad = s.opneemPad == null ? "?" : s.opneemPad.naam();
            r.add(new Rijdef("§e✎ recording '" + pad + "'", null));
            r.add(new Rijdef("§e■ END RECORDING", q -> plugin.manager().stopAlles(plugin.manager().sessie(q))));
        } else {
            r.add(new Rijdef("§7○ boiler idle", null));
            r.add(new Rijdef("§a▶ START FILM", q -> {
                CineSession t = plugin.manager().sessie(q);
                if (!t.actiefFilmpje()) plugin.manager().startFilm(q, q);
            }));
        }
        r.add(new Rijdef("§7⚙ afk-auto: " + (s.cineToegelaten ? "§aON" : "§8OFF"),
            q -> { CineSession t = plugin.manager().sessie(q); t.cineToegelaten = !t.cineToegelaten; }));
        List<String> pads = plugin.pads().namen();
        for (int i = 0; i < Math.min(6, pads.size()); i++) {
            String naam = pads.get(i);
            r.add(new Rijdef("§6▶ §f" + naam, q -> {
                Pad pad = plugin.pads().pad(naam);
                if (pad != null && pad.punten().size() >= 2) plugin.manager().startPad(q, pad);
            }));
        }
        if (pads.isEmpty()) r.add(new Rijdef("§8no paths yet — /cine record <name>", null));
        r.add(new Rijdef("§8╙─────────────╜", null));
        return r;
    }

    // ---------- bouw/vernieuw ----------

    private void vernieuw(Player eigenaar) {
        Menu m = menus.get(eigenaar.getUniqueId());
        if (m == null) return;
        try {
            List<Rijdef> nye = rijenVoor(eigenaar);
            if (nye.size() != m.regels.size()) {         // aantal rijen veranderd: compleet herbouwen
                wis(m);
                if (!bouw(m, eigenaar)) menus.remove(eigenaar.getUniqueId());
                return;
            }
            for (int i = 0; i < nye.size(); i++) {
                m.regels.get(i).text(LEGACY.deserialize(nye.get(i).tekst()));
                knoppen.put(m.klikkers.get(i).getUniqueId(), new Knop(m, nye.get(i).actie()));
            }
        } catch (Throwable t) {                       // chunk weggeunload tijdens refresh
            verwijder(eigenaar.getUniqueId());
        }
    }

    private boolean bouw(Menu m, Player eigenaar) {
        try {
            List<Rijdef> rijen = rijenVoor(eigenaar);
            int n = rijen.size();
            double top = m.anker.getY() + (n - 1) * RIJHOOGTE / 2.0;
            for (int i = 0; i < n; i++) {
                Location plek = m.anker.clone();
                plek.setY(top - i * RIJHOOGTE);

                TextDisplay d = (TextDisplay) m.anker.getWorld().spawnEntity(plek, EntityType.TEXT_DISPLAY);
                d.text(LEGACY.deserialize(rijen.get(i).tekst()));
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBackgroundColor(KETEL);
                d.setSeeThrough(false);
                m.regels.add(d);

                // klik-gebied net vóór de plaat: wint de raycast van de TextDisplay-zelf
                Location klikPlek = plek.clone().add(m.richting.clone().multiply(0.30));
                Interaction it = (Interaction) m.anker.getWorld().spawnEntity(klikPlek, EntityType.INTERACTION);
                it.setInteractionWidth(2.6f);
                it.setInteractionHeight(0.30f);
                it.setResponsive(true);
                m.klikkers.add(it);
                knoppen.put(it.getUniqueId(), new Knop(m, rijen.get(i).actie()));
            }
            return true;
        } catch (Throwable t) {
            wis(m);
            plugin.getLogger().warning("steam panel build failed: " + t);
            return false;
        }
    }

    private void wis(Menu m) {
        List<Entity> alles = new ArrayList<>();
        alles.addAll(m.regels);
        alles.addAll(m.klikkers);
        for (Entity e : alles) {
            knoppen.remove(e.getUniqueId());
            try { e.remove(); } catch (Throwable ignored) { }
        }
        m.regels.clear();
        m.klikkers.clear();
    }
}
