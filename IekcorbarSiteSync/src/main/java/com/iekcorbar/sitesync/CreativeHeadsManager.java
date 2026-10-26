package com.iekcorbar.sitesync;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HeadDatabase heads are free only for staff in creative mode; everyone else
 * always pays. The server's permissions are managed through CMI, which has no
 * way to make a permission depend on the gamemode (LuckPerms contexts would),
 * so this sets HeadDatabase's "free" nodes per player through a Bukkit
 * PermissionAttachment: true while a player with iekcorbar.heads.creativefree
 * is in creative, explicitly false in every other case. The explicit false is
 * what stops OP'd staff from getting free heads in survival (OP otherwise
 * grants every permission that isn't explicitly set).
 *
 * Bukkit itself doesn't expand wildcards, so every free node is listed
 * separately (config: creative-heads.free-nodes).
 */
public class CreativeHeadsManager implements Listener {

    static final String STAFF_PERMISSION = "iekcorbar.heads.creativefree";

    private static final List<String> DEFAULT_NODES = List.of(
            "headdb.free.*",
            "headdb.free.alphabet", "headdb.free.animals", "headdb.free.blocks",
            "headdb.free.decoration", "headdb.free.food_drinks", "headdb.free.humans",
            "headdb.free.humanoid", "headdb.free.miscellaneous", "headdb.free.monsters",
            "headdb.free.plants", "headdb.free.custom", "headdb.free.custom2",
            "headdb.free.custom3", "headdb.free.custom4", "headdb.free.custom5",
            "headdb.free.online_players", "headdb.free.phead");

    private final IekcorbarSiteSync plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private List<String> nodes = DEFAULT_NODES;

    public CreativeHeadsManager(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // The live server keeps its own config.yml, so a missing section just
        // means "on, with the default node list".
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("creative-heads");
        if (section != null && !section.getBoolean("enabled", true)) {
            plugin.getLogger().info("Creative heads: disabled in config.");
            return;
        }
        if (section != null && !section.getStringList("free-nodes").isEmpty()) {
            nodes = new ArrayList<>(section.getStringList("free-nodes"));
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            apply(player, player.getGameMode());
        }
        plugin.getLogger().info("Creative heads: " + nodes.size() + " HeadDatabase free node(s) managed.");
    }

    public void disable() {
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                player.removeAttachment(entry.getValue());
            }
        }
        attachments.clear();
    }

    private void apply(Player player, GameMode mode) {
        boolean free = mode == GameMode.CREATIVE && player.hasPermission(STAFF_PERMISSION);
        PermissionAttachment attachment = attachments.computeIfAbsent(
                player.getUniqueId(), id -> player.addAttachment(plugin));
        for (String node : nodes) {
            attachment.setPermission(node, free);
        }
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Creative heads: " + player.getName() + " (" + mode + ") free=" + free);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer(), event.getPlayer().getGameMode());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        apply(event.getPlayer(), event.getNewGameMode());
    }

    // Per-world gamemode plugins can switch the mode silently on a world change.
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        apply(event.getPlayer(), event.getPlayer().getGameMode());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = attachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            event.getPlayer().removeAttachment(attachment);
        }
    }
}
