package com.iekcorbar.friendinvite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Custom bring-a-friend rewards. No Discord, no website changes, no third-party plugin. */
public final class FriendInvitePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String GOLD = "§6", WHITE = "§f", GREEN = "§a", RED = "§c", GRAY = "§7";

    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "invites.yml");
        loadData();
        var cmd = getCommand("friend");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::checkPending, 200L, 600L); // 10s, then every 30s
        getLogger().info("FriendInvite " + getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ------------------------------------------------------------ storage

    private void loadData() {
        data = new YamlConfiguration();
        if (dataFile.exists()) {
            try {
                data.load(dataFile);
            } catch (Exception e) {
                getLogger().warning("Could not load invites.yml: " + e.getMessage());
            }
        }
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save invites.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ helpers

    private String codeFor(UUID uuid) {
        String key = "codes." + uuid;
        String code = data.getString(key);
        if (code == null || code.isBlank()) {
            code = uuid.toString().replace("-", "").substring(0, 6).toUpperCase();
            data.set(key, code);
            saveData();
        }
        return code;
    }

    private UUID inviterByCode(String code) {
        var section = data.getConfigurationSection("codes");
        if (section == null) return null;
        for (String uuid : section.getKeys(false)) {
            if (code.equalsIgnoreCase(data.getString("codes." + uuid))) {
                try {
                    return UUID.fromString(uuid);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private long firstSeen(UUID uuid) {
        return data.getLong("seen." + uuid, 0L);
    }

    private void grant(Player p) {
        for (String raw : getConfig().getStringList("rewards")) {
            String cmd = raw.replace("{player}", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private void message(Player p, String text) {
        p.sendMessage(GOLD + "[FriendInvite] " + WHITE + text);
    }

    /** Announce to the public Discord channel via DiscordSRV's console command. No compile dependency. */
    private void discord(String text) {
        String id = getConfig().getString("discord-channel-id", "");
        String target = (id == null || id.isBlank()) ? "" : "#" + id + " ";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "discord broadcast " + target + text);
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (firstSeen(uuid) == 0L) {
            data.set("seen." + uuid, System.currentTimeMillis());
            saveData();
            message(p, "You're new here! Ask a friend for their invite code and use " + GRAY + "/friend claim <code>" + WHITE + " within 24 hours to get a starter reward together.");
        }
        // An inviter's reward waits here until they are online again.
        String pending = "pending_reward." + uuid;
        if (data.contains(pending)) {
            data.set(pending, null);
            saveData();
            grant(p);
            message(p, "A friend finished their first 30 minutes — here's your reward!");
        }
    }

    /** Runs on the main thread; the playtime statistic is safe for online players only. */
    private void checkPending() {
        var section = data.getConfigurationSection("pending");
        if (section == null) return;
        long required = (long) getConfig().getInt("min-play-minutes", 30) * 60L * 20L;
        for (String inviteeKey : new ArrayList<>(section.getKeys(false))) {
            UUID invitee;
            UUID inviter;
            try {
                invitee = UUID.fromString(inviteeKey);
                inviter = UUID.fromString(data.getString("pending." + inviteeKey, ""));
            } catch (IllegalArgumentException bad) {
                data.set("pending." + inviteeKey, null);
                saveData();
                continue;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(invitee);
            Player p = off.getPlayer();
            if (p == null || !p.isOnline()) continue;             // wait until they are back
            if (p.getStatistic(Statistic.PLAY_ONE_MINUTE) < required) continue;

            data.set("pending." + inviteeKey, null);
            data.set("rewarded." + inviteeKey, System.currentTimeMillis());
            saveData();

            grant(p);
            message(p, "Thanks for playing 30 minutes — your invite reward has been delivered!");

            String inviterName = Bukkit.getOfflinePlayer(inviter).getName();
            discord("**" + p.getName() + "** reached 30 minutes with an invite from **" + inviterName + "** — both just got a starter reward. Welcome aboard!");

            Player friend = Bukkit.getPlayer(inviter);
            if (friend != null && friend.isOnline()) {
                grant(friend);
                message(friend, "Your friend reached 30 minutes — here's your reward!");
            } else {
                data.set("pending_reward." + inviter, System.currentTimeMillis());
                saveData();
            }
        }
    }

    // ------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("friendinvite.use")) {
            sender.sendMessage(RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.isOp()) {
            reloadConfig();
            sender.sendMessage(GREEN + "FriendInvite config reloaded.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            message(p, GRAY + "/friend code" + WHITE + " - get your invite code; " + GRAY + "/friend claim <code>" + WHITE + " - use a friend's code; " + GRAY + "/friend status");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "code" -> message(p, "Your invite code is " + GOLD + codeFor(p.getUniqueId()) + WHITE + ". Share it with a friend who has never played here.");
            case "status" -> {
                int friends = 0;
                var claimed = data.getConfigurationSection("claimed");
                if (claimed != null) {
                    String me = p.getUniqueId().toString();
                    for (String k : claimed.getKeys(false)) if (me.equals(data.getString("claimed." + k))) friends++;
                }
                message(p, "Your code: " + GOLD + codeFor(p.getUniqueId()) + WHITE + ". Friends rewarded: " + GREEN + friends + WHITE + ". You can invite as many friends as you like.");
            }
            case "claim" -> {
                if (args.length < 2) {
                    message(p, GRAY + "Usage: /friend claim <code>");
                    return true;
                }
                UUID uuid = p.getUniqueId();
                if (data.contains("claimed." + uuid) || data.contains("pending." + uuid) || data.contains("rewarded." + uuid)) {
                    message(p, RED + "You have already claimed an invite. One per player.");
                    return true;
                }
                UUID inviter = inviterByCode(args[1]);
                if (inviter == null) {
                    message(p, RED + "That invite code doesn't exist. Check it with your friend.");
                    return true;
                }
                if (inviter.equals(uuid)) {
                    message(p, RED + "You can't invite yourself.");
                    return true;
                }
                long seen = firstSeen(uuid);
                long windowMs = (long) getConfig().getInt("claim-window-hours", 24) * 3600_000L;
                if (seen == 0L || System.currentTimeMillis() - seen > windowMs) {
                    message(p, RED + "Only brand-new players can claim an invite code. Join for the first time and claim it within 24 hours.");
                    return true;
                }
                long inviterSeen = firstSeen(inviter);
                long minAge = (long) getConfig().getInt("min-inviter-age-hours", 1) * 3600_000L;
                if (inviterSeen == 0L || System.currentTimeMillis() - inviterSeen < minAge) {
                    message(p, RED + "That inviter is too new to earn rewards yet.");
                    return true;
                }
                data.set("claimed." + uuid, inviter.toString());
                data.set("pending." + uuid, inviter.toString());
                saveData();
                message(p, GREEN + "Claimed! Play for " + getConfig().getInt("min-play-minutes", 30) + " minutes and you both get a reward.");
                Player friend = Bukkit.getPlayer(inviter);
                if (friend != null && friend.isOnline())
                    message(friend, GREEN + p.getName() + " used your invite code. They need 30 minutes of playtime.");
                String inviterName = Bukkit.getOfflinePlayer(inviter).getName();
                discord("**" + p.getName() + "** just joined with an invite from **" + inviterName + "**. They need 30 minutes of playtime for both to get a reward!");
            }
            default -> message(p, GRAY + "/friend code | claim <code> | status");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("code", "claim", "status", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) return List.of("<code>");
        return List.of();
    }
}
