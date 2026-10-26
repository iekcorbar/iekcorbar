package com.iekcorbar.steamworks.api;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.List;

/**
 * A machine that lives in another plugin but turns inside the Steamworks network.
 * Register one with {@code plugin(SteamworksPlugin.class).customParts().register(...)} from your
 * onEnable (declare {@code softdepend: [Steamworks]} so the call always works). Steamworks then
 * places, connects, powers, saves and draws it exactly like its own parts - you only answer
 * questions about YOUR machine, and tick it.
 *
 * <p>The item your part is built and placed with is created by Steamworks from the metadata
 * below, so it stays consistent with every other part (PDC marker, model, lore).</p>
 */
public interface CustomPart {

    /**
     * Globally unique lowercase id, e.g. "pow_water_turbine". It names your FMM model
     * ("pow_water_turbine.json" in the Steamworks model folder) and is what gets saved with every
     * placed machine - choose once, never change.
     */
    String id();

    /** Name on the item and in the info line, e.g. "Water Turbine". */
    String displayName();

    /** Lore lines on the item (short, the plugin name is appended by Steamworks). */
    String[] lore();

    /** Name of your plugin, shown on the item's last lore line. */
    String ownerPlugin();

    /** Particle block and placement sound when a player breaks it. */
    default Material particleBlock() {
        return Material.IRON_BLOCK;
    }

    // ------------------------------------------------------------ network shape

    /**
     * Which faces of the neighbour blocks connect this part to the network, relative to its
     * axis. Default: both shaft ends (the part turns like a shaft and passes rotation through).
     */
    default EnumSet<BlockFace> ports(Axis axis) {
        BlockFace end = positive(axis);
        return EnumSet.of(end, end.getOppositeFace());
    }

    static BlockFace positive(Axis a) {
        return a == Axis.X ? BlockFace.EAST : a == Axis.Y ? BlockFace.UP : BlockFace.SOUTH;
    }

    /** Speed factor of a shaft plugged into face f (1 = same speed, -1 = reversed, 2 = double). */
    default double portFactor(BlockFace f) {
        return 1;
    }

    /** Does the steam wrench turn this part? (default: yes, like a shaft) */
    default boolean rotatable() {
        return true;
    }

    /** Which way the part points when placed on face f by player p. Default: shaft-like, along the face. */
    default Axis initialAxis(BlockFace f, Player p) {
        return f.getModY() != 0 ? (p != null ? axisOf(p.getFacing()) : Axis.X) : axisOf(f);
    }

    static Axis axisOf(BlockFace f) {
        return f.getModX() != 0 ? Axis.X : f.getModY() != 0 ? Axis.Y : Axis.Z;
    }

    // ------------------------------------------------------------ power

    /** Sources: torque per RPM this machine delivers (0 = not a power source). */
    default double capacity() {
        return 0;
    }

    /** Consumers: torque per RPM this machine draws (0 = pure shaft). */
    default double impact() {
        return 0;
    }

    /** Sources: RPM you produce right now, signed along the positive axis. Called every second. */
    default double generatedSpeed(CustomNode n) {
        return 0;
    }

    // ------------------------------------------------------------ drawing & life

    /** The floating models: one spinning casing + shaft stub by default. */
    default List<CustomVisual> visuals(CustomNode n) {
        return List.of(CustomVisual.spin(id()));
    }

    /** Right-click (called before the info line; return true when you handled it). */
    default boolean interact(CustomNode n, Player p, ItemStack hand) {
        return false;
    }

    /** Your work: called every game tick while the chunk is loaded (guard your own interval). */
    default void tick(CustomNode n, long tick) {
    }

    /** Called once after placing (not after a chunk load). */
    default void onPlace(CustomNode n, Player by) {
    }

    /** Called when the machine leaves the world: broken, or the chunk it stands in unloads. Clean up entities you spawned yourself. */
    default void onRemove(CustomNode n) {
    }

    /** Extra state saved with the chunk (return null for none). Restore in {@link #loadState}. */
    default byte[] saveState(CustomNode n) {
        return null;
    }

    default void loadState(CustomNode n, byte[] data) {
    }
}
