package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.form.Form;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.Objects;

/**
 * Placement semantics carried by an immutable plan. Ordinary palette forms use the compact
 * default; reusable prefabs opt into authored materials, exact-state conflicts, clearance, and
 * an explicit confirmation/activation policy without creating a second block-placement engine.
 */
public record PlanOptions(Kind kind, String label, String contentBinding,
                          boolean livePaletteRequired, boolean strictExistingStates,
                          boolean alwaysConfirm, int activationUses,
                          List<PlanValidationCell> validationCells,
                          List<BlockVector> clearanceCells,
                          boolean requirePlacementLogging,
                          boolean refuseLivingEntitiesAtStart,
                          int confirmationSeconds) {

    public enum Kind {
        ORDINARY,
        PREFAB
    }

    public PlanOptions {
        Objects.requireNonNull(kind, "kind");
        label = Objects.requireNonNull(label, "label").strip();
        contentBinding = Objects.requireNonNull(contentBinding, "contentBinding").strip();
        if (label.isEmpty() || contentBinding.isEmpty()) {
            throw new IllegalArgumentException("plan label and binding cannot be blank");
        }
        if (activationUses < 0) {
            throw new IllegalArgumentException("activation Uses cannot be negative");
        }
        validationCells = List.copyOf(validationCells);
        clearanceCells = List.copyOf(clearanceCells);
        if (validationCells.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("validation cells cannot contain null");
        }
        if (clearanceCells.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("clearance cells cannot contain null");
        }
        if (alwaysConfirm && confirmationSeconds <= 0) {
            throw new IllegalArgumentException("confirmed plans need a positive expiry");
        }
    }

    public static PlanOptions ordinary(Form form) {
        return new PlanOptions(Kind.ORDINARY, form.label(), "ordinary:" + form.key(),
                true, false, false, 0, List.of(), List.of(), false, false, 0);
    }

    public static PlanOptions ordinary(Form form, List<PlanValidationCell> validationCells) {
        return new PlanOptions(Kind.ORDINARY, form.label(), "ordinary:" + form.key(),
                true, false, false, 0, validationCells, List.of(), false, false, 0);
    }

    public static PlanOptions prefab(String label, String contentBinding, int activationUses,
                                     List<BlockVector> clearanceCells,
                                     boolean requirePlacementLogging, int confirmationSeconds) {
        return new PlanOptions(Kind.PREFAB, label, contentBinding,
                false, true, true, activationUses, List.of(), clearanceCells,
                requirePlacementLogging, true, confirmationSeconds);
    }

    public boolean prefab() {
        return kind == Kind.PREFAB;
    }
}
