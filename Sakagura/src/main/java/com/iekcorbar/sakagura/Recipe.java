package com.iekcorbar.sakagura;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One drink from recipes.yml (format shared with iekcorbar.com/brewing). */
final class Recipe {
    record Step(char type, double time, int runs, double years, String wood, Map<String, Integer> ingredients) {}

    final String slug;
    final List<String> names;
    final Color color;
    final int difficulty, alcohol;
    final List<String> lore, effects;
    final List<Step> steps = new ArrayList<>();
    /** Process letters in order: C cook, M mix, D distill, A age. */
    final String sequence;

    Recipe(String slug, ConfigurationSection s) {
        this.slug = slug;
        names = s.getStringList("names");
        color = color(s.getString("color", "WHITE"));
        difficulty = Math.max(1, Math.min(10, s.getInt("difficulty", 5)));
        alcohol = s.getInt("alcohol", 0);
        lore = s.getStringList("lore");
        effects = s.getStringList("effects");
        StringBuilder seq = new StringBuilder();
        for (Map<?, ?> m : s.getMapList("steps")) {
            String type = String.valueOf(m.get("type")).toLowerCase(Locale.ROOT);
            char t = switch (type) {
                case "cook" -> 'C';
                case "mix" -> 'M';
                case "distill" -> 'D';
                case "age" -> 'A';
                default -> throw new IllegalArgumentException("unknown step " + type);
            };
            Map<String, Integer> ings = new LinkedHashMap<>();
            Object list = m.get("ingredients");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    String[] p = String.valueOf(o).split("/");
                    ings.merge(Ingredients.key(p[0].trim()), p.length > 1 ? Integer.parseInt(p[1].trim()) : 1, Integer::sum);
                }
            }
            steps.add(new Step(t, num(m.get("time"), 1), (int) num(m.get("runs"), 1), num(m.get("years"), 1),
                    m.get("wood") == null ? "any" : String.valueOf(m.get("wood")).toLowerCase(Locale.ROOT), ings));
            seq.append(t);
        }
        if (steps.isEmpty() || (steps.get(0).type != 'C' && steps.get(0).type != 'M'))
            throw new IllegalArgumentException("must start with cook or mix");
        sequence = seq.toString();
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    static Color color(String s) {
        s = s.trim();
        if (s.matches("(?i)[0-9a-f]{6}")) return Color.fromRGB(Integer.parseInt(s, 16));
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "BLACK" -> Color.fromRGB(30, 26, 24);
            case "DARK_RED" -> Color.fromRGB(110, 20, 20);
            case "RED" -> Color.fromRGB(190, 30, 30);
            case "GREEN" -> Color.fromRGB(60, 160, 60);
            case "ORANGE" -> Color.fromRGB(230, 130, 40);
            case "YELLOW" -> Color.fromRGB(240, 220, 70);
            case "BLUE" -> Color.fromRGB(50, 90, 210);
            case "PURPLE" -> Color.fromRGB(120, 50, 160);
            case "PINK" -> Color.fromRGB(240, 150, 190);
            case "BRIGHT_GREY", "GREY", "GRAY" -> Color.fromRGB(170, 170, 170);
            default -> Color.WHITE;
        };
    }

    /** Name for a quality 1-10 (one name, or poor / normal / good). */
    String name(int quality) {
        if (names.isEmpty()) return slug;
        if (names.size() == 1) return names.get(0);
        int i = quality <= 3 ? 0 : quality <= 7 ? 1 : 2;
        return names.get(Math.min(i, names.size() - 1));
    }

    boolean answersTo(String name) {
        for (String n : names) if (n.equalsIgnoreCase(name)) return true;
        return slug.equalsIgnoreCase(name.replace(' ', '_'));
    }

    Step first() {
        return steps.get(0);
    }

    Step step(char t) {
        for (Step s : steps) if (s.type == t) return s;
        return null;
    }
}
