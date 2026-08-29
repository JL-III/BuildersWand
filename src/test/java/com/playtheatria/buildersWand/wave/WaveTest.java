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
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTest {

    @Test
    void activationUsesAreAccountedOnceBeforeAnyCellCompletes() {
        World world = world();
        PlannedCell admitted = cell(world);
        PlanOptions options = PlanOptions.prefab("Starter House", "prefab:house:v1:hash",
                20, List.of(), true, 30);
        WandItems.UseReceipt activationReceipt = new WandItems.UseReceipt(100, 80);

        Wave wave = new Wave(UUID.randomUUID(), "Builder", world,
                MaterialSelectionSnapshot.single(admitted.material()), options,
                List.of(admitted), 1, Set.of(), false, false, "wand-token", 2,
                activationReceipt, 20);

        assertEquals(20L, wave.usesSpent);
        assertEquals(activationReceipt, wave.activationReceipt);
        assertEquals(20, wave.activationUses);
        assertTrue(wave.partialAdmission);
        assertEquals(1, wave.total());
        assertEquals(0, wave.successfulCells());
    }

    @Test
    void constructorRejectsImpossibleAdmissionAndActivationCounts() {
        World world = world();
        PlannedCell admitted = cell(world);
        PlanOptions options = PlanOptions.prefab("Starter House", "prefab:house:v1:hash",
                20, List.of(), true, 30);

        assertThrows(IllegalArgumentException.class, () -> new Wave(
                UUID.randomUUID(), "Builder", world,
                MaterialSelectionSnapshot.single(admitted.material()), options,
                List.of(admitted), 1, Set.of(), false, false, "wand-token", 0,
                new WandItems.UseReceipt(100, 80), 20));
        assertThrows(IllegalArgumentException.class, () -> new Wave(
                UUID.randomUUID(), "Builder", world,
                MaterialSelectionSnapshot.single(admitted.material()), options,
                List.of(admitted), 1, Set.of(), false, false, "wand-token", 1,
                null, -1));
    }

    private static PlannedCell cell(World world) {
        PrintMaterial stone = PrintMaterial.block(Material.STONE);
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMaterial" -> Material.STONE;
                    case "getAsString" -> "minecraft:stone";
                    case "clone" -> proxy;
                    default -> null;
                });
        return new PlannedCell(new Location(world, 1, 2, 3), stone, data);
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
