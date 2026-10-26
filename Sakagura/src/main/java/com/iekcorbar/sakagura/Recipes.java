package com.iekcorbar.sakagura;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recipes + how well a brew follows one. A brew is scored against each recipe whose
 * steps it has done (in order): ingredients (40%), cooking/mixing time (25%), distill
 * runs (15%) and ageing years + wood (20%), each judged more strictly for harder
 * recipes. The score gives the quality 1-10 (shown as 1-5 stars).
 */
final class Recipes {
    record Match(Recipe recipe, double score, int quality, boolean finished) {}

    private static final Set<String> HARMFUL = Set.of("poison", "wither", "weakness", "slowness", "nausea", "blindness", "hunger",
            "mining_fatigue", "instant_damage", "darkness", "bad_omen", "unluck");

    private final Sakagura plugin;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();

    Recipes(Sakagura plugin) {
        this.plugin = plugin;
    }

    void load() {
        loadFrom(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "recipes.yml")));
        plugin.getLogger().info("Loaded " + recipes.size() + " recipe(s).");
    }

    /** Reads recipes from a recipes.yml (also used by the offline test). */
    void loadFrom(YamlConfiguration y) {
        recipes.clear();
        ConfigurationSection s = y.getConfigurationSection("recipes");
        if (s != null) {
            for (String slug : s.getKeys(false)) {
                try {
                    recipes.put(slug, new Recipe(slug, s.getConfigurationSection(slug)));
                } catch (Exception e) {
                    warn("recipes.yml: skipping " + slug + " (" + e.getMessage() + ")");
                }
            }
        }
        // ingredient names that are drinks ("Gin") point to that recipe
        for (Recipe r : recipes.values()) {
            for (Recipe.Step st : r.steps) {
                Map<String, Integer> fixed = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> e : st.ingredients().entrySet()) {
                    String k = e.getKey();
                    if (k.startsWith("name:")) {
                        String name = k.substring(5);
                        for (Recipe o : recipes.values()) if (o.answersTo(name)) k = "brew:" + o.slug;
                        if (k.startsWith("name:")) warn("recipes.yml: " + r.slug + " uses unknown ingredient '" + name + "'");
                    }
                    fixed.merge(k, e.getValue(), Integer::sum);
                }
                st.ingredients().clear();
                st.ingredients().putAll(fixed);
            }
        }
    }

    private void warn(String msg) {
        if (plugin != null) plugin.getLogger().warning(msg);
        else System.out.println("WARN " + msg);
    }

    Collection<Recipe> all() {
        return recipes.values();
    }

    Recipe get(String slug) {
        return recipes.get(slug);
    }

    // ------------------------------------------------------------ scoring

    /** Score 0-1 for this brew against a recipe, or -1 if the brew can't be (on its way to) this drink. */
    double score(Recipe r, Brew b) {
        if (b.seq.isEmpty() || !r.sequence.startsWith(b.seq)) return -1;
        double tol = 1.6 - r.difficulty * 0.1;
        Recipe.Step first = r.first();
        double acc = 0;
        for (Map.Entry<String, Integer> e : first.ingredients().entrySet()) {
            int n = e.getValue(), a = b.ingredients.getOrDefault(e.getKey(), 0);
            acc += a == 0 ? 0 : Math.max(0, 1 - Math.abs(a - n) / (n * tol * 0.6 + 0.5));
        }
        int extras = 0;
        for (String k : b.ingredients.keySet()) if (!first.ingredients().containsKey(k)) extras++;
        double ing = (first.ingredients().isEmpty() ? 1 : acc / first.ingredients().size()) - 0.25 * extras;
        if (ing < 0.25) return -1;
        double total = 0.4 * ing, weight = 0.4;
        double t = first.type() == 'C' ? b.cook : b.mix;
        total += 0.25 * Math.max(0, 1 - Math.abs(t - first.time()) / (Math.max(1.5, first.time() * 0.4) * tol));
        weight += 0.25;
        Recipe.Step d = r.step('D');
        if (d != null && b.seq.indexOf('D') >= 0) {
            total += 0.15 * Math.max(0, 1 - Math.abs(b.distills - d.runs()) * 0.3 / tol);
            weight += 0.15;
        }
        Recipe.Step a = r.step('A');
        if (a != null && b.seq.indexOf('A') >= 0) {
            double age = Math.max(0, 1 - Math.abs(b.years - a.years()) / (Math.max(1, a.years() * 0.4) * tol));
            if (!a.wood().equals("any") && !a.wood().equals(b.wood)) age *= Woods.similar(a.wood(), b.wood) ? 0.75 : 0.55;
            total += 0.2 * age;
            weight += 0.2;
        }
        return Math.max(0, Math.min(1, total / weight));
    }

    /** Best recipe this brew matches (finished = all steps done), or null if it matches nothing. */
    Match match(Brew b) {
        Match best = null;
        for (Recipe r : recipes.values()) {
            double s = score(r, b);
            if (s < 0) continue;
            boolean done = r.sequence.equals(b.seq);
            // prefer finished matches, then the higher score
            if (best == null || (done && !best.finished()) || (done == best.finished() && s > best.score()))
                best = new Match(r, s, Math.max(1, Math.min(10, (int) Math.round(s * 10))), done);
        }
        if (best != null && best.finished() && best.score() < 0.3) return new Match(best.recipe(), best.score(), 0, true); // failed
        return best;
    }

    Recipe finished(Brew b) {
        Match m = match(b);
        return m != null && m.finished() && m.quality() > 0 ? m.recipe() : null;
    }

    // ------------------------------------------------------------ effects + lore

    /** Effects of a finished drink at a quality (harmful ones get milder with quality, good ones stronger). */
    List<PotionEffect> effects(Recipe r, int q) {
        List<PotionEffect> out = new ArrayList<>();
        for (String spec : r.effects) {
            String s = spec.trim();
            int plus = 0;
            while (plus < s.length() && s.charAt(plus) == '+') plus++;
            if (plus > 0 && plus != band(q)) continue;
            String[] p = s.substring(plus).split("/");
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(p[0].toLowerCase(Locale.ROOT)));
            if (type == null) continue;
            boolean harmful = HARMFUL.contains(p[0].toLowerCase(Locale.ROOT));
            double f = harmful ? (10 - q) / 10.0 : q / 10.0;
            int level = p.length >= 3 ? (int) Math.round(range(p[1], f)) : 0;
            double secs = range(p.length >= 3 ? p[2] : p.length == 2 ? p[1] : "10", f);
            if (secs <= 0 && !type.isInstant()) continue;
            out.add(new PotionEffect(type, type.isInstant() ? 1 : (int) Math.round(secs * 20), Math.max(0, level)));
        }
        return out;
    }

    private static double range(String s, double f) {
        String[] r = s.split("-");
        double a = Double.parseDouble(r[0]), b = r.length > 1 ? Double.parseDouble(r[1]) : a;
        return a + (b - a) * f;
    }

    /** 1 = poor, 2 = normal, 3 = good (for "+", "++", "+++" lines). */
    static int band(int q) {
        return q <= 3 ? 1 : q <= 7 ? 2 : 3;
    }

    /** Lore lines of the recipe that show at this quality (without their + marks). */
    List<String> lore(Recipe r, int q) {
        List<String> out = new ArrayList<>();
        for (String l : r.lore) {
            int plus = 0;
            while (plus < l.length() && l.charAt(plus) == '+') plus++;
            if (plus == 0 || plus == band(q)) out.add(l.substring(plus));
        }
        return out;
    }
}
