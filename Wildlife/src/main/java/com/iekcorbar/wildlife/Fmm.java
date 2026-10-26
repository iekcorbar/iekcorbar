package com.iekcorbar.wildlife;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * FreeMinecraftModels by reflection (no compile dependency, same as Steamworks):
 * DynamicEntity.createWithInvisibility(model, mob) puts an animated model on an
 * invisible mob; FMM plays idle/walk/death itself, playAnimation does the rest.
 */
final class Fmm {
    private final Logger log;
    private Method create, isDynamic, getDynamic, play, stop, applyDisplayModel;
    private boolean broken;

    Fmm(Logger log) {
        this.log = log;
    }

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
            isDynamic = de.getMethod("isDynamicEntity", Entity.class);
            getDynamic = de.getMethod("getDynamicEntity", Entity.class);
            play = me.getMethod("playAnimation", String.class, boolean.class, boolean.class);
            stop = me.getMethod("stopCurrentAnimations");
            applyDisplayModel = Class.forName("com.magmaguy.freeminecraftmodels.api.ModelItemAPI", true, cl)
                    .getMethod("applyDisplayModel", ItemStack.class, String.class);
            return true;
        } catch (Exception e) {
            broken = true;
            log.warning("FreeMinecraftModels API not found (" + e + ") - no animals will spawn.");
            return false;
        }
    }

    boolean available() {
        return ready();
    }

    boolean modeled(Entity e) {
        if (!ready()) return false;
        try {
            return (boolean) isDynamic.invoke(null, e);
        } catch (Exception ex) {
            return false;
        }
    }

    /** Puts the model on the mob; false if FMM doesn't know the model (yet). */
    boolean attach(LivingEntity mob, String model) {
        if (!ready()) return false;
        try {
            return create.invoke(null, model, mob) != null;
        } catch (Exception e) {
            return false;
        }
    }

    void play(Entity mob, String animation, boolean loop) {
        if (!ready()) return;
        try {
            Object d = getDynamic.invoke(null, mob);
            if (d != null) play.invoke(d, animation, false, loop);
        } catch (Exception ignored) {
            // the model has no such animation
        }
    }

    void stop(Entity mob) {
        if (!ready()) return;
        try {
            Object d = getDynamic.invoke(null, mob);
            if (d != null) stop.invoke(d);
        } catch (Exception ignored) {
        }
    }

    boolean itemModel(ItemStack item, String model) {
        if (!ready()) return false;
        try {
            return (boolean) applyDisplayModel.invoke(null, item, model);
        } catch (Exception e) {
            return false;
        }
    }
}
