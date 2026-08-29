package com.playtheatria.buildersWand.wave;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementBudgetTest {

    @Test
    void missingAssignmentMarksExactCellsUnavailableWithoutSubstitutionOrRephasing() {
        List<PlacementBudget.Cost> costs = List.of(
                new PlacementBudget.Cost(Material.STONE, true, 1),
                new PlacementBudget.Cost(Material.DIRT, true, 1),
                new PlacementBudget.Cost(Material.STONE, true, 1),
                new PlacementBudget.Cost(Material.DIRT, true, 1)
        );

        PlacementBudget.Result result = PlacementBudget.evaluate(costs,
                Map.of(Material.STONE, 2, Material.DIRT, 0), 20, false, false);

        assertEquals(List.of(true, false, true, false), result.decisions().stream()
                .map(PlacementBudget.Decision::affordable).toList());
        assertEquals(2, result.affordableCells());
        assertEquals(2L, result.affordableUses());
        assertEquals(Map.of(Material.DIRT, 2), result.missingMaterials());
        assertFalse(result.fullyAffordable());
    }

    @Test
    void useShortageMarksOnlyCellsWhoseIndividualCostDoesNotFit() {
        List<PlacementBudget.Cost> costs = List.of(
                new PlacementBudget.Cost(Material.WATER_BUCKET, false, 3),
                new PlacementBudget.Cost(Material.STONE, true, 1),
                new PlacementBudget.Cost(Material.STONE, true, 1)
        );

        PlacementBudget.Result result = PlacementBudget.evaluate(costs,
                Map.of(Material.WATER_BUCKET, 1, Material.STONE, 2), 2, false, false);

        assertFalse(result.decisions().get(0).affordable());
        assertFalse(result.decisions().get(0).usesAffordable());
        assertTrue(result.decisions().get(1).affordable());
        assertTrue(result.decisions().get(2).affordable());
        assertEquals(2L, result.affordableUses());
        assertEquals(5L, result.requiredUses());
        assertFalse(result.fullyAffordable());
    }

    @Test
    void oneReusableBucketMakesEveryWaterCellMaterialAffordable() {
        PlacementBudget.Cost water = new PlacementBudget.Cost(Material.WATER_BUCKET, false, 3);

        PlacementBudget.Result result = PlacementBudget.evaluate(
                List.of(water, water, water), Map.of(Material.WATER_BUCKET, 1),
                9, false, false);

        assertTrue(result.decisions().stream().allMatch(PlacementBudget.Decision::affordable));
        assertEquals(3, result.affordableCells());
        assertEquals(Map.of(Material.WATER_BUCKET, 1), result.requiredMaterials());
        assertEquals(Map.of(), result.missingMaterials());
        assertTrue(result.fullyAffordable());
    }

    @Test
    void reusableBucketIsRequiredEvenWhenConsumedMaterialsAreBypassed() {
        PlacementBudget.Cost water = new PlacementBudget.Cost(Material.WATER_BUCKET, false, 3);

        PlacementBudget.Result result = PlacementBudget.evaluate(
                List.of(water, water), Map.of(), 6, true, false);

        assertFalse(result.decisions().getFirst().materialAffordable());
        assertFalse(result.decisions().getFirst().affordable());
        assertEquals(Map.of(Material.WATER_BUCKET, 1), result.missingMaterials());
        assertEquals(0L, result.affordableUses());
    }

    @Test
    void oneMissingWaterUseFailsTheWholePlanGate() {
        PlacementBudget.Cost water = new PlacementBudget.Cost(Material.WATER_BUCKET, false, 3);

        PlacementBudget.Result result = PlacementBudget.evaluate(
                List.of(water, water, water), Map.of(Material.WATER_BUCKET, 1),
                7, false, false);

        assertEquals(List.of(true, true, false), result.decisions().stream()
                .map(PlacementBudget.Decision::affordable).toList());
        assertEquals(2, result.affordableCells());
        assertEquals(6L, result.affordableUses());
        assertEquals(9L, result.requiredUses());
        assertFalse(result.fullyAffordable());
    }

    @Test
    void usesBypassStillRequiresBucketAndCreativeStillRequiresUses() {
        PlacementBudget.Cost water = new PlacementBudget.Cost(Material.WATER_BUCKET, false, 3);

        PlacementBudget.Result usesBypassMissingBucket = PlacementBudget.evaluate(
                List.of(water), Map.of(), 0, false, true);
        assertFalse(usesBypassMissingBucket.decisions().getFirst().affordable());
        assertFalse(usesBypassMissingBucket.decisions().getFirst().materialAffordable());
        assertTrue(usesBypassMissingBucket.decisions().getFirst().usesAffordable());

        PlacementBudget.Result creativeWithBucketButNoUses = PlacementBudget.evaluate(
                List.of(water), Map.of(Material.WATER_BUCKET, 1), 0, true, false);
        assertFalse(creativeWithBucketButNoUses.decisions().getFirst().affordable());
        assertTrue(creativeWithBucketButNoUses.decisions().getFirst().materialAffordable());
        assertFalse(creativeWithBucketButNoUses.decisions().getFirst().usesAffordable());
    }

    @Test
    void bypassesAreIndependent() {
        PlacementBudget.Cost cost = new PlacementBudget.Cost(Material.STONE, true, 2);

        PlacementBudget.Result creativeOnly = PlacementBudget.evaluate(
                List.of(cost), Map.of(), 1, true, false);
        assertFalse(creativeOnly.decisions().getFirst().affordable());
        assertTrue(creativeOnly.decisions().getFirst().materialAffordable());

        PlacementBudget.Result usesOnly = PlacementBudget.evaluate(
                List.of(cost), Map.of(), 0, false, true);
        assertFalse(usesOnly.decisions().getFirst().affordable());
        assertTrue(usesOnly.decisions().getFirst().usesAffordable());

        PlacementBudget.Result both = PlacementBudget.evaluate(
                List.of(cost), Map.of(), 0, true, true);
        assertTrue(both.decisions().getFirst().affordable());
        assertTrue(both.fullyAffordable());
    }
}
