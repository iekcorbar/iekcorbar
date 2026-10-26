package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.api.CustomNode;
import com.iekcorbar.steamworks.api.CustomPart;
import com.iekcorbar.steamworks.api.CustomPartRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The add-on side of Steamworks: other plugins register {@link CustomPart}s here and their
 * machines place, connect, turn, save and break like our own (KNode type CUSTOM).
 */
final class ExtParts implements CustomPartRegistry {
    private final SteamworksPlugin plugin;
    private final Map<String, CustomPart> parts = new LinkedHashMap<>();

    ExtParts(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(CustomPart part) {
        String id = part.id().toLowerCase(java.util.Locale.ROOT);
        CustomPart known = parts.get(id);
        if (known != null) {
            plugin.getLogger().warning("Custom part id '" + id + "' already registered by "
                    + known.ownerPlugin() + " - " + part.ownerPlugin() + "'s version was ignored.");
            return;
        }
        parts.put(id, part);
        plugin.getLogger().info("Add-on machine registered: " + part.displayName() + " (" + part.ownerPlugin() + ").");
    }

    @Override
    public CustomPart byId(String id) {
        return id == null ? null : parts.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Collection<CustomPart> parts() {
        return parts.values();
    }

    @Override
    public CustomPart partOf(ItemStack hand) {
        if (hand == null || hand.getType() != Material.PAPER || !hand.hasItemMeta()) return null;
        String id = hand.getItemMeta().getPersistentDataContainer().get(plugin.items().partKey, PersistentDataType.STRING);
        return id == null ? null : parts.get(id);
    }

    @Override
    public ItemStack item(CustomPart part, int amount) {
        ItemStack item = plugin.items().displayItem(part.id());      // paper + the add-on's FMM model
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(part.displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : part.lore()) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(part.ownerPlugin(), NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.items().partKey, PersistentDataType.STRING, part.id());
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, amount));
        return item;
    }

    /** Every machine of one plugin walked out of the world: dropped, unsaved from the chunk. */
    void unregister(Plugin owner) {
        List<CustomPart> mine = new ArrayList<>();
        for (CustomPart p : parts.values()) if (p.ownerPlugin().equalsIgnoreCase(owner.getName())) mine.add(p);
        for (CustomPart p : mine) parts.remove(p.id());
        if (mine.isEmpty()) return;
        for (KNode n : new ArrayList<>(plugin.kinetics().customs))
            if (mine.contains(n.ext)) plugin.breakNode(n, null, true, false);
        plugin.getLogger().info("Removed " + mine.size() + " add-on machine type(s) of " + owner.getName() + ".");
    }

    static CustomNode view(KNode n) {
        return new ExtNode(n);
    }
}
