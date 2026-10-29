package com.iekcorbar.afkcinema;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Per speler: AFK-status, bioscoop-sessie en wat we moeten terugzetten. */
final class CineSession {
    final Player speler;
    long sinceActionMs;
    boolean afk;
    boolean cinemaToegelaten = true;   // /cine automatisch uit zet dit af

    // bioscoop-toestand
    boolean filmLoopt;
    GameMode vorigeGameMode;
    Location vorigeLoc;
    Location focusPunt;        // waar de camera omheen draait (hoofd van de speler bij AFK-start)
    double hoek;               // huidige positie op de baan (rad)
    int liftTeller;            // botsings-correctie-ticks

    CineSession(Player speler) {
        this.speler = speler;
        this.sinceActionMs = System.currentTimeMillis();
    }

    boolean hasMovedRecent(int seconden) {
        return System.currentTimeMillis() - sinceActionMs < seconden * 1000L;
    }

    void markeerActief() {
        sinceActionMs = System.currentTimeMillis();
        afk = false;
    }
}
