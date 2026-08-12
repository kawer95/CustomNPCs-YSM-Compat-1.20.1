package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
