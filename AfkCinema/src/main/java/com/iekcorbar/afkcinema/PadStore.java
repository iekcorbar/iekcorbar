package com.iekcorbar.afkcinema;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leest en schrijft pads.yml: de camerapaden die met /cine opname worden gemaakt. */
final class PadStore {

    private final File bestand;
    private final Map<String, Pad> pads = new LinkedHashMap<>();

    PadStore(File dataFolder) {
        this.bestand = new File(dataFolder, "pads.yml");
    }

    Pad pad(String naam) { return naam == null ? null : pads.get(naam.toLowerCase()); }

    Pad pad(String naam, boolean aanmaken) {
        Pad p = pad(naam);
        if (p == null && aanmaken) {
            p = new Pad(naam);
            pads.put(naam.toLowerCase(), p);
        }
        return p;
    }

    List<String> namen() {
        List<String> out = new ArrayList<>();
        for (Pad p : pads.values()) out.add(p.naam());
        return out;
    }

    boolean verwijderPad(String naam) { return pads.remove(naam.toLowerCase()) != null; }

    void laad() {
        pads.clear();
        if (!bestand.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(bestand);
        ConfigurationSection root = y.getConfigurationSection("pads");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            Pad p = new Pad(key);
            p.snelheid(sec.getDouble("snelheid", 0));
            for (Map<?, ?> raw : sec.getMapList("punten")) {
                p.punten().add(new Pad.Punt(
                    String.valueOf(raw.get("wereld")),
                    ((Number) raw.get("x")).doubleValue(),
                    ((Number) raw.get("y")).doubleValue(),
                    ((Number) raw.get("z")).doubleValue(),
                    getal(raw.get("yaw"), 0f),
                    getal(raw.get("pitch"), 0f)));
            }
            pads.put(key.toLowerCase(), p);
        }
    }

    void opslaan() {
        YamlConfiguration y = new YamlConfiguration();
        for (Pad p : pads.values()) {
            ConfigurationSection sec = y.createSection("pads." + p.naam());
            if (p.snelheid() > 0) sec.set("snelheid", p.snelheid());
            List<Map<String, Object>> lijst = new ArrayList<>();
            for (Pad.Punt pt : p.punten()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("wereld", pt.wereld());
                m.put("x", round(pt.x()));
                m.put("y", round(pt.y()));
                m.put("z", round(pt.z()));
                m.put("yaw", round(pt.yaw()));
                m.put("pitch", round(pt.pitch()));
                lijst.add(m);
            }
            sec.set("punten", lijst);
        }
        try {
            y.save(bestand);
        } catch (IOException e) {
            throw new RuntimeException("pads.yml opslaan mislukt", e);
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static float getal(Object o, float standaard) {
        return o instanceof Number n ? n.floatValue() : standaard;
    }
}
