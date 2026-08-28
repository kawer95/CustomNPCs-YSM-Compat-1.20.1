package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandGunTacticsTest {
    @Test
    void pursuesUntilConfiguredNpcRangeAndLineOfSightAreSatisfied() {
        assertEquals(CommandGunTactics.Maneuver.PURSUE,
                CommandGunTactics.decide(true, 41.0D, 40.0D, false));
        assertEquals(CommandGunTactics.Maneuver.PURSUE,
                CommandGunTactics.decide(false, 20.0D, 40.0D, false));
        assertEquals(CommandGunTactics.Maneuver.HOLD,
                CommandGunTactics.decide(true, 20.0D, 40.0D, false));
    }

    @Test
    void retreatsInsideTenBlocksButCqbOverridesRetreat() {
        assertEquals(CommandGunTactics.Maneuver.RETREAT,
                CommandGunTactics.decide(true, 9.99D, 32.0D, false));
        assertEquals(CommandGunTactics.Maneuver.HOLD,
                CommandGunTactics.decide(true, 9.99D, 32.0D, true));
        assertEquals(CommandGunTactics.Maneuver.HOLD,
                CommandGunTactics.decide(true, 10.0D, 32.0D, false));
    }

    @Test
    void sentryMayShootButNeverReceivesMovement() {
        assertEquals(CommandGunTactics.Maneuver.SENTRY,
                CommandGunTactics.decideControlled(false, true, 5.0D, 32.0D, false));
        assertEquals(CommandGunTactics.Maneuver.SENTRY,
                CommandGunTactics.decideControlled(false, false, 40.0D, 32.0D, false));
    }

    @Test
    void onlyIdleMovementLockedCommandStateIsAStationarySentry() {
        DominionCommandBridge.Snapshot hold = new DominionCommandBridge.Snapshot(
                true, false, true, false, false, false, false, null);
        DominionCommandBridge.Snapshot move = new DominionCommandBridge.Snapshot(
                true, true, false, false, false, false, false, null);
        assertEquals(true, hold.stationarySentry());
        assertEquals(false, move.stationarySentry());
    }

    @Test
    void proneCommandAttackHoldsPositionButFiresAtAnyVisibleDistance() {
        assertEquals(CommandGunTactics.Maneuver.HOLD,
                CommandGunTactics.decideControlled(true, false, 80.0D, 32.0D, false, true));
        assertEquals(true, CommandGunTactics.canFire(true, true, 80.0D, 32.0D));
        assertEquals(false, CommandGunTactics.canFire(true, false, 4.0D, 32.0D));
        assertEquals(false, CommandGunTactics.canFire(false, true, 80.0D, 32.0D));
    }

    @Test
    void watchUsesItsMultiPointRayResultForEveryFiringGoal() {
        assertTrue(CommandGunTactics.effectiveLineOfSight(true, false, true));
        assertFalse(CommandGunTactics.effectiveLineOfSight(true, true, false));
        assertTrue(CommandGunTactics.effectiveLineOfSight(false, true, false));
        assertFalse(CommandGunTactics.effectiveLineOfSight(false, false, true));
    }
}
