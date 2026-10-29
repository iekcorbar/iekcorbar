package com.iekcorbar.satchel;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.UseRemainder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The backpack menu (a chest GUI: storage rows on top, one row of upgrade slots at the bottom) and what the upgrades do.
 * Contents live in the backpack item itself; the open menu is saved back into it when it closes.
 */
final class Backpacks implements Listener {
    private final SatchelPlugin plugin;
    private final Items items;
    private final Adv adv;
    /** Backpacks that are open right now: bag id -> viewer. While open, upgrades leave that backpack alone. */
    private final Map<UUID, UUID> open = new HashMap<>();

    Backpacks(SatchelPlugin plugin, Items items, Adv adv) {
        this.plugin = plugin;
        this.items = items;
        this.adv = adv;
    }

    // ------------------------------------------------------------ menu

    private static final class Menu implements InventoryHolder {
        final UUID bag;
        final SItem type;
        Inventory inv;

        Menu(UUID bag, SItem type) {
            this.bag = bag;
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        int storage() {
            return type.storage();
        }

        boolean upgradeSlot(int slot) {
            return slot >= storage() && slot < storage() + type.upgradeSlots;
        }
    }

    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        m.setHideTooltip(true);
        it.setItemMeta(m);
        return it;
    }

    private boolean isPane(ItemStack it) {
        return it != null && it.getType() == Material.GRAY_STAINED_GLASS_PANE && it.hasItemMeta() && it.getItemMeta().isHideTooltip();
    }

    void openBag(Player p, int slot) {
        PlayerInventory pi = p.getInventory();
        ItemStack bag = pi.getItem(slot);
        SItem t = items.bagType(bag);
        if (t == null) return;
        UUID id = items.ensureBagId(bag);
        UUID viewer = open.get(id);
        if (viewer != null && Bukkit.getPlayer(viewer) != null && Bukkit.getPlayer(viewer).getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.bag.equals(id)) {
            p.sendActionBar(Component.text("This backpack is open already.", NamedTextColor.RED));
            return;
        }
        pi.setItem(slot, bag);   // the new id is now stored
        Menu menu = new Menu(id, t);
        int size = (t.rows + 1) * 9;
        menu.inv = Bukkit.createInventory(menu, size, Component.text(t.displayName));
        ItemStack[] st = items.storage(bag, t);
        ItemStack[] up = items.upgrades(bag, t);
        for (int i = 0; i < st.length; i++) menu.inv.setItem(i, st[i]);
        ItemStack pane = pane();
        for (int i = 0; i < 9; i++) {
            int slotI = t.storage() + i;
            menu.inv.setItem(slotI, i < t.upgradeSlots ? up[i] : pane);
        }
        open.put(id, p.getUniqueId());
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1f);
        adv.grant(p, "opened");
    }

    /** Finds the backpack item with this id in the player's inventory; returns its slot or -1. */
    private int find(Player p, UUID id) {
        PlayerInventory pi = p.getInventory();
        for (int i = 0; i < pi.getSize(); i++) {
            ItemStack it = pi.getItem(i);
            if (it != null && it.getType() == Material.PAPER && items.bagType(it) != null && id.equals(items.bagId(it))) return i;
        }
        return -1;
    }

    private boolean isOpenBag(Player p, ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || items.bagType(it) == null) return false;
        UUID id = items.bagId(it);
        return id != null && p.getUniqueId().equals(open.get(id));
    }

    private void save(Player p, Menu m) {
        int slot = find(p, m.bag);
        ItemStack[] st = new ItemStack[m.type.storage()];
        ItemStack[] up = new ItemStack[m.type.upgradeSlots];
        for (int i = 0; i < st.length; i++) st[i] = m.inv.getItem(i);
        for (int i = 0; i < up.length; i++) {
            ItemStack u = m.inv.getItem(m.type.storage() + i);
            up[i] = isPane(u) ? null : u;
        }
        if (slot < 0) {
            // should not happen (the backpack is locked while open); never lose the items
            plugin.getLogger().warning("Backpack " + m.bag + " vanished from " + p.getName() + "'s inventory; dropping its contents.");
            for (ItemStack s : st) if (s != null) p.getWorld().dropItemNaturally(p.getLocation(), s);
            for (ItemStack s : up) if (s != null) p.getWorld().dropItemNaturally(p.getLocation(), s);
            return;
        }
        ItemStack bag = p.getInventory().getItem(slot);
        items.write(bag, st, up);
        p.getInventory().setItem(slot, bag);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m) || !(e.getPlayer() instanceof Player p)) return;
        save(p, m);
        open.remove(m.bag);
        if (m.type == SItem.NETHERITE_BACKPACK) { // joke: a completely full netherite backpack is heavy
            boolean full = true;
            for (int i = 0; i < m.storage(); i++) if (m.inv.getItem(i) == null) full = false;
            if (full) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 600, 0));
                p.sendActionBar(Component.text("Back day.", NamedTextColor.GRAY));
                adv.grant(p, "back_day");
            }
        }
        if (m.type.upgradeSlots > 0) {
            boolean all = true;
            for (int i = 0; i < m.type.upgradeSlots; i++) if (items.typeOf(m.inv.getItem(m.storage() + i)) == null) all = false;
            if (all) adv.grant(p, "full");
        }
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6f, 0.9f);
    }

    private void wizard(Player p) {
        p.sendActionBar(Component.text("Nice try, wizard.", NamedTextColor.LIGHT_PURPLE));
        adv.grant(p, "wizard");
    }

    /** Save before the death drops are fixed, and put the saved backpack in the drop list. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m)) return;
        save(p, m);
        int slot = find(p, m.bag);
        if (slot >= 0) {
            ItemStack fresh = p.getInventory().getItem(slot);
            for (int i = 0; i < e.getDrops().size(); i++) {
                ItemStack d = e.getDrops().get(i);
                if (items.bagType(d) != null && m.bag.equals(items.bagId(d))) e.getDrops().set(i, fresh);
            }
            if (e.getKeepInventory()) p.getInventory().setItem(slot, fresh);
        }
    }

    void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
    }

    // ------------------------------------------------------------ opening

    @EventHandler(ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() == null) return;
        Player p = e.getPlayer();
        ItemStack in = e.getItem();
        if (!p.hasPermission("satchel.use")) return;
        int slot = e.getHand() == EquipmentSlot.OFF_HAND ? 40 : p.getInventory().getHeldItemSlot();
        SItem sold = items.fromTrader(in);
        if (sold != null) {
            // bought from a wandering trader: swap the plain paper for the real backpack on first use
            ItemStack real = items.create(sold, 1);
            real.setAmount(in.getAmount());
            p.getInventory().setItem(slot, real);
            e.setCancelled(true);
            return;
        }
        if (items.bagType(in) == null) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && p.isSneaking()) {
            if (e.useInteractedBlock() != org.bukkit.event.Event.Result.DENY && containerAction(p, slot, e.getClickedBlock())) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        openBag(p, slot);
    }

    /** Deposit / Restock upgrades: sneak + right-click a container. Only kinds both sides already hold are moved. */
    private boolean containerAction(Player p, int slot, org.bukkit.block.Block b) {
        if (b == null || !(b.getState() instanceof org.bukkit.block.Container c)) return false;
        ItemStack bag = p.getInventory().getItem(slot);
        SItem t = items.bagType(bag);
        if (t == null || busy(p, bag)) return false;
        boolean dep = items.has(bag, SItem.DEPOSIT_UPGRADE), res = items.has(bag, SItem.RESTOCK_UPGRADE);
        if (!dep && !res) return false;
        ItemStack[] st = items.storage(bag, t);
        Inventory inv = c.getInventory();
        int moved = 0;
        if (dep) {
            for (int i = 0; i < st.length; i++) {
                if (st[i] == null || !Items.contains(inv.getContents(), st[i])) continue;
                int before = st[i].getAmount();
                ItemStack left = inv.addItem(st[i].clone()).values().stream().findFirst().orElse(null);
                int rest = left == null ? 0 : left.getAmount();
                moved += before - rest;
                st[i] = rest == 0 ? null : left;
            }
        }
        if (res) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || s.isEmpty() || !Items.contains(st, s) || items.bagType(s) != null) continue;
                ItemStack left = Items.insert(st, s);
                int rest = left == null ? 0 : left.getAmount();
                moved += s.getAmount() - rest;
                inv.setItem(i, left);
            }
        }
        if (moved > 0) {
            items.write(bag, st, items.upgrades(bag, t));
            p.getInventory().setItem(slot, bag);
            p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.7f, 1f);
            p.sendActionBar(Component.text("Moved " + moved + " items.", NamedTextColor.GREEN));
        }
        return true;
    }

    /** Refill upgrade: top up hotbar stacks from the backpack. */
    private void refill(Player p, int[] bags) {
        PlayerInventory pi = p.getInventory();
        for (int slot : bags) {
            ItemStack bag = pi.getItem(slot);
            if (!items.has(bag, SItem.REFILL_UPGRADE) || busy(p, bag)) continue;
            SItem t = items.bagType(bag);
            ItemStack[] st = items.storage(bag, t);
            boolean changed = false;
            for (int h = 0; h < 9; h++) {
                ItemStack hot = pi.getItem(h);
                if (hot == null || hot.isEmpty() || hot.getMaxStackSize() <= 1 || items.typeOf(hot) != null) continue;
                int room = hot.getMaxStackSize() - hot.getAmount();
                if (room <= 0 || hot.getAmount() > hot.getMaxStackSize() / 2) continue;
                for (int i = 0; i < st.length && room > 0; i++) {
                    if (st[i] == null || !st[i].isSimilar(hot)) continue;
                    int n = Math.min(room, st[i].getAmount());
                    st[i].setAmount(st[i].getAmount() - n);
                    if (st[i].getAmount() <= 0) st[i] = null;
                    hot.setAmount(hot.getAmount() + n);
                    room -= n;
                    changed = true;
                }
                pi.setItem(h, hot);
            }
            if (changed) {
                items.write(bag, st, items.upgrades(bag, t));
                pi.setItem(slot, bag);
            }
        }
    }

    // ------------------------------------------------------------ keeping the menu consistent

    private boolean allowed(Menu m, int slot, ItemStack in) {
        if (in == null || in.isEmpty()) return true;
        if (slot < m.storage()) return items.bagType(in) == null;
        if (!m.upgradeSlot(slot)) return false;
        SItem t = items.typeOf(in);
        if (t == null || !t.upgrade() || in.getAmount() != 1) return false;
        for (int i = 0; i < m.type.upgradeSlots; i++) {
            if (m.storage() + i == slot) continue;
            if (items.typeOf(m.inv.getItem(m.storage() + i)) == t) return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        // the open backpack itself can't be moved, dropped or swapped
        if (isOpenBag(p, e.getCurrentItem()) || isOpenBag(p, e.getCursor())) {
            e.setCancelled(true);
            if (isOpenBag(p, e.getCursor())) wizard(p);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY && isOpenBag(p, p.getInventory().getItem(e.getHotbarButton()))) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND && isOpenBag(p, p.getInventory().getItemInOffHand())) {
            e.setCancelled(true);
            return;
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        int top = e.getInventory().getSize();
        boolean inTop = e.getRawSlot() >= 0 && e.getRawSlot() < top;
        if (inTop) {
            int slot = e.getRawSlot();
            if (slot >= m.storage() && !m.upgradeSlot(slot)) {
                e.setCancelled(true);
                return;
            }
            ItemStack incoming = null;
            if (e.getClick() == ClickType.NUMBER_KEY) incoming = p.getInventory().getItem(e.getHotbarButton());
            else if (e.getClick() == ClickType.SWAP_OFFHAND) incoming = p.getInventory().getItemInOffHand();
            else if (e.getAction() == InventoryAction.PLACE_ALL || e.getAction() == InventoryAction.PLACE_ONE || e.getAction() == InventoryAction.PLACE_SOME
                    || e.getAction() == InventoryAction.SWAP_WITH_CURSOR) incoming = e.getCursor();
            if (!allowed(m, slot, incoming)) {
                e.setCancelled(true);
                if (incoming != null && items.bagType(incoming) != null) wizard(p);
                return;
            }
            if (m.upgradeSlot(slot) && incoming != null && !incoming.isEmpty() && incoming.getAmount() > 1
                    && e.getAction() != InventoryAction.PLACE_ONE) {
                e.setCancelled(true);
            }
            return;
        }
        // shift-click from the player's inventory into the menu: do it by hand so upgrades and rules are respected
        if (e.isShiftClick() && e.getClickedInventory() == p.getInventory()) {
            e.setCancelled(true);
            ItemStack src = e.getCurrentItem();
            if (src != null && !src.isEmpty() && items.bagType(src) != null) wizard(p);
            if (src == null || src.isEmpty() || items.bagType(src) != null) return;
            SItem t = items.typeOf(src);
            if (t != null && t.upgrade()) {
                for (int i = 0; i < m.type.upgradeSlots; i++) {
                    int s = m.storage() + i;
                    if (m.inv.getItem(s) == null && allowed(m, s, src.asOne())) {
                        m.inv.setItem(s, src.asOne());
                        src.setAmount(src.getAmount() - 1);
                        e.setCurrentItem(src.isEmpty() ? null : src);
                        return;
                    }
                }
            }
            ItemStack[] st = new ItemStack[m.storage()];
            for (int i = 0; i < st.length; i++) st[i] = m.inv.getItem(i);
            ItemStack rest = Items.insert(st, src);
            for (int i = 0; i < st.length; i++) m.inv.setItem(i, st[i]);
            e.setCurrentItem(rest);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getInventory().getHolder() instanceof Menu m)) return;
        int top = e.getInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw >= top) continue;
            if (raw >= m.storage() || items.bagType(e.getOldCursor()) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isOpenBag(e.getPlayer(), e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ upgrades

    private int[] carried(Player p) {
        PlayerInventory pi = p.getInventory();
        int[] tmp = new int[pi.getSize()];
        int n = 0;
        for (int i = 0; i < pi.getSize(); i++) {
            ItemStack it = pi.getItem(i);
            if (it != null && it.getType() == Material.PAPER && it.hasItemMeta() && items.bagType(it) != null) tmp[n++] = i;
        }
        return java.util.Arrays.copyOf(tmp, n);
    }

    private boolean busy(Player p, ItemStack bag) {
        UUID id = items.bagId(bag);
        return id != null && open.containsKey(id);
    }

    /** Pickup upgrade: items the player walks over go into backpacks that have it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.getGameMode() == GameMode.SPECTATOR) return;
        Item entity = e.getItem();
        ItemStack stack = entity.getItemStack();
        if (items.bagType(stack) != null) return;
        boolean matching = plugin.getConfig().getString("pickup.mode", "all").equalsIgnoreCase("matching");
        ItemStack rest = stack.clone();
        boolean changed = false;
        for (int slot : carried(p)) {
            ItemStack bag = p.getInventory().getItem(slot);
            if (!items.has(bag, SItem.PICKUP_UPGRADE) || busy(p, bag)) continue;
            SItem t = items.bagType(bag);
            ItemStack[] st = items.storage(bag, t);
            if (matching && !Items.contains(st, rest)) continue;
            ItemStack left = Items.insert(st, rest);
            int taken = rest.getAmount() - (left == null ? 0 : left.getAmount());
            if (taken <= 0) continue;
            items.write(bag, st, items.upgrades(bag, t));
            p.getInventory().setItem(slot, bag);
            changed = true;
            rest = left;
            if (rest == null) break;
        }
        if (!changed) return;
        if (rest == null) {
            e.setCancelled(true);
            p.playPickupItemAnimation(entity);
            entity.remove();
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.4f);
        } else {
            entity.setItemStack(rest);
            e.setCancelled(true);   // the remainder stays on the ground; the player picks it up on the next tick
        }
    }

    /** Magnet and feeding, a few times a second. Only players who carry a backpack cost anything. */
    void tick(long tick) {
        double radius = plugin.getConfig().getDouble("magnet.radius", 6);
        double speed = plugin.getConfig().getDouble("magnet.speed", 0.35);
        int missing = plugin.getConfig().getInt("feeding.hunger-missing", 6);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            boolean magnet = false, pickup = false, feed = false, refill = false;
            int[] bags = carried(p);
            if (bags.length == 0) continue;
            for (int slot : bags) {
                ItemStack bag = p.getInventory().getItem(slot);
                if (busy(p, bag)) continue;
                magnet |= items.has(bag, SItem.MAGNET_UPGRADE);
                pickup |= items.has(bag, SItem.PICKUP_UPGRADE);
                feed |= items.has(bag, SItem.FEEDING_UPGRADE);
                refill |= items.has(bag, SItem.REFILL_UPGRADE);
            }
            if (refill && tick % 20 == 0) refill(p, bags);
            if (magnet) pull(p, radius, speed, pickup);
            if (feed && tick % 20 == 0 && 20 - p.getFoodLevel() >= missing) feed(p, bags);
        }
    }

    private void pull(Player p, double radius, double speed, boolean pickup) {
        Location to = p.getLocation().add(0, 0.5, 0);
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (!(en instanceof Item it) || it.getPickupDelay() > 0 || it.isDead()) continue;
            if (items.bagType(it.getItemStack()) != null) continue;
            Vector v = to.toVector().subtract(it.getLocation().toVector());
            double d = v.length();
            if (d < 1.2) continue;   // close enough to be picked up normally
            it.setVelocity(v.normalize().multiply(Math.min(speed, d / 4 + 0.1)));
        }
    }

    private void feed(Player p, int[] bags) {
        for (int slot : bags) {
            ItemStack bag = p.getInventory().getItem(slot);
            if (!items.has(bag, SItem.FEEDING_UPGRADE) || busy(p, bag)) continue;
            SItem t = items.bagType(bag);
            ItemStack[] st = items.storage(bag, t);
            int best = -1;
            int bestNutrition = 0;
            for (int i = 0; i < st.length; i++) {
                FoodProperties food = st[i] == null ? null : st[i].getData(DataComponentTypes.FOOD);
                if (food == null || food.canAlwaysEat()) continue;
                Consumable c = st[i].getData(DataComponentTypes.CONSUMABLE);
                if (c != null && !c.consumeEffects().isEmpty()) continue;   // potions, suspicious stew, rotten flesh...
                int missing = 20 - p.getFoodLevel();
                // the food that fills the most without wasting much
                int score = Math.min(food.nutrition(), missing) * 4 - Math.max(0, food.nutrition() - missing);
                if (best < 0 || score > bestNutrition) {
                    best = i;
                    bestNutrition = score;
                }
            }
            if (best < 0) continue;
            ItemStack food = st[best];
            FoodProperties props = food.getData(DataComponentTypes.FOOD);
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + props.nutrition()));
            p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + props.saturation()));
            UseRemainder rem = food.getData(DataComponentTypes.USE_REMAINDER);
            ItemStack back = rem == null ? null : rem.transformInto().clone();
            food.setAmount(food.getAmount() - 1);
            if (food.getAmount() <= 0) st[best] = null;
            if (back != null) {
                ItemStack left = Items.insert(st, back);
                if (left != null) p.getInventory().addItem(left).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            }
            items.write(bag, st, items.upgrades(bag, t));
            p.getInventory().setItem(slot, bag);
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.6f, 1f);
            adv.grant(p, "fed");
            return;
        }
    }

    // ------------------------------------------------------------ everlasting

    private final java.util.Map<java.util.UUID, Integer> sinking = new java.util.HashMap<>();

    private boolean everlasting(Entity en) {
        return plugin.getConfig().getBoolean("everlasting.enabled", true) && en instanceof Item it
                && items.bagType(it.getItemStack()) != null && items.has(it.getItemStack(), SItem.EVERLASTING_UPGRADE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        if (everlasting(e.getEntity())) {
            e.setCancelled(true);
            e.getEntity().setTicksLived(1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (everlasting(e.getEntity())) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Entity en = e.getEntity();
                Location l = en.getWorld().getSpawnLocation().add(0.5, 1, 0.5);
                en.setVelocity(new Vector());
                en.teleportAsync(l);
            }
            return;
        }
        // joke: an unprotected backpack that lands in lava sinks slowly - with bubbles, with dignity
        if (e.getEntity() instanceof Item it && e.getCause() == EntityDamageEvent.DamageCause.LAVA
                && items.bagType(it.getItemStack()) != null && !sinking.containsKey(it.getUniqueId())) {
            e.setCancelled(true);
            zink(it);
        }
    }

    private void zink(Item it) {
        sinking.put(it.getUniqueId(), 0);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            Integer t = sinking.get(it.getUniqueId());
            if (t == null || t > 30 || !it.isValid()) {       // opgepakt, vergaan of af
                sinking.remove(it.getUniqueId());
                task.cancel();
                return;
            }
            sinking.put(it.getUniqueId(), t + 1);
            Location l = it.getLocation();
            it.setVelocity(new Vector(0, -0.05, 0));
            l.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE, l.clone().add(0, 0.2, 0), 2, 0.08, 0.05, 0.08, 0);
            if (t == 30) {
                sinking.remove(it.getUniqueId());
                task.cancel();
                Location last = it.getLocation();
                it.remove();
                last.getWorld().playSound(last, "minecraft:block.lava.extinguish", 1f, 0.8f);
                Player nearest = null;
                double best = 64;
                for (Player q : last.getWorld().getNearbyPlayers(last, 8)) {
                    double d = q.getLocation().distanceSquared(last);
                    if (d < best) { best = d; nearest = q; }
                    q.sendActionBar(Component.text("It goes down with dignity.", NamedTextColor.GRAY));
                }
                if (nearest != null) adv.grant(nearest, "sink");
            }
        }, 2L, 2L);
    }

    List<ItemStack> allItems() {
        return java.util.Arrays.stream(SItem.values()).map(t -> items.create(t, 1)).toList();
    }
}
