package com.playtheatria.buildersWand.wave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredPlacementQueueTest {

    @Test
    void deferredCellsRetryAfterEveryPrimaryCell() {
        DeferredPlacementQueue<String> queue = new DeferredPlacementQueue<>(List.of("a", "b", "c"));

        assertEquals("a", queue.current());
        assertEquals(DeferredPlacementQueue.ObstructionResult.DEFERRED, queue.obstructCurrent());
        assertEquals("b", queue.current());
        queue.completeCurrent();
        assertEquals("c", queue.current());
        queue.completeCurrent();

        assertEquals("a", queue.current());
        assertTrue(queue.currentIsRetry());
        queue.completeCurrent();

        assertTrue(queue.isComplete());
        assertEquals(3, queue.resolved());
        assertEquals(0, queue.skipped());
    }

    @Test
    void aSecondObstructionSkipsWithoutAnotherRetry() {
        DeferredPlacementQueue<String> queue = new DeferredPlacementQueue<>(List.of("blocked", "clear"));

        queue.obstructCurrent();
        queue.completeCurrent();
        assertEquals("blocked", queue.current());
        assertEquals(DeferredPlacementQueue.ObstructionResult.SKIPPED, queue.obstructCurrent());

        assertTrue(queue.isComplete());
        assertEquals(2, queue.resolved());
        assertEquals(1, queue.skipped());
        assertEquals(0, queue.deferred());
    }

    @Test
    void multipleDeferredCellsKeepTheirOriginalOrder() {
        DeferredPlacementQueue<Integer> queue = new DeferredPlacementQueue<>(List.of(1, 2, 3));

        queue.obstructCurrent();
        queue.obstructCurrent();
        queue.completeCurrent();

        assertEquals(1, queue.current());
        queue.completeCurrent();
        assertEquals(2, queue.current());
        queue.completeCurrent();

        assertTrue(queue.isComplete());
        assertEquals(3, queue.resolved());
        assertEquals(0, queue.skipped());
    }

    @Test
    void deferredCellsRemainUnresolvedUntilTheirRetryFinishes() {
        DeferredPlacementQueue<String> queue = new DeferredPlacementQueue<>(List.of("a", "b"));

        assertFalse(queue.currentIsRetry());
        queue.obstructCurrent();
        assertEquals(0, queue.resolved());
        assertEquals(1, queue.deferred());
        queue.completeCurrent();
        assertEquals(1, queue.resolved());
        assertEquals(2, queue.total());
        assertFalse(queue.isComplete());
    }

    @Test
    void completedQueueCannotProduceOrResolveAnotherCell() {
        DeferredPlacementQueue<String> queue = new DeferredPlacementQueue<>(List.of("only"));
        queue.completeCurrent();

        assertThrows(NoSuchElementException.class, queue::current);
        assertThrows(NoSuchElementException.class, queue::currentIsRetry);
        assertThrows(NoSuchElementException.class, queue::completeCurrent);
        assertThrows(NoSuchElementException.class, queue::obstructCurrent);
    }

    @Test
    void equalValuesHaveIndependentRetryAllowances() {
        DeferredPlacementQueue<String> queue = new DeferredPlacementQueue<>(List.of("same", "same"));

        assertEquals(DeferredPlacementQueue.ObstructionResult.DEFERRED, queue.obstructCurrent());
        assertEquals(DeferredPlacementQueue.ObstructionResult.DEFERRED, queue.obstructCurrent());
        assertEquals("same", queue.current());
        assertEquals(DeferredPlacementQueue.ObstructionResult.SKIPPED, queue.obstructCurrent());
        assertEquals("same", queue.current());
        assertEquals(DeferredPlacementQueue.ObstructionResult.SKIPPED, queue.obstructCurrent());

        assertTrue(queue.isComplete());
        assertEquals(2, queue.skipped());
        assertEquals(0, queue.successful());
    }

    @Test
    void mixedTransitionsPreserveTheCellAccountingInvariant() {
        DeferredPlacementQueue<String> queue = new DeferredPlacementQueue<>(List.of("a", "b", "c", "d"));

        assertInvariant(queue);
        queue.completeCurrent();
        assertInvariant(queue);
        queue.obstructCurrent();
        assertInvariant(queue);
        queue.completeCurrent();
        assertInvariant(queue);
        queue.obstructCurrent();
        assertInvariant(queue);
        queue.completeCurrent();
        assertInvariant(queue);
        queue.obstructCurrent();
        assertInvariant(queue);

        assertTrue(queue.isComplete());
        assertEquals(3, queue.successful());
        assertEquals(1, queue.skipped());
    }

    @Test
    void allBlockedCellsTakeOneRetryThenFinishAsSkipped() {
        DeferredPlacementQueue<Integer> queue = new DeferredPlacementQueue<>(List.of(1, 2, 3));

        queue.obstructCurrent();
        queue.obstructCurrent();
        queue.obstructCurrent();
        assertEquals(3, queue.deferred());
        queue.obstructCurrent();
        queue.obstructCurrent();
        queue.obstructCurrent();

        assertTrue(queue.isComplete());
        assertEquals(3, queue.resolved());
        assertEquals(3, queue.skipped());
        assertEquals(0, queue.successful());
    }

    private static void assertInvariant(DeferredPlacementQueue<?> queue) {
        assertEquals(queue.total(), queue.resolved() + queue.primaryRemaining() + queue.deferred());
        assertEquals(queue.resolved(), queue.successful() + queue.skipped());
    }
}
