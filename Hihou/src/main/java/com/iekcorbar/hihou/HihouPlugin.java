package com.iekcorbar.hihou;

import com.iekcorbar.hihou.bw.BwGameManager;
import com.iekcorbar.hihou.bw.BwListeners;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 秘宝 Hihou - treasure/event/vote/lucky/victory chests with physical keys. v0.1: the chest side.
 * v0.2: the Game Island automation - finish-plate races, last-one-standing rounds and manual
 * awards. v0.3 (etappe 0.3.0 of the BedWars port, see HIHOU-DEV-NOTES.md): arenas, teams and the
 * join flow in the bw/ module - generators/beds come in 0.4.0, shop/upgrades/kits in 0.5.0.
 */
public final class HihouPlugin extends JavaPlugin {
    private Keys keys;
    private Chests chests;
    private Games games;
    private BwGameManager bw;
    private long tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keys = new Keys(this);
        chests = new Chests(this, keys);
        games = new Games(this, keys);
        bw = new BwGameManager(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(chests.decor, this);
        pm.registerEvents(chests, this);
        pm.registerEvents(games, this);
        pm.registerEvents(new BwListeners(bw), this);
        chests.decor.registerRecipes(); // none defined yet (chests are staff-given), harmless
        HihouCommand cmd = new HihouCommand(this);
        getCommand("hihou").setExecutor(cmd);
        getCommand("hihou").setTabCompleter(cmd);
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick += 5;
            chests.decor.tick(tick);
        }, 5L, 5L);
        games.onEnableCleanupAnnounce();
        bw.onEnableCleanupAnnounce();
        getLogger().info("Hihou enabled: " + Chests.TYPES.length + " chest types, " + Games.GAME_TYPES.length
                + " game modes, " + bw.names().size() + " BedWars arenas (etappe 0.3.0).");
    }

    @Override
    public void onDisable() {
        if (bw != null) bw.restoreAll();
        if (chests != null) {
            chests.decor.removeAllDisplays();
            chests.store.flush();
        }
    }

    Keys keys() {
        return keys;
    }

    Chests chests() {
        return chests;
    }

    Games games() {
        return games;
    }

    BwGameManager bw() {
        return bw;
    }
}
