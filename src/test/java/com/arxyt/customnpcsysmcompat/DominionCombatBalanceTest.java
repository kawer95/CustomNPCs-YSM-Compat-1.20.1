package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class DominionCombatBalanceTest {
    @Test
    void settingsClampAccuracyAndUnavailableFailsClosed() {
        DominionCombatBalance.Settings settings = new DominionCombatBalance.Settings(true, true, true, 187);

        assertEquals(100, settings.taczAccuracy());
        assertFalse(DominionCombatBalance.Settings.UNAVAILABLE.available());
        assertFalse(DominionCombatBalance.Settings.UNAVAILABLE.targetReactionEnabled());
    }

    @Test
    void reflectionValueNormalizationKeepsConfiguredRange() {
        assertEquals(0, DominionCombatBalance.clampAccuracy(-2));
        assertEquals(75, DominionCombatBalance.clampAccuracy(75));
        assertEquals(100, DominionCombatBalance.clampAccuracy(101));
        assertEquals(0, DominionCombatBalance.clampAccuracy(null));
    }
}
