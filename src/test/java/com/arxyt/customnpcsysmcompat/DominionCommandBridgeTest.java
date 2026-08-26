package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that only a genuine Dominion attack queue is allowed to hold TaCZ ADS. */
final class DominionCommandBridgeTest {
    @Test
    void onlyNonEmptyAttackOrdersAreQueuedAttacks() {
        assertTrue(DominionCommandBridge.hasQueuedAttack("attack", 1));
        assertTrue(DominionCommandBridge.hasQueuedAttack("attack", 5));
        assertFalse(DominionCommandBridge.hasQueuedAttack("attack", 0));
        assertFalse(DominionCommandBridge.hasQueuedAttack("hold", 2));
        assertFalse(DominionCommandBridge.hasQueuedAttack("", 2));
    }

    @Test
    void onlyDirectSingleTargetOrdersBypassTargetReaction() {
        assertTrue(DominionCommandBridge.isDirectSingleTargetAttack("attack", true));
        assertFalse(DominionCommandBridge.isDirectSingleTargetAttack("attack", false));
        assertFalse(DominionCommandBridge.isDirectSingleTargetAttack("hold", true));
    }
}
