package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.form.Dims;
import com.playtheatria.buildersWand.form.Form;
import com.playtheatria.buildersWand.wand.MaterialSelectionSnapshot;
import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementQuoteTest {

    @Test
    void exactBindingIncludesUsesAndWorldState() {
        Plan plan = plan();
        List<PlannedCell> printable = plan.targets();
        PlacementBudget.Result budget = PlacementBudget.evaluate(
                List.of(new PlacementBudget.Cost(Material.STONE, true, 1)),
                Map.of(Material.STONE, 5), 5, false, false);
        PlacementQuote original = new PlacementQuote(plan, printable, 0,
                Map.of(Material.STONE, 5), budget, 5, 10, false, false,
                "wand-id", 0, List.of("minecraft:air"), 100L);
        PlacementQuote exact = new PlacementQuote(plan, printable, 0,
                Map.of(Material.STONE, 5), budget, 5, 10, false, false,
                "wand-id", 0, List.of("minecraft:air"), 100L);
        PlacementQuote issuedLater = new PlacementQuote(plan, printable, 0,
                Map.of(Material.STONE, 5), budget, 5, 10, false, false,
                "wand-id", 0, List.of("minecraft:air"), 500L);
        PlacementQuote usesChanged = new PlacementQuote(plan, printable, 0,
                Map.of(Material.STONE, 5), budget, 6, 10, false, false,
                "wand-id", 0, List.of("minecraft:air"), 100L);
        PlacementQuote worldChanged = new PlacementQuote(plan, printable, 0,
                Map.of(Material.STONE, 5), budget, 5, 10, false, false,
                "wand-id", 0, List.of("minecraft:cave_air"), 100L);

        assertEquals(original, exact);
        assertNotEquals(original, usesChanged);
        assertNotEquals(original, worldChanged);
        assertTrue(original.sameBinding(exact));
        assertTrue(original.sameBinding(issuedLater));
        assertTrue(original.expiredAt(10_000_000_100L, 10));
    }

    @Test
    void shortBudgetCannotBecomeAConfirmationQuote() {
        Plan plan = plan();
        PlacementBudget.Result budget = PlacementBudget.evaluate(
                List.of(new PlacementBudget.Cost(Material.STONE, true, 1)),
                Map.of(), 5, false, false);

        assertThrows(IllegalArgumentException.class, () -> new PlacementQuote(
                plan, plan.targets(), 0, Map.of(Material.STONE, 0), budget,
                5, 10, false, false, "wand-id", 0,
                List.of("minecraft:air"), 100L));
    }

    @Test
    void prefabTotalIncludesActivationExactlyOnce() {
        Plan plan = prefabPlan(3);
        List<PlannedCell> printable = plan.targets();
        PlacementBudget.Result fullBudget = PlacementBudget.evaluate(
                List.of(new PlacementBudget.Cost(Material.STONE, true, 2),
                        new PlacementBudget.Cost(Material.STONE, true, 2)),
                Map.of(Material.STONE, 2),
                WaveRunner.remainingUsesForCells(7, 3, false), false, false);
        PlacementQuote full = new PlacementQuote(plan, printable, 0,
                Map.of(Material.STONE, 2), fullBudget, 7, 10, false, false,
                "wand-id", 0, List.of("minecraft:air", "minecraft:air"), 100L);

        assertEquals(7L, full.requiredTotalUses());
    }

    @Test
    void prefabBindingIncludesItsContentAndActivationPolicy() {
        Plan originalPlan = prefabPlan(3);
        Plan changedActivationPlan = prefabPlan(4);
        Plan changedContentPlan = prefabPlan(3, "prefab:house:v2:different-hash");
        PlacementBudget.Result budget = PlacementBudget.evaluate(
                List.of(new PlacementBudget.Cost(Material.STONE, true, 1),
                        new PlacementBudget.Cost(Material.STONE, true, 1)),
                Map.of(Material.STONE, 2), 7, false, false);
        PlacementQuote original = new PlacementQuote(originalPlan, originalPlan.targets(),
                0, Map.of(Material.STONE, 2), budget,
                10, 10, false, false, "wand-id", 0,
                List.of("minecraft:air", "minecraft:air"), 100L);
        PlacementQuote changedActivation = new PlacementQuote(changedActivationPlan,
                changedActivationPlan.targets(), 0,
                Map.of(Material.STONE, 2), budget, 10, 10, false, false,
                "wand-id", 0, List.of("minecraft:air", "minecraft:air"), 100L);
        PlacementQuote changedContent = new PlacementQuote(changedContentPlan,
                changedContentPlan.targets(), 0,
                Map.of(Material.STONE, 2), budget, 10, 10, false, false,
                "wand-id", 0, List.of("minecraft:air", "minecraft:air"), 100L);

        assertFalse(original.sameBinding(changedActivation));
        assertFalse(original.sameBinding(changedContent));
    }

    @Test
    void quoteWorldBindingCountsNonTargetValidationCells() {
        Plan base = plan();
        PlanOptions options = PlanOptions.ordinary(base.form(), List.of(
                new PlanValidationCell(new BlockVector(0, 1, 0), "minecraft:stone")));
        Plan validating = new Plan(base.world(), base.form(), base.dims(),
                base.effectiveAnchor(), base.interactionAnchor(), base.targets(),
                base.materialSelection(), base.density(), options);
        PlacementBudget.Result budget = PlacementBudget.evaluate(
                List.of(new PlacementBudget.Cost(Material.STONE, true, 1)),
                Map.of(Material.STONE, 1), 1, false, false);

        PlacementQuote quote = new PlacementQuote(validating, validating.targets(),
                0, Map.of(Material.STONE, 1), budget,
                1, 10, false, false, "wand-id", 0,
                List.of("minecraft:air", "minecraft:stone"), 100L);

        assertEquals(2, quote.worldStates().size());
        assertThrows(IllegalArgumentException.class, () -> new PlacementQuote(
                validating, validating.targets(), 0,
                Map.of(Material.STONE, 1), budget, 1, 10, false, false,
                "wand-id", 0, List.of("minecraft:air"), 100L));
    }

    private static Plan plan() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> null);
        BlockData blockData = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, args) -> null);
        PrintMaterial material = PrintMaterial.block(Material.STONE);
        PlannedCell cell = new PlannedCell(new Location(world, 1, 2, 3), material, blockData);
        return new Plan(world, Form.BOX, new Dims(1, 1, 1), new BlockVector(1, 2, 3),
                List.of(cell), MaterialSelectionSnapshot.single(material));
    }

    private static Plan prefabPlan(int activationUses) {
        return prefabPlan(activationUses, "prefab:house:v1:hash");
    }

    private static Plan prefabPlan(int activationUses, String contentBinding) {
        Plan ordinary = plan();
        PlannedCell first = ordinary.targets().getFirst();
        PlannedCell second = new PlannedCell(new Location(ordinary.world(), 2, 2, 3),
                first.material(), first.blockData());
        PlanOptions options = PlanOptions.prefab("Starter House", contentBinding,
                activationUses, List.of(), true, 30);
        return new Plan(ordinary.world(), ordinary.form(), ordinary.dims(),
                ordinary.effectiveAnchor(), ordinary.interactionAnchor(), List.of(first, second),
                ordinary.materialSelection(), ordinary.density(), options);
    }
}
