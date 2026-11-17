package com.iekcorbar.xmas;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * FreeMinecraftModels via reflectie (geen compile-afhankelijkheid), dezelfde weg als Wildlife en
 * Steamworks nemen. FMM zet een onzichtbare mob onder een geanimeerd model:
 * {@code DynamicEntity.createWithInvisibility(model, mob)}, met "xmas_santa" als model-naam
 * (het bestand {@code plugins/FreeMinecraftModels/models/xmas/xmas_santa.bbmodel} - FMM pakt de
 * stam van de bestandsnaam, niet het pad).
 *
 * Waarom een kopie in plaats van Wildlife's helper: die is package-private in com.iekcorbar.wildlife
 * en heeft dier-specifieke aannames (rare/baby-kijkers, item-modellen voor statues). Een kerstman is
 * een eenling; deze vijftig regels doen alleen wat hij nodig heeft.
 */
final class Fmm {

    private Method create, getDynamic, play;
    private boolean broken;

    private boolean ready() {
        if (create != null) return true;
        if (broken) return false;
        try {
            Plugin fmm = Bukkit.getPluginManager().getPlugin("FreeMinecraftModels");
            if (fmm == null || !fmm.isEnabled()) return false;
            ClassLoader cl = fmm.getClass().getClassLoader();
            Class<?> de = Class.forName("com.magmaguy.freeminecraftmodels.customentity.DynamicEntity", true, cl);
            Class<?> me = Class.forName("com.magmaguy.freeminecraftmodels.customentity.ModeledEntity", true, cl);
            create = de.getMethod("createWithInvisibility", String.class, LivingEntity.class);
            getDynamic = de.getMethod("getDynamicEntity", Entity.class);
            play = me.getMethod("playAnimation", String.class, boolean.class, boolean.class);
            return true;
        } catch (Exception e) {
            broken = true;
            return false;
        }
    }

    boolean available() {
        return ready();
    }

    /** Zet het model op de mob; false als FMM de naam (nog) niet kent - probeer het dan opnieuw. */
    boolean attach(LivingEntity mob, String model) {
        if (!ready()) return false;
        try {
            return create.invoke(null, model, mob) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Een clip spelen: "idle", "walk", "greet", "death". Geen fout als FMM hem niet heeft. */
    void play(Entity mob, String animation, boolean loop) {
        if (!ready()) return;
        try {
            Object d = getDynamic.invoke(null, mob);
            if (d != null) play.invoke(d, animation, false, loop);
        } catch (Exception ignored) {
            // het model heeft die animatie niet
        }
    }
}
