package com.playtheatria.buildersWand.wand;

import java.util.Optional;

/** Pure normalization and spending rules for the wand's PDC-backed uses. */
public final class UseCounter {

    private UseCounter() {
    }

    public record State(int remaining, int maximum) {
        public State {
            if (maximum < 1 || remaining < 0 || remaining > maximum) {
                throw new IllegalArgumentException("Invalid wand uses " + remaining + "/" + maximum);
            }
        }

        public boolean depleted() {
            return remaining == 0;
        }
    }

    /** Missing legacy values become a full wand; malformed values are clamped safely. */
    public static State normalize(Integer storedRemaining, Integer storedMaximum, int configuredMaximum) {
        if (configuredMaximum < 1) {
            throw new IllegalArgumentException("configuredMaximum must be positive");
        }
        int maximum = storedMaximum != null && storedMaximum > 0
                ? storedMaximum
                : configuredMaximum;
        int remaining = storedRemaining == null
                ? maximum
                : Math.max(0, Math.min(storedRemaining, maximum));
        return new State(remaining, maximum);
    }

    public static State spendOne(State state) {
        return spend(state, 1).orElse(state);
    }

    /** Atomically spend an exact positive amount, or return empty without changing the state. */
    public static Optional<State> spend(State state, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (state.remaining() < amount) {
            return Optional.empty();
        }
        return Optional.of(new State(state.remaining() - amount, state.maximum()));
    }

    /** Replace remaining Uses while preserving the wand's maximum, or reject an invalid value. */
    public static Optional<State> setRemaining(State state, int remaining) {
        if (remaining < 0 || remaining > state.maximum()) {
            return Optional.empty();
        }
        return Optional.of(new State(remaining, state.maximum()));
    }
}
