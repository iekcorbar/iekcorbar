package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.iekcorbar.steamworks.trains.CarType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /steamworks give|info|list|reload */
final class SteamworksCommand implements TabExecutor {
    private final SteamworksPlugin plugin;

    SteamworksCommand(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                if (!(s instanceof Player p)) return msg(s, "Players only.");
                Block b = p.getTargetBlockExact(8);
                KNode n = plugin.kinetics().at(b);
                if (n == null) return msg(s, "Look at a Steamworks part.");
                s.sendMessage(plugin.info(n));
                Network net = n.network;
                if (net != null) {
                    msg(s, String.format("Network: %d parts, root speed %.2f RPM, ratio here %.3g, axis %s",
                            net.nodes.size(), net.ref, n.ratio, n.axis));
                }
            }
            case "help" -> s.sendMessage(Component.text("Steamworks guide (machines, trains, oil, Karakuri Arm language): ", NamedTextColor.GOLD)
                    .append(plugin.arms().docsLink(plugin.settings().docsUrl)));
            case "list" -> {
                List<String> ids = new ArrayList<>();
                for (PartType t : PartType.values()) ids.add(t.id());
                msg(s, "Parts: " + String.join(", ", ids));
                ids.clear();
                for (MaterialType t : MaterialType.values()) ids.add(t.id());
                msg(s, "Materials: " + String.join(", ", ids));
                ids.clear();
                for (CarType t : CarType.values()) ids.add(t.id());
                msg(s, "Train cars: " + String.join(", ", ids));
                ids.clear();
                for (OreType t : OreType.values()) ids.add(t.id());
                msg(s, "Ore blocks: " + String.join(", ", ids));
                ids.clear();
                for (DecorType t : DecorType.values()) ids.add(t.id());
                msg(s, "Building blocks: " + String.join(", ", ids));
            }
            case "giveall" -> {
                if (!s.hasPermission("steamworks.admin")) return msg(s, "No permission.");
                Player target = a.length > 1 ? plugin.getServer().getPlayerExact(a[1]) : s instanceof Player self ? self : null;
                if (target == null) return msg(s, "/" + label + " giveall [player]");
                int n = GiveAll.give(target, new ZukanData(plugin), "Steamworks");
                return msg(s, "Gave " + n + " Steamworks items to " + target.getName() + ".");
            }
            case "give" -> {
                if (!s.hasPermission("steamworks.admin")) return msg(s, "No permission.");
                if (a.length < 3) return msg(s, "/" + label + " give <player> <part> [amount]");
                Player target = plugin.getServer().getPlayerExact(a[1]);
                PartType t = PartType.byId(a[2]);
                MaterialType mt = t == null ? MaterialType.byId(a[2]) : null;
                CarType ct = t == null && mt == null ? CarType.byId(a[2]) : null;
                OreType ot = t == null && mt == null && ct == null ? OreType.byId(a[2]) : null;
                DecorType dt = t == null && mt == null && ct == null && ot == null ? DecorType.byId(a[2]) : null;
                boolean shape = t == null && mt == null && ct == null && ot == null && dt == null && plugin.shapes().itemById(a[2], 1) != null;
                if (target == null) return msg(s, "Player not online.");
                if (t == null && mt == null && ct == null && ot == null && dt == null && !shape) return msg(s, "Unknown part. /" + label + " list");
                int amount = 1;
                if (a.length > 3) {
                    try {
                        amount = Math.max(1, Math.min(64 * 36, Integer.parseInt(a[3])));
                    } catch (NumberFormatException e) {
                        return msg(s, "Amount must be a number.");
                    }
                }
                while (amount > 0) {
                    int n = Math.min(64, amount);
                    amount -= n;
                    if (ct != null) n = 1; // cars don't stack
                    var item = t != null ? plugin.items().create(t, n) : mt != null ? plugin.items().createMaterial(mt, n)
                            : ot != null ? plugin.ores().item(ot, n) : dt != null ? plugin.decor().item(dt, n)
                            : shape ? plugin.shapes().itemById(a[2], n) : plugin.items().createCar(ct, 1);
                    for (var left : target.getInventory().addItem(item).values())
                        target.getWorld().dropItemNaturally(target.getLocation(), left);
                }
                msg(s, "Gave " + (t != null ? t.displayName : mt != null ? mt.displayName : ot != null ? ot.displayName : dt != null ? dt.displayName : shape ? a[2] : ct.displayName) + " to " + target.getName() + ".");
            }
            case "manual" -> {
                if (!(s instanceof Player p)) return msg(s, "Players only.");
                ItemStack book = plugin.manual().create(p);
                for (var left : p.getInventory().addItem(book).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.openBook(book);
            }
            case "reload" -> {
                if (!s.hasPermission("steamworks.admin")) return msg(s, "No permission.");
                plugin.reload();
                msg(s, "Steamworks config and machines.yml reloaded.");
            }
            default -> msg(s, "/" + label + " help | info | list" + (s.hasPermission("steamworks.admin") ? " | give <player> <part> [amount] | reload" : ""));
        }
        return true;
    }

    private static boolean msg(CommandSender s, String text) {
        s.sendMessage(Component.text(text, NamedTextColor.GOLD));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : s.hasPermission("steamworks.admin") ? List.of("help", "info", "list", "manual", "give", "giveall", "reload") : List.of("help", "info", "list", "manual"))
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : plugin.getServer().getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (PartType t : PartType.values()) if (t.id().startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(t.id());
            for (MaterialType t : MaterialType.values()) if (t.id().startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(t.id());
            for (CarType t : CarType.values()) if (t.id().startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(t.id());
            for (OreType t : OreType.values()) if (t.id().startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(t.id());
            for (DecorType t : DecorType.values()) {
                for (String v : new String[]{t.id(), t.id() + "_stairs", t.id() + "_slab"})
                    if (v.startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(v);
            }
        }
        return out;
    }
}
