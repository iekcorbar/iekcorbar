package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class MySQLSync {

    private final IekcorbarSiteSync plugin;
    private BukkitTask syncTask;
    private Connection connection;
    private final AccessListSync accessListSync;
    // Guards the periodic sync tick: if the previous tick is still stuck waiting on a
    // slow/unreachable database (see getConnection()'s timeout below), skip this tick
    // instead of piling up another blocked thread on top of it.
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public MySQLSync(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
        this.accessListSync = new AccessListSync(plugin);
    }

    public void start() {
        // Initialize tables asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::createTablesIfNotExist);

        int intervalSeconds = plugin.getConfig().getInt("database.sync-interval-seconds", 
                             plugin.getConfig().getInt("mysql.sync-interval-seconds", 30));
        long ticks = intervalSeconds * 20L;

        syncTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!syncing.compareAndSet(false, true)) {
                    // Previous tick hasn't finished (database likely unreachable/slow) —
                    // skip this tick rather than spawning another thread on top of it.
                    return;
                }
                try {
                    syncDatabase();
                    syncAllPlayerStats();
                } finally {
                    syncing.set(false);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 40L, ticks);
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        // closeConnection is synchronized: a sync thread still connecting would block the main thread (and so the
        // server stop) until its connect timeout, so close from a separate thread
        Thread closer = new Thread(this::closeConnection, "IekcorbarSiteSync-close");
        closer.setDaemon(true);
        closer.start();
    }

    /** Shared JDBC access for DeepSync — the same pooled connection, reached the same safe way. */
    synchronized Connection sharedConnection() throws Exception {
        return getConnection();
    }

    private synchronized Connection getConnection() throws Exception {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return connection;
        }

        // Check 'database' section first (ReviveNode configuration), fallback to 'mysql'
        String host = plugin.getConfig().getString("database.host", plugin.getConfig().getString("mysql.host", "127.0.0.1"));
        int port = plugin.getConfig().getInt("database.port", plugin.getConfig().getInt("mysql.port", 3306));
        String databaseName = plugin.getConfig().getString("database.name", plugin.getConfig().getString("mysql.database", "iekcorbar"));
        String username = plugin.getConfig().getString("database.username", plugin.getConfig().getString("mysql.username", "iekjeBR36"));
        String password = plugin.getConfig().getString("database.password", plugin.getConfig().getString("mysql.password", "TODO")); // echt ww staat alleen in config.yml op de server
        boolean useSSL = plugin.getConfig().getBoolean("database.ssl", false);

        Class.forName("com.mysql.cj.jdbc.Driver");
        // IMPORTANT: connectTimeout/socketTimeout are required here. Without them, a
        // slow/unreachable host can leave this call blocked for a very long time (OS TCP
        // retry behavior, not the driver's), and since this runs on a fresh async thread
        // every sync tick / player join / player quit, an unreachable database can pile up
        // enough stuck OS threads to exhaust the JVM's thread quota (pthread_create EAGAIN)
        // and starve unrelated plugins too. Fail fast instead.
        DriverManager.setLoginTimeout(5);
        String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName +
                "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&autoReconnect=true" +
                "&connectTimeout=5000&socketTimeout=10000";
        connection = DriverManager.getConnection(url, username, password);
        return connection;
    }

    private synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception ignored) {}
    }

    private void createTablesIfNotExist() {
        try {
            Connection conn = getConnection();
            
            // Create player_stats table
            String sqlStats = "CREATE TABLE IF NOT EXISTS player_stats (" +
                    "username VARCHAR(100) PRIMARY KEY," +
                    "online BOOLEAN DEFAULT FALSE," +
                    "playtime_hours INT DEFAULT 0," +
                    "kills INT DEFAULT 0," +
                    "deaths INT DEFAULT 0," +
                    "mob_kills INT DEFAULT 0," +
                    "blocks_mined INT DEFAULT 0," +
                    "balance DOUBLE DEFAULT 0.0," +
                    "elite_coins INT DEFAULT 0," +
                    "lands_claimed INT DEFAULT 0," +
                    "jobs_json LONGTEXT," +
                    "plugin_placeholders_json LONGTEXT," +
                    "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";
            try (PreparedStatement stmt = conn.prepareStatement(sqlStats)) {
                stmt.executeUpdate();
            }

            // Create player_history table
            String sqlHistory = "CREATE TABLE IF NOT EXISTS player_history (" +
                    "day VARCHAR(10) PRIMARY KEY," +
                    "players INT NOT NULL DEFAULT 0," +
                    "peak_time VARCHAR(10) DEFAULT ''," +
                    "last_updated VARCHAR(20) DEFAULT ''" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";
            try (PreparedStatement stmt = conn.prepareStatement(sqlHistory)) {
                stmt.executeUpdate();
            }

            // Real, verified votes received via VotifierListener — see that
            // class for why this is a separate table from vote_log rather
            // than replacing it (deliberately additive, doesn't change the
            // existing click-based reward flow's behavior).
            String sqlVotifierEvents = "CREATE TABLE IF NOT EXISTS votifier_events (" +
                    "id VARCHAR(50) PRIMARY KEY," +
                    "username VARCHAR(100) NOT NULL," +
                    "service_name VARCHAR(100) NOT NULL," +
                    "address VARCHAR(100)," +
                    "vote_timestamp VARCHAR(50)," +
                    "received_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "KEY idx_votifier_events_user (username)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";
            try (PreparedStatement stmt = conn.prepareStatement(sqlVotifierEvents)) {
                stmt.executeUpdate();
            }

            accessListSync.createTablesIfNotExist(conn);

        } catch (Exception e) {
            plugin.getLogger().warning("MySQL Table creation check notice: " + e.getMessage());
        }
    }

    // Called (from VotifierListener, off the main thread already isn't
    // guaranteed since Bukkit fires most events synchronously — hence the
    // explicit async dispatch here) for every real vote received, from any
    // service including our own direct "Iekcorbar" one.
    public void recordVotifierEvent(String username, String serviceName, String address, String timeStamp) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = getConnection();
                String sql = "INSERT INTO votifier_events (id, username, service_name, address, vote_timestamp) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, java.util.UUID.randomUUID().toString());
                    stmt.setString(2, username);
                    stmt.setString(3, serviceName);
                    stmt.setString(4, address);
                    stmt.setString(5, timeStamp);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record votifier event in MySQL: " + e.getMessage());
            }
        });
    }

    private void syncDatabase() {
        try {
            Connection conn = getConnection();

            // Mirror banned-players.json / whitelist.json — see AccessListSync.
            accessListSync.sync(conn);

            // 1. Sync Player History Table (online players, peak time)
            int onlineCount = Bukkit.getOnlinePlayers().size();
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            String nowTime = new SimpleDateFormat("HH:mm").format(new Date());

            String upsertHistory = "INSERT INTO player_history (day, players, peak_time, last_updated) " +
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                    "players = GREATEST(players, VALUES(players)), " +
                    "peak_time = IF(VALUES(players) > players, VALUES(peak_time), peak_time), " +
                    "last_updated = VALUES(last_updated)";

            try (PreparedStatement stmt = conn.prepareStatement(upsertHistory)) {
                stmt.setString(1, today);
                stmt.setInt(2, onlineCount);
                stmt.setString(3, nowTime);
                stmt.setString(4, nowTime);
                stmt.executeUpdate();
            }

            // 2. Poll and Process Unrewarded Votes (rewarded = 0)
            String queryVotes = "SELECT id, username, site_name FROM vote_log WHERE rewarded = 0 LIMIT 20";
            try (PreparedStatement stmt = conn.prepareStatement(queryVotes);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String voteId = rs.getString("id");
                    String voterName = rs.getString("username");

                    Player player = Bukkit.getPlayerExact(voterName);
                    if (player != null && player.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "give " + voterName + " diamond 3");
                            player.sendMessage("§a[Vote] Thank you for voting! You received 3 diamonds!");
                        });

                        try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE vote_log SET rewarded = 1 WHERE id = ?")) {
                            updateStmt.setString(1, voteId);
                            updateStmt.executeUpdate();
                        }
                    }
                }
            }

        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("MySQL Sync warning: " + e.getMessage());
            }
            closeConnection();
        }
    }

    public void syncSinglePlayer(OfflinePlayer offlinePlayer, boolean isOnline) {
        if (offlinePlayer == null || offlinePlayer.getName() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = getConnection();

                int ticksPlayed = 0;
                try { ticksPlayed = offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE); } 
                catch (Exception e) {
                    try { ticksPlayed = offlinePlayer.getStatistic(Statistic.valueOf("PLAY_TIME")); } catch (Exception ignored) {}
                }
                int playtimeHours = ticksPlayed / 72000;

                int kills = 0, deaths = 0, mobKills = 0;
                try { kills = offlinePlayer.getStatistic(Statistic.PLAYER_KILLS); } catch (Exception ignored) {}
                try { deaths = offlinePlayer.getStatistic(Statistic.DEATHS); } catch (Exception ignored) {}
                try { mobKills = offlinePlayer.getStatistic(Statistic.MOB_KILLS); } catch (Exception ignored) {}

                int blocksMined = 0;
                try {
                    for (Material mat : Material.values()) {
                        if (mat.isBlock()) {
                            blocksMined += offlinePlayer.getStatistic(Statistic.MINE_BLOCK, mat);
                        }
                    }
                } catch (Exception ignored) {}

                double balance = 0.0;
                if (plugin.getEconomy() != null) {
                    try { balance = plugin.getEconomy().getBalance(offlinePlayer); } catch (Exception ignored) {}
                }

                int eliteCoins = 0;
                Plugin eliteMobsPlugin = Bukkit.getPluginManager().getPlugin("EliteMobs");
                if (eliteMobsPlugin != null && offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                    try {
                        Player p = offlinePlayer.getPlayer();
                        if (!p.getMetadata("eliteCoins").isEmpty()) {
                            eliteCoins = p.getMetadata("eliteCoins").get(0).asInt();
                        }
                    } catch (Exception ignored) {}
                }

                // Generic PlaceholderAPI bridge (ValhallaMMO, ValhallaRaces, ...)
                // — see PlaceholderSync.java and config.yml's placeholder-sync section.
                // Chunk claims come from Gouw's claims.json now (2026-10-26, Lands retired);
                // the placeholder bridge stays as fallback for a server without Gouw.
                PlaceholderSync.Result placeholderResult = PlaceholderSync.resolve(plugin, offlinePlayer);
                Integer gouwClaims = GouwClaims.count(offlinePlayer.getUniqueId());
                int landsClaimed = gouwClaims != null ? gouwClaims
                        : (placeholderResult.landsClaims != null ? placeholderResult.landsClaims : 0);

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
                                    jobsJson.append("{\"name\":\"").append(jName.replace("\"", "\\\"")).append("\",\"level\":").append(jLevel);
                                    // Experience toward the next level — best-effort, added on
                                    // top of the already-working name/level fields above rather
                                    // than replacing them, so a signature mismatch here (Jobs
                                    // plugin version differences) only drops these two extra
                                    // fields instead of breaking job sync entirely.
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

                String sqlUpsert = "INSERT INTO player_stats " +
                        "(username, online, playtime_hours, kills, deaths, mob_kills, blocks_mined, balance, elite_coins, lands_claimed, jobs_json, plugin_placeholders_json) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "online = VALUES(online), " +
                        "playtime_hours = VALUES(playtime_hours), " +
                        "kills = VALUES(kills), " +
                        "deaths = VALUES(deaths), " +
                        "mob_kills = VALUES(mob_kills), " +
                        "blocks_mined = VALUES(blocks_mined), " +
                        "balance = VALUES(balance), " +
                        "elite_coins = VALUES(elite_coins), " +
                        "lands_claimed = VALUES(lands_claimed), " +
                        "jobs_json = VALUES(jobs_json), " +
                        "plugin_placeholders_json = VALUES(plugin_placeholders_json)";

                try (PreparedStatement stmt = conn.prepareStatement(sqlUpsert)) {
                    stmt.setString(1, offlinePlayer.getName());
                    stmt.setBoolean(2, isOnline);
                    stmt.setInt(3, playtimeHours);
                    stmt.setInt(4, kills);
                    stmt.setInt(5, deaths);
                    stmt.setInt(6, mobKills);
                    stmt.setInt(7, blocksMined);
                    stmt.setDouble(8, balance);
                    stmt.setInt(9, eliteCoins);
                    stmt.setInt(10, landsClaimed);
                    stmt.setString(11, jobsJson.toString());
                    stmt.setString(12, placeholderResult.json);
                    stmt.executeUpdate();
                }

            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Failed to sync player stats for " + offlinePlayer.getName() + ": " + e.getMessage());
                }
            }
        });
    }

    private void syncAllPlayerStats() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            syncSinglePlayer(p, true);
            // Achievements can be earned on the website while the player is
            // already online in-game (e.g. sending a chat message from
            // another device) — join alone wouldn't catch that, so this also
            // runs on every periodic tick for whoever's currently online.
            processPendingAchievements(p);
        }
    }

    // Website actions (chat, journal, friends, brewing, shop — see
    // src/lib/achievements.js) award achievements instantly on the website
    // side; this delivers the in-game side of it (toast + chat message) the
    // next time the player is seen, either right on join or on the next
    // periodic tick if they're already online. `notified` is this table's
    // equivalent of shop_purchases.status — marks what still needs the
    // plugin to catch up.
    public void processPendingAchievements(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = getConnection();
                String query = "SELECT pa.id, a.title, a.icon, a.slug FROM player_achievements pa " +
                        "JOIN achievements a ON a.id = pa.achievement_id " +
                        "WHERE LOWER(pa.username) = LOWER(?) AND pa.notified = 0";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, player.getName());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String rowId = rs.getString("id");
                            String title = rs.getString("title");
                            String icon = rs.getString("icon");
                            String slug = rs.getString("slug");

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§6§l[Achievement] §e" + title);
                                ToastNotifier.showToast(plugin, player, title, icon != null ? icon : "minecraft:paper");
                                // Custom advancements (advancements.yml) tied to this website achievement.
                                if (plugin instanceof IekcorbarSiteSync main && main.getAdvancementManager() != null) {
                                    main.getAdvancementManager().onWebsiteAchievement(player, slug);
                                }
                            });

                            try (PreparedStatement update = conn.prepareStatement(
                                    "UPDATE player_achievements SET notified = 1 WHERE id = ?")) {
                                update.setString(1, rowId);
                                update.executeUpdate();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Failed to process pending achievements for " + player.getName() + ": " + e.getMessage());
                }
            }
        });
    }

    // Mirrors an in-game/console-typed CMI moderation command into the same
    // moderation_logs table the website writes to (see ModerationAuditListener
    // for why this exists, and api/moderation/route.js for the website's own
    // insert this matches the shape of). Skips the write entirely if the
    // command sender isn't recognized as website staff (admin/moderator) —
    // same authority the website's own role checks use — so a random
    // player's failed, no-permission attempt at a staff command doesn't
    // pollute the audit log as if it were real. The server console is always
    // trusted (typing at the machine's own terminal already implies more
    // trust than any in-game permission node could).
    public void logModerationAction(String username, String type, String reason, String staffMember) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = getConnection();

                if (!"Console".equals(staffMember)) {
                    try (PreparedStatement roleCheck = conn.prepareStatement(
                            "SELECT role FROM users WHERE LOWER(mc_username) = LOWER(?)")) {
                        roleCheck.setString(1, staffMember);
                        try (ResultSet rs = roleCheck.executeQuery()) {
                            if (!rs.next()) return;
                            String role = rs.getString("role");
                            if (!"admin".equals(role) && !"moderator".equals(role)) return;
                        }
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO moderation_logs (id, username, type, reason, staff_member, date) VALUES (?, ?, ?, ?, ?, NOW())")) {
                    stmt.setString(1, java.util.UUID.randomUUID().toString());
                    stmt.setString(2, username);
                    stmt.setString(3, type);
                    stmt.setString(4, reason);
                    stmt.setString(5, staffMember);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Failed to mirror moderation action to MySQL: " + e.getMessage());
                }
            }
        });
    }

    // Shop purchases made while a player was offline are recorded by the
    // website as status 'queued' rather than attempted blind — a give
    // command's RCON response can't reliably signal "player was offline"
    // (no exception, just an in-game reply the site can't parse), so the
    // website skips attempting delivery entirely and leaves it to this
    // instead (see api/shop/purchase/route.js). Mirrors the unrewarded-vote
    // polling in syncDatabase() above: async DB read, hop back to the main
    // thread for the actual give/toast since Bukkit API calls aren't
    // thread-safe off it. Called from IekcorbarSiteSync's onPlayerJoin.
    public void processQueuedShopPurchases(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Connection conn = getConnection();
                String query = "SELECT sp.id, sp.item_name, si.give_command FROM shop_purchases sp " +
                        "JOIN shop_items si ON si.id = sp.item_id " +
                        "WHERE LOWER(sp.username) = LOWER(?) AND sp.status = 'queued'";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, player.getName());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String purchaseId = rs.getString("id");
                            String itemName = rs.getString("item_name");
                            String giveTemplate = rs.getString("give_command");
                            String giveCommand = giveTemplate.replace("{player}", player.getName());
                            // ';;' chains multiple console commands in one give_command
                            // (shop packs: kit + a /relics random roll). dispatchCommand
                            // takes exactly one command per call, same split as the
                            // website-side purchase route does via RCON.
                            String[] parts = giveCommand.split(";;");

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                for (String part : parts) {
                                    String cmd = part.trim();
                                    if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                }
                                player.sendMessage("§a[Shop] Your " + itemName + " purchase has been delivered!");
                                ToastNotifier.showToast(plugin, player, "Delivered: " + itemName,
                                        ToastNotifier.iconFromGiveCommand(giveTemplate));
                            });

                            try (PreparedStatement update = conn.prepareStatement(
                                    "UPDATE shop_purchases SET status = 'delivered', give_message = ? WHERE id = ?")) {
                                update.setString(1, "Delivered automatically on join");
                                update.setString(2, purchaseId);
                                update.executeUpdate();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Failed to process queued shop purchases for " + player.getName() + ": " + e.getMessage());
                }
            }
        });
    }
}
