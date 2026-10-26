package com.iekcorbar.steamworks.api;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * The door Steamworks opens for add-on machines: {@code SteamworksPlugin.customParts()}.
 * Register your {@link CustomPart}s from your plugin's onEnable (softdepend: [Steamworks]);
 * Steamworks un-registers them again when YOUR plugin disables, cleaning up placed machines.
 */
public interface CustomPartRegistry {

    /** Adds your machine to the network vocabulary (id() must be globally unique and lowercase). */
    void register(CustomPart part);

    /** The part with this id, or null. */
    CustomPart byId(String id);

    /** All registered parts, in registration order. */
    Collection<CustomPart> parts();

    /** The part the item in a player's hand represents, or null. */
    CustomPart partOf(ItemStack hand);

    /** A fresh item of this part (also what the workbench result should be built from). */
    ItemStack item(CustomPart part, int amount);

    /**
     * A Steamworks material/part item by id, for your recipes: "material/zinc_nugget",
     * "material/iron_dust", "part/cogwheel" - or null when the id is unknown.
     */
    ItemStack steamworksItem(String id);

    /** Removes and un-registers every machine of your plugin (call it from your onDisable). */
    void unregister(org.bukkit.plugin.Plugin owner);
}
