package com.playtheatria.buildersWand.prefab;

import java.util.List;
import java.util.Objects;

/** Parsed schematic data before source rotation, trimming, safety validation, and anchoring. */
public final class RawPrefabSchematic {

    private final PrefabDimensions dimensions;
    private final List<String> palette;
    private final int[] paletteIndices;
    private final int blockEntityCount;
    private final int entityCount;

    public RawPrefabSchematic(
            PrefabDimensions dimensions,
            List<String> palette,
            int[] paletteIndices,
            int blockEntityCount,
            int entityCount
    ) {
        this.dimensions = Objects.requireNonNull(dimensions, "dimensions");
        this.palette = List.copyOf(palette);
        this.paletteIndices = paletteIndices.clone();
        if (this.palette.isEmpty()) {
            throw new IllegalArgumentException("Schematic palette cannot be empty");
        }
        if (dimensions.volume() != this.paletteIndices.length) {
            throw new IllegalArgumentException("Schematic data length does not match its dimensions");
        }
        for (int index : this.paletteIndices) {
            if (index < 0 || index >= this.palette.size()) {
                throw new IllegalArgumentException("Schematic contains an invalid palette index: " + index);
            }
        }
        if (blockEntityCount < 0 || entityCount < 0) {
            throw new IllegalArgumentException("Entity counts cannot be negative");
        }
        this.blockEntityCount = blockEntityCount;
        this.entityCount = entityCount;
    }

    public PrefabDimensions dimensions() {
        return dimensions;
    }

    public List<String> palette() {
        return palette;
    }

    public int[] paletteIndices() {
        return paletteIndices.clone();
    }

    public int blockEntityCount() {
        return blockEntityCount;
    }

    public int entityCount() {
        return entityCount;
    }

    /** Sponge linearization: X changes fastest, then Z, then Y. */
    public String blockStateAt(int x, int y, int z) {
        if (x < 0 || x >= dimensions.width()
                || y < 0 || y >= dimensions.height()
                || z < 0 || z >= dimensions.depth()) {
            throw new IndexOutOfBoundsException("Schematic coordinate is outside its dimensions");
        }
        int linear = x
                + z * dimensions.width()
                + y * dimensions.width() * dimensions.depth();
        return palette.get(paletteIndices[linear]);
    }
}
