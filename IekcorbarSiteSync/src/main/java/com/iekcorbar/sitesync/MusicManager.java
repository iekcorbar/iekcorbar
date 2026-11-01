package com.iekcorbar.sitesync;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Muziek per speler, gekozen op WorldGuard-regio > biome > wereld > default (music.yml).
 * De speler hoort een track tot het gebied wisselt; dan stopt de oude en start de nieuwe.
 * Een track met gap 0 loopt naadloos (het bestand is zelf loop-klaar gemaakt).
 * Per tick kost dit een getalvergelijking per speler; regio/biome wordt elke 20 ticks bepaald.
 */
public final class MusicManager {

    private static final class Track {
        String sound;
        int ticks;
        int gapTicks;
        int delayTicks;
        String title;
    }

    private static final class State {
        String track;
        long nextAt;
        boolean announced;
    }

    private final IekcorbarSiteSync plugin;
    private final Map<String, Track> tracks = new HashMap<>();
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<String, String> worlds = new HashMap<>();
    private final Map<String, String> biomes = new HashMap<>();
    private final Map<String, String> regions = new HashMap<>();
    private final Map<String, String> prefixes = new HashMap<>();
    private String defaultTrack = "overworld";
    private boolean wg;
    private BukkitTask task;
    private long tick;

    MusicManager(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
    }

    void start() {
        File f = new File(plugin.getDataFolder(), "music.yml");
        if (!f.exists()) {
            plugin.saveResource("music.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        defaultTrack = cfg.getString("default", "overworld");
        read(cfg.getConfigurationSection("worlds"), worlds);
        read(cfg.getConfigurationSection("biomes"), biomes);
        read(cfg.getConfigurationSection("regions"), regions);
        read(cfg.getConfigurationSection("region-prefixes"), prefixes);
        ConfigurationSection ts = cfg.getConfigurationSection("tracks");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                ConfigurationSection t = ts.getConfigurationSection(k);
                if (t == null || t.getString("sound") == null) continue;
                Track tr = new Track();
                tr.sound = t.getString("sound");
                tr.ticks = (int) Math.round(t.getDouble("seconds", 60) * 20);
                tr.gapTicks = (int) Math.round(t.getDouble("gap", 0) * 20);
                tr.delayTicks = (int) Math.round(t.getDouble("delay", 0) * 20);
                tr.title = t.getString("title", "");
                tracks.put(k, tr);
            }
        }
        wg = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        plugin.getLogger().info("Muziek: " + tracks.size() + " track(s), WorldGuard " + (wg ? "aan" : "niet gevonden"));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 20L, 1L);
    }

    void stop() {
        if (task != null) task.cancel();
        states.clear();
    }

    private static void read(ConfigurationSection s, Map<String, String> into) {
        if (s == null) return;
        for (String k : s.getKeys(false)) into.put(k, s.getString(k));
    }

    private void run() {
        tick++;
        boolean decide = tick % 20 == 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            State st = states.computeIfAbsent(p.getUniqueId(), u -> new State());
            if (decide) {
                String want = choose(p);
                if (!want.equals(st.track)) {
                    if (st.track != null) {
                        Track old = tracks.get(st.track);
                        if (old != null) p.stopSound(old.sound, SoundCategory.MUSIC);
                    }
                    Track nt = tracks.get(want);
                    st.track = want;
                    st.announced = false;
                    st.nextAt = tick + (nt == null ? 0 : nt.delayTicks);
                }
            }
            if (st.track == null) continue;
            Track t = tracks.get(st.track);
            if (t == null || tick < st.nextAt) continue;
            p.playSound(p, t.sound, SoundCategory.MUSIC, 1f, 1f);
            st.nextAt = tick + t.ticks + t.gapTicks;
            if (!st.announced && t.title != null && !t.title.isEmpty()) {
                p.sendActionBar(Component.text("♪ " + t.title));
            }
            st.announced = true;
        }
        if (decide) states.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    private String choose(Player p) {
        if (wg && (!regions.isEmpty() || !prefixes.isEmpty())) {
            String r = region(p);
            if (r != null) return r;
        }
        String b = p.getLocation().getBlock().getBiome().getKey().getKey();
        String t = biomes.get(b);
        if (t != null && tracks.containsKey(t)) return t;
        t = worlds.get(p.getWorld().getName());
        if (t != null && tracks.containsKey(t)) return t;
        return defaultTrack;
    }

    /** Track van de regio met de hoogste prioriteit waar de speler in staat, of null. */
    private String region(Player p) {
        try {
            ApplicableRegionSet set = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .createQuery().getApplicableRegions(BukkitAdapter.adapt(p.getLocation()));
            String best = null;
            int bestPrio = Integer.MIN_VALUE;
            for (ProtectedRegion pr : set) {
                String t = regions.get(pr.getId());
                if (t == null) {
                    for (Map.Entry<String, String> e : prefixes.entrySet()) {
                        if (pr.getId().startsWith(e.getKey())) {
                            t = e.getValue();
                            break;
                        }
                    }
                }
                if (t == null || !tracks.containsKey(t)) continue;
                if (pr.getPriority() > bestPrio) {
                    bestPrio = pr.getPriority();
                    best = t;
                }
            }
            return best;
        } catch (Throwable ex) {
            wg = false;
            plugin.getLogger().warning("Muziek: WorldGuard-opvraag mislukt, regio's uitgezet: " + ex);
            return null;
        }
    }
}
