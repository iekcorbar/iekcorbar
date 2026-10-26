package com.iekcorbar.sitesync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.UUID;

// Fires a genuine Minecraft advancement toast (top-right popup + sound) for
// arbitrary plugin-driven notifications — e.g. "a queued shop purchase was
// just delivered" — without leaving a permanent, cluttering entry in the
// player's real advancement tree. Dynamically registers a throwaway
// advancement via the Unsafe advancement-JSON loader (Bukkit.getUnsafe()),
// grants it for a few seconds so the client renders the toast, then revokes
// and unregisters it again. Must be called from the main thread (all the
// Bukkit calls here are).
//
// This is the same technique several advancement-toast plugins use; it isn't
// exercised by any automated test in this repo, so treat the exact JSON
// shape as best-effort until it's been seen firing on the live server (see
// the compile-verification note in CLAUDE.md's plugin section for the
// general pattern this project follows for that).
public class ToastNotifier {

    public static void showToast(Plugin plugin, Player player, String title, String icon) {
        showToast(plugin, player, "{\"text\":\"" + escape(title) + "\"}", icon, "goal", title);
    }

    // Same toast, but with a coloured title (legacy &-codes / MiniMessage, via
    // AdvancementManager.text) and a chosen frame — the frame decides the
    // client's header line ("Advancement Made!" / "Goal Reached!" / "Challenge Complete!").
    public static void showToast(Plugin plugin, Player player, Component title, String icon, String frame) {
        showToast(plugin, player, GsonComponentSerializer.gson().serialize(title), icon, frame,
                PlainTextComponentSerializer.plainText().serialize(title));
    }

    private static void showToast(Plugin plugin, Player player, String titleJson, String icon, String frame, String logName) {
        String keyName = "shop_toast_" + UUID.randomUUID().toString().replace("-", "");
        NamespacedKey key = new NamespacedKey(plugin, keyName);

        String json = "{"
                + "\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"display\":{"
                + "\"icon\":{\"id\":\"" + icon + "\"},"
                + "\"title\":" + titleJson + ","
                + "\"description\":{\"text\":\"\"},"
                + "\"frame\":\"" + frame + "\","
                + "\"announce_to_chat\":false,"
                + "\"show_toast\":true,"
                + "\"hidden\":true"
                + "}}";

        try {
            // Loading an advancement makes the server reload every online
            // player's advancement progress from disk — save first so freshly
            // earned (custom) advancements are never lost. See AdvancementManager.
            Bukkit.savePlayers();
            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
            if (advancement == null) return;

            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : new ArrayList<>(progress.getRemainingCriteria())) {
                progress.awardCriteria(criterion);
            }

            // Revoke + unregister a few seconds later — long enough for the
            // toast animation to play out, short enough that a burst of
            // notifications doesn't pile up dozens of throwaway advancements
            // registered on the server at once.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    AdvancementProgress p = player.getAdvancementProgress(advancement);
                    for (String criterion : new ArrayList<>(p.getAwardedCriteria())) {
                        p.revokeCriteria(criterion);
                    }
                    Bukkit.savePlayers();
                    Bukkit.getUnsafe().removeAdvancement(key);
                } catch (Exception ignored) {
                    // Player may have logged off already — nothing to clean up for them then.
                }
            }, 100L); // 5 seconds
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to show advancement toast '" + logName + "': " + e.getMessage());
        }
    }

    // Best-effort: pulls a Minecraft item id out of a give-style command
    // template for the toast icon, e.g. "give {player} milk_bucket 1" ->
    // "minecraft:milk_bucket". Falls back to a generic icon for anything that
    // doesn't look like a plain vanilla give (a "cmi give" alias, a custom
    // command syntax, etc.) rather than risk loadAdvancement rejecting an
    // invalid item id.
    public static String iconFromGiveCommand(String template) {
        if (template != null) {
            String[] parts = template.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("{player}") && i + 1 < parts.length) {
                    String candidate = parts[i + 1].toLowerCase();
                    if (candidate.matches("[a-z0-9_]+")) return "minecraft:" + candidate;
                }
            }
        }
        return "minecraft:chest";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
