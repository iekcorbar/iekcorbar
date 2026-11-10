package com.iekcorbar.sitesync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

public class WebStatsCommand implements CommandExecutor {

    private final IekcorbarSiteSync plugin;

    public WebStatsCommand(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("{\"error\":\"Username argument missing\"}");
            return true;
        }

        String targetName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);

        if (offlinePlayer == null || (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline())) {
            sender.sendMessage("{\"error\":\"Player not found on server\",\"username\":\"" + escapeJson(targetName) + "\"}");
            return true;
        }

        boolean isOnline = offlinePlayer.isOnline();
        Player onlinePlayer = offlinePlayer.getPlayer();

        // 1-3. Speeltijd, kills/deaths en gebroken blokken.
        //
        // 29-10 fix (Paper watchdog 15-20 s stall): CraftOfflinePlayer.getStatistic
        // parsede het complete stats-bestand VAN ELKE ROEP opnieuw. DeMaterialen-loop
        // hieronder maakte daarvan ~1600 hoofddraad-parsings per /webstats.
        // Offline spelers lezen het bestand nu ONE keer direct van schijf; online
        // spelers treffen een geladen counter aan en gaan gewoon via de API.
        JsonObject stats = isOnline ? null : readStatisticsFile(offlinePlayer.getUniqueId());

        // 1. Playtime in hours (TICKS to HOURS: 20 ticks = 1 sec -> 72,000 ticks = 1 hour)
        int ticksPlayed = 0;
        if (stats != null) {
            ticksPlayed = customInt(stats, "minecraft:play_time", "minecraft:play_one_minute");
        } else {
            try {
                ticksPlayed = offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE);
            } catch (Exception e) {
                try {
                    // Fallback for older Spigot versions
                    ticksPlayed = offlinePlayer.getStatistic(Statistic.valueOf("PLAY_TIME"));
                } catch (Exception ignored) {}
            }
        }
        int playtimeHours = ticksPlayed / 72000;

        // 2. Kills and Deaths
        int kills = 0;
        int deaths = 0;
        int mobKills = 0;
        if (stats != null) {
            kills = customInt(stats, "minecraft:players_killed");
            deaths = customInt(stats, "minecraft:deaths");
            mobKills = customInt(stats, "minecraft:mob_kills");
        } else {
            try { kills = offlinePlayer.getStatistic(Statistic.PLAYER_KILLS); } catch (Exception ignored) {}
            try { deaths = offlinePlayer.getStatistic(Statistic.DEATHS); } catch (Exception ignored) {}
            try { mobKills = offlinePlayer.getStatistic(Statistic.MOB_KILLS); } catch (Exception ignored) {}
        }

        // 3. Blocks mined: de som van de minecraft:mined-categorie.
        long blocksMined = 0;
        if (stats != null) {
            blocksMined = sumCategory(stats, "minecraft:mined");
        } else if (isOnline) {
            // Online: de statistieken zitten al in het geheugen, dit is geen IO.
            try {
                for (Material mat : Material.values()) {
                    if (mat.isBlock()) {
                        blocksMined += onlinePlayer.getStatistic(Statistic.MINE_BLOCK, mat);
                    }
                }
            } catch (Exception ignored) {}
        }
        // Offline zónder stats-bestand: de speler heeft niets geregistreerd -> 0 is correct.

        // 4. Economy balance (Vault integration)
        double balance = 0.0;
        if (plugin.getEconomy() != null) {
            try {
                balance = plugin.getEconomy().getBalance(offlinePlayer);
            } catch (Exception ignored) {}
        }

        // 5. EliteCoins (EliteMobs integration if present)
        int eliteCoins = 0;
        Plugin eliteMobsPlugin = Bukkit.getPluginManager().getPlugin("EliteMobs");
        if (eliteMobsPlugin != null && isOnline && onlinePlayer != null) {
            try {
                if (!onlinePlayer.getMetadata("eliteCoins").isEmpty()) {
                    eliteCoins = onlinePlayer.getMetadata("eliteCoins").get(0).asInt();
                }
            } catch (Exception ignored) {}
        }

        // 6. Generic PlaceholderAPI bridge (Lands, ValhallaMMO, ValhallaRaces, ...) —
        // see PlaceholderSync.java and config.yml's placeholder-sync section.
        PlaceholderSync.Result placeholderResult = PlaceholderSync.resolve(plugin, offlinePlayer);
        int landsClaimed = placeholderResult.landsClaims != null ? placeholderResult.landsClaims : 0;

        // 7. Jobs Reborn integration (if present)
        StringBuilder jobsJson = new StringBuilder("[");
        Plugin jobsPlugin = Bukkit.getPluginManager().getPlugin("Jobs");
        if (jobsPlugin != null) {
            try {
                Class<?> jobsClass = Class.forName("com.gamingmesh.jobs.Jobs");
                Object jPlayer = jobsClass.getMethod("getPlayer", OfflinePlayer.class).invoke(null, offlinePlayer);
                if (jPlayer != null) {
                    java.util.List<?> jobProgList = (java.util.List<?>) jPlayer.getClass().getMethod("getJobProgression").invoke(jPlayer);
                    if (jobProgList != null && !jobProgList.isEmpty()) {
                        boolean first = true;
                        for (Object prog : jobProgList) {
                            Object jobObj = prog.getClass().getMethod("getJob").invoke(prog);
                            String jName = (String) jobObj.getClass().getMethod("getName").invoke(jobObj);
                            int jLevel = (int) prog.getClass().getMethod("getLevel").invoke(prog);
                            if (!first) jobsJson.append(",");
                            jobsJson.append("{\"name\":\"").append(escapeJson(jName)).append("\",\"level\":").append(jLevel);
                            try {
                                double jExp = (double) prog.getClass().getMethod("getExperience").invoke(prog);
                                int jMaxExp = (int) prog.getClass().getMethod("getMaxExperience").invoke(prog);
                                jobsJson.append(",\"experience\":").append(jExp).append(",\"maxExperience\":").append(jMaxExp);
                            } catch (Exception ignored) {}
                            jobsJson.append("}");
                            first = false;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        jobsJson.append("]");

        // Build JSON String
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"username\":\"").append(escapeJson(offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName)).append("\",");
        json.append("\"online\":").append(isOnline).append(",");
        json.append("\"playtimeHours\":").append(playtimeHours).append(",");
        json.append("\"kills\":").append(kills).append(",");
        json.append("\"deaths\":").append(deaths).append(",");
        json.append("\"mobKills\":").append(mobKills).append(",");
        json.append("\"blocksMined\":").append(blocksMined).append(",");
        json.append("\"balance\":").append(balance).append(",");
        json.append("\"eliteCoins\":").append(eliteCoins).append(",");
        json.append("\"landsClaimed\":").append(landsClaimed).append(",");
        json.append("\"jobs\":").append(jobsJson).append(",");
        json.append("\"pluginStats\":").append(placeholderResult.json);
        json.append("}");

        sender.sendMessage(json.toString());
        return true;
    }

    /**
     * Leest het stats-JSON van één speler direct van schijf (één keer, geen
     * per-Material-herschoteling). Eerst de hoofdwereld (waar Bukkit mee opstart),
     * dan de rest - het speelse statsbestand staat normaliter daar.
     */
    private static JsonObject readStatisticsFile(UUID id) {
        for (World w : Bukkit.getWorlds()) {
            File f = new File(w.getWorldFolder(), "stats" + File.separator + id + ".json");
            if (!f.isFile()) {
                continue;
            }
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(r);
                if (root.isJsonObject() && root.getAsJsonObject().has("statistics")) {
                    return root.getAsJsonObject().getAsJsonObject("statistics");
                }
            } catch (Exception ignored) {}
            // keep looking in the other worlds' stats folders - this used to `return null`
            // right here, so one unreadable file in the first world ended the whole search
        }
        return null;
    }

    /** Waarde uit de minecraft:custom-categorie; meerdere mogelijke (oude/nieuwe) namen. */
    private static int customInt(JsonObject stats, String... keys) {
        JsonElement c = stats.get("minecraft:custom");
        if (c == null || !c.isJsonObject()) {
            return 0;
        }
        for (String k : keys) {
            JsonElement v = c.getAsJsonObject().get(k);
            if (v != null && v.isJsonPrimitive()) {
                try {
                    return v.getAsInt();
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    /** De som van alle waarden in een categorie (bv. minecraft:mined = alle gebroken blokken). */
    private static long sumCategory(JsonObject stats, String category) {
        JsonElement c = stats.get(category);
        if (c == null || !c.isJsonObject()) {
            return 0;
        }
        long total = 0;
        for (Map.Entry<String, JsonElement> e : c.getAsJsonObject().entrySet()) {
            try {
                total += e.getValue().getAsInt();
            } catch (Exception ignored) {}
        }
        return total;
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
