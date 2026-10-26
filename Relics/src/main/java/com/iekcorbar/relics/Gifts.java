package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Gift Box, the big version: right-click an empty box and you get a container of nine slots to fill with
 * presents, a button to change the wrapping and a button to lock it. Locking asks for one player name in chat
 * and stores their UUID, so a renamed player still gets their gift. A locked box is frozen: nobody can open it
 * or break it outside the gift window (config gift-window, default 24-26 December) - and then only the recipient, never the one who wrapped it (that stays
 * true inside the period too, just like the old box: you cannot open your own present).
 */
final class Gifts implements Listener {
    /** Wrapping colours: the variant is also the model name (see Relic.model). */
    static final String[] COLOURS = {"gift_box", "gift_box_green", "gift_box_gold"};
    private static final Material[] COLOUR_ICONS = {Material.RED_DYE, Material.GREEN_DYE, Material.YELLOW_DYE};

    private final RelicsPlugin plugin;
    private final Relics relics;
    /** Players that have a lock waiting for a name in chat. */
    private final Map<UUID, Relics.Relic> pendingLock = new HashMap<>();

    Gifts(RelicsPlugin plugin, Relics relics) {
        this.plugin = plugin;
        this.relics = relics;
    }

    /** The present may only be opened inside the config `gift-window` (default: Christmas, in the server's timezone). */
    boolean christmasNow() {
        if (windowAlways()) return true;
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(RelicsPlugin.zone);
        int[] a = windowFrom(), b = windowUntil();
        int day = now.getDayOfMonth();
        if (a[0] == 0 && b[0] == 0) {                       // no months given: a day range that repeats every month (test mode)
            return a[1] <= b[1] ? (day >= a[1] && day <= b[1]) : (day >= a[1] || day <= b[1]);
        }
        int cur = rank(now.getMonthValue(), day);
        int va = rank(a[0] == 0 ? now.getMonthValue() : a[0], a[1]);
        int vb = rank(b[0] == 0 ? now.getMonthValue() : b[0], b[1]);
        return va <= vb ? (cur >= va && cur <= vb) : (cur >= va || cur <= vb);
    }

    private boolean windowAlways() {
        return "any".equalsIgnoreCase(plugin.getConfig().getString("gift-window", "12-24 to 12-26").trim());
    }

    int[] windowFrom() {
        return parseDate(spec(true));
    }

    int[] windowUntil() {
        return parseDate(spec(false));
    }

    private String spec(boolean from) {
        String w = plugin.getConfig().getString("gift-window", "12-24 to 12-26").trim();
        String[] parts = w.split("\\s+to\\s+");
        if (parts.length == 1) parts = w.split("\\s*-\\s*");
        return (from || parts.length < 2 ? parts[0] : parts[1]).trim();
    }

    /** "12-24" -> {12, 24}; a bare "24" -> {0, 24} = every month. */
    private static int[] parseDate(String s) {
        String[] md = s.trim().split("-");
        try {
            if (md.length == 2) return new int[]{Integer.parseInt(md[0]), Integer.parseInt(md[1])};
            if (md.length == 1) return new int[]{0, Integer.parseInt(md[0])};
        } catch (NumberFormatException ignored) { }
        return new int[]{12, 24};   // the default start; christmasNow falls back to Christmas on garbage
    }

    private static int rank(int month, int day) {
        return month * 32 + day;
    }

    /** Human label for the messages: "24-26 December", or the raw text in an odd shape, or "any day (test mode)". */
    String window() {
        if (windowAlways()) return "any day (test mode - set gift-window back to '12-24 to 12-26' when done)";
        int[] a = windowFrom(), b = windowUntil();
        java.util.function.IntFunction<String> mon = m -> java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        if (a[0] == 0 && b[0] == 0) return "every month on day " + a[1] + "-" + b[1] + " (test mode)";
        int ma = a[0] == 0 ? 12 : a[0], mb = b[0] == 0 ? 12 : b[0];
        return ma == mb ? a[1] + "-" + b[1] + " " + mon.apply(ma) : a[1] + " " + mon.apply(ma) + " to " + b[1] + " " + mon.apply(mb);
    }

    private static String name0(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String n = op.getName();
        return n == null ? "someone" : n;
    }

    static String name(UUID id) {
        return id == null ? "unknown" : name0(id);
    }

    /** One line of what the box holds and who may open it - for /relics info, where and gift find. */
    static String status(Relics.Relic r) {
        if (r.locked) return "locked for " + name(r.recipient) + ": " + r.gifts.size() + " present(s), wrapped by " + name(r.owner);
        if (r.owner != null) return r.gifts.isEmpty() ? "empty - " + name(r.owner) + " is still wrapping" : r.gifts.size() + " present(s) in it, not locked yet";
        return "empty, anyone may start wrapping";
    }

    /** Look a typed name up: online players first, then the profile cache - the player must have joined at least once. */
    static UUID findByName(String typed) {
        Player on = Bukkit.getPlayerExact(typed);
        if (on != null) return on.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(typed);
        if (off != null && (off.isOnline() || off.hasPlayedBefore())) return off.getUniqueId();
        return null;
    }

    // ------------------------------------------------------------ right-click

    void use(Relics.Relic r, Player p) {
        if (r.locked) {
            if (!christmasNow()) {
                p.sendActionBar(Component.text("Locked for " + name(r.recipient) + " - only they open it, on " + window() + ".", NamedTextColor.GRAY));
                return;
            }
            if (p.getUniqueId().equals(r.owner)) {
                p.sendActionBar(Component.text("You wrapped this yourself - someone else has to open it.", NamedTextColor.GRAY));
                return;
            }
            if (!p.getUniqueId().equals(r.recipient)) {
                p.sendActionBar(Component.text("This present is for " + name(r.recipient) + ".", NamedTextColor.GRAY));
                return;
            }
            openFor(r, p);
            return;
        }
        if (r.owner != null && !r.owner.equals(p.getUniqueId())) {
            p.sendActionBar(Component.text(name(r.owner) + " is wrapping this box already.", NamedTextColor.GRAY));
            return;
        }
        if (p.isSneaking()) {
            if (r.owner != null && !r.gifts.isEmpty()) {
                takeBack(r, p);
                return;
            }
        }
        if (r.owner == null) {
            r.owner = p.getUniqueId();
            r.variant = COLOURS[ThreadLocalRandom.current().nextInt(COLOURS.length)];
            relics.relook(r);
            relics.save(r);
        }
        open(r, p);
    }

    /** The recipient, on the right days: the box is theirs. */
    private void openFor(Relics.Relic r, Player p) {
        UUID giver = r.owner;
        var c = r.center();
        for (ItemStack it : new ArrayList<>(r.gifts))
            for (var left : p.getInventory().addItem(it).values()) c.getWorld().dropItemNaturally(c, left);
        r.gifts.clear();
        r.locked = false;
        r.recipient = null;
        r.owner = null;
        r.variant = "";
        relics.relook(r);
        relics.save(r);
        c.getWorld().spawnParticle(Particle.DUST, c, 30, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.RED, 1f));
        c.getWorld().spawnParticle(Particle.DUST, c, 30, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(230, 190, 60), 1f));
        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c, 10, 0.4, 0.3, 0.4, 0);
        c.getWorld().playSound(c, "relics:gift.open", 1f, 1f);
        p.sendMessage(Component.text("Merry Christmas! This present was wrapped just for you.", NamedTextColor.GOLD));
        Player g = giver == null ? null : Bukkit.getPlayer(giver);
        if (g != null) g.sendMessage(Component.text(p.getName() + " opened your present!", NamedTextColor.GOLD));
    }

    /** The wrapper, before locking: sneak + right-click takes everything back. */
    private void takeBack(Relics.Relic r, Player p) {
        for (ItemStack it : new ArrayList<>(r.gifts))
            for (var left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        r.gifts.clear();
        r.owner = null;
        r.variant = "";
        relics.relook(r);
        relics.save(r);
        r.world.playSound(r.center(), "minecraft:entity.item.pickup", 0.8f, 1.2f);
        p.sendActionBar(Component.text("Box emptied - wrapping taken back.", NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------ the filling menu

    private static final int COLOUR = 20, LOCK = 22;

    private static final class Box implements InventoryHolder {
        final Relics.Relic relic;
        Inventory inv;

        Box(Relics.Relic relic) {
            this.relic = relic;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void open(Relics.Relic r, Player p) {
        Box b = new Box(r);
        b.inv = Bukkit.createInventory(b, 27, Component.text("Gift Box - fill with presents"));
        for (int i = 0; i < 9 && i < r.gifts.size(); i++) b.inv.setItem(i, r.gifts.get(i));
        fill(b.inv, r);
        p.openInventory(b.inv);
    }

    private ItemStack icon(Material mat, String nm, NamedTextColor c, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(nm, c).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Component.text(x, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        im.lore(l);
        it.setItemMeta(im);
        return it;
    }

    private void fill(Inventory inv, Relics.Relic r) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 9; i < 27; i++) if (i != COLOUR && i != LOCK) inv.setItem(i, pane);
        int col = Math.max(0, java.util.Arrays.asList(COLOURS).indexOf(r.variant.isEmpty() ? COLOURS[0] : r.variant));
        inv.setItem(COLOUR, icon(COLOUR_ICONS[col], "Change the wrapping", NamedTextColor.YELLOW,
                "Now: " + (col == 0 ? "red" : col == 1 ? "green" : "gold")));
        inv.setItem(LOCK, icon(Material.ENDER_EYE, "Lock this box", NamedTextColor.GOLD,
                "Then type the recipient's name in chat.",
                "Only they can open it, on " + window() + ".",
                "Even you not - that is the whole point."));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Box b)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Relics.Relic r = b.relic;
        if (relics.at(r.world.getBlockAt(r.x, r.y, r.z)) != r) {
            e.setCancelled(true);
            p.closeInventory();
            return;
        }
        if (e.getClickedInventory() != e.getInventory()) return;   // the player's own inventory stays normal
        int slot = e.getSlot();
        if (slot < 9) return;                                      // the present slots: let them put things in
        e.setCancelled(true);
        if (slot == COLOUR) {
            int idx = Math.max(0, java.util.Arrays.asList(COLOURS).indexOf(r.variant.isEmpty() ? COLOURS[0] : r.variant));
            r.variant = COLOURS[(idx + 1) % COLOURS.length];
            relics.relook(r);
            relics.save(r);
            p.playSound(p.getLocation(), "minecraft:item.dye.use", 0.6f, 1.2f);
        } else if (slot == LOCK) {
            boolean any = false;
            for (int i = 0; i < 9; i++) {
                ItemStack it = e.getInventory().getItem(i);
                if (it != null && !it.isEmpty()) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                p.sendActionBar(Component.text("First put something in the box.", NamedTextColor.GRAY));
                return;
            }
            saveNow(r, e.getInventory());
            pendingLock.put(p.getUniqueId(), r);
            p.closeInventory();
            p.sendMessage(Component.text("Who is this present for? Type their player name in chat ('cancel' to keep it open):", NamedTextColor.GOLD));
            p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 1f, 1.4f);
            return;
        }
        fill(e.getInventory(), r);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Box)) return;
        for (int raw : e.getRawSlots()) if (raw >= 9) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Box b)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Relics.Relic r = b.relic;
        if (relics.at(r.world.getBlockAt(r.x, r.y, r.z)) == r) {
            saveNow(r, e.getInventory());
            return;
        }
        // the box was broken or removed while it was open: hand the contents back rather than lose them
        for (int i = 0; i < 9; i++) {
            ItemStack it = e.getInventory().getItem(i);
            if (it != null && !it.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), it);
        }
    }

    private void saveNow(Relics.Relic r, Inventory inv) {
        List<ItemStack> kept = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.isEmpty()) kept.add(it);
        }
        r.gifts.clear();
        r.gifts.addAll(kept);
        relics.save(r);
    }

    // ------------------------------------------------------------ lock by name -> UUID

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Relics.Relic r = pendingLock.remove(e.getPlayer().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        String typed = e.getMessage().trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> lock(r, p, typed));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pendingLock.remove(e.getPlayer().getUniqueId());
    }

    private void lock(Relics.Relic r, Player p, String typed) {
        if (typed.equalsIgnoreCase("cancel") || typed.isEmpty()) {
            p.sendMessage(Component.text("Not locked - the box stays open.", NamedTextColor.GRAY));
            return;
        }
        if (relics.at(r.world.getBlockAt(r.x, r.y, r.z)) != r || r.gifts.isEmpty()) {
            p.sendMessage(Component.text("The box is gone or empty - nothing locked.", NamedTextColor.RED));
            return;
        }
        UUID who = findByName(typed);
        if (who == null) {
            p.sendMessage(Component.text("No player named '" + typed + "' is known to this server (they must have joined at least once). Not locked.", NamedTextColor.RED));
            return;
        }
        if (who.equals(p.getUniqueId())) {
            p.sendMessage(Component.text("You cannot lock a present to yourself - someone else has to open it.", NamedTextColor.RED));
            return;
        }
        r.locked = true;
        r.recipient = who;
        relics.save(r);
        r.world.playSound(r.center(), "minecraft:block.note_block.pling", 1f, 0.8f);
        p.sendMessage(Component.text("Locked for " + name(who) + "! Only they can open it - " + window() + ".", NamedTextColor.GOLD));
    }
}
