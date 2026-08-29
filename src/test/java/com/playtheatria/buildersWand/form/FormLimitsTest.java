package com.playtheatria.buildersWand.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormLimitsTest {

    @Test
    void defaultsMatchTheDesignBoundaries() {
        FormLimits limits = FormLimits.DEFAULTS;
        assertEquals(1_024, limits.maxCellsPerPrint());
        assertEquals(4_096, limits.maxScannedCellsPerPlan());
        assertEquals(9, limits.maxChunksPerPrint());
        assertEquals(new Dims(64, 1, 1), limits.maxSpans(Form.LINE));
        assertEquals(new Dims(32, 32, 1), limits.maxSpans(Form.WALL));
        assertEquals(new Dims(32, 32, 1), limits.maxSpans(Form.FLOOR));
        assertEquals(new Dims(16, 16, 16), limits.maxSpans(Form.BOX));
    }

    @Test
    void builderProducesAnIndependentImmutablePolicyInput() {
        FormLimits custom = FormLimits.DEFAULTS.toBuilder()
                .maxCellsPerPrint(2_048)
                .maxScannedCellsPerPlan(8_192)
                .maxChunksPerPrint(12)
                .maxSpans(Form.LINE, 96, 1, 1)
                .build();
        assertEquals(2_048, custom.maxCellsPerPrint());
        assertEquals(8_192, custom.maxScannedCellsPerPlan());
        assertEquals(96, custom.maxSpan(Form.LINE, 0));
        assertEquals(64, FormLimits.DEFAULTS.maxSpan(Form.LINE, 0));
        assertEquals(4_096, FormLimits.DEFAULTS.maxScannedCellsPerPlan());
    }

    @Test
    void invalidConfigurationFailsAtConstruction() {
        assertEquals("max cells per print must be positive", assertThrows(IllegalArgumentException.class,
                () -> FormLimits.builder().maxCellsPerPrint(0).build()).getMessage());
        assertEquals("max chunks per print must be positive", assertThrows(IllegalArgumentException.class,
                () -> FormLimits.builder().maxChunksPerPrint(-1).build()).getMessage());
        assertEquals("max scanned cells per plan must be positive", assertThrows(IllegalArgumentException.class,
                () -> FormLimits.builder().maxScannedCellsPerPlan(0).build()).getMessage());
        assertEquals("max scanned cells per plan cannot be below max cells per print",
                assertThrows(IllegalArgumentException.class,
                        () -> FormLimits.builder()
                                .maxCellsPerPrint(1_025)
                                .maxScannedCellsPerPlan(1_024)
                                .build()).getMessage());
        assertEquals("wall span limits must be positive", assertThrows(IllegalArgumentException.class,
                () -> FormLimits.builder().maxSpans(Form.WALL, 32, 0, 1).build()).getMessage());
    }
}
