package com.iekcorbar.gouw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The gouw chest menu (/gouw or /gouw menu): your chunk count and limit, claim / autoclaim / unclaim,
 * the border pulse, the four member flags and the trusted heads. Members are added by name in chat -
 * the same lock-flow players know from the gift box - and stored as UUIDs. Staff with gouw.admin get a
 * second page (all gouwen as heads: click to delete, plus the bypass toggle, the expire check and the
 * Lands reimport).
 */
final class GouwMenu implements Listener {
    private final GouwPlugin plugin;
    private final ClaimStore store;
    /** player -> the gouw they are about to add a member to (chat supplies the name). */
    private final Map<UUID, Land> pendingTrust = new ConcurrentHashMap<>();

    GouwMenu(GouwPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
    }

    // ------------------------------------------------------------ layout

    private static final int LAND = 4, CLAIM = 10, AUTO = 11, UNCLAIM = 12, BORDER = 13, FLAGS = 15, TRUST = 16,
            BACK = 22, ADMIN = 26;
    private static final int HEADS_FROM = 19, HEADS_TO = 21;
    private static final int A_BYPASS = 10, A_EXPIRE = 12;
    private static final Material[] FLAG_ICON = {Material.GRASS_BLOCK, Material.CHEST, Material.BONE, Material.IRON_SWORD};
    private static final String[] FLAG_LABEL = {"Members may build", "Members may open chests & doors", "Members may hit mobs", "Members may attack players"};
    private static final String[] FLAG_NAME = {"Build", "Open", "Mobs", "Players"};

    private enum Page {MAIN, ADMIN, FLAGS}

    private static final class Holder implements InventoryHolder {
        final Player viewer;
        final Page page;

        Holder(Player viewer, Page page) {
            this.viewer = viewer;
            this.page = page;
        }

        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------ open & draw

    void open(Player p) {
        show(p, Page.MAIN);
    }

    private void show(Player p, Page page) {
        Holder h = new Holder(p, page);
        h.inv = Bukkit.createInventory(h, 27, Component.text(page == Page.ADMIN ? "Gouw - staff" : page == Page.FLAGS ? "Gouw - what may members do?" : "Gouw - " + p.getName()));
        draw(h);
        p.openInventory(h.inv);
    }

    private void draw(Holder h) {
        if (h.page == Page.ADMIN) drawAdmin(h);
        else if (h.page == Page.FLAGS) drawFlags(h);
        else drawMain(h);
    }

    private void panes(Inventory inv) {
        for (int i = 0; i < 27; i++) inv.setItem(i, icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY));
    }

    private void drawMain(Holder h) {
        Player p = h.viewer;
        Inventory inv = h.inv;
        panes(inv);
        Land l = store.landOf(p.getUniqueId());
        int max = p.hasPermission("gouw.admin") ? Integer.MAX_VALUE : plugin.getMaxChunks();
        inv.setItem(LAND, icon(l == null ? Material.OAK_SIGN : Material.GOLD_NUGGET, "Your gouw", NamedTextColor.GOLD,
                l == null ? "Claim your first chunk below." : "Chunks: " + l.chunks.size() + (max == Integer.MAX_VALUE ? "" : " / " + max),
                l == null ? "-" : "World: " + l.world,
                l == null ? "-" : "Members: " + l.trusted.size()));
        inv.setItem(CLAIM, icon(Material.GRASS_BLOCK, "Claim this chunk", NamedTextColor.GREEN, "The chunk you are standing in"));
        inv.setItem(AUTO, icon(Material.SAND, "Autoclaim around you", NamedTextColor.YELLOW, "Claims a 5x5 square, up to your limit"));
        inv.setItem(UNCLAIM, icon(Material.BUCKET, "Unclaim this chunk", NamedTextColor.RED, "Shift-click: release your whole gouw"));
        inv.setItem(BORDER, icon(Material.COMPASS, "Show my border", NamedTextColor.AQUA, "Gold particles trace your gouw"));
        inv.setItem(FLAGS, icon(Material.LEATHER_BOOTS, "Member rights", NamedTextColor.LIGHT_PURPLE,
                l == null ? "Claim first, then set rights." : "Now: build " + on(l, Land.Flag.BUILD) + ", open " + on(l, Land.Flag.ACCESS)
                        + ", mobs " + on(l, Land.Flag.MOB) + ", players " + on(l, Land.Flag.PVP)));
        inv.setItem(TRUST, icon(Material.OAK_SIGN, "Trust someone", NamedTextColor.GREEN,
                "Closes the menu: type their name in chat.", "Click a head below to remove a member."));
        inv.setItem(HEADS_FROM - 1, icon(Material.PLAYER_HEAD, "Members", NamedTextColor.GOLD));
        int slot = HEADS_FROM;
        if (l != null) for (UUID t : l.trusted) {
            if (slot > HEADS_TO) break;
            inv.setItem(slot++, head(t));
        }
        if (slot <= HEADS_TO) inv.setItem(slot, icon(Material.BARRIER, "Empty", NamedTextColor.DARK_GRAY));
        if (p.hasPermission("gouw.admin"))
            inv.setItem(ADMIN, icon(Material.COMMAND_BLOCK, "Staff page", NamedTextColor.LIGHT_PURPLE, "All gouwen, bypass, expire check"));
    }

    private static String on(Land l, Land.Flag f) {
        return l.memberFlags.contains(f) ? "on" : "off";
    }

    private void drawFlags(Holder h) {
        Land l = store.landOf(h.viewer.getUniqueId());
        panes(h.inv);
        if (l == null) {
            h.viewer.sendActionBar(Component.text("Claim a chunk first.", NamedTextColor.RED));
            return;
        }
        for (int i = 0; i < Land.Flag.values().length; i++) {
            Land.Flag f = Land.Flag.values()[i];
            boolean on = l.memberFlags.contains(f);
            h.inv.setItem(11 + i * 2, icon(on ? FLAG_ICON[i] : Material.BARRIER, FLAG_LABEL[i],
                    on ? NamedTextColor.GREEN : NamedTextColor.RED, FLAG_NAME[i] + ": " + (on ? "yes" : "no") + " - click to toggle"));
        }
        h.inv.setItem(BACK, icon(Material.ARROW, "Back", NamedTextColor.YELLOW));
    }

    private void drawAdmin(Holder h) {
        Inventory inv = h.inv;
        panes(inv);
        inv.setItem(4, icon(Material.BOOK, "All gouwen: " + store.all().size(), NamedTextColor.GOLD,
                store.totalChunks() + " chunks claimed", "Click a head to delete that gouw"));
        int slot = 0;
        for (Land l : store.all()) {
            if (slot > 8 + 9) break;   // rows 1 and 2 (0-17)
            ItemStack it = head(l.owner);
            ItemMeta m = it.getItemMeta();
            m.displayName(Component.text(l.name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(l.world + " - " + l.chunks.size() + " chunks", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to delete!", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(slot < 9 ? 10 + slot : 19 + slot - 9, it);
            slot++;
        }
        Player p = h.viewer;
        boolean bypass = plugin.isBypassing(p.getUniqueId());
        inv.setItem(A_BYPASS, icon(bypass ? Material.NETHER_STAR : Material.BEDROCK, "Bypass: " + (bypass ? "ON" : "off"),
                bypass ? NamedTextColor.AQUA : NamedTextColor.GRAY, "Build anywhere while on"));
        inv.setItem(A_EXPIRE, icon(Material.CLOCK, "Expire check now", NamedTextColor.YELLOW,
                "Releases gouwen of inactive owners (config expire-inactive-days)"));
        inv.setItem(BACK, icon(Material.ARROW, "Back", NamedTextColor.YELLOW));
        inv.setItem(24, icon(Material.HOPPER, "Reimport Lands", NamedTextColor.LIGHT_PURPLE, "Only when claims.json is gone/empty"));
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

    private ItemStack head(UUID t) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(t);
        m.setOwningPlayer(op);
        m.displayName(Component.text(op.getName() == null ? "unknown" : op.getName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Click to remove", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ clicking

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != h.inv || !(e.getWhoClicked() instanceof Player p)) return;
        Land mine = store.landOf(p.getUniqueId());
        int slot = e.getSlot();

        switch (h.page) {
            case MAIN -> {
                switch (slot) {
                    case CLAIM -> act(h, store.claim(p, p.getWorld(), chunkOf(p)[0], chunkOf(p)[1]), "Chunk claimed.");
                    case AUTO -> autoclaim(h);
                    case UNCLAIM -> {
                        if (mine == null) act(h, "you have no gouw", null);
                        else if (e.isShiftClick()) {
                            store.unclaimAll(mine);
                            act(h, null, "Your whole gouw is released.");
                        } else act(h, store.unclaim(p, p.getWorld(), chunkOf(p)[0], chunkOf(p)[1]), "Chunk released.");
                    }
                    case BORDER -> {
                        if (mine == null) act(h, "you have no gouw", null);
                        else {
                            p.closeInventory();
                            for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> Border.draw(mine), i * 12L);
                        }
                    }
                    case FLAGS -> {
                        if (mine == null) act(h, "claim a chunk first", null);
                        else show(p, Page.FLAGS);
                    }
                    case TRUST -> {
                        if (mine == null) act(h, "claim a chunk first", null);
                        else {
                            pendingTrust.put(p.getUniqueId(), mine);
                            p.closeInventory();
                            p.sendMessage(Component.text("Type the name of the player to trust ('cancel' to stop):", NamedTextColor.GOLD));
                            p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 1f, 1.2f);
                        }
                    }
                    case ADMIN -> {
                        if (p.hasPermission("gouw.admin")) show(p, Page.ADMIN);
                    }
                    default -> {
                        if (slot >= HEADS_FROM && slot <= HEADS_TO && e.getCurrentItem() != null
                                && e.getCurrentItem().getType() == Material.PLAYER_HEAD && mine != null) {
                            OfflinePlayer op = skullOwner(e.getCurrentItem());
                            if (op != null) {
                                if (store.untrust(mine, op.getUniqueId()))
                                    p.sendActionBar(Component.text(op.getName() + " is no longer a member.", NamedTextColor.GOLD));
                                drawMain(h);
                            }
                        }
                    }
                }
            }
            case FLAGS -> {
                if (slot == BACK) {
                    show(p, Page.MAIN);
                    return;
                }
                if (mine == null) return;
                int idx = (slot - 11) / 2;
                if (idx >= 0 && idx < Land.Flag.values().length && slot % 2 == 1) {
                    boolean nowOn = store.toggleFlag(mine, Land.Flag.values()[idx]);
                    p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.5f, nowOn ? 1.6f : 0.7f);
                    drawFlags(h);
                }
            }
            case ADMIN -> {
                if (!p.hasPermission("gouw.admin")) return;
                if (slot == BACK) {
                    show(p, Page.MAIN);
                } else if (slot == A_BYPASS) {
                    plugin.setBypassing(p.getUniqueId(), !plugin.isBypassing(p.getUniqueId()));
                    p.sendActionBar(Component.text("Bypass " + (plugin.isBypassing(p.getUniqueId()) ? "ON - build anywhere." : "off."),
                            NamedTextColor.AQUA));
                    drawAdmin(h);
                } else if (slot == A_EXPIRE) {
                    int n = plugin.expiry().check();
                    int d = plugin.expiry().days();
                    p.sendActionBar(Component.text(
                            n > 0 ? "Released " + n + " expired gouw(s)."
                                    : d == 0 ? "Expire is off (config expire-inactive-days = 0)." : "Nothing expired (limit " + d + " days).",
                            n > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                    drawAdmin(h);
                } else if (slot == 24) {
                    int n = LandsImporter.run(plugin, store);
                    p.sendMessage(Component.text(n > 0 ? "Imported " + n + " gouwen from Lands." : "Nothing imported - claims.json already has gouwen?",
                            n > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
                    drawAdmin(h);
                } else if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                    OfflinePlayer op = skullOwner(e.getCurrentItem());
                    Land l = op == null ? null : store.landOf(op.getUniqueId());
                    if (l == null) {
                        p.sendActionBar(Component.text("That gouw is gone.", NamedTextColor.RED));
                        drawAdmin(h);
                    } else {
                        int n = l.chunks.size();
                        store.unclaimAll(l);
                        p.sendMessage(Component.text("Deleted " + l.name + " (" + n + " chunks released).", NamedTextColor.GREEN));
                        drawAdmin(h);
                    }
                }
            }
        }
        p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.4f, 1.4f);
    }

    private static OfflinePlayer skullOwner(ItemStack it) {
        if (it.getItemMeta() instanceof SkullMeta m) return m.getOwningPlayer();
        return null;
    }

    private void autoclaim(Holder h) {
        Player p = h.viewer;
        int[] c = chunkOf(p);
        int got = 0;
        String last = null;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                last = store.claim(p, p.getWorld(), c[0] + dx, c[1] + dz);
                if (last == null) got++;
            }
        act(h, got == 0 ? last : null, "Claimed " + got + " chunk(s) around you.");
    }

    private void act(Holder h, String err, String ok) {
        if (err != null) h.viewer.sendActionBar(Component.text("Nope: " + err + ".", NamedTextColor.RED));
        else if (ok != null) h.viewer.sendActionBar(Component.text(ok, NamedTextColor.GREEN));
        drawMain(h);
    }

    private static int[] chunkOf(Player p) {
        return new int[]{p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4};
    }

    // ------------------------------------------------------------ trust via chat name (UUID-based)

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Land l = pendingTrust.remove(e.getPlayer().getUniqueId());
        if (l == null) return;
        e.setCancelled(true);
        String typed = e.getMessage().trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (typed.equalsIgnoreCase("cancel") || typed.isEmpty()) {
                p.sendMessage(Component.text("Nobody trusted - your gouw page: /gouw menu", NamedTextColor.GRAY));
                return;
            }
            if (gouwGone(l)) {
                p.sendMessage(Component.text("Your gouw is gone - nothing trusted.", NamedTextColor.RED));
                return;
            }
            UUID who = GouwCommand.uuidOf(typed);
            if (who == null) p.sendMessage(Component.text("No player named '" + typed + "' is known to this server.", NamedTextColor.RED));
            else if (who.equals(l.owner)) p.sendMessage(Component.text("You own that gouw - no need to trust yourself.", NamedTextColor.RED));
            else if (store.trust(l, who)) p.sendMessage(Component.text(typed + " may now build in " + l.name + ".", NamedTextColor.GREEN));
            else p.sendMessage(Component.text(typed + " was already a member.", NamedTextColor.GRAY));
        });
    }

    /** True when the gouw was deleted (unclaimed fully) while the player was typing the name. */
    private boolean gouwGone(Land l) {
        return store.landOf(l.owner) != l;
    }
}
