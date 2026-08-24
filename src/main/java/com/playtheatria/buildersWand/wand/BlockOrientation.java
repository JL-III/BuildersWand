package com.playtheatria.buildersWand.wand;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Slab;

/**
 * Turns a material into oriented block data for a rotation step (owner feature, 2026-08-24).
 * Step 0 is the material's own default orientation (identical regardless of where the player
 * faces); each step rotates from there. The 8 states are 4 horizontal quarter-turns
 * (clockwise from default) × bottom/top, so stairs reach every facing and the upside-down half.
 * Blocks with no orientation ignore the step.
 */
public final class BlockOrientation {

    /** Number of distinct rotation steps: 4 facings × bottom/top. */
    public static final int STATES = 8;

    private BlockOrientation() {
    }

    public static BlockData oriented(Material material, int step) {
        BlockData data = material.createBlockData();
        int normalized = ((step % STATES) + STATES) % STATES;
        boolean top = normalized >= 4;
        int turns = normalized % 4;

        if (top) {
            if (data instanceof Bisected bisected) {
                bisected.setHalf(Bisected.Half.TOP);
            } else if (data instanceof Slab slab && slab.getType() != Slab.Type.DOUBLE) {
                slab.setType(Slab.Type.TOP);
            }
        }

        if (data instanceof Directional directional) {
            BlockFace facing = rotate(directional.getFacing(), turns);
            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
            }
        } else if (data instanceof Orientable orientable) {
            Axis[] axes = {Axis.Y, Axis.X, Axis.Z};
            Axis target = axes[turns % axes.length];
            if (orientable.getAxes().contains(target)) {
                orientable.setAxis(target);
            }
        } else if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(rotate(rotatable.getRotation(), turns));
        }
        return data;
    }

    /** Rotate a compass face clockwise (viewed from above) by the given quarter-turns. */
    private static BlockFace rotate(BlockFace face, int quarterTurns) {
        BlockFace result = face;
        for (int i = 0; i < quarterTurns; i++) {
            result = switch (result) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                case NORTH_EAST -> BlockFace.SOUTH_EAST;
                case SOUTH_EAST -> BlockFace.SOUTH_WEST;
                case SOUTH_WEST -> BlockFace.NORTH_WEST;
                case NORTH_WEST -> BlockFace.NORTH_EAST;
                default -> result; // UP / DOWN and finer rotations pass through
            };
        }
        return result;
    }
}
