package com.playtheatria.buildersWand.prefab;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** One immutable generation of the live prefab catalog. */
public record PrefabCatalogSnapshot(
        long generation,
        Instant loadedAt,
        Map<String, PrefabDefinition> prefabs
) {
    public PrefabCatalogSnapshot {
        if (generation < 0) {
            throw new IllegalArgumentException("Catalog generation cannot be negative");
        }
        loadedAt = loadedAt == null ? Instant.EPOCH : loadedAt;
        prefabs = Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(prefabs)));
    }

    public Optional<PrefabDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(prefabs.get(id));
    }

    public static PrefabCatalogSnapshot empty() {
        return new PrefabCatalogSnapshot(0, Instant.EPOCH, Map.of());
    }
}
