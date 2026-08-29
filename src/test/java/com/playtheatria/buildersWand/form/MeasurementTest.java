package com.playtheatria.buildersWand.form;

import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure measurement helpers (design §7.4–§7.6). */
class MeasurementTest {

    @Test
    void extentSigning() {
        assertEquals(4, Measurement.extentFromOffset(3));
        assertEquals(-4, Measurement.extentFromOffset(-3));
        assertEquals(1, Measurement.extentFromOffset(0));
    }

    @Test
    void clampPreservesSign() {
        assertEquals(8, Measurement.clampExtent(10, 8));
        assertEquals(-8, Measurement.clampExtent(-10, 8));
        assertEquals(5, Measurement.clampExtent(5, 8));
        assertEquals(-5, Measurement.clampExtent(-5, 8));
    }

    @Test
    void radiusStepRounding() {
        assertEquals(1, Measurement.radiusStep(0, 0, 9));
        assertEquals(2, Measurement.radiusStep(1, 1, 9));
        assertEquals(5, Measurement.radiusStep(3.6, 0, 9));
        assertEquals(4, Measurement.radiusStep(3.6, 0, 4)); // capped by max
    }

    @Test
    void negativeAnchorShift() {
        assertEquals(-3, Measurement.negativeAnchorShift(-4));
        assertEquals(0, Measurement.negativeAnchorShift(0));
        assertEquals(0, Measurement.negativeAnchorShift(3));
    }

    @Test
    void configuredClampUsesCentralSpan() {
        assertEquals(64, Measurement.clampExtent(100, Form.LINE, 0, FormLimits.DEFAULTS));
        assertEquals(-32, Measurement.clampExtent(-100, Form.WALL, 1, FormLimits.DEFAULTS));
    }

    @Test
    void largestLegalExtentUsesCandidateScanBudgetNotPlacementBudget() {
        // Measurement can expose all 32 courses because world filtering applies the placement
        // budget later; the full 1,024-cell candidate plane is inside the 4,096 scan budget.
        assertEquals(32, Measurement.largestLegalExtent(
                Form.WALL, Density.SHELL, new Dims(32, 1, 1), 1, 32, FormLimits.DEFAULTS));
        assertEquals(-32, Measurement.largestLegalExtent(
                Form.WALL, Density.SHELL, new Dims(32, 1, 1), 1, -32, FormLimits.DEFAULTS));

        FormLimits lowScanBudget = FormLimits.DEFAULTS.toBuilder()
                .maxCellsPerPrint(512)
                .maxScannedCellsPerPlan(512)
                .build();
        assertEquals(16, Measurement.largestLegalExtent(
                Form.WALL, Density.SHELL, new Dims(32, 1, 1), 1, 32, lowScanBudget));
    }

    @Test
    void dominantAxisIsSignedAndHasStableTieBreaks() {
        assertEquals(new BlockVector(-1, 0, 0), Measurement.dominantAxis(-8, 2, 1));
        assertEquals(new BlockVector(0, 1, 0), Measurement.dominantAxis(2, 8, 1));
        assertEquals(new BlockVector(0, 0, -1), Measurement.dominantAxis(2, 3, -8));
        assertEquals(new BlockVector(1, 0, 0), Measurement.dominantAxis(5, 5, 5));
        assertEquals(new BlockVector(1, 0, 0), Measurement.dominantAxis(0, 0, 0));
    }
}
