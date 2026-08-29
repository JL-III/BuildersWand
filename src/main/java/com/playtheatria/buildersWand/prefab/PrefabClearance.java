package com.playtheatria.buildersWand.prefab;

/** How authored air is interpreted by the placement preflight. */
public enum PrefabClearance {
    SCHEMATIC_AIR("schematic-air");

    private final String metadataValue;

    PrefabClearance(String metadataValue) {
        this.metadataValue = metadataValue;
    }

    public String metadataValue() {
        return metadataValue;
    }

    public static PrefabClearance parse(String value) {
        for (PrefabClearance mode : values()) {
            if (mode.metadataValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported clearance mode: " + value);
    }
}
