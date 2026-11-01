package com.iekcorbar.gouw;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gouw: chunk land claims, the in-house successor of the (paid) Lands plugin.
 *
 * First start: claims.json missing and a Lands database still in plugins/Lands -> import everything
 * (owner, members, chunks - see LandsImporter), so the old gouwen stay exactly where players left them.
 * After that Gouw keeps its own little readable JSON and guards the chunks with GouwListener.
 *
 * One gouw per player, trusted members may build; the flag set is deliberately small (build/interact/
 * fight in, entry messages) - no nations, no wars, no upkeep. That is what the server actually used.
 */
public final class GouwPlugin extends JavaPlugin {
    private ClaimStore store;
    private GouwListener listener;
    private GouwMenu menu;
    private Expiry expiry;
    private final Set<UUID> bypassing = new HashSet<>();
    /** Extra chunks a player bought (contract NPC), on top of the rank limit. Saved in bonus.yml. */
    private final java.util.Map<UUID, Integer> bonus = new java.util.HashMap<>();
    private File bonusFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new ClaimStore(this, new File(getDataFolder(), "claims.json"));
        if (!store.load() || store.all().isEmpty()) {
            if (store.all().isEmpty()) LandsImporter.run(this, store);
        }
        bonusFile = new File(getDataFolder(), "bonus.yml");
        if (bonusFile.exists()) {
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(bonusFile);
            for (String k : y.getKeys(false)) {
                try { bonus.put(UUID.fromString(k), y.getInt(k)); } catch (IllegalArgumentException ignored) { }
            }
        }
        expiry = new Expiry(this);
        listener = new GouwListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        menu = new GouwMenu(this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getScheduler().runTaskTimer(this, this::expireTick, 20L * 60, 20L * 3600);
        GouwCommand cmd = new GouwCommand(this);
        PluginCommand pc = getCommand("gouw");
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);
        getLogger().info("Gouw enabled - " + store.all().size() + " gouwen, " + store.totalChunks() + " chunks claimed.");
    }

    /** Hourly housekeeping; check() no-ops while expire-inactive-days is 0, so a reload can turn it on. */
    private void expireTick() {
        int n = expiry.check();
        if (n > 0) getLogger().info("Expire check released " + n + " gouw(s) of inactive owners.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    public int bonusOf(UUID id) {
        return bonus.getOrDefault(id, 0);
    }

    public void setBonus(UUID id, int n) {
        if (n <= 0) bonus.remove(id); else bonus.put(id, n);
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        for (java.util.Map.Entry<UUID, Integer> e : bonus.entrySet()) y.set(e.getKey().toString(), e.getValue());
        try { y.save(bonusFile); } catch (java.io.IOException ex) { getLogger().severe("Could not save bonus.yml: " + ex.getMessage()); }
    }

    public ClaimStore store() {
        return store;
    }

    public GouwMenu menu() {
        return menu;
    }

    public Expiry expiry() {
        return expiry;
    }

    public int getMaxChunks() {
        return Math.max(1, getConfig().getInt("max-chunks", 16));
    }

    /**
     * Chunk limit for one player: the highest of the config value and any
     * gouw.chunks.<n> permission node they hold (a rank group in the permission
     * plugin grants e.g. gouw.chunks.55). No dependency on a specific permission plugin.
     */
    public int getMaxChunks(org.bukkit.entity.Player p) {
        int max = getMaxChunks();
        for (org.bukkit.permissions.PermissionAttachmentInfo i : p.getEffectivePermissions()) {
            if (!i.getValue()) continue;
            String n = i.getPermission();
            if (!n.startsWith("gouw.chunks.")) continue;
            try { max = Math.max(max, Integer.parseInt(n.substring(12))); } catch (NumberFormatException ignored) { }
        }
        return max + bonusOf(p.getUniqueId());
    }

    public boolean enterMessages() {
        return getConfig().getBoolean("enter-messages", true);
    }

    public boolean isBypassing(UUID id) {
        return bypassing.contains(id);
    }

    public void setBypassing(UUID id, boolean on) {
        if (on) bypassing.add(id);
        else bypassing.remove(id);
    }
}
