package com.playtheatria.buildersWand.form;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Golden geometry numbers (design §17). Drift fails the build. */
class ExpansionTest {

    @Test
    void boxShellCounts() {
        assertEquals(26, Expansion.cellCount(Form.BOX, new Dims(3, 3, 3)));
        assertEquals(296, Expansion.cellCount(Form.BOX, new Dims(8, 8, 8)));
        assertEquals(40, Expansion.cellCount(Form.BOX, new Dims(2, 5, 4))); // any extent ≤2 → solid
    }

    @Test
    void boxSolidFillsInterior() {
        assertEquals(26, Expansion.cellCount(Form.BOX, new Dims(3, 3, 3), Density.SHELL));
        assertEquals(27, Expansion.cellCount(Form.BOX, new Dims(3, 3, 3), Density.SOLID));
        assertEquals(1_000, Expansion.cellCount(Form.BOX, new Dims(10, 10, 10), Density.SOLID));
    }

    @Test
    void cylinderRimCounts() {
        int[] expected = {1, 8, 12, 16, 32, 28, 40, 40, 48}; // steps 1..9
        for (int step = 1; step <= 9; step++) {
            assertEquals(expected[step - 1], Expansion.rim(step).size(), "rim step " + step);
        }
    }

    @Test
    void largestCylinderIs384() {
        assertEquals(384, Expansion.cellCount(Form.CYLINDER, new Dims(9, 8, 1)));
    }

    @Test
    void solidCylinderUsesFilledDisks() {
        assertEquals(8, Expansion.rim(2).size());
        assertEquals(9, Expansion.disk(2).size());
        assertEquals(18, Expansion.cellCount(Form.CYLINDER, new Dims(2, 2, 1), Density.SOLID));
    }

    @Test
    void sphereShellCounts() {
        int[] expected = {1, 18, 62, 98, 210, 350, 450}; // steps 1..7, length 1
        for (int step = 1; step <= 7; step++) {
            assertEquals(expected[step - 1], Expansion.cellCount(Form.SPHERE, new Dims(step, 1, 1)), "sphere step " + step);
        }
    }

    @Test
    void capsuleFormula() {
        assertEquals(490, Expansion.cellCount(Form.SPHERE, new Dims(7, 2, 1))); // largest legal
        assertEquals(98, Expansion.cellCount(Form.SPHERE, new Dims(3, 4, 1)));  // 62 + 12·3
    }

    @Test
    void solidSphereAndCapsuleFillInterior() {
        assertEquals(18, Expansion.cellCount(Form.SPHERE, new Dims(2, 1, 1), Density.SHELL));
        assertEquals(19, Expansion.cellCount(Form.SPHERE, new Dims(2, 1, 1), Density.SOLID));
        assertEquals(28, Expansion.cellCount(Form.SPHERE, new Dims(2, 2, 1), Density.SOLID));
    }

    @Test
    void diagonalCounts() {
        assertEquals(40, Expansion.cellCount(Form.DIAGONAL, new Dims(8, 5, 1))); // run × width
    }

    @Test
    void wallIsWorldVerticalAndDeterministic() {
        Orientation northWall = Orientation.fromClick(BlockFace.NORTH, 0f);
        List<BlockVector> cells = Expansion.cells(Form.WALL, new Dims(3, 2, 1), northWall);
        assertEquals(List.of(
                bv(0, 0, 0), bv(-1, 0, 0), bv(-2, 0, 0),
                bv(0, 1, 0), bv(-1, 1, 0), bv(-2, 1, 0)), cells);
    }

    @Test
    void floorIsHorizontalEvenFromWallClick() {
        Orientation northWall = Orientation.fromClick(BlockFace.NORTH, 0f);
        List<BlockVector> cells = Expansion.cells(Form.FLOOR, new Dims(2, 2, 1), northWall);
        assertEquals(List.of(
                bv(0, 0, 0), bv(-1, 0, 0),
                bv(0, 0, -1), bv(-1, 0, -1)), cells);
    }

    @Test
    void lineUsesRuntimeSuppliedDominantAxis() {
        Orientation vertical = new Orientation(
                bv(0, 1, 0), bv(1, 0, 0), bv(0, 0, 1), true, BlockFace.NORTH);
        assertEquals(List.of(bv(0, 0, 0), bv(0, 1, 0), bv(0, 2, 0), bv(0, 3, 0)),
                Expansion.cells(Form.LINE, new Dims(4, 1, 1), vertical));
    }

    @Test
    void densityIsIgnoredByIntrinsicSolidForms() {
        Dims dims = new Dims(4, 3, 1);
        assertEquals(Expansion.cellCount(Form.WALL, dims, Density.SHELL),
                Expansion.cellCount(Form.WALL, dims, Density.SOLID));
    }

    @Test
    void extendSurfaceRequiresWorldTraversal() {
        Orientation floor = Orientation.fromClick(BlockFace.UP, 0f);
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> Expansion.cells(Form.EXTEND_SURFACE, new Dims(1, 1, 1), floor));
        assertEquals("extend_surface requires connected live-world traversal; dimensions alone are insufficient",
                error.getMessage());
    }

    @Test
    void emissionOrderIsNMajorDeterministic() {
        Orientation floor = Orientation.fromClick(BlockFace.UP, 0f);
        List<BlockVector> first = Expansion.cells(Form.BOX, new Dims(3, 3, 3), floor);
        List<BlockVector> second = Expansion.cells(Form.BOX, new Dims(3, 3, 3), floor);
        assertEquals(first, second); // deterministic
        assertEquals(26, first.size());
        assertEquals(
                List.of(bv(0, 0, 0), bv(-1, 0, 0), bv(-2, 0, 0), bv(0, 0, 1), bv(-1, 0, 1)),
                first.subList(0, 5));
        assertEquals(
                List.of(bv(-1, 2, 1), bv(-2, 2, 1), bv(0, 2, 2), bv(-1, 2, 2), bv(-2, 2, 2)),
                first.subList(first.size() - 5, first.size()));
    }

    @Test
    void expansionKeepsCandidatesBeyondThePlacementBudgetInDeterministicOrder() {
        Orientation floor = Orientation.fromClick(BlockFace.UP, 0f);
        List<BlockVector> shell = Expansion.cells(
                Form.BOX, new Dims(16, 16, 16), floor, Density.SHELL);

        assertEquals(1_352, shell.size());
        assertEquals(bv(0, 0, 0), shell.get(0));
        assertEquals(bv(-15, 15, 15), shell.get(shell.size() - 1));
    }

    @Test
    void boundedIllegalPreviewPreservesTheCompleteRequestedBounds() {
        Orientation floor = Orientation.fromClick(BlockFace.UP, 0f);
        Dims dims = new Dims(16, 16, 16);
        List<BlockVector> complete = Expansion.cells(Form.BOX, dims, floor, Density.SHELL);
        List<BlockVector> bounded = Expansion.previewCells(
                Form.BOX, dims, floor, Density.SHELL, FormLimits.DEFAULTS, 1_025);

        assertEquals(1_352, complete.size());
        assertEquals(1_025, bounded.size());
        assertEquals(bounds(complete), bounds(bounded));
        assertEquals(new Bounds(-15, 0, 0, 15, 0, 15), bounds(bounded));
    }

    @Test
    void tinyIllegalPreviewBudgetStillRetainsEveryCoordinateExtent() {
        Orientation floor = Orientation.fromClick(BlockFace.UP, 0f);
        Dims dims = new Dims(16, 16, 16);
        List<BlockVector> complete = Expansion.cells(Form.BOX, dims, floor, Density.SHELL);
        List<BlockVector> bounded = Expansion.previewCells(
                Form.BOX, dims, floor, Density.SHELL, FormLimits.DEFAULTS, 1);

        assertEquals(bounds(complete), bounds(bounded));
        assertTrue(bounded.size() <= 6);
    }

    private static Bounds bounds(List<BlockVector> cells) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockVector cell : cells) {
            minX = Math.min(minX, cell.getBlockX());
            maxX = Math.max(maxX, cell.getBlockX());
            minY = Math.min(minY, cell.getBlockY());
            maxY = Math.max(maxY, cell.getBlockY());
            minZ = Math.min(minZ, cell.getBlockZ());
            maxZ = Math.max(maxZ, cell.getBlockZ());
        }
        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    private static BlockVector bv(int x, int y, int z) {
        return new BlockVector(x, y, z);
    }
}
