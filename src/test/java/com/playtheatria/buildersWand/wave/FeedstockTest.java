package com.playtheatria.buildersWand.wave;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedstockTest {

    @Test
    void oneSamplePerMatchingHotbarSlotIsReserved() {
        List<Feedstock.SupplySlot> stone = List.of(
                new Feedstock.SupplySlot(0, 1),
                new Feedstock.SupplySlot(3, 3),
                new Feedstock.SupplySlot(9, 4));

        assertEquals(6, Feedstock.consumableCount(stone));
    }

    @Test
    void duplicateOneItemSamplesAddWeightButNoConsumableSupply() {
        List<Feedstock.SupplySlot> samples = List.of(
                new Feedstock.SupplySlot(0, 1),
                new Feedstock.SupplySlot(4, 1),
                new Feedstock.SupplySlot(8, 1));

        assertEquals(0, Feedstock.consumableCount(samples));
        assertTrue(Feedstock.nextSpendSlot(samples).isEmpty());
    }

    @Test
    void mainInventoryIsSpentBeforeHotbarSurplus() {
        List<Feedstock.SupplySlot> supply = List.of(
                new Feedstock.SupplySlot(0, 64),
                new Feedstock.SupplySlot(5, 2),
                new Feedstock.SupplySlot(12, 1),
                new Feedstock.SupplySlot(27, 32));

        assertEquals(12, Feedstock.nextSpendSlot(supply).orElseThrow());
    }

    @Test
    void hotbarSurplusCanBeSpentWithoutTakingTheFinalSample() {
        List<Feedstock.SupplySlot> supply = List.of(
                new Feedstock.SupplySlot(0, 1),
                new Feedstock.SupplySlot(4, 3));

        assertEquals(4, Feedstock.nextSpendSlot(supply).orElseThrow());
        assertEquals(2, Feedstock.consumableCount(supply));
    }

    @Test
    void customizedItemsNeverQualifyAsFeedstock() {
        assertTrue(Feedstock.isPlainFeedstock(
                org.bukkit.Material.STONE, false, org.bukkit.Material.STONE, false));
        assertFalse(Feedstock.isPlainFeedstock(
                org.bukkit.Material.STONE, true, org.bukkit.Material.STONE, false));
        assertFalse(Feedstock.isPlainFeedstock(
                org.bukkit.Material.STONE, false, org.bukkit.Material.ANDESITE, false));
        assertFalse(Feedstock.isPlainFeedstock(
                org.bukkit.Material.WATER_BUCKET, true,
                org.bukkit.Material.WATER_BUCKET, false));
    }
}
