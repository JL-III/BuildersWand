package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabCatalogTest {

    @TempDir
    Path directory;

    @Test
    void swapsCompleteValidGenerationAndPreservesItOnAnyLaterFailure() throws Exception {
        writeSidecar("house.yml", "house", "house.schem");
        Files.writeString(directory.resolve("house.schem"), "parser seam fixture");
        RawPrefabSchematic oneStone = new RawPrefabSchematic(
                new PrefabDimensions(1, 1, 1),
                java.util.List.of("minecraft:stone"),
                new int[]{0},
                0,
                0
        );
        PrefabCatalog catalog = new PrefabCatalog(
                ignored -> oneStone,
                new PrefabImporter(PrefabBlockPolicy.allowAll(), 10, 10),
                20
        );

        PrefabCatalogReloadResult initial = catalog.reload(directory);
        assertTrue(initial.success());
        assertEquals(1, initial.liveSnapshot().generation());
        assertTrue(initial.liveSnapshot().find("house").isPresent());
        PrefabCatalogSnapshot firstGeneration = catalog.snapshot();

        writeSidecar("broken.yml", "broken", "missing.schem");
        PrefabCatalogReloadResult failed = catalog.reload(directory);

        assertFalse(failed.success());
        assertSame(firstGeneration, catalog.snapshot());
        assertEquals(1, failed.liveSnapshot().generation());
        assertTrue(failed.issues().stream().anyMatch(issue -> issue.contains("missing.schem")));

        Files.delete(directory.resolve("broken.yml"));
        writeSidecar("tower.yml", "tower", "tower.schem");
        Files.writeString(directory.resolve("tower.schem"), "parser seam fixture");
        PrefabCatalogReloadResult repaired = catalog.reload(directory);

        assertTrue(repaired.success());
        assertEquals(2, catalog.snapshot().generation());
        assertEquals(java.util.Set.of("house", "tower"), catalog.snapshot().prefabs().keySet());
    }

    @Test
    void duplicateIdsRejectWholeCandidate() throws Exception {
        writeSidecar("first.yml", "same", "first.schem");
        writeSidecar("second.yml", "same", "second.schem");
        Files.writeString(directory.resolve("first.schem"), "first");
        Files.writeString(directory.resolve("second.schem"), "second");
        RawPrefabSchematic oneStone = new RawPrefabSchematic(
                new PrefabDimensions(1, 1, 1),
                java.util.List.of("minecraft:stone"),
                new int[]{0},
                0,
                0
        );
        PrefabCatalog catalog = new PrefabCatalog(
                ignored -> oneStone,
                new PrefabImporter(PrefabBlockPolicy.allowAll(), 10, 10),
                20
        );

        PrefabCatalogReloadResult result = catalog.reload(directory);

        assertFalse(result.success());
        assertEquals(0, catalog.snapshot().generation());
        assertTrue(catalog.snapshot().prefabs().isEmpty());
    }

    private void writeSidecar(String filename, String id, String schematic) throws Exception {
        Files.writeString(directory.resolve(filename), """
                id: %s
                name: %s
                version: 1
                schematic: %s
                source-rotation: 0
                activation-uses: 20
                allow-rotation: true
                clearance: schematic-air
                """.formatted(id, id, schematic));
    }
}
