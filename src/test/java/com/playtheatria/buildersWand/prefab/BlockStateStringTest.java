package com.playtheatria.buildersWand.prefab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockStateStringTest {

    @Test
    void canonicalizesPropertiesAndRotatesDirectionalState() {
        BlockStateString state = BlockStateString.parse(
                "oak_stairs[waterlogged=false,facing=north,half=bottom]"
        );

        assertEquals(
                "minecraft:oak_stairs[facing=north,half=bottom,waterlogged=false]",
                state.canonical()
        );
        assertEquals(
                "minecraft:oak_stairs[facing=east,half=bottom,waterlogged=false]",
                state.rotateClockwise(1).canonical()
        );
    }

    @Test
    void rotatesDirectionalPropertyKeysAxesAndSixteenthRotations() {
        assertEquals(
                "minecraft:oak_fence[east=true,north=false]",
                BlockStateString.parse("oak_fence[north=true,west=false]")
                        .rotateClockwise(1)
                        .canonical()
        );
        assertEquals(
                "minecraft:oak_log[axis=z]",
                BlockStateString.parse("oak_log[axis=x]").rotateClockwise(1).canonical()
        );
        assertEquals(
                "minecraft:oak_sign[rotation=1]",
                BlockStateString.parse("oak_sign[rotation=13]").rotateClockwise(1).canonical()
        );
        assertEquals(
                "minecraft:rail[shape=north_south]",
                BlockStateString.parse("rail[shape=north_south]").rotateClockwise(2).canonical()
        );
    }

    @Test
    void rejectsMalformedAndDuplicateProperties() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateString.parse("stone[broken]"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockStateString.parse("stone[a=one,a=two]")
        );
    }
}
