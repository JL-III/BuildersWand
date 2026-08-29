package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanOrderingTest {

    @Test
    void scarceAdmissionStartsAtInteractionAnchorEvenAfterNegativeShift() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> null);
        BlockData data = blockData();
        PrintMaterial stone = PrintMaterial.block(Material.STONE);
        List<PlannedCell> rowMajorFromFarCorner = List.of(
                cell(world, -3, 0, 0, stone, data),
                cell(world, -2, 0, 0, stone, data),
                cell(world, -1, 0, 0, stone, data),
                cell(world, 0, 0, 0, stone, data));

        List<PlannedCell> ordered = PlanOrdering.anchorOut(
                rowMajorFromFarCorner, new BlockVector(0, 0, 0));

        assertEquals(List.of(0, -1, -2, -3), ordered.stream()
                .map(cell -> cell.location().getBlockX()).toList());
    }

    @Test
    void coordinateTieBreakersAreStable() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> null);
        BlockData data = blockData();
        PrintMaterial stone = PrintMaterial.block(Material.STONE);
        List<PlannedCell> ordered = PlanOrdering.anchorOut(List.of(
                cell(world, 1, 0, 0, stone, data),
                cell(world, 0, 1, 0, stone, data),
                cell(world, 0, 0, 1, stone, data)), new BlockVector());

        assertEquals(List.of("1,0,0", "0,0,1", "0,1,0"), ordered.stream()
                .map(cell -> cell.location().getBlockX() + ","
                        + cell.location().getBlockY() + ","
                        + cell.location().getBlockZ()).toList());
    }

    private static PlannedCell cell(World world, int x, int y, int z,
                                    PrintMaterial material, BlockData data) {
        return new PlannedCell(new Location(world, x, y, z), material, data);
    }

    private static BlockData blockData() {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> Material.STONE;
                    case "getAsString" -> "minecraft:stone";
                    case "clone" -> proxy;
                    default -> null;
                });
    }
}
