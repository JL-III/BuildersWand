package com.playtheatria.buildersWand.wand;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialSelectionSnapshotTest {

    private static final UUID WORLD = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void singleSelectionAlwaysReturnsItsOnlyMaterial() {
        PrintMaterial stone = PrintMaterial.block(Material.STONE);
        MaterialSelectionSnapshot selection = MaterialSelectionSnapshot.single(stone);

        assertEquals(stone, selection.select(WORLD, 0, 64, 0));
        assertEquals(stone, selection.select(WORLD, Integer.MIN_VALUE, -64, Integer.MAX_VALUE));
    }

    @Test
    void canonicalPaletteIgnoresSlotOrderButRetainsDuplicateWeights() {
        MaterialSelectionSnapshot first = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.ANDESITE),
                PrintMaterial.block(Material.STONE)));
        MaterialSelectionSnapshot rearranged = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.ANDESITE)));

        assertEquals(first, rearranged);
        assertEquals(2, first.weights().get(Material.STONE));
        assertEquals(1, first.weights().get(Material.ANDESITE));
        assertEquals(3, first.entries().size());
        assertEquals(first.fingerprint(), rearranged.fingerprint());
    }

    @Test
    void changingDuplicateCountChangesFingerprintAndWeightedOutput() {
        MaterialSelectionSnapshot equal = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.ANDESITE)));
        MaterialSelectionSnapshot stoneWeighted = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.ANDESITE)));

        assertNotEquals(equal.fingerprint(), stoneWeighted.fingerprint());
        long stones = java.util.stream.IntStream.range(-2_000, 2_000)
                .mapToObj(x -> stoneWeighted.select(WORLD, x, 70, -4))
                .filter(material -> material.placedBlock() == Material.STONE)
                .count();
        assertTrue(stones > 2_300 && stones < 3_000,
                "two of three palette entries should give stone roughly two-thirds of cells");
    }

    @Test
    void coordinateAssignmentIsStableAndFilteringDoesNotRephaseIt() {
        MaterialSelectionSnapshot selection = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE),
                PrintMaterial.block(Material.COBBLESTONE),
                PrintMaterial.block(Material.ANDESITE)));

        List<PrintMaterial> all = java.util.stream.IntStream.range(0, 32)
                .mapToObj(x -> selection.select(WORLD, x, 70, -4))
                .toList();
        List<PrintMaterial> afterFiltering = java.util.stream.IntStream.range(0, 32)
                .filter(x -> x != 5 && x != 17)
                .mapToObj(x -> selection.select(WORLD, x, 70, -4))
                .toList();

        assertEquals(all, java.util.stream.IntStream.range(0, 32)
                .mapToObj(x -> selection.select(WORLD, x, 70, -4))
                .toList());
        assertEquals(afterFiltering, java.util.stream.IntStream.range(0, 32)
                .filter(x -> x != 5 && x != 17)
                .mapToObj(all::get)
                .toList());
        assertTrue(all.stream().distinct().count() > 1);
    }

    @Test
    void worldIdentityParticipatesInThePattern() {
        MaterialSelectionSnapshot selection = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE), PrintMaterial.block(Material.DIRT)));
        UUID otherWorld = UUID.fromString("9f8e7d6c-5b4a-3210-9876-123456789abc");

        boolean differs = java.util.stream.IntStream.range(-64, 64)
                .anyMatch(x -> !selection.select(WORLD, x, 70, 2)
                        .equals(selection.select(otherWorld, x, 70, 2)));

        assertTrue(differs);
    }

    @Test
    void repeatedWaterSamplesCanonicalizeToOneReusableBucket() {
        MaterialSelectionSnapshot selection = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.water(), PrintMaterial.water(), PrintMaterial.water()));

        assertTrue(selection.singleMaterial());
        assertTrue(selection.containsWater());
        assertEquals(1, selection.entries().size());
        assertEquals(PrintMaterial.water(), selection.select(WORLD, 4, 72, -9));
    }

    @Test
    void repeatedSlotsOfOneSolidAreStillASingleMaterialPalette() {
        MaterialSelectionSnapshot selection = MaterialSelectionSnapshot.palette(List.of(
                PrintMaterial.block(Material.STONE), PrintMaterial.block(Material.STONE)));

        assertTrue(selection.singleMaterial());
        assertEquals(2, selection.weights().get(Material.STONE));
    }

    @Test
    void rejectsEmptyNullOversizedAndMixedWaterSelections() {
        assertThrows(IllegalArgumentException.class,
                () -> MaterialSelectionSnapshot.palette(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> MaterialSelectionSnapshot.palette(java.util.Collections.singletonList(null)));
        List<PrintMaterial> tooMany = java.util.stream.IntStream.range(0, 10)
                .mapToObj(ignored -> PrintMaterial.block(Material.STONE))
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> MaterialSelectionSnapshot.palette(tooMany));
        assertThrows(IllegalArgumentException.class,
                () -> MaterialSelectionSnapshot.palette(List.of(
                        PrintMaterial.water(), PrintMaterial.block(Material.STONE))));
    }
}
