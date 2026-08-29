package com.playtheatria.buildersWand.form;

import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;

/**
 * Per-axis semantic dimensions for a form. Dims are <strong>never clamped</strong> into legality:
 * callers either receive the requested values unchanged or a named policy refusal.
 */
public record Dims(int primary, int secondary, int tertiary) {

    public int axis(int axisIndex) {
        return switch (axisIndex) {
            case 0 -> primary;
            case 1 -> secondary;
            case 2 -> tertiary;
            default -> throw new IllegalArgumentException("axis index must be 0, 1, or 2");
        };
    }

    public Dims withAxis(int axisIndex, int value) {
        return switch (axisIndex) {
            case 0 -> new Dims(value, secondary, tertiary);
            case 1 -> new Dims(primary, value, tertiary);
            case 2 -> new Dims(primary, secondary, value);
            default -> throw new IllegalArgumentException("axis index must be 0, 1, or 2");
        };
    }

    public static Result<Dims, IllegalArgumentException> validated(Form form, int primary, int secondary, int tertiary) {
        return validated(form, primary, secondary, tertiary, Density.DEFAULT, FormLimits.DEFAULTS);
    }

    public static Result<Dims, IllegalArgumentException> validated(
            Form form, int primary, int secondary, int tertiary, Density density) {
        return validated(form, primary, secondary, tertiary, density, FormLimits.DEFAULTS);
    }

    public static Result<Dims, IllegalArgumentException> validated(
            Form form, int primary, int secondary, int tertiary, Density density, FormLimits limits) {
        Dims dims = new Dims(primary, secondary, tertiary);
        PlanDecision decision = new PlanPolicy(limits).evaluateDimensions(form, dims, density);
        if (!decision.allowed()) {
            return new Err<>(new IllegalArgumentException(decision.message()));
        }
        return new Ok<>(dims);
    }
}
