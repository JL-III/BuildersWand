package com.playtheatria.buildersWand.wave;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRulesTest {

    @Test
    void exactPostconditionIncludesBlockPropertiesNotOnlyMaterial() {
        BlockData requested = blockData("minecraft:oak_stairs[facing=north,half=bottom]");
        Block exact = block(blockData("minecraft:oak_stairs[facing=north,half=bottom]"));
        Block physicsChanged = block(blockData(
                "minecraft:oak_stairs[facing=east,half=bottom]"));

        assertTrue(PlacementRules.isAlreadyBuilt(exact, requested));
        assertFalse(PlacementRules.isAlreadyBuilt(physicsChanged, requested));
    }

    private static Block block(BlockData current) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
                new Class<?>[]{Block.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getBlockData" -> current;
                    case "isReplaceable" -> true;
                    default -> null;
                });
    }

    private static BlockData blockData(String state) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAsString" -> state;
                    case "clone" -> proxy;
                    default -> null;
                });
    }
}
