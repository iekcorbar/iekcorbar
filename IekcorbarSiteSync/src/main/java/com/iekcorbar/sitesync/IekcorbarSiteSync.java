package com.iekcorbar.sitesync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class IekcorbarSiteSync extends JavaPlugin implements Listener {

    private static IekcorbarSiteSync instance;
    private Economy economy = null;
    private MySQLSync mysqlSync;
    private DeepSync deepSync;
    private AdvancementManager advancementManager;
    private BookManager bookManager;
    private CreativeHeadsManager creativeHeadsManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Setup Vault economy if present
        setupEconomy();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Mirrors in-game/console CMI moderation commands into the website's
        // own moderation_logs table — see ModerationAuditListener.
        getServer().getPluginManager().registerEvents(new ModerationAuditListener(this), this);

        // Register /webstats command
        if (getCommand("webstats") != null) {
            getCommand("webstats").setExecutor(new WebStatsCommand(this));
        }

        // /webrewards (VoteSpeed) and /webskin (SkinsRestorer) were removed in
        // 1.5.0 — both plugins are deprecated on this server. Votes will come
        // in via NuVotifier (VotifierListener already listens to its event).

        // Start MySQL sync service if enabled in config
        if (getConfig().getBoolean("database.enabled", getConfig().getBoolean("mysql.enabled", true))) {
            mysqlSync = new MySQLSync(this);
            mysqlSync.start();
            // Deep sync (vanilla stats + advancements → player_stats) rides the same connection.
            deepSync = new DeepSync(this, mysqlSync);
            deepSync.start();
        }

        // Real vote verification (VoteSpeed's built-in NuVotifier receiver,
        // or a standalone Votifier/NuVotifier plugin if that's what's
        // actually present) — see VotifierListener for details. Registered
        // after mysqlSync is up since recordVotifierEvent needs it.
        VotifierListener.register(this);

        // Config-driven custom advancements (advancements.yml, /iadv).
        advancementManager = new AdvancementManager(this);
        advancementManager.enable();

        // Custom written books from books/*.txt, loot-table drops, /ibook.
        bookManager = new BookManager(this);
        bookManager.enable();

        // HeadDatabase: free heads only for staff in creative, see CreativeHeadsManager.
        creativeHeadsManager = new CreativeHeadsManager(this);
        creativeHeadsManager.enable();

        // FreeMinecraftModels props in every direction (props.yml), see PropPlacementManager.
        PropPlacementManager props = new PropPlacementManager(this);
        props.enable();
        SteamWrenchManager wrench = new SteamWrenchManager(this, props);
        wrench.enable();
        // /props: creative-only menu with every prop item.
        new PropsMenu(this, props, wrench).enable();

        getLogger().info("IekcorbarSiteSync v" + getPluginMeta().getVersion() + " has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (advancementManager != null) {
            advancementManager.disable();
        }
        if (creativeHeadsManager != null) {
            creativeHeadsManager.disable();
        }
        if (mysqlSync != null) {
            mysqlSync.stop();
        }
        if (deepSync != null) {
            deepSync.stop();
        }
        getLogger().info("IekcorbarSiteSync has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (mysqlSync != null && getConfig().getBoolean("sync-player-stats", true)) {
            mysqlSync.syncSinglePlayer(event.getPlayer(), true);
        }
        // Deliver any shop purchases made while this player was offline —
        // see MySQLSync.processQueuedShopPurchases for why this can't just
        // happen at purchase time.
        if (mysqlSync != null) {
            mysqlSync.processQueuedShopPurchases(event.getPlayer());
            mysqlSync.processPendingAchievements(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (mysqlSync != null && getConfig().getBoolean("sync-player-stats", true)) {
            mysqlSync.syncSinglePlayer(event.getPlayer(), false);
        }
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    public static IekcorbarSiteSync getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public AdvancementManager getAdvancementManager() {
        return advancementManager;
    }

    public BookManager getBookManager() {
        return bookManager;
    }

    public MySQLSync getMysqlSync() {
        return mysqlSync;
    }
}
