package com.playtheatria.buildersWand.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveRunnerAccountingTest {

    @Test
    void prefabActivationIsReservedBeforePerCellAdmission() {
        assertEquals(75, WaveRunner.remainingUsesForCells(100, 25, false));
        assertEquals(0, WaveRunner.remainingUsesForCells(20, 25, false));
        assertEquals(0, WaveRunner.remainingUsesForCells(0, 25, false));
    }

    @Test
    void usesBypassDoesNotReduceTheDisplayedPdcBalance() {
        assertEquals(20, WaveRunner.remainingUsesForCells(20, 25, true));
        assertEquals(0, WaveRunner.remainingUsesForCells(0, 25, true));
    }

    @Test
    void rejectsImpossibleAccountingInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> WaveRunner.remainingUsesForCells(-1, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> WaveRunner.remainingUsesForCells(1, -1, false));
    }
}
