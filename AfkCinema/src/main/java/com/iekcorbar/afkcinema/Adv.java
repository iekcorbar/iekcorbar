package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verborgen advancements in de stijl van alle andere iekcorbar-plugins:
 * declaratie in code, loadAdvancement, grant vanuit de code.
 * Namespace: afkcinema:afkcinema/<id> — zelfde dubbel-patroon als de rest.
 */
final class Adv {

    private record Def(String title, String desc, String icon, String parent, boolean hidden) { }

    private final AfkCinemaPlugin plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();

    Adv(AfkCinemaPlugin plugin) {
        this.plugin = plugin;
        defs.put("root", new Def("AfkCinema", "The film studio on the steam railway.", "book", null, false));
        defs.put("extra", new Def("Extra!", "Someone wandered into your shot.", "ender_eye", "root", true));
        defs.put("closing_credits", new Def("Roll Credits", "Fall asleep while the camera is running.", "red_bed", "root", true));
        defs.put("directors_cut", new Def("Director's Cut", "Demand seven takes in one day.", "clock", "root", true));
    }

    void register() {
        for (Map.Entry<String, Def> e : defs.entrySet()) {
            NamespacedKey k = key(e.getKey());
            if (Bukkit.getAdvancement(k) != null) {
                try { Bukkit.getUnsafe().removeAdvancement(k); } catch (Throwable ignored) { }
            }
            Bukkit.getUnsafe().loadAdvancement(k, json(e.getValue()));
        }
    }

    private NamespacedKey key(String id) { return new NamespacedKey(plugin, "afkcinema/" + id); }

    void grant(Player p, String id) {
        Advancement adv = Bukkit.getAdvancement(key(id));
        if (adv == null) return;
        Def d = defs.get(id);
        if (d != null && d.parent() != null && !has(p, d.parent())) grant(p, d.parent());
        AdvancementProgress pr = p.getAdvancementProgress(adv);
        if (pr.isDone()) return;
        for (String c : new ArrayList<>(pr.getRemainingCriteria())) pr.awardCriteria(c);
    }

    boolean has(Player p, String id) {
        Advancement adv = Bukkit.getAdvancement(key(id));
        return adv != null && p.getAdvancementProgress(adv).isDone();
    }

    private String json(Def d) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"criteria\":{\"done\":{\"trigger\":\"minecraft:impossible\"}},");
        sb.append("\"display\":{\"icon\":{\"item\":\"minecraft:").append(d.icon()).append("\"},");
        sb.append("\"title\":\"").append(esc(d.title())).append("\",");
        sb.append("\"description\":\"").append(esc(d.desc())).append("\",");
        sb.append("\"frame\":\"task\",\"show_toast\":true,\"announce_to_chat\":false");
        if (d.hidden()) sb.append(",\"hidden\":true");
        sb.append("}");
        if (d.parent() != null) sb.append(",\"parent\":\"afkcinema:afkcinema/").append(d.parent()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
