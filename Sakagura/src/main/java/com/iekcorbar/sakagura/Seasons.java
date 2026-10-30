package com.iekcorbar.sakagura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;

import java.util.List;

/**
 * Season system for the Sakagura garden. Seasons cycle in real time and affect
 * which crops can grow and how fast. Each season lasts a configurable number of
 * real days. Season is stored in the server's world data so it persists.
 */
final class Seasons {
    private final Sakagura plugin;

    enum Season {
        SPRING("Spring", "Cherry blossoms and fresh growth", new String[]{"cherry", "ume", "peach", "strawberry", "apricot", "mikan", "kiwi", "tomato", "raspberry", "gooseberry"}),
        SUMMER("Summer", "Lush growth and tropical fruits", new String[]{"yuzu", "mango", "papaya", "guava", "rambutan", "longan", "mangosteen", "dragonfruit", "passionfruit", "vanilla", "chili", "maize", "tomato"}),
        AUTUMN("Autumn", "Harvest time and falling leaves", new String[]{"grape", "apple", "pear", "persimmon", "loquat", "pomegranate", "fig", "quince", "olive", "date", "lychee", "elderberry", "cranberry", "mulberry", "barley", "rye", "hops"}),
        WINTER("Winter", "Dormant gardens and hardy crops", new String[]{"ginger", "wasabi", "daikon", "rice", "juniper", "blueberry", "blackcurrant", "strawberry"});

        final String displayName;
        final String description;
        final String[] crops;

        Season(String displayName, String description, String[] crops) {
            this.displayName = displayName;
            this.description = description;
            this.crops = crops;
        }

        boolean canGrow(String cropId) {
            for (String c : crops) if (c.equals(cropId)) return true;
            return false;
        }

        String color() {
            return switch (this) {
                case SPRING -> "&a";
                case SUMMER -> "&e";
                case AUTUMN -> "&6";
                case WINTER -> "&b";
            };
        }
    }

    private Season current = Season.SPRING;
    private long seasonStart;
    private int daysPerSeason;

    Seasons(Sakagura plugin) {
        this.plugin = plugin;
        this.daysPerSeason = plugin.getConfig().getInt("seasons.days-per-season", 7);
        this.seasonStart = System.currentTimeMillis();
        load();
    }

    private void load() {
        seasonStart = plugin.getConfig().getLong("seasons.start-time", System.currentTimeMillis());
        String name = plugin.getConfig().getString("seasons.current", "SPRING");
        try { current = Season.valueOf(name); } catch (IllegalArgumentException e) { current = Season.SPRING; }
        // auto-advance if enough time has passed
        advance();
    }

    private void save() {
        plugin.getConfig().set("seasons.current", current.name());
        plugin.getConfig().set("seasons.start-time", seasonStart);
        plugin.saveConfig();
    }

    /** Check and advance season if needed. */
    void tick() {
        advance();
    }

    private void advance() {
        long elapsed = System.currentTimeMillis() - seasonStart;
        long seasonMs = (long) daysPerSeason * 24 * 60 * 60 * 1000;
        if (elapsed >= seasonMs) {
            int steps = (int) (elapsed / seasonMs);
            Season[] vals = Season.values();
            int idx = (current.ordinal() + steps) % vals.length;
            current = vals[idx];
            seasonStart = System.currentTimeMillis();
            save();
            // broadcast season change
            plugin.getServer().broadcast(Component.text(
                    "The season has changed to " + current.displayName + "! " + current.description,
                    NamedTextColor.GOLD));
        }
    }

    Season current() { return current; }

    boolean canGrow(String cropId) {
        return current.canGrow(cropId);
    }

    /** Get season growth multiplier (spring/summer = 1.0, autumn = 0.8, winter = 0.5 for non-winter crops). */
    double growthMultiplier(String cropId) {
        if (current.canGrow(cropId)) return 1.0;
        // crops that can't grow this season grow slower
        return switch (current) {
            case WINTER -> 0.3;
            case AUTUMN -> 0.7;
            default -> 0.5;
        };
    }

    /** Force set season (admin). */
    void setSeason(Season s) {
        current = s;
        seasonStart = System.currentTimeMillis();
        save();
    }

    /** Get time until next season. */
    long timeUntilNext() {
        long elapsed = System.currentTimeMillis() - seasonStart;
        long seasonMs = (long) daysPerSeason * 24 * 60 * 60 * 1000;
        return Math.max(0, seasonMs - elapsed);
    }

    String status() {
        long rem = timeUntilNext();
        long days = rem / (24 * 60 * 60 * 1000);
        long hours = (rem / (60 * 60 * 1000)) % 24;
        return current.displayName + " - " + current.description + " (next season in " + days + "d " + hours + "h)";
    }
}
