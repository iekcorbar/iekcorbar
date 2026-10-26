package com.iekcorbar.sakagura;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

/** Cask woods (plus the steampunk copper cask) and which ones taste alike. */
enum Woods {
    OAK(Material.OAK_PLANKS), SPRUCE(Material.SPRUCE_PLANKS), BIRCH(Material.BIRCH_PLANKS), JUNGLE(Material.JUNGLE_PLANKS),
    ACACIA(Material.ACACIA_PLANKS), DARK_OAK(Material.DARK_OAK_PLANKS), CHERRY(Material.CHERRY_PLANKS),
    MANGROVE(Material.MANGROVE_PLANKS), BAMBOO(Material.BAMBOO_PLANKS), COPPER(Material.COPPER_BLOCK);

    private static final Set<Set<String>> SIMILAR = Set.of(Set.of("oak", "dark_oak"), Set.of("oak", "birch"), Set.of("spruce", "dark_oak"),
            Set.of("jungle", "bamboo"), Set.of("acacia", "mangrove"), Set.of("cherry", "birch"));

    final Material planks;

    Woods(Material planks) {
        this.planks = planks;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    String label() {
        String s = id().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static boolean similar(String a, String b) {
        return SIMILAR.contains(Set.of(a, b));
    }

    static Woods byId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
