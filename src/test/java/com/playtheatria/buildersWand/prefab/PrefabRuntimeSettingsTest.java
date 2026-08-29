package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrefabRuntimeSettingsTest {

    @Test
    void normalizesDirectoryAndRetainsEconomicPolicy() {
        PrefabRuntimeSettings settings = new PrefabRuntimeSettings(
                true,
                Path.of("build", "..", "prefabs"),
                30,
                20,
                512,
                8192,
                32,
                8,
                8192,
                true
        );

        assertEquals(Path.of("prefabs").toAbsolutePath().normalize(), settings.directory());
        assertEquals(20, settings.defaultActivationUses());
    }

    @Test
    void rejectsNegativeEconomicsAndInconsistentLimits() {
        assertThrows(IllegalArgumentException.class, () -> new PrefabRuntimeSettings(
                true, Path.of("prefabs"), 30, -1, 512, 8192, 32, 8, 8192, true
        ));
        assertThrows(IllegalArgumentException.class, () -> new PrefabRuntimeSettings(
                true, Path.of("prefabs"), 30, 20, 513, 512, 32, 8, 512, true
        ));
        assertThrows(IllegalArgumentException.class, () -> new PrefabRuntimeSettings(
                true, Path.of("prefabs"), 30, 20, 512, 8192, 32, 8, 8193, true
        ));
    }
}
