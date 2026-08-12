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
    void reportsMinecraftYawFromActualMovementDirection() {
        NpcMovementTracker tracker = new NpcMovementTracker();
        tracker.sample(1, 0.0, 0.0);
        assertEquals(-90.0F, tracker.sample(2, 0.2, 0.0).movementYaw(), 0.001F);
        assertEquals(0.0F, tracker.sample(3, 0.2, 0.2).movementYaw(), 0.001F);
        assertEquals(90.0F, tracker.sample(4, 0.0, 0.2).movementYaw(), 0.001F);
        assertEquals(180.0F, Math.abs(tracker.sample(5, 0.0, 0.0).movementYaw()), 0.001F);
    }

    @Test
    void teleportAndLongTrackingGapDoNotTriggerWalking() {
        NpcMovementTracker tracker = new NpcMovementTracker();
        tracker.sample(30, 0.0, 0.0);
        assertFalse(tracker.sample(31, 10.0, 0.0).walking());
        assertFalse(tracker.sample(50, 11.0, 0.0).walking());
    }

    @Test
    void distinguishesBackpedallingFromTurningAndStrafing() {
        assertFalse(new NpcMovementTracker.Sample(true, 0.5F, 0.0F).backpedalling(0.0F));
        assertTrue(new NpcMovementTracker.Sample(true, 0.5F, 180.0F).backpedalling(0.0F));
        assertFalse(new NpcMovementTracker.Sample(true, 0.5F, 99.0F).backpedalling(0.0F));
        assertTrue(new NpcMovementTracker.Sample(true, 0.5F, -170.0F).backpedalling(10.0F));
        assertFalse(NpcMovementTracker.Sample.STOPPED.backpedalling(180.0F));
    }
}
