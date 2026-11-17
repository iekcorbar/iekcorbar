package com.iekcorbar.hihou.bw;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 0.5.0: the team shop + team upgrades (BedWars' store and per-team upgrades, with vanilla
 * stock and no shop NPC to babysit). Open it by right-clicking the team's shop marker
 * (/ih bw team <arena> shop <team> - the script-built bases mark theirs with an anvil) or
 * with /ih bw shop.
 *
 * Everything is paid in the ore the generators drop: you literally hand over the iron, one
 * cost type per offer keeps the counting honest. Prices follow the stock BedWars numbers -
 * iron is cheap and diamonds are real money. The Upgrades tab is the shared, match-long
 * investment (protection, sharpness, forge, healers, trap): levels live in BwArena.upg, the
 * playing tick arms them, and finish() wipes them with the rest of the session.
 */
final class BwStore {
    /** one purchasable thing: a pre-built give stack (meta and all) for cost x costAmount ore. */
    private record Offer(ItemStack give, Material cost, int costAmount, String blurb) {
    }

    /** one team upgrade: emerald cost per next level; index = the BwArena.upg slot. */
    private record Upgrade(String label, Material icon, int max, int[] emeraldCost, String blurb) {
    }

    private static final String[] PAGES = {"Blocks", "Armor", "Weapons", "Food", "Potions", "Upgrades"};

    private static final Map<String, List<Offer>> STOCK = new LinkedHashMap<>();

    private static Offer of(Material m, int amount, Material cost, int costAmount, String blurb) {
        return new Offer(new ItemStack(m, amount), cost, costAmount, blurb);
    }

    static {
        STOCK.put("Blocks", List.of(
                of(Material.WHITE_WOOL, 16, Material.IRON_INGOT, 4, "The classic bridge material."),
                of(Material.WHITE_WOOL, 64, Material.IRON_INGOT, 12, "A whole stack: finish that bridge."),
                of(Material.GLASS, 8, Material.IRON_INGOT, 4, "Beware - wool breaks glass instantly."),
                of(Material.LADDER, 16, Material.GOLD_INGOT, 3, "Cheaper than wool for down-climb-up."),
                of(Material.OAK_PLANKS, 32, Material.IRON_INGOT, 6, "Flammable. A torch nearby is a mistake.")));
        STOCK.put("Armor", List.of(
                of(Material.LEATHER_HELMET, 1, Material.IRON_INGOT, 10, "Better than nothing."),
                of(Material.CHAINMAIL_LEGGINGS, 1, Material.IRON_INGOT, 15, "The workhorse piece."),
                of(Material.CHAINMAIL_CHESTPLATE, 1, Material.IRON_INGOT, 20, "Mail over the heart."),
                of(Material.IRON_HELMET, 1, Material.GOLD_INGOT, 4, "Iron head, gold purse."),
                of(Material.IRON_CHESTPLATE, 1, Material.DIAMOND, 3, "Now the team looks like a team."),
                of(Material.IRON_LEGGINGS, 1, Material.DIAMOND, 2, "Legs you can run bridges on.")));
        STOCK.put("Weapons", List.of(
                of(Material.WOODEN_SWORD, 1, Material.IRON_INGOT, 5, "Replacement after the first clash."),
                of(Material.STONE_SWORD, 1, Material.IRON_INGOT, 10, "The people's sword."),
                of(Material.STONE_AXE, 1, Material.IRON_INGOT, 12, "Chops doors and careless players."),
                of(Material.IRON_SWORD, 1, Material.GOLD_INGOT, 6, "Mid-game flex."),
                of(Material.DIAMOND_SWORD, 1, Material.DIAMOND, 6, "Bed destroyed? This is why."),
                of(Material.BOW, 1, Material.GOLD_INGOT, 4, "Arrows sold separately, of course."),
                of(Material.ARROW, 12, Material.GOLD_INGOT, 2, "Aim for the bridge."),
                of(Material.SHEARS, 1, Material.IRON_INGOT, 6, "Enemy wool is a resource; bed wool is a crime scene."),
                of(Material.TNT, 1, Material.EMERALD, 3, "Careful - beds are blocks too."),
                of(Material.ENDER_PEARL, 1, Material.DIAMOND, 3, "Out of a corner, into their face.")));
        STOCK.put("Food", List.of(
                of(Material.BREAD, 4, Material.IRON_INGOT, 3, "Cheap fuel."),
                of(Material.COOKED_CHICKEN, 4, Material.GOLD_INGOT, 2, "Hunger heals nobody fast."),
                of(Material.COOKED_BEEF, 4, Material.GOLD_INGOT, 3, "Steak between sieges."),
                of(Material.GOLDEN_CARROT, 2, Material.GOLD_INGOT, 5, "The one real golden thing here."),
                of(Material.CAKE, 1, Material.IRON_INGOT, 10, "Three bites and a morale boost.")));
        STOCK.put("Potions", List.of(
                new Offer(healing(), Material.EMERALD, 1, "Throw it at your own feet."),
                new Offer(fireRes(), Material.GOLD_INGOT, 4, "Lava pools happen on bridges."),
                new Offer(leaping(), Material.EMERALD, 2, "Jump like you mean it.")));
    }

    private static final Upgrade[] UPGRADES = {
            new Upgrade("Protection", Material.IRON_CHESTPLATE, 3, new int[]{2, 4, 6},
                    "Resistance for the whole team while you hold the line."),
            new Upgrade("Sharpness", Material.IRON_SWORD, 3, new int[]{2, 4, 7},
                    "Strength - your swords hit like they mean it."),
            new Upgrade("Forge", Material.BLAST_FURNACE, 3, new int[]{3, 5, 8},
                    "Your iron generators tick faster and richer."),
            new Upgrade("Healers", Material.GLASS_BOTTLE, 2, new int[]{4, 7},
                    "Every 45 s the whole team gets Regeneration I."),
            new Upgrade("Trap", Material.TRIPWIRE_HOOK, 2, new int[]{4, 6},
                    "Intruders near spawn get slowed - and announced to you."),
    };

    private final BwGameManager mgr;
    /** viewer -> the open GUI (so finish() can close shops whose match just ended). */
    private final Map<Player, StoreHolder> open = new LinkedHashMap<>();

    BwStore(BwGameManager mgr) {
        this.mgr = mgr;
    }

    // ------------------------------------------------------------ potions (real items, meta baked)

    private static ItemStack healing() {
        ItemStack it = new ItemStack(Material.SPLASH_POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 2, false, true), true);
        m.displayName(Component.text("Splash Instant Health II", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack fireRes() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0, false, true), true);
        m.displayName(Component.text("Fire Resistance (60s)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack leaping() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.addCustomEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 3600, 1, false, true), true);
        m.displayName(Component.text("Leaping II (90s)", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ opening

    /** True when this block is ANY team's shop marker; the caller cancels the event. */
    boolean blockClick(Player p, Block b) {
        String key = b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        for (BwArena a : mgr.arenas())
            for (BwTeam t : a.teams.values()) {
                if (!key.equals(t.shop())) continue;
                String err = open(p, a, t);
                if (!err.isEmpty()) p.sendActionBar(Component.text(err, NamedTextColor.RED));
                return true;
            }
        return false;
    }

    /** /ih bw shop - the shop of the team you play for, in the arena you're in. */
    String openCommand(Player p) {
        BwArena a = mgr.arenaOf(p.getUniqueId());
        if (a == null) return "You are not in a BedWars game.";
        if (a.status != BwArena.Status.PLAYING) return "The shop opens when the match is on.";
        BwTeam t = a.teamOf(p.getUniqueId());
        if (t == null) return "Spectators don't shop.";
        String err = open(p, a, t);
        return err.isEmpty() ? null : err;
    }

    private String open(Player p, BwArena a, BwTeam t) {
        if (a.status != BwArena.Status.PLAYING) return "The shop only works during a match.";
        if (!a.players.contains(p.getUniqueId())) return "This shop belongs to a running match.";
        if (a.teamOf(p.getUniqueId()) != t) return "That is " + t.name() + "'s shop - not yours.";
        StoreHolder h = new StoreHolder(p, a.name, t.name());
        h.inv = Bukkit.createInventory(h, 54, Component.text("Team shop - " + a.name + " (" + t.name() + ")", NamedTextColor.DARK_AQUA));
        draw(h, "Blocks");
        open.put(p, h);
        p.openInventory(h.inv);
        return "";
    }

    /** End of match: a GUI over a finished game is a shop window to nowhere. */
    void closeFor(String arenaName) {
        for (Map.Entry<Player, StoreHolder> e : new ArrayList<>(open.entrySet()))
            if (e.getValue().arena.equals(arenaName)) {
                e.getKey().closeInventory();
                open.remove(e.getKey());
            }
    }

    // ------------------------------------------------------------ the GUI

    static final class StoreHolder implements InventoryHolder {
        final Player viewer;
        final String arena, team;
        String page = "Blocks";

        Inventory inv;

        StoreHolder(Player viewer, String arena, String team) {
            this.viewer = viewer;
            this.arena = arena;
            this.team = team;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void draw(StoreHolder h, String page) {
        h.page = page;
        Inventory inv = h.inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, pane());
        BwArena a = mgr.arena(h.arena);
        BwTeam t = a == null ? null : a.team(h.team);
        if (a == null || t == null) return;
        Player p = h.viewer;
        inv.setItem(0, tab(Material.GOLD_BLOCK,
                "Wallet: " + count(p, Material.IRON_INGOT) + " iron  " + count(p, Material.GOLD_INGOT)
                        + " gold  " + count(p, Material.DIAMOND) + " diamond  " + count(p, Material.EMERALD) + " emerald"));
        Material[] icons = {Material.WHITE_WOOL, Material.CHAINMAIL_CHESTPLATE, Material.IRON_SWORD,
                Material.BREAD, Material.POTION, Material.EMERALD};
        for (int i = 0; i < PAGES.length; i++)
            inv.setItem(1 + i, tab(icons[i], PAGES[i] + (PAGES[i].equals(page) ? "  <- here" : "")));
        inv.setItem(8, tab(Material.BARRIER, "close"));
        if (page.equals("Upgrades")) drawUpgrades(h, a, t);
        else {
            int slot = 10;
            for (Offer o : STOCK.get(page)) {
                if (slot > 34) break;
                inv.setItem(slot++, icon(o));
            }
        }
    }

    private void drawUpgrades(StoreHolder h, BwArena a, BwTeam t) {
        int[] lv = a.upg.computeIfAbsent(t.name(), k -> new int[UPGRADES.length]);
        for (int i = 0; i < UPGRADES.length; i++) {
            Upgrade u = UPGRADES[i];
            ItemStack it = new ItemStack(u.icon());
            ItemMeta m = it.getItemMeta();
            m.displayName(Component.text(u.label(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Level " + lv[i] + "/" + u.max() + " for " + t.name(), NamedTextColor.YELLOW));
            lore.add(Component.text(u.blurb(), NamedTextColor.GRAY));
            if (lv[i] < u.max())
                lore.add(Component.text("Cost: " + u.emeraldCost()[lv[i]] + " emeralds - click to buy", NamedTextColor.GREEN));
            else lore.add(Component.text("Maxed - well spent.", NamedTextColor.AQUA));
            m.lore(lore);
            it.setItemMeta(m);
            h.inv.setItem(10 + i * 2, it);
        }
    }

    private static ItemStack icon(Offer o) {
        ItemStack it = o.give().clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        lore.add(Component.text("Cost: " + o.costAmount() + " " + costName(o.cost()) + "s", NamedTextColor.YELLOW));
        lore.add(Component.text(o.blurb(), NamedTextColor.GRAY));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack tab(Material mat, String label) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.empty());
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ clicks

    void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof StoreHolder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != h.inv) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        BwArena a = mgr.arena(h.arena);
        BwTeam t = a == null ? null : a.team(h.team);
        if (a == null || t == null || a.status != BwArena.Status.PLAYING || a.teamOf(p.getUniqueId()) != t) {
            p.closeInventory();
            open.remove(p);
            return;
        }
        int slot = e.getSlot();
        if (slot == 8) {
            p.closeInventory();
            open.remove(p);
            return;
        }
        if (slot >= 1 && slot <= 6) {
            draw(h, PAGES[slot - 1]);
            return;
        }
        if (slot < 10 || slot > 34) return;
        if (h.page.equals("Upgrades")) {
            int idx = (slot - 10) / 2;
            if (slot % 2 == 0 && idx >= 0 && idx < UPGRADES.length) buyUpgrade(p, a, t, idx);
        } else {
            List<Offer> page = STOCK.get(h.page);
            int idx = slot - 10;
            if (idx < page.size()) buyOffer(p, a, t, page.get(idx));
        }
    }

    private void buyOffer(Player p, BwArena a, BwTeam t, Offer o) {
        int have = count(p, o.cost());
        if (have < o.costAmount()) {
            deny(p, "not enough " + costName(o.cost()) + " (" + have + "/" + o.costAmount() + ")");
            return;
        }
        take(p, o.cost(), o.costAmount());
        Map<Integer, ItemStack> left = p.getInventory().addItem(o.give().clone());
        for (ItemStack rem : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rem);
        p.playSound(p.getLocation(), "minecraft:block.note_block.pling", 0.6f, 1.5f);
        wallet(p);
        StoreHolder h = open.get(p);
        if (h != null) draw(h, h.page);
    }

    private void buyUpgrade(Player p, BwArena a, BwTeam t, int idx) {
        Upgrade u = UPGRADES[idx];
        int[] lv = a.upg.computeIfAbsent(t.name(), k -> new int[UPGRADES.length]);
        if (lv[idx] >= u.max()) {
            deny(p, u.label() + " is already maxed");
            return;
        }
        int cost = u.emeraldCost()[lv[idx]];
        if (count(p, Material.EMERALD) < cost) {
            deny(p, "not enough emeralds (" + count(p, Material.EMERALD) + "/" + cost + ")");
            return;
        }
        take(p, Material.EMERALD, cost);
        lv[idx]++;
        a.upg.put(t.name(), lv);
        if (idx == 2) mgr.applyForge(a, t, lv[2]);
        mgr.toArena(a, p.getName() + " bought " + t.name() + " " + u.label() + " " + lv[idx] + "!",
                NamedTextColor.GREEN);
        wallet(p);
        StoreHolder h = open.get(p);
        if (h != null) draw(h, "Upgrades");
    }

    // ------------------------------------------------------------ helpers

    private static String costName(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace("_ingot", "");
    }

    /** 0.5.1: the little money peek - after every purchase and every base deploy. */
    void wallet(Player p) {
        p.sendActionBar(Component.text("wallet: " + count(p, Material.IRON_INGOT) + " iron  "
                + count(p, Material.GOLD_INGOT) + " gold  " + count(p, Material.DIAMOND) + " diamond  "
                + count(p, Material.EMERALD) + " emerald", NamedTextColor.GRAY));
    }

    /** Resource money in the inventory: generator ore IS the wallet. */
    private int count(Player p, Material ore) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getType() == ore) n += it.getAmount();
        return n;
    }

    private void take(Player p, Material ore, int amount) {
        var inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize() && amount > 0; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType() != ore) continue;
            int use = Math.min(it.getAmount(), amount);
            amount -= use;
            if (use == it.getAmount()) inv.setItem(slot, null);
            else it.setAmount(it.getAmount() - use);
        }
    }

    private void deny(Player p, String why) {
        p.sendActionBar(Component.text("Nope - " + why + ".", NamedTextColor.RED));
        p.playSound(p.getLocation(), "minecraft:block.note_block.hat", 0.6f, 0.8f);
    }

    /**
     * Once a second with the playing tick: the persistent upgrade effects. Protection and
     * Sharpness re-cast short silent potion effects on the team (cheap, self-healing when
     * someone dies), Healers pulse Regen every 45 s, and the Trap scans for enemy feet
     * within six blocks of the team spawn - once slowed and announced per 15 s intruder.
     */
    void tick(BwArena a) {
        for (BwTeam t : a.teams.values()) {
            int[] lv = a.upg.get(t.name());
            if (lv == null) continue;
            List<Player> on = new ArrayList<>();
            for (java.util.UUID u : t.members()) {
                Player q = Bukkit.getPlayer(u);
                if (q != null && !q.isDead()) on.add(q);
            }
            if (lv[0] > 0)
                for (Player q : on) q.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, lv[0] - 1, false, false, false));
            if (lv[1] > 0)
                for (Player q : on) q.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, lv[1] - 1, false, false, false));
            if (lv[3] > 0 && a.elapsed % 45 == 0)
                for (Player q : on) {
                    q.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0, false, true, false));
                    q.playSound(q.getLocation(), "minecraft:block.beacon.power_select", 0.4f, 1.6f);
                }
            if (lv[4] > 0 && t.spawn() != null) {
                long now = System.currentTimeMillis();
                for (Player q : a.online()) {
                    if (a.teamOf(q.getUniqueId()) == t) continue;
                    if (q.getLocation().distanceSquared(t.spawn()) > 36) continue;
                    Long until = a.trapCool.get(q.getUniqueId());
                    if (until != null && until > now) continue;
                    a.trapCool.put(q.getUniqueId(), now + 15000);
                    q.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, false));
                    q.playSound(q.getLocation(), "minecraft:block.note_block.bass", 0.8f, 0.6f);
                    Component warn = Component.text(q.getName() + " is in your base!", BwGameManager.chat(t.color()));
                    for (Player d : on) d.sendMessage(warn);
                }
            }
        }
    }
}
