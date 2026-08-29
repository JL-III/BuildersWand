package com.playtheatria.buildersWand.form;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPolicyTest {

    private final PlanPolicy policy = new PlanPolicy(FormLimits.DEFAULTS);

    @Test
    void exactCandidateScanBoundaryAllowedAndNextPlanRefused() {
        FormLimits oneThousandTwentyFourScans = FormLimits.DEFAULTS.toBuilder()
                .maxScannedCellsPerPlan(1_024)
                .build();
        PlanPolicy scanPolicy = new PlanPolicy(oneThousandTwentyFourScans);

        PlanDecision exact = scanPolicy.evaluateDimensions(
                Form.WALL, new Dims(32, 32, 1), Density.SHELL);
        assertTrue(exact.allowed());
        assertEquals(1_024, exact.metrics().expandedCells());

        PlanDecision over = scanPolicy.evaluateDimensions(
                Form.BOX, new Dims(16, 16, 16), Density.SHELL);
        assertFalse(over.allowed());
        assertEquals(1_352, over.metrics().expandedCells());
        assertEquals(LimitKind.SCANNED_CELLS, over.violation().orElseThrow().kind());
        assertEquals("box(16,16,16) expands to 1352 candidate cells; scan max 1024", over.message());
    }

    @Test
    void placementBoundaryIsAppliedOnlyAfterWorldFiltering() {
        assertTrue(policy.evaluatePlacementCount(Form.WALL, 1_024).allowed());

        PlanDecision over = policy.evaluatePlacementCount(Form.WALL, 1_025);
        assertFalse(over.allowed());
        assertEquals(1_025, over.metrics().expandedCells());
        assertEquals(LimitKind.CELLS, over.violation().orElseThrow().kind());
        assertEquals("wall would place 1025 cells; max 1024", over.message());
    }

    @Test
    void candidatesOverPlacementBudgetRemainValidUntilPrintableCountIsKnown() {
        Dims dims = new Dims(16, 16, 16);
        PlanDecision candidateDecision = policy.evaluateDimensions(Form.BOX, dims, Density.SHELL);
        assertTrue(candidateDecision.allowed());
        assertEquals(1_352, candidateDecision.metrics().expandedCells());

        assertTrue(policy.evaluatePlacementCount(Form.BOX, 467).allowed());
        PlanDecision allPrintable = policy.evaluatePlacementCount(Form.BOX, 1_352);
        assertFalse(allPrintable.allowed());
        assertEquals(LimitKind.CELLS, allPrintable.violation().orElseThrow().kind());
    }

    @Test
    void spanFailurePrecedesCellEvaluation() {
        PlanDecision decision = policy.evaluateDimensions(Form.LINE, new Dims(65, 1, 1), Density.SHELL);
        assertFalse(decision.allowed());
        assertEquals(LimitKind.DIMENSION, decision.violation().orElseThrow().kind());
        assertEquals("line primary 65 exceeds max 64", decision.message());
    }

    @Test
    void generatedLineCountsChunksAtAnUnfriendlyAlignment() {
        Orientation east = new Orientation(
                new BlockVector(1, 0, 0), new BlockVector(0, 1, 0), new BlockVector(0, 0, 1),
                true, BlockFace.NORTH);
        PlanDecision decision = policy.evaluateGenerated(
                Form.LINE, new Dims(64, 1, 1), Density.SHELL,
                new BlockVector(15, 70, 0), east);
        assertTrue(decision.allowed());
        assertEquals(64, decision.metrics().expandedCells());
        assertEquals(5, decision.metrics().touchedChunks());
    }

    @Test
    void chunkBoundaryIsExactForTraversalSuppliedCells() {
        List<BlockVector> nineChunks = chunkSamples(9);
        PlanDecision exact = policy.evaluateAbsoluteCells(
                Form.EXTEND_SURFACE, new Dims(64, 1, 1), Density.SHELL, nineChunks);
        assertTrue(exact.allowed());
        assertEquals(9, exact.metrics().touchedChunks());

        List<BlockVector> tenChunks = chunkSamples(10);
        PlanDecision over = policy.evaluateAbsoluteCells(
                Form.EXTEND_SURFACE, new Dims(64, 1, 1), Density.SHELL, tenChunks);
        assertFalse(over.allowed());
        assertEquals(LimitKind.CHUNKS, over.violation().orElseThrow().kind());
        assertEquals("extend_surface touches 10 chunks; max 9", over.message());
    }

    @Test
    void negativeCoordinatesUseMinecraftFloorChunks() {
        FormLimits oneChunk = FormLimits.DEFAULTS.toBuilder().maxChunksPerPrint(1).build();
        PlanDecision decision = new PlanPolicy(oneChunk).evaluateAbsoluteCells(
                Form.EXTEND_SURFACE, new Dims(2, 1, 1), Density.SHELL,
                List.of(new BlockVector(-1, 70, 0), new BlockVector(0, 70, 0)));
        assertFalse(decision.allowed());
        assertEquals(2, decision.metrics().touchedChunks());
        assertEquals("extend_surface touches 2 chunks; max 1", decision.message());
    }

    @Test
    void parameterizedPlansCannotLoseOrDuplicateCells() {
        PlanDecision lost = policy.evaluateAbsoluteCells(
                Form.LINE, new Dims(3, 1, 1), Density.SHELL,
                List.of(new BlockVector(0, 0, 0), new BlockVector(1, 0, 0)));
        assertFalse(lost.allowed());
        assertEquals("line expansion expected 3 cells but received 2", lost.message());

        PlanDecision duplicate = policy.evaluateAbsoluteCells(
                Form.LINE, new Dims(2, 1, 1), Density.SHELL,
                List.of(new BlockVector(0, 0, 0), new BlockVector(0, 0, 0)));
        assertFalse(duplicate.allowed());
        assertEquals(LimitKind.GEOMETRY, duplicate.violation().orElseThrow().kind());
        assertEquals("line expansion contains duplicate cell CellKey[x=0, y=0, z=0]", duplicate.message());
    }

    @Test
    void densityAffectsCandidatesWithoutPrematurePlacementRefusal() {
        assertTrue(policy.evaluateDimensions(Form.BOX, new Dims(10, 10, 10), Density.SHELL).allowed());
        PlanDecision solid = policy.evaluateDimensions(Form.BOX, new Dims(10, 10, 10), Density.SOLID);
        assertTrue(solid.allowed());
        assertEquals(1_000, solid.metrics().expandedCells());
        assertTrue(policy.evaluatePlacementCount(Form.BOX, 1_000).allowed());
    }

    private static List<BlockVector> chunkSamples(int count) {
        List<BlockVector> cells = new ArrayList<>(count);
        for (int chunk = 0; chunk < count; chunk++) {
            cells.add(new BlockVector(chunk * 16, 70, 0));
        }
        return cells;
    }
}
