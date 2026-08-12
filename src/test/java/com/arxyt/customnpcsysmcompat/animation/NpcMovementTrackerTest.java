package com.arxyt.customnpcsysmcompat.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcMovementTrackerTest {
    @Test
    void followsPhysicalDisplacementInsteadOfAiIntent() {
        NpcMovementTracker tracker = new NpcMovementTracker();
        assertFalse(tracker.sample(10, 0.0, 0.0).walking());
        assertTrue(tracker.sample(11, 0.2, 0.0).walking());
        assertTrue(tracker.sample(12, 0.205, 0.0).walking());
        assertFalse(tracker.sample(13, 0.205, 0.0).walking());
    }

    @Test
    void repeatedRenderInSameTickDoesNotAdvanceState() {
        NpcMovementTracker tracker = new NpcMovementTracker();
        tracker.sample(20, 1.0, 1.0);
        NpcMovementTracker.Sample moving = tracker.sample(21, 1.2, 1.0);
        assertEquals(moving, tracker.sample(21, 1.2, 1.0));
    }

    @Test
    void teleportAndLongTrackingGapDoNotTriggerWalking() {
        NpcMovementTracker tracker = new NpcMovementTracker();
        tracker.sample(30, 0.0, 0.0);
        assertFalse(tracker.sample(31, 10.0, 0.0).walking());
        assertFalse(tracker.sample(50, 11.0, 0.0).walking());
    }
}
