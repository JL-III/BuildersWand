package com.playtheatria.buildersWand.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormTest {

    @Test
    void newKeysAndLabelsAreStable() {
        assertEquals("extend_surface", Form.EXTEND_SURFACE.key());
        assertEquals("Extend Surface", Form.EXTEND_SURFACE.label());
        assertEquals(Form.EXTEND_SURFACE, Form.fromKey("extend-surface").orElseThrow());
        assertEquals(Form.EXTEND_SURFACE, Form.fromKey("Extend Surface").orElseThrow());
    }

    @Test
    void commonFormsNeedNoIntermediateLockClick() {
        assertEquals(0, Form.WALL.lockStages());
        assertEquals(0, Form.LINE.lockStages());
        assertEquals(0, Form.FLOOR.lockStages());
        assertEquals(0, Form.EXTEND_SURFACE.lockStages());
    }

    @Test
    void onlyEnclosedFormsSupportDensity() {
        assertTrue(Form.BOX.supportsDensity());
        assertTrue(Form.CYLINDER.supportsDensity());
        assertTrue(Form.SPHERE.supportsDensity());
        assertFalse(Form.WALL.supportsDensity());
        assertFalse(Form.LINE.supportsDensity());
    }

    @Test
    void extendSurfaceNamesItsRuntimeDependency() {
        assertFalse(Form.EXTEND_SURFACE.hasParametricExpansion());
        assertTrue(Form.WALL.hasParametricExpansion());
    }
}
