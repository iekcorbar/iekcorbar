package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dragon's saddlebags (9 slots, a chest on the saddle) and the pet menu (/wildlife pet).
 * Bag contents live in the dragon's own PDC (serialized items), so they survive restarts and
 * chunk unloads; if the dragon dies they drop where it fell.
 */
final class DragonPets implements Listener {
    private static final int BAG_SLOTS = 9;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final WildlifePlugin plugin;
    private final NamespacedKey bagKey;
    /** dragon -> its open bag inventory (one owner, so at most one viewer). */
    private final Map<UUID, Inventory> open = new HashMap<>();
    /** pets whose owner clicked Release once: the second click frees it. */
    private final Map<UUID, Long> releaseAsk = new HashMap<>();

    DragonPets(WildlifePlugin plugin) {
        this.plugin = plugin;
        this.bagKey = new NamespacedKey(plugin, "dragon_bag");
        initEggs();
    }

    private static final class BagHolder implements InventoryHolder {
        final UUID dragon;
        Inventory inv;
        BagHolder(UUID dragon) { this.dragon = dragon; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class MenuHolder implements InventoryHolder {
        final UUID dragon;
        Inventory inv;
        MenuHolder(UUID dragon) { this.dragon = dragon; }
        @Override public Inventory getInventory() { return inv; }
    }

    // ------------------------------------------------------------ saddlebags

    boolean hasBag(LivingEntity e) {
        return e.getPersistentDataContainer().has(bagKey, PersistentDataType.BYTE_ARRAY);
    }

    /** Right-click with a chest: fit saddlebags (one chest). Returns true when the click was used. */
    boolean installBag(Player p, LivingEntity e, ItemStack hand) {
        if (!DragonAI.allowed(p, "bag")) return true;
        if (hasBag(e)) {
            p.sendActionBar(Component.text("It already carries saddlebags.", NamedTextColor.GRAY));
            return true;
        }
        e.getPersistentDataContainer().set(bagKey, PersistentDataType.BYTE_ARRAY, new byte[0]);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        e.getWorld().playSound(e.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.8f);
        p.sendMessage(Component.text("Saddlebags fitted. Open them with /wildlife pet bag.", NamedTextColor.GOLD));
        plugin.adv().grant(p, "dragon_bags");
        return true;
    }

    void openBag(Player p, LivingEntity e) {
        if (!DragonAI.allowed(p, "bag")) return;
        if (!hasBag(e)) {
            p.sendMessage(Component.text("This dragon has no saddlebags: right-click it with a chest.", NamedTextColor.RED));
            return;
        }
        if (!p.getUniqueId().equals(DragonAI.tamer(e))) return;
        if (e.getWorld() != p.getWorld() || e.getLocation().distanceSquared(p.getLocation()) > 64) {
            p.sendMessage(Component.text("Your dragon is too far away.", NamedTextColor.RED));
            return;
        }
        Inventory inv = open.get(e.getUniqueId());
        if (inv != null && !inv.getViewers().isEmpty()) {
            p.sendMessage(Component.text("The saddlebags are already open.", NamedTextColor.RED));
            return;
        }
        BagHolder h = new BagHolder(e.getUniqueId());
        inv = Bukkit.createInventory(h, BAG_SLOTS, Component.text("Saddlebags", NamedTextColor.DARK_GRAY));
        h.inv = inv;
        byte[] raw = e.getPersistentDataContainer().get(bagKey, PersistentDataType.BYTE_ARRAY);
        if (raw != null && raw.length > 0) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(raw);
            for (int i = 0; i < Math.min(items.length, BAG_SLOTS); i++) inv.setItem(i, items[i]);
        }
        open.put(e.getUniqueId(), inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
    }

    private void save(UUID dragon, Inventory inv, Player closer) {
        ItemStack[] items = inv.getContents();
        boolean any = false;
        for (ItemStack it : items) if (it != null && !it.isEmpty()) any = true;
        Entity en = Bukkit.getEntity(dragon);
        if (en instanceof LivingEntity le && le.isValid() && hasBag(le)) {
            le.getPersistentDataContainer().set(bagKey, PersistentDataType.BYTE_ARRAY, any ? ItemStack.serializeItemsAsBytes(items) : new byte[0]);
        } else if (any && closer != null) {                                 // the dragon is gone: nothing may vanish
            for (ItemStack it : items) if (it != null && !it.isEmpty()) closer.getWorld().dropItem(closer.getLocation(), it);
            inv.clear();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BagHolder h)) return;
        save(h.dragon, e.getInventory(), e.getPlayer() instanceof Player p ? p : null);
        open.remove(h.dragon);
    }

    /** A dying dragon spills its bags (the live contents when they are open right now). */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity d = e.getEntity();
        if (!hasBag(d)) return;
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = open.remove(d.getUniqueId());
        if (inv != null) {
            for (ItemStack it : inv.getContents()) if (it != null && !it.isEmpty()) out.add(it);
            inv.clear();
            for (var v : new ArrayList<>(inv.getViewers())) v.closeInventory();
        } else {
            byte[] raw = d.getPersistentDataContainer().get(bagKey, PersistentDataType.BYTE_ARRAY);
            if (raw != null && raw.length > 0) for (ItemStack it : ItemStack.deserializeItemsFromBytes(raw)) if (it != null && !it.isEmpty()) out.add(it);
        }
        out.add(new ItemStack(Material.CHEST));
        for (ItemStack it : out) d.getWorld().dropItemNaturally(d.getLocation(), it);
    }

    // ------------------------------------------------------------ dragon eggs

    private static final String EGG_ID = "dragon_egg";
    private NamespacedKey eggLaid, eggLook, eggOwner;

    private void initEggs() {
        eggLaid = new NamespacedKey(plugin, "egg_laid");
        eggLook = new NamespacedKey(plugin, "egg_look");
        eggOwner = new NamespacedKey(plugin, "egg_owner");
        Bukkit.getScheduler().runTaskTimer(plugin, this::hatchTick, 200L, 100L);
    }

    /** A dragon egg: carry it in your inventory and it hatches (config dragon.egg-hatch-minutes) into a baby that is yours. */
    ItemStack egg(String look, UUID owner) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Dragon Egg", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Warm. Something ticks inside.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Carry it in your inventory and it hatches.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Wildlife", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)));
        var pdc = m.getPersistentDataContainer();
        pdc.set(plugin.items().itemKey, PersistentDataType.STRING, EGG_ID);
        pdc.set(eggLaid, PersistentDataType.LONG, System.currentTimeMillis());
        pdc.set(eggLook, PersistentDataType.STRING, look);
        if (owner != null) pdc.set(eggOwner, PersistentDataType.STRING, owner.toString());
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("zoo", "egg_dragon"));
        return it;
    }

    /** Two dragons bred: an egg appears between them instead of a baby. */
    void layEgg(org.bukkit.Location at, String look, UUID owner) {
        at.getWorld().dropItemNaturally(at, egg(look, owner));
        at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_TURTLE_LAY_EGG, 1f, 0.7f);
        at.getWorld().spawnParticle(org.bukkit.Particle.HEART, at.clone().add(0, 0.6, 0), 6, 0.4, 0.3, 0.4, 0);
        for (Player near : at.getWorld().getNearbyPlayers(at, 24)) {
            plugin.adv().grant(near, "dragon_breeder");
            near.sendActionBar(Component.text("An egg! Carry it, or sneak-click a hay block to make a nest.", NamedTextColor.GOLD));
        }
    }

    private void hatchTick() {
        long ms = 60_000L * Math.max(1, plugin.getConfig().getInt("dragon.egg-hatch-minutes", 8));
        long now = System.currentTimeMillis();
        // eggs carried by their owner
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] inv = p.getInventory().getContents();
            for (int i = 0; i < inv.length; i++) {
                ItemStack it = inv[i];
                if (!isEgg(it) || !ready(it, now, ms) || !mine(it, p)) continue;
                org.bukkit.Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).multiply(1.8));
                if (!hatch(it, at, p)) continue;                               // model not ready: try again next time
                it.setAmount(it.getAmount() - 1);
                p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
                break;                                                         // one egg per check keeps it calm
            }
        }
        // eggs lying in a nest (a hay block): they stay warm with nobody around
        for (UUID id : new ArrayList<>(nests)) {
            Entity en = Bukkit.getEntity(id);
            if (en == null) continue;                                          // its chunk is not loaded: it waits
            if (!(en instanceof org.bukkit.entity.Item item) || !item.isValid() || !isEgg(item.getItemStack())) { nests.remove(id); continue; }
            ItemStack it = item.getItemStack();
            if (!ready(it, now, ms)) continue;
            String owner = it.getItemMeta().getPersistentDataContainer().get(eggOwner, PersistentDataType.STRING);
            Player p = owner == null ? null : Bukkit.getPlayer(UUID.fromString(owner));
            if (p == null || p.getWorld() != item.getWorld()) continue;       // its keeper must be around to be adopted
            if (hatch(it, item.getLocation().add(0, 0.4, 0), p)) {
                nests.remove(id);
                item.remove();
            }
        }
    }

    private boolean isEgg(ItemStack it) {
        return it != null && !it.isEmpty() && it.hasItemMeta()
                && EGG_ID.equals(it.getItemMeta().getPersistentDataContainer().get(plugin.items().itemKey, PersistentDataType.STRING));
    }

    private boolean ready(ItemStack it, long now, long ms) {
        Long laid = it.getItemMeta().getPersistentDataContainer().get(eggLaid, PersistentDataType.LONG);
        return laid != null && now - laid >= ms;
    }

    /** Only the keeper (the owner of the parents) can hatch it; an egg without a keeper hatches for whoever carries it. */
    private boolean mine(ItemStack it, Player p) {
        String owner = it.getItemMeta().getPersistentDataContainer().get(eggOwner, PersistentDataType.STRING);
        return owner == null || owner.equals(p.getUniqueId().toString());
    }

    private boolean hatch(ItemStack it, org.bukkit.Location at, Player p) {
        String look = it.getItemMeta().getPersistentDataContainer().get(eggLook, PersistentDataType.STRING);
        Animals.Animal baby = plugin.animals().spawn(Species.DRAGON, at, false, true, look);
        if (baby == null) return false;
        plugin.dragon().adopt(baby, p);
        at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_TURTLE_EGG_HATCH, 1f, 0.8f);
        at.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, at.clone().add(0, 0.5, 0), 14, 0.3, 0.3, 0.3, 0.02);
        p.sendMessage(Component.text("The egg cracks - a baby dragon looks at you, and decides you are its parent.", NamedTextColor.GOLD));
        plugin.adv().grant(p, "dragon_tame");
        return true;
    }

    // ---- the nest: sneak + right-click a hay block with the egg; sneak + right-click it with an empty hand to take it back

    private final java.util.Set<UUID> nests = new java.util.HashSet<>();

    @EventHandler
    public void onNest(org.bukkit.event.player.PlayerInteractEvent ev) {
        if (ev.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || ev.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        var block = ev.getClickedBlock();
        Player p = ev.getPlayer();
        if (block == null || block.getType() != Material.HAY_BLOCK || !p.isSneaking()) return;
        ItemStack held = ev.getItem();
        org.bukkit.Location top = block.getLocation().add(0.5, 1.02, 0.5);
        if (isEgg(held)) {
            ev.setCancelled(true);
            if (!mine(held, p)) { p.sendActionBar(Component.text("This is not your egg.", NamedTextColor.RED)); return; }
            for (Entity en : block.getWorld().getNearbyEntities(top, 0.6, 0.6, 0.6)) if (nests.contains(en.getUniqueId())) { p.sendActionBar(Component.text("The nest is taken.", NamedTextColor.GRAY)); return; }
            ItemStack one = held.clone();
            one.setAmount(1);
            var item = block.getWorld().dropItem(top, one);
            item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            item.setUnlimitedLifetime(true);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setInvulnerable(true);
            nests.add(item.getUniqueId());
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
            p.sendActionBar(Component.text("The egg rests in the straw. It stays warm.", NamedTextColor.GOLD));
            plugin.adv().grant(p, "dragon_nest");
        } else if (held == null || held.isEmpty()) {
            for (Entity en : block.getWorld().getNearbyEntities(top, 0.6, 0.6, 0.6)) {
                if (!(en instanceof org.bukkit.entity.Item item) || !nests.contains(en.getUniqueId()) || !isEgg(item.getItemStack())) continue;
                if (!mine(item.getItemStack(), p)) { p.sendActionBar(Component.text("This is not your egg.", NamedTextColor.RED)); return; }
                ev.setCancelled(true);
                p.getInventory().addItem(item.getItemStack()).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
                nests.remove(en.getUniqueId());
                item.remove();
                return;
            }
        }
    }

    /** Breaking the straw frees the egg. */
    @EventHandler
    public void onNestBreak(org.bukkit.event.block.BlockBreakEvent ev) {
        if (ev.getBlock().getType() != Material.HAY_BLOCK) return;
        org.bukkit.Location top = ev.getBlock().getLocation().add(0.5, 1.02, 0.5);
        for (Entity en : ev.getBlock().getWorld().getNearbyEntities(top, 0.6, 0.6, 0.6)) {
            if (en instanceof org.bukkit.entity.Item item && nests.remove(en.getUniqueId())) {
                item.setCanPlayerPickup(true);
                item.setInvulnerable(false);
            }
        }
    }

    /** Nest eggs remembered across restarts: an unpickable egg item is a nest egg. */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent ev) {
        for (Entity en : ev.getEntities())
            if (en instanceof org.bukkit.entity.Item item && !item.canPlayerPickup() && isEgg(item.getItemStack())) nests.add(en.getUniqueId());
    }

    // ------------------------------------------------------------ the pet menu

    private ItemStack button(Material m, String name, NamedTextColor color, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    void openMenu(Player p, Animals.Animal pet) {
        LivingEntity e = pet.entity;
        MenuHolder h = new MenuHolder(e.getUniqueId());
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Your dragon", NamedTextColor.DARK_GREEN));
        h.inv = inv;
        String name = e.customName() == null ? "Steam Dragon" : PLAIN.serialize(e.customName());
        boolean sit = DragonAI.isSitting(e);
        inv.setItem(4, button(Material.SADDLE, name, NamedTextColor.GOLD,
                sit ? "Sitting." : "Standing.", "Hunger: " + pet.hunger + "/100"));
        inv.setItem(10, sit ? button(Material.FEATHER, "Stand up", NamedTextColor.GREEN, "Let it follow you again.")
                : button(Material.OAK_STAIRS, "Sit", NamedTextColor.YELLOW, "It stays where it is."));
        inv.setItem(12, button(Material.ENDER_PEARL, "Come here", NamedTextColor.AQUA, "Your dragon flies to your side."));
        inv.setItem(14, button(Material.NAME_TAG, "Rename", NamedTextColor.WHITE, "Type /wildlife pet name <name>."));
        inv.setItem(16, hasBag(e) ? button(Material.CHEST, "Saddlebags", NamedTextColor.GOLD, "Open the 9 slots.")
                : button(Material.BARRIER, "No saddlebags", NamedTextColor.RED, "Right-click the dragon with a chest."));
        Long ask = releaseAsk.get(e.getUniqueId());
        boolean sure = ask != null && System.currentTimeMillis() - ask < 8000;
        inv.setItem(22, sure ? button(Material.TNT, "Click again to set it free", NamedTextColor.RED, "This cannot be undone.")
                : button(Material.RED_DYE, "Set free", NamedTextColor.DARK_RED, "Give it back to the wild."));
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof MenuHolder h)) return;
        ev.setCancelled(true);
        if (ev.getClickedInventory() != ev.getInventory() || !(ev.getWhoClicked() instanceof Player p)) return;
        Entity en = Bukkit.getEntity(h.dragon);
        Animals.Animal pet = en == null ? null : plugin.animals().get(en);
        if (pet == null || !en.isValid() || !p.getUniqueId().equals(DragonAI.tamer(pet.entity))) {
            p.closeInventory();
            return;
        }
        LivingEntity e = pet.entity;
        switch (ev.getSlot()) {
            case 10 -> { plugin.dragon().setSitting(pet, p, !DragonAI.isSitting(e)); openMenu(p, pet); }
            case 12 -> {
                p.closeInventory();
                p.sendActionBar(plugin.dragon().come(pet, p) ? Component.text("Your dragon comes to you.", NamedTextColor.GREEN)
                        : Component.text("It cannot come (another world, or someone is riding it).", NamedTextColor.RED));
            }
            case 14 -> {
                p.closeInventory();
                p.sendMessage(Component.text("Type /wildlife pet name <name> to rename it.", NamedTextColor.YELLOW));
            }
            case 16 -> {
                p.closeInventory();
                if (hasBag(e)) openBag(p, e);
            }
            case 22 -> {
                Long ask = releaseAsk.get(e.getUniqueId());
                if (ask != null && System.currentTimeMillis() - ask < 8000) {
                    releaseAsk.remove(e.getUniqueId());
                    p.closeInventory();
                    plugin.dragon().release(pet, p);
                    p.sendMessage(Component.text("The dragon is free again.", NamedTextColor.GRAY));
                } else {
                    releaseAsk.put(e.getUniqueId(), System.currentTimeMillis());
                    openMenu(p, pet);
                }
            }
            default -> { }
        }
    }
}
