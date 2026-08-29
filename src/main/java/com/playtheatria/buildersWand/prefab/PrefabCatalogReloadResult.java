package com.playtheatria.buildersWand.prefab;

import java.util.List;

/** Result of an all-or-nothing catalog reload. */
public record PrefabCatalogReloadResult(
        boolean success,
        long previousGeneration,
        PrefabCatalogSnapshot liveSnapshot,
        List<String> issues
) {
    public PrefabCatalogReloadResult {
        issues = List.copyOf(issues);
        if (success && !issues.isEmpty()) {
            throw new IllegalArgumentException("A successful reload cannot contain issues");
        }
    }
}
