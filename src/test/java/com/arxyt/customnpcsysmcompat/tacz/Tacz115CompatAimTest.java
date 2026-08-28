package com.arxyt.customnpcsysmcompat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Tacz115CompatAimTest {
    @Test
    void standingMachineGunPenaltyReducesExactLockProbabilityByFiftyPercent() {
        assertEquals(40, Tacz115Compat.effectiveAccuracy(80, true, true, false, false));
        assertEquals(38, Tacz115Compat.effectiveAccuracy(75, true, true, false, false));
        assertEquals(80, Tacz115Compat.effectiveAccuracy(80, false, true, false, false));
        assertEquals(80, Tacz115Compat.effectiveAccuracy(80, true, false, false, false));
        assertEquals(80, Tacz115Compat.effectiveAccuracy(80, true, true, false, true));
    }

    @Test
    void sniperAccuracyUsesStandingAndCrawlingMultipliers() {
        assertEquals(60, Tacz115Compat.effectiveAccuracy(75, true, false, true, false));
        assertEquals(100, Tacz115Compat.effectiveAccuracy(75, true, false, true, true));
        assertEquals(68, Tacz115Compat.effectiveAccuracy(50, true, false, true, true));
    }

    @Test
    void perfectAccuracyAlwaysUsesTheExactTargetAim() {
        assertEquals(0.0F, Tacz115Compat.aimErrorDegrees(100, 0.6D, 12.0D, 99, 1.0D, true));
    }

    @Test
    void rangedAccuracyIsTheChanceOfAnExactTargetAim() {
        assertTrue(Tacz115Compat.isExactAimShot(75, 0));
        assertTrue(Tacz115Compat.isExactAimShot(75, 74));
        assertFalse(Tacz115Compat.isExactAimShot(75, 75));
        assertFalse(Tacz115Compat.isExactAimShot(0, 0));
    }

    @Test
    void nonExactAimUsesVisibleSignedYawOffset() {
        float positive = Tacz115Compat.aimErrorDegrees(0, 0.6D, 12.0D, 0, 0.0D, true);
        float negative = Tacz115Compat.aimErrorDegrees(0, 0.6D, 12.0D, 0, 1.0D, false);

        assertTrue(positive > 2.0F);
        assertTrue(negative < -2.0F);
    }

    @Test
    void activeNpcGunTargetAlwaysRequestsAdsUntilGoalStopsIt() {
        assertTrue(Tacz115Compat.needsAimForTarget(false));
        assertFalse(Tacz115Compat.needsAimForTarget(true));
    }

    @Test
    void queuedDominionAttackKeepsAdsButAnExplicitClearAlwaysReleasesIt() {
        assertFalse(Tacz115Compat.shouldExitAim(true, true, false));
        assertTrue(Tacz115Compat.shouldExitAim(true, false, false));
        assertTrue(Tacz115Compat.shouldExitAim(true, true, true));
        assertFalse(Tacz115Compat.shouldExitAim(false, true, true));
    }

}
