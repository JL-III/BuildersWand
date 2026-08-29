package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrefabDefinitionTransformTest {

    private static final String HASH = "b".repeat(64);

    @Test
    void rotatesOffsetsAndDirectionalStateThroughEveryQuarterTurn() {
        PrefabDefinition definition = definition(true);

        assertTransform(definition, 0,
                new PrefabPosition(1, 0, 0), new PrefabPosition(0, 0, -1), "north");
        assertTransform(definition, 1,
                new PrefabPosition(0, 0, 1), new PrefabPosition(1, 0, 0), "east");
        assertTransform(definition, 2,
                new PrefabPosition(-1, 0, 0), new PrefabPosition(0, 0, 1), "south");
        assertTransform(definition, 3,
                new PrefabPosition(0, 0, -1), new PrefabPosition(-1, 0, 0), "west");

        assertEquals(definition.transformedCells(3), definition.transformedCells(-1));
        assertEquals(definition.transformedCells(1), definition.transformedCells(5));
    }

    @Test
    void keepsStableAnchorOutPlacementOrderAfterRotation() {
        PrefabDefinition definition = definition(true);

        List<PrefabPosition> first = definition.transformedPlacementCells(1).stream()
                .map(TransformedPrefabCell::anchorOffset)
                .toList();
        List<PrefabPosition> second = definition.transformedPlacementCells(1).stream()
                .map(TransformedPrefabCell::anchorOffset)
                .toList();

        assertEquals(List.of(
                new PrefabPosition(0, 0, 0),
                new PrefabPosition(0, 0, 1)
        ), first);
        assertEquals(first, second);
    }

    @Test
    void fixedRotationMetadataRejectsAnyNormalizedNonZeroTurn() {
        PrefabDefinition definition = definition(false);

        assertEquals(definition.transformedCells(0), definition.transformedCells(4));
        assertThrows(IllegalArgumentException.class, () -> definition.transformedCells(1));
        assertThrows(IllegalArgumentException.class, () -> definition.transformedCells(-1));
    }

    private static void assertTransform(PrefabDefinition definition, int turns,
                                        PrefabPosition stoneOffset,
                                        PrefabPosition clearanceOffset,
                                        String expectedFacing) {
        List<TransformedPrefabCell> cells = definition.transformedCells(turns);
        TransformedPrefabCell stairs = cells.stream()
                .filter(cell -> cell.kind() == PrefabCellKind.BLOCK
                        && cell.blockState().startsWith("minecraft:oak_stairs"))
                .findFirst().orElseThrow();
        TransformedPrefabCell stone = cells.stream()
                .filter(cell -> "minecraft:stone".equals(cell.blockState()))
                .findFirst().orElseThrow();
        TransformedPrefabCell clearance = cells.stream()
                .filter(cell -> cell.kind() == PrefabCellKind.CLEARANCE)
                .findFirst().orElseThrow();

        assertEquals(new PrefabPosition(0, 0, 0), stairs.anchorOffset());
        assertEquals("minecraft:oak_stairs[facing=" + expectedFacing + "]", stairs.blockState());
        assertEquals(stoneOffset, stone.anchorOffset());
        assertEquals(clearanceOffset, clearance.anchorOffset());
    }

    private static PrefabDefinition definition(boolean allowRotation) {
        PrefabMetadata metadata = new PrefabMetadata(
                "starter_house", "Starter House", 1, "starter_house.schem",
                0, 20, allowRotation, PrefabClearance.SCHEMATIC_AIR);
        return new PrefabDefinition(
                metadata,
                new PrefabDimensions(2, 1, 2),
                new PrefabPosition(0, 0, 0),
                List.of(
                        PrefabCell.block(new PrefabPosition(0, 0, 0),
                                "minecraft:oak_stairs[facing=north]"),
                        PrefabCell.block(new PrefabPosition(1, 0, 0), "minecraft:stone"),
                        PrefabCell.clearance(new PrefabPosition(0, 0, 1)),
                        PrefabCell.ignored(new PrefabPosition(1, 0, 1))
                ),
                HASH
        );
    }
}
