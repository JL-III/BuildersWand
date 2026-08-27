package com.playtheatria.buildersWand.stats;

public record ServerBuildStats(
        long playersTracked,
        long totalUses,
        long nonWaterBlocksPlaced,
        long waterSourceBlocksPlaced,
        long printsCompleted,
        long largestCompletedPrint,
        long refillsCompleted,
        long refillUsesPurchased,
        long refillDenariiSpent
) {
    /** Compatibility constructor for callers compiled against the original build-only totals. */
    public ServerBuildStats(
            long playersTracked,
            long totalUses,
            long nonWaterBlocksPlaced,
            long waterSourceBlocksPlaced,
            long printsCompleted,
            long largestCompletedPrint
    ) {
        this(playersTracked, totalUses, nonWaterBlocksPlaced, waterSourceBlocksPlaced,
                printsCompleted, largestCompletedPrint, 0, 0, 0);
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
