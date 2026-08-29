package com.playtheatria.buildersWand.wave;

import com.playtheatria.buildersWand.form.Form;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanOptionsTest {

    @Test
    void ordinaryFactoryKeepsLegacyPaletteSemanticsWithoutConfirmationFee() {
        PlanOptions options = PlanOptions.ordinary(Form.WALL);

        assertEquals(PlanOptions.Kind.ORDINARY, options.kind());
        assertEquals("Wall", options.label());
        assertEquals("ordinary:wall", options.contentBinding());
        assertTrue(options.livePaletteRequired());
        assertFalse(options.strictExistingStates());
        assertFalse(options.alwaysConfirm());
        assertEquals(0, options.activationUses());
        assertEquals(0, options.confirmationSeconds());
    }

    @Test
    void prefabFactoryEnablesStrictMandatoryConfirmationAndCopiesClearanceList() {
        List<BlockVector> sourceClearance = new ArrayList<>();
        sourceClearance.add(new BlockVector(1, 2, 3));

        PlanOptions options = PlanOptions.prefab("Starter House", "prefab:house:v1:hash",
                20, sourceClearance, true, 30);
        sourceClearance.clear();

        assertEquals(PlanOptions.Kind.PREFAB, options.kind());
        assertFalse(options.livePaletteRequired());
        assertTrue(options.strictExistingStates());
        assertTrue(options.alwaysConfirm());
        assertTrue(options.refuseLivingEntitiesAtStart());
        assertEquals(20, options.activationUses());
        assertEquals(List.of(new BlockVector(1, 2, 3)), options.clearanceCells());
        assertThrows(UnsupportedOperationException.class,
                () -> options.clearanceCells().add(new BlockVector()));
    }

    @Test
    void rejectsBlankBindingsNegativeFeesAndNonPositiveConfirmedExpiry() {
        assertThrows(IllegalArgumentException.class,
                () -> PlanOptions.prefab(" ", "binding", 1, List.of(), true, 30));
        assertThrows(IllegalArgumentException.class,
                () -> PlanOptions.prefab("House", " ", 1, List.of(), true, 30));
        assertThrows(IllegalArgumentException.class,
                () -> PlanOptions.prefab("House", "binding", -1, List.of(), true, 30));
        assertThrows(IllegalArgumentException.class,
                () -> PlanOptions.prefab("House", "binding", 1, List.of(), true, 0));
    }
}
