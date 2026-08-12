package com.arxyt.customnpcsysmcompat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YsmVerticalAnchorTest {
    @Test
    void removesOnlyDownwardRootTranslationDuringHurt() {
        YsmVerticalAnchor anchor = new YsmVerticalAnchor();
        assertEquals(YsmVerticalAnchor.Update.CALIBRATED, anchor.observe(-1.0D, 1.0D, false));

        anchor.observe(-2.5D, -0.5D, true);
        assertEquals(1.5D, anchor.correction(), 1.0E-6D);
        assertTrue(anchor.anchoring());
    }

    @Test
    void consecutiveHitsKeepAnchorUntilRawRootRecovers() {
        YsmVerticalAnchor anchor = new YsmVerticalAnchor();
        anchor.observe(-1.0D, 1.0D, false);
        anchor.observe(-2.5D, -0.5D, true);
        anchor.observe(-2.5D, -0.5D, false);
        assertTrue(anchor.anchoring());
        assertEquals(1.5D, anchor.correction(), 1.0E-6D);

        anchor.observe(-2.5D, -0.5D, true);
        assertTrue(anchor.anchoring());
        assertEquals(YsmVerticalAnchor.Update.RELEASED, anchor.observe(-1.0D, 1.0D, false));
        assertFalse(anchor.anchoring());
        assertEquals(0.0D, anchor.correction(), 1.0E-6D);
    }

    @Test
    void doesNotPushDownAnUpwardAnimation() {
        YsmVerticalAnchor anchor = new YsmVerticalAnchor();
        anchor.observe(-1.0D, 1.0D, false);
        anchor.observe(-0.5D, 1.5D, true);
        assertEquals(0.0D, anchor.correction(), 1.0E-6D);
    }

    @Test
    void rejectsInvalidSamples() {
        YsmVerticalAnchor anchor = new YsmVerticalAnchor();
        assertEquals(YsmVerticalAnchor.Update.INVALID,
                anchor.observe(Double.NaN, 1.0D, false));
        assertTrue(anchor.needsSample(false));
    }
}
