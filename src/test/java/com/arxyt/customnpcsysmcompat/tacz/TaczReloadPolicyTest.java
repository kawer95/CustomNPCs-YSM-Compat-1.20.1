package com.arxyt.customnpcsysmcompat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TaczReloadPolicyTest {
    private static final TaczReloadPolicy.Schedule DEFAULTS = TaczReloadPolicy.schedule(10, 20, 30);

    @Test
    void threeQuietTimeTiersUseStrictMagazineThresholds() {
        assertTrue(TaczReloadPolicy.shouldAutoReload(49, 100, 200, DEFAULTS));
        assertFalse(TaczReloadPolicy.shouldAutoReload(50, 100, 200, DEFAULTS));
        assertTrue(TaczReloadPolicy.shouldAutoReload(65, 100, 400, DEFAULTS));
        assertFalse(TaczReloadPolicy.shouldAutoReload(67, 100, 400, DEFAULTS));
        assertTrue(TaczReloadPolicy.shouldAutoReload(99, 100, 600, DEFAULTS));
        assertFalse(TaczReloadPolicy.shouldAutoReload(100, 100, 600, DEFAULTS));
    }

    @Test
    void autoReloadNeverBeginsBeforeTheRelevantQuietPeriod() {
        assertFalse(TaczReloadPolicy.shouldAutoReload(20, 100, 199, DEFAULTS));
        assertFalse(TaczReloadPolicy.shouldAutoReload(60, 100, 399, DEFAULTS));
        assertFalse(TaczReloadPolicy.shouldAutoReload(90, 100, 599, DEFAULTS));
    }

    @Test
    void invalidLaterConfigurationValuesAreNormalizedUpward() {
        TaczReloadPolicy.Schedule schedule = TaczReloadPolicy.schedule(10, 3, 10);
        assertEquals(10, schedule.belowHalfSeconds());
        assertEquals(11, schedule.belowTwoThirdsSeconds());
        assertEquals(12, schedule.nonFullSeconds());
    }
}
