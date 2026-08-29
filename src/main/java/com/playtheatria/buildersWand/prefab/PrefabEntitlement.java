package com.playtheatria.buildersWand.prefab;

import java.util.Objects;
import java.util.UUID;

/** Durable evidence that one player owns one reusable prefab design. */
public record PrefabEntitlement(
        UUID playerId,
        String prefabId,
        long unlockedAtEpochMillis,
        String source
) {
    public PrefabEntitlement {
        Objects.requireNonNull(playerId, "playerId");
        prefabId = PrefabId.requireValid(prefabId);
        if (unlockedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Unlock timestamp cannot be negative");
        }
        source = Objects.requireNonNull(source, "source");
    }
}
