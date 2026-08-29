package com.playtheatria.buildersWand.ghost;

import com.playtheatria.buildersWand.form.Form;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostServiceStatusTest {

    @Test
    void affordableWaterPremiumIsInformationNotCaution() {
        assertEquals(GhostService.PreviewStatus.READY,
                GhostService.previewStatus(false, false, true));
        assertEquals(GhostService.PreviewStatus.READY,
                GhostService.previewStatus(false, false, false));
    }

    @Test
    void waterShortageAndHardFailureKeepTheirAdmissionStatus() {
        assertEquals(GhostService.PreviewStatus.PARTIAL,
                GhostService.previewStatus(false, true, true));
        assertEquals(GhostService.PreviewStatus.BLOCKED,
                GhostService.previewStatus(true, false, true));
    }

    @Test
    void anchoredInvalidPaletteShowsItsActualSelectionProblem() {
        assertEquals("Water buckets cannot be mixed with building blocks.",
                GhostService.anchoredPlanFailure(Form.BOX, false,
                        "Water buckets cannot be mixed with building blocks."));
    }

    @Test
    void anchoredExtendFailureExplainsThatTheSourceMustBeAnchoredAgain() {
        assertEquals("The source surface changed. Cancel and anchor it again.",
                GhostService.anchoredPlanFailure(Form.EXTEND_SURFACE, true, ""));
    }

    @Test
    void anchoredGeometryFailureDoesNotClaimThatMaterialsAreMissing() {
        assertEquals("The current print could not be planned. Cancel and anchor it again.",
                GhostService.anchoredPlanFailure(Form.BOX, true, ""));
    }

    @Test
    void limitOutlineAlwaysIncludesTheStoredAnchorOutsideATruncatedLowerSample() {
        List<BlockVector> lowerSample = List.of(
                new BlockVector(-4, 20, -6), new BlockVector(8, 25, 10));
        BlockVector originalAnchor = new BlockVector(8, 40, 10);

        Set<BlockVector> outline = GhostService.boundingOutline(lowerSample, originalAnchor);

        assertTrue(outline.contains(originalAnchor));
        assertTrue(outline.contains(new BlockVector(-4, 20, -6)));
        assertTrue(outline.size() <= 152);
    }
}
