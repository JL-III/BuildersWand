package com.playtheatria.buildersWand.form;

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
}
