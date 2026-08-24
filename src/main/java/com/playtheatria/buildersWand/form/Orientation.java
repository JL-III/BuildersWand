package com.playtheatria.buildersWand.form;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;

/**
 * The frozen basis derived from the clicked face (design §5.3).
 *
 * <p>{@code l}, {@code s}, {@code n} are unit axis vectors:
 * <ul>
 *   <li>Wall (side face): {@code s} is UP, {@code n} the clicked face's outward normal,
 *       {@code l = (n.z, 0, −n.x)} (screen-right seen from outside the wall).</li>
 *   <li>Floor/ceiling (±Y face): {@code s} is the player-yaw cardinal, {@code n} the face
 *       normal (±Y), {@code l = (−s.z, 0, s.x)} (S rotated clockwise seen from above).</li>
 * </ul>
 */
public record Orientation(BlockVector l, BlockVector s, BlockVector n, boolean wall, BlockFace heading) {

    public static Orientation fromClick(BlockFace clickedFace, float playerYaw) {
        BlockVector n = new BlockVector(clickedFace.getModX(), clickedFace.getModY(), clickedFace.getModZ());
        if (clickedFace == BlockFace.UP || clickedFace == BlockFace.DOWN) {
            // Floor / ceiling: secondary axis is the player-facing cardinal.
            int idx = yawIndex(playerYaw);
            BlockVector s = cardinalVector(idx);
            BlockFace heading = cardinalFace(idx);
            BlockVector l = new BlockVector(-s.getBlockZ(), 0, s.getBlockX());
            return new Orientation(l, s, n, false, heading);
        }
        // Wall: secondary axis is up, lateral is screen-right from outside the wall.
        BlockVector s = new BlockVector(0, 1, 0);
        BlockVector l = new BlockVector(n.getBlockZ(), 0, -n.getBlockX());
        return new Orientation(l, s, n, true, clickedFace);
    }

    /** Bukkit yaw: 0=south, 90=west, 180=north, 270=east. Snap to nearest cardinal. */
    private static int yawIndex(float yaw) {
        float normalized = yaw % 360f;
        if (normalized < 0) {
            normalized += 360f;
        }
        return Math.round(normalized / 90f) % 4;
    }

    private static BlockVector cardinalVector(int idx) {
        return switch (idx) {
            case 0 -> new BlockVector(0, 0, 1);   // south
            case 1 -> new BlockVector(-1, 0, 0);  // west
            case 2 -> new BlockVector(0, 0, -1);  // north
            default -> new BlockVector(1, 0, 0);  // east
        };
    }

    private static BlockFace cardinalFace(int idx) {
        return switch (idx) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
