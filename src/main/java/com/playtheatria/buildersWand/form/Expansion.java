package com.playtheatria.buildersWand.form;

import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;

/**
 * The single geometry truth (design §6). Produces cell offsets from the effective anchor in
 * the (L, S/V, N) basis, emitted N-major → S/V → L. Both the ghost and the commit call
 * {@link #cells}; no other class contains cell-membership logic.
 */
public final class Expansion {

    /** Cap on expanded plan cells (kept cells included). Not configurable (design §6). */
    public static final int MAX_WAVE_CELLS = 512;

    private Expansion() {
    }

    /**
     * Cell offsets from the effective anchor, in emission order (design §6.1–§6.5).
     * Throws {@link IllegalArgumentException} if handed dims that {@link Dims#validated}
     * would refuse (the invariant's structural tooth).
     */
    public static List<BlockVector> cells(Form form, Dims dims, Orientation orientation) {
        Dims.validated(form, dims.primary(), dims.secondary(), dims.tertiary())
                .value(); // throws the named IllegalArgumentException on refusal
        BlockVector l = orientation.l();
        BlockVector s = orientation.s();
        BlockVector n = orientation.n();
        List<int[]> abstractCells = abstractCells(form, dims);
        List<BlockVector> out = new ArrayList<>(abstractCells.size());
        for (int[] c : abstractCells) {
            int a = c[0];
            int b = c[1];
            int d = c[2];
            int x = a * l.getBlockX() + b * s.getBlockX() + d * n.getBlockX();
            int y = a * l.getBlockY() + b * s.getBlockY() + d * n.getBlockY();
            int z = a * l.getBlockZ() + b * s.getBlockZ() + d * n.getBlockZ();
            out.add(new BlockVector(x, y, z));
        }
        return out;
    }

    /**
     * Expanded cell count (design §6): closed-form for single/box/diagonal, generated-and-
     * counted for cylinder/sphere/capsule. Does not validate — used by {@link Dims#validated}.
     */
    public static int cellCount(Form form, Dims dims) {
        int p = dims.primary();
        int sSpan = dims.secondary();
        int t = dims.tertiary();
        return switch (form) {
            case SINGLE -> 1;
            case DIAGONAL -> p * sSpan; // run × width (§6.2)
            case BOX -> p * sSpan * t
                    - Math.max(p - 2, 0) * Math.max(sSpan - 2, 0) * Math.max(t - 2, 0); // §6.3
            case CYLINDER -> rim(p).size() * sSpan; // rim × courses (§6.4)
            case SPHERE -> sphereCells(p, sSpan).size(); // generated (§6.5)
        };
    }

    // ---- abstract (a, b, c) generation in emission order (a = L, b = S/V, c = N) ----

    private static List<int[]> abstractCells(Form form, Dims dims) {
        return switch (form) {
            case SINGLE -> List.of(new int[]{0, 0, 0});
            case DIAGONAL -> diagonalCells(dims.primary(), dims.secondary());
            case BOX -> boxCells(dims.primary(), dims.secondary(), dims.tertiary());
            case CYLINDER -> cylinderCells(dims.primary(), dims.secondary());
            case SPHERE -> sphereCells(dims.primary(), dims.secondary());
        };
    }

    private static List<int[]> diagonalCells(int run, int width) {
        // cell = col·L + step·(S/V) + step·N  (design §6.2); N-major means step is the outer loop.
        List<int[]> out = new ArrayList<>(run * width);
        for (int step = 0; step < run; step++) {
            for (int col = 0; col < width; col++) {
                out.add(new int[]{col, step, step});
            }
        }
        return out;
    }

    private static List<int[]> boxCells(int p, int sSpan, int t) {
        // Hollow shell (design §6.3), emitted N-major → S/V → L.
        List<int[]> out = new ArrayList<>();
        for (int c = 0; c < t; c++) {
            for (int b = 0; b < sSpan; b++) {
                for (int a = 0; a < p; a++) {
                    boolean shell = a == 0 || a == p - 1 || b == 0 || b == sSpan - 1 || c == 0 || c == t - 1;
                    if (shell) {
                        out.add(new int[]{a, b, c});
                    }
                }
            }
        }
        return out;
    }

    private static List<int[]> cylinderCells(int step, int courses) {
        // Open tube (design §6.4): the rim per course, N-major.
        List<int[]> ringCells = rim(step);
        List<int[]> out = new ArrayList<>(ringCells.size() * courses);
        for (int c = 0; c < courses; c++) {
            for (int[] ab : ringCells) {
                out.add(new int[]{ab[0], ab[1], c});
            }
        }
        return out;
    }

    private static List<int[]> sphereCells(int step, int length) {
        // One-cell-thick shell around the axis segment {k·N : 0 ≤ k < length} (design §6.5).
        List<int[]> out = new ArrayList<>();
        if (step == 1) {
            for (int c = 0; c < length; c++) {
                out.add(new int[]{0, 0, c});
            }
            return out;
        }
        int r = step - 1;
        int lo = -r;
        int hi = length - 1 + r;
        int inner = (2 * r - 1) * (2 * r - 1);
        int outer = (2 * r + 1) * (2 * r + 1);
        for (int c = lo; c <= hi; c++) {
            int clampedC = Math.max(0, Math.min(length - 1, c));
            int dz = c - clampedC;
            for (int b = -r; b <= r; b++) {
                for (int a = -r; a <= r; a++) {
                    int fourDSq = 4 * (a * a + b * b + dz * dz);
                    if (fourDSq >= inner && fourDSq < outer) {
                        out.add(new int[]{a, b, c});
                    }
                }
            }
        }
        return out;
    }

    /**
     * The integer rim band for a cylinder of the given step (design §6.4). Radius
     * {@code r = step − 1}. Package-visible for tests. Ordered S/V-major then L for
     * deterministic emission.
     */
    static List<int[]> rim(int step) {
        if (step == 1) {
            return List.of(new int[]{0, 0});
        }
        int r = step - 1;
        int inner = (2 * r - 1) * (2 * r - 1);
        int outer = (2 * r + 1) * (2 * r + 1);
        List<int[]> out = new ArrayList<>();
        for (int b = -r; b <= r; b++) {
            for (int a = -r; a <= r; a++) {
                int fourDSq = 4 * (a * a + b * b);
                if (fourDSq >= inner && fourDSq < outer) {
                    out.add(new int[]{a, b});
                }
            }
        }
        return out;
    }
}
