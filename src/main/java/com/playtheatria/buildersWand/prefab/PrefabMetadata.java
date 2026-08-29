package com.playtheatria.buildersWand.prefab;

import java.util.Objects;

/** Strict server-owned YAML sidecar fields for one prefab. */
public record PrefabMetadata(
        String id,
        String name,
        int version,
        String schematic,
        int sourceRotationDegrees,
        long activationUses,
        boolean allowRotation,
        PrefabClearance clearance
) {

    public PrefabMetadata {
        id = PrefabId.requireCatalogId(id);
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty() || name.length() > 80) {
            throw new IllegalArgumentException("Prefab name must contain 1-80 characters");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("Prefab version must be positive");
        }
        schematic = Objects.requireNonNull(schematic, "schematic").trim();
        if (schematic.isEmpty()
                || !schematic.endsWith(".schem")
                || schematic.contains("/")
                || schematic.contains("\\")
                || schematic.equals(".")
                || schematic.equals("..")) {
            throw new IllegalArgumentException("Schematic must be a local .schem filename");
        }
        if (sourceRotationDegrees != 0
                && sourceRotationDegrees != 90
                && sourceRotationDegrees != 180
                && sourceRotationDegrees != 270) {
            throw new IllegalArgumentException("Source rotation must be 0, 90, 180, or 270");
        }
        if (activationUses < 0) {
            throw new IllegalArgumentException("Activation Uses cannot be negative");
        }
        Objects.requireNonNull(clearance, "clearance");
    }

    public int sourceQuarterTurns() {
        return sourceRotationDegrees / 90;
    }
}
