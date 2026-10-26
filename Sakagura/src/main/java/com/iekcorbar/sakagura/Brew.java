package com.iekcorbar.sakagura;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What happened to a brew so far - kept in the bottle's item data as one line:
 * ingredients, minutes cooked (heated cauldron) or mixed (cold cauldron), distill runs,
 * years aged and in which wood, and the order of the steps (C cook, M mix, D distill, A age).
 * While a brew sits in a cask, "since" is the moment it went in (real time keeps counting).
 */
final class Brew {
    final Map<String, Integer> ingredients = new LinkedHashMap<>();
    double cook, mix, years;
    int distills;
    String wood = "";
    String seq = "";
    long since;

    Brew copy() {
        Brew b = parse(format());
        b.since = since;
        return b;
    }

    /** Adds a step letter unless it repeats the last one (two distill runs = one D). */
    void step(char c) {
        if (seq.isEmpty() || seq.charAt(seq.length() - 1) != c) seq += c;
    }

    String format() {
        StringBuilder sb = new StringBuilder("ing=");
        boolean first = true;
        for (Map.Entry<String, Integer> e : ingredients.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('*').append(e.getValue());
            first = false;
        }
        sb.append(";cook=").append(round(cook)).append(";mix=").append(round(mix)).append(";dist=").append(distills)
                .append(";age=").append(round(years)).append(";wood=").append(wood).append(";seq=").append(seq)
                .append(";since=").append(since);
        return sb.toString();
    }

    private static String round(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    static Brew parse(String s) {
        Brew b = new Brew();
        if (s == null) return b;
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String k = part.substring(0, eq), v = part.substring(eq + 1);
            try {
                switch (k) {
                    case "ing" -> {
                        if (!v.isEmpty()) for (String i : v.split(",")) {
                            int st = i.lastIndexOf('*');
                            b.ingredients.put(i.substring(0, st), Integer.parseInt(i.substring(st + 1)));
                        }
                    }
                    case "cook" -> b.cook = Double.parseDouble(v);
                    case "mix" -> b.mix = Double.parseDouble(v);
                    case "dist" -> b.distills = Integer.parseInt(v);
                    case "age" -> b.years = Double.parseDouble(v);
                    case "wood" -> b.wood = v;
                    case "seq" -> b.seq = v;
                    case "since" -> b.since = Long.parseLong(v);
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // a damaged value: keep the default
            }
        }
        return b;
    }
}
