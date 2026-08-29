package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

/** Full-cell living-body obstruction policy for solid prints. */
public final class LivingBodyCollision {

    private LivingBodyCollision() {
    }

    /** Water cannot trap a body in a solid block, so only solid materials require clearance. */
    static boolean requiresClearance(PrintMaterial material) {
        return !material.isWater();
    }

    /** Whether placing this material in this cell must wait or be skipped to avoid clipping. */
    public static boolean blocks(PrintMaterial material, World world, Location cell) {
        if (!requiresClearance(material)) {
            return false;
        }
        int x = cell.getBlockX();
        int y = cell.getBlockY();
        int z = cell.getBlockZ();
        BoundingBox cellBox = cellBox(x, y, z);
        return !world.getNearbyEntities(cellBox, entity -> entity instanceof LivingEntity
                && overlapsCell(entity.getBoundingBox(), x, y, z)).isEmpty();
    }

    /** Exact face contact is safe; only positive-volume overlap occupies the future block cell. */
    static boolean overlapsCell(BoundingBox body, int x, int y, int z) {
        return body.overlaps(cellBox(x, y, z));
    }

    private static BoundingBox cellBox(int x, int y, int z) {
        return new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
    }
}
