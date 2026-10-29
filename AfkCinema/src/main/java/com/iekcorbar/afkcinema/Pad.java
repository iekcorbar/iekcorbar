package com.iekcorbar.afkcinema;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/** Een gefilmd camerapad: punten met positie én kijkrichting (de POV tijdens opname). */
final class Pad {

    record Punt(String wereld, double x, double y, double z, float yaw, float pitch) {
        Location loc(World w) { return new Location(w, x, y, z, yaw, pitch); }
    }

    private final String naam;
    private final List<Punt> punten = new ArrayList<>();
    private double snelheid;            // 0 = globale config

    Pad(String naam) { this.naam = naam; }

    String naam() { return naam; }
    List<Punt> punten() { return punten; }
    double snelheid() { return snelheid; }
    void snelheid(double s) { this.snelheid = s; }

    void voegToe(Location cam) {
        punten.add(new Punt(cam.getWorld().getName(),
            cam.getX(), cam.getY(), cam.getZ(), cam.getYaw(), cam.getPitch()));
    }

    /** Index is 1-based (zoals getoond in game). */
    boolean verwijder(int index) {
        if (index < 1 || index > punten.size()) return false;
        punten.remove(index - 1);
        return true;
    }

    World wereld() {
        if (punten.isEmpty()) return null;
        return Bukkit.getWorld(punten.get(0).wereld());
    }
}
