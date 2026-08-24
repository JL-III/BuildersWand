package com.playtheatria.buildersWand.form;

/**
 * Pure measurement helpers used to turn live aim into legal dims (design §7.4–§7.6).
 * Only {@link Measurement} clamps — {@link Dims} never does.
 */
public final class Measurement {

    private Measurement() {
    }

    /**
     * Signed inclusive extent from a signed in-plane cell offset (design §7.4).
     * Never returns 0: offset 3 → 4, offset −3 → −4, offset 0 → 1.
     */
    public static int extentFromOffset(int cellOffset) {
        return cellOffset >= 0 ? cellOffset + 1 : cellOffset - 1;
    }

    /** Magnitude-clamp an extent to {@code ±axisMax}, preserving sign (design §7.4). */
    public static int clampExtent(int extent, int axisMax) {
        if (extent > axisMax) {
            return axisMax;
        }
        if (extent < -axisMax) {
            return -axisMax;
        }
        return extent;
    }

    /**
     * Circle sizing (design §7.4): {@code step = round(hypot(a, b)) + 1}, clamped to
     * {@code [1, max]}.
     */
    public static int radiusStep(double a, double b, int max) {
        int step = (int) Math.round(Math.hypot(a, b)) + 1;
        return Math.max(1, Math.min(max, step));
    }

    /**
     * Effective-anchor shift for a negative drag (design §7.5): the clicked cell stays the
     * near corner. Extent −4 → shift −3; non-negative extents do not shift.
     */
    public static int negativeAnchorShift(int extent) {
        return extent < 0 ? extent + 1 : 0;
    }
}
