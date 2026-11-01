package com.iekcorbar.wildlife;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Field Journal, Crab Claw (+1 reach in the off hand) and Lizard Tail; looks come from FMM display models. */
final class WildItems implements Listener {
    private final WildlifePlugin plugin;
    final NamespacedKey itemKey, journalRecipe, whistleRecipe;

    WildItems(WildlifePlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "item");
        journalRecipe = new NamespacedKey(plugin, "field_journal");
        whistleRecipe = new NamespacedKey(plugin, "dragon_whistle");
    }

    private ItemStack make(Material base, String id, String name, String model, String... lore) {
        ItemStack it = new ItemStack(base);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        l.add(Component.text("Wildlife", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false));
        m.lore(l);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        if (id.equals("crab_claw")) {
            m.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier(new NamespacedKey(plugin, "claw_block"),
                    1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.OFFHAND));
            m.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(new NamespacedKey(plugin, "claw_entity"),
                    1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.OFFHAND));
        }
        it.setItemMeta(m);
        plugin.fmm().itemModel(it, model);
        return it;
    }

    String idOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    /** A book named "Field Journal" with the lore line "Wildlife" but without our data: what the WanderingTrades trade file hands out. */
    boolean fromTrader(ItemStack it) {
        if (it == null || it.getType() != Material.BOOK || !it.hasItemMeta() || idOf(it) != null) return false;
        ItemMeta m = it.getItemMeta();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        if (!m.hasDisplayName() || !plain.serialize(m.displayName()).equals("Field Journal")) return false;
        List<Component> lore = m.lore();
        return lore != null && !lore.isEmpty() && plain.serialize(lore.get(lore.size() - 1)).equals("Wildlife");
    }

    /** Spawn egg of a species: right-click a block to spawn one, sneak + right-click for a baby. Model zoo:egg_<id> (Zoo pack). */
    ItemStack egg(Species s) {
        return egg(s, null);
    }

    /** Every egg a species has: the plain one is separate; these are the rare look and each extra look (macaw/songbird/dragon colours, the Ender dragon). */
    List<String> eggVariants(Species s) {
        List<String> out = new java.util.ArrayList<>();
        if (!s.animal()) return out;
        out.add("rare");
        List<String> looks = s.models();
        for (int i = 1; i < looks.size(); i++) out.add(looks.get(i));
        if (s == Species.DRAGON) out.add("wl_dragon_ender");
        return out;
    }

    /** Name of an egg variant as typed in commands: "rare", or the look without the wl_<species>_ prefix ("ender", "kura", "blue"). */
    static String variantShort(Species s, String variant) {
        return variant.startsWith("wl_" + s.id() + "_") ? variant.substring(("wl_" + s.id() + "_").length()) : variant;
    }

    /** variant: null = the plain egg, "rare" = the rare look, otherwise a model name (wl_dragon_kura ...). */
    ItemStack egg(Species s, String variant) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        String title = variant == null ? s.displayName
                : variant.equals("rare") ? s.rareName()
                : variant.equals("wl_dragon_ender") ? "Ender Dragon"
                : s.displayName + " (" + variantShort(s, variant) + ")";
        m.displayName(Component.text(title + " Spawn Egg", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Right-click a block to spawn one.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak + right-click: a baby.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Breeds with " + s.foodText() + ".", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Wildlife", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "egg:" + s.id() + (variant == null ? "" : ":" + variant));
        if (variant != null) m.setEnchantmentGlintOverride(true);   // the special ones shimmer
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("zoo", "egg_" + s.id()));
        return it;
    }

    private void useEgg(PlayerInteractEvent e, Species s, String variant) {
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        var at = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        // spawn() clears the baby flag for non-animals on its own, so a sneak-click gives the same creature
        boolean rare = "rare".equals(variant);
        if (plugin.animals().spawn(s, at, rare, p.isSneaking(), variant == null || rare ? null : variant) == null) {
            p.sendActionBar(Component.text("The model isn't loaded in FreeMinecraftModels (see console).", NamedTextColor.RED));
            return;
        }
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
    }

    ItemStack journal() {
        return make(Material.BOOK, "field_journal", "Field Journal", "wl_field_journal",
                "Right-click to read.", "Watch animals through a spyglass to fill it.");
    }

    ItemStack crabClaw() {
        return make(Material.PAPER, "crab_claw", "Crab Claw", "wl_crab_claw", "Hold it in your off hand", "to reach one block further.");
    }

    ItemStack lizardTail() {
        return make(Material.PAPER, "lizard_tail", "Lizard Tail", "wl_lizard_tail", "Still wriggling a little.", "Don't worry - it grows back.");
    }

    /** Calls every tamed dragon of the owner (blown from anywhere in the same world). Looks like a goat horn. */
    ItemStack whistle() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Dragon Whistle", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Right-click: your dragons fly to you,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("wherever they were in this world.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Wildlife", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "dragon_whistle");
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("minecraft", "goat_horn"));
        return it;
    }

    void registerRecipes() {
        Bukkit.removeRecipe(whistleRecipe, false);
        ShapelessRecipe w = new ShapelessRecipe(whistleRecipe, whistle());
        w.addIngredient(Material.COPPER_INGOT);
        w.addIngredient(Material.IRON_NUGGET);
        w.addIngredient(Material.COAL);
        Bukkit.addRecipe(w);
        Bukkit.removeRecipe(journalRecipe, false);
        ShapelessRecipe r = new ShapelessRecipe(journalRecipe, journal());
        r.addIngredient(Material.BOOK);
        r.addIngredient(Material.FEATHER);
        r.addIngredient(Material.WHEAT_SEEDS);
        Bukkit.addRecipe(r);
    }

    /** Recipes are registered before FMM has its models: give the result its look when crafted. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() instanceof org.bukkit.Keyed k && k.getKey().equals(journalRecipe)) e.getInventory().setResult(journal());
        else for (ItemStack i : e.getInventory().getMatrix()) if (idOf(i) != null) e.getInventory().setResult(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(journalRecipe);
        e.getPlayer().discoverRecipe(whistleRecipe);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack held = e.getItem();
        String id = idOf(held);
        if (id != null && id.startsWith("egg:")) {
            String[] part = id.substring(4).split(":", 2);
            Species egg = Species.byId(part[0]);
            if (egg != null) useEgg(e, egg, part.length > 1 ? part[1] : null);
            return;
        }
        if ("dragon_whistle".equals(id)) {
            e.setCancelled(true);
            plugin.dragon().whistle(e.getPlayer());
            return;
        }
        if (!"field_journal".equals(idOf(held))) {
            if (!fromTrader(held)) return;
            // a journal made by the WanderingTrades config: swap it for the real item (data + model) on first use
            ItemStack real = journal();
            real.setAmount(held.getAmount());
            e.getPlayer().getInventory().setItemInMainHand(real);
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        plugin.journal().open(p);
    }
}
