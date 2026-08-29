package com.playtheatria.buildersWand.wave;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveRunnerCommitResultTest {

    @Test
    void liveAdjustmentsAreDistinguishedFromHardWorldBlocks() {
        assertTrue(WaveRunner.CommitResult.adjustableRefusal("too large")
                .adjustableRefusal());
        assertTrue(WaveRunner.CommitResult.adjustableRefusal("restock every required resource")
                .adjustableRefusal());
        assertFalse(WaveRunner.CommitResult.blocked("protected region")
                .adjustableRefusal());
    }

    @Test
    void commitStatusesHaveNoPartialStartOutcome() {
        assertEquals(EnumSet.of(WaveRunner.CommitStatus.STARTED,
                        WaveRunner.CommitStatus.QUOTED, WaveRunner.CommitStatus.BLOCKED),
                EnumSet.allOf(WaveRunner.CommitStatus.class));
        assertEquals(WaveRunner.CommitStatus.STARTED,
                WaveRunner.CommitResult.started().status());
    }
}
