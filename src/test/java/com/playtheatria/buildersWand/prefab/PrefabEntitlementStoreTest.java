package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabEntitlementStoreTest {

    private static final Logger LOGGER = Logger.getLogger(
            PrefabEntitlementStoreTest.class.getName()
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void grantsIdempotentlyPersistsSourceAndSurvivesReopen() {
        Path database = temporaryDirectory.resolve("entitlements.sqlite");
        UUID player = UUID.randomUUID();

        try (PrefabEntitlementStore first = PrefabEntitlementStore.open(
                database,
                LOGGER,
                () -> 123_456L
        )) {
            assertTrue(first.isAvailable());
            assertEquals(
                    PrefabEntitlementStore.GrantResult.GRANTED,
                    first.grant(player, "starter_house", "essentials-kit-sign")
            );
            assertEquals(
                    PrefabEntitlementStore.GrantResult.ALREADY_UNLOCKED,
                    first.grant(player, "starter_house", "duplicate")
            );
            assertEquals(PrefabEntitlementStore.Access.UNLOCKED,
                    first.access(player, "starter_house"));
            PrefabEntitlement entitlement = first.find(player, "starter_house").orElseThrow();
            assertEquals(123_456L, entitlement.unlockedAtEpochMillis());
            assertEquals("essentials-kit-sign", entitlement.source());
        }

        try (PrefabEntitlementStore reopened = PrefabEntitlementStore.open(database, LOGGER)) {
            assertEquals(PrefabEntitlementStore.Access.UNLOCKED,
                    reopened.access(player, "starter_house"));
            assertEquals(Set.of("starter_house"), reopened.unlockedPrefabIds(player).orElseThrow());
        }
    }

    @Test
    void tracksMultiplePlayersAndRevokesOnlyExactCompositeKey() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (PrefabEntitlementStore store = PrefabEntitlementStore.open(
                temporaryDirectory.resolve("entitlements.sqlite"),
                LOGGER
        )) {
            store.grant(first, "house", "admin");
            store.grant(first, "tower", "admin");
            store.grant(second, "house", "admin");

            assertEquals(
                    PrefabEntitlementStore.RevokeResult.REVOKED,
                    store.revoke(first, "house")
            );
            assertEquals(PrefabEntitlementStore.Access.LOCKED, store.access(first, "house"));
            assertEquals(PrefabEntitlementStore.Access.UNLOCKED, store.access(first, "tower"));
            assertEquals(PrefabEntitlementStore.Access.UNLOCKED, store.access(second, "house"));
            assertEquals(
                    PrefabEntitlementStore.RevokeResult.NOT_UNLOCKED,
                    store.revoke(first, "house")
            );
        }
    }

    @Test
    void failsClosedAfterClose() {
        PrefabEntitlementStore store = PrefabEntitlementStore.open(
                temporaryDirectory.resolve("entitlements.sqlite"),
                LOGGER
        );
        UUID player = UUID.randomUUID();
        store.close();

        assertFalse(store.isAvailable());
        assertEquals(PrefabEntitlementStore.Access.UNAVAILABLE, store.access(player, "house"));
        assertEquals(
                PrefabEntitlementStore.GrantResult.UNAVAILABLE,
                store.grant(player, "house", "admin")
        );
        assertTrue(store.unlockedPrefabIds(player).isEmpty());
    }
}
