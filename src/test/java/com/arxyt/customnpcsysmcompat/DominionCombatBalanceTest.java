package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DominionCombatBalanceTest {
    @Test
    void settingsAndUnavailableBridgeFailClosed() {
        DominionCombatBalance.Settings settings = new DominionCombatBalance.Settings(true, true, true);

        assertTrue(settings.available());
        assertFalse(DominionCombatBalance.Settings.UNAVAILABLE.available());
        assertFalse(DominionCombatBalance.Settings.UNAVAILABLE.targetReactionEnabled());
    }
}
