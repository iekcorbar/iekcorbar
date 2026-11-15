package com.iekcorbar.sitesync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Website → server command queue — the replacement for RCON (15-11-2026).
 *
 * RCON was switched off in server.properties: every command the website sent
 * (and every reconnect attempt) echoed into the live console, which had
 * become unreadable spam. Instead, the website INSERTs a command row into
 * `site_command_queue` (in this plugin's own MySQL database) and polls the
 * row; this service claims rows once per tick, dispatches them as console on
 * the main thread through a CAPTURING CommandSender (so the reply is written
 * back to the row and NEVER printed to the console), and marks the row done.
 * Net effect for the website: same request/response semantics as before,
 * zero console output, and no open TCP port at all.
 *
 * Trust model: whoever can write to this database table can make the server
 * run console commands — exactly the same trust level RCON had (the RCON
 * password lived in the same config), so nothing new is exposed.
 */
public class CommandQueueService {

    private static final int MAX_RESPONSE_CHARS = 60_000;

    private final IekcorbarSiteSync plugin;
    private final MySQLSync mysqlSync;
    private final AtomicBoolean ticking = new AtomicBoolean(false);
    private BukkitTask task;
    private int pollSeconds;
    private int maxPerTick;
    private int commandTimeoutSeconds;

    public CommandQueueService(IekcorbarSiteSync plugin, MySQLSync mysqlSync) {
        this.plugin = plugin;
        this.mysqlSync = mysqlSync;
    }

    public void start() {
        pollSeconds = Math.max(1, plugin.getConfig().getInt("command-queue.poll-seconds", 1));
        maxPerTick = Math.max(1, plugin.getConfig().getInt("command-queue.max-per-tick", 10));
        commandTimeoutSeconds = Math.max(1, plugin.getConfig().getInt("command-queue.command-timeout-seconds", 8));

        createTables();
        task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!ticking.compareAndSet(false, true)) return; // previous tick still busy — skip, never pile up
                try {
                    tick();
                } catch (Exception e) {
                    plugin.getLogger().warning("Command queue tick failed: " + e.getMessage());
                } finally {
                    ticking.set(false);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, pollSeconds * 20L);
        plugin.getLogger().info("CommandQueueService started (website command transport; RCON no longer needed).");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void createTables() {
        try {
            Connection conn = mysqlSync.sharedConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS site_command_queue (" +
                            "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "command TEXT NOT NULL," +
                            "status VARCHAR(10) NOT NULL DEFAULT 'queued'," +
                            "response MEDIUMTEXT," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "started_at DATETIME NULL," +
                            "done_at DATETIME NULL," +
                            "KEY idx_queue_status (status, id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS site_queue_status (" +
                            "id TINYINT NOT NULL PRIMARY KEY," +
                            "heartbeat DATETIME NOT NULL," +
                            "command_queue_enabled TINYINT NOT NULL DEFAULT 1" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) {
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create command-queue tables: " + e.getMessage());
        }
    }

    private void tick() throws Exception {
        Connection conn = mysqlSync.sharedConnection();

        // Heartbeat + enabled flag: the website refuses to queue commands
        // while this row is older than a few ticks, so a dead/stale service
        // surfaces as an instant, clear error instead of a silent timeout.
        boolean enabled = plugin.getConfig().getBoolean("command-queue.enabled", true);
        try (PreparedStatement stmt = conn.prepareStatement(
                "REPLACE INTO site_queue_status (id, heartbeat, command_queue_enabled) VALUES (1, NOW(), ?)")) {
            stmt.setInt(1, enabled ? 1 : 0);
            stmt.executeUpdate();
        }
        if (!enabled) return;

        // A row stuck in 'running' can only mean the plugin was killed /
        // restarted mid-command: no live thread will ever finish it. Fail it
        // so the site gets an error instead of waiting forever.
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE site_command_queue SET status='failed', response='Server restarted before this command finished.', done_at=NOW() " +
                        "WHERE status='running' AND started_at < NOW() - INTERVAL 5 MINUTE")) {
            stmt.executeUpdate();
        }

        List<long[]> claimed = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, command FROM site_command_queue WHERE status='queued' ORDER BY id LIMIT " + maxPerTick)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String command = rs.getString(2);
                    // Claim atomically: if the UPDATE hits 0 rows another tick
                    // (or a second server instance) already owns this row.
                    try (PreparedStatement claim = conn.prepareStatement(
                            "UPDATE site_command_queue SET status='running', started_at=NOW() WHERE id=? AND status='queued'")) {
                        claim.setLong(1, id);
                        if (claim.executeUpdate() == 1) {
                            claimed.add(new long[]{id});
                            commands.add(command);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < claimed.size(); i++) {
            long id = claimed.get(i)[0];
            finishRow(conn, id, runCommand(commands.get(i)));
        }

        // Housekeeping: answered rows the website already read (or abandoned)
        // have no reason to linger.
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM site_command_queue WHERE status IN ('done', 'failed') AND done_at < NOW() - INTERVAL 10 MINUTE")) {
            stmt.executeUpdate();
        }
    }

    private void finishRow(Connection conn, long id, String[] statusAndResponse) throws Exception {
        String response = statusAndResponse[1];
        if (response != null && response.length() > MAX_RESPONSE_CHARS) {
            response = response.substring(0, MAX_RESPONSE_CHARS) + "…";
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE site_command_queue SET status=?, response=?, done_at=NOW() WHERE id=?")) {
            stmt.setString(1, statusAndResponse[0]);
            stmt.setString(2, response);
            stmt.setLong(3, id);
            stmt.executeUpdate();
        }
    }

    /** Dispatches on the main thread (required for 99% of Bukkit commands) and captures the reply. */
    private String[] runCommand(String command) {
        List<String> captured = Collections.synchronizedList(new ArrayList<>());
        ConsoleCommandSender sender = capturingSender(captured);
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean ok = Bukkit.dispatchCommand(sender, command);
                    String out = joinLines(captured);
                    future.complete(out.isEmpty() ? (ok ? "" : "Command failed or unknown: " + command) : out);
                } catch (Throwable t) {
                    future.complete("Command error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                }
            });
        } catch (Throwable t) {
            return new String[]{"failed", "Server was shutting down; command not run."};
        }
        try {
            String out = future.get(commandTimeoutSeconds, TimeUnit.SECONDS);
            return new String[]{"done", out};
        } catch (Exception e) {
            return new String[]{"failed", "The command took longer than " + commandTimeoutSeconds + "s on the server."};
        }
    }

    private static String joinLines(List<String> lines) {
        synchronized (lines) {
            return String.join("\n", lines).trim();
        }
    }

    /**
     * A console CommandSender that swallows output into a buffer instead of
     * printing it — this is the whole reason the console no longer sees the
     * website's commands. It's a dynamic proxy AROUND the real console
     * sender: every {@code sendMessage}/{@code sendPlainMessage}/
     * {@code sendRichMessage} variant (String, String[], UUID-prefixed,
     * Component, Adventure, bungee) is recorded and dropped, and everything
     * else — permissions, isOp, getServer, whatever a plugin pokes at — is
     * delegated to the genuine console, so commands behave exactly as if
     * they were typed there. Nothing is ever printed to the live console.
     */
    private static ConsoleCommandSender capturingSender(List<String> out) {
        ConsoleCommandSender real = Bukkit.getConsoleSender();
        return (ConsoleCommandSender) java.lang.reflect.Proxy.newProxyInstance(
                CommandQueueService.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("send") && args != null) {
                        for (Object arg : args) addCaptured(out, arg);
                        return null;
                    }
                    if (name.equals("toString")) return "CONSOLE";
                    return method.invoke(real, args);
                });
    }

    private static void addCaptured(List<String> out, Object arg) {
        if (arg == null) return;
        if (arg instanceof Component component) {
            out.add(PlainTextComponentSerializer.plainText().serialize(component));
        } else if (arg instanceof String s) {
            out.add(s);
        } else if (arg instanceof String[] arr) {
            Collections.addAll(out, arr);
        } else if (arg instanceof Component[] arr) {
            for (Component c : arr) out.add(PlainTextComponentSerializer.plainText().serialize(c));
        } else if (arg.getClass().getName().startsWith("net.md_5.bungee")) {
            // Legacy bungee chat components: best-effort text.
            out.add(String.valueOf(arg));
        }
        // UUID and other marker arguments carry no text — ignored.
    }
}
