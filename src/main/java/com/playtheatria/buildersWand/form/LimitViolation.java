package com.playtheatria.buildersWand.form;

import java.util.Objects;

/** A named reason suitable for diagnostics and player-facing refusal copy. */
public record LimitViolation(LimitKind kind, String message) {
    public LimitViolation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("violation message must not be blank");
        }
    }
}
