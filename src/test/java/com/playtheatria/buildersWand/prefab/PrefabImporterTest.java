package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabImporterTest {

    private static final PrefabImporter IMPORTER = new PrefabImporter(
            PrefabBlockPolicy.allowAll(),
            512,
            4_096
    );

    @Test
    void trimsExteriorAirPreservesInteriorClearanceAndInfersSouthFrontAnchor() throws Exception {
        PrefabDimensions sourceDimensions = new PrefabDimensions(5, 3, 5);
        int[] cells = filled(sourceDimensions, 0);
        set(cells, sourceDimensions, 1, 1, 1, 1);
        set(cells, sourceDimensions, 3, 1, 3, 1);
        set(cells, sourceDimensions, 2, 1, 2, 2);

        PrefabDefinition definition = IMPORTER.importPrefab(
                metadata(0, 20),
                new RawPrefabSchematic(
                        sourceDimensions,
                        List.of("minecraft:air", "minecraft:stone", "minecraft:structure_void"),
                        cells,
                        0,
                        0
                )
        );

        assertEquals(new PrefabDimensions(3, 1, 3), definition.dimensions());
        assertEquals(new PrefabPosition(1, 1, 3), definition.inferredSourceAnchor());
        assertEquals(PrefabCellKind.BLOCK,
                definition.cellAt(new PrefabPosition(0, 0, 2)).orElseThrow().kind());
        assertEquals(PrefabCellKind.BLOCK,
                definition.cellAt(new PrefabPosition(2, 0, 0)).orElseThrow().kind());
        assertEquals(PrefabCellKind.IGNORED,
                definition.cellAt(new PrefabPosition(1, 0, 1)).orElseThrow().kind());
        assertEquals(2, definition.placementCells().size());
        assertEquals(6, definition.clearanceCells().size());
    }

    @Test
    void appliesSourceRotationToCoordinatesAndBlockStatesBeforeAnchoring() throws Exception {
        PrefabDimensions dimensions = new PrefabDimensions(3, 1, 2);
        int[] cells = filled(dimensions, 0);
        set(cells, dimensions, 0, 0, 0, 1);
        set(cells, dimensions, 2, 0, 1, 2);

        PrefabDefinition definition = IMPORTER.importPrefab(
                metadata(90, 20),
                new RawPrefabSchematic(
                        dimensions,
                        List.of(
                                "minecraft:air",
                                "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                                "minecraft:stone"
                        ),
                        cells,
                        0,
                        0
                )
        );

        assertEquals(new PrefabDimensions(2, 1, 3), definition.dimensions());
        assertEquals(new PrefabPosition(0, 0, 2), definition.inferredSourceAnchor());
        assertEquals(
                "minecraft:stone",
                definition.cellAt(new PrefabPosition(0, 0, 0)).orElseThrow().blockState()
        );
        assertEquals(
                "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
                definition.cellAt(new PrefabPosition(1, 0, 2)).orElseThrow().blockState()
        );
    }

    @Test
    void usesStableBottomToTopAnchorOutPriorityAndPlacementTransform() throws Exception {
        PrefabDimensions dimensions = new PrefabDimensions(2, 2, 2);
        int[] cells = filled(dimensions, 1);
        PrefabDefinition definition = IMPORTER.importPrefab(
                metadata(0, 20),
                new RawPrefabSchematic(
                        dimensions,
                        List.of("minecraft:air", "minecraft:stone"),
                        cells,
                        0,
                        0
                )
        );

        assertEquals(new PrefabPosition(0, 0, 0), definition.placementCells().getFirst().position());
        assertTrue(definition.placementCells().get(3).position().y()
                <= definition.placementCells().get(4).position().y());

        TransformedPrefabCell localInward = definition.transformedCells(1).stream()
                .filter(cell -> cell.anchorOffset().equals(new PrefabPosition(1, 0, 0)))
                .findFirst()
                .orElseThrow();
        assertEquals(PrefabCellKind.BLOCK, localInward.kind());
    }

    @Test
    void stableHashIgnoresPaletteOrderingAndExteriorAirButTracksEconomics() throws Exception {
        PrefabDefinition first = IMPORTER.importPrefab(
                metadata(0, 20),
                new RawPrefabSchematic(
                        new PrefabDimensions(1, 1, 1),
                        List.of("minecraft:stone", "minecraft:air"),
                        new int[]{0},
                        0,
                        0
                )
        );
        PrefabDimensions padded = new PrefabDimensions(3, 1, 3);
        int[] paddedCells = filled(padded, 0);
        set(paddedCells, padded, 1, 0, 1, 1);
        PrefabDefinition equivalent = IMPORTER.importPrefab(
                metadata(0, 20),
                new RawPrefabSchematic(
                        padded,
                        List.of("minecraft:air", "minecraft:stone"),
                        paddedCells,
                        0,
                        0
                )
        );
        PrefabDefinition differentActivation = IMPORTER.importPrefab(
                metadata(0, 21),
                new RawPrefabSchematic(
                        new PrefabDimensions(1, 1, 1),
                        List.of("minecraft:stone"),
                        new int[]{0},
                        0,
                        0
                )
        );

        assertEquals(first.contentHash(), equivalent.contentHash());
        assertNotEquals(first.contentHash(), differentActivation.contentHash());
        assertTrue(first.contentHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsEntitiesUnsafeBlocksAndConfiguredLimits() {
        RawPrefabSchematic unsafe = new RawPrefabSchematic(
                new PrefabDimensions(1, 1, 1),
                List.of("minecraft:chest"),
                new int[]{0},
                1,
                1
        );
        PrefabImporter strict = new PrefabImporter(
                PrefabBlockPolicy.conservativeDefaults(),
                1,
                1
        );

        PrefabValidationException exception = assertThrows(
                PrefabValidationException.class,
                () -> strict.importPrefab(metadata(0, 20), unsafe)
        );
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.contains("block entities")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.contains("entities")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.contains("chest")));
    }

    @Test
    void rejectsConfiguredAxisAndClearanceLimitsAfterTrimming() {
        PrefabDimensions dimensions = new PrefabDimensions(4, 1, 1);
        int[] cells = {1, 0, 0, 1};
        PrefabImporter strict = new PrefabImporter(
                PrefabBlockPolicy.allowAll(),
                10,
                10,
                3,
                1
        );

        PrefabValidationException exception = assertThrows(
                PrefabValidationException.class,
                () -> strict.importPrefab(
                        metadata(0, 20),
                        new RawPrefabSchematic(
                                dimensions,
                                List.of("minecraft:air", "minecraft:stone"),
                                cells,
                                0,
                                0
                        )
                )
        );

        assertTrue(exception.issues().stream().anyMatch(issue -> issue.contains("axis-span")));
        assertTrue(exception.issues().stream().anyMatch(issue -> issue.contains("clearance cells")));
    }

    @Test
    void rejectsActivationPriceAboveTheConfiguredWandMaximum() {
        PrefabImporter strict = new PrefabImporter(
                PrefabBlockPolicy.allowAll(), 10, 10, 10, 10, 5_000);
        RawPrefabSchematic oneBlock = new RawPrefabSchematic(
                new PrefabDimensions(1, 1, 1), List.of("minecraft:stone"),
                new int[]{0}, 0, 0);

        PrefabValidationException exception = assertThrows(
                PrefabValidationException.class,
                () -> strict.importPrefab(metadata(0, 5_001), oneBlock));

        assertTrue(exception.issues().stream()
                .anyMatch(issue -> issue.contains("exceed the wand maximum 5000")));
    }

    private static PrefabMetadata metadata(int sourceRotation, long activationUses) {
        return new PrefabMetadata(
                "starter_house",
                "Starter House",
                1,
                "starter_house.schem",
                sourceRotation,
                activationUses,
                true,
                PrefabClearance.SCHEMATIC_AIR
        );
    }

    private static int[] filled(PrefabDimensions dimensions, int value) {
        int[] cells = new int[Math.toIntExact(dimensions.volume())];
        java.util.Arrays.fill(cells, value);
        return cells;
    }

    private static void set(
            int[] cells,
            PrefabDimensions dimensions,
            int x,
            int y,
            int z,
            int value
    ) {
        cells[x + z * dimensions.width() + y * dimensions.width() * dimensions.depth()] = value;
    }
}
