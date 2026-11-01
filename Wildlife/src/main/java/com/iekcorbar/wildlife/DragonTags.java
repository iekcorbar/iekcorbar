package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Name tags for tame dragons. The base mob is a small invisible parrot, so its own name tag would float inside the
 * dragon; a text display rides it, lifted above the model, and shows the name (or "Owner's Dragon") plus the owner.
 * Looking at a dragon shows its hunger and nest in the action bar. Displays are not saved: a sweep recreates them.
 */
final class DragonTags {
    private final WildlifePlugin plugin;
    private final Map<UUID, UUID> tags = new HashMap<>();   // dragon -> text display

    DragonTags(WildlifePlugin plugin) {
        this.plugin = plugin;
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private String nameOf(LivingEntity e) {
        UUID owner = DragonAI.tamer(e);
        String ownerName = owner == null ? null : Bukkit.getOfflinePlayer(owner).getName();
        String name = e.customName() != null ? plain(e.customName()) : null;
        if (name == null || name.isBlank() || name.equalsIgnoreCase("dragon") || name.equalsIgnoreCase("Steam Dragon"))
            name = (ownerName == null ? "A" : ownerName + "'s") + " Dragon";
        return name;
    }

    private Component text(LivingEntity e, Animals.Animal a) {
        UUID owner = DragonAI.tamer(e);
        String ownerName = owner == null ? null : Bukkit.getOfflinePlayer(owner).getName();
        return Component.text(nameOf(e), NamedTextColor.GOLD).append(Component.newline())
                .append(Component.text(ownerName == null ? "" : "owner: " + ownerName, NamedTextColor.GRAY));
    }

    /** Every second: make sure each tame dragon has one tag, with the right text, lifted by the dragon's size. */
    void tick() {
        for (Animals.Animal a : plugin.animals().all()) {
            if (a.species != Species.DRAGON || !a.attached) continue;
            LivingEntity e = a.entity;
            Entity cur = tags.containsKey(e.getUniqueId()) ? Bukkit.getEntity(tags.get(e.getUniqueId())) : null;
            if (DragonAI.tamer(e) == null || !e.isValid() || DragonAI.ridden(e)) {   // wild, or the rider's own view stays clear
                if (cur != null) cur.remove();
                tags.remove(e.getUniqueId());
                continue;
            }
            var sc = e.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            float k = sc == null ? 1f : (float) (sc.getValue() / 1.5);
            TextDisplay td = cur instanceof TextDisplay t0 && t0.isValid() && e.getPassengers().contains(t0) ? t0 : null;
            if (td == null) {
                if (cur != null) cur.remove();
                td = e.getWorld().spawn(e.getLocation(), TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setSeeThrough(false);
                    d.setShadowed(true);
                    d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                    d.setViewRange(0.4f);
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                    d.setLineWidth(200);
                });
                e.addPassenger(td);
                tags.put(e.getUniqueId(), td.getUniqueId());
            }
            td.text(text(e, a));
            td.setTransformation(new Transformation(new Vector3f(0, 1.35f * k, 0), new Quaternionf(), new Vector3f(1f, 1f, 1f), new Quaternionf()));
        }
        for (var it = tags.entrySet().iterator(); it.hasNext(); ) {       // dragons that are gone: take their tags with them
            var en = it.next();
            if (Bukkit.getEntity(en.getKey()) == null) {
                Entity t = Bukkit.getEntity(en.getValue());
                if (t != null) t.remove();
                it.remove();
            }
        }
    }

    /** Every half second: whoever looks straight at a tame dragon sees its state in the action bar. */
    void lookTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 24, 0.6,
                    en -> en instanceof LivingEntity le && plugin.animals().get(le) != null && plugin.animals().get(le).species == Species.DRAGON);
            if (r == null || !(r.getHitEntity() instanceof LivingEntity le)) continue;
            Animals.Animal a = plugin.animals().get(le);
            UUID owner = DragonAI.tamer(le);
            if (a == null || owner == null) continue;
            String hunger = a.hunger >= 70 ? "well fed" : a.hunger >= 35 ? "peckish" : "hungry";
            boolean nest = plugin.nests().claimedBy(le) != null;
            p.sendActionBar(Component.text(nameOf(le), NamedTextColor.GOLD)
                    .append(Component.text(" - " + hunger + (nest ? " - has a nest" : ""), NamedTextColor.GRAY)));
        }
    }

    void removeAll() {
        for (UUID id : tags.values()) {
            Entity t = Bukkit.getEntity(id);
            if (t != null) t.remove();
        }
        tags.clear();
    }
}
