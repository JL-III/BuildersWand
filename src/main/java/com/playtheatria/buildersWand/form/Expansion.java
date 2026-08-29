package com.playtheatria.buildersWand.form;

import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The single parametric geometry truth. Produces cell offsets from the effective anchor in a
 * deterministic order shared by preview and commit. Extend Surface is deliberately absent: its
 * connected source membership must be supplied by a bounded world traversal.
 */
public final class Expansion {

    /** Backward-compatible alias for callers not yet wired to {@link FormLimits}. */
    public static final int MAX_WAVE_CELLS = FormLimits.DEFAULT_MAX_CELLS;

    private Expansion() {
    }

    /** Cell offsets using the default Shell density and default limits. */
    public static List<BlockVector> cells(Form form, Dims dims, Orientation orientation) {
        return cells(form, dims, orientation, Density.DEFAULT, FormLimits.DEFAULTS);
    }

    /** Cell offsets using an explicit density and the default limits. */
    public static List<BlockVector> cells(Form form, Dims dims, Orientation orientation, Density density) {
        return cells(form, dims, orientation, density, FormLimits.DEFAULTS);
    }

    /**
     * Cell offsets from the effective anchor using the exact policy inputs configured at startup.
     * Throws a named {@link IllegalArgumentException} on an illegal dimension or cell count.
     */
    public static List<BlockVector> cells(Form form, Dims dims, Orientation orientation,
                                          Density density, FormLimits limits) {
        if (!form.hasParametricExpansion()) {
            throw new UnsupportedOperationException(
                    "extend_surface requires connected live-world traversal; dimensions alone are insufficient");
        }
        Dims.validated(form, dims.primary(), dims.secondary(), dims.tertiary(), density, limits)
                .value();

        Basis basis = basis(form, orientation);
        List<int[]> abstractCells = abstractCells(form, dims, density);
        List<BlockVector> out = new ArrayList<>(abstractCells.size());
        for (int[] c : abstractCells) {
            int a = c[0];
            int b = c[1];
            int d = c[2];
            int x = a * basis.a().getBlockX() + b * basis.b().getBlockX() + d * basis.c().getBlockX();
            int y = a * basis.a().getBlockY() + b * basis.b().getBlockY() + d * basis.c().getBlockY();
            int z = a * basis.a().getBlockZ() + b * basis.b().getBlockZ() + d * basis.c().getBlockZ();
            out.add(new BlockVector(x, y, z));
        }
        return List.copyOf(out);
    }

    /**
     * Bounded expansion for a red illegal preview. Span validation still applies, but the cell
     * cap deliberately does not. When the expansion exceeds {@code previewCellLimit}, the result
     * retains real expansion cells witnessing every minimum and maximum world-axis offset. This
     * lets a bounded preview draw the requested shape's complete AABB instead of an AABB around
     * only the first emission-order slice.
     *
     * <p>The result contains at most {@code previewCellLimit} cells when that budget can hold all
     * unique extent witnesses. For a pathologically tiny budget it may contain up to six cells,
     * one for each coordinate extent; it never expands to the complete over-budget geometry.
     */
    public static List<BlockVector> previewCells(Form form, Dims dims, Orientation orientation,
                                                 Density density, FormLimits limits,
                                                 int previewCellLimit) {
        if (previewCellLimit <= 0) {
            throw new IllegalArgumentException("preview cell limit must be positive");
        }
        if (!form.hasParametricExpansion()) {
            throw new UnsupportedOperationException(
                    "extend_surface requires connected live-world traversal");
        }
        Dims max = limits.maxSpans(form);
        if (dims.primary() < 1 || dims.secondary() < 1 || dims.tertiary() < 1
                || dims.primary() > max.primary()
                || dims.secondary() > max.secondary()
                || dims.tertiary() > max.tertiary()) {
            throw new IllegalArgumentException("dimensions exceed configured " + form.key()
                    + " spans");
        }
        Basis basis = basis(form, orientation);
        List<int[]> abstractCells = abstractCells(form, dims, density);
        int count = Math.min(abstractCells.size(), previewCellLimit);
        List<BlockVector> prefix = new ArrayList<>(count);
        BlockVector minX = null;
        BlockVector maxX = null;
        BlockVector minY = null;
        BlockVector maxY = null;
        BlockVector minZ = null;
        BlockVector maxZ = null;
        for (int index = 0; index < abstractCells.size(); index++) {
            int[] c = abstractCells.get(index);
            int x = c[0] * basis.a().getBlockX() + c[1] * basis.b().getBlockX()
                    + c[2] * basis.c().getBlockX();
            int y = c[0] * basis.a().getBlockY() + c[1] * basis.b().getBlockY()
                    + c[2] * basis.c().getBlockY();
            int z = c[0] * basis.a().getBlockZ() + c[1] * basis.b().getBlockZ()
                    + c[2] * basis.c().getBlockZ();
            BlockVector cell = new BlockVector(x, y, z);
            if (index < count) {
                prefix.add(cell);
            }
            if (minX == null || x < minX.getBlockX()) {
                minX = cell;
            }
            if (maxX == null || x > maxX.getBlockX()) {
                maxX = cell;
            }
            if (minY == null || y < minY.getBlockY()) {
                minY = cell;
            }
            if (maxY == null || y > maxY.getBlockY()) {
                maxY = cell;
            }
            if (minZ == null || z < minZ.getBlockZ()) {
                minZ = cell;
            }
            if (maxZ == null || z > maxZ.getBlockZ()) {
                maxZ = cell;
            }
        }
        if (abstractCells.size() <= previewCellLimit) {
            return List.copyOf(prefix);
        }

        Set<BlockVector> witnesses = new LinkedHashSet<>();
        witnesses.add(minX);
        witnesses.add(maxX);
        witnesses.add(minY);
        witnesses.add(maxY);
        witnesses.add(minZ);
        witnesses.add(maxZ);
        int boundedSize = Math.max(previewCellLimit, witnesses.size());
        LinkedHashSet<BlockVector> sampled = new LinkedHashSet<>(boundedSize);
        sampled.addAll(witnesses);
        for (BlockVector cell : prefix) {
            if (sampled.size() >= boundedSize) {
                break;
            }
            sampled.add(cell);
        }
        return List.copyOf(sampled);
    }

    /** Expanded Shell cell count. Does not validate. */
    public static int cellCount(Form form, Dims dims) {
        return cellCount(form, dims, Density.DEFAULT);
    }

    /** Expanded cell count for the requested density. Does not validate. */
    public static int cellCount(Form form, Dims dims, Density density) {
        int p = dims.primary();
        int sSpan = dims.secondary();
        int t = dims.tertiary();
        return switch (form) {
            case DIAGONAL, WALL, FLOOR -> p * sSpan;
            case LINE -> p;
            case BOX -> density == Density.SOLID
                    ? p * sSpan * t
                    : p * sSpan * t
                    - Math.max(p - 2, 0) * Math.max(sSpan - 2, 0) * Math.max(t - 2, 0);
            case CYLINDER -> (density == Density.SOLID ? disk(p).size() : rim(p).size()) * sSpan;
            case SPHERE -> sphereCells(p, sSpan, density).size();
            case EXTEND_SURFACE -> throw new UnsupportedOperationException(
                    "extend_surface cell count comes from connected live-world traversal");
        };
    }

    // ---- abstract (a, b, c) generation in emission order (a = L, b = S/V, c = N) ----

    private static List<int[]> abstractCells(Form form, Dims dims, Density density) {
        return switch (form) {
            case DIAGONAL -> diagonalCells(dims.primary(), dims.secondary());
            case BOX -> boxCells(dims.primary(), dims.secondary(), dims.tertiary(), density);
            case CYLINDER -> cylinderCells(dims.primary(), dims.secondary(), density);
            case SPHERE -> sphereCells(dims.primary(), dims.secondary(), density);
            case WALL, FLOOR -> rectangleCells(dims.primary(), dims.secondary());
            case LINE -> lineCells(dims.primary());
            case EXTEND_SURFACE -> throw new UnsupportedOperationException(
                    "extend_surface requires connected live-world traversal");
        };
    }

    private static Basis basis(Form form, Orientation orientation) {
        return switch (form) {
            // Wall is world-vertical regardless of which face established the anchor.
            case WALL -> new Basis(orientation.l(), new BlockVector(0, 1, 0), orientation.n());
            // Floor is always horizontal. A wall click uses its horizontal face normal as axis B.
            case FLOOR -> new Basis(orientation.l(), orientation.wall() ? orientation.n() : orientation.s(),
                    new BlockVector(0, 1, 0));
            // Runtime supplies the chosen signed dominant axis in L for horizontal or vertical lines.
            case LINE -> new Basis(orientation.l(), orientation.s(), orientation.n());
            default -> new Basis(orientation.l(), orientation.s(), orientation.n());
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

    private static List<int[]> boxCells(int p, int sSpan, int t, Density density) {
        List<int[]> out = new ArrayList<>(density == Density.SOLID ? p * sSpan * t : 0);
        for (int c = 0; c < t; c++) {
            for (int b = 0; b < sSpan; b++) {
                for (int a = 0; a < p; a++) {
                    boolean shell = a == 0 || a == p - 1 || b == 0 || b == sSpan - 1 || c == 0 || c == t - 1;
                    if (density == Density.SOLID || shell) {
                        out.add(new int[]{a, b, c});
                    }
                }
            }
        }
        return out;
    }

    private static List<int[]> cylinderCells(int step, int courses, Density density) {
        List<int[]> crossSection = density == Density.SOLID ? disk(step) : rim(step);
        List<int[]> out = new ArrayList<>(crossSection.size() * courses);
        for (int c = 0; c < courses; c++) {
            for (int[] ab : crossSection) {
                out.add(new int[]{ab[0], ab[1], c});
            }
        }
        return out;
    }

    private static List<int[]> sphereCells(int step, int length, Density density) {
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
                    boolean inside = fourDSq < outer;
                    if (inside && (density == Density.SOLID || fourDSq >= inner)) {
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

    /** Filled integer disk for a cylinder cross-section, ordered B-major then A. */
    static List<int[]> disk(int step) {
        if (step == 1) {
            return List.of(new int[]{0, 0});
        }
        int r = step - 1;
        int outer = (2 * r + 1) * (2 * r + 1);
        List<int[]> out = new ArrayList<>();
        for (int b = -r; b <= r; b++) {
            for (int a = -r; a <= r; a++) {
                if (4 * (a * a + b * b) < outer) {
                    out.add(new int[]{a, b});
                }
            }
        }
        return out;
    }

    private static List<int[]> rectangleCells(int primary, int secondary) {
        List<int[]> out = new ArrayList<>(primary * secondary);
        for (int b = 0; b < secondary; b++) {
            for (int a = 0; a < primary; a++) {
                out.add(new int[]{a, b, 0});
            }
        }
        return out;
    }

    private static List<int[]> lineCells(int length) {
        List<int[]> out = new ArrayList<>(length);
        for (int a = 0; a < length; a++) {
            out.add(new int[]{a, 0, 0});
        }
        return out;
    }

    private record Basis(BlockVector a, BlockVector b, BlockVector c) {
    }
}
