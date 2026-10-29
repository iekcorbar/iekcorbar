package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

/** AFK-detectie + de bioscoop-draaibaan. Server-only; werkt op elke vanilla client. */
final class AfkCinemaListener implements Listener {

    private final AfkCinemaPlugin plugin;
    private final Map<java.util.UUID, CineSession> sessies = new HashMap<>();

    AfkCinemaListener(AfkCinemaPlugin plugin) {
        this.plugin = plugin;
    }

    CineSession sessie(Player p) {
        return sessies.computeIfAbsent(p.getUniqueId(), id -> new CineSession(p));
    }

    int afkSeconden() { return Math.max(10, plugin.getConfig().getInt("afk-seconds", 180)); }

    // ---------- actie-registratie ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opInteractie(PlayerInteractEvent e) { actief(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opCommando(PlayerCommandPreprocessEvent e) { actief(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    void opSchade(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            // schade is GEEN input: niet activeer, maar wel uit de bioscoop —
            // vechtende "AFK-ers" terughalen is belangrijker dan mooie shots
            CineSession s = sessies.get(p.getUniqueId());
            if (s != null && s.filmLoopt) stopFilm(s, true);
        }
    }

    void actief(Player p) {
        CineSession s = sessie(p);
        boolean wasAfk = s.afk;
        s.markeerActief();
        if (s.filmLoopt) stopFilm(s, true);
        if (wasAfk && plugin.broadcastAfk()) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.empty(),
                net.kyori.adventure.text.Component.text("welkom terug"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(300))));
        }
    }

    // ---------- verplaatsing: echte input vs onze teleport ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opBeweging(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        CineSession s = sessies.get(p.getUniqueId());
        if (s == null) { return; }
        Location van = e.getFrom(), naar = e.getTo();
        if (naar == null) return;
        double dx = naar.getX() - van.getX(), dy = naar.getY() - van.getY(), dz = naar.getZ() - van.getZ();
        double ver = dx * dx + dy * dy + dz * dz;
        if (ver > 0.0004) {                 // 2 cm — muis-kijken (alleen yaw) telt niet
            actief(p);
        }
    }

    @EventHandler
    void opJoin(PlayerJoinEvent e) { sessies.remove(e.getPlayer().getUniqueId()); }

    @EventHandler
    void opQuit(PlayerQuitEvent e) {
        CineSession s = sessies.remove(e.getPlayer().getUniqueId());
        if (s != null && s.filmLoopt) { /* speler is weg; gamemode is opgeslagen in de DB */ }
    }

    // ---------- de tick-kant ----------

    void tick() {
        for (CineSession s : new java.util.ArrayList<>(sessies.values())) {
            Player p = s.speler;
            if (!p.isOnline()) { sessies.remove(p.getUniqueId()); continue; }
            if (!s.filmLoopt) {
                if (!s.afk && !s.hasMovedRecent(afkSeconden()) && s.cinemaToegelaten && !p.isDead()) {
                    s.afk = true;
                    startFilm(p, s);
                }
            } else {
                stap(s);
            }
        }
    }

    private void startFilm(Player p, CineSession s) {
        s.vorigeLoc = p.getLocation().clone();
        s.vorigeGameMode = p.getGameMode();
        double head = plugin.getConfig().getDouble("cine.head-offset", 1.4);
        s.focusPunt = p.getLocation().add(0, head, 0);
        s.hoek = Math.random() * Math.PI * 2;
        s.liftTeller = 0;
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage("§8[§ecinema§8] §7je camera draait — beweeg om terug te keren");
        s.filmLoopt = true;
    }

    private void stap(CineSession s) {
        Player p = s.speler;
        World w = s.focusPunt.getWorld();
        // langzame baan: ~6 seconden per graad... rustiger: 120 radialen per minuut
        s.hoek += 0.0035;
        double straal = 5.0 + Math.sin(s.hoek * 0.7) * 1.6;            // ademende baan
        double hoogte = 1.6 + Math.sin(s.hoek * 0.35) * 1.9;           // langzale crane
        double x = s.focusPunt.getX() + Math.cos(s.hoek) * straal;
        double z = s.focusPunt.getZ() + Math.sin(s.hoek) * straal;
        double y = s.focusPunt.getY() + hoogte;

        if (plugin.collisionLift()) {
            // vlieg niet door muren: als het camera-punt in solide blok zit, tillen
            if (w.getBlockAt((int) x, (int) Math.floor(y), (int) z).isSolid()) {
                y += 0.35; s.liftTeller++;
                if (s.liftTeller > 60) { stopFilm(s, false); return; }  // te benauwd: uit
            } else {
                s.liftTeller = Math.max(0, s.liftTeller - 1);
            }
        }

        Location cam = new Location(w, x, y, z);
        Location doel = s.focusPunt.clone();
        org.bukkit.util.Vector richting = doel.toVector().subtract(cam.toVector());
        double horizontaal = Math.hypot(richting.getX(), richting.getZ());
        float yaw = (float) (Math.toDegrees(Math.atan2(richting.getZ(), richting.getX())) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(richting.getY(), horizontaal));
        cam.setYaw(yaw); cam.setPitch(pitch);
        p.teleport(cam);
    }

    private void stopFilm(CineSession s, boolean terugzetten) {
        if (!s.filmLoopt) return;
        s.filmLoopt = false;
        Player p = s.speler;
        if (terugzetten && p.isOnline()) {
            p.setGameMode(s.vorigeGameMode);
            p.teleport(s.vorigeLoc);
        }
    }

    // ---------- publieke API (voor SiteSync/website later) ----------

    boolean isAfk(Player p) {
        CineSession s = sessies.get(p.getUniqueId());
        return s != null && s.afk;
    }

    /** Handmatige start vanuit het commando. */
    void forceFilm(Player p) {
        CineSession s = sessie(p);
        if (!s.filmLoopt) startFilm(p, s);
    }

    /** Handmatig stoppen vanuit het commando (terugzetten zoals bij input). */
    void stopViaCommand(CineSession s) { stopFilm(s, true); }

    Map<java.util.UUID, CineSession> sessies() { return sessies; }
}
