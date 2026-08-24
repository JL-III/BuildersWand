package com.playtheatria.buildersWand.form;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orientation basis from the clicked face (design §5.3). */
class OrientationTest {

    @Test
    void wallLateralIsScreenRight() {
        assertWallLateral(BlockFace.SOUTH, bv(1, 0, 0));
        assertWallLateral(BlockFace.NORTH, bv(-1, 0, 0));
        assertWallLateral(BlockFace.EAST, bv(0, 0, -1));
        assertWallLateral(BlockFace.WEST, bv(0, 0, 1));
    }

    private void assertWallLateral(BlockFace face, BlockVector expectedL) {
        Orientation o = Orientation.fromClick(face, 0f);
        assertTrue(o.wall());
        assertEquals(bv(0, 1, 0), o.s()); // UP
        assertEquals(expectedL, o.l());
        // l = (n.z, 0, −n.x)
        assertEquals(o.n().getBlockZ(), o.l().getBlockX());
        assertEquals(-o.n().getBlockX(), o.l().getBlockZ());
    }

    @Test
    void floorLateralIsClockwiseOfHeading() {
        assertFloor(0f, BlockFace.SOUTH, bv(0, 0, 1), bv(-1, 0, 0));
        assertFloor(90f, BlockFace.WEST, bv(-1, 0, 0), bv(0, 0, -1));
        assertFloor(180f, BlockFace.NORTH, bv(0, 0, -1), bv(1, 0, 0));
        assertFloor(270f, BlockFace.EAST, bv(1, 0, 0), bv(0, 0, 1));
    }

    private void assertFloor(float yaw, BlockFace heading, BlockVector s, BlockVector l) {
        Orientation o = Orientation.fromClick(BlockFace.UP, yaw);
        assertFalse(o.wall());
        assertEquals(heading, o.heading());
        assertEquals(s, o.s());
        assertEquals(l, o.l());
        assertEquals(bv(0, 1, 0), o.n()); // up from a floor
        // l = (−s.z, 0, s.x)
        assertEquals(-o.s().getBlockZ(), o.l().getBlockX());
        assertEquals(o.s().getBlockX(), o.l().getBlockZ());
    }

    @Test
    void yawSnapsToCardinal() {
        assertEquals(BlockFace.SOUTH, Orientation.fromClick(BlockFace.UP, 0f).heading());
        assertEquals(BlockFace.WEST, Orientation.fromClick(BlockFace.UP, 90f).heading());
        assertEquals(BlockFace.NORTH, Orientation.fromClick(BlockFace.UP, 180f).heading());
        assertEquals(BlockFace.EAST, Orientation.fromClick(BlockFace.UP, 270f).heading());
        assertEquals(BlockFace.SOUTH, Orientation.fromClick(BlockFace.UP, 44f).heading());
        assertEquals(BlockFace.WEST, Orientation.fromClick(BlockFace.UP, 46f).heading());
        assertEquals(BlockFace.SOUTH, Orientation.fromClick(BlockFace.UP, 359f).heading());
        assertEquals(BlockFace.EAST, Orientation.fromClick(BlockFace.UP, -90f).heading());
    }

    private static BlockVector bv(int x, int y, int z) {
        return new BlockVector(x, y, z);
    }
}
