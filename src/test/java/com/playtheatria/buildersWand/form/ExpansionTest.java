package com.playtheatria.buildersWand.form;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Golden geometry numbers (design §17). Drift fails the build. */
class ExpansionTest {

    @Test
    void boxShellCounts() {
        assertEquals(26, Expansion.cellCount(Form.BOX, new Dims(3, 3, 3)));
        assertEquals(296, Expansion.cellCount(Form.BOX, new Dims(8, 8, 8)));
        assertEquals(40, Expansion.cellCount(Form.BOX, new Dims(2, 5, 4))); // any extent ≤2 → solid
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
    void diagonalCounts() {
        assertEquals(40, Expansion.cellCount(Form.DIAGONAL, new Dims(8, 5, 1))); // run × width
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

    private static BlockVector bv(int x, int y, int z) {
        return new BlockVector(x, y, z);
    }
}
