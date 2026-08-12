package com.arxyt.customnpcsysmcompat.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeleeAttackSyncTest {
    @Test
    void advancesAllSwingFieldsAsOneContinuousTimeline() {
        MeleeAttackSync.Sample start = MeleeAttackSync.timeline(0, 0.5F);
        assertTrue(start.active());
        assertEquals(0, start.swingTime());
        assertEquals(0.0F, start.previousProgress(), 0.0001F);
        assertEquals(1.0F / 6.0F, start.currentProgress(), 0.0001F);
        assertEquals(1.0F / 12.0F, start.interpolatedProgress(), 0.0001F);

        MeleeAttackSync.Sample end = MeleeAttackSync.timeline(5, 1.0F);
        assertTrue(end.active());
        assertEquals(5, end.swingTime());
        assertEquals(1.0F, end.currentProgress(), 0.0001F);
        assertFalse(MeleeAttackSync.timeline(6, 0.0F).active());
    }
}
