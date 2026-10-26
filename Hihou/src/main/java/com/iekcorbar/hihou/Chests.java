package com.iekcorbar.hihou;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The five chest types and everything that hangs off them: unlocking with the matching key,
 * per-chest loot stored in chests.json, the take-GUI (you can only ever TAKE from a found chest,
 * never put in), the staff edit-GUI, lucky chests that draw from the config pool, event chests
 * that are locked outside of an event window, and treasure maps.
 *
 * Chests are placeable props from the shared propkit Decor engine: solid base (barrier), their own
 * openable model (closed + _open), unbreakable by hand (break-hits), no vanilla chest GUI - the
 * content is not a block inventory but the store list, so a pickaxe through the chunk can never
 * duplicate it.
 */
final class Chests implements Listener {
    static final String[] TYPES = {"treasure", "event", "vote", "lucky", "victory"};
    private static final int SLOTS = 27;

    final Decor decor;
    final ChestStore store;
    private final HihouPlugin plugin;
    private final Keys keys;
    private boolean eventOpen;

    Chests(HihouPlugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
        store = new ChestStore(plugin);
        List<DecorDef> defs = new ArrayList<>();
        for (String t : TYPES) {
            defs.add(new DecorDef(t + "_chest", Keys.cap(t) + " Chest", loreFor(t))
                    .openable()
                    .use((d, p, pl, hand) -> unlock(p, pl, hand)));
        }
        decor = new Decor(plugin, "hihou", defs, plugin.getConfig().getInt("break-hits", 100));
    }

    private static String loreFor(String t) {
        return switch (t) {
            case "treasure" -> "A hidden prize - find it, then find the key.";
            case "event" -> "Only opens while a Hihou event is running.";
            case "vote" -> "One key per vote you cast on a listing site.";
            case "lucky" -> "Draws one random prize from the lucky pool per unlock.";
            default -> "The winner's reward chest on Game Island.";
        };
    }

    DecorDef def(String type) {
        return decor.defs.get(type + "_chest");
    }

    boolean eventOpen() {
        return eventOpen;
    }

    void eventOpen(boolean v) {
        eventOpen = v;
    }

    // ------------------------------------------------------------ unlocking

    private boolean unlock(Decor.Prop p, Player pl, ItemStack hand) {
        String type = p.def.id.substring(0, p.def.id.length() - "_chest".length());
        String kt = keys.typeOf(hand);
        if (!type.equals(kt)) {
            pl.sendActionBar(Component.text("Locked - this chest needs the " + type + " key.", NamedTextColor.RED));
            return true;
        }
        if (type.equals("event") && !eventOpen) {
            pl.sendActionBar(Component.text("No event is running right now.", NamedTextColor.RED));
            return true;
        }
        // consume one key - via the live inventory reference, not the event's copy of the stack
        ItemStack live = pl.getInventory().getItemInMainHand();
        if (live.getAmount() > 1) live.setAmount(live.getAmount() - 1);
        else pl.getInventory().setItemInMainHand(null);

        decor.open(p, 60);
        World w = p.world;
        w.playSound(p.center(), "minecraft:block.chest.open", 1f, 1.1f);
        w.playSound(p.center(), "minecraft:entity.player.levelup", 0.6f, 1.5f);
        if (announce()) {
            String msg = Keys.cap(type) + " Chest opened by " + pl.getName() + ".";
            if (type.equals("treasure") || type.equals("victory")) msg += " Somewhere..."; // never leak coordinates
            Bukkit.broadcast(Component.text("秘宝 " + msg, NamedTextColor.GOLD));
        }
        if (type.equals("lucky")) {
            lucky(pl, p);
            return true;
        }
        openLoot(pl, p);
        return true;
    }

    private boolean announce() {
        return plugin.getConfig().getBoolean("announce", true);
    }

    /** Lucky chests draw from the config pool instead of stored loot - the chest can be farmed forever. */
    private void lucky(Player pl, Decor.Prop p) {
        List<String> pool = plugin.getConfig().getStringList("lucky-pool");
        int total = 0;
        List<Object[]> rolls = new ArrayList<>(); // {Material, amount, weight}
        for (String entry : pool) {
            String[] f = entry.trim().split("\\s+");
            Material m = Material.matchMaterial(f[0]);
            if (m == null || !m.isItem()) continue;
            int amount = f.length > 1 ? parseInt(f[1], 1) : 1;
            int weight = f.length > 2 ? parseInt(f[2], 1) : 10;
            rolls.add(new Object[]{m, amount, weight});
            total += weight;
        }
        if (rolls.isEmpty() || total <= 0) {
            pl.sendActionBar(Component.text("The lucky pool is empty - tell staff.", NamedTextColor.RED));
            return;
        }
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
        Object[] pick = rolls.get(rolls.size() - 1);
        for (Object[] o : rolls) {
            r -= (int) o[2];
            if (r < 0) {
                pick = o;
                break;
            }
        }
        ItemStack prize = new ItemStack((Material) pick[0], (int) pick[1]);
        for (ItemStack left : pl.getInventory().addItem(prize).values())
            p.world.dropItemNaturally(p.center(), left);
        p.world.playSound(p.center(), "minecraft:ui.toast.challenge_complete", 0.8f, 1.2f);
        pl.sendMessage(Component.text("Lucky draw: ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(pretty(prize), NamedTextColor.WHITE)));
        if (announce())
            Bukkit.broadcast(Component.text("秘宝 " + pl.getName() + " drew " + pretty(prize) + " from a Lucky Chest!", NamedTextColor.LIGHT_PURPLE));
    }

    private static String pretty(ItemStack it) {
        return it.getAmount() + "x " + it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ------------------------------------------------------------ the chest GUIs

    /** Holder so click/close handlers know WHICH chest and whether this is a staff edit. */
    static final class ChestHolder implements InventoryHolder {
        final String id;
        final boolean edit;
        Inventory inv;

        ChestHolder(String id, boolean edit) {
            this.id = id;
            this.edit = edit;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void openLoot(Player pl, Decor.Prop p) {
        String id = ChestStore.id(p.center());
        List<ItemStack> loot = store.loot(id);
        boolean empty = loot.stream().allMatch(s -> s == null || s.getType().isAir());
        if (empty) {
            pl.sendActionBar(Component.text("The chest is empty.", NamedTextColor.GRAY));
            return;
        }
        ChestHolder h = new ChestHolder(id, false);
        h.inv = Bukkit.createInventory(h, SLOTS, Component.text(p.def.name));
        for (int i = 0; i < loot.size() && i < SLOTS; i++) h.inv.setItem(i, loot.get(i));
        pl.openInventory(h.inv);
    }

    /** Staff /hihou edit: full control over the stored items of the looked-at chest. */
    void openEdit(Player pl, Decor.Prop p) {
        String id = ChestStore.id(p.center());
        List<ItemStack> loot = store.loot(id);
        ChestHolder h = new ChestHolder(id, true);
        h.inv = Bukkit.createInventory(h, SLOTS, Component.text("Edit: " + p.def.name));
        for (int i = 0; i < loot.size() && i < SLOTS; i++) h.inv.setItem(i, loot.get(i));
        pl.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ChestHolder h)) return;
        if (h.edit) return; // staff mode: normal container behaviour, saved on close
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack taken = e.getView().getTopInventory().getItem(e.getSlot());
        if (taken == null || taken.getType().isAir()) return;
        e.getView().getTopInventory().setItem(e.getSlot(), null);
        Map<Integer, ItemStack> left = ((Player) e.getWhoClicked()).getInventory().addItem(taken);
        for (Map.Entry<Integer, ItemStack> en : left.entrySet())
            e.getView().getTopInventory().setItem(e.getSlot(), en.getValue()); // inventory full: back it goes
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof ChestHolder h)) return;
        store.setLoot(h.id, Arrays.asList(e.getInventory().getContents()));
        store.flush(); // loot counts are small; saving on close is cheap and crash-safe
    }

    // ------------------------------------------------------------ treasure maps

    /** A pre-rendered map centred on the chest: shows the region, NOT a pin - the hunt is the point. */
    ItemStack map(Decor.Prop p) {
        MapView mv = Bukkit.createMap(p.world);
        mv.setCenterX(p.x);
        mv.setCenterZ(p.z);
        mv.setScale(MapView.Scale.CLOSEST);
        ItemStack m = new ItemStack(Material.FILLED_MAP);
        var mm = (org.bukkit.inventory.meta.MapMeta) m.getItemMeta();
        mm.setMapView(mv);
        mm.displayName(Component.text("Treasure Map", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        mm.lore(List.of(
                Component.text("Marks the area around a hidden " + Keys.cap(chestType(p)) + " Chest,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("near X " + (p.x / 32 * 32) + " Z " + (p.z / 32 * 32) + " in " + p.world.getName() + ".", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("The exact spot is yours to find.", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        m.setItemMeta(mm);
        return m;
    }

    private static String chestType(Decor.Prop p) {
        return p.def.id.substring(0, p.def.id.indexOf('_'));
    }
}
