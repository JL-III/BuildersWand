package com.playtheatria.buildersWand.form;

import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;

/**
 * Per-axis dimensions for a form (design §6). {@link #validated} enforces the §6 bounds table
 * and the {@link Expansion#MAX_WAVE_CELLS} cap, naming the violated bound. Dims are
 * <strong>never clamped</strong> into legality — refused instead.
 *
 * <p>The error is carried as an {@link IllegalArgumentException} (the shipped {@code Result}
 * type constrains its error to {@code Throwable}); its message is the named bound, and
 * {@link Expansion#cells} rethrows it when handed refused dims.
 */
public record Dims(int primary, int secondary, int tertiary) {

    public static Result<Dims, IllegalArgumentException> validated(Form form, int primary, int secondary, int tertiary) {
        IllegalArgumentException axisError = checkAxis(form, "primary", primary, 0);
        if (axisError != null) {
            return new Err<>(axisError);
        }
        axisError = checkAxis(form, "secondary", secondary, 1);
        if (axisError != null) {
            return new Err<>(axisError);
        }
        axisError = checkAxis(form, "tertiary", tertiary, 2);
        if (axisError != null) {
            return new Err<>(axisError);
        }
        Dims dims = new Dims(primary, secondary, tertiary);
        int count = Expansion.cellCount(form, dims);
        if (count > Expansion.MAX_WAVE_CELLS) {
            return new Err<>(new IllegalArgumentException(
                    form.key() + "(" + primary + "," + secondary + ") is " + count
                            + " cells; max " + Expansion.MAX_WAVE_CELLS));
        }
        return new Ok<>(dims);
    }

    private static IllegalArgumentException checkAxis(Form form, String axisName, int value, int axisIndex) {
        int[] bounds = bounds(form, axisIndex);
        int min = bounds[0];
        int max = bounds[1];
        if (value < min) {
            return new IllegalArgumentException(form.key() + " " + axisName + " " + value + " below min " + min);
        }
        if (value > max) {
            return new IllegalArgumentException(form.key() + " " + axisName + " " + value + " exceeds max " + max);
        }
        return null;
    }

    /** {@code {min, max}} for the given axis (0=primary, 1=secondary, 2=tertiary), per §6. */
    private static int[] bounds(Form form, int axisIndex) {
        return switch (form) {
            case DIAGONAL -> switch (axisIndex) {
                case 0 -> new int[]{1, 8}; // run
                case 1 -> new int[]{1, 5}; // width (tread)
                default -> new int[]{1, 1};
            };
            case BOX -> new int[]{1, 8}; // p, s, t all 1..8
            case CYLINDER -> switch (axisIndex) {
                case 0 -> new int[]{1, 9}; // step
                case 1 -> new int[]{1, 8}; // courses
                default -> new int[]{1, 1};
            };
            case SPHERE -> switch (axisIndex) {
                case 0 -> new int[]{1, 7}; // step
                case 1 -> new int[]{1, 8}; // length
                default -> new int[]{1, 1};
            };
        };
    }
}
