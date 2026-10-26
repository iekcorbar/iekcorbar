package com.iekcorbar.satchel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /satchel help | give | giveall | list | reload */
final class SatchelCommand implements CommandExecutor, TabCompleter {
    private final SatchelPlugin plugin;

    SatchelCommand(SatchelPlugin plugin) {
        this.plugin = plugin;
    }

    private static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!s.hasPermission("satchel.admin")) return deny(s);
                SItem t = args.length > 1 ? SItem.byId(args[1]) : null;
                if (t == null) {
                    s.sendMessage(Component.text("/satchel give <item> [player] [amount]  (see /satchel list)", NamedTextColor.GRAY));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : s instanceof Player p ? p : null;
                if (target == null) {
                    s.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                int n = 1;
                if (args.length > 3) {
                    try {
                        n = Math.max(1, Math.min(t.backpack() ? 8 : 64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                        // keep 1
                    }
                }
                if (t.backpack()) for (int i = 0; i < n; i++) give(target, plugin.items().create(t, 1));
                else give(target, plugin.items().create(t, n));
                s.sendMessage(Component.text("Gave " + n + "x " + t.displayName + " to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "giveall" -> {
                if (!s.hasPermission("satchel.admin")) return deny(s);
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : s instanceof Player p ? p : null;
                if (target == null) {
                    s.sendMessage(Component.text("/satchel giveall [player]", NamedTextColor.GRAY));
                    return true;
                }
                for (SItem t : SItem.values()) give(target, plugin.items().create(t, 1));
                s.sendMessage(Component.text("Gave one of every Satchel item (" + SItem.values().length + ") to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "list" -> {
                s.sendMessage(Component.text("Satchel items:", NamedTextColor.GOLD));
                for (SItem t : SItem.values()) {
                    String extra = t.backpack() ? " (" + t.storage() + " slots, " + t.upgradeSlots + " upgrades)" : "";
                    s.sendMessage(Component.text(" " + t.id() + " - " + t.displayName + extra, NamedTextColor.GRAY));
                }
            }
            case "reload" -> {
                if (!s.hasPermission("satchel.admin")) return deny(s);
                plugin.reload();
                s.sendMessage(Component.text("Satchel reloaded.", NamedTextColor.GREEN));
            }
            case "importbackpacks" -> plugin.backpackImport().run(s, args);
            default -> {
                s.sendMessage(Component.text("Satchel - backpacks", NamedTextColor.GOLD));
                s.sendMessage(Component.text("Right-click a backpack to open it. Craft the next tier from the old backpack to keep everything in it.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Upgrades go in the bottom row of the backpack.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Guide: " + plugin.docsUrl(), NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(plugin.docsUrl())));
            }
        }
        return true;
    }

    private static boolean deny(CommandSender s) {
        s.sendMessage(Component.text("No permission.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String a = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String o : new String[]{"help", "list", "give", "giveall", "reload", "importbackpacks"}) if (o.startsWith(a)) out.add(o);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (SItem t : SItem.values()) if (t.id().startsWith(a)) out.add(t.id());
        } else if ((args.length == 2 && args[0].equalsIgnoreCase("giveall")) || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a)) out.add(p.getName());
        }
        return out;
    }
}
