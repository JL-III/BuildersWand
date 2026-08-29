package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import com.playtheatria.buildersWand.wand.WandItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveTest {

    @Test
    void activationUsesAreAccountedOnceAndWaveHoldsTheCompleteFundedList() {
        World world = world();
        PlannedCell first = cell(world, 1);
        PlannedCell second = cell(world, 2);
        List<PlannedCell> printable = List.of(first, second);
        PlanOptions options = PlanOptions.prefab("Starter House", "prefab:house:v1:hash",
                20, List.of(), true, 30);
        WandItems.UseReceipt activationReceipt = new WandItems.UseReceipt(100, 80);

        Wave wave = new Wave(UUID.randomUUID(), "Builder", world,
                MaterialSelectionSnapshot.single(first.material()), options,
                printable, 1, Set.of(), false, false, "wand-token",
                activationReceipt, 20);

        assertEquals(20L, wave.usesSpent);
        assertEquals(activationReceipt, wave.activationReceipt);
        assertEquals(20, wave.activationUses);
        assertEquals(printable, wave.printable);
        assertEquals(2, wave.total());
        assertEquals(0, wave.successfulCells());
    }

    @Test
    void constructorRejectsNegativeActivationCounts() {
        World world = world();
        PlannedCell printable = cell(world, 1);
        PlanOptions options = PlanOptions.prefab("Starter House", "prefab:house:v1:hash",
                20, List.of(), true, 30);

        assertThrows(IllegalArgumentException.class, () -> new Wave(
                UUID.randomUUID(), "Builder", world,
                MaterialSelectionSnapshot.single(printable.material()), options,
                List.of(printable), 1, Set.of(), false, false, "wand-token",
                null, -1));
    }

    private static PlannedCell cell(World world, int x) {
        PrintMaterial stone = PrintMaterial.block(Material.STONE);
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMaterial" -> Material.STONE;
                    case "getAsString" -> "minecraft:stone";
                    case "clone" -> proxy;
                    default -> null;
                });
        return new PlannedCell(new Location(world, x, 2, 3), stone, data);
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }
}
