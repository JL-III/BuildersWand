package com.playtheatria.buildersWand.form;

/** Exact operational quantities calculated while evaluating a plan. */
public record PlanMetrics(int expandedCells, int touchedChunks) {
    public PlanMetrics {
        if (expandedCells < 0 || touchedChunks < 0) {
            throw new IllegalArgumentException("plan metrics cannot be negative");
        }
    }
}
