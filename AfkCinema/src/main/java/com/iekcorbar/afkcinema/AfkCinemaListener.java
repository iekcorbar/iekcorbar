package com.iekcorbar.afkcinema;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
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
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AFK-detectie + de bioscoop. Server-only; werkt op elke vanilla client.
 * Alle speler-teksten zijn Engels (publieke server).
 *
 * ORBIT  : camera cirkelt om een stand-in (Citizens-NPC met jouw skin) —
 *          zo zie je je EIGEN personage in beeld, ook al zweef je als spectator.
 * DOELOF : camera cirkelt om een andere (gewoon zichtbare) speler.
 * PAD    : camera vliegt een opgenomen waypoint-route langs (Catmull-Rom).
 * OPNAME : vrij rondvliegen als spectator terwijl je /cine pad add doet.
 */
final class AfkCinemaListener implements Listener {

    private final AfkCinemaPlugin plugin;
    private final Map<java.util.UUID, CineSession> sessies = new HashMap<>();
    private final Map<java.util.UUID, Long> opgemerkt = new HashMap<>();  // "You're in the shot."-cooldown

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
            // schade is géén input, maar halen we wél uit de bioscoop:
            // vechtende "AFK-ers" terugvinden is belangrijker dan mooie shots
            CineSession s = sessies.get(p.getUniqueId());
            if (s != null && s.filmLoopt && s.modus != CineSession.Modus.OPNAME) stopAlles(s);
        }
    }

    void actief(Player p) {
        CineSession s = sessie(p);
        boolean wasAfk = s.afk;
        s.markeerActief();
        if (s.modus == CineSession.Modus.OPNAME) return;   // opnemer mag vrij vliegen
        if (s.filmLoopt) stopAlles(s);
        if (wasAfk && plugin.broadcastAfk()) {
            p.showTitle(net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.empty(),
                net.kyori.adventure.text.Component.text("welcome back"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(300))));
        }
    }

    // ---------- verplaatsing: echte input vs onszelf ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void opBeweging(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        CineSession s = sessies.get(p.getUniqueId());
        if (s == null) return;
        if (s.modus == CineSession.Modus.OPNAME) { s.markeerActief(); return; }
        if (!s.filmLoopt) return;
        Location naar = e.getTo();
        if (naar == null) return;
        // onszelf aan het teleporteren? cameraLoc is wat wij zojuist stuurden
        Location cam = s.cameraLoc;
        if (cam != null && naar.getWorld() == cam.getWorld()
                && naar.distanceSquared(cam) < 1.0e-6) return;
        stopAlles(s);                                       // echte input: film uit, terug naar plek
    }

    @EventHandler
    void opJoin(PlayerJoinEvent e) { sessies.remove(e.getPlayer().getUniqueId()); }

    @EventHandler
    void opQuit(PlayerQuitEvent e) {
        CineSession s = sessies.remove(e.getPlayer().getUniqueId());
        if (s != null) herstel(s);                          // Anders staat z'n data straks
        // in de lucht en in spectator — vóór het opslaan terugzetten.
        plugin.menus().verwijder(e.getPlayer().getUniqueId());
    }

    // ---------- starten / stoppen ----------

    /** Bioscoop starten: viewer = speler die meekijkt, onderwerp = wie eromheen draait. */
    void startFilm(Player viewer, Player onderwerp) {
        CineSession s = sessie(viewer);
        if (s.filmLoopt) return;
        s.vorigeLoc = viewer.getLocation().clone();
        s.vorigeGameMode = viewer.getGameMode();
        s.standYaw = s.vorigeLoc.getYaw();
        double head = plugin.getConfig().getDouble("cine.head-offset", 1.4);
        if (viewer.getUniqueId().equals(onderwerp.getUniqueId())) {
            s.modus = CineSession.Modus.ORBIT;
            s.standIn = maakStandIn(onderwerp, s.vorigeLoc);
            s.focusPunt = s.vorigeLoc.clone().add(0, head, 0);
        } else {
            s.modus = CineSession.Modus.DOELOF;
            s.doel = onderwerp;
            s.focusPunt = onderwerp.getLocation().add(0, head, 0);
        }
        s.hoek = Math.random() * Math.PI * 2;
        s.liftTeller = 0;
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.sendMessage("§8[§6Cinema§8] §7"
            + (s.standIn != null ? "the camera is orbiting your character"
                                 : "camera rolling — move to return"));
        s.filmLoopt = true;

        // grapjes: handmatige taken tellen; in slaap gefilmd = aftiteling
        if (!s.afk) {
            int dag = java.time.LocalDate.now().getDayOfYear();
            if (s.takeDay != dag) { s.takeDay = dag; s.takes = 0; }
            s.takes++;
            if (s.takes >= 7 && !plugin.adv().has(viewer, "directors_cut")) {
                viewer.sendActionBar(net.kyori.adventure.text.Component.text(
                        "That's a wrap... again.", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                plugin.adv().grant(viewer, "directors_cut");
            }
        }
        if (viewer.isSleeping() && !plugin.adv().has(viewer, "closing_credits")) {
            viewer.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Roll credits.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            plugin.adv().grant(viewer, "closing_credits");
        }
    }

    /** Camera-vlucht langs een opgenomen pad. */
    void startPad(Player viewer, Pad pad) {
        if (pad.punten().size() < 2) { viewer.sendMessage("§cthat path has fewer than 2 points"); return; }
        World w = pad.wereld();
        if (w == null) { viewer.sendMessage("§cthe world of this path no longer exists"); return; }
        CineSession s = sessie(viewer);
        if (s.filmLoopt) stopAlles(s);
        s.vorigeLoc = viewer.getLocation().clone();
        s.vorigeGameMode = viewer.getGameMode();
        s.modus = CineSession.Modus.PAD;
        s.pad = pad;
        bouwgCum(s);
        s.padT = 0;
        s.padRichting = 1;
        viewer.setGameMode(GameMode.SPECTATOR);
        s.filmLoopt = true;
        teleportNaarPunt(s, 0);
        viewer.sendMessage("§8[§6Cinema§8] §7path '" + pad.naam() + "' (" + pad.punten().size()
            + " points, " + String.format(java.util.Locale.ROOT, "%.0fs", s.totaal) + ") — move to return");
    }

    private void bouwgCum(CineSession s) {
        List<Pad.Punt> ptn = s.pad.punten();
        int n = ptn.size();
        boolean lus = plugin.padLus().equals("loop") && n >= 3;
        int segmenten = lus ? n : n - 1;
        double snel = s.pad.snelheid() > 0 ? s.pad.snelheid() : plugin.padSnelheid();
        s.cum = new double[segmenten + 1];
        s.cum[0] = 0;
        for (int i = 0; i < segmenten; i++) {
            Pad.Punt a = ptn.get(i), b = ptn.get((i + 1) % n);
            double d = Math.sqrt(sq(a.x() - b.x()) + sq(a.y() - b.y()) + sq(a.z() - b.z()));
            s.cum[i + 1] = s.cum[i] + Math.max(0.05, d / Math.max(0.1, snel));
        }
        s.totaal = s.cum[segmenten];
        s.lusModus = lus;
    }

    private static double sq(double v) { return v * v; }

    // ---------- de tick-kant ----------

    void tick() {
        for (CineSession s : new ArrayList<>(sessies.values())) {
            Player p = s.speler;
            if (!p.isOnline()) { sessies.remove(p.getUniqueId()); continue; }
            if (!s.filmLoopt) {
                if (s.modus == CineSession.Modus.OPNAME) continue;   // opnemer is zelf de camera
                if (!s.afk && !s.hasMovedRecent(afkSeconden()) && s.cineToegelaten && !p.isDead()) {
                    s.afk = true;
                    startAfkFilm(p, s);
                }
            } else {
                switch (s.modus) {
                    case ORBIT, DOELOF -> orbitStap(s);
                    case PAD -> padStap(s);
                    default -> { }
                }
            }
        }
    }

    /** AFK geworden: kies de shot volgens config (orbit | pad:<naam>). */
    private void startAfkFilm(Player p, CineSession s) {
        String shot = plugin.getConfig().getString("cine.afk-shot", "orbit");
        if (shot.toLowerCase().startsWith("pad:")) {
            Pad pad = plugin.pads().pad(shot.substring(4));
            if (pad != null && pad.punten().size() >= 2) { startPad(p, pad); return; }
            p.sendMessage("§8[§6Cinema§8] §7path '" + shot.substring(4) + "' not found — falling back to the orbit");
        }
        startFilm(p, p);
    }

    private void orbitStap(CineSession s) {
        Player p = s.speler;
        double head = plugin.getConfig().getDouble("cine.head-offset", 1.4);
        if (s.modus == CineSession.Modus.DOELOF) {
            if (s.doel == null || !s.doel.isOnline()) { stopAlles(s); return; }
            s.focusPunt = s.doel.getLocation().clone().add(0, head, 0);
        } else if (s.standIn != null) {
            // stand-in blijft staan; een voorbijganger wordt opgemerkt: hoofd draait langzaam naar ze toe
            NPC npc = (NPC) s.standIn;
            if (npc.isSpawned()) {
                float richting = s.vorigeLoc.getYaw();
                Player passer = null;
                for (Player q : s.vorigeLoc.getWorld().getNearbyPlayers(s.vorigeLoc, 5.2)) {
                    if (!q.getUniqueId().equals(s.speler.getUniqueId())) { passer = q; break; }
                }
                if (passer != null) {
                    org.bukkit.util.Vector dv = passer.getLocation().toVector().subtract(s.vorigeLoc.toVector());
                    richting = (float) (Math.toDegrees(Math.atan2(dv.getZ(), dv.getX())) - 90);
                    long now = System.currentTimeMillis();
                    Long last = opgemerkt.get(passer.getUniqueId());
                    if (last == null || now - last > 30_000L) {
                        opgemerkt.put(passer.getUniqueId(), now);
                        passer.sendActionBar(net.kyori.adventure.text.Component.text(
                                "You're in the shot.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                        plugin.adv().grant(passer, "extra");
                    }
                }
                s.standYaw = lerpHoek(s.standYaw, richting, 0.08);
                Location staan = s.vorigeLoc.clone();
                staan.setYaw(s.standYaw);
                npc.teleport(staan, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
        World w = s.focusPunt.getWorld();
        double staps = plugin.getConfig().getDouble("cine.orbit-speed", 0.0035);
        double straalBasis = plugin.getConfig().getDouble("cine.orbit-radius", 5.0);
        double adem = plugin.getConfig().getDouble("cine.orbit-breathe", 1.6);
        double crane = plugin.getConfig().getDouble("cine.orbit-crane", 1.9);
        s.hoek += staps;
        double straal = straalBasis + Math.sin(s.hoek * 0.7) * adem;
        double hoogte = 1.6 + Math.sin(s.hoek * 0.35) * crane;
        double x = s.focusPunt.getX() + Math.cos(s.hoek) * straal;
        double z = s.focusPunt.getZ() + Math.sin(s.hoek) * straal;
        double y = s.focusPunt.getY() + hoogte;

        if (plugin.collisionLift()) {
            if (w.getBlockAt((int) x, (int) Math.floor(y), (int) z).isSolid()) {
                y += 0.35; s.liftTeller++;
                if (s.liftTeller > 60) { stopAlles(s); return; }  // te benauwd: uit
            } else {
                s.liftTeller = Math.max(0, s.liftTeller - 1);
            }
        }

        Location cam = new Location(w, x, y, z);
        richtCamNaar(cam, s.focusPunt);
        teleporteer(p, s, cam);
    }

    private void padStap(CineSession s) {
        List<Pad.Punt> ptn = s.pad.punten();
        int n = ptn.size();
        World w = s.pad.wereld();
        if (w == null) { stopAlles(s); return; }
        double dt = 0.05;                                   // scheduler draait op 1 tick
        String lus = plugin.padLus();
        s.padT += dt * s.padRichting;
        if (lus.equals("loop")) {
            double m = s.padT % s.totaal;
            s.padT = (m < 0 ? m + s.totaal : m);
        } else if (lus.equals("once")) {
            if (s.padT >= s.totaal) s.padT = s.totaal;
        } else {                                            // back-and-forth
            if (s.padT >= s.totaal) { s.padT = s.totaal; s.padRichting = -1; }
            else if (s.padT <= 0) { s.padT = 0; s.padRichting = 1; }
        }
        int segmenten = s.cum.length - 1;
        int i = 0;
        while (i + 1 < segmenten && s.padT >= s.cum[i + 1]) i++;
        double reik = Math.max(1.0e-6, s.cum[i + 1] - s.cum[i]);
        double t = (s.padT - s.cum[i]) / reik;
        if (plugin.padEasing()) t = t * t * (3 - 2 * t);    // smoothstep: zacht in/out per been

        // positie: Catmull-Rom door de punten
        int b = (i + 1) % n;
        int a = index(i - 1, n, s.lusModus);
        int c = index(i + 1, n, s.lusModus);
        int d = index(i + 2, n, s.lusModus);
        Pad.Punt p0 = ptn.get(a), p1 = ptn.get(i), p2 = ptn.get(c), p3 = ptn.get(d);
        double tt = t, t2 = t * t, t3 = t2 * t;
        double x = 0.5 * ((2 * p1.x()) + (-p0.x() + p2.x()) * tt
            + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2
            + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
        double y = 0.5 * ((2 * p1.y()) + (-p0.y() + p2.y()) * tt
            + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2
            + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
        double z = 0.5 * ((2 * p1.z()) + (-p0.z() + p2.z()) * tt
            + (2 * p0.z() - 5 * p1.z() + 4 * p2.z() - p3.z()) * t2
            + (-p0.z() + 3 * p1.z() - 3 * p2.z() + p3.z()) * t3);

        Pad.Punt naar = ptn.get(b);
        float yaw = lerpHoek(p1.yaw(), naar.yaw(), t);
        float pitch = (float) (p1.pitch() + (naar.pitch() - p1.pitch()) * t);

        Location cam = new Location(w, x, y, z, yaw, pitch);
        teleporteer(s.speler, s, cam);
    }

    private int index(int idx, int n, boolean lus) {
        if (lus) return ((idx % n) + n) % n;
        return Math.max(0, Math.min(n - 1, idx));
    }

    private static float lerpHoek(float a, float b, double t) {
        float d = ((b - a + 540f) % 360f) - 180f;            // kortste boog
        return (float) (a + d * t);
    }

    private void teleportNaarPunt(CineSession s, int i) {
        World w = s.pad.wereld();
        if (w == null) return;
        List<Pad.Punt> ptn = s.pad.punten();
        if (i >= ptn.size()) i = ptn.size() - 1;
        Pad.Punt pt = ptn.get(i);
        s.cameraLoc = pt.loc(w);
        s.speler.teleport(s.cameraLoc);
    }

    private void richtCamNaar(Location cam, Location doel) {
        org.bukkit.util.Vector richting = doel.toVector().subtract(cam.toVector());
        double horizontaal = Math.hypot(richting.getX(), richting.getZ());
        cam.setYaw((float) (Math.toDegrees(Math.atan2(richting.getZ(), richting.getX())) - 90));
        cam.setPitch((float) -Math.toDegrees(Math.atan2(richting.getY(), horizontaal)));
    }

    /** Teleport + boekhouden, zodat opBeweging weet dat WIJ dit deden. */
    private void teleporteer(Player p, CineSession s, Location cam) {
        s.cameraLoc = cam;
        p.teleport(cam);
    }

    // ---------- stoppen / terugzetten ----------

    void stopAlles(CineSession s) {
        if (!s.actiefFilmpje()) return;
        s.filmLoopt = false;
        herstel(s);
        s.pad = null;
        s.opneemPad = null;
        s.doel = null;
        s.modus = CineSession.Modus.ORBIT;
    }

    private void herstel(CineSession s) {
        Player p = s.speler;
        verwijderStandIn(s);
        if (s.vorigeLoc != null && s.vorigeGameMode != null) {
            try {                                  // bij quit is isOnline() soms al false,
                p.setGameMode(s.vorigeGameMode);   // maar terugzetten MOET wel (spelerdata!)
                p.teleport(s.vorigeLoc);
            } catch (Throwable ignored) { }
        }
        s.vorigeLoc = null;
        s.vorigeGameMode = null;
        s.cameraLoc = null;
    }

    /** Bij plugin-stop: iedereen netjes terug. */
    void allesStoppen() {
        for (CineSession s : sessies.values()) {
            if (s.actiefFilmpje()) { s.filmLoopt = false; herstel(s); }
        }
    }

    // ---------- stand-in (Citizens) ----------

    private Object maakStandIn(Player p, Location loc) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null
                || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) return null;
        try {
            NPC npc;
            try {
                npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "");
            } catch (Throwable naamLeeg) {
                npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "cine-" + p.getName());
            }
            npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(p);
            npc.spawn(loc.clone(), SpawnReason.PLUGIN);
            npc.teleport(loc.clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            return npc;
        } catch (Throwable t) {
            plugin.getLogger().warning("stand-in aanmaken mislukt (Citizens?): " + t);
            return null;
        }
    }

    private void verwijderStandIn(CineSession s) {
        if (s.standIn == null) return;
        try { ((NPC) s.standIn).destroy(); } catch (Throwable ignored) { }
        s.standIn = null;
    }

    // ---------- publieke API ----------

    boolean isAfk(Player p) {
        CineSession s = sessies.get(p.getUniqueId());
        return s != null && s.afk;
    }

    void forceFilm(Player p) {
        CineSession s = sessie(p);
        if (!s.filmLoopt) startFilm(p, p);
    }

    /** Vrije opname-camera: spectator zonder terugzet-gedrag, voor /cine pad add. */
    void startOpname(Player p, Pad pad) {
        CineSession s = sessie(p);
        if (s.filmLoopt) stopAlles(s);
        s.vorigeLoc = p.getLocation().clone();
        s.vorigeGameMode = p.getGameMode();
        s.modus = CineSession.Modus.OPNAME;
        s.opneemPad = pad;
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage("§8[§6Cinema§8] §7recording on — §f/cine pad add §7drops a point in '"
            + pad.naam() + "' (movement no longer stops anything now)");
    }

    Map<java.util.UUID, CineSession> sessies() { return sessies; }
}
