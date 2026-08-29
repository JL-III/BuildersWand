package com.playtheatria.buildersWand.wave;

import org.bukkit.Location;
import org.bukkit.util.BlockVector;

import java.util.Comparator;
import java.util.List;

/** Stable ordinary-form admission ordering around the player's original clicked anchor. */
public final class PlanOrdering {

    private PlanOrdering() {
    }

    public static List<PlannedCell> anchorOut(List<PlannedCell> cells, BlockVector anchor) {
        return cells.stream().sorted(comparator(anchor)).toList();
    }

    public static Comparator<PlannedCell> comparator(BlockVector anchor) {
        return Comparator
                .comparingLong((PlannedCell cell) -> distance(cell.location(), anchor))
                .thenComparingInt(cell -> cell.location().getBlockY())
                .thenComparingInt(cell -> cell.location().getBlockZ())
                .thenComparingInt(cell -> cell.location().getBlockX());
    }

    static long distance(Location location, BlockVector anchor) {
        return Math.abs((long) location.getBlockX() - anchor.getBlockX())
                + Math.abs((long) location.getBlockY() - anchor.getBlockY())
                + Math.abs((long) location.getBlockZ() - anchor.getBlockZ());
    }
}
