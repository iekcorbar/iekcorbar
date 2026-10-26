package com.iekcorbar.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The New Year Countdown Board: a big scoreboard that really counts down to the next New Year. Every board has a
 * time zone (saved in the relic's count: 0 = "world", otherwise the number of the zone in config new-year.zones):
 * "world" always shows the zone whose New Year comes next, so a board in Xmas Town follows the midnight round the
 * planet; a fixed board counts to midnight in its own zone. At midnight of a zone the board shows "Happy New Year"
 * for ten minutes, players near it get a title, and for the home zone (config timezone) or a fixed board the
 * firework launchers around it fire. Right-click shows the time table of all zones; the placer and staff change
 * the zone with sneak + right-click.
 */
final class NewYearBoards {
    record Zone(String label, ZoneId id) {}

    private static final List<String> DEFAULT_ZONES = List.of(
            "Kiritimati|Pacific/Kiritimati", "Auckland|Pacific/Auckland", "Sydney|Australia/Sydney", "Tokyo|Asia/Tokyo",
            "Dubai|Asia/Dubai", "Amsterdam|Europe/Amsterdam", "London|Europe/London", "New York|America/New_York",
            "Los Angeles|America/Los_Angeles", "Honolulu|Pacific/Honolulu");

    private final RelicsPlugin plugin;
    private final Relics relics;
    private final org.bukkit.NamespacedKey tagKey;
    private final List<Zone> zones = new ArrayList<>();
    private final Map<String, Integer> zoneYear = new HashMap<>();

    NewYearBoards(RelicsPlugin plugin, Relics relics, org.bukkit.NamespacedKey tagKey) {
        this.plugin = plugin;
        this.relics = relics;
        this.tagKey = tagKey;
        load();
    }

    void load() {
        zones.clear();
        List<String> lines = plugin.getConfig().getStringList("new-year.zones");
        for (String line : lines.isEmpty() ? DEFAULT_ZONES : lines) {
            String[] p = line.split("\\|", 2);
            try {
                zones.add(new Zone(p.length > 1 ? p[0].trim() : p[0].trim(), ZoneId.of((p.length > 1 ? p[1] : p[0]).trim())));
            } catch (Exception ex) {
                plugin.getLogger().warning("new-year.zones: unknown time zone in '" + line + "' - skipped.");
            }
        }
        if (zones.isEmpty()) zones.add(new Zone("Home", RelicsPlugin.zone));
        zoneYear.clear();
    }

    // ------------------------------------------------------------ drawing

    private static float faceYaw(org.bukkit.block.BlockFace f) {
        return switch (f) {
            case EAST -> -90;
            case SOUTH -> 0;
            case WEST -> 90;
            default -> 180;
        };
    }

    /** Three text lines on the panel: title, big countdown, zone line. */
    void spawn(Relics.Relic r) {
        remove(r);
        r.texts = new ArrayList<>();
        Vector dir = r.facing.getDirection();
        double off = r.mount.equals("wall") ? -0.215 : 0.035; // the panel sits against the wall, or on the legs in front of the centre
        float[] y = {1.36f, 0.86f, 0.60f};
        if (!r.mount.equals("wall")) for (int i = 0; i < y.length; i++) y[i] += 2.6f; // the floor board stands 2.6 higher, so players can walk under it
        float[] scale = {0.95f, 1.7f, 0.8f};
        for (int i = 0; i < 3; i++) {
            Location at = new Location(r.world, r.x + 0.5 + dir.getX() * off, r.y + y[i], r.z + 0.5 + dir.getZ() * off, faceYaw(r.facing), 0f);
            final float s = scale[i];
            TextDisplay t = r.world.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                d.setSeeThrough(false);
                d.setLineWidth(1000);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(0.7f);
                d.text(Component.empty());
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            });
            r.texts.add(t);
        }
        r.last = new String[3];
    }

    void remove(Relics.Relic r) {
        if (r.texts != null) for (TextDisplay t : r.texts) t.remove();
        r.texts = null;
    }

    boolean lost(Relics.Relic r) {
        if (r.texts == null || r.texts.size() < 3) return true;
        for (TextDisplay t : r.texts) if (!t.isValid()) return true;
        return false;
    }

    // ------------------------------------------------------------ the clock

    private Zone zoneOf(Relics.Relic r) {
        return r.count > 0 && r.count <= zones.size() ? zones.get(r.count - 1) : null;
    }

    /** Staff: the zone list, for /relics board and tab completion. */
    List<Zone> zoneList() {
        return zones;
    }

    /** Staff: what a board counts down to, as one line of text. */
    String describeZone(Relics.Relic r) {
        Zone z = zoneOf(r);
        return z == null ? "world (follows the next midnight)" : z.label() + " (" + z.id() + ")";
    }

    /** Command word -> the value of a board's count field (0 = world, i+1 = zone); -1 when nothing matches. Label, ZoneId or number. */
    int zoneArg(String arg) {
        if (arg.equalsIgnoreCase("world") || arg.equalsIgnoreCase("next")) return 0;
        for (int i = 0; i < zones.size(); i++) {
            Zone z = zones.get(i);
            if (z.label().equalsIgnoreCase(arg) || z.id().getId().equalsIgnoreCase(arg) || String.valueOf(i + 1).equals(arg)) return i + 1;
        }
        return -1;
    }

    private static boolean celebrating(ZonedDateTime local) {
        return local.getMonthValue() == 1 && local.getDayOfMonth() == 1 && local.getHour() == 0 && local.getMinute() < 10;
    }

    /** Midnight of the next New Year in the zone of {@code local}. The zone rules (summer/winter time) decide its real
     *  instant and its UTC offset, so the countdown is the real elapsed time even when the clocks change on the way. */
    private static ZonedDateTime newYearOf(ZonedDateTime local) {
        return ZonedDateTime.of(local.getYear() + 1, 1, 1, 0, 0, 0, 0, local.getZone());
    }

    private static long secondsToNewYear(ZonedDateTime local) {
        return Duration.between(local, newYearOf(local)).getSeconds();
    }

    private static String offset(Zone z, ZonedDateTime now) {
        int total = z.id().getRules().getOffset(now.toInstant()).getTotalSeconds();
        String sign = total < 0 ? "-" : "+";
        total = Math.abs(total);
        return "UTC" + sign + total / 3600 + (total % 3600 == 0 ? "" : String.format(":%02d", total % 3600 / 60));
    }

    /** Every second, called by Relics.newYearTick: detects zone midnights, then refreshes boards that have a player near. */
    void tick(java.util.Set<Relics.Relic> fired) {
        List<Relics.Relic> boards = new ArrayList<>();
        for (Relics.Relic r : relics.all()) if (r.type == RelicType.COUNTDOWN_BOARD) boards.add(r);
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        for (Zone z : zones) {
            int year = utc.withZoneSameInstant(z.id()).getYear();
            Integer last = zoneYear.put(z.label(), year);
            if (last != null && last != year) celebrate(z, year, boards, fired);
        }
        for (Relics.Relic r : boards) {
            if (lost(r) || r.world.getNearbyPlayers(r.center(), 48).isEmpty()) continue;
            refresh(r, utc);
        }
    }

    private void celebrate(Zone z, int year, List<Relics.Relic> boards, java.util.Set<Relics.Relic> fired) {
        boolean home = z.id().equals(RelicsPlugin.zone) || z.id().getRules().equals(RelicsPlugin.zone.getRules());
        for (Relics.Relic r : boards) {
            Zone own = zoneOf(r);
            if (own != null && !own.label().equals(z.label())) continue;
            if (!home) for (Player p : r.world.getNearbyPlayers(r.center(), 48)) // the home zone's title already goes to everybody
                p.showTitle(Title.title(Component.text("Happy New Year, " + z.label() + "!", NamedTextColor.GOLD), Component.text(String.valueOf(year), NamedTextColor.YELLOW)));
            boolean full = own != null || home; // a fixed board or the home zone gets the full show, other zones in "world" mode a shorter one
            for (int i = 0; i < (full ? 6 : 3); i++)
                Bukkit.getScheduler().runTaskLater(plugin, () -> r.world.playSound(r.center(), "relics:clock.strike", 2f, 1f), i * 30L);
            if (full || plugin.getConfig().getBoolean("new-year.fireworks-every-zone", true))
                for (Relics.Relic l : relics.launchersNear(r.center(), 64))
                    if (fired.add(l)) relics.show(l, full ? 12 : 6);
        }
    }

    private void refresh(Relics.Relic r, ZonedDateTime utc) {
        Zone own = zoneOf(r);
        List<Zone> candidates = own != null ? List.of(own) : zones;
        String title, main, sub;
        NamedTextColor mainColor = NamedTextColor.WHITE;
        Zone celebrating = null;
        for (Zone z : candidates) if (celebrating(utc.withZoneSameInstant(z.id()))) celebrating = z;
        if (celebrating != null) {
            ZonedDateTime local = utc.withZoneSameInstant(celebrating.id());
            title = "HAPPY NEW YEAR";
            main = String.valueOf(local.getYear());
            sub = celebrating.label() + " (" + offset(celebrating, local) + ")";
            mainColor = NamedTextColor.GOLD;
        } else {
            Zone best = null;
            long bestLeft = Long.MAX_VALUE;
            for (Zone z : candidates) {
                long left = secondsToNewYear(utc.withZoneSameInstant(z.id()));
                if (left < bestLeft) {
                    bestLeft = left;
                    best = z;
                }
            }
            ZonedDateTime local = utc.withZoneSameInstant(best.id());
            title = "NEW YEAR " + (local.getYear() + 1);
            main = String.format("%02d:%02d:%02d", bestLeft / 3600, bestLeft % 3600 / 60, bestLeft % 60);
            if (bestLeft < 60) mainColor = NamedTextColor.RED;
            else if (bestLeft < 3600) mainColor = NamedTextColor.YELLOW;
            sub = (own == null ? "Next: " : "") + best.label() + " (" + offset(best, newYearOf(local)) + ")"; // offset at midnight, not now
        }
        set(r, 0, title, NamedTextColor.GOLD);
        set(r, 1, main, mainColor);
        set(r, 2, sub, NamedTextColor.GRAY);
    }

    private void set(Relics.Relic r, int line, String text, NamedTextColor colour) {
        if (text.equals(r.last[line])) return;
        r.last[line] = text;
        r.texts.get(line).text(Component.text(text, colour));
    }

    // ------------------------------------------------------------ clicking

    void use(Relics.Relic r, Player p) {
        if (p.isSneaking() && (p.hasPermission("relics.admin") || p.getUniqueId().equals(r.owner))) {
            r.count = (r.count + 1) % (zones.size() + 1);
            r.last = new String[3];
            relics.save(r);
            Zone z = zoneOf(r);
            p.sendActionBar(Component.text("Board zone: " + (z == null ? "world (follows the next midnight)" : z.label() + " (" + z.id() + ")"), NamedTextColor.GOLD));
            return;
        }
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        List<Zone> order = new ArrayList<>(zones);
        order.sort((a, b) -> Long.compare(secondsToNewYear(utc.withZoneSameInstant(a.id())), secondsToNewYear(utc.withZoneSameInstant(b.id()))));
        p.sendMessage(Component.text("New Year around the world:", NamedTextColor.GOLD));
        for (Zone z : order) {
            ZonedDateTime local = utc.withZoneSameInstant(z.id());
            long s = secondsToNewYear(local);
            String left = local.getMonthValue() == 1 && local.getDayOfMonth() <= 2 ? "already " + local.getYear()
                    : s / 86400 > 0 ? s / 86400 + "d " + s % 86400 / 3600 + "h" : s / 3600 + "h " + s % 3600 / 60 + "m " + s % 60 + "s";
            p.sendMessage(Component.text(String.format(Locale.ROOT, " %-14s %-9s %s", z.label(), offset(z, newYearOf(local)), left), NamedTextColor.GRAY));
        }
    }
}
