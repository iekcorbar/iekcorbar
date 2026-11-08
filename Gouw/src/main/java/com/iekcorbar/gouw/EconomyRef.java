package com.iekcorbar.gouw;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Vault economy via reflection (no compile dependency, same trick as the old admin buychunks flow):
 * works whether or not Vault is installed; every call fails soft.
 */
final class EconomyRef {
    private EconomyRef() {
    }

    private static Object eco() {
        try {
            Class<?> c = Class.forName("net.milkbowl.vault.economy.Economy");
            var rsp = Bukkit.getServicesManager().getRegistration(c);
            return rsp == null ? null : rsp.getProvider();
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    public static boolean available() {
        return eco() != null;
    }

    public static boolean withdraw(OfflinePlayer p, double amount) {
        Object e = eco();
        if (e == null) return false;
        try {
            Class<?> c = findIface(e);
            if (!(boolean) c.getMethod("has", OfflinePlayer.class, double.class).invoke(e, p, amount)) return false;
            Object r = c.getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(e, p, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static boolean deposit(OfflinePlayer p, double amount) {
        Object e = eco();
        if (e == null) return false;
        try {
            Object r = findIface(e).getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(e, p, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static double balance(OfflinePlayer p) {
        Object e = eco();
        if (e == null) return 0;
        try {
            return (double) findIface(e).getMethod("getBalance", OfflinePlayer.class).invoke(e, p);
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    /** The actual Economy interface (providers are proxied/implementation classes). */
    private static Class<?> findIface(Object e) throws ClassNotFoundException {
        return Class.forName("net.milkbowl.vault.economy.Economy");
    }
}
