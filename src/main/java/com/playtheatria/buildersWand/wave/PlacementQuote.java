package com.playtheatria.buildersWand.wave;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable resource quote for one frozen plan. A confirmation is valid only when a freshly
 * evaluated quote has the same binding as this one; this prevents a second click from silently
 * placing a different subset after inventory, Uses, wand state, or the world changes.
 */
public record PlacementQuote(Plan plan, List<PlannedCell> printable,
                             List<PlannedCell> admitted, int kept,
                             Map<Material, Integer> availableMaterials,
                             PlacementBudget.Result budget, int remainingUses, int maximumUses,
                             boolean creative, boolean usesBypass,
                             String wandId, int rotation, List<String> worldStates,
                             long issuedAtNanos) {

    public PlacementQuote {
        Objects.requireNonNull(plan, "plan");
        printable = List.copyOf(printable);
        admitted = List.copyOf(admitted);
        if (kept < 0) {
            throw new IllegalArgumentException("kept cannot be negative");
        }
        availableMaterials = Map.copyOf(new LinkedHashMap<>(availableMaterials));
        Objects.requireNonNull(budget, "budget");
        if (remainingUses < 0) {
            throw new IllegalArgumentException("remainingUses cannot be negative");
        }
        if (maximumUses < remainingUses) {
            throw new IllegalArgumentException("maximumUses cannot be below remainingUses");
        }
        Objects.requireNonNull(wandId, "wandId");
        worldStates = List.copyOf(worldStates);
        if (worldStates.size() != plan.targets().size()
                + plan.options().clearanceCells().size()
                + plan.options().validationCells().size()) {
            throw new IllegalArgumentException(
                    "world state count must match targets plus required clearance");
        }
        if (budget.decisions().size() != printable.size()) {
            throw new IllegalArgumentException("budget decisions must match printable cells");
        }
        if (admitted.size() != budget.affordableCells()) {
            throw new IllegalArgumentException("admitted cells must match the budget");
        }
    }

    public boolean partial() {
        return admitted.size() < printable.size();
    }

    public int remainingCells() {
        return printable.size() - admitted.size();
    }

    public long requiredTotalUses() {
        return Math.addExact(budget.requiredUses(), plan.options().activationUses());
    }

    public long admittedTotalUses() {
        return Math.addExact(budget.affordableUses(), plan.options().activationUses());
    }

    /** Exact confirmation binding, deliberately excluding only the monotonic issue time. */
    public boolean sameBinding(PlacementQuote other) {
        return other != null
                && plan.equals(other.plan)
                && printable.equals(other.printable)
                && admitted.equals(other.admitted)
                && kept == other.kept
                && availableMaterials.equals(other.availableMaterials)
                && budget.equals(other.budget)
                && remainingUses == other.remainingUses
                && maximumUses == other.maximumUses
                && creative == other.creative
                && usesBypass == other.usesBypass
                && wandId.equals(other.wandId)
                && rotation == other.rotation
                && worldStates.equals(other.worldStates);
    }

    /** Uses {@link System#nanoTime()} semantics so wall-clock changes cannot extend a quote. */
    public boolean expiredAt(long nowNanos, int confirmationSeconds) {
        if (confirmationSeconds <= 0) {
            throw new IllegalArgumentException("confirmationSeconds must be positive");
        }
        return nowNanos - issuedAtNanos >= confirmationSeconds * 1_000_000_000L;
    }
}
