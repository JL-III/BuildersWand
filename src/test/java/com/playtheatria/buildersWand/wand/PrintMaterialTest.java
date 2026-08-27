package com.playtheatria.buildersWand.wand;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintMaterialTest {

    @Test
    void waterBucketSelectsReusableSourceWater() {
        PrintMaterial material = WandItems.printMaterialFor(Material.WATER_BUCKET).orElseThrow();

        assertEquals(Material.WATER_BUCKET, material.sourceItem());
        assertEquals(Material.WATER, material.placedBlock());
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS, material.previewBlock());
        assertTrue(material.reusable());
        assertTrue(material.isWater());
    }

    @Test
    void lavaBucketIsNotEnabledByTheWaterException() {
        assertTrue(WandItems.printMaterialFor(Material.LAVA_BUCKET).isEmpty());
    }

    @Test
    void ordinaryBlockPolicyStillConsumesMatchingItems() {
        PrintMaterial material = PrintMaterial.block(Material.STONE);

        assertEquals(Material.STONE, material.sourceItem());
        assertEquals(Material.STONE, material.placedBlock());
        assertFalse(material.reusable());
        assertFalse(material.isWater());
    }
}
