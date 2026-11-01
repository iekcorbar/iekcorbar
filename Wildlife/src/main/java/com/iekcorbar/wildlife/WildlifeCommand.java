package com.iekcorbar.wildlife;

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
import java.util.Set;

/** /wildlife [help] | journal | spawn &lt;species&gt; [rare] [baby] | egg &lt;species&gt; [player] | give &lt;player&gt; &lt;journal|crab_claw|lizard_tail&gt; | info | reload */
final class WildlifeCommand implements TabExecutor {
    private final WildlifePlugin plugin;

    WildlifeCommand(WildlifePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? (s instanceof Player ? "menu" : "help") : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (s instanceof Player p && p.hasPermission("wildlife.use")) plugin.menu().open(p);
                else s.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
            }
            case "journal", "stats", "list", "find", "tamed" -> {
                if (!s.hasPermission("wildlife.use")) {
                    s.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
                    return true;
                }
                switch (sub) {
                    case "journal" -> { if (s instanceof Player p) plugin.journal().open(p); }
                    case "stats" -> {
                        Player target = (s instanceof Player self) ? self : (a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null);
                        if (target == null) {
                            s.sendMessage(Component.text("/" + label + " stats [player]", NamedTextColor.RED));
                            return true;
                        }
                        Set<String> seen = plugin.journal().seen(target, false);
                        Set<String> rares = plugin.journal().seen(target, true);
                        s.sendMessage(Component.text("Wildlife stats for " + target.getName(), NamedTextColor.DARK_GREEN));
                        s.sendMessage(Component.text("Species recorded: " + seen.size() + " / " + Species.journalCount(), NamedTextColor.GRAY));
                        s.sendMessage(Component.text("Rare variants: " + rares.size(), NamedTextColor.GRAY));
                        s.sendMessage(Component.text("Animals nearby: " + plugin.animals().countNear(target.getLocation(), 64, null), NamedTextColor.GRAY));
                        if (s.hasPermission("wildlife.admin"))
                            s.sendMessage(Component.text("Total loaded: " + plugin.animals().count(), NamedTextColor.GRAY));
                    }
                    case "list" -> {
                        String filter = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                        s.sendMessage(Component.text("Wildlife species:", NamedTextColor.DARK_GREEN));
                        for (Species sp : Species.values()) {
                            if (!sp.animal()) continue;
                            if (!filter.isEmpty() && !sp.displayName.toLowerCase(Locale.ROOT).contains(filter) && !sp.id().contains(filter)) continue;
                            String biomeStr = String.join(", ", sp.biomes);
                            s.sendMessage(Component.text("  " + sp.displayName + " (" + sp.id() + ") - " + biomeStr, NamedTextColor.GRAY));
                        }
                    }
                    case "find" -> {
                        if (!(s instanceof Player p)) {
                            s.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                            return true;
                        }
                        Species sp = a.length > 1 ? Species.byId(a[1]) : null;
                        if (sp == null) {
                            s.sendMessage(Component.text("/" + label + " find <species>", NamedTextColor.RED));
                            return true;
                        }
                        int count = plugin.animals().countNear(p.getLocation(), 128, sp);
                        if (count == 0) {
                            s.sendMessage(Component.text("No " + sp.displayName + " found within 128 blocks.", NamedTextColor.GRAY));
                        } else {
                            s.sendMessage(Component.text(count + " " + sp.displayName + "(s) within 128 blocks.", NamedTextColor.GREEN));
                        }
                    }
                    case "tamed" -> {
                        if (!(s instanceof Player p)) {
                            s.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                            return true;
                        }
                        int n = 0;
                        for (Animals.Animal an : plugin.animals().all()) {
                            if (an.species == Species.DRAGON && plugin.dragon().isTamedBy(an, p)) {
                                n++;
                                s.sendMessage(Component.text("  Dragon at " + an.entity.getLocation().getBlockX() + ", " + an.entity.getLocation().getBlockY() + ", " + an.entity.getLocation().getBlockZ(), NamedTextColor.GRAY));
                            }
                        }
                        if (n == 0) s.sendMessage(Component.text("You have no tamed dragons.", NamedTextColor.GRAY));
                        else s.sendMessage(Component.text("You have " + n + " tamed dragon(s).", NamedTextColor.GREEN));
                    }
                }
            }
            case "spawn" -> {
                if (!admin(s) || !(s instanceof Player p)) return true;
                Species sp = a.length > 1 ? Species.byId(a[1]) : null;
                if (sp == null) {
                    s.sendMessage(Component.text("/" + label + " spawn <species> [rare] [baby]", NamedTextColor.RED));
                    return true;
                }
                boolean rare = false, baby = false;
                for (int i = 2; i < a.length; i++) {
                    rare |= a[i].equalsIgnoreCase("rare");
                    baby |= a[i].equalsIgnoreCase("baby");
                }
                if (!sp.animal() && (rare || baby))
                    s.sendMessage(Component.text(sp.displayName + " is not an animal: no rare or baby variant - placing it as is.", NamedTextColor.GRAY));
                boolean ok = plugin.animals().spawn(sp, p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3)), rare, baby, null) != null;
                s.sendMessage(ok ? Component.text("Spawned a " + (baby ? "baby " : "") + sp.displayName + ".", NamedTextColor.GREEN)
                        : Component.text("Could not spawn: the model isn't loaded in FreeMinecraftModels (see console).", NamedTextColor.RED));
            }
            case "egg" -> {
                if (!admin(s)) return true;
                Species sp = a.length > 1 ? Species.byId(a[1]) : null;
                // /wildlife egg <species> [rare|<look>] [player]  (the second word is a player name when it is no variant)
                String variant = null;
                int pi = 2;
                if (sp != null && a.length > 2) {
                    for (String v : plugin.items().eggVariants(sp)) if (a[2].equalsIgnoreCase(WildItems.variantShort(sp, v)) || a[2].equalsIgnoreCase(v)) variant = v;
                    if (variant != null) pi = 3;
                }
                Player target = a.length > pi ? Bukkit.getPlayerExact(a[pi]) : s instanceof Player self ? self : null;
                if (sp == null || target == null) {
                    s.sendMessage(Component.text("/" + label + " egg <species> [rare|<look>] [player]", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(plugin.items().egg(sp, variant));
                s.sendMessage(Component.text("Gave a " + (variant == null ? sp.displayName : sp.displayName + " (" + WildItems.variantShort(sp, variant) + ")") + " spawn egg to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "giveall" -> {
                if (!admin(s)) return true;
                Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player self ? self : null;
                if (target == null) {
                    s.sendMessage(Component.text("/" + label + " giveall [player]", NamedTextColor.RED));
                    return true;
                }
                int n = GiveAll.give(target, new ZukanData(plugin), "Wildlife");
                s.sendMessage(Component.text("Gave " + n + " Wildlife items to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (!admin(s)) return true;
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                String what = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "journal";
                ItemStack it = switch (what) {
                    case "crab_claw" -> plugin.items().crabClaw();
                    case "lizard_tail" -> plugin.items().lizardTail();
                    default -> plugin.items().journal();
                };
                if (t == null) {
                    s.sendMessage(Component.text("Player not online.", NamedTextColor.RED));
                    return true;
                }
                int n = 1;
                try { if (a.length > 3) n = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) { }
                for (int left = n; left > 0; ) {
                    ItemStack stack = it.clone();
                    int amt = Math.min(left, stack.getMaxStackSize());
                    stack.setAmount(amt);
                    for (var rest : t.getInventory().addItem(stack).values()) t.getWorld().dropItemNaturally(t.getLocation(), rest);
                    left -= amt;
                }
                s.sendMessage(Component.text("Gave " + n + " x " + what + " to " + t.getName(), NamedTextColor.GREEN));
            }
            case "info" -> {
                if (!admin(s)) return true;
                s.sendMessage(Component.text("Loaded wildlife: " + plugin.animals().count() + " animals. FreeMinecraftModels: "
                        + (plugin.fmm().available() ? "ok" : "MISSING"), NamedTextColor.GOLD));
                s.sendMessage(Component.text("Species: " + Species.values().length + " (animals: " + Species.journalCount() + ")", NamedTextColor.GRAY));
            }
            case "reload" -> {
                if (!admin(s)) return true;
                plugin.reload();
                s.sendMessage(Component.text("Wildlife config reloaded.", NamedTextColor.GREEN));
            }
            default -> {
                String url = plugin.getConfig().getString("docs-url", "https://iekcorbar.com/wiki?page=wildlife");
                s.sendMessage(Component.text("Wildlife - shy animals to find and record", NamedTextColor.DARK_GREEN));
                s.sendMessage(Component.text("Watch an animal through a spyglass for a moment to add it to your Field Journal.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Each species lives in its own biomes; rare colour variants exist too.", NamedTextColor.GRAY));
                s.sendMessage(Component.text("/" + label + " - open the Wildlife menu | /" + label + " journal - read your journal (or craft one: book + feather + seeds)", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Guide: ", NamedTextColor.GRAY).append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));
                if (s.hasPermission("wildlife.admin"))
                    s.sendMessage(Component.text("/" + label + " spawn <species> [rare] [baby] | egg <species> [player] | give <player> <item> [amount] | info | reload | remove <radius>", NamedTextColor.GRAY));
                s.sendMessage(Component.text("/" + label + " stats [player] | list [filter] | find <species> | tamed", NamedTextColor.GRAY));
                s.sendMessage(Component.text("Feed an adult its food to breed it; a golden dandelion keeps a baby small.", NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private static boolean admin(CommandSender s) {
        if (s.hasPermission("wildlife.admin")) return true;
        s.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String v : new String[]{"help", "menu", "journal", "spawn", "egg", "give", "giveall", "info", "reload", "stats", "list", "find", "tamed", "remove"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("spawn") || a[0].equalsIgnoreCase("egg") || a[0].equalsIgnoreCase("find"))) {
            boolean egg = a[0].equalsIgnoreCase("egg");
            for (Species sp : Species.values()) if (sp.id().startsWith(last)) out.add(sp.id());
        } else if (a.length >= 3 && a[0].equalsIgnoreCase("spawn")) {
            Species sp = Species.byId(a[1]);
            if (sp == null || sp.animal()) {   // custom creatures take no rare/baby
                out.add("rare");
                out.add("baby");
            }
        } else if (a.length >= 3 && a[0].equalsIgnoreCase("egg")) {
            Species sp = Species.byId(a[1]);
            if (a.length == 3 && sp != null) for (String v : plugin.items().eggVariants(sp)) if (WildItems.variantShort(sp, v).startsWith(last)) out.add(WildItems.variantShort(sp, v));
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (String v : new String[]{"journal", "crab_claw", "lizard_tail"}) if (v.startsWith(last)) out.add(v);
        }
        return out;
    }
}
