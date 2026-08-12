package com.arxyt.customnpcsysmcompat.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcOrientationTrackerTest {
    @Test
    void advancesOnlyOncePerTickAndKeepsPreviousYawCoherent() {
        NpcOrientationTracker tracker = new NpcOrientationTracker();
        tracker.sample(10, 0.0F, 0.0F);
        NpcOrientationTracker.Frame turned = tracker.sample(11, 90.0F, 90.0F);
        assertEquals(30.0F, turned.bodyYaw(), 0.001F);
        assertEquals(0.0F, turned.previousBodyYaw(), 0.001F);
        assertEquals(turned, tracker.sample(11, -90.0F, -90.0F));
    }

    @Test
    void takesShortestPathAcrossDegreeWrap() {
        NpcOrientationTracker tracker = new NpcOrientationTracker();
        tracker.sample(1, 170.0F, 170.0F);
        NpcOrientationTracker.Frame frame = tracker.sample(2, -170.0F, -170.0F);
        assertEquals(190.0F, frame.bodyYaw(), 0.001F);
        assertEquals(180.0F, frame.interpolatedBodyYaw(0.5F), 0.001F);
    }
}
