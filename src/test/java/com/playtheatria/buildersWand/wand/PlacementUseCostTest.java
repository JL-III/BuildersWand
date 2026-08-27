package com.playtheatria.buildersWand.wand;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementUseCostTest {

    @Test
    void waterUsesConfiguredCostWhileSolidBlocksRemainOneToOne() {
        assertEquals(3, PlacementUseCost.perCell(PrintMaterial.water(), 3));
        assertEquals(1, PlacementUseCost.perCell(PrintMaterial.block(Material.STONE), 3));
    }

    @Test
    void waterAffordabilityOnlyCountsWholeSourceCosts() {
        assertEquals(0, PlacementUseCost.affordableCells(0, 3));
        assertEquals(0, PlacementUseCost.affordableCells(1, 3));
        assertEquals(0, PlacementUseCost.affordableCells(2, 3));
        assertEquals(1, PlacementUseCost.affordableCells(3, 3));
        assertEquals(1, PlacementUseCost.affordableCells(5, 3));
        assertEquals(2, PlacementUseCost.affordableCells(6, 3));
    }

    @Test
    void plannedWaterCostUsesLongArithmetic() {
        assertEquals(1_099_511_627_264L,
                PlacementUseCost.totalUses(512, Integer.MAX_VALUE));
    }
}
