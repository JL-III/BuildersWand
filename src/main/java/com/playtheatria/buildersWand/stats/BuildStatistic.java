package com.playtheatria.buildersWand.stats;

/** Statistics that may receive one-time recognition thresholds. */
public enum BuildStatistic {
    TOTAL_USES("total_uses", "total_uses"),
    TOTAL_BLOCKS_PLACED(
            "total_blocks_placed",
            "non_water_blocks_placed + water_source_blocks_placed"
    ),
    NON_WATER_BLOCKS_PLACED("non_water_blocks_placed", "non_water_blocks_placed"),
    WATER_SOURCE_BLOCKS_PLACED("water_source_blocks_placed", "water_source_blocks_placed"),
    PRINTS_COMPLETED("prints_completed", "prints_completed"),
    LARGEST_COMPLETED_PRINT("largest_completed_print", "largest_completed_print"),
    REFILLS_COMPLETED("refills_completed", "refills_completed"),
    REFILL_USES_PURCHASED("refill_uses_purchased", "refill_uses_purchased"),
    REFILL_DENARII_SPENT("refill_denarii_spent", "refill_denarii_spent");

    private final String storageKey;
    private final String valueExpression;

    BuildStatistic(String storageKey, String valueExpression) {
        this.storageKey = storageKey;
        this.valueExpression = valueExpression;
    }

    String storageKey() {
        return storageKey;
    }

    String valueExpression() {
        return valueExpression;
    }
}
