package com.iekcorbar.gouw;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Daily pay from the town bank for residents that the burgemeester assigned a taak (job) to.
 * Runs hourly; pays once per game-day after the configured salary-hour. Salary per job comes from
 * the Gouw config ("salaries: <job>: <amount>", fallback default-salary). A gouw with an empty or
 * too-small bank is skipped (one console line), so towns must fund their own payroll.
 */
final class Salaries {
    private final GouwPlugin plugin;
    private final ClaimStore store;
    private LocalDate lastRun = LocalDate.EPOCH;

    Salaries(GouwPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
        // remember the date across restarts so a reboot never double-pays
        java.io.File f = new java.io.File(plugin.getDataFolder(), "meta.yml");
        if (f.exists()) {
            String s = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f).getString("last-salary-day");
            if (s != null) try { lastRun = LocalDate.parse(s); } catch (Exception ignored) { }
        }
    }

    /** Called from a repeating timer; pays only the first tick of every configured hour after the reset hour. */
    void tick() {
        if (!plugin.getConfig().getBoolean("salary-enabled", true)) return;
        int hour = plugin.getConfig().getInt("salary-hour", 7);
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() < hour) return;
        LocalDate today = now.toLocalDate();
        if (lastRun.equals(today)) return;
        lastRun = today;
        payDay(today);
    }

    /** @return number of residents paid. Public so staff can force a payout from the admin page. */
    public int forceRun() {
        LocalDate today = LocalDate.now();
        lastRun = today;
        return payDay(today);
    }

    private int payDay(LocalDate today) {
        int paid = 0, skipped = 0;
        double defaultSalary = plugin.getConfig().getDouble("default-salary", 50);
        for (Land l : store.all()) {
            if (l.jobs.isEmpty()) continue;
            for (Map.Entry<UUID, String> jobEntry : Map.copyOf(l.jobs).entrySet()) {
                if (!l.ranks.containsKey(jobEntry.getKey())) continue; // lid is vertrokken of gekickt
                double amount = plugin.getConfig().getDouble("salaries." + jobEntry.getValue(), defaultSalary);
                if (amount <= 0) continue;
                if (l.bank < amount) {
                    skipped++;
                    plugin.getLogger().info("Gouw " + l.name + ": bank " + String.format("%.2f", l.bank)
                            + " cannot pay taak salary " + String.format("%.2f", amount) + " (" + nameOf(jobEntry.getKey()) + ")");
                    continue;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(jobEntry.getKey());
                if (EconomyRef.deposit(op, amount)) {
                    l.bank -= amount;
                    paid++;
                } else {
                    plugin.getLogger().warning("Vault deposit failed for " + op.getName() + " (taak salary)");
                }
            }
        }
        if (paid > 0 || skipped > 0) {
            store.save();
            saveDate(today);
            plugin.getLogger().info("Gouw salaries: paid " + paid + ", skipped " + skipped + " (insufficient bank).");
        }
        return paid;
    }

    private void saveDate(LocalDate d) {
        org.bukkit.configuration.file.YamlConfiguration y =
                new org.bukkit.configuration.file.YamlConfiguration();
        y.set("last-salary-day", d.toString());
        try { y.save(new java.io.File(plugin.getDataFolder(), "meta.yml")); }
        catch (java.io.IOException ex) { plugin.getLogger().warning("Could not save meta.yml: " + ex.getMessage()); }
    }

    private String nameOf(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? u.toString() : n;
    }
}
