package com.playtheatria.buildersWand.wand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UseCounterTest {

    @Test
    void legacyWandStartsFullAtConfiguredMaximum() {
        assertEquals(new UseCounter.State(5_000, 5_000),
                UseCounter.normalize(null, null, 5_000));
    }

    @Test
    void storedMaximumSurvivesLaterConfigChanges() {
        assertEquals(new UseCounter.State(2_500, 5_000),
                UseCounter.normalize(2_500, 5_000, 8_000));
    }

    @Test
    void malformedStoredUsesAreClampedWithoutCreatingExtraCapacity() {
        assertEquals(new UseCounter.State(5_000, 5_000),
                UseCounter.normalize(9_999, 5_000, 5_000));
        assertEquals(new UseCounter.State(0, 5_000),
                UseCounter.normalize(-12, 5_000, 5_000));
    }

    @Test
    void onePrintedCellSpendsExactlyOneUse() {
        assertEquals(new UseCounter.State(4_999, 5_000),
                UseCounter.spendOne(new UseCounter.State(5_000, 5_000)));
    }

    @Test
    void waterCostSpendsThreeUsesAtomically() {
        assertEquals(new UseCounter.State(2, 5_000),
                UseCounter.spend(new UseCounter.State(5, 5_000), 3).orElseThrow());
        assertTrue(UseCounter.spend(new UseCounter.State(2, 5_000), 3).isEmpty());
    }

    @Test
    void depletedWandRemainsAtZeroForRefilling() {
        UseCounter.State depleted = new UseCounter.State(0, 5_000);

        assertSame(depleted, UseCounter.spendOne(depleted));
        assertTrue(depleted.depleted());
    }

    @Test
    void administratorCanSetAnyRemainingValueWithinTheExistingMaximum() {
        UseCounter.State current = new UseCounter.State(1_250, 5_000);

        assertEquals(new UseCounter.State(0, 5_000),
                UseCounter.setRemaining(current, 0).orElseThrow());
        assertEquals(new UseCounter.State(2_750, 5_000),
                UseCounter.setRemaining(current, 2_750).orElseThrow());
        assertEquals(new UseCounter.State(5_000, 5_000),
                UseCounter.setRemaining(current, 5_000).orElseThrow());
        assertTrue(UseCounter.setRemaining(current, -1).isEmpty());
        assertTrue(UseCounter.setRemaining(current, 5_001).isEmpty());
    }
}
