package com.iekcorbar.kitchen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /kitchen help | list | give | take &lt;player&gt; &lt;item&gt; [amount] | info | reload */
final class KitchenCommand implements TabExecutor {
    private final KitchenPlugin plugin;

    KitchenCommand(KitchenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                for (KItem.Kind k : KItem.Kind.values()) {
                    StringBuilder sb = new StringBuilder();
                    for (KItem t : KItem.values()) if (t.kind == k) sb.append(sb.isEmpty() ? "" : ", ").append(t.id());
                    s.sendMessage(Component.text(k.name().toLowerCase(Locale.ROOT) + ": ", NamedTextColor.GOLD)
                            .append(Component.text(sb.toString(), NamedTextColor.GRAY)));
                }
            }
            case "giveall" -> {
                if (!s.hasPermission("kitchen.admin")) return noPerm(s);
                org.bukkit.entity.Player target = a.length > 1 ? org.bukkit.Bukkit.getPlayerExact(a[1]) : s instanceof org.bukkit.entity.Player self ? self : null;
                if (target == null) {
                    s.sendMessage(Component.text("/" + label + " giveall [player]", NamedTextColor.RED));
                    return true;
                }
                int n = GiveAll.give(target, new ZukanData(plugin), "Kitchen");
                s.sendMessage(Component.text("Gave " + n + " Kitchen items to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (!s.hasPermission("kitchen.admin")) return noPerm(s);
                if (a.length < 3) {
                    s.sendMessage(Component.text("/" + label + " give <player> <item> [amount]", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(a[1]);
                KItem t = KItem.byId(a[2]);
                if (target == null || t == null) {
                    s.sendMessage(Component.text(target == null ? "Player not online." : "Unknown item - see /" + label + " list", NamedTextColor.RED));
                    return true;
                }
                int n = 1;
                try {
                    if (a.length > 3) n = Math.max(1, Math.min(6400, Integer.parseInt(a[3])));
                } catch (NumberFormatException ignored) { }
                int max = t.knife() ? 1 : t.maxStack;
                for (int left = n; left > 0; left -= max) {
                    ItemStack it = plugin.items().create(t, Math.min(max, left));
                    for (ItemStack rest : target.getInventory().addItem(it).values()) target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
                s.sendMessage(Component.text("Gave " + n + " x " + t.displayName + " to " + target.getName(), NamedTextColor.GREEN));
            }
            case "take" -> {
                if (!s.hasPermission("kitchen.admin")) return noPerm(s);
                if (a.length < 3) {
                    s.sendMessage(Component.text("/" + label + " take <player> <item> [amount, all when left out]", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(a[1]);
                KItem t = KItem.byId(a[2]);
                if (target == null || t == null) {
                    s.sendMessage(Component.text(target == null ? "Player not online." : "Unknown item - see /" + label + " list", NamedTextColor.RED));
                    return true;
                }
                int want = Integer.MAX_VALUE;
                try {
                    if (a.length > 3) want = Math.max(1, Integer.parseInt(a[3]));
                } catch (NumberFormatException ignored) { }
                int taken = 0;
                ItemStack[] slots = target.getInventory().getStorageContents();
                for (int i = 0; i < slots.length && taken < want; i++) {
                    ItemStack st = slots[i];
                    if (st == null || st.isEmpty() || plugin.items().typeOf(st) != t) continue;
                    int use = Math.min(st.getAmount(), want - taken);
                    taken += use;
                    if (use >= st.getAmount()) target.getInventory().setItem(i, null);
                    else {
                        st.setAmount(st.getAmount() - use);
                        target.getInventory().setItem(i, st);
                    }
                }
                s.sendMessage(Component.text(taken == 0 ? target.getName() + " carries no " + t.displayName + "."
                        : "Took " + taken + " x " + t.displayName + " from " + target.getName(), taken == 0 ? NamedTextColor.RED : NamedTextColor.GOLD));
            }
            case "info" -> {
                if (!s.hasPermission("kitchen.admin")) return noPerm(s);
                s.sendMessage(Component.text("Loaded: " + plugin.crops().count() + " crops, " + plugin.props().count() + " props, "
                        + plugin.meals().count() + " meals; " + plugin.recipes().cuts.size() + " cutting + "
                        + plugin.recipes().cooks.size() + " cooking recipes.", NamedTextColor.GOLD));
            }
            case "reload" -> {
                if (!s.hasPermission("kitchen.admin")) return noPerm(s);
                plugin.reload();
                s.sendMessage(Component.text("Kitchen config and recipes reloaded.", NamedTextColor.GREEN));
            }
            default -> {
                s.sendMessage(Component.text("Kitchen - Japanese farming and cooking", NamedTextColor.GOLD));
                s.sendMessage(Component.text("Grow rice, soybeans, napa cabbage, green onion, daikon, tea, corn, tomato,", NamedTextColor.GRAY));
                s.sendMessage(Component.text("chili peppers and sesame; cut food on a Cutting Board with a knife, cook in a", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Cooking Pot over heat, and put meals down to share.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Full guide with all recipes: ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.docsUrl(), NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(plugin.docsUrl()))));
                s.sendMessage(Component.text("/" + label + " list - all items", NamedTextColor.GRAY));
                if (s.hasPermission("kitchen.admin"))
                    s.sendMessage(Component.text("/" + label + " give|take <player> <item> [amount] | info | reload", NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private static boolean noPerm(CommandSender s) {
        s.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String v : new String[]{"help", "list", "give", "take", "giveall", "info", "reload"}) if (v.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(v);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take"))) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take"))) {
            for (KItem t : KItem.values()) if (t.id().startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(t.id());
        }
        return out;
    }
}
