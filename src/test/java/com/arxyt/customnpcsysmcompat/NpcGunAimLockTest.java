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
    void visualTurnBarrierMatchesClientBodyTurnRateAndWrapsAtOneEighty() {
        assertEquals(0, NpcGunAimLock.visualTurnDelayTicks(20.0F, 20.0F));
        assertEquals(1, NpcGunAimLock.visualTurnDelayTicks(0.0F, 30.0F));
        assertEquals(2, NpcGunAimLock.visualTurnDelayTicks(0.0F, 31.0F));
        assertEquals(1, NpcGunAimLock.visualTurnDelayTicks(170.0F, -170.0F));
        assertEquals(6, NpcGunAimLock.visualTurnDelayTicks(0.0F, 180.0F));
    }
}
