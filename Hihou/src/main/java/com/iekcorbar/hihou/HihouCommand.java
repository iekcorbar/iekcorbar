package com.iekcorbar.hihou;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /ih give|edit|remove|map <...> (staff) plus /ih key <type> [player] [amount] and
 * /ih event open|close. "key" also works from the console on purpose: the site's vote-reward
 * command (vote_reward_config) can then be "ih key %player% vote", giving one key per vote with
 * zero extra code in SiteSync or this plugin.
 */
final class HihouCommand implements TabExecutor {
    private final HihouPlugin plugin;

    HihouCommand(HihouPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!admin(s)) return true;
                if (a.length < 2 || !Keys.validType(a[1])) {
                    msg(s, "/ih give <" + String.join("|", Chests.TYPES) + "> [player] [amount]", NamedTextColor.RED);
                    return true;
                }
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
                if (t == null) {
                    msg(s, "Player not found.", NamedTextColor.RED);
                    return true;
                }
                int amount = a.length > 3 ? parseInt(a[3], 1) : 1;
                var d = plugin.chests().def(Keys.norm(a[1]));
                t.getInventory().addItem(plugin.chests().decor.item(d, amount));
                msg(s, "Gave " + amount + "x " + d.name + " to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "key" -> {
                if (a.length < 2 || !Keys.validType(a[1])) {
                    msg(s, "/ih key <" + String.join("|", Keys.TYPES) + "> [player] [amount]", NamedTextColor.RED);
                    return true;
                }
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
                if (t == null) {
                    msg(s, "Player not found.", NamedTextColor.RED);
                    return true;
                }
                if (!(s instanceof Player) && !s.hasPermission("hihou.admin") && !Bukkit.getConsoleSender().equals(s)) {
                    msg(s, "Only the console or staff may mint keys.", NamedTextColor.RED);
                    return true;
                }
                int amount = a.length > 3 ? parseInt(a[3], 1) : 1;
                t.getInventory().addItem(plugin.keys().item(Keys.norm(a[1]), amount));
                msg(s, "Gave " + amount + "x " + Keys.cap(a[1]) + " Key to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "edit", "remove", "map" -> {
                if (!admin(s) || !(s instanceof Player p)) return true;
                Block hitB = p.getTargetBlock(null, 6);
                Decor.Prop prop = hitB == null ? null : plugin.chests().decor.at(hitB);
                if (prop == null && hitB != null) prop = plugin.chests().decor.at(hitB.getRelative(org.bukkit.block.BlockFace.UP));
                if (prop == null) {
                    msg(s, "Look at a Hihou chest.", NamedTextColor.RED);
                    return true;
                }
                switch (sub) {
                    case "edit" -> plugin.chests().openEdit(p, prop);
                    case "remove" -> {
                        plugin.chests().store.clear(ChestStore.id(prop.center()));
                        plugin.chests().decor.breakProp(prop, false); // no drop: the item is re-givable via /ih give
                        plugin.chests().store.flush();
                        msg(s, "Chest removed (loot deleted with it).", NamedTextColor.GREEN);
                    }
                    default -> {
                        p.getInventory().addItem(plugin.chests().map(prop));
                        msg(s, "Treasure map for that chest created.", NamedTextColor.GREEN);
                    }
                }
            }
            case "event" -> {
                if (!admin(s)) return true;
                boolean open = a.length > 1 && a[1].equalsIgnoreCase("open");
                plugin.chests().eventOpen(open);
                Bukkit.broadcast(Component.text("秘宝 Event chests are now " + (open ? "OPEN - go and vote, hunt and win!" : "closed. Thanks for playing!"),
                        open ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            }
            case "reload" -> {
                if (!admin(s)) return true;
                plugin.reloadConfig();
                msg(s, "Config reloaded (break-hits applies to newly placed props on restart).", NamedTextColor.GREEN);
            }
            default -> {
                msg(s, "秘宝 Hihou - chests, keys, lucky draws and treasure maps.", NamedTextColor.GOLD);
                msg(s, "/ih give <type> [player] [amount] - place a chest item", NamedTextColor.GRAY);
                msg(s, "/ih key <type> [player] [amount] - mint keys (console: vote rewards hook)", NamedTextColor.GRAY);
                msg(s, "/ih edit | remove | map - look at a chest", NamedTextColor.GRAY);
                msg(s, "/ih event open|close - the event-chest window", NamedTextColor.GRAY);
            }
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("hihou.admin")) return true;
        msg(s, "You don't have permission for that.", NamedTextColor.RED);
        return false;
    }

    private static void msg(CommandSender s, String t, NamedTextColor c) {
        s.sendMessage(Component.text(t, c));
    }

    private static int parseInt(String s, int def) {
        try {
            return Math.max(1, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("give", "key", "edit", "remove", "map", "event", "reload", "help"));
        else if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("key"))) out.addAll(List.of(Keys.TYPES));
        else if (a.length == 2 && a[0].equalsIgnoreCase("event")) out.addAll(List.of("open", "close"));
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(prefix));
        return out;
    }
}
