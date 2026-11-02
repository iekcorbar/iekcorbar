package com.iekcorbar.sakagura;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared orientation maths for the barrel-covers (Steam Still and Sake Casks).
 * Pattern copied from SteamworksAdditions Decor.java: the model's front is its NORTH side
 * (-Z), and an ItemDisplay shows the model turned 180 degrees about Y, so a cover gets
 * yaw(facing) to make the front really point the way the player looked when placing.
 */
final class CoverTransform {
    private CoverTransform() { }

    /** Horizontal facing from the player: where they look, or their body yaw when aiming straight up/down. */
    static BlockFace horizontalFacing(Player p) {
        var d = p.getEyeLocation().getDirection();
        if (Math.abs(d.getX()) < 0.2 && Math.abs(d.getZ()) < 0.2) {
            float yaw = ((p.getLocation().getYaw() % 360) + 360) % 360; // 0=zuid, 90=west, 180=noord, 270=oost
            if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
            if (yaw < 135) return BlockFace.WEST;
            if (yaw < 225) return BlockFace.NORTH;
            return BlockFace.EAST;
        }
        return Math.abs(d.getX()) > Math.abs(d.getZ())
                ? (d.getX() > 0 ? BlockFace.EAST : BlockFace.WEST)
                : (d.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
    }

    /** PDC facing of a covered barrel; old covers without a tag keep their pre-fix look (front south). */
    static BlockFace facingOf(Block b, NamespacedKey facingKey) {
        if (b.getState(false) instanceof Barrel barrel) {
            String s = barrel.getPersistentDataContainer().get(facingKey, PersistentDataType.STRING);
            if (s != null) {
                try { return BlockFace.valueOf(s); } catch (IllegalArgumentException ignored) { }
            }
        }
        return BlockFace.SOUTH;
    }

    static void storeFacing(Barrel barrel, NamespacedKey facingKey, BlockFace face) {
        barrel.getPersistentDataContainer().set(facingKey, PersistentDataType.STRING, face.name());
    }

    /** NONE-context cover transformation: spin about the block centre, keep the 1% over-scale against z-fighting. */
    static Transformation rotated(BlockFace f) {
        float yaw = switch (f) {
            case EAST -> -90f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            default -> 0f;
        };
        Quaternionf q = new Quaternionf();
        q.rotateY((float) Math.toRadians(yaw));
        return new Transformation(new Vector3f(), q, new Vector3f(1.01f, 1.01f, 1.01f), new Quaternionf());
    }
}
