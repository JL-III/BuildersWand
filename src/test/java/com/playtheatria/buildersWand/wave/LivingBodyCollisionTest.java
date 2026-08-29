package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.wand.PrintMaterial;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivingBodyCollisionTest {

    @Test
    void solidMaterialsRequireClearanceButWaterDoesNot() {
        assertTrue(LivingBodyCollision.requiresClearance(PrintMaterial.block(Material.STONE)));
        assertFalse(LivingBodyCollision.requiresClearance(PrintMaterial.water()));
    }

    @Test
    void detectsBodiesInsideOrPartiallyCrossingTheCell() {
        assertTrue(LivingBodyCollision.overlapsCell(
                new BoundingBox(0.2, 0.1, 0.2, 0.8, 0.9, 0.8), 0, 0, 0));
        assertTrue(LivingBodyCollision.overlapsCell(
                new BoundingBox(-0.2, 0.1, 0.2, 0.2, 1.8, 0.8), 0, 0, 0));
        assertTrue(LivingBodyCollision.overlapsCell(
                new BoundingBox(-2.0, -1.0, -2.0, 2.0, 3.0, 2.0), 0, 0, 0));
    }

    @Test
    void handlesNegativeWorldCoordinates() {
        assertTrue(LivingBodyCollision.overlapsCell(
                new BoundingBox(-3.4, 10.2, -5.6, -2.8, 11.8, -5.0), -3, 10, -6));
    }

    @Test
    void exactFaceEdgeAndCornerContactAreNotIntersections() {
        assertFalse(LivingBodyCollision.overlapsCell(
                new BoundingBox(0.0, 1.0, 0.0, 1.0, 2.8, 1.0), 0, 0, 0));
        assertFalse(LivingBodyCollision.overlapsCell(
                new BoundingBox(1.0, 1.0, 0.2, 1.6, 2.8, 0.8), 0, 0, 0));
        assertFalse(LivingBodyCollision.overlapsCell(
                new BoundingBox(1.0, 1.0, 1.0, 1.6, 2.8, 1.6), 0, 0, 0));
    }

    @Test
    void disjointBodyDoesNotBlockTheCell() {
        assertFalse(LivingBodyCollision.overlapsCell(
                new BoundingBox(4.0, 4.0, 4.0, 4.6, 5.8, 4.6), 0, 0, 0));
    }
}
