package com.arxyt.customnpcsysmcompat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YsmReloadClockTest {
    @Test
    void slowsReloadWithoutQuantizingFrames() {
        YsmReloadClock clock = new YsmReloadClock();
        assertEquals(100.0F, clock.scaleElapsed(100.0F));
        clock.target(true, 0.25F);
        assertEquals(0.0F, clock.scaleElapsed(0.0F));
        assertEquals(0.125F, clock.scaleElapsed(0.5F));
        assertEquals(5.0F, clock.scaleElapsed(20.0F));
    }

    @Test
    void leavingReloadRestoresNormalElapsedTime() {
        YsmReloadClock clock = new YsmReloadClock();
        clock.target(true, 0.5F);
        assertEquals(5.0F, clock.scaleElapsed(10.0F));
        clock.target(false, 1.0F);
        assertEquals(10.0F, clock.scaleElapsed(10.0F));
    }

    @Test
    void locksSpeedForOneReload() {
        YsmReloadClock clock = new YsmReloadClock();
        clock.target(true, 0.5F);
        clock.target(true, 2.0F);
        assertEquals(5.0F, clock.scaleElapsed(10.0F));
    }
}
