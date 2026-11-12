package com.iekcorbar.xmas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Kerstman: one Santa, walking around Xmas Town.
 *
 * Base mob is a VILLAGER on purpose: villagers pathfind nicely over a village (they step up, they
 * wait at fences), they never attack a player, and right-clicking them is an event we can cancel -
 * so the trading GUI never opens and a click becomes "ho ho ho" plus a gift instead. He is silent
 * (his voice comes from us), persistent (never despawns), age-locked and cannot breed, and no
 * zombie-panic: {@link Villager#setProfession} stays NONE so no job site claims him.
 *
 * Everything about him is one entity, remembered by UUID + last position in config.yml, so a
 * restart puts him back on the square instead of spawning a second one. The FMM model
 * ("xmas_santa", tools/make_santa.py) is attached when he appears and retried every 5 s until FMM
 * has loaded it - same trick Wildlife uses for its 203 animals.
 *
 * Gifts: one per player per day, only inside the present window (24-26 December, same window the
 * Relics gift boxes use), unless config santa.gifts-anytime is true (staff testing). What he hands
 * out is our own stuff so Xmas never depends on another plugin: a gift cube, candy and gingerbread,
 * and once a season a street lamp, because a lit village is the whole point.
 */
final class Santa implements Listener {

    static final String MODEL = "xmas_santa";

    private final XmasPlugin plugin;
    private final Fmm fmm;
    private final org.bukkit.NamespacedKey santaKey, giftKey;

    private UUID uuid;
    private LivingEntity entity;
    private boolean attached;
    private long nextWalk, nextChime;
    private int greetCool;

    Santa(XmasPlugin plugin, Fmm fmm) {
        this.plugin = plugin;
        this.fmm = fmm;
        santaKey = new org.bukkit.NamespacedKey(plugin, "santa");
        giftKey = new org.bukkit.NamespacedKey(plugin, "gift_day");
        // onmogelijk dat er ergens een tweede kerstman loopt: de server spawn er maar één
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ---------------------------------------------------------------- positiesheugen

    void remember() {
        if (entity == null || !entity.isValid()) return;
        var cfg = plugin.getConfig();
        var l = entity.getLocation();
        cfg.set("santa.uuid", entity.getUniqueId().toString());
        cfg.set("santa.world", l.getWorld().getName());
        cfg.set("santa.pos", List.of(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        plugin.saveConfig();
    }

    /** Door de plugin geplaatst? Dan is de UUID het bewijs (config is ons heugen). */
    boolean isSanta(Entity e) {
        return uuid != null && e.getUniqueId().equals(uuid);
    }

    // ---------------------------------------------------------------- aan-en-uitzetten

    /** Haalt de bewaarde kerstman terug, of zet een nieuwe neer op {@code at}. */
    void summon(Location at) {
        Villager old = live();
        if (old != null) {
            old.teleport(at);
            remember();
            return;
        }
        // World.spawn vraagt hier om het CLASS-type van de entity (een EntityType volstaat niet),
        // en de consumer is de enige plek waar we alles in één atomic setup zetten
        Villager v = at.getWorld().spawn(at, Villager.class, mob -> {
            mob.customName(Component.text("Santa", NamedTextColor.GOLD));
            mob.setCustomNameVisible(true);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setSilent(true);
            mob.setAI(true);
            mob.setProfession(Villager.Profession.NONE);   // geen werkplaats, geen handelaar
            mob.setAgeLock(true);
            mob.setAdult();
            mob.setBreed(false);
            mob.setCanPickupItems(false);
            mob.getPersistentDataContainer().set(santaKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (v == null) return;
        uuid = v.getUniqueId();
        entity = v;
        attach(v);
        remember();
        plugin.getLogger().info("Santa placed at " + at.getWorld().getName() + " "
                + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
    }

    private void attach(LivingEntity mob) {
        attached = fmm.attach(mob, MODEL);
    }

    Villager live() {
        if (entity instanceof Villager v && v.isValid()) return v;
        if (uuid == null) return null;
        if (plugin.getServer().getEntity(uuid) instanceof Villager v && v.isValid()) {
            entity = v;
            return v;
        }
        entity = null;
        return null;
    }

    void dismiss() {
        Villager e = live();
        if (e != null) e.remove();
        uuid = null;
        entity = null;
        plugin.getConfig().set("santa.uuid", null);
        plugin.saveConfig();
    }

    /** Wordt één keer per enable (en na een chunk-load) geprobeerd; lukt het niet, over 5 s opnieuw. */
    void restore() {
        var cfg = plugin.getConfig();
        String id = cfg.getString("santa.uuid");
        if (id == null || !cfg.getBoolean("santa.enabled", true)) return;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return;
        }
        LivingEntity e = live();
        if (e != null) {
            attach(e);
            return;
        }
        // de entity is weg (schatkisten-verwijderd, chunk nog niet geladen): herbouw op de plek
        String world = cfg.getString("santa.world");
        List<Integer> pos = cfg.getIntegerList("santa.pos");
        World w = world == null ? null : plugin.getServer().getWorld(world);
        if (w == null || pos.size() != 3) return;
        Location at = new Location(w, pos.get(0) + 0.5, pos.get(1), pos.get(2) + 0.5);
        if (at.getBlock().getType() == Material.BARRIER || !at.getBlock().isPassable()) {
            at.add(0, 1, 0);                     // de vorige plek is nu bebouwd
        }
        Villager mob = w.spawn(at, Villager.class, v -> {
            v.customName(Component.text("Santa", NamedTextColor.GOLD));
            v.setCustomNameVisible(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setSilent(true);
            v.setProfession(Villager.Profession.NONE);
            v.setAgeLock(true);
            v.setAdult();
            v.setBreed(false);
            v.setCanPickupItems(false);
            v.getPersistentDataContainer().set(santaKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (mob == null) return;
        uuid = mob.getUniqueId();
        entity = mob;
        attach(mob);
        remember();
    }

    // ---------------------------------------------------------------- hartklop

    void tick(long tick) {
        Villager e = live();
        if (e == null) return;

        // FMM laadt lazily: een model dat er nog niet was, proberen we gewoon opnieuw
        if (!attached && tick % 10 == 0) attach(e);

        // rondlopen: elke 6-11 s een nieuw doel binnen 24 blokken, alleen op begaanbare grond
        if (tick >= nextWalk) {
            nextWalk = tick + 120 + (long) (Math.random() * 100);
            wander(e);
        }
        // kerstklokje: af en toe een belletje als er iemand in de buurt staat
        if (tick >= nextChime) {
            nextChime = tick + 400 + (long) (Math.random() * 600);
            Player near = nearest(e, 8);
            if (near != null) {
                near.getWorld().playSound(e.getLocation(), "minecraft:block.bell.use", 0.7f, 1.35f);
                e.getWorld().spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, 2.1, 0), 8,
                        0.4, 0.2, 0.4, 0.02);
            }
        }
        if (greetCool > 0) greetCool--;
        if (tick % 600 == 0) remember();
    }

    private void wander(Villager e) {
        if (e.isInsideVehicle() || e.isSleeping()) return;
        World w = e.getWorld();
        Location here = e.getLocation();
        for (int poging = 0; poging < 6; poging++) {
            double a = Math.random() * Math.PI * 2;
            double r = 4 + Math.random() * 20;
            Location t = here.clone().add(new Vector(Math.cos(a) * r, 0, Math.sin(a) * r));
            t.setY(here.getY() + (int) (Math.random() * 3) - 1);
            Block floor = t.clone().add(0, -1, 0).getBlock();
            if (floor.isPassable() || !t.getBlock().isPassable() || !t.getBlock().getRelative(0, 1, 0).isPassable())
                continue;                                  // geen water, geen muur, geen plafond
            if (!e.getPathfinder().moveTo(t, 0.86)) continue;
            fmm.play(e, "walk", true);
            return;
        }
        fmm.play(e, "idle", true);
    }

    private Player nearest(LivingEntity e, double radius) {
        Player best = null;
        double bd = radius * radius;
        for (Player p : e.getWorld().getPlayers()) {
            if (!p.getWorld().equals(e.getWorld())) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- rechtsklik: groet en pakje

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent ev) {
        Entity e = ev.getRightClicked();
        if (!isSanta(e)) return;
        ev.setCancelled(true);           // geen handels-venster: hij geeft, hij verkoopt niet
        if (!(e instanceof Villager mob)) return;
        Player p = ev.getPlayer();
        mob.lookAt(p.getLocation());
        if (greetCool == 0) {
            greetCool = 12;
            fmm.play(mob, "greet", false);
            mob.getWorld().playSound(mob.getLocation(), "minecraft:entity.villager.yes", 1.0f, 0.75f);
        }
        p.sendMessage(Component.text("Santa: ", NamedTextColor.RED)
                .append(Component.text(HOHO[3 + (int) (Math.random() * (HOHO.length - 3))], NamedTextColor.GOLD)));
        geef(p);
    }

    private static final String[] HOHO = {
            "You have been on the nice list this year.",
            "Merry Christmas!", "Ho ho ho!", "Cookies were excellent this season.",
            "Take care of each other in the snow.", "The village looks wonderful this year.",
            "Ho ho ho - mind the ice."};

    /** Eén geschenk per speler per dag, alleen in het cadeau-venster (24-26 december). */
    private void geef(Player p) {
        if (!p.hasPlayedBefore()) return;
        LocalDate vandaag = LocalDate.now();
        if (!plugin.getConfig().getBoolean("santa.gifts-anytime", false) && !kadofvenster(vandaag)) {
            p.sendMessage(Component.text("He reaches into his sack, then shakes his head: not yet.",
                    NamedTextColor.GRAY));
            return;
        }
        String dag = vandaag.toString();
        String ouder = p.getPersistentDataContainer().get(giftKey, PersistentDataType.STRING);
        if (dag.equals(ouder)) {
            p.sendMessage(Component.text("You already have today's gift. He winks.", NamedTextColor.GRAY));
            return;
        }
        List<ItemStack> geschenk = new java.util.ArrayList<>();
        var defs = plugin.decor().decor.defs;
        geschenk.add(plugin.decor().decor.item(defs.get(Math.random() < 0.5 ? "present_red" : "present_green"), 2));
        geschenk.add(plugin.decor().decor.item(defs.get("candy_block"), 3));
        geschenk.add(plugin.decor().decor.item(defs.get("gingerbread_block"), 3));
        // één keer per seizoen een lantaarn: verlichte straten zijn het hele punt
        if (vandaag.getDayOfMonth() <= 27 && vandaag.getMonth() == Month.DECEMBER
                && Math.random() < 0.34) {
            geschenk.add(plugin.decor().decor.item(defs.get("village_lamp"), 1));
        }
        for (ItemStack left : p.getInventory().addItem(geschenk.toArray(new ItemStack[0])).values())
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.getPersistentDataContainer().set(giftKey, PersistentDataType.STRING, dag);
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.6, 0), 24, 0.5, 0.4, 0.5, 0.05);
        p.getWorld().playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", 0.8f, 1.15f);
        p.sendMessage(Component.text("Santa hands you a wrapped bundle.", NamedTextColor.GREEN));
    }

    /** Zelfde venster als de Relics-cadeaudos: 24 tot en met 26 december. */
    static boolean kadofvenster(LocalDate d) {
        return d.getMonth() == Month.DECEMBER && d.getDayOfMonth() >= 24 && d.getDayOfMonth() <= 26;
    }

    /** Nooit een tweede: elke spontaneous villager-spawn die ons UUID-tag draagt is een lek. */
    @EventHandler
    public void onSpawn(CreatureSpawnEvent ev) {
        if (uuid == null) return;
        Entity e = ev.getEntity();
        if (e.getUniqueId().equals(uuid) && ev.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            ev.setCancelled(true);
        }
    }

    String status() {
        Villager e = live();
        if (e == null) return "no Santa standing (config santa.uuid: " + (uuid == null ? "leeg" : uuid) + ")";
        Location l = e.getLocation();
        return String.format(Locale.ROOT, "walking in %s at %d,%d,%d (model %s)", l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ(), attached ? "aangemaakt" : "NOT loading");
    }
}
