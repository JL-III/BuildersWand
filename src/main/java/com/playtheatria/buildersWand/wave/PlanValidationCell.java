package com.playtheatria.buildersWand.wave;

import org.bukkit.util.BlockVector;

import java.util.Objects;

/** A world state that defines a plan but is not itself placed or cleared. */
public record PlanValidationCell(BlockVector location, String expectedBlockData) {
    public PlanValidationCell {
        Objects.requireNonNull(location, "location");
        expectedBlockData = Objects.requireNonNull(expectedBlockData, "expectedBlockData");
    }
}
