package com.playtheatria.buildersWand.form;

import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Dims are refused naming the span or scan bound, never the post-filter placement budget. */
class DimsTest {

    @Test
    void sphereOverFormerPlacementCapIsValidForScanning() {
        Result<Dims, IllegalArgumentException> result = Dims.validated(Form.SPHERE, 7, 3, 1);
        Ok<Dims, IllegalArgumentException> ok = assertInstanceOf(Ok.class, result);
        assertEquals(new Dims(7, 3, 1), ok.value());
    }

    @Test
    void boxPrimary17RefusedByNewSpanLimit() {
        Result<Dims, IllegalArgumentException> result = Dims.validated(Form.BOX, 17, 1, 1);
        Err<Dims, IllegalArgumentException> err = assertInstanceOf(Err.class, result);
        assertEquals("box primary 17 exceeds max 16", err.error().getMessage());
    }

    @Test
    void diagonalWidth6Refused() {
        Result<Dims, IllegalArgumentException> result = Dims.validated(Form.DIAGONAL, 1, 6, 1);
        Err<Dims, IllegalArgumentException> err = assertInstanceOf(Err.class, result);
        assertEquals("diagonal secondary 6 exceeds max 5", err.error().getMessage());
    }

    @Test
    void neverClamped() {
        // Refused dims come back as Err — never a silently clamped Ok.
        assertInstanceOf(Err.class, Dims.validated(Form.BOX, 17, 1, 1));
        FormLimits lowScanBudget = FormLimits.DEFAULTS.toBuilder()
                .maxCellsPerPrint(512)
                .maxScannedCellsPerPlan(600)
                .build();
        assertInstanceOf(Err.class, Dims.validated(
                Form.WALL, 32, 20, 1, Density.SHELL, lowScanBudget));
        // A legal request is accepted verbatim.
        Ok<Dims, IllegalArgumentException> ok = assertInstanceOf(Ok.class, Dims.validated(Form.BOX, 8, 8, 8));
        assertEquals(new Dims(8, 8, 8), ok.value());
    }

    @Test
    void densityChangesScanLegalityWithoutApplyingPlacementBudget() {
        FormLimits lowScanBudget = FormLimits.DEFAULTS.toBuilder()
                .maxCellsPerPrint(512)
                .maxScannedCellsPerPlan(600)
                .build();
        assertInstanceOf(Ok.class, Dims.validated(
                Form.BOX, 10, 10, 10, Density.SHELL, lowScanBudget));
        Err<Dims, IllegalArgumentException> solid = assertInstanceOf(
                Err.class, Dims.validated(
                        Form.BOX, 10, 10, 10, Density.SOLID, lowScanBudget));
        assertEquals("box(10,10,10) expands to 1000 candidate cells; scan max 600",
                solid.error().getMessage());

        // The default policy admits all 1,000 candidates; final printable-cell admission is later.
        assertInstanceOf(Ok.class, Dims.validated(Form.BOX, 10, 10, 10, Density.SOLID));
    }

    @Test
    void newFormSpansUseCentralDefaults() {
        assertInstanceOf(Ok.class, Dims.validated(Form.LINE, 64, 1, 1));
        assertInstanceOf(Err.class, Dims.validated(Form.LINE, 65, 1, 1));
        assertInstanceOf(Ok.class, Dims.validated(Form.WALL, 32, 32, 1));
        Err<Dims, IllegalArgumentException> tooWide = assertInstanceOf(
                Err.class, Dims.validated(Form.WALL, 32, 33, 1));
        assertEquals("wall secondary 33 exceeds max 32", tooWide.error().getMessage());
    }

    @Test
    void axisAccessAndReplacementAreExplicit() {
        Dims dims = new Dims(2, 3, 4);
        assertEquals(3, dims.axis(1));
        assertEquals(new Dims(2, 9, 4), dims.withAxis(1, 9));
    }
}
