package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Game Night Winner's Crate (0.7.9): staff places the crate once per venue
 * (/relics give staff gamenight_crate) and hands tonight's winner a key. Turning the key
 * in the lock consumes it - one key, one opening - and draws EXACTLY ONE prize from the
 * weighted table in config "gamenight-crate".
 * Prize rows are "prize:<weight>:<entry>". An entry is either a plain crates.yml entry
 * (relic:/disc:/chance:/random:) - repeatable - or "pool:<name>": a list of concrete
 * prizes of which the same one is never won twice by the same player across all Game
 * Nights (tracked per UUID in crates-used.yml, "gamenight.won.<uuid>"). A pool whose
 * prizes are all won counts as spent: it drops out of the roll; every row spent falls
 * back to config "gamenight-crate.fallback" (default random:relic), so nobody ever
 * leaves empty-handed. Pool entries may also be "cmd:<console command>" - the seam for
 * MMOItems ("mi give SWORD X {player}") and TechCore, same as the shop's give_command.
 * No wager is anywhere involved: the key is a prize for winning the night, never for
 * sale - keep the wording prize-flavoured, never casino (GAMBLING-MECHANIC-RISK-NOTES).
 */
final class GameNight {

    private final RelicsPlugin plugin;

    GameNight(RelicsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * A player right-clicked the placed crate with something in hand. The key is consumed
     * on a successful draw; anything else just says whose hand a key belongs in.
     */
    void use(Player p, ItemStack hand) {
        if (hand == null || plugin.relics().typeOf(hand) != RelicType.GAMENIGHT_KEY) {
            p.sendActionBar(Component.text("The lock holds. Tonight's key sits with the winner.", NamedTextColor.GRAY));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        String pick = roll(p);
        if (pick == null) {
            p.sendMessage(Component.text("The crate's seal holds - the Guild has not set the Game Night prizes yet "
                    + "(config gamenight-crate).", NamedTextColor.RED));
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);              // one key, one opening
            if (hand.getAmount() <= 0) p.getInventory().setItemInMainHand(null);
        }
        String won = deliver(p, pick);
        if (won != null && plugin.getConfig().getBoolean("gamenight-crate.broadcast", true))
            Bukkit.broadcast(Component.text(p.getName() + " turned the Game Night key and drew " + won + ".",
                    NamedTextColor.GOLD));
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 1f, 1.1f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.4, 0), 40, 0.4, 0.5, 0.4, 0.02);
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1.2, 0), 20, 0.3, 0.4, 0.3, 0.1);
    }

    // ------------------------------------------------------------ the roll

    /** Rows "prize:<weight>:<entry>", pools that are fully won dropped: returns the entry that won, or null. */
    private String roll(Player p) {
        List<String> prizes = plugin.getConfig().getStringList("gamenight-crate.prizes");
        List<String[]> live = new ArrayList<>();          // {weight, entry}
        double total = 0;
        for (String row : prizes) {
            String[] p2 = row.trim().split(":", 3);
            if (p2.length < 3 || !p2[0].equalsIgnoreCase("prize")) continue;
            double w;
            try {
                w = Double.parseDouble(p2[1]);
            } catch (NumberFormatException bad) {
                plugin.getLogger().warning("Bad gamenight-crate prize weight in '" + row + "' - skipped");
                continue;
            }
            if (w <= 0) continue;
            String entry = p2[2];
            if (entry.toLowerCase(Locale.ROOT).startsWith("pool:") && poolLeft(p, entry.substring(5)) == null)
                continue;                                 // that pool is fully won - not in play
            live.add(new String[]{p2[1], entry});
            total += w;
        }
        if (live.isEmpty()) return plugin.getConfig().getString("gamenight-crate.fallback", "random:relic");
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (String[] row : live) {
            r -= Double.parseDouble(row[0]);
            if (r < 0) return row[1];
        }
        return live.get(live.size() - 1)[1];
    }

    /** Entries of a named pool this player has not won yet; null when the pool is spent or missing. */
    private List<String> poolLeft(Player p, String pool) {
        // 0.7.13: "pool:any" really means ALL relics now - the Game Night collection included
        // (owner request 21-11) - filtered by what this player already drew.
        List<String> won = plugin.wonPrizes(p);
        if ("any".equalsIgnoreCase(pool.trim())) {
            List<String> all = new ArrayList<>();
            for (RelicType t : RelicType.values()) all.add("relic:" + t.id());
            all.removeIf(e -> won.contains(prizeKey(e)));
            return all.isEmpty() ? null : all;
        }
        List<String> all = plugin.getConfig().getStringList("gamenight-crate.pools." + pool.trim());
        if (all.isEmpty()) {
            plugin.getLogger().warning("gamenight-crate pool '" + pool.trim() + "' is missing or empty");
            return null;
        }
        List<String> left = new ArrayList<>();
        for (String e : all) if (!won.contains(prizeKey(e))) left.add(e);
        return left.isEmpty() ? null : left;
    }

    // ------------------------------------------------------------ resolve + deliver

    /**
     * Hand the entry's prize to the player. Pool entries resolve to one concrete prize, are
     * marked won and delivered; plain entries run the crates.yml grammar (repeatable).
     * Returns the display name of what was won (for the broadcast), or null on failure.
     */
    private String deliver(Player p, String entry) {
        String e = entry.trim();
        String low = e.toLowerCase(Locale.ROOT);
        if (low.startsWith("pool:")) {
            List<String> left = poolLeft(p, e.substring(5));
            if (left == null)                               // spent since the roll: draw the fallback
                return deliver(p, plugin.getConfig().getString("gamenight-crate.fallback", "random:relic"));
            String pick = left.get(ThreadLocalRandom.current().nextInt(left.size())).trim();
            if (pick.toLowerCase(Locale.ROOT).startsWith("random:")) {
                String id = resolveRandom(p, pick);         // resolve to the concrete prize actually won
                if (id != null) pick = id;
            }
            plugin.recordWon(p, prizeKey(pick));
            return grant(p, pick);
        }
        if (low.startsWith("cmd:")) {
            runCmd(p, e.substring(4).trim());
            String nm = pretty(e);
            p.sendMessage(Component.text("The crate falls open: " + nm + "!", NamedTextColor.GOLD));
            return nm;
        }
        if (low.startsWith("random:")) {
            String id = resolveRandom(p, e);                // fallback rows may be random too (not recorded)
            return id == null ? null : grant(p, id);
        }
        // plain rows (relic:/disc:/chance:) run the shop grammar as-is - repeatable
        for (ItemStack it : plugin.crateItems(e, p)) {
            for (ItemStack left : p.getInventory().addItem(it).values())
                p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        String id = prizeKey(e);
        if (id.startsWith("relic:") || id.startsWith("disc:") || id.startsWith("cmd:")) {
            String nm = pretty(id);
            p.sendMessage(Component.text("The crate falls open: " + nm + "!", NamedTextColor.GOLD));
            return nm;
        }
        return null;
    }

    /** Deliver a resolved prize entry: cmd: dispatches, relic:/disc: land in the inventory; name back for the announce. */
    private String grant(Player p, String entry) {
        String low = entry.toLowerCase(Locale.ROOT);
        if (low.startsWith("cmd:")) {
            runCmd(p, entry.trim().substring(4).trim());
            String nm = pretty(entry);
            p.sendMessage(Component.text("The crate falls open: " + nm + "!", NamedTextColor.GOLD));
            return nm;
        }
        if (low.startsWith("random:")) {                  // fallback lines may be random too
            String id = resolveRandom(p, entry.trim());
            return id == null ? null : grant(p, id);
        }
        String id = prizeKey(entry);
        if (id.startsWith("relic:")) {
            String rid = id.substring(6);
            RelicType t = RelicType.byId(rid);
            if (t == null && rid.endsWith("_blessed")) t = RelicType.byId(rid.substring(0, rid.length() - 8));
            if (t != null) {
                for (ItemStack left : p.getInventory().addItem(plugin.relics().item(t, 1, rid.endsWith("_blessed"))).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                String nm = rid.endsWith("_blessed") ? "Blessed " + t.displayName : "a " + t.displayName;
                p.sendMessage(Component.text("The crate falls open: " + nm + "!", NamedTextColor.GOLD));
                return nm;
            }
        } else if (id.startsWith("disc:")) {
            Discs.Disc d = plugin.discs().byId(id.substring(5));
            if (d != null) {
                for (ItemStack left : p.getInventory().addItem(plugin.discs().item(d)).values())
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.sendMessage(Component.text("The crate falls open: " + d.title() + "!", NamedTextColor.GOLD));
                return d.title();
            }
        }
        plugin.getLogger().warning("Game Night: cannot grant prize '" + entry + "'");
        return null;
    }

    /** random:relic[:COLL] / random:disc -> a concrete relic:/disc: entry (won-free first, repeats only when spent). */
    private String resolveRandom(Player p, String entry) {
        String[] parts = entry.trim().toLowerCase(Locale.ROOT).split(":");
        List<String> won = plugin.wonPrizes(p);
        var rnd = ThreadLocalRandom.current();
        if (parts.length > 1 && parts[1].equals("disc")) {
            List<Discs.Disc> pool = new ArrayList<>(), spent = new ArrayList<>();
            for (Discs.Disc d : plugin.discs().discs.values()) {
                if (d.recipe() == null) (won.contains("disc:" + d.id()) ? spent : pool).add(d);
            }
            List<Discs.Disc> live = pool.isEmpty() ? spent : pool;
            return live.isEmpty() ? null : "disc:" + live.get(rnd.nextInt(live.size())).id();
        }
        RelicType.Collection coll = parts.length > 2 ? RelicType.Collection.byId(parts[2]) : null;
        List<RelicType> pool = new ArrayList<>(), spent = new ArrayList<>();
        for (RelicType t : RelicType.values()) {
            if (!plugin.randomStock(t) || (coll != null && t.collection != coll)) continue;
            (won.contains("relic:" + t.id()) ? spent : pool).add(t);
        }
        List<RelicType> live = pool.isEmpty() ? spent : pool;
        return live.isEmpty() ? null : "relic:" + live.get(rnd.nextInt(live.size())).id();
    }

    /** Console commands like the shop's give_command: {player} is filled in; MMOItems/TechCore both work here. */
    private void runCmd(Player p, String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
    }

    /** The stable identity of a prize for the won-list: what the player actually received. */
    static String prizeKey(String entry) {
        String low = entry.trim().toLowerCase(Locale.ROOT);
        if (low.startsWith("cmd:")) return "cmd:" + low.substring(4).trim().replace("{player}", "{p}");
        return low;
    }

    /** What to call the prize in the announce message. */
    private String pretty(String entry) {
        String id = prizeKey(entry);
        if (id.startsWith("relic:")) {
            String rid = id.substring(6);
            RelicType t = RelicType.byId(rid);
            if (t == null && rid.endsWith("_blessed")) t = RelicType.byId(rid.substring(0, rid.length() - 8));
            if (t != null) return rid.endsWith("_blessed") ? "Blessed " + t.displayName : t.displayName;
        } else if (id.startsWith("disc:")) {
            Discs.Disc d = plugin.discs().byId(id.substring(5));
            if (d != null) return d.title();
        } else if (id.startsWith("cmd:")) {
            String c = id.substring(4);
            String[] w = c.split("\\s+");
            for (int i = 0; i < w.length; i++)
                if (w[i].contains("_"))
                    return RelicsPlugin.titleize(w[i].toLowerCase(Locale.ROOT));
            return "a Guild prize";
        }
        return entry;
    }
}
