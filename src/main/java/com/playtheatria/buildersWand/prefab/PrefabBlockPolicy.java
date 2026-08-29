package com.playtheatria.buildersWand.prefab;

import java.util.Optional;

/**
 * Catalog-load hook that decides whether one authored block has safe one-item/one-cell semantics.
 */
@FunctionalInterface
public interface PrefabBlockPolicy {

    /** Empty accepts the block; a message rejects it and is surfaced to administrators. */
    Optional<String> rejectionReason(String canonicalBlockState);

    static PrefabBlockPolicy allowAll() {
        return ignored -> Optional.empty();
    }

    static PrefabBlockPolicy conservativeDefaults() {
        return new ConservativePrefabBlockPolicy();
    }
}
