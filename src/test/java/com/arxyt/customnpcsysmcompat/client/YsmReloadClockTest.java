package com.arxyt.customnpcsysmcompat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YsmReloadClockTest {
    @Test
    void slowsReloadWithoutQuantizingFrames() {
        YsmReloadClock clock = new YsmReloadClock();
        assertEquals(100.0F, clock.transform(100.0F));
        clock.target(true, 0.25F);
        assertEquals(100.0F, clock.transform(100.0F));
        assertEquals(100.125F, clock.transform(100.5F));
        assertEquals(105.0F, clock.transform(120.0F));
    }

    @Test
    void leavingReloadPreservesTimeAndRestoresNormalRate() {
        YsmReloadClock clock = new YsmReloadClock();
        clock.transform(40.0F);
        clock.target(true, 0.5F);
        clock.transform(40.0F);
        assertEquals(45.0F, clock.transform(50.0F));
        clock.target(false, 1.0F);
        assertEquals(45.0F, clock.transform(50.0F));
        assertEquals(46.0F, clock.transform(51.0F));
    }

    @Test
    void locksSpeedForOneReload() {
        YsmReloadClock clock = new YsmReloadClock();
        clock.transform(0.0F);
        clock.target(true, 0.5F);
        clock.transform(0.0F);
        clock.target(true, 2.0F);
        assertEquals(5.0F, clock.transform(10.0F));
    }
}
