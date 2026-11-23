package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Iekcorbar Gazette — a weekly written book, composed by the website and
 * delivered through the existing site_command_queue.
 *
 * The website owns the content (it is the only side that can read news_posts,
 * events and the user table). It enqueues `gazette publish <issue> <base64>`;
 * this class decodes it, overwrites books/weekly_gazette.txt, reloads the
 * BookManager, hands a copy to everyone online, fills the spawn lectern and
 * remembers the issue so offline players get theirs on their next join.
 *
 * On Sunday 18:00 (server time) the plugin calls the site over HTTPS with a
 * shared secret, so no Plesk cron is needed. The site is idempotent per ISO
 * week, so a server restart at exactly that moment cannot double-publish.
 */
public class Gazette implements Listener, TabExecutor {

    public static final String BOOK_ID = "weekly_gazette";

    private final IekcorbarSiteSync plugin;
    private final BookManager books;
    private final File stateFile;
    private final NamespacedKey issueKey;

    private int currentIssue = 0;
    private String siteUrl = "https://iekcorbar.com/api/gazette/publish";
    private String secret = "";
    private boolean autoEnabled = true;
    private String lecternSpec = "";
    private String model = "none";
    private BukkitTask autoTask;
    private BukkitTask lecternTask;
    private long nextAutoRun = Long.MAX_VALUE;
    private volatile boolean autoInFlight = false;

    public Gazette(IekcorbarSiteSync plugin, BookManager books) {
        this.plugin = plugin;
        this.books = books;
        this.stateFile = new File(plugin.getDataFolder(), "gazette.yml");
        this.issueKey = new NamespacedKey(plugin, "gazette_issue");
    }

    // ------------------------------------------------------------------ lifecycle

    public void enable() {
        loadConfig();
        loadState();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("gazette") != null) {
            plugin.getCommand("gazette").setExecutor(this);
            plugin.getCommand("gazette").setTabCompleter(this);
        }
        scheduleAuto();
        lecternTask = Bukkit.getScheduler().runTaskTimer(plugin, this::fillLectern, 200L, 20L * 600L);
    }

    public void stop() {
        if (autoTask != null) autoTask.cancel();
        if (lecternTask != null) lecternTask.cancel();
    }

    private void loadConfig() {
        siteUrl = plugin.getConfig().getString("gazette.site-url", siteUrl).trim();
        secret = plugin.getConfig().getString("gazette.secret", "").trim();
        autoEnabled = plugin.getConfig().getBoolean("gazette.auto.enabled", true);
        lecternSpec = plugin.getConfig().getString("gazette.lectern", "").trim();
        model = plugin.getConfig().getString("gazette.model", "none").trim();
        if (model.isEmpty()) model = "none";
    }

    private void loadState() {
        if (!stateFile.isFile()) return;
        try {
            currentIssue = YamlConfiguration.loadConfiguration(stateFile).getInt("current-issue", 0);
        } catch (Exception e) {
            plugin.getLogger().warning("[Gazette] Could not read gazette.yml: " + e.getMessage());
        }
    }

    private void saveState() {
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.set("current-issue", currentIssue);
            y.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Gazette] Could not write gazette.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ publishing

    /** Called by the command queue (and by staff) with the base64 book body. */
    public boolean publish(String issueArg, String base64) {
        int issue;
        try {
            issue = Integer.parseInt(issueArg);
        } catch (NumberFormatException e) {
            return false;
        }
        if (base64 == null || base64.length() < 16 || base64.length() > 60_000) return false;

        String body;
        try {
            body = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (body.isBlank()) return false;

        File dir = new File(plugin.getDataFolder(), "books");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Gazette] Could not create the books folder.");
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("id: ").append(BOOK_ID).append('\n');
        sb.append("title: The Iekcorbar Gazette\n");
        sb.append("author: The Guild Press\n");
        sb.append("description: Issue #").append(issue).append(" - printed by the Guild Press\n");
        sb.append("series: The Gazette\n");
        sb.append("volume: ").append(issue).append('\n');
        sb.append("model: ").append(model).append('\n');
        sb.append("copyable: true\n");
        sb.append("loot: false\n");
        sb.append("===\n");
        sb.append(body);
        if (!body.endsWith("\n")) sb.append('\n');
        try {
            Files.write(new File(dir, BOOK_ID + ".txt").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[Gazette] Could not write weekly_gazette.txt: " + e.getMessage());
            return false;
        }

        books.reload();
        int given = books.giveToAll(BOOK_ID);
        currentIssue = issue;
        saveState();
        fillLectern();

        plugin.getLogger().info("[Gazette] Published issue " + issue + " — " + given + " online player(s) served.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage("[Gazette] Issue " + issue
                        + " is out — check your inventory or the lectern at spawn.");
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ lectern & join

    private void fillLectern() {
        if (lecternSpec.isBlank() || currentIssue <= 0) return;
        try {
            String[] parts = lecternSpec.split(",");
            if (parts.length != 4) {
                plugin.getLogger().warning("[Gazette] gazette.lectern must be world,x,y,z.");
                return;
            }
            World world = Bukkit.getWorld(parts[0].trim());
            if (world == null) return;
            Block block = world.getBlockAt(
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim()));
            if (!(block.getState() instanceof Lectern lectern)) {
                plugin.getLogger().warning("[Gazette] No lectern at " + lecternSpec + ".");
                return;
            }
            ItemStack onIt = lectern.getInventory().getItem(0);
            if (onIt != null && currentIssue == onIt.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(issueKey, PersistentDataType.INTEGER, 0)) return;

            ItemStack book = books.create(BOOK_ID);
            if (book == null) return;
            ItemMeta meta = book.getItemMeta();
            meta.getPersistentDataContainer().set(issueKey, PersistentDataType.INTEGER, currentIssue);
            book.setItemMeta(meta);
            lectern.getInventory().setItem(0, book);
            lectern.update();
        } catch (Exception e) {
            plugin.getLogger().warning("[Gazette] fillLectern: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> deliver(player), 20L);
    }

    private void deliver(Player player) {
        if (currentIssue <= 0 || !player.isOnline()) return;
        int have = player.getPersistentDataContainer().getOrDefault(issueKey, PersistentDataType.INTEGER, 0);
        if (have >= currentIssue) return;
        ItemStack book = books.create(BOOK_ID);
        if (book == null) return;
        ItemMeta meta = book.getItemMeta();
        meta.getPersistentDataContainer().set(issueKey, PersistentDataType.INTEGER, currentIssue);
        book.setItemMeta(meta);
        for (ItemStack rest : player.getInventory().addItem(book).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        player.getPersistentDataContainer().set(issueKey, PersistentDataType.INTEGER, currentIssue);
        player.sendMessage("[Gazette] Issue " + currentIssue
                + " was printed while you were away — this one's on the Guild.");
    }

    // ------------------------------------------------------------------ auto trigger

    private void scheduleAuto() {
        if (autoTask != null) autoTask.cancel();
        if (!autoEnabled || secret.isBlank()) {
            plugin.getLogger().info("[Gazette] Automatic publishing is off (gazette.auto.enabled or gazette.secret).");
            return;
        }
        nextAutoRun = nextSunday18();
        autoTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::autoTick, 100L, 20L * 60L);
    }

    private static long nextSunday18() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime t = now.with(DayOfWeek.SUNDAY).withHour(18).withMinute(0).withSecond(0).withNano(0);
        if (!t.isAfter(now)) t = t.plusWeeks(1);
        return t.toInstant().toEpochMilli();
    }

    private void autoTick() {
        if (System.currentTimeMillis() < nextAutoRun || autoInFlight) return;
        autoInFlight = true;
        nextAutoRun = nextSunday18();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(siteUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"source\":\"plugin\"}"))
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            plugin.getLogger().warning("[Gazette] Auto-publish failed: " + error.getMessage());
                        } else if (response.statusCode() >= 300) {
                            plugin.getLogger().warning("[Gazette] Auto-publish returned HTTP "
                                    + response.statusCode() + ": " + response.body());
                        } else {
                            plugin.getLogger().info("[Gazette] Auto-publish OK: " + response.body());
                        }
                        autoInFlight = false;
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("[Gazette] Auto-publish error: " + e.getMessage());
            autoInFlight = false;
        }
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "publish" -> {
                if (args.length < 3) {
                    send(sender, "Usage: /gazette publish <issue> <base64>");
                    return true;
                }
                boolean ok = publish(args[1], args[2]);
                send(sender, ok ? "Gazette issue " + args[1] + " published."
                        : "Could not publish — bad issue number, payload or file path. Check the console.");
            }
            case "status" -> {
                send(sender, "Gazette — current issue: " + (currentIssue > 0 ? currentIssue : "none")
                        + " | auto: " + (autoTask != null ? "on" : "off")
                        + " | next: " + (autoTask == null ? "-" : java.time.Instant.ofEpochMilli(nextAutoRun).toString()));
                send(sender, "Lectern: " + (lecternSpec.isBlank() ? "(not configured)" : lecternSpec));
            }
            case "reload" -> {
                loadConfig();
                scheduleAuto();
                books.reload();
                send(sender, "Gazette config reloaded.");
            }
            case "forget" -> {
                if (args.length < 2) {
                    send(sender, "Usage: /gazette forget <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    send(sender, "Player is not online.");
                    return true;
                }
                target.getPersistentDataContainer().remove(issueKey);
                send(sender, "Next join will hand " + target.getName() + " the current issue again.");
            }
            default -> {
                send(sender, "/gazette status — current issue and schedule");
                send(sender, "/gazette publish <issue> <base64> — used by the website queue");
                send(sender, "/gazette reload — re-read config + books");
                send(sender, "/gazette forget <player> — re-deliver on next join (testing)");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("status", "publish", "reload", "forget")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("forget")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }

    private static void send(CommandSender to, String message) {
        to.sendMessage(message);
    }
}
