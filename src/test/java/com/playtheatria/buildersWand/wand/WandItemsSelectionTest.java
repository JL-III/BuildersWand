package com.playtheatria.buildersWand.wand;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandItemsSelectionTest {

    @Test
    void emptyHotbarHasAnActionableRefusal() {
        WandItems.MaterialSelection selection = capture(List.of(
                Material.AIR, Material.AIR, Material.AIR, Material.AIR, Material.AIR,
                Material.AIR, Material.AIR, Material.AIR, Material.AIR));

        assertTrue(selection.snapshot().isEmpty());
        assertTrue(selection.problem().contains("hotbar"));
    }

    @Test
    void gapsAndSlotOrderDoNotChangeTheWeightedPalette() {
        WandItems.MaterialSelection first = capture(List.of(
                Material.STONE, Material.AIR, Material.AIR, Material.ANDESITE,
                Material.AIR, Material.AIR, Material.AIR, Material.AIR, Material.STONE));
        WandItems.MaterialSelection rearranged = capture(List.of(
                Material.AIR, Material.STONE, Material.STONE, Material.AIR,
                Material.AIR, Material.AIR, Material.ANDESITE, Material.AIR, Material.AIR));

        MaterialSelectionSnapshot left = first.snapshot().orElseThrow();
        MaterialSelectionSnapshot right = rearranged.snapshot().orElseThrow();
        assertEquals(left, right);
        assertEquals(2, left.weights().get(Material.STONE));
        assertEquals(1, left.weights().get(Material.ANDESITE));
    }

    @Test
    void ordinaryUtilityItemsAreIgnoredButUnsupportedBucketsAreRejected() {
        assertEquals(Material.STONE, capture(List.of(Material.APPLE, Material.STONE))
                .snapshot().orElseThrow().entries().getFirst().sourceItem());

        WandItems.MaterialSelection refused = capture(List.of(Material.LAVA_BUCKET));
        assertTrue(refused.snapshot().isEmpty());
        assertTrue(refused.problem().contains("only water buckets"));
    }

    @Test
    void customizedCrateKeyMaterialIsIgnoredInsteadOfRefusingThePalette() {
        List<Material> hotbar = List.of(
                WandItems.paletteMaterial(Material.TRIPWIRE_HOOK, true),
                WandItems.paletteMaterial(Material.STONE, false));

        assertEquals(Material.STONE, capture(hotbar)
                .snapshot().orElseThrow().entries().getFirst().sourceItem());
        assertEquals(Material.AIR,
                WandItems.paletteMaterial(Material.WATER_BUCKET, true));
    }

    @Test
    void unsupportedBlockNamesItsHotbarSlot() {
        WandItems.MaterialSelection refused = capture(List.of(Material.AIR, Material.CHEST));

        assertTrue(refused.snapshot().isEmpty());
        assertTrue(refused.problem().contains("slot 2"));
        assertTrue(refused.problem().contains("chest"));
    }

    @Test
    void productionPolicyRejectsNamedMultiCellAndContentBlocks() {
        assertTrue(WandItems.isDenylisted(Material.CHEST));
        assertTrue(WandItems.isDenylisted(Material.TRAPPED_CHEST));
        assertTrue(WandItems.isDenylisted(Material.COPPER_CHEST));
        assertTrue(WandItems.isDenylisted(Material.SHULKER_BOX));
        assertTrue(WandItems.isDenylisted(Material.OAK_DOOR));
        assertTrue(WandItems.isDenylisted(Material.RED_BED));
    }

    @Test
    void multipleWaterBucketsAreOnePaletteAndCannotMixWithSolids() {
        MaterialSelectionSnapshot waterOnly = capture(List.of(
                Material.WATER_BUCKET, Material.AIR, Material.WATER_BUCKET))
                .snapshot().orElseThrow();
        assertTrue(waterOnly.containsWater());
        assertEquals(1, waterOnly.entries().size());

        WandItems.MaterialSelection mixed = capture(List.of(
                Material.WATER_BUCKET, Material.STONE));
        assertFalse(mixed.snapshot().isPresent());
        assertTrue(mixed.problem().contains("cannot be mixed"));
    }

    @Test
    void moreThanNineSlotsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> capture(java.util.Collections.nCopies(10,
                Material.STONE)));
    }

    private static WandItems.MaterialSelection capture(List<Material> hotbar) {
        return WandItems.materialSelection(hotbar,
                material -> switch (material) {
                    case STONE, ANDESITE -> Optional.of(PrintMaterial.block(material));
                    default -> Optional.empty();
                },
                material -> material == Material.STONE
                        || material == Material.ANDESITE
                        || material == Material.CHEST);
    }
}
