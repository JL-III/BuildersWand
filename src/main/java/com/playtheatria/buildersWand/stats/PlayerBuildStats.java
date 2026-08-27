package com.playtheatria.buildersWand.stats;

import java.util.UUID;

public record PlayerBuildStats(
        UUID playerId,
        String lastKnownName,
        long totalUses,
        long nonWaterBlocksPlaced,
        long waterSourceBlocksPlaced,
        long printsCompleted,
        long largestCompletedPrint,
        long updatedAtEpochMillis,
        long refillsCompleted,
        long refillUsesPurchased,
        long refillDenariiSpent
) {
    /** Compatibility constructor for callers compiled against the original build-only totals. */
    public PlayerBuildStats(
            UUID playerId,
            String lastKnownName,
            long totalUses,
            long nonWaterBlocksPlaced,
            long waterSourceBlocksPlaced,
            long printsCompleted,
            long largestCompletedPrint,
            long updatedAtEpochMillis
    ) {
        this(playerId, lastKnownName, totalUses, nonWaterBlocksPlaced, waterSourceBlocksPlaced,
                printsCompleted, largestCompletedPrint, updatedAtEpochMillis, 0, 0, 0);
    }

    public long totalBlocksPlaced() {
        return saturatedAdd(nonWaterBlocksPlaced, waterSourceBlocksPlaced);
    }

    private static long saturatedAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
