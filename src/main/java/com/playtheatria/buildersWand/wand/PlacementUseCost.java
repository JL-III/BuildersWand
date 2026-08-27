package com.playtheatria.buildersWand.wand;

import java.util.Objects;

/** Shared placement-use pricing and affordability math for previews and committed waves. */
public final class PlacementUseCost {

    private PlacementUseCost() {
    }

    public static int perCell(PrintMaterial material, int waterUsesPerSource) {
        Objects.requireNonNull(material, "material");
        if (waterUsesPerSource < 1) {
            throw new IllegalArgumentException("waterUsesPerSource must be positive");
        }
        return material.isWater() ? waterUsesPerSource : 1;
    }

    public static int affordableCells(int remainingUses, int usesPerCell) {
        if (remainingUses < 0) {
            throw new IllegalArgumentException("remainingUses cannot be negative");
        }
        if (usesPerCell < 1) {
            throw new IllegalArgumentException("usesPerCell must be positive");
        }
        return remainingUses / usesPerCell;
    }

    public static long totalUses(int cells, int usesPerCell) {
        if (cells < 0) {
            throw new IllegalArgumentException("cells cannot be negative");
        }
        if (usesPerCell < 1) {
            throw new IllegalArgumentException("usesPerCell must be positive");
        }
        return (long) cells * usesPerCell;
    }
}
