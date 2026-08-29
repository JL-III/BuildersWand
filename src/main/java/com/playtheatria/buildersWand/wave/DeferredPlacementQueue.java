package com.playtheatria.buildersWand.wave;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Runs every primary cell once, then runs each deferred cell exactly once more in original order.
 * A second obstruction permanently resolves that cell as skipped, so retries can never loop.
 */
final class DeferredPlacementQueue<T> {

    enum ObstructionResult {
        DEFERRED,
        SKIPPED
    }

    private final List<T> primary;
    private final Deque<T> deferred = new ArrayDeque<>();
    private int primaryIndex;
    private int resolved;
    private int skipped;

    DeferredPlacementQueue(List<T> primary) {
        this.primary = List.copyOf(primary);
    }

    T current() {
        if (primaryIndex < primary.size()) {
            return primary.get(primaryIndex);
        }
        T current = deferred.peekFirst();
        if (current == null) {
            throw new NoSuchElementException("placement queue is complete");
        }
        return current;
    }

    boolean currentIsRetry() {
        requirePending();
        return primaryIndex >= primary.size();
    }

    void completeCurrent() {
        requirePending();
        if (primaryIndex < primary.size()) {
            primaryIndex++;
        } else {
            deferred.removeFirst();
        }
        resolved++;
    }

    ObstructionResult obstructCurrent() {
        requirePending();
        if (primaryIndex < primary.size()) {
            deferred.addLast(primary.get(primaryIndex));
            primaryIndex++;
            return ObstructionResult.DEFERRED;
        }
        deferred.removeFirst();
        resolved++;
        skipped++;
        return ObstructionResult.SKIPPED;
    }

    boolean isComplete() {
        return primaryIndex >= primary.size() && deferred.isEmpty();
    }

    int total() {
        return primary.size();
    }

    int resolved() {
        return resolved;
    }

    int skipped() {
        return skipped;
    }

    int successful() {
        return resolved - skipped;
    }

    int deferred() {
        return deferred.size();
    }

    int primaryRemaining() {
        return primary.size() - primaryIndex;
    }

    private void requirePending() {
        if (isComplete()) {
            throw new NoSuchElementException("placement queue is complete");
        }
    }
}
