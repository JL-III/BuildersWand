package com.playtheatria.buildersWand.form;

import org.bukkit.util.BlockVector;

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
        if (axisMax <= 0) {
            throw new IllegalArgumentException("axis max must be positive");
        }
        if (extent > axisMax) {
            return axisMax;
        }
        if (extent < -axisMax) {
            return -axisMax;
        }
        return extent;
    }

    /** Clamp against the same configured semantic span used by final plan admission. */
    public static int clampExtent(int extent, Form form, int axisIndex, FormLimits limits) {
        return clampExtent(extent, limits.maxSpan(form, axisIndex));
    }

    /**
     * Finds the largest signed extent at or below the requested magnitude whose expanded cell
     * count is legal. This is useful for live aim walk-down without duplicating shape formulas.
     */
    public static int largestLegalExtent(Form form, Density density, Dims otherAxes,
                                         int axisIndex, int requestedExtent, FormLimits limits) {
        if (requestedExtent == 0) {
            throw new IllegalArgumentException("requested extent must not be zero");
        }
        int sign = requestedExtent < 0 ? -1 : 1;
        long requestedMagnitude = Math.abs((long) requestedExtent);
        int magnitude = (int) Math.min(requestedMagnitude, limits.maxSpan(form, axisIndex));
        PlanPolicy policy = new PlanPolicy(limits);
        for (int candidate = magnitude; candidate >= 1; candidate--) {
            Dims dims = otherAxes.withAxis(axisIndex, candidate);
            if (policy.evaluateDimensions(form, dims, density).allowed()) {
                return sign * candidate;
            }
        }
        throw new IllegalArgumentException(
                "no legal " + form.key() + " extent with fixed dimensions " + otherAxes);
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

    /**
     * Deterministic signed dominant world axis for Line. Ties prefer X, then Y, then Z; a zero
     * vector resolves to positive X so callers always receive a unit axis.
     */
    public static BlockVector dominantAxis(int xOffset, int yOffset, int zOffset) {
        long x = Math.abs((long) xOffset);
        long y = Math.abs((long) yOffset);
        long z = Math.abs((long) zOffset);
        if (x >= y && x >= z) {
            return new BlockVector(xOffset < 0 ? -1 : 1, 0, 0);
        }
        if (y >= z) {
            return new BlockVector(0, yOffset < 0 ? -1 : 1, 0);
        }
        return new BlockVector(0, 0, zOffset < 0 ? -1 : 1);
    }
}
