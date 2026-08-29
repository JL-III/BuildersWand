package com.playtheatria.buildersWand.prefab;

import com.playtheatria.buildersWand.form.Density;
import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.wave.Plan;
import com.playtheatria.buildersWand.wave.PlanOptions;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefabPlanFactoryTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void createsStrictAuthoredPlanWithAbsoluteRotatedCellsAndClearance() {
        PrefabDefinition definition = definition(25, true);
        World world = world();
        BlockVector callerAnchor = new BlockVector(10, 20, 30);
        PrefabPlanFactory factory = new PrefabPlanFactory(
                PrefabBlockPolicy.allowAll(),
                state -> blockData(state.contains("oak_planks")
                        ? Material.OAK_PLANKS : Material.STONE, false),
                ignored -> java.util.Optional.empty()
        );

        Plan plan = factory.create(definition, world, callerAnchor, 1, true, 30);
        callerAnchor.setX(999);

        assertSame(world, plan.world());
        assertEquals(Form.BOX, plan.form());
        assertEquals(Density.SOLID, plan.density());
        assertEquals(new Dims(2, 1, 3), plan.dims());
        assertEquals(new BlockVector(10, 20, 30), plan.effectiveAnchor());
        assertEquals(new BlockVector(10, 20, 30), plan.interactionAnchor());

        assertEquals(2, plan.targets().size());
        assertEquals(new BlockVector(10, 20, 30), vectorOf(plan.targets().get(0).location()));
        assertEquals(Material.STONE, plan.targets().get(0).material().sourceItem());
        assertEquals(new BlockVector(11, 20, 32), vectorOf(plan.targets().get(1).location()));
        assertEquals(Material.OAK_PLANKS, plan.targets().get(1).material().sourceItem());

        PlanOptions options = plan.options();
        assertEquals(PlanOptions.Kind.PREFAB, options.kind());
        assertEquals("Starter House", options.label());
        assertEquals("prefab:starter_house:v2:" + HASH, options.contentBinding());
        assertFalse(options.livePaletteRequired());
        assertTrue(options.strictExistingStates());
        assertTrue(options.alwaysConfirm());
        assertEquals(25, options.activationUses());
        assertEquals(List.of(new BlockVector(11, 20, 30)), options.clearanceCells());
        assertTrue(options.requirePlacementLogging());
        assertTrue(options.refuseLivingEntitiesAtStart());
        assertEquals(30, options.confirmationSeconds());

        // Prefab plans do not use this compatibility snapshot to assign authored cells.
        assertEquals(Material.STONE, plan.materialSelection().entries().getFirst().sourceItem());
    }

    @Test
    void normalizesRotationAndSwapsOnlyHorizontalDimensions() {
        PrefabDimensions dimensions = new PrefabDimensions(3, 4, 2);

        assertEquals(new Dims(3, 4, 2), PrefabPlanFactory.rotatedDimensions(dimensions, 0));
        assertEquals(new Dims(2, 4, 3), PrefabPlanFactory.rotatedDimensions(dimensions, 1));
        assertEquals(new Dims(3, 4, 2), PrefabPlanFactory.rotatedDimensions(dimensions, 6));
        assertEquals(new Dims(2, 4, 3), PrefabPlanFactory.rotatedDimensions(dimensions, -1));
    }

    @Test
    void rejectsActivationUsesOutsideSharedIntegerCounter() {
        assertEquals(Integer.MAX_VALUE,
                PrefabPlanFactory.activationUsesAsInt(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> PrefabPlanFactory.activationUsesAsInt((long) Integer.MAX_VALUE + 1));
        assertThrows(IllegalArgumentException.class,
                () -> PrefabPlanFactory.activationUsesAsInt(-1));
    }

    @Test
    void rejectsCoordinateOverflowInsteadOfWrappingIntoAnotherLocation() {
        assertThrows(ArithmeticException.class, () -> PrefabPlanFactory.translate(
                new BlockVector(Integer.MAX_VALUE, 0, 0),
                new PrefabPosition(1, 0, 0)
        ));
    }

    @Test
    void rejectsFluidAndNonItemRuntimeStatesDefensively() {
        PrefabDefinition definition = oneCellDefinition(0);
        PrefabPlanFactory water = new PrefabPlanFactory(
                PrefabBlockPolicy.allowAll(),
                ignored -> blockData(Material.WATER, false),
                ignored -> java.util.Optional.empty()
        );
        PrefabPlanFactory nonItem = new PrefabPlanFactory(
                PrefabBlockPolicy.allowAll(),
                ignored -> blockData(Material.PISTON_HEAD, false),
                material -> java.util.Optional.of(material.name()
                        + " has no one-item feedstock mapping")
        );

        IllegalArgumentException fluidFailure = assertThrows(IllegalArgumentException.class,
                () -> water.create(definition, world(), new BlockVector(), 0, false, 30));
        assertTrue(fluidFailure.getMessage().contains("fluids"));
        IllegalArgumentException itemFailure = assertThrows(IllegalArgumentException.class,
                () -> nonItem.create(definition, world(), new BlockVector(), 0, false, 30));
        assertTrue(itemFailure.getMessage().contains("feedstock"));
    }

    @Test
    void rejectsWaterloggedRuntimeStateEvenWhenCatalogPolicyIsInjected() {
        PrefabPlanFactory factory = new PrefabPlanFactory(
                PrefabBlockPolicy.allowAll(),
                ignored -> blockData(Material.OAK_STAIRS, true),
                ignored -> java.util.Optional.empty()
        );

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> factory.create(
                        oneCellDefinition(0), world(), new BlockVector(), 0, false, 30
                ));

        assertTrue(failure.getMessage().contains("fluids"));
    }

    private static PrefabDefinition definition(long activationUses, boolean allowRotation) {
        PrefabMetadata metadata = metadata(activationUses, allowRotation);
        List<PrefabCell> cells = new ArrayList<>();
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 3; x++) {
                PrefabPosition position = new PrefabPosition(x, 0, z);
                if (position.equals(new PrefabPosition(0, 0, 0))) {
                    cells.add(PrefabCell.block(position, "minecraft:stone"));
                } else if (position.equals(new PrefabPosition(2, 0, 1))) {
                    cells.add(PrefabCell.block(position, "minecraft:oak_planks"));
                } else if (position.equals(new PrefabPosition(0, 0, 1))) {
                    cells.add(PrefabCell.clearance(position));
                } else {
                    cells.add(PrefabCell.ignored(position));
                }
            }
        }
        return new PrefabDefinition(
                metadata,
                new PrefabDimensions(3, 1, 2),
                new PrefabPosition(0, 0, 0),
                cells,
                HASH
        );
    }

    private static PrefabDefinition oneCellDefinition(long activationUses) {
        return new PrefabDefinition(
                metadata(activationUses, true),
                new PrefabDimensions(1, 1, 1),
                new PrefabPosition(0, 0, 0),
                List.of(PrefabCell.block(new PrefabPosition(0, 0, 0), "minecraft:stone")),
                HASH
        );
    }

    private static PrefabMetadata metadata(long activationUses, boolean allowRotation) {
        return new PrefabMetadata(
                "starter_house",
                "Starter House",
                2,
                "starter_house.schem",
                0,
                activationUses,
                allowRotation,
                PrefabClearance.SCHEMATIC_AIR
        );
    }

    private static BlockVector vectorOf(org.bukkit.Location location) {
        return new BlockVector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static World world() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUID" -> id;
                    case "getName" -> "test";
                    case "toString" -> "test-world";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static BlockData blockData(Material material, boolean waterlogged) {
        Class<?>[] interfaces = waterlogged
                ? new Class<?>[]{BlockData.class, Waterlogged.class}
                : new Class<?>[]{BlockData.class};
        return (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                interfaces,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "isWaterlogged" -> waterlogged;
                    case "toString", "getAsString" -> material.name().toLowerCase();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
