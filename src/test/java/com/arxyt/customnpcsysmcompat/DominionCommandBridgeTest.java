package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that Dominion attack and watch queues may hold TaCZ ADS between targets. */
final class DominionCommandBridgeTest {
    @Test
    void onlyNonEmptyAttackOrWatchOrdersAreQueuedAttacks() {
        assertTrue(DominionCommandBridge.hasQueuedAttack("attack", 1));
        assertTrue(DominionCommandBridge.hasQueuedAttack("attack", 5));
        assertTrue(DominionCommandBridge.hasQueuedAttack("watch", 1));
        assertTrue(DominionCommandBridge.hasQueuedAttack("breach", 1));
        assertFalse(DominionCommandBridge.hasQueuedAttack("attack", 0));
        assertFalse(DominionCommandBridge.hasQueuedAttack("watch", 0));
        assertFalse(DominionCommandBridge.hasQueuedAttack("hold", 2));
        assertFalse(DominionCommandBridge.hasQueuedAttack("", 2));
    }

    @Test
    void onlyDirectSingleTargetOrdersBypassTargetReaction() {
        assertTrue(DominionCommandBridge.isDirectSingleTargetAttack("attack", true));
        assertFalse(DominionCommandBridge.isDirectSingleTargetAttack("attack", false));
        assertFalse(DominionCommandBridge.isDirectSingleTargetAttack("hold", true));
    }

    @Test
    void missingOptionalDominionSpeedApiUsesCallerFallback() {
        assertEquals(1.6D, DominionCommandBridge.commandMovementSpeed(null, 1.6D));
    }
}
