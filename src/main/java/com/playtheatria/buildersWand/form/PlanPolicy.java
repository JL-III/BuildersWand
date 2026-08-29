package com.playtheatria.buildersWand.form;

import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Central pure admission policy for semantic spans, expanded cells, and touched chunks. No world
 * lookup occurs here; callers pass either generated offsets plus an anchor or final absolute cells.
 */
public final class PlanPolicy {

    private final FormLimits limits;

    public PlanPolicy(FormLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public FormLimits limits() {
        return limits;
    }

    /** Evaluates dimensions and candidate scan size; touched chunks are not yet known. */
    public PlanDecision evaluateDimensions(Form form, Dims dims, Density density) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(dims, "dims");
        Objects.requireNonNull(density, "density");

        PlanDecision spanDecision = evaluateSpans(form, dims);
        if (!spanDecision.allowed()) {
            return spanDecision;
        }
        if (!form.hasParametricExpansion()) {
            return PlanDecision.allowed(0, 0);
        }

        int cells = Expansion.cellCount(form, dims, density);
        if (cells <= 0) {
            return PlanDecision.refused(cells < 0 ? 0 : cells, 0, LimitKind.GEOMETRY,
                    form.key() + " expansion has no cells");
        }
        if (cells > limits.maxScannedCellsPerPlan()) {
            return PlanDecision.refused(cells, 0, LimitKind.SCANNED_CELLS,
                    dimensions(form, dims) + " expands to " + cells
                            + " candidate cells; scan max " + limits.maxScannedCellsPerPlan());
        }
        return PlanDecision.allowed(cells, 0);
    }

    /** Generates a parametric plan and evaluates all span, cell, and chunk guards. */
    public PlanDecision evaluateGenerated(Form form, Dims dims, Density density,
                                          BlockVector absoluteAnchor, Orientation orientation) {
        Objects.requireNonNull(absoluteAnchor, "absoluteAnchor");
        Objects.requireNonNull(orientation, "orientation");
        if (!form.hasParametricExpansion()) {
            return PlanDecision.refused(0, 0, LimitKind.GEOMETRY,
                    "extend_surface requires cells from connected live-world traversal");
        }
        PlanDecision dimensionDecision = evaluateDimensions(form, dims, density);
        if (!dimensionDecision.allowed()) {
            return dimensionDecision;
        }
        List<BlockVector> offsets = Expansion.cells(form, dims, orientation, density, limits);
        return evaluateOffsets(form, dims, density, absoluteAnchor, offsets);
    }

    /** Evaluates relative offsets after translating them by an absolute world-cell anchor. */
    public PlanDecision evaluateOffsets(Form form, Dims dims, Density density,
                                        BlockVector absoluteAnchor, Collection<BlockVector> offsets) {
        Objects.requireNonNull(absoluteAnchor, "absoluteAnchor");
        Objects.requireNonNull(offsets, "offsets");
        List<BlockVector> absoluteCells = new ArrayList<>(offsets.size());
        for (BlockVector offset : offsets) {
            Objects.requireNonNull(offset, "offset cell");
            absoluteCells.add(new BlockVector(
                    absoluteAnchor.getBlockX() + offset.getBlockX(),
                    absoluteAnchor.getBlockY() + offset.getBlockY(),
                    absoluteAnchor.getBlockZ() + offset.getBlockZ()));
        }
        return evaluateAbsoluteCells(form, dims, density, absoluteCells);
    }

    /**
     * Evaluates an exact complete plan. Extend Surface should pass its bounded traversal result to
     * this method; all parametric forms additionally verify that no cell was lost or duplicated.
     */
    public PlanDecision evaluateAbsoluteCells(Form form, Dims dims, Density density,
                                              Collection<BlockVector> absoluteCells) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(dims, "dims");
        Objects.requireNonNull(density, "density");
        Objects.requireNonNull(absoluteCells, "absoluteCells");

        PlanDecision spanDecision = evaluateSpans(form, dims);
        if (!spanDecision.allowed()) {
            return spanDecision;
        }

        int expectedCells = -1;
        if (form.hasParametricExpansion()) {
            expectedCells = Expansion.cellCount(form, dims, density);
            if (expectedCells > limits.maxScannedCellsPerPlan()) {
                return PlanDecision.refused(expectedCells, 0, LimitKind.SCANNED_CELLS,
                        dimensions(form, dims) + " expands to " + expectedCells
                                + " candidate cells; scan max "
                                + limits.maxScannedCellsPerPlan());
            }
        }

        int actualCells = absoluteCells.size();
        if (actualCells == 0) {
            return PlanDecision.refused(0, 0, LimitKind.GEOMETRY,
                    form.key() + " expansion has no cells");
        }
        if (actualCells > limits.maxScannedCellsPerPlan()) {
            return PlanDecision.refused(actualCells, 0, LimitKind.SCANNED_CELLS,
                    form.key() + " expansion has " + actualCells
                            + " candidate cells; scan max "
                            + limits.maxScannedCellsPerPlan());
        }

        if (form.hasParametricExpansion()) {
            if (actualCells != expectedCells) {
                return PlanDecision.refused(actualCells, 0, LimitKind.GEOMETRY,
                        form.key() + " expansion expected " + expectedCells + " cells but received " + actualCells);
            }
        }

        Set<CellKey> uniqueCells = new HashSet<>(actualCells);
        Set<ChunkKey> chunks = new HashSet<>();
        for (BlockVector cell : absoluteCells) {
            Objects.requireNonNull(cell, "absolute cell");
            CellKey cellKey = new CellKey(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
            if (!uniqueCells.add(cellKey)) {
                return PlanDecision.refused(actualCells, chunks.size(), LimitKind.GEOMETRY,
                        form.key() + " expansion contains duplicate cell " + cellKey);
            }
            chunks.add(new ChunkKey(Math.floorDiv(cellKey.x(), 16), Math.floorDiv(cellKey.z(), 16)));
        }

        int touchedChunks = chunks.size();
        if (touchedChunks > limits.maxChunksPerPrint()) {
            return PlanDecision.refused(actualCells, touchedChunks, LimitKind.CHUNKS,
                    form.key() + " touches " + touchedChunks + " chunks; max " + limits.maxChunksPerPrint());
        }
        return PlanDecision.allowed(actualCells, touchedChunks);
    }

    /**
     * Evaluates the cells that would actually mutate after live world-state filtering. Existing
     * or non-replaceable ordinary cells are deliberately absent: they cost no inventory, Uses,
     * ghost entity, or placement work and therefore must not consume the operational limit.
     */
    public PlanDecision evaluatePlacementCount(Form form, int printableCells) {
        Objects.requireNonNull(form, "form");
        if (printableCells < 0) {
            return PlanDecision.refused(0, 0, LimitKind.GEOMETRY,
                    "printable cell count cannot be negative");
        }
        if (printableCells > limits.maxCellsPerPrint()) {
            return PlanDecision.refused(printableCells, 0, LimitKind.CELLS,
                    form.key() + " would place " + printableCells + " cells; max "
                            + limits.maxCellsPerPrint());
        }
        return PlanDecision.allowed(printableCells, 0);
    }

    private PlanDecision evaluateSpans(Form form, Dims dims) {
        int[] values = {dims.primary(), dims.secondary(), dims.tertiary()};
        String[] names = {"primary", "secondary", "tertiary"};
        for (int axis = 0; axis < values.length; axis++) {
            int value = values[axis];
            if (value < 1) {
                return PlanDecision.refused(0, 0, LimitKind.DIMENSION,
                        form.key() + " " + names[axis] + " " + value + " below min 1");
            }
            int max = limits.maxSpan(form, axis);
            if (value > max) {
                return PlanDecision.refused(0, 0, LimitKind.DIMENSION,
                        form.key() + " " + names[axis] + " " + value + " exceeds max " + max);
            }
        }
        return PlanDecision.allowed(0, 0);
    }

    private static String dimensions(Form form, Dims dims) {
        int count = form.dimensionCount();
        StringBuilder out = new StringBuilder(form.key()).append('(').append(dims.primary());
        if (count >= 2) {
            out.append(',').append(dims.secondary());
        }
        if (count >= 3) {
            out.append(',').append(dims.tertiary());
        }
        return out.append(')').toString();
    }

    private record CellKey(int x, int y, int z) {
    }

    private record ChunkKey(int x, int z) {
    }
}
