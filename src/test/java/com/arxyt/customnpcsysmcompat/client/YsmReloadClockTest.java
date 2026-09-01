package com.arxyt.customnpcsysmcompat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YsmReloadClockTest {
    @Test
    void slowsReloadWithoutQuantizingFrames() {
        YsmReloadClock clock = new YsmReloadClock();
        assertEquals(100.0F, clock.scaleAbsoluteTime(100.0F));
        clock.target(true, 0.25F);
        assertEquals(100.0F, clock.scaleAbsoluteTime(100.0F));
        assertEquals(100.125F, clock.scaleAbsoluteTime(100.5F));
        assertEquals(105.0F, clock.scaleAbsoluteTime(120.0F));
    }

    @Test
    void leavingReloadRestoresNormalElapsedTime() {
        YsmReloadClock clock = new YsmReloadClock();
        assertEquals(100.0F, clock.scaleAbsoluteTime(100.0F));
        clock.target(true, 0.5F);
        assertEquals(100.0F, clock.scaleAbsoluteTime(100.0F));
        assertEquals(105.0F, clock.scaleAbsoluteTime(110.0F));
        clock.target(false, 1.0F);
        assertEquals(110.0F, clock.scaleAbsoluteTime(110.0F));
    }

    @Test
    void locksSpeedForOneReload() {
        YsmReloadClock clock = new YsmReloadClock();
        clock.scaleAbsoluteTime(0.0F);
        clock.target(true, 0.5F);
        clock.scaleAbsoluteTime(0.0F);
        clock.target(true, 2.0F);
        assertEquals(5.0F, clock.scaleAbsoluteTime(10.0F));
    }
}
