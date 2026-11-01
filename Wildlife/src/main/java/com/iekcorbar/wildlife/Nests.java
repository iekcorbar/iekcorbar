package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Dragon Nests (a Relics prop) and who has claimed them. A tame dragon that lands on a free nest claims it: one nest
 * per dragon, one dragon per nest, so dragons never fight over one. The claim lives here (plugins/Wildlife/nests.yml),
 * Relics only says where the nests are (a BiFunction service: centre, radius -> nests nearest first).
 * A claim ends when the dragon dies or is released, when the owner or staff frees the nest (sneak + right-click),
 * when the nest is gone, or when the dragon has not come near it for dragon.nest-claim-days days.
 */
final class Nests implements Listener {
    private record Claim(UUID dragon, UUID owner, long seen) { }

    private final WildlifePlugin plugin;
    private final File file;
    private final Map<String, Claim> claims = new HashMap<>();   // "world;x;y;z" -> claim
    private boolean dirty;

    Nests(WildlifePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nests.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            var sec = y.getConfigurationSection("claims");
            if (sec != null) for (String k : sec.getKeys(false)) {
                try {
                    claims.put(k.replace('|', ';'), new Claim(UUID.fromString(sec.getString(k + ".dragon")), UUID.fromString(sec.getString(k + ".owner")), sec.getLong(k + ".seen")));
                } catch (RuntimeException ignored) { }
            }
        }
    }

    // ------------------------------------------------------------ finding nests (through Relics)

    @SuppressWarnings("unchecked")
    private List<Location> nestsNear(Location from, double r) {
        for (RegisteredServiceProvider<BiFunction> p : Bukkit.getServicesManager().getRegistrations(BiFunction.class)) {
            if (!"Relics".equals(p.getPlugin().getName())) continue;
            try {
                Object o = ((BiFunction<Location, Double, Object>) p.getProvider()).apply(from, r);
                if (o instanceof List<?> l) {
                    List<Location> out = new ArrayList<>();
                    for (Object x : l) if (x instanceof Location loc) out.add(loc);
                    return out;
                }
            } catch (RuntimeException ignored) { }
        }
        return List.of();
    }

    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    double range() { return plugin.getConfig().getDouble("dragon.nest-range", 64); }

    private long expiryMs() { return 86_400_000L * Math.max(1, plugin.getConfig().getInt("dragon.nest-claim-days", 14)); }

    // ------------------------------------------------------------ queries for the dragons

    /** The nest this dragon has claimed (centre), or null. */
    Location claimedBy(LivingEntity dragon) {
        for (Map.Entry<String, Claim> en : claims.entrySet()) {
            if (!en.getValue().dragon().equals(dragon.getUniqueId())) continue;
            String[] f = en.getKey().split(";");
            var w = Bukkit.getWorld(f[0]);
            if (w == null) return null;
            return new Location(w, Integer.parseInt(f[1]) + 0.5, Integer.parseInt(f[2]), Integer.parseInt(f[3]) + 0.5);
        }
        return null;
    }

    /** The closest nest within r that nobody has claimed (a wild dragon settles near these; a tame one may claim one). */
    Location freeNear(Location from, double r) {
        for (Location n : nestsNear(from, r)) if (!claims.containsKey(key(n))) return n;
        return null;
    }

    /** True when a tame dragon of this owner has claimed a nest within r of the point; that nest's centre is returned through the array. */
    Location ownNestNear(UUID owner, Location at, double r) {
        for (Location n : nestsNear(at, r)) {
            Claim c = claims.get(key(n));
            if (c != null && c.owner().equals(owner)) return n;
        }
        return null;
    }

    /** A tame dragon lands on a free nest: it is theirs now. Returns true when the claim was made. */
    boolean claim(LivingEntity dragon, UUID owner, Location nest) {
        if (owner == null || claimedBy(dragon) != null || claims.containsKey(key(nest))) return false;
        claims.put(key(nest), new Claim(dragon.getUniqueId(), owner, System.currentTimeMillis()));
        dirty = true;
        nest.getWorld().spawnParticle(Particle.CLOUD, nest.clone().add(0, 0.8, 0), 12, 0.4, 0.2, 0.4, 0.02);
        nest.getWorld().playSound(nest, org.bukkit.Sound.BLOCK_CAMPFIRE_CRACKLE, 1f, 0.8f);
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            p.sendActionBar(Component.text("Your dragon has claimed this nest.", NamedTextColor.GOLD));
            plugin.adv().grant(p, "dragon_nesting");
        }
        return true;
    }

    void release(LivingEntity dragon) {
        if (claims.values().removeIf(c -> c.dragon().equals(dragon.getUniqueId()))) dirty = true;
    }

    /** Called now and then: a dragon near its nest keeps the claim fresh. */
    void touch(LivingEntity dragon) {
        Location n = claimedBy(dragon);
        if (n == null || n.getWorld() != dragon.getWorld() || n.distanceSquared(dragon.getLocation()) > range() * range()) return;
        String k = key(n);
        Claim c = claims.get(k);
        if (c != null && System.currentTimeMillis() - c.seen() > 60_000) {
            claims.put(k, new Claim(c.dragon(), c.owner(), System.currentTimeMillis()));
            dirty = true;
        }
    }

    // ------------------------------------------------------------ upkeep

    void tick() {
        long now = System.currentTimeMillis();
        long ttl = expiryMs();
        for (var it = claims.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (now - en.getValue().seen() > ttl) {
                it.remove();
                dirty = true;
                continue;
            }
            String[] f = en.getKey().split(";");
            var w = Bukkit.getWorld(f[0]);
            if (w == null) continue;
            Location c = new Location(w, Integer.parseInt(f[1]) + 0.5, Integer.parseInt(f[2]), Integer.parseInt(f[3]) + 0.5);
            if (!w.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) continue;
            boolean exists = false;
            for (Location n : nestsNear(c, 1.5)) if (key(n).equals(en.getKey())) { exists = true; break; }
            if (!exists) { it.remove(); dirty = true; }      // the nest was broken
        }
        if (dirty) save();
    }

    void save() {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var en : claims.entrySet()) {
            String k = en.getKey().replace(';', '|');
            y.set("claims." + k + ".dragon", en.getValue().dragon().toString());
            y.set("claims." + k + ".owner", en.getValue().owner().toString());
            y.set("claims." + k + ".seen", en.getValue().seen());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save nests.yml: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof LivingEntity le && claimedBy(le) != null) release(le);
    }

    // ------------------------------------------------------------ clicking a nest: who lives here / let it go

    @EventHandler
    public void onClick(PlayerInteractEntityEvent ev) {
        if (!(ev.getRightClicked() instanceof Interaction hit) || ev.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = ev.getPlayer();
        Location at = hit.getLocation();
        for (Location n : nestsNear(at, 2.5)) {
            Claim c = claims.get(key(n));
            if (c == null) {
                Bukkit.getScheduler().runTask(plugin, () -> p.sendActionBar(Component.text("A free dragon nest. A tame dragon that lands here will claim it.", NamedTextColor.GRAY)));
                return;
            }
            boolean mine = c.owner().equals(p.getUniqueId());
            Entity d = Bukkit.getEntity(c.dragon());
            String name = d != null && d.customName() != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(d.customName()) : "a dragon";
            if (p.isSneaking() && p.getInventory().getItemInMainHand().isEmpty() && (mine || p.hasPermission("wildlife.admin"))) {
                claims.remove(key(n));
                dirty = true;
                save();
                Bukkit.getScheduler().runTask(plugin, () -> p.sendActionBar(Component.text("You let the nest go. Anyone's dragon may claim it now.", NamedTextColor.GOLD)));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> p.sendActionBar(Component.text("Claimed by " + name + (mine ? " (yours)" : "") + (mine ? " - sneak + right-click to let it go." : "."), NamedTextColor.GOLD)));
            return;
        }
    }
}
