package com.playtheatria.buildersWand.form;

import java.util.Objects;
import java.util.Optional;

/** The single allow/refuse result shared by live measurement, preview, and final preflight. */
public record PlanDecision(boolean allowed, PlanMetrics metrics, Optional<LimitViolation> violation) {
    public PlanDecision {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(violation, "violation");
        if (allowed == violation.isPresent()) {
            throw new IllegalArgumentException("allowed decisions have no violation; refusals require one");
        }
    }

    public static PlanDecision allowed(int expandedCells, int touchedChunks) {
        return new PlanDecision(true, new PlanMetrics(expandedCells, touchedChunks), Optional.empty());
    }

    public static PlanDecision refused(int expandedCells, int touchedChunks, LimitKind kind, String message) {
        return new PlanDecision(false, new PlanMetrics(expandedCells, touchedChunks),
                Optional.of(new LimitViolation(kind, message)));
    }

    public String message() {
        return violation.map(LimitViolation::message).orElse("allowed");
    }
}
