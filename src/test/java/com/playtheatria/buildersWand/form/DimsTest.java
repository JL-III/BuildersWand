package com.playtheatria.buildersWand.form;

import com.playtheatria.buildersWand.utils.Err;
import com.playtheatria.buildersWand.utils.Ok;
import com.playtheatria.buildersWand.utils.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Dims are refused naming the bound, never clamped (design §6, §17). */
class DimsTest {

    @Test
    void sphereOverCapRefusedNamed530() {
        Result<Dims, IllegalArgumentException> result = Dims.validated(Form.SPHERE, 7, 3, 1);
        Err<Dims, IllegalArgumentException> err = assertInstanceOf(Err.class, result);
        assertEquals("sphere(7,3) is 530 cells; max 512", err.error().getMessage());
    }

    @Test
    void boxPrimary9Refused() {
        Result<Dims, IllegalArgumentException> result = Dims.validated(Form.BOX, 9, 1, 1);
        Err<Dims, IllegalArgumentException> err = assertInstanceOf(Err.class, result);
        assertEquals("box primary 9 exceeds max 8", err.error().getMessage());
    }

    @Test
    void diagonalWidth6Refused() {
        Result<Dims, IllegalArgumentException> result = Dims.validated(Form.DIAGONAL, 1, 6, 1);
        Err<Dims, IllegalArgumentException> err = assertInstanceOf(Err.class, result);
        assertEquals("diagonal secondary 6 exceeds max 5", err.error().getMessage());
    }

    @Test
    void neverClamped() {
        // Refused dims come back as Err — never a silently clamped Ok.
        assertInstanceOf(Err.class, Dims.validated(Form.BOX, 9, 1, 1));
        assertInstanceOf(Err.class, Dims.validated(Form.SPHERE, 7, 3, 1));
        // A legal request is accepted verbatim.
        Ok<Dims, IllegalArgumentException> ok = assertInstanceOf(Ok.class, Dims.validated(Form.BOX, 8, 8, 8));
        assertEquals(new Dims(8, 8, 8), ok.value());
    }
}
