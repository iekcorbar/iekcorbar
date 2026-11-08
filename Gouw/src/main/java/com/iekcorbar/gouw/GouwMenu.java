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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The gouw chest menu (/gouw or /gouw menu). Since 0.4 it is town-shaped: MAIN (claim/autoclaim/
 * border/invite), LEDEN (member heads: click promotes/demotes, shift-click kicks), KAS (deposit,
 * withdraw, buy chunks for the town), JOBS (the live Jobs Reborn list with salaries) and the old
 * FLAGS page - plus the staff page. Members are added by name in chat - the same lock-flow players
 * know from the gift box - and stored as UUIDs.
 */
final class GouwMenu implements Listener {
    private final GouwPlugin plugin;
    private final ClaimStore store;
    /** player -> the gouw they are about to invite someone into (chat supplies the name). */
    private final Map<UUID, Land> pendingTrust = new ConcurrentHashMap<>();

    GouwMenu(GouwPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
    }

    // ------------------------------------------------------------ layout

    private static final int LAND = 4, CLAIM = 10, AUTO = 11, UNCLAIM = 12, BORDER = 13, BUY = 14, FLAGS = 15, TRUST = 16,
            MEMBERS = 17, KAS = 18, JOBS = 19, BACK = 22, ADMIN = 26;
    private static final int DEP_FROM = 10, DEP_TO = 12, WDR_FROM = 14, WDR_TO = 16, BUYN_FROM = 19, BUYN_TO = 21;
    private static final int A_BYPASS = 10, A_EXPIRE = 12;
    private static final Material[] FLAG_ICON = {Material.GRASS_BLOCK, Material.CHEST, Material.BONE, Material.IRON_SWORD};
    private static final String[] FLAG_LABEL = {"Members may build", "Members may open chests & doors", "Members may hit mobs", "Members may attack players"};
    private static final String[] FLAG_NAME = {"Build", "Open", "Mobs", "Players"};
    private static final double[] BANK_DEPOSIT = {10, 50, 250};
    private static final double[] BANK_WITHDRAW = {10, 50, 250};
    private static final int[] CHUNK_BUY = {1, 5, 10};

    private enum Page {MAIN, ADMIN, FLAGS, MEMBERS, BANK, JOBS}

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

    private String title(Page page, Land l) {
        return switch (page) {
            case ADMIN -> "Gouw - staff";
            case FLAGS -> "Gouw - what may members do?";
            case MEMBERS -> "Gouw - inwoners van " + (l == null ? "" : l.name);
            case BANK -> "Gouw - bank van " + (l == null ? "" : l.name);
            case JOBS -> "Gouw - taken (Jobs Reborn)";
            default -> "Gouw - " + (l != null ? l.name : viewerName);
        };
    }

    private String viewerName = "";

    private void show(Player p, Page page) {
        viewerName = p.getName();
        Holder h = new Holder(p, page);
        int size = (page == Page.MEMBERS || page == Page.JOBS) ? 54 : 27;
        h.inv = Bukkit.createInventory(h, size, Component.text(title(page, store.homeOf(p.getUniqueId()))));
        draw(h);
        p.openInventory(h.inv);
    }

    private void draw(Holder h) {
        switch (h.page) {
            case ADMIN -> drawAdmin(h);
            case FLAGS -> drawFlags(h);
            case MEMBERS -> drawMembers(h);
            case BANK -> drawBank(h);
            case JOBS -> drawJobs(h);
            default -> drawMain(h);
        }
    }

    private void panes(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY));
    }

    private void drawMain(Holder h) {
        Player p = h.viewer;
        Inventory inv = h.inv;
        panes(inv);
        Land l = store.homeOf(p.getUniqueId());
        int max = p.hasPermission("gouw.admin") ? Integer.MAX_VALUE : (l != null ? store.limitOf(l) : plugin.getMaxChunks(p));
        String role = l == null ? "-" : l.isMayor(p.getUniqueId()) ? "burgemeester" : switch (l.rank(p.getUniqueId())) {
            case ASSISTANT -> "adjunct"; case RESIDENT -> "resident"; case null, default -> "-"; };
        inv.setItem(LAND, icon(l == null ? Material.OAK_SIGN : Material.GOLD_NUGGET, "Your gouw", NamedTextColor.GOLD,
                l == null ? "Claim your first chunk below." : "Chunks: " + l.chunks.size() + (max == Integer.MAX_VALUE ? "" : " / " + max),
                l == null ? "-" : "World: " + l.world,
                l == null ? "-" : "Inwoners: " + l.ranks.size() + " - you are " + role,
                l == null ? "-" : "Bank: " + GouwCommand.money(l.bank)));
        inv.setItem(CLAIM, icon(Material.GRASS_BLOCK, "Claim this chunk", NamedTextColor.GREEN, "Burgemeester and adjunct only"));
        inv.setItem(AUTO, icon(Material.SAND, "Autoclaim around you", NamedTextColor.YELLOW, "Claims a 5x5 square, up to the gouw limit"));
        inv.setItem(UNCLAIM, icon(Material.BUCKET, "Unclaim this chunk", NamedTextColor.RED, "Shift-click: release your whole gouw"));
        inv.setItem(BORDER, icon(Material.COMPASS, "Show my border", NamedTextColor.AQUA, "Gold particles trace the gouw"));
        inv.setItem(BUY, icon(Material.EMERALD, "Buy chunks", NamedTextColor.GREEN, "+1 chunk from the town bank", "More options on the KAS page"));
        inv.setItem(FLAGS, icon(Material.LEATHER_BOOTS, "Member rights", NamedTextColor.LIGHT_PURPLE,
                l == null ? "Claim first, then set rights." : "Now: build " + on(l, Land.Flag.BUILD) + ", open " + on(l, Land.Flag.ACCESS)
                        + ", mobs " + on(l, Land.Flag.MOB) + ", players " + on(l, Land.Flag.PVP)));
        inv.setItem(TRUST, icon(Material.OAK_SIGN, "Invite someone", NamedTextColor.GREEN,
                l == null ? "Claim first." : "Closes the menu: type their name in chat."));
        inv.setItem(MEMBERS, icon(Material.PLAYER_HEAD, "Inwoners", NamedTextColor.GOLD, "Click heads to promote/demote or kick."));
        inv.setItem(KAS, icon(Material.GOLD_BLOCK, "Town bank", NamedTextColor.YELLOW,
                l == null ? "Claim first." : "Balance: " + GouwCommand.money(l.bank), "Deposits, withdrawals, buying chunks"));
        inv.setItem(JOBS, icon(Material.BOOK, "Taken / jobs", NamedTextColor.AQUA,
                "Live list from Jobs Reborn", "Assign: /gouw taak <player> <job>"));
        if (p.hasPermission("gouw.admin"))
            inv.setItem(ADMIN, icon(Material.COMMAND_BLOCK, "Staff page", NamedTextColor.LIGHT_PURPLE, "All gouwen, bypass, expire check"));
    }

    private static String on(Land l, Land.Flag f) {
        return l.memberFlags.contains(f) ? "on" : "off";
    }

    private void drawFlags(Holder h) {
        Land l = store.homeOf(h.viewer.getUniqueId());
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

    /** 54-slot page: row 2 (10-16) and rows 3-5 (19-43) are member heads, 45 invite, 49 back. */
    private static boolean isMemberHeadSlot(int slot) {
        return (slot >= 10 && slot <= 16) || (slot >= 19 && slot <= 43);
    }

    private void drawMembers(Holder h) {
        Land l = store.homeOf(h.viewer.getUniqueId());
        panes(h.inv);
        if (l == null) {
            h.viewer.sendActionBar(Component.text("Claim a chunk first.", NamedTextColor.RED));
            return;
        }
        h.inv.setItem(4, icon(Material.PLAYER_HEAD, "Burgemeester: " + nameOf(l.owner), NamedTextColor.GOLD,
                l.ranks.size() + " inwoner(s)"));
        int slot = 10;
        for (Map.Entry<UUID, Land.Rank> r : l.ranks.entrySet()) {
            if (slot == 17) slot = 19;
            if (slot > 43) break;
            UUID who = r.getKey();
            String taak = l.jobs.get(who);
            ItemStack it = head(who);
            ItemMeta m = it.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(r.getValue() == Land.Rank.ASSISTANT ? "adjunct" : "resident", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("taak: " + (taak == null ? "none" : taak), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click: " + (l.isMayor(h.viewer.getUniqueId()) ? (r.getValue() == Land.Rank.ASSISTANT ? "demote" : "promote to adjunct") : "no rights")
                    + " - shift: remove", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            it.setItemMeta(m);
            h.inv.setItem(slot++, it);
        }
        h.inv.setItem(45, icon(Material.OAK_SIGN, "Invite someone", NamedTextColor.GREEN, "Closes the menu: type their name in chat."));
        h.inv.setItem(49, icon(Material.ARROW, "Back", NamedTextColor.YELLOW));
    }

    private void drawBank(Holder h) {
        Land l = store.homeOf(h.viewer.getUniqueId());
        panes(h.inv);
        if (l == null) {
            h.viewer.sendActionBar(Component.text("Claim a chunk first.", NamedTextColor.RED));
            return;
        }
        int price = plugin.getConfig().getInt("price-per-chunk", 250);
        h.inv.setItem(4, icon(Material.GOLD_BLOCK, "Bank: " + GouwCommand.money(l.bank), NamedTextColor.GOLD,
                "Chunks " + l.chunks.size() + "/" + store.limitOf(l) + " (" + l.purchased + " bought)",
                "Chunk price: " + GouwCommand.money(price)));
        for (int i = 0; i < BANK_DEPOSIT.length; i++)
            h.inv.setItem(DEP_FROM + i, icon(Material.GOLD_NUGGET, "Deposit " + GouwCommand.money(BANK_DEPOSIT[i]), NamedTextColor.GREEN, "From your own wallet"));
        boolean manage = l.mayManage(h.viewer.getUniqueId());
        for (int i = 0; i < BANK_WITHDRAW.length; i++)
            h.inv.setItem(WDR_FROM + i, icon(manage ? Material.GOLD_INGOT : Material.BARRIER, "Withdraw " + GouwCommand.money(BANK_WITHDRAW[i]),
                    manage ? NamedTextColor.YELLOW : NamedTextColor.RED, manage ? "To your own wallet" : "Burgemeester and adjunct only"));
        for (int i = 0; i < CHUNK_BUY.length; i++)
            h.inv.setItem(BUYN_FROM + i, icon(Material.GRASS_BLOCK, "Buy " + CHUNK_BUY[i] + " chunk" + (CHUNK_BUY[i] > 1 ? "s" : ""),
                    manage ? NamedTextColor.GREEN : NamedTextColor.RED, manage ? "Costs " + GouwCommand.money((double) price * CHUNK_BUY[i]) + " from the bank" : "Burgemeester and adjunct only"));
        h.inv.setItem(BACK, icon(Material.ARROW, "Back", NamedTextColor.YELLOW));
    }

    private void drawJobs(Holder h) {
        panes(h.inv);
        List<String> jobs = plugin.jobs().all();
        double dflt = plugin.getConfig().getDouble("default-salary", 50);
        h.inv.setItem(4, icon(Material.BOOK, jobs.size() + " jobs (Jobs Reborn)", NamedTextColor.GOLD,
                "Assign: /gouw taak <player> <job>", "Residents are paid daily from the town bank."));
        int slot = 10;
        for (String j : jobs) {
            if (slot == 17) slot = 19;
            if (slot > 43) break;
            double sal = plugin.getConfig().getDouble("salaries." + j.toLowerCase(java.util.Locale.ROOT), dflt);
            h.inv.setItem(slot++, icon(Material.PAPER, j, NamedTextColor.AQUA, "Salary: " + GouwCommand.money(sal) + "/day"));
        }
        h.inv.setItem(49, icon(Material.ARROW, "Back", NamedTextColor.YELLOW));
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
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ clicking

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != h.inv || !(e.getWhoClicked() instanceof Player p)) return;
        Land mine = store.homeOf(p.getUniqueId());
        int slot = e.getSlot();

        switch (h.page) {
            case MAIN -> {
                switch (slot) {
                    case CLAIM -> act(h, store.claim(p, p.getWorld(), chunkOf(p)[0], chunkOf(p)[1]), "Chunk claimed.");
                    case AUTO -> autoclaim(h);
                    case UNCLAIM -> {
                        if (mine == null) act(h, "you have no gouw", null);
                        else if (e.isShiftClick()) {
                            if (!mine.isMayor(p.getUniqueId())) act(h, "only the burgemeester releases the whole gouw", null);
                            else {
                                store.unclaimAll(mine);
                                act(h, null, "Your whole gouw is released.");
                            }
                        } else act(h, store.unclaim(p, p.getWorld(), chunkOf(p)[0], chunkOf(p)[1]), "Chunk released.");
                    }
                    case BORDER -> {
                        if (mine == null) act(h, "you have no gouw", null);
                        else {
                            p.closeInventory();
                            for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> Border.draw(mine), i * 12L);
                        }
                    }
                    case BUY -> {
                        if (mine == null) act(h, "claim a chunk first", null);
                        else act(h, store.buyChunks(p, mine, 1), "+1 chunk for the gouw!");
                    }
                    case FLAGS -> {
                        if (mine == null) act(h, "claim a chunk first", null);
                        else show(p, Page.FLAGS);
                    }
                    case TRUST -> inviteFlow(h, mine);
                    case MEMBERS -> show(p, Page.MEMBERS);
                    case KAS -> show(p, Page.BANK);
                    case JOBS -> show(p, Page.JOBS);
                    case ADMIN -> {
                        if (p.hasPermission("gouw.admin")) show(p, Page.ADMIN);
                    }
                }
            }
            case MEMBERS -> {
                if (slot == 49) show(p, Page.MAIN);
                else if (slot == 45) inviteFlow(h, mine);
                else if (mine != null && isMemberHeadSlot(slot) && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                    OfflinePlayer op = skullOwner(e.getCurrentItem());
                    if (op == null || op.getUniqueId().equals(mine.owner)) return;
                    if (e.isShiftClick()) {
                        if (!mine.mayManage(p.getUniqueId())) p.sendActionBar(Component.text("Only burgemeester and adjunct remove members.", NamedTextColor.RED));
                        else {
                            String err = store.kick(mine, op.getUniqueId());
                            p.sendActionBar(Component.text(err == null ? op.getName() + " is removed." : err, err == null ? NamedTextColor.GOLD : NamedTextColor.RED));
                        }
                    } else {
                        if (!mine.isMayor(p.getUniqueId())) p.sendActionBar(Component.text("Only the burgemeester promotes and demotes.", NamedTextColor.RED));
                        else {
                            String err = mine.rank(op.getUniqueId()) == Land.Rank.ASSISTANT
                                    ? store.demote(mine, op.getUniqueId()) : store.promote(mine, op.getUniqueId());
                            p.sendActionBar(Component.text(err == null ? op.getName() + "'s role changed." : err, err == null ? NamedTextColor.GREEN : NamedTextColor.RED));
                        }
                    }
                    drawMembers(h);
                }
            }
            case BANK -> {
                if (slot == BACK) show(p, Page.MAIN);
                else if (mine == null) return;
                else if (slot >= DEP_FROM && slot <= DEP_TO && slot < DEP_FROM + BANK_DEPOSIT.length) {
                    String err = store.deposit(p, mine, BANK_DEPOSIT[slot - DEP_FROM]);
                    p.sendActionBar(Component.text(err == null ? "Deposited." : "Nope: " + err, err == null ? NamedTextColor.GREEN : NamedTextColor.RED));
                    drawBank(h);
                } else if (slot >= WDR_FROM && slot <= WDR_TO && slot < WDR_FROM + BANK_WITHDRAW.length) {
                    String err = store.withdraw(p, mine, BANK_WITHDRAW[slot - WDR_FROM]);
                    p.sendActionBar(Component.text(err == null ? "Withdrew " + GouwCommand.money(BANK_WITHDRAW[slot - WDR_FROM]) + "." : "Nope: " + err, err == null ? NamedTextColor.GREEN : NamedTextColor.RED));
                    drawBank(h);
                } else if (slot >= BUYN_FROM && slot <= BUYN_TO && slot < BUYN_FROM + CHUNK_BUY.length) {
                    String err = store.buyChunks(p, mine, CHUNK_BUY[slot - BUYN_FROM]);
                    p.sendActionBar(Component.text(err == null ? "+" + CHUNK_BUY[slot - BUYN_FROM] + " chunk quota!" : "Nope: " + err, err == null ? NamedTextColor.GREEN : NamedTextColor.RED));
                    drawBank(h);
                }
            }
            case JOBS -> {
                if (slot == 49) show(p, Page.MAIN);
                else if (slot == 4) show(p, Page.BANK);
            }
            case FLAGS -> {
                if (slot == BACK) {
                    show(p, Page.MAIN);
                    return;
                }
                if (mine == null || !mine.mayManage(p.getUniqueId())) {
                    p.sendActionBar(Component.text("Only the burgemeester and adjunct change rights.", NamedTextColor.RED));
                    return;
                }
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

    private void inviteFlow(Holder h, Land mine) {
        Player p = h.viewer;
        if (mine == null) {
            act(h, "claim a chunk first", null);
            return;
        }
        if (!mine.mayManage(p.getUniqueId())) {
            act(h, "only the burgemeester and adjunct invite", null);
            return;
        }
        pendingTrust.put(p.getUniqueId(), mine);
        p.closeInventory();
        p.sendMessage(Component.text("Type the name of the player to invite ('cancel' to stop):", NamedTextColor.GOLD));
        p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 1f, 1.2f);
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

    // ------------------------------------------------------------ invite via chat name (UUID-based)

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Land l = pendingTrust.remove(e.getPlayer().getUniqueId());
        if (l == null) return;
        e.setCancelled(true);
        String typed = e.getMessage().trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (typed.equalsIgnoreCase("cancel") || typed.isEmpty()) {
                p.sendMessage(Component.text("Nobody invited - your gouw page: /gouw menu", NamedTextColor.GRAY));
                return;
            }
            if (gouwGone(l)) {
                p.sendMessage(Component.text("Your gouw is gone - nobody invited.", NamedTextColor.RED));
                return;
            }
            UUID who = GouwCommand.uuidOf(typed);
            if (who == null) p.sendMessage(Component.text("No player named '" + typed + "' is known to this server.", NamedTextColor.RED));
            else {
                String err = store.invite(l, who);
                if (err != null) p.sendMessage(Component.text("Cannot invite " + typed + ": " + err + ".", NamedTextColor.RED));
                else p.sendMessage(Component.text(typed + " is now a resident of " + l.name + ".", NamedTextColor.GREEN));
            }
        });
    }

    /** True when the gouw was deleted (unclaimed fully) while the player was typing the name. */
    private boolean gouwGone(Land l) {
        return store.landOf(l.owner) != l;
    }

    private String nameOf(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "unknown" : n;
    }
}
