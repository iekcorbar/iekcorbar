package com.iekcorbar.wildlife;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Build your own zoo: ticket kiosk (sells tickets for in-game money, the owner gets the money), turnstiles and
 * gates that open for a valid ticket, staff gates, fences, enclosure props, feeding troughs and info boards.
 * Props come from the shared engine (Decor), models from the Zoo resource pack (tools/make_zoo_pack.py).
 */
final class Zoo implements org.bukkit.event.Listener {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final WildlifePlugin plugin;
    final Decor decor;
    private final NamespacedKey ticketKey;
    /** player -> time of the last "ticket needed" message, so it is not spammed. */
    private final Map<UUID, Long> told = new HashMap<>();
    /** player -> kiosk position + time of the first click (a second click within 5 s buys). */
    private final Map<UUID, long[]> confirm = new HashMap<>();

    Zoo(WildlifePlugin plugin) {
        this.plugin = plugin;
        this.ticketKey = new NamespacedKey(plugin, "ticket");
        decor = new Decor(plugin, "zoo", defs(), plugin.getConfig().getInt("zoo.break-hits", 3));
    }

    // ------------------------------------------------------------ the props

    private List<DecorDef> defs() {
        List<DecorDef> l = new ArrayList<>();
        Tag<Material> planks = Tag.PLANKS;
        // entrance
        l.add(new DecorDef("ticket_kiosk", "Ticket Kiosk", "Sells zoo tickets. Right-click twice to buy; the owner sneaks to see the settings.")
                .height(2).use(new Kiosk()).recipe(1, planks, planks, planks, Material.PAPER, Material.GOLD_NUGGET));
        l.add(new DecorDef("turnstile", "Entry Turnstile", "Opens for a valid ticket of this zoo and lets you through.")
                .group("zoofence").tick(1, this::gate).use(this::gateUse).recipe(1, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_BARS, Material.IRON_BARS, Material.REDSTONE)
                .openable());
        l.add(new DecorDef("exit_turnstile", "Exit Turnstile", "Always opens: the way out of the zoo.")
                .group("zoofence").tick(1, this::exitGate).recipe(1, Material.IRON_INGOT, Material.IRON_BARS, Material.IRON_BARS).openable());
        l.add(new DecorDef("zoo_gate", "Zoo Gate", "A tall iron gate that opens for a valid ticket.")
                .height(2).group("zoofence").tick(1, this::gate).use(this::gateUse)
                .recipe(1, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_INGOT, Material.REDSTONE).openable());
        l.add(new DecorDef("staff_gate", "Staff Gate", "Opens for the zoo owner and staff only.")
                .height(2).group("zoofence").tick(1, this::staffGate)
                .recipe(1, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_INGOT, Material.TRIPWIRE_HOOK).openable());
        l.add(new DecorDef("entrance_arch", "Entrance Arch", "A wide arch to walk under. Three blocks wide.")
                .hit(3.0f, 0.4f).recipe(1, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT, planks, planks, Material.GOLD_INGOT));
        // enclosures
        l.add(new DecorDef("zoo_fence", "Zoo Fence", "Tall iron fence that joins its neighbours, gates included.")
                .height(2).connect("zoofence").recipe(4, Material.IRON_BARS, Material.IRON_BARS, Material.IRON_NUGGET));
        l.add(new DecorDef("bamboo_fence", "Bamboo Fence", "A bamboo fence for gentler enclosures. Joins its neighbours.")
                .height(2).connect("zoofence").recipe(4, Material.BAMBOO, Material.BAMBOO, Material.BAMBOO, Material.BAMBOO, Material.STRING));
        l.add(new DecorDef("rock_small", "Boulder", "A mossy boulder for the enclosure.")
                .variants("rock_small", "rock_small_b").recipe(2, Material.COBBLESTONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE).sound("minecraft:block.stone.place"));
        l.add(new DecorDef("rock_large", "Large Boulder", "Two blocks tall, for climbing animals.")
                .height(2).recipe(1, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE).sound("minecraft:block.stone.place"));
        l.add(new DecorDef("log_horizontal", "Climbing Log", "A big fallen log, about three blocks long and as tall as a player: hide behind it or let animals climb it.")
                .scale(3f).hit(2.4f, 1.9f).recipe(1, Tag.LOGS, Tag.LOGS));
        l.add(new DecorDef("water_trough", "Water Trough", "A drinking trough.")
                .hit(0.9f, 0.5f).recipe(1, planks, planks, planks, Material.BUCKET));
        l.add(new DecorDef("feeding_trough", "Feeding Trough", "Right-click with food to fill it. Animals nearby eat from it and the zoo keeps count.")
                .hit(0.9f, 0.5f).use(this::trough).tick(3, this::troughTick).recipe(1, planks, planks, Material.WHEAT));
        l.add(new DecorDef("hay_feeder", "Hay Feeder", "A rack of hay.")
                .hit(0.9f, 0.9f).recipe(1, planks, planks, Material.STICK, Material.STICK, Material.WHEAT));
        l.add(new DecorDef("bird_perch", "Bird Perch", "A branch on a post for birds.")
                .height(2).recipe(1, Material.STICK, Material.STICK, Material.STICK, Tag.LOGS));
        l.add(new DecorDef("animal_hut", "Animal Hut", "A little shelter.")
                .recipe(1, planks, planks, planks, planks, Material.WHEAT));
        // guests
        l.add(new DecorDef("info_board", "Info Board", "Right-click it to read about the animal standing nearby.")
                .hit(0.8f, 1.2f).use(this::info).recipe(1, planks, planks, Material.PAPER, Material.STICK));
        l.add(new DecorDef("map_board", "Zoo Map", "A map of the zoo. Decoration.")
                .hit(1.0f, 1.6f).recipe(1, planks, planks, Material.PAPER, Material.PAPER, Material.COMPASS));
        l.add(new DecorDef("direction_sign", "Direction Sign", "Points the way. Place it on the floor or a wall.")
                .wall().hit(0.8f, 1.2f).recipe(1, planks, Material.STICK, Material.PAPER));
        l.add(new DecorDef("zoo_bench", "Zoo Bench", "Right-click to sit.")
                .seat(0.45f).hit(0.9f, 0.7f).recipe(1, planks, planks, Material.STICK, Material.STICK));
        l.add(new DecorDef("trash_bin", "Animal Trash Bin", "Right-click with an item to throw it away.")
                .use(this::bin).recipe(1, Material.IRON_INGOT, Material.IRON_INGOT, Material.COAL));
        l.add(new DecorDef("lamp_post", "Lamp Post", "Three blocks of iron post with a lantern on top.")
                .height(3).glow(15).recipe(1, Material.IRON_INGOT, Material.IRON_INGOT, Material.STICK, Material.LANTERN));
        l.add(new DecorDef("souvenir_stall", "Souvenir Stall", "A little striped stall. Decoration.")
                .height(2).recipe(1, planks, planks, planks, Tag.WOOL, Tag.WOOL, Material.PAPER));
        statues(l);
        return l;
    }

    /**
     * Every animal as a placeable statue (an adult, a rare one and a baby): its cubes in rest pose, true size, from the Zoo
     * pack. Craft: stones + the animal's icon item (an adult 3 stone, a baby 2 stone, a rare one 3 stone + an amethyst shard).
     * Scale and hitbox of each model come from statues.csv (made by tools/make_zoo_pack.py).
     */
    private void statues(List<DecorDef> l) {
        Map<String, float[]> data = new HashMap<>();
        try (var in = plugin.getResource("statues.csv")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] f = line.split(",");
                    data.put(f[0], new float[]{Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3])});
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read statues.csv: " + e.getMessage());
        }
        for (Species s : Species.values()) {
            Material icon = Material.matchMaterial(s.icon);
            Object[] adult = icon == null ? new Object[]{Material.STONE, Material.STONE, Material.STONE} : new Object[]{Material.STONE, Material.STONE, Material.STONE, icon};
            Object[] baby = icon == null ? new Object[]{Material.STONE, Material.STONE} : new Object[]{Material.STONE, Material.STONE, icon};
            Object[] rare = icon == null ? new Object[]{Material.STONE, Material.STONE, Material.STONE, Material.AMETHYST_SHARD}
                    : new Object[]{Material.STONE, Material.STONE, Material.STONE, icon, Material.AMETHYST_SHARD};
            String[] looks = s.models().stream().map(m -> "stat_" + m.substring(3)).toArray(String[]::new);
            DecorDef d = statue(data, "animal_" + s.id(), s.displayName + " Statue", "A true-size statue of a " + s.displayName.toLowerCase() + ". Hit it to pick it up again.", looks[0]);
            if (looks.length > 1) d.variants(looks);
            l.add(d.recipe(1, adult));
            l.add(statue(data, "animal_" + s.id() + "_rare", s.rareName() + " Statue", "A statue of the rare " + s.rareName().toLowerCase() + ".", "stat_" + s.id() + "_rare").recipe(1, rare));
            l.add(statue(data, "animal_" + s.id() + "_baby", "Baby " + s.displayName + " Statue", "A statue of a baby " + s.displayName.toLowerCase() + ".", "stat_" + s.id() + "_baby").recipe(1, baby));
        }
    }

    private static DecorDef statue(Map<String, float[]> data, String id, String name, String lore, String model) {
        float[] f = data.getOrDefault(model, new float[]{1f, 0.8f, 0.9f});
        return new DecorDef(id, name, lore, model).hit(f[1], f[2]).scale(f[0]).sound("minecraft:block.stone.place");
    }

    List<ItemStack> allItems() {
        List<ItemStack> out = decor.allItems();
        out.add(ticket("Zoo", System.currentTimeMillis() + 3_600_000L, false));
        return out;
    }

    // ------------------------------------------------------------ tickets

    /** Kiosk data: name|price|minutes|sold|takings|open (1/0). */
    private static String[] kiosk(Decor.Prop p) {
        String[] f = p.data.split("\\|", -1);
        if (f.length < 5) f = new String[]{"Zoo", "10", "60", "0", "0"};
        if (f.length < 6) f = java.util.Arrays.copyOf(f, 6);
        if (f[5] == null || f[5].isEmpty()) f[5] = "1";
        return f;
    }

    private static void saveKiosk(Decor d, Decor.Prop p, String[] f) {
        p.data = String.join("|", f);
        d.save(p);
    }

    ItemStack ticket(String zoo, long expiry, boolean single) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Zoo Ticket", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(zoo, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(single ? "One entry" : "Valid until " + TIME.format(Instant.ofEpochMilli(expiry)), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Hold it near the entrance gate.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Wildlife", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(ticketKey, PersistentDataType.STRING, zoo + "|" + expiry + "|" + (single ? 1 : 0));
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("zoo", "ticket"));
        return it;
    }

    /** The inventory slot holding a valid ticket for this zoo, or -1. */
    private int validTicket(Player p, String zoo) {
        ItemStack[] inv = p.getInventory().getContents();
        long now = System.currentTimeMillis();
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) continue;
            String v = it.getItemMeta().getPersistentDataContainer().get(ticketKey, PersistentDataType.STRING);
            if (v == null) continue;
            String[] f = v.split("\\|");
            if (f.length < 3 || !f[0].equalsIgnoreCase(zoo)) continue;
            if (f[2].equals("1") || Long.parseLong(f[1]) > now) return i;
        }
        return -1;
    }

    private boolean isStaff(Player p, Decor.Prop prop) {
        return p.hasPermission("wildlife.zoo.staff") || p.getUniqueId().equals(prop.owner);
    }

    // ------------------------------------------------------------ economy (Vault, by reflection: no hard dependency)

    private Object economy() {
        try {
            Class<?> c = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = Bukkit.getServicesManager().getRegistration(c);
            return rsp == null ? null : rsp.getProvider();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean tx(Object eco, String method, OfflinePlayer who, double amount) {
        try {
            Object r = eco.getClass().getMethod(method, OfflinePlayer.class, double.class).invoke(eco, who, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ kiosk

    private final class Kiosk implements DecorDef.Use, Decor.Placed {
        @Override
        public void placed(Decor d, Decor.Prop p, Player by) {
            p.data = by.getName() + "'s Zoo|10|60|0|0|1";
            d.save(p);
            plugin.adv().grant(by, "kiosk");
            by.sendMessage(Component.text("Ticket Kiosk placed. Sneak + right-click to see and change the settings.", NamedTextColor.GREEN));
        }

        @Override
        public boolean use(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
            String[] f = kiosk(p);
            if (pl.isSneaking() && isStaff(pl, p)) {
                openSettings(pl, p);
                return true;
            }
            boolean owner = pl.getUniqueId().equals(p.owner);
            if (f[5].equals("0") && !owner) {
                pl.sendMessage(Component.text(f[0] + " is closed right now.", NamedTextColor.RED));
                return true;
            }
            double price = Double.parseDouble(f[1]);
            int minutes = Integer.parseInt(f[2]);
            long now = System.currentTimeMillis();
            long[] c = confirm.get(pl.getUniqueId());
            long id = ((long) p.x * 31 + p.y) * 31 + p.z;
            if (c == null || c[0] != id || now - c[1] > 5000) {
                confirm.put(pl.getUniqueId(), new long[]{id, now});
                String valid = minutes <= 0 ? "one entry" : minutes + " minutes";
                pl.sendMessage(Component.text(f[0] + ": ticket for " + valid + ", price " + money(price) + ". Right-click again to buy.", NamedTextColor.GOLD));
                return true;
            }
            confirm.remove(pl.getUniqueId());
            if (!owner && price > 0) {
                Object eco = economy();
                if (eco == null) {
                    pl.sendMessage(Component.text("Tickets cannot be sold right now (no economy found).", NamedTextColor.RED));
                    return true;
                }
                if (!tx(eco, "withdrawPlayer", pl, price)) {
                    pl.sendMessage(Component.text("You cannot afford a ticket (" + money(price) + ").", NamedTextColor.RED));
                    return true;
                }
                if (p.owner != null) tx(eco, "depositPlayer", Bukkit.getOfflinePlayer(p.owner), price);
            }
            ItemStack t = ticket(f[0], now + minutes * 60_000L, minutes <= 0);
            for (ItemStack left : pl.getInventory().addItem(t).values()) pl.getWorld().dropItemNaturally(pl.getLocation(), left);
            f[3] = String.valueOf(Integer.parseInt(f[3]) + 1);
            if (!owner) f[4] = String.valueOf(Double.parseDouble(f[4]) + price);
            saveKiosk(d, p, f);
            plugin.adv().grant(pl, "ticket");
            pl.getWorld().playSound(p.center(), "minecraft:entity.experience_orb.pickup", 0.8f, 1.4f);
            pl.sendMessage(Component.text("Ticket bought. Enjoy the zoo!", NamedTextColor.GREEN));
            return true;
        }
    }

    private static String money(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    // ------------------------------------------------------------ settings menu

    private static final int[] DURATIONS = {0, 15, 30, 60, 120, 240, 1440};

    /** Holder of the kiosk settings menu: remembers which kiosk block it belongs to. */
    private static final class SettingsMenu implements org.bukkit.inventory.InventoryHolder {
        final org.bukkit.block.Block block;
        org.bukkit.inventory.Inventory inv;

        SettingsMenu(org.bukkit.block.Block block) {
            this.block = block;
        }

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inv;
        }
    }

    private static ItemStack icon(Material m, String name, NamedTextColor c, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(name, c).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String x : lore) l.add(Component.text(x, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        im.lore(l);
        it.setItemMeta(im);
        return it;
    }

    void openSettings(Player pl, Decor.Prop p) {
        SettingsMenu h = new SettingsMenu(p.center().getBlock());
        h.inv = Bukkit.createInventory(h, 27, Component.text("Ticket Kiosk settings"));
        fillMenu(h.inv, kiosk(p));
        pl.openInventory(h.inv);
    }

    private void fillMenu(org.bukkit.inventory.Inventory inv, String[] f) {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        double price = Double.parseDouble(f[1]);
        int minutes = Integer.parseInt(f[2]);
        inv.setItem(10, icon(Material.RED_CONCRETE, "Price -", NamedTextColor.RED, "Click: -1", "Right-click: -10", "Shift-click: -100"));
        inv.setItem(11, icon(Material.GOLD_INGOT, "Ticket price: " + money(price), NamedTextColor.GOLD,
                "Paid to you through the server economy", "(Vault / CMI) when somebody else buys one."));
        inv.setItem(12, icon(Material.LIME_CONCRETE, "Price +", NamedTextColor.GREEN, "Click: +1", "Right-click: +10", "Shift-click: +100"));
        inv.setItem(14, icon(Material.CLOCK, minutes <= 0 ? "Valid for: one entry" : "Valid for: " + minutes + " minutes", NamedTextColor.AQUA,
                "Click: next length", "Right-click: previous length"));
        boolean open = f[5].equals("1");
        inv.setItem(16, icon(open ? Material.LIME_DYE : Material.GRAY_DYE, open ? "Open for visitors" : "Closed", open ? NamedTextColor.GREEN : NamedTextColor.RED,
                "Click to switch. A closed zoo sells no tickets."));
        inv.setItem(22, icon(Material.NAME_TAG, "Name: " + f[0], NamedTextColor.YELLOW, "Change it with /zoo set name <name>",
                "Sold: " + f[3] + "   Taken so far: " + money(Double.parseDouble(f[4]))));
    }

    @org.bukkit.event.EventHandler
    public void onMenuClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SettingsMenu h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory() || !(e.getWhoClicked() instanceof Player pl)) return;
        Decor.Prop prop = decor.at(h.block);
        if (prop == null || !prop.def.id.equals("ticket_kiosk") || !isStaff(pl, prop)) {
            pl.closeInventory();
            return;
        }
        String[] f = kiosk(prop);
        int slot = e.getSlot();
        if (slot == 10 || slot == 12) {
            int step = e.isShiftClick() ? 100 : e.isRightClick() ? 10 : 1;
            double v = Double.parseDouble(f[1]) + (slot == 12 ? step : -step);
            f[1] = money(Math.max(0, Math.min(1_000_000, v)));
        } else if (slot == 14) {
            int minutes = Integer.parseInt(f[2]);
            int i = 0;
            while (i < DURATIONS.length - 1 && DURATIONS[i] < minutes) i++;
            i = (i + (e.isRightClick() ? DURATIONS.length - 1 : 1)) % DURATIONS.length;
            f[2] = String.valueOf(DURATIONS[i]);
        } else if (slot == 16) {
            f[5] = f[5].equals("1") ? "0" : "1";
        } else {
            return;
        }
        saveKiosk(decor, prop, f);
        fillMenu(e.getInventory(), f);
        pl.playSound(pl.getLocation(), "minecraft:ui.button.click", 0.6f, 1.2f);
    }

    private void settings(Player pl, String[] f) {
        pl.sendMessage(Component.text("--- " + f[0] + " ---", NamedTextColor.GOLD));
        pl.sendMessage(Component.text("Tickets sold: " + f[3] + "   Taken so far: " + money(Double.parseDouble(f[4])), NamedTextColor.GRAY));
        pl.sendMessage(line("Name: " + f[0], "/zoo set name " + f[0]));
        pl.sendMessage(line("Price: " + f[1], "/zoo set price " + f[1]));
        pl.sendMessage(line("Valid for (minutes, 0 = one entry): " + f[2], "/zoo set minutes " + f[2]));
        pl.sendMessage(line("Open: " + (f[5].equals("1") ? "on" : "off"), "/zoo set open " + (f[5].equals("1") ? "off" : "on")));
        pl.sendMessage(Component.text("Look at the kiosk and click a line to change it.", NamedTextColor.DARK_GRAY));
    }

    private static Component line(String text, String cmd) {
        return Component.text("  " + text, NamedTextColor.YELLOW).clickEvent(ClickEvent.suggestCommand(cmd));
    }

    /** /zoo set name|price|minutes <value> on the kiosk the player is looking at. */
    String set(Player p, String what, String value) {
        var hit = p.rayTraceBlocks(6);
        Decor.Prop prop = hit == null || hit.getHitBlock() == null ? null : decor.at(hit.getHitBlock());
        if (prop == null || !prop.def.id.equals("ticket_kiosk")) return "Look at a Ticket Kiosk.";
        if (!isStaff(p, prop)) return "This is not your kiosk.";
        String[] f = kiosk(prop);
        try {
            switch (what) {
                case "name" -> {
                    if (value.isBlank() || value.length() > 32 || value.contains("|")) return "Pick a name of at most 32 characters, without |.";
                    String old = f[0];
                    f[0] = value.trim();
                    for (Decor.Prop g : decor.near(prop.center(), 60))
                        if ((g.def.id.equals("turnstile") || g.def.id.equals("zoo_gate")) && g.data.equalsIgnoreCase(old)) {
                            g.data = f[0];
                            decor.save(g);
                        }
                }
                case "open" -> {
                    if (!value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) return "Use on or off.";
                    f[5] = value.equalsIgnoreCase("on") ? "1" : "0";
                }
                case "price" -> {
                    double v = Double.parseDouble(value);
                    if (v < 0 || v > 1_000_000) return "Pick a price between 0 and 1000000.";
                    f[1] = money(v);
                }
                case "minutes" -> {
                    int v = Integer.parseInt(value);
                    if (v < 0 || v > 10_080) return "Pick 0 (one entry) up to 10080 minutes.";
                    f[2] = String.valueOf(v);
                }
                default -> {
                    return "Use name, price, minutes or open.";
                }
            }
        } catch (NumberFormatException e) {
            return "That is not a number.";
        }
        saveKiosk(decor, prop, f);
        return "";
    }

    // ------------------------------------------------------------ gates

    /** A gate takes its zoo name from the nearest kiosk of the same owner when placed (see data); show it when used. */
    private boolean gateUse(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        if (p.data.isEmpty()) bind(p);
        pl.sendActionBar(Component.text("Entrance of " + p.data + ". Hold a ticket near the gate.", NamedTextColor.GOLD));
        return true;
    }

    private void bind(Decor.Prop p) {
        Decor.Prop best = null;
        double bd = 1600;
        for (Decor.Prop k : decor.near(p.center(), 40)) {
            if (!k.def.id.equals("ticket_kiosk")) continue;
            if (p.owner != null && !p.owner.equals(k.owner)) continue;
            double dist = k.center().distanceSquared(p.center());
            if (dist < bd) {
                bd = dist;
                best = k;
            }
        }
        p.data = best != null ? kiosk(best)[0] : "Zoo";
        decor.save(p);
    }

    private void gate(Decor d, Decor.Prop p, long tick) {
        if (p.data.isEmpty()) bind(p);
        for (Player pl : p.world.getNearbyPlayers(p.center(), 1.8)) {
            if (pl.getGameMode() == GameMode.SPECTATOR) continue;
            if (isStaff(pl, p)) {
                d.open(p, 40);
                continue;
            }
            if (p.open) {
                d.open(p, 40); // keep it open while someone with a ticket walks through
                continue;
            }
            int slot = validTicket(pl, p.data);
            if (slot >= 0) {
                ItemStack it = pl.getInventory().getItem(slot);
                String v = it.getItemMeta().getPersistentDataContainer().get(ticketKey, PersistentDataType.STRING);
                if (v != null && v.endsWith("|1")) it.setAmount(it.getAmount() - 1);
                d.open(p, 50);
                pl.sendActionBar(Component.text("Welcome to " + p.data + "!", NamedTextColor.GREEN));
            } else {
                long now = System.currentTimeMillis();
                if (now - told.getOrDefault(pl.getUniqueId(), 0L) > 3000) {
                    told.put(pl.getUniqueId(), now);
                    pl.sendActionBar(Component.text("A ticket for " + p.data + " is needed. Buy one at the kiosk.", NamedTextColor.RED));
                }
            }
        }
    }

    private void exitGate(Decor d, Decor.Prop p, long tick) {
        if (!p.world.getNearbyPlayers(p.center(), 1.8).isEmpty()) d.open(p, 30);
    }

    private void staffGate(Decor d, Decor.Prop p, long tick) {
        for (Player pl : p.world.getNearbyPlayers(p.center(), 1.8)) {
            if (isStaff(pl, p)) {
                d.open(p, 40);
            } else {
                long now = System.currentTimeMillis();
                if (now - told.getOrDefault(pl.getUniqueId(), 0L) > 3000) {
                    told.put(pl.getUniqueId(), now);
                    pl.sendActionBar(Component.text("Staff only.", NamedTextColor.RED));
                }
            }
        }
    }

    // ------------------------------------------------------------ feeding trough, info board, bin

    /** Trough data: food|fed. */
    private boolean trough(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        String[] f = p.data.split("\\|");
        int food = f.length > 0 && !f[0].isEmpty() ? Integer.parseInt(f[0]) : 0;
        int fed = f.length > 1 ? Integer.parseInt(f[1]) : 0;
        if (hand != null && !hand.isEmpty() && hand.getType().isEdible() && food < 32) {
            int put = Math.min(hand.getAmount(), 32 - food);
            if (pl.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - put);
            food += put;
            p.data = food + "|" + fed;
            d.save(p);
            pl.getWorld().playSound(p.center(), "minecraft:entity.item.pickup", 0.8f, 0.9f);
        }
        pl.sendActionBar(Component.text("Food: " + food + "   Animals fed: " + fed, NamedTextColor.GOLD));
        return true;
    }

    private void troughTick(Decor d, Decor.Prop p, long tick) {
        String[] f = p.data.split("\\|");
        int food = f.length > 0 && !f[0].isEmpty() ? Integer.parseInt(f[0]) : 0;
        if (food <= 0) return;
        List<LivingEntity> hungry = new ArrayList<>();
        for (Animals.Animal a : plugin.animals().all()) {
            LivingEntity e = a.entity;
            if (e != null && e.isValid() && e.getWorld() == p.world && e.getLocation().distanceSquared(p.center()) <= 36) hungry.add(e);
        }
        if (hungry.isEmpty() || ((tick / 10) + p.x + p.z) % 4 != 0) return; // a bite every ~6 s
        LivingEntity e = hungry.get(0);
        food--;
        int fed = (f.length > 1 ? Integer.parseInt(f[1]) : 0) + 1;
        p.data = food + "|" + fed;
        d.save(p);
        e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight() + 0.2, 0), 3, 0.3, 0.2, 0.3, 0);
        e.getWorld().playSound(e.getLocation(), "minecraft:entity.generic.eat", 0.5f, 1f);
    }

    private boolean info(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        Animals.Animal best = null;
        double bd = 100;
        for (Animals.Animal a : plugin.animals().all()) {
            LivingEntity e = a.entity;
            if (e == null || !e.isValid() || e.getWorld() != p.world) continue;
            double dist = e.getLocation().distanceSquared(p.center());
            if (dist < bd) {
                bd = dist;
                best = a;
            }
        }
        if (best == null) {
            pl.sendMessage(Component.text("There is no animal near this board.", NamedTextColor.GRAY));
            return true;
        }
        Species s = best.species;
        pl.sendMessage(Component.text("--- " + s.displayName + " ---", NamedTextColor.GOLD));
        pl.sendMessage(Component.text(s.notes, NamedTextColor.GRAY));
        pl.sendMessage(Component.text("In the wild: " + s.hint, NamedTextColor.DARK_GREEN));
        return true;
    }

    private boolean bin(Decor d, Decor.Prop p, Player pl, ItemStack hand) {
        if (hand == null || hand.isEmpty()) return true;
        if (pl.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        pl.getWorld().playSound(p.center(), "minecraft:block.barrel.close", 0.8f, 1.2f);
        pl.sendActionBar(Component.text("Thank you for keeping the zoo tidy!", NamedTextColor.GREEN));
        return true;
    }
}
