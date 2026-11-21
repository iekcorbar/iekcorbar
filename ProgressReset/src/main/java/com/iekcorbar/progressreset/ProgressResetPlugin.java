package com.iekcorbar.progressreset;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Staff-only, one-time reset of two collection systems that store progress in the
 * player's PersistentDataContainer instead of a config file:
 *   Wildlife journal: wildlife:seen, wildlife:seen_rare  (+ wildlife:* advancements)
 *   Relics guide:     relics:owned, relics:favs          (+ relics:* advancements)
 * The target must be online; PDC of offline players cannot be edited safely without
 * shutting the server down and editing playerdata/*.dat.
 */
public final class ProgressResetPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final NamespacedKey[] KEYS = {
            new NamespacedKey("wildlife", "seen"),
            new NamespacedKey("wildlife", "seen_rare"),
            new NamespacedKey("relics", "owned"),
            new NamespacedKey("relics", "favs"),
    };
    private static final NamespacedKey DONE = new NamespacedKey("progressreset", "done");

    @Override
    public void onEnable() {
        var cmd = getCommand("resetprogress");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("ProgressReset " + getDescription().getVersion() + " enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("progressreset.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /resetprogress <player> [confirm]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer '" + args[0] + "' is not online. Ask them to join first.");
            return true;
        }
        if (target.getPersistentDataContainer().has(DONE, PersistentDataType.BYTE)) {
            sender.sendMessage("§c" + target.getName() + " has already used their one-time reset.");
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§eThis clears " + target.getName() + "'s Wildlife journal AND Relics collection permanently, once. Type §6/resetprogress " + target.getName() + " confirm §eto continue.");
            return true;
        }

        int removed = 0;
        for (NamespacedKey key : KEYS) {
            if (target.getPersistentDataContainer().has(key)) {
                target.getPersistentDataContainer().remove(key);
                removed++;
            }
        }
        int revoked = 0;
        var advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement adv = advancements.next();
            String ns = adv.getKey().getNamespace().toLowerCase(Locale.ROOT);
            if (!ns.equals("wildlife") && !ns.equals("relics")) continue;
            AdvancementProgress progress = target.getAdvancementProgress(adv);
            for (String criteria : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criteria);
                revoked++;
            }
        }
        target.getPersistentDataContainer().set(DONE, PersistentDataType.BYTE, (byte) 1);

        String msg = "Your Wildlife journal and Relics collection have been reset (" + removed
                + " lists, " + revoked + " advancement entries). Please relog once so the in-game guides refresh.";
        target.sendMessage("§6[ProgressReset] §f" + msg);
        sender.sendMessage("§aDone: " + target.getName() + " — " + removed + " PDC lists removed, "
                + revoked + " advancement criteria revoked. They should relog once.");
        getLogger().info("ProgressReset: " + sender.getName() + " reset " + target.getName()
                + " (" + removed + " keys, " + revoked + " advancement criteria)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 2) return List.of("confirm");
        return List.of();
    }
}
