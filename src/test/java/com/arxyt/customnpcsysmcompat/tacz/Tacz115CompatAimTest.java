package com.arxyt.customnpcsysmcompat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Tacz115CompatAimTest {
    @Test
    void perfectAccuracyNeverAddsAimError() {
        assertEquals(0.0F, Tacz115Compat.aimErrorDegrees(100, 0.6D, 12.0D, 99, 1.0D, true));
    }

    @Test
    void inaccurateAimUsesVisibleSignedYawOffset() {
        float positive = Tacz115Compat.aimErrorDegrees(0, 0.6D, 12.0D, 0, 0.0D, true);
        float negative = Tacz115Compat.aimErrorDegrees(0, 0.6D, 12.0D, 0, 1.0D, false);

        assertTrue(positive > 2.0F);
        assertTrue(negative < -2.0F);
    }
}
