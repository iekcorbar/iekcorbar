package com.iekcorbar.afkcinema;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Per speler: AFK-status, bioscoop-sessie en wat we moeten terugzetten. */
final class CineSession {

    enum Modus { ORBIT, DOELOF, PAD, OPNAME }

    final Player speler;
    long sindsActieMs;
    boolean afk;
    boolean cineToegelaten = true;      // /cine automatisch uit zet dit af

    // bioscoop-toestand
    Modus modus = Modus.ORBIT;
    boolean filmLoopt;
    GameMode vorigeGameMode;
    Location vorigeLoc;
    Location focusPunt;                 // waar de camera omheen draait
    Player doel;                        // bij DOELOF: het gefilmde slachtoffer (blijft zichtbaar)
    Object standIn;                     // bij ORBIT-onszelf: Citizens-NPC met jouw skin — dáár kijk je naar
    Location cameraLoc;                 // onze eigen teleport — herkennen om onszelf niet te cancellen
    double hoek;
    int liftTeller;

    // pad-afspeelstaat
    Pad pad;
    double[] cum;                       // cumulatieve tijden per segment
    double totaal;
    double padT;
    int padRichting = 1;                // pingpong
    boolean lusModus;                   // indices lopen rond

    // grapjes
    float standYaw;                     // waar de stand-in naar kijkt (hoofd draait langzaam mee)
    int takeDay;                        // dag-teller voor de director's-cut-grap
    int takes;

    // opname-modus (vrij kijken terwijl je waypoints zet)
    Pad opneemPad;

    CineSession(Player speler) {
        this.speler = speler;
        this.sindsActieMs = System.currentTimeMillis();
    }

    boolean hasMovedRecent(int seconden) {
        return System.currentTimeMillis() - sindsActieMs < seconden * 1000L;
    }

    void markeerActief() {
        sindsActieMs = System.currentTimeMillis();
        afk = false;
    }

    boolean actiefFilmpje() {
        return filmLoopt || modus == Modus.OPNAME;
    }
}
