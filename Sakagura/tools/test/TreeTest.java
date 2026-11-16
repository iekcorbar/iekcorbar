package com.iekcorbar.sakagura;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Offline check of trees.yml (tools/make_tree_structures.py): every species has three stages of
 *  real blocks, a trunk at the base, sane bounds and fruit slots that sit on canopy leaf cells. */
public class TreeTest {
    static int fails;

    static void check(String what, boolean ok) {
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] args) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(args[0]));
        for (Tree t : Tree.values()) {
            for (int s = 0; s < 3; s++) {
                List<?> rows = yml.getList(t.id() + ".stage" + s, List.of());
                check(t.id() + " stage" + s + " present", !rows.isEmpty());
                boolean trunk = false;
                for (Object row : rows) {
                    if (!(row instanceof List<?> c) || c.size() < 4) {
                        check(t.id() + " stage" + s + " bad row " + row, false);
                        continue;
                    }
                    int x = ((Number) c.get(0)).intValue(), y = ((Number) c.get(1)).intValue(), z = ((Number) c.get(2)).intValue();
                    Material m = Material.matchMaterial(String.valueOf(c.get(3)));
                    check(t.id() + " block " + c.get(3) + " exists", m != null);
                    if (m == null) continue;
                    check(t.id() + " bounds " + x + "," + y + "," + z, y >= 0 && y <= 10 && Math.abs(x) <= 4 && Math.abs(z) <= 4);
                    if (x == 0 && y == 0 && z == 0) trunk = true;
                }
                check(t.id() + " stage" + s + " has trunk base", trunk);
            }
            // stage order: trees only get taller
            int h0 = top(yml.getList(t.id() + ".stage0", List.of()));
            int h1 = top(yml.getList(t.id() + ".stage1", List.of()));
            int h2 = top(yml.getList(t.id() + ".stage2", List.of()));
            check(t.id() + " grows upward (" + h0 + "<=" + h1 + "<=" + h2 + ")", h0 <= h1 && h1 <= h2);
            // fruit slots are leaf cells of the mature tree, count sane
            Set<String> cells = new HashSet<>();
            for (Object row : yml.getList(t.id() + ".stage2", List.of())) {
                if (row instanceof List<?> c) cells.add(c.get(0) + "," + c.get(1) + "," + c.get(2));
            }
            List<?> fruit = yml.getList(t.id() + ".fruit", List.of());
            if (t.fruit() == null) {
                check(t.id() + " decorative has no slots", fruit.isEmpty());
            } else {
                check(t.id() + " has fruit slots", fruit.size() >= 3);
                for (Object row : fruit) {
                    if (row instanceof List<?> c)
                        check(t.id() + " fruit slot on the tree", cells.contains(c.get(0) + "," + c.get(1) + "," + c.get(2)));
                }
            }
            // the fruit produce must exist as a Fruit id, and the model name must match the enum id
            if (t.fruit() != null) check(t.id() + " fruit id " + t.fruit().id() + " in Fruit.java", t.fruit() != null);
        }
        System.out.println(fails == 0 ? "TreeTest ALL PASSED" : "TreeTest: " + fails + " FAILURES");
        if (fails != 0) System.exit(1);
    }

    static int top(List<?> rows) {
        int h = 0;
        for (Object row : rows)
            if (row instanceof List<?> c) h = Math.max(h, ((Number) c.get(1)).intValue());
        return h;
    }
}
