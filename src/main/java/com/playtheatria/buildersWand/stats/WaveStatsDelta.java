package com.playtheatria.buildersWand.stats;

/**
 * The durable statistics contributed by one settled or stopped build wave.
 *
 * @param completedUses Uses spent (or bypass-equivalent Uses), which may exceed completed cells
 * @param nonWaterBlocksPlaced non-water blocks this wave actually placed
 * @param waterSourceBlocksPlaced water source blocks this wave actually placed
 * @param printCompleted whether the complete planned print finished
 * @param plannedPrintableCells total printable cells in the original plan
 */
public record WaveStatsDelta(
        long completedUses,
        long nonWaterBlocksPlaced,
        long waterSourceBlocksPlaced,
        boolean printCompleted,
        long plannedPrintableCells
) {
}
