package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Relics: placeable props from the lore of Iekcorbar. Collection 1,
 * "Shoutlander's Shadow" (Halloween 2026), uses only the known history of the
 * Story Room lore (Base): Shoutlander laid the world waste, his creatures stayed
 * among the ruins, the First Settlement was destroyed and its survivors fled to
 * the Guild. Nothing that later chapters are meant to reveal is hinted at.
 */
public final class RelicsPlugin extends JavaPlugin implements TabExecutor {
    /** Our own protection probe - ignored by our break handler. */
    static final class ProbeBreakEvent extends BlockBreakEvent {
        ProbeBreakEvent(Block block, Player player) {
            super(block, player);
        }
    }

    long tick;
    /** Time zone for Advent, the advent calendar and the New Year countdown (config: timezone). */
    static java.time.ZoneId zone = java.time.ZoneId.systemDefault();
    private Relics relics;
    private Treats treats;
    private Discs discs;
    private Adv adv;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            zone = java.time.ZoneId.of(getConfig().getString("timezone", "Europe/Amsterdam"));
        } catch (Exception e) {
            getLogger().warning("Unknown timezone '" + getConfig().getString("timezone") + "' - using the server's own (" + zone + ").");
        }
        treats = new Treats(this);
        relics = new Relics(this);
        getServer().getPluginManager().registerEvents(relics, this);
        getServer().getPluginManager().registerEvents(treats, this);
        getServer().getPluginManager().registerEvents(relics.jukebox(), this);
        getServer().getPluginManager().registerEvents(relics.giftBoxes(), this);
        relics.registerRecipes();
        discs = new Discs(this);
        getServer().getPluginManager().registerEvents(discs, this);
        discs.registerRecipes();
        discs.installDatapack();
        adv = Achievements.create(this);
        adv.start();
        getServer().getServicesManager().register(java.util.function.Supplier.class, new ZukanData(this, relics), this, org.bukkit.plugin.ServicePriority.Normal); // recipe viewer (Zukan)
        getCommand("relics").setExecutor(this);
        getCommand("relics").setTabCompleter(this);
        getServer().getScheduler().runTask(this, () -> {
            for (World w : getServer().getWorlds()) for (Chunk c : w.getLoadedChunks()) relics.loadChunk(c);
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 10;
            relics.tick(tick);
            if (tick % 20 == 0) relics.newYearTick();
        }, 10L, 10L);
        getLogger().info("Relics enabled (" + RelicType.values().length + " relics).");
    }

    @Override
    public void onDisable() {
        if (relics != null && !getServer().isStopping()) relics.removeAllDisplays(); // vanish with the server anyway
        if (discs != null && !getServer().isStopping()) discs.unregisterRecipes();
    }

    Adv adv() {
        return adv;
    }

    Relics relics() {
        return relics;
    }

    Treats treats() {
        return treats;
    }

    Discs discs() {
        return discs;
    }

    /** An item spec from the config: "relics:&lt;relic or treat id&gt; [amount]" or "&lt;MATERIAL&gt; [amount]". */
    org.bukkit.inventory.ItemStack reward(String spec) {
        String[] p = spec.trim().split("\\s+");
        int n = p.length > 1 ? Integer.parseInt(p[1]) : 1;
        String id = p[0].toLowerCase(Locale.ROOT);
        if (id.startsWith("relics:")) {
            RelicType r = RelicType.byId(id.substring(7));
            if (r != null) return relics.item(r, n);
            for (Treats.Treat t : Treats.Treat.values()) if (t.id().equals(id.substring(7))) return treats.item(t, n);
            return null;
        }
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(p[0]);
        return m == null ? null : new org.bukkit.inventory.ItemStack(m, n);
    }

    boolean canBreak(Player p, Block b) {
        ProbeBreakEvent ev = new ProbeBreakEvent(b, p);
        getServer().getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    /** Staff permission check with the standard message. */
    private boolean denied(CommandSender s, String permission) {
        if (s.hasPermission(permission)) return false;
        s.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("giveall")) {
            if (denied(s, "relics.give")) return true;
            Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player self ? self : null;
            if (target == null) {
                s.sendMessage(Component.text("/" + label + " giveall [player]", NamedTextColor.RED));
                return true;
            }
            int n = GiveAll.give(target, new ZukanData(this, relics), "Relics");
            s.sendMessage(Component.text("Gave " + n + " relics and treats to " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("disc")) {
            String what = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
            if (what.equals("give")) {
                if (denied(s, "relics.give")) return true;
                Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player self ? self : null;
                String id = a.length > 3 ? a[3] : "all";
                if (t == null || (!id.equalsIgnoreCase("all") && discs.byId(id) == null)) {
                    s.sendMessage(Component.text("/" + label + " disc give <player> <disc|all>", NamedTextColor.RED));
                    return true;
                }
                int n = 0;
                for (Discs.Disc d : discs.discs.values()) if (id.equalsIgnoreCase("all") || d.id().equalsIgnoreCase(id)) {
                    n++;
                    for (var left : t.getInventory().addItem(discs.item(d)).values()) t.getWorld().dropItemNaturally(t.getLocation(), left);
                }
                s.sendMessage(Component.text("Gave " + n + " music disc(s) to " + t.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            s.sendMessage(Component.text("Music discs (play them in a jukebox)", NamedTextColor.GOLD));
            for (Discs.Disc d : discs.discs.values())
                s.sendMessage(Component.text(" - " + d.title(), NamedTextColor.GRAY).append(Component.text("  " + d.id() + ", " + d.seconds() + " s"
                        + (d.recipe() == null ? "" : ", craft: note block + paper + black dye + " + d.recipe().name().toLowerCase(Locale.ROOT).replace('_', ' ')), NamedTextColor.DARK_GRAY)));
            s.sendMessage(Component.text("Staff: /" + label + " disc give <player> <disc|all>", NamedTextColor.DARK_GRAY));
            return true;
        }
        if (sub.equals("remove")) {
            if (denied(s, "relics.build")) return true;
            if (!(s instanceof Player self)) {
                s.sendMessage("Players only.");
                return true;
            }
            if (a.length > 1 && a[1].equalsIgnoreCase("target")) {   // the classic staff chore: remove the one thing across the street
                Relics.Relic r = relics.lookingAt(self, 24);
                if (r == null) {
                    s.sendMessage(Component.text("You are not looking at a relic (within 24 blocks).", NamedTextColor.GRAY));
                    return true;
                }
                relics.breakRelic(r, true);
                s.sendMessage(Component.text("Removed " + r.type.displayName + " at " + r.x + ", " + r.y + ", " + r.z + " (dropped as an item).", NamedTextColor.GREEN));
                return true;
            }
            double radius = 3;
            try {
                if (a.length > 1) radius = Math.max(1, Math.min(10, Double.parseDouble(a[1])));
            } catch (NumberFormatException ignored) { }
            int n = relics.removeNear(self.getLocation(), radius);
            s.sendMessage(Component.text("Removed " + n + " relic(s) within " + (int) radius + " blocks.", n > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            return true;
        }
        if (sub.equals("random")) {
            if (denied(s, "relics.give")) return true;
            Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player p ? p : null;
            if (target == null) {
                s.sendMessage(Component.text("Player not found: " + a[1], NamedTextColor.RED));
                return true;
            }
            String tier = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "common";
            List<String> ids = getConfig().getStringList("random." + tier);
            if (ids.isEmpty()) ids = getConfig().getStringList("random.common");
            List<RelicType> pool = new ArrayList<>();
            for (String id : ids) {
                RelicType t = RelicType.byId(id);
                // windowed and staff-y items never come out of a dip
                if (t != null && t.use != RelicType.Use.GIFT && t.use != RelicType.Use.CALENDAR
                        && t != RelicType.EASTER_EGG && t != RelicType.COUNTDOWN_BOARD) pool.add(t);
            }
            if (pool.isEmpty()) {
                s.sendMessage(Component.text("No relics configured for tier '" + tier + "' (config: random.).", NamedTextColor.RED));
                return true;
            }
            RelicType pick = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
            for (var left : target.getInventory().addItem(relics.item(pick, 1)).values()) target.getWorld().dropItemNaturally(target.getLocation(), left);
            s.sendMessage(Component.text("Rolled " + pick.displayName + " (" + tier + ") for " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Lucky dip: a " + pick.displayName + " falls into your bag!", NamedTextColor.GOLD));
            target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            return true;
        }
        if (sub.equals("protect")) {
            if (denied(s, "relics.admin")) return true;
            if (!(s instanceof Player self)) {
                s.sendMessage("Players only.");
                return true;
            }
            Relics.Relic r = relics.lookingAt(self, 24);
            if (r == null) {
                s.sendMessage(Component.text("You are not looking at a relic (within 24 blocks).", NamedTextColor.GRAY));
                return true;
            }
            r.staffPlaced = a.length == 1 || !a[1].equalsIgnoreCase("off");
            relics.save(r);
            s.sendMessage(Component.text(r.type.displayName + (r.staffPlaced ? " is now staff-protected - players cannot break it." : " is breakable again (by whoever placed it)."),
                    r.staffPlaced ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            return true;
        }
        if (sub.equals("where")) {
            if (denied(s, "relics.build")) return true;
            Player target = s instanceof Player self ? self : null;
            int from = 1;
            if (a.length > 1) {
                Player t2 = Bukkit.getPlayerExact(a[1]);
                if (t2 != null) {
                    target = t2;
                    from = 2;
                }
            }
            if (target == null) {
                s.sendMessage(Component.text("/" + label + " where <player> [radius]", NamedTextColor.RED));
                return true;
            }
            double radius = 48;
            try {
                if (a.length > from) radius = Math.max(2, Math.min(256, Double.parseDouble(a[from])));
            } catch (NumberFormatException ignored) { }
            List<Relics.Relic> found = relics.near(target.getLocation(), radius);
            s.sendMessage(Component.text("Relics within " + (int) radius + " blocks of " + target.getName() + ": " + found.size(), NamedTextColor.GOLD));
            int shown = 0;
            for (Relics.Relic r : found) {
                if (shown == 40) {
                    s.sendMessage(Component.text("  ... and " + (found.size() - shown) + " more (smaller radius shows less).", NamedTextColor.GRAY));
                    break;
                }
                shown++;
                Component line = Component.text(" " + r.type.displayName + " ", NamedTextColor.YELLOW)
                        .append(Component.text(r.world.getName() + " " + r.x + ", " + r.y + ", " + r.z + " (" + r.mount + ") - "
                                + (int) Math.round(r.center().distance(target.getLocation())) + " blocks", NamedTextColor.GRAY));
                if (r.owner != null) line = line.append(Component.text(" - by " + Bukkit.getOfflinePlayer(r.owner).getName(), NamedTextColor.DARK_GRAY));
                if (r.type == RelicType.GIFT_BOX) line = line.append(Component.text("  [" + Gifts.status(r) + "]", NamedTextColor.LIGHT_PURPLE));
                s.sendMessage(line);
            }
            return true;
        }
        if (sub.equals("info")) {
            if (denied(s, "relics.build")) return true;
            if (!(s instanceof Player self)) {
                s.sendMessage("Players only.");
                return true;
            }
            Relics.Relic r = relics.lookingAt(self, 24);
            if (r == null) {
                s.sendMessage(Component.text("You are not looking at a relic (within 24 blocks).", NamedTextColor.GRAY));
                return true;
            }
            s.sendMessage(Component.text(r.type.displayName, NamedTextColor.GOLD)
                    .append(Component.text("  " + r.type.id() + "  (" + r.type.collection.title + ")", NamedTextColor.GRAY)));
            s.sendMessage(Component.text(" " + r.world.getName() + " " + r.x + ", " + r.y + ", " + r.z
                    + " - " + r.mount + ", facing " + r.facing.name().toLowerCase(Locale.ROOT), NamedTextColor.GRAY));
            if (r.owner != null) s.sendMessage(Component.text(" Placed by " + Bukkit.getOfflinePlayer(r.owner).getName(), NamedTextColor.GRAY));
            if (r.type == RelicType.COUNTDOWN_BOARD) s.sendMessage(Component.text(" Counts down to: " + relics.boards().describeZone(r), NamedTextColor.AQUA));
            if (Jukebox.isMusicProp(r)) s.sendMessage(Component.text(" " + relics.jukebox().state(r), NamedTextColor.AQUA));
            if (r.type == RelicType.GIFT_BOX) s.sendMessage(Component.text(" " + Gifts.status(r), NamedTextColor.LIGHT_PURPLE));
            if (r.type == RelicType.ADVENT_WREATH && r.candles >= 0) s.sendMessage(Component.text(" " + r.candles + " candle(s) lit", NamedTextColor.GRAY));
            return true;
        }
        if (sub.equals("board")) {
            if (denied(s, "relics.build")) return true;
            String what = a.length > 1 ? a[1] : "list";
            if (what.equalsIgnoreCase("list")) {
                List<Relics.Relic> bs = new ArrayList<>();
                for (Relics.Relic r : relics.all()) if (r.type == RelicType.COUNTDOWN_BOARD) bs.add(r);
                s.sendMessage(Component.text("Countdown boards: " + bs.size(), NamedTextColor.GOLD));
                for (Relics.Relic r : bs)
                    s.sendMessage(Component.text(" " + r.world.getName() + " " + r.x + ", " + r.y + ", " + r.z + " - ", NamedTextColor.YELLOW)
                            .append(Component.text(relics.boards().describeZone(r), NamedTextColor.GRAY)));
                s.sendMessage(Component.text(" Set one: /" + label + " board <world|zone|number> [player] - the board you look at, or else the nearest within 64 blocks.", NamedTextColor.DARK_GRAY));
                return true;
            }
            int z = relics.boards().zoneArg(what);
            if (z < 0) {
                StringBuilder zs = new StringBuilder();
                List<NewYearBoards.Zone> zones = relics.boards().zoneList();
                for (int i = 0; i < zones.size(); i++) zs.append(zs.isEmpty() ? "" : ", ").append(i + 1).append("=").append(zones.get(i).label());
                s.sendMessage(Component.text("Unknown zone '" + what + "'.", NamedTextColor.RED));
                s.sendMessage(Component.text(" Zones: world, " + zs, NamedTextColor.GRAY));
                return true;
            }
            Player target = s instanceof Player self ? self : null;
            if (a.length > 2) {
                Player t2 = Bukkit.getPlayerExact(a[2]);
                if (t2 != null) target = t2;
            }
            if (target == null) {
                s.sendMessage(Component.text("/" + label + " board <world|zone|number> <player>", NamedTextColor.RED));
                return true;
            }
            Relics.Relic r = relics.lookingAt(target, 24);
            if (r == null || r.type != RelicType.COUNTDOWN_BOARD) {
                r = null;
                for (Relics.Relic one : relics.near(target.getLocation(), 64)) if (one.type == RelicType.COUNTDOWN_BOARD) {
                    r = one;
                    break;
                }
            }
            if (r == null) {
                s.sendMessage(Component.text("No countdown board in view or within 64 blocks of " + target.getName() + ".", NamedTextColor.GRAY));
                return true;
            }
            r.count = z;
            r.last = new String[3];
            relics.save(r);
            s.sendMessage(Component.text("Board at " + r.world.getName() + " " + r.x + ", " + r.y + ", " + r.z + " now counts down to ", NamedTextColor.GREEN)
                    .append(Component.text(relics.boards().describeZone(r), NamedTextColor.AQUA)));
            return true;
        }
        if (sub.equals("music")) {
            if (denied(s, "relics.build")) return true;
            if (!(s instanceof Player self)) {
                s.sendMessage("Players only.");
                return true;
            }
            Jukebox jk = relics.jukebox();
            String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
            if (act.equals("list")) {
                double radius = 48;
                try {
                    if (a.length > 2) radius = Math.max(2, Math.min(256, Double.parseDouble(a[2])));
                } catch (NumberFormatException ignored) { }
                int n = 0;
                for (Relics.Relic r : relics.near(self.getLocation(), radius)) {
                    if (!Jukebox.isMusicProp(r)) continue;
                    n++;
                    s.sendMessage(Component.text(" " + r.type.displayName + " ", NamedTextColor.YELLOW)
                            .append(Component.text(r.x + ", " + r.y + ", " + r.z + " - " + jk.state(r), NamedTextColor.GRAY)));
                }
                if (n == 0) s.sendMessage(Component.text("No music props within " + (int) radius + " blocks.", NamedTextColor.GRAY));
                else s.sendMessage(Component.text("Look at one and: /" + label + " music <play|pause|skip|shuffle>", NamedTextColor.DARK_GRAY));
                return true;
            }
            Relics.Relic r = relics.lookingAt(self, 24);
            if (r == null || !Jukebox.isMusicProp(r)) {
                s.sendMessage(Component.text("Look at a music prop (music box, harp, xylophone, gramophone).", NamedTextColor.GRAY));
                return true;
            }
            switch (act) {
                case "play" -> {
                    jk.play(r);
                    s.sendMessage(Component.text(r.type.displayName + " - " + jk.state(r), NamedTextColor.GREEN));
                }
                case "pause" -> {
                    jk.stop(r);
                    s.sendMessage(Component.text(r.type.displayName + " - " + jk.state(r), NamedTextColor.GRAY));
                }
                case "skip" -> {
                    jk.skip(r);
                    s.sendMessage(Component.text(r.type.displayName + " - " + jk.state(r), NamedTextColor.GREEN));
                }
                case "shuffle" -> {
                    jk.toggleShuffle(r);
                    s.sendMessage(Component.text(r.type.displayName + " - " + jk.state(r), NamedTextColor.GREEN));
                }
                default -> s.sendMessage(Component.text("/" + label + " music <play|pause|skip|shuffle|list> [radius]", NamedTextColor.RED));
            }
            return true;
        }
        if (sub.equals("gift")) {
            if (denied(s, "relics.build")) return true;
            String what = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
            if (what.equals("find")) {   // every gift box addressed to this player - locked for them, or still being wrapped by them
                Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player self ? self : null;
                if (target == null) {
                    s.sendMessage(Component.text("/" + label + " gift find <player> [radius]", NamedTextColor.RED));
                    return true;
                }
                double radius = 128;
                try {
                    if (a.length > 3) radius = Math.max(16, Math.min(512, Double.parseDouble(a[3])));
                } catch (NumberFormatException ignored) { }
                UUID t = target.getUniqueId();
                int n = 0;
                for (Relics.Relic r : relics.near(target.getLocation(), radius)) {
                    if (r.type != RelicType.GIFT_BOX) continue;
                    if (!t.equals(r.recipient) && !(!r.locked && t.equals(r.owner))) continue;
                    n++;
                    s.sendMessage(Component.text(" Gift Box ", NamedTextColor.YELLOW)
                            .append(Component.text(r.world.getName() + " " + r.x + ", " + r.y + ", " + r.z + " - " + Gifts.status(r), NamedTextColor.GRAY)));
                }
                if (n == 0) s.sendMessage(Component.text("No gift boxes addressed to " + target.getName() + " within " + (int) radius + " blocks.", NamedTextColor.GRAY));
                return true;
            }
            if (what.equals("rellock")) {   // staff fixes a typo: new recipient, contents untouched, the box stays locked
                if (!(s instanceof Player self)) {
                    s.sendMessage("Players only.");
                    return true;
                }
                if (a.length < 3) {
                    s.sendMessage(Component.text("/" + label + " gift rellock <new recipient>", NamedTextColor.RED));
                    return true;
                }
                Relics.Relic r = relics.lookingAt(self, 24);
                if (r == null || r.type != RelicType.GIFT_BOX) {
                    s.sendMessage(Component.text("Look at a gift box.", NamedTextColor.GRAY));
                    return true;
                }
                if (!r.locked) {
                    s.sendMessage(Component.text("This box is not locked yet - the wrapper is still filling it.", NamedTextColor.GRAY));
                    return true;
                }
                UUID who = Gifts.findByName(a[2]);
                if (who == null) {
                    s.sendMessage(Component.text("No player named '" + a[2] + "' is known to this server.", NamedTextColor.RED));
                    return true;
                }
                if (who.equals(r.owner)) {
                    s.sendMessage(Component.text("A present cannot be locked on the one who wrapped it.", NamedTextColor.RED));
                    return true;
                }
                String was = Gifts.name(r.recipient);
                r.recipient = who;
                relics.save(r);
                s.sendMessage(Component.text("Box at " + r.world.getName() + " " + r.x + ", " + r.y + ", " + r.z
                        + " now locked for " + Gifts.name(who) + " (was " + was + ") - contents untouched.", NamedTextColor.GREEN));
                return true;
            }
            s.sendMessage(Component.text("/" + label + " gift <find <player> [radius] | rellock <new recipient>>", NamedTextColor.RED));
            return true;
        }
        if (sub.equals("secret")) {   // Secret Santa: draw lots among the named players, everyone gets a fresh box and a private whisper
            if (denied(s, "relics.give")) return true;
            List<Player> players = new ArrayList<>();
            for (int i = 1; i < a.length; i++) {
                Player t = Bukkit.getPlayerExact(a[i]);
                if (t == null) {
                    s.sendMessage(Component.text(a[i] + " is not online - Secret Santa hands the boxes out live.", NamedTextColor.RED));
                    return true;
                }
                if (!players.contains(t)) players.add(t);
            }
            if (players.size() < 2) {
                s.sendMessage(Component.text("/" + label + " secret <player> <player> [more...]", NamedTextColor.RED));
                return true;
            }
            List<Player> draw = new ArrayList<>(players);
            java.util.Collections.shuffle(draw);
            for (int i = 0; i < players.size(); i++) {   // nobody draws themselves: every fixed point swaps with the next seat
                if (draw.get(i) != players.get(i)) continue;
                int k = (i + 1) % draw.size();
                Player tmp = draw.set(i, draw.get(k));
                draw.set(k, tmp);
            }
            for (int i = 0; i < players.size(); i++) {
                Player giver = players.get(i), lot = draw.get(i);
                ItemStack box = relics.item(RelicType.GIFT_BOX, 1);
                for (var left : giver.getInventory().addItem(box).values()) giver.getWorld().dropItemNaturally(giver.getLocation(), left);
                giver.sendMessage(Component.text("Secret Santa: you wrap a present for " + lot.getName()
                        + " - fill a box somewhere, lock it on them, and only they can open it when the gift window is open ("
                        + relics.giftBoxes().window() + ").", NamedTextColor.GOLD));
                giver.playSound(giver.getLocation(), "minecraft:block.note_block.bell", 1f, 1.2f);
            }
            s.sendMessage(Component.text("Drew lots for " + players.size() + " players: everyone got a fresh gift box and a private whisper of their secret name.", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("give")) {
            if (denied(s, "relics.give")) return true;
            Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
            if (t != null && a.length > 2 && reward("relics:" + a[2]) != null && RelicType.byId(a[2]) == null) {   // a treat
                t.getInventory().addItem(reward("relics:" + a[2] + " " + (a.length > 3 ? a[3] : "1")));
                s.sendMessage(Component.text("Gave " + a[2] + " to " + t.getName(), NamedTextColor.GREEN));
                return true;
            }
            RelicType r = a.length > 2 ? RelicType.byId(a[2]) : null;
            RelicType.Collection grp = a.length > 2 && r == null ? RelicType.Collection.byId(a[2]) : null;
            if (t != null && grp != null) {
                int count = 0;
                for (RelicType one : RelicType.values()) if (one.collection == grp) {
                    count++;
                    for (var left : t.getInventory().addItem(relics.item(one, 1)).values()) t.getWorld().dropItemNaturally(t.getLocation(), left);
                }
                s.sendMessage(Component.text("Gave " + count + " relics of " + grp.title + " to " + t.getName(), NamedTextColor.GREEN));
                return true;
            }
            if (t == null || (r == null && !(a.length > 2 && a[2].equalsIgnoreCase("all")))) {
                s.sendMessage(Component.text("/" + label + " give <player> <relic|group|all> [amount]", NamedTextColor.RED));
                return true;
            }
            int n = 1;
            try {
                if (a.length > 3) n = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException ignored) { }
            for (RelicType one : r == null ? RelicType.values() : new RelicType[]{r})
                for (var left : t.getInventory().addItem(relics.item(one, n)).values()) t.getWorld().dropItemNaturally(t.getLocation(), left);
            s.sendMessage(Component.text("Gave " + (r == null ? "every relic" : r.displayName) + " to " + t.getName(), NamedTextColor.GREEN));
            return true;
        }
        RelicType.Collection group = a.length > 1 ? RelicType.Collection.byId(a[1]) : sub.equals("list") ? null : RelicType.Collection.byId(sub);
        if (group != null) {
            s.sendMessage(Component.text(group.title + " (" + group.size() + ")", group == RelicType.Collection.SHADOW ? NamedTextColor.DARK_RED : NamedTextColor.GOLD));
            for (RelicType r : RelicType.values()) if (r.collection == group)
                s.sendMessage(Component.text(" - " + r.displayName, NamedTextColor.GRAY).append(Component.text("  " + r.id(), NamedTextColor.DARK_GRAY)));
            s.sendMessage(Component.text("Staff: /" + label + " give <player> " + group.id() + " gives the whole group.", NamedTextColor.DARK_GRAY));
            return true;
        }
        s.sendMessage(Component.text("Relics - groups (click one to see what is in it)", NamedTextColor.GOLD));
        for (RelicType.Collection col : RelicType.Collection.values()) {
            s.sendMessage(Component.text(" " + col.title, col == RelicType.Collection.SHADOW ? NamedTextColor.DARK_RED : NamedTextColor.YELLOW)
                    .append(Component.text("  " + col.size() + " relics", NamedTextColor.GRAY))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/" + label + " list " + col.id()))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("/" + label + " list " + col.id()))));
        }
        StringBuilder food = new StringBuilder();
        for (Treats.Treat t : Treats.Treat.values()) food.append(food.isEmpty() ? "" : ", ").append(t.id());
        s.sendMessage(Component.text(" Treats: " + food, NamedTextColor.GRAY));
        s.sendMessage(Component.text("Craft them (see the recipe book) or get them from staff. Placed: " + relics.count(), NamedTextColor.GRAY));
        s.sendMessage(Component.text("Staff: give, giveall, disc give, remove [target|radius], where [player] [radius], info, board, music, gift find|rellock, secret <players>", NamedTextColor.DARK_GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        if (a.length == 1) {
            for (String v : new String[]{"list", "give", "giveall", "random", "disc", "remove", "protect", "where", "info", "board", "music", "gift", "secret"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("remove")) {
            if ("target".startsWith(last)) out.add("target");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("where")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("board")) {
            for (String v : new String[]{"list", "world"}) if (v.startsWith(last)) out.add(v);
            for (NewYearBoards.Zone z : relics.boards().zoneList()) {
                if (z.id().getId().toLowerCase(Locale.ROOT).startsWith(last)) out.add(z.id().getId());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("board") && !a[1].equalsIgnoreCase("list")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("music")) {
            for (String v : new String[]{"list", "play", "pause", "skip", "shuffle"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("gift")) {
            for (String v : new String[]{"find", "rellock"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("gift") && !a[1].equalsIgnoreCase("help")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length >= 2 && a[0].equalsIgnoreCase("secret")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("list")) {
            for (RelicType.Collection c : RelicType.Collection.values()) if (c.id().startsWith(last)) out.add(c.id());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("disc")) {
            for (String v : new String[]{"list", "give"}) if (v.startsWith(last)) out.add(v);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("disc") && a[1].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 4 && a[0].equalsIgnoreCase("disc") && a[1].equalsIgnoreCase("give")) {
            if ("all".startsWith(last)) out.add("all");
            for (String id : discs.discs.keySet()) if (id.startsWith(last)) out.add(id);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            if ("all".startsWith(last)) out.add("all");
            for (RelicType.Collection c : RelicType.Collection.values()) if (c.id().startsWith(last)) out.add(c.id());
            for (RelicType r : RelicType.values()) if (r.id().startsWith(last)) out.add(r.id());
            for (Treats.Treat t : Treats.Treat.values()) if (t.id().startsWith(last)) out.add(t.id());
        }
        return out;
    }
}
