package com.playtheatria.buildersWand.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveRunnerCommitResultTest {

    @Test
    void sizeRefusalIsDistinguishedFromAWorldOrResourceBlock() {
        assertTrue(WaveRunner.CommitResult.adjustableRefusal("too large")
                .adjustableRefusal());
        assertFalse(WaveRunner.CommitResult.blocked("protected")
                .adjustableRefusal());
    }
}
