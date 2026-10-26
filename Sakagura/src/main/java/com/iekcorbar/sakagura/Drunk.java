package com.iekcorbar.sakagura;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Drunkenness, like the old Brewery: every drink adds to a drunk level (0-100, kept in the
 * player's data). Tipsy players stumble, the screen sways, chat gets slurred, very drunk
 * players throw up, and at 100 they pass out and wake up at their spawn point with a
 * headache. The level goes down by itself; a hard night ends in a hangover.
 */
final class Drunk implements Listener {
    private final Sakagura plugin;
    private final NamespacedKey levelKey, peakKey;
    private double decayPerMinute, perAlcohol;
    /** Players whose display name is currently the joke one -> their real display name. */
    private final java.util.Map<java.util.UUID, net.kyori.adventure.text.Component> realNames = new java.util.HashMap<>();

    Drunk(Sakagura plugin) {
        this.plugin = plugin;
        levelKey = new NamespacedKey(plugin, "drunk");
        peakKey = new NamespacedKey(plugin, "drunk_peak");
        load();
    }

    void load() {
        decayPerMinute = plugin.getConfig().getDouble("drunk.sober-per-minute", 2.0);
        perAlcohol = plugin.getConfig().getDouble("drunk.per-alcohol-percent", 0.6);
    }

    double level(Player p) {
        return p.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.DOUBLE, 0.0);
    }

    void set(Player p, double v) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        v = Math.max(0, Math.min(100, v));
        pdc.set(levelKey, PersistentDataType.DOUBLE, v);
        if (v > pdc.getOrDefault(peakKey, PersistentDataType.DOUBLE, 0.0)) pdc.set(peakKey, PersistentDataType.DOUBLE, v);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        Brew b = plugin.goods().brewOf(e.getItem());
        if (b == null) return;
        Player p = e.getPlayer();
        plugin.adv().grant(p, "tasted");
        Recipes.Match m = plugin.recipes().match(b);
        if (m == null || !m.finished() || m.quality() == 0) {
            set(p, level(p) + 4);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
            p.sendActionBar(Component.text("That tasted awful. Don't tell the cat.", NamedTextColor.GRAY));
            plugin.adv().grant(p, "dont_tell");
            return;
        }
        int alc = m.recipe().alcohol;
        if (alc > 0) {
            double add = alc * perAlcohol * (m.quality() < 4 ? 1.3 : 1.0); // bad stuff hits harder
            set(p, level(p) + add);
            if (ThreadLocalRandom.current().nextInt(3) == 0) p.getWorld().playSound(p.getLocation(), "sakagura:drunk.hiccup", 0.7f, 1f);
        }
    }

    /** Every second. */
    void tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            double lv = level(p);
            if (lv >= 80 && !realNames.containsKey(p.getUniqueId())) {
                realNames.put(p.getUniqueId(), p.displayName());
                p.displayName(Component.text("Definitely Fine"));
                plugin.adv().grant(p, "definitely_fine");
            } else if (lv < 60 && realNames.containsKey(p.getUniqueId())) {
                p.displayName(realNames.remove(p.getUniqueId()));
            }
            if (lv <= 0) continue;
            double next = lv - decayPerMinute / 60.0;
            if (next <= 5 && lv > 5) {
                double peak = p.getPersistentDataContainer().getOrDefault(peakKey, PersistentDataType.DOUBLE, 0.0);
                if (peak >= 60) hangover(p);
                p.getPersistentDataContainer().set(peakKey, PersistentDataType.DOUBLE, 0.0);
            }
            set(p, next);
            if (p.isDead() || p.isFlying()) continue;
            if (lv >= 20 && p.isOnGround() && p.getVelocity().setY(0).lengthSquared() > 0.0001 && r.nextDouble() < (lv - 15) / 120) {
                Vector side = new Vector(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5).normalize().multiply(0.12 + lv / 600);
                p.setVelocity(p.getVelocity().add(side));
            }
            if (lv >= 40 && r.nextInt(25) == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, true, false));
            if (lv >= 70 && r.nextInt(30) == 0) puke(p);
            if (lv >= 85 && r.nextInt(20) == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, false));
            if (lv >= 100 || (lv >= 92 && r.nextInt(40) == 0)) passOut(p);
        }
    }

    /** Two players sneak next to each other, both holding a drink: cheers. */
    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !holdsDrink(p)) return;
        for (Player o : p.getWorld().getNearbyPlayers(p.getLocation(), 3)) {
            if (o == p || !o.isSneaking() || !holdsDrink(o)) continue;
            Location mid = p.getLocation().add(o.getLocation()).multiply(0.5).add(0, 1.4, 0);
            p.getWorld().playSound(mid, "minecraft:block.amethyst_block.chime", 1.2f, 1.4f);
            p.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, mid, 8, 0.2, 0.2, 0.2, 0);
            for (Player who : new Player[]{p, o}) {
                who.sendActionBar(Component.text("Kanpai!", NamedTextColor.GOLD));
                plugin.adv().grant(who, "kanpai");
            }
            return;
        }
    }

    private boolean holdsDrink(Player p) {
        return plugin.goods().brewOf(p.getInventory().getItemInMainHand()) != null || plugin.goods().brewOf(p.getInventory().getItemInOffHand()) != null;
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        var real = realNames.remove(e.getPlayer().getUniqueId());
        if (real != null) e.getPlayer().displayName(real);
    }

    private void puke(Player p) {
        plugin.adv().grant(p, "tipsy");
        Location mouth = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.4)).add(0, -0.2, 0);
        p.getWorld().playSound(mouth, "sakagura:drunk.puke", 0.8f, 1f);
        p.setFoodLevel(Math.max(0, p.getFoodLevel() - 2));
        for (int i = 0; i < 6; i++) {
            Item it = p.getWorld().dropItem(mouth, new ItemStack(Material.BROWN_DYE));
            it.setPickupDelay(Integer.MAX_VALUE);
            it.setVelocity(p.getLocation().getDirection().multiply(0.25).add(new Vector(0, 0.05, 0)));
            it.setTicksLived(5980 - 60); // gone after ~3 seconds
            it.setCanMobPickup(false);
        }
        p.getWorld().spawnParticle(Particle.ITEM, mouth, 10, 0.1, 0.1, 0.1, 0.05, new ItemStack(Material.BROWN_DYE));
    }

    private void passOut(Player p) {
        plugin.adv().grant(p, "tipsy");
        plugin.adv().grant(p, "passed_out");
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 140, 0, true, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 6, true, false));
        p.sendActionBar(Component.text("Everything goes dark...", NamedTextColor.DARK_GRAY));
        set(p, 45);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Location wake = p.getRespawnLocation();
            if (wake == null) wake = p.getWorld().getSpawnLocation();
            p.teleport(wake);
            p.sendMessage(Component.text("You wake up somewhere else, with a pounding headache. What happened last night?", NamedTextColor.GRAY));
            hangover(p);
        }, 80L);
    }

    private void hangover(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 2400, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 2400, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 1200, 0));
        p.sendActionBar(Component.text("Ugh. A hangover.", NamedTextColor.GRAY));
    }

    /** Slurred chat for drunk players. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        double lv = level(e.getPlayer());
        if (lv < 45) return;
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        e.message(Component.text(slur(text, lv)));
    }

    static String slur(String s, double lv) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            String x = w;
            if (r.nextDouble() < lv / 150) x = x.replace("s", "sh").replace("S", "Sh");
            if (r.nextDouble() < lv / 300 && x.length() > 3) x = x.substring(0, x.length() / 2) + x.charAt(x.length() / 2) + x.substring(x.length() / 2);
            sb.append(sb.isEmpty() ? "" : " ").append(x);
            if (r.nextDouble() < lv / 600) sb.append(" *hic*");
        }
        return sb.toString();
    }
}
