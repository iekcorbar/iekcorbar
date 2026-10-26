package com.iekcorbar.sakagura;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Offline check of the recipe scoring: perfect brews give their own drink at top quality, sloppy ones less. */
public class RecipeTest {
    static int fails;

    static void check(String what, boolean ok) {
        if (!ok) fails++;
        System.out.println((ok ? "OK   " : "FAIL ") + what);
    }

    static Brew perfect(Recipe r) {
        Brew b = new Brew();
        b.ingredients.putAll(r.first().ingredients());
        for (Recipe.Step st : r.steps) {
            switch (st.type()) {
                case 'C' -> b.cook = st.time();
                case 'M' -> b.mix = st.time();
                case 'D' -> b.distills = st.runs();
                case 'A' -> { b.years = st.years(); b.wood = st.wood().equals("any") ? "oak" : st.wood(); }
                default -> { }
            }
            b.step(st.type());
        }
        return b;
    }

    public static void main(String[] args) {
        Recipes recipes = new Recipes(null);
        recipes.loadFrom(YamlConfiguration.loadConfiguration(new File(args[0])));
        check("recipes loaded (" + recipes.all().size() + ")", recipes.all().size() >= 30);
        for (Recipe r : recipes.all()) {
            Recipes.Match m = recipes.match(perfect(r));
            boolean same = m != null && m.finished() && (m.recipe() == r || m.score() >= 0.99);
            check(r.slug + " perfect -> " + (m == null ? "nothing" : m.recipe().slug + " q" + m.quality()), same && m.quality() >= 9);
            // sloppy: double the time and an extra ingredient
            Brew bad = perfect(r);
            bad.cook *= 3;
            bad.mix *= 3;
            bad.ingredients.merge("mc:DIRT", 1, Integer::sum);
            Recipes.Match mb = recipes.match(bad);
            check(r.slug + " sloppy is worse", mb == null || mb.recipe() != r || mb.quality() < m.quality());
            // a step missing: not finished
            if (r.sequence.length() > 1) {
                Brew half = perfect(r);
                half.seq = r.sequence.substring(0, 1);
                Recipes.Match mh = recipes.match(half);
                check(r.slug + " half-done is unfinished", mh == null || !mh.finished() || mh.recipe() != r);
            }
        }
        // Brew text round trip
        Brew b = perfect(recipes.get("sake"));
        b.since = 123;
        Brew back = Brew.parse(b.format());
        check("brew round trip", back.format().equals(b.format()));
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
    }
}
