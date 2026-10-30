package com.iekcorbar.relics;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

/** Xmas Town food and drink (vanilla food components; looks from Relics-pack). */
final class Treats implements Listener {
    enum Treat {
        HOT_CHOCOLATE("Hot Chocolate", "Warms you right through - no more shivering.", 4, 5f, true, false),
        MULLED_CIDER("Mulled Cider", "Apples, berries and spice, served hot.", 3, 4f, true, true),
        GINGERBREAD_COOKIE("Gingerbread Cookie", "He looks at you. You eat him anyway.", 2, 1.6f, false, false),
        CHRISTMAS_STOLLEN("Christmas Stollen", "Dense, sweet and dusted with sugar.", 7, 8f, false, false),
        CANDY_CANE("Candy Cane", "Peppermint. A little spring in your step.", 1, 0.6f, false, false),
        SPARKLING_CIDER("Sparkling Cider", "Bubbles for midnight. Hold on to your hat.", 2, 2f, true, true),
        OLIEBOL("Oliebol", "A Dutch New Year's Eve doughnut, dusted with sugar.", 4, 4f, false, false),
        TOSHIKOSHI_SOBA("Toshikoshi Soba", "Long noodles for a long life, eaten on the last night of the year.", 8, 9f, false, false),
        SPARKLER("Sparkler", "Right-click to light it.", 0, 0f, false, false),
        CHOCOLATE_BUNNY("Chocolate Bunny", "Ears first, obviously.", 4, 3f, false, false),
        HOT_CROSS_BUN("Hot Cross Bun", "Spiced and sweet.", 5, 5f, false, false),
        GREEN_GINGER_ALE("Green Ginger Ale", "Bubbly, gingery and very green.", 2, 2f, true, true),
        SODA_BREAD("Soda Bread", "Crusty Irish bread.", 6, 7f, false, false),
        SHAMROCK_COOKIE("Shamrock Cookie", "A little extra luck.", 2, 1.2f, false, false),
        CHOCOLATE_HEART("Chocolate Heart", "Half bitter, half sweet - and a beat after eating, something extra.", 3, 1.2f, false, false),
        WATER_BOTTLE("Water Bottle", "Cool, clear and chased with nothing. Washes the day off.", 1, 0.4f, true, true);

        final String displayName, lore;
        final int nutrition;
        final float saturation;
        final boolean drink, bottle;

        Treat(String displayName, String lore, int nutrition, float saturation, boolean drink, boolean bottle) {
            this.displayName = displayName;
            this.lore = lore;
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.drink = drink;
            this.bottle = bottle;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final RelicsPlugin plugin;
    private final NamespacedKey key;

    Treats(RelicsPlugin plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "treat");
    }

    ItemStack item(Treat t, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(t.displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        String label = t == Treat.WATER_BOTTLE ? "Curiosities"
                : t.ordinal() >= Treat.CHOCOLATE_HEART.ordinal() ? "Valentine's"
                : t.ordinal() >= Treat.SODA_BREAD.ordinal() || t == Treat.GREEN_GINGER_ALE ? "St. Patrick's Day"
                : t.ordinal() >= Treat.CHOCOLATE_BUNNY.ordinal() ? "Easter"
                : t.ordinal() >= Treat.SPARKLING_CIDER.ordinal() ? "New Year" : "Xmas Town";
        m.lore(List.of(Component.text(t.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.text(label, NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id());
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("relics", t.id()));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, t.drink ? 16 : 64);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("relics", t.id()));
        if (t == Treat.SPARKLER) return it; // not food
        it.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(t.nutrition).saturation(t.saturation).canAlwaysEat(t.drink).build());
        it.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(t.drink ? 1.6f : 1.2f)
                .animation(t.drink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
                .sound(Key.key(t.drink ? "entity.generic.drink" : "entity.generic.eat")).hasConsumeParticles(!t.drink).build());
        if (t.bottle) it.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(new ItemStack(Material.GLASS_BOTTLE)));
        return it;
    }

    Treat typeOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        try {
            return Treat.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Treat t = typeOf(e.getItem());
        if (t == null) return;
        Player p = e.getPlayer();
        plugin.adv().grant(p, "treat");
        switch (t) {
            case HOT_CHOCOLATE -> {
                p.setFreezeTicks(0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
            }
            case MULLED_CIDER -> {
                p.setFreezeTicks(0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
            }
            case CANDY_CANE -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 0));
            case CHRISTMAS_STOLLEN -> p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20, 0));
            case SPARKLING_CIDER -> p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 600, 1));
            case TOSHIKOSHI_SOBA -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
            case CHOCOLATE_BUNNY -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0));
            case GREEN_GINGER_ALE -> p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 6000, 0));
            case SHAMROCK_COOKIE -> p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 1200, 0));
            case CHOCOLATE_HEART -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 0));
                p.getWorld().spawnParticle(org.bukkit.Particle.HEART, p.getEyeLocation().add(0, 0.6, 0), 3, 0.3, 0.15, 0.3, 0.02);
            }
            case WATER_BOTTLE -> {                                   // water washes things clean
                p.setFireTicks(0);
                p.removePotionEffect(PotionEffectType.POISON);
                p.removePotionEffect(PotionEffectType.NAUSEA);
                p.removePotionEffect(PotionEffectType.WEAKNESS);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 1200, 0));
            }
            default -> { }
        }
    }

    void registerRecipes(List<NamespacedKey> keys) {
        add(keys, Treat.HOT_CHOCOLATE, 3, Material.COCOA_BEANS, Material.MILK_BUCKET, Material.SUGAR);
        add(keys, Treat.MULLED_CIDER, 1, Material.APPLE, Material.SWEET_BERRIES, Material.SUGAR, Material.GLASS_BOTTLE);
        add(keys, Treat.GINGERBREAD_COOKIE, 6, Material.WHEAT, Material.WHEAT, Material.SUGAR, Material.HONEY_BOTTLE);
        add(keys, Treat.CHRISTMAS_STOLLEN, 1, Material.WHEAT, Material.WHEAT, Material.SUGAR, Material.SWEET_BERRIES, Material.EGG);
        add(keys, Treat.CANDY_CANE, 3, Material.SUGAR, Material.SUGAR, Material.RED_DYE);
        add(keys, Treat.SPARKLING_CIDER, 1, Material.APPLE, Material.SUGAR, Material.GLASS_BOTTLE, Material.AMETHYST_SHARD);
        add(keys, Treat.OLIEBOL, 4, Material.WHEAT, Material.WHEAT, Material.EGG, Material.SUGAR, Material.APPLE);
        add(keys, Treat.TOSHIKOSHI_SOBA, 1, Material.WHEAT, Material.WHEAT, Material.KELP, Material.BOWL);
        add(keys, Treat.SPARKLER, 4, Material.STICK, Material.GUNPOWDER, Material.GLOWSTONE_DUST);
        add(keys, Treat.CHOCOLATE_BUNNY, 2, Material.COCOA_BEANS, Material.COCOA_BEANS, Material.SUGAR, Material.MILK_BUCKET);
        add(keys, Treat.HOT_CROSS_BUN, 3, Material.WHEAT, Material.WHEAT, Material.SUGAR, Material.EGG);
        add(keys, Treat.GREEN_GINGER_ALE, 1, Material.SUGAR, Material.LIME_DYE, Material.GLASS_BOTTLE);
        add(keys, Treat.SODA_BREAD, 2, Material.WHEAT, Material.WHEAT, Material.WHEAT, Material.MILK_BUCKET);
        add(keys, Treat.SHAMROCK_COOKIE, 8, Material.WHEAT, Material.WHEAT, Material.SUGAR, Material.LIME_DYE);
        add(keys, Treat.CHOCOLATE_HEART, 4, Material.COCOA_BEANS, Material.COCOA_BEANS, Material.SUGAR, Material.SUGAR, Material.MILK_BUCKET);
        add(keys, Treat.WATER_BOTTLE, 2, Material.GLASS_BOTTLE, Material.ICE, Material.ICE);
    }

    private void add(List<NamespacedKey> keys, Treat t, int amount, Material... ing) {
        NamespacedKey k = new NamespacedKey(plugin, t.id());
        Bukkit.removeRecipe(k, false);
        ShapelessRecipe r = new ShapelessRecipe(k, item(t, amount));
        for (Material m : ing) r.addIngredient(m);
        Bukkit.addRecipe(r);
        keys.add(k);
    }

    /** Lit sparkler: 10 seconds of sparks at the player's hand. */
    @EventHandler
    public void onSparkler(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (typeOf(e.getItem()) != Treat.SPARKLER) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) e.getItem().setAmount(e.getItem().getAmount() - 1);
        p.getWorld().playSound(p.getLocation(), "relics:sparkler.fizz", 0.7f, 1f);
        int[] left = {20};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || left[0]-- <= 0) {
                task.cancel();
                return;
            }
            org.bukkit.Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6)).add(0, -0.4, 0);
            p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, hand, 3, 0.05, 0.05, 0.05, 0.05);
            p.getWorld().spawnParticle(org.bukkit.Particle.SMALL_FLAME, hand, 1, 0.02, 0.02, 0.02, 0);
            if (left[0] % 4 == 0) p.getWorld().playSound(hand, "relics:sparkler.fizz", 0.4f, 1.1f);
        }, 0L, 10L);
    }

    RecipeChoice exact(Treat t) {
        return RecipeChoice.exactChoice(item(t, 1));
    }
}
