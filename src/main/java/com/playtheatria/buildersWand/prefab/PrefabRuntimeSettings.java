package com.playtheatria.buildersWand.prefab;

import java.nio.file.Path;
import java.util.Objects;

/** Validated runtime settings for the server-owned prefab catalog. */
public record PrefabRuntimeSettings(
        boolean enabled,
        Path directory,
        int confirmationSeconds,
        long defaultActivationUses,
        int maxCells,
        int maxVolume,
        int maxAxisSpan,
        int maxChunks,
        int maxClearanceCells,
        boolean requireLogBlock
) {

    public PrefabRuntimeSettings {
        Objects.requireNonNull(directory, "directory");
        directory = directory.toAbsolutePath().normalize();
        if (confirmationSeconds <= 0) {
            throw new IllegalArgumentException("Prefab confirmation seconds must be positive");
        }
        if (defaultActivationUses < 0) {
            throw new IllegalArgumentException("Default prefab activation Uses cannot be negative");
        }
        if (maxCells <= 0 || maxVolume <= 0 || maxAxisSpan <= 0
                || maxChunks <= 0 || maxClearanceCells <= 0) {
            throw new IllegalArgumentException("Prefab limits must be positive");
        }
        if (maxCells > maxVolume) {
            throw new IllegalArgumentException("Prefab max volume cannot be smaller than max cells");
        }
        if (maxClearanceCells > maxVolume) {
            throw new IllegalArgumentException(
                    "Prefab max clearance cells cannot be larger than max volume"
            );
        }
    }
}
