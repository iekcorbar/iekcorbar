package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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

        // 1. Playtime in hours (TICKS to HOURS: 20 ticks = 1 sec -> 72,000 ticks = 1 hour)
        int ticksPlayed = 0;
        try {
            ticksPlayed = offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE);
        } catch (Exception e) {
            try {
                // Fallback for older Spigot versions
                ticksPlayed = offlinePlayer.getStatistic(Statistic.valueOf("PLAY_TIME"));
            } catch (Exception ignored) {}
        }
        int playtimeHours = ticksPlayed / 72000;

        // 2. Kills and Deaths
        int kills = 0;
        int deaths = 0;
        int mobKills = 0;
        try { kills = offlinePlayer.getStatistic(Statistic.PLAYER_KILLS); } catch (Exception ignored) {}
        try { deaths = offlinePlayer.getStatistic(Statistic.DEATHS); } catch (Exception ignored) {}
        try { mobKills = offlinePlayer.getStatistic(Statistic.MOB_KILLS); } catch (Exception ignored) {}

        // 3. Blocks mined
        int blocksMined = 0;
        try {
            for (Material mat : Material.values()) {
                if (mat.isBlock()) {
                    blocksMined += offlinePlayer.getStatistic(Statistic.MINE_BLOCK, mat);
                }
            }
        } catch (Exception ignored) {}

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
