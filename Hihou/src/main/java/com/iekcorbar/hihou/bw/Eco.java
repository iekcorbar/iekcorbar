package com.iekcorbar.hihou.bw;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 0.6.0: the Vault lane for BedWars rewards. Reflection instead of a compile dependency
 * (same trick Gouw and Hihou's kits already use elsewhere), so a server without an economy
 * plugin simply pays nothing instead of throwing - the match never depends on money.
 */
final class Eco {
    private Eco() {
    }

    /** @return true when the player's wallet actually grew. */
    static boolean pay(Player p, double amount) {
        if (amount <= 0) return true;
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) return false;
            Object e = rsp.getProvider();
            Object r = eco.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(e, p, amount);
            return r != null && (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
