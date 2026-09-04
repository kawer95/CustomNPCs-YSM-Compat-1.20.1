package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the target yaw shared by TaCZ's shot solution and CNPC body alignment. */
final class NpcGunAimLockTest {
    @Test
    void targetYawMatchesMinecraftForwardAxes() {
        assertEquals(0.0F, NpcGunAimLock.targetYaw(0.0D, 1.0D), 1.0E-6F);
        assertEquals(-90.0F, NpcGunAimLock.targetYaw(1.0D, 0.0D), 1.0E-6F);
        assertEquals(90.0F, NpcGunAimLock.targetYaw(-1.0D, 0.0D), 1.0E-6F);
        assertEquals(180.0F, Math.abs(NpcGunAimLock.targetYaw(0.0D, -1.0D)), 1.0E-6F);
    }

    @Test
    void zeroOrNonFiniteHorizontalOffsetsDoNotProduceAnOrientation() {
        assertTrue(Float.isNaN(NpcGunAimLock.targetYaw(0.0D, 0.0D)));
        assertTrue(Float.isNaN(NpcGunAimLock.targetYaw(Double.NaN, 1.0D)));
    }

    @Test
    void turnAdvancesInVisibleStepsAndUsesShortestWrappedPath() {
        assertEquals(20.0F, NpcGunAimLock.stepAngle(0.0F, 90.0F, 20.0F), 0.001F);
        assertEquals(40.0F, NpcGunAimLock.stepAngle(20.0F, 90.0F, 20.0F), 0.001F);
        assertEquals(90.0F, NpcGunAimLock.stepAngle(80.0F, 90.0F, 20.0F), 0.001F);
        assertEquals(190.0F, NpcGunAimLock.stepAngle(170.0F, -170.0F, 20.0F), 0.001F);
    }

    @Test
    void fireGateWaitsForBodyHeadAndPitchInsteadOfElapsedTime() {
        assertTrue(NpcGunAimLock.aligned(1.0F, -1.0F, 2.0F, 0.0F, 0.0F));
        org.junit.jupiter.api.Assertions.assertFalse(
                NpcGunAimLock.aligned(3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        org.junit.jupiter.api.Assertions.assertFalse(
                NpcGunAimLock.aligned(0.0F, 3.0F, 0.0F, 0.0F, 0.0F));
        org.junit.jupiter.api.Assertions.assertFalse(
                NpcGunAimLock.aligned(0.0F, 0.0F, 3.0F, 0.0F, 0.0F));
    }
}
