package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the deterministic boundary between CNPC animation selection and TaCZ stance requests. */
final class NpcCrawlStateTest {
    @Test
    void onlyTheNativeCrawlAnimationSelectsProneRendering() {
        assertTrue(NpcCrawlState.isCrawling(NpcCrawlState.CRAWL_ANIMATION));
        assertFalse(NpcCrawlState.isCrawling(0));
        assertFalse(NpcCrawlState.isCrawling(2));
    }

    @Test
    void taczReceivesACrawlRequestOnlyForAnActiveTaczGunNpc() {
        assertTrue(NpcCrawlState.requestsTaczCrawl(NpcCrawlState.CRAWL_ANIMATION, true));
        assertFalse(NpcCrawlState.requestsTaczCrawl(NpcCrawlState.CRAWL_ANIMATION, false));
        assertFalse(NpcCrawlState.requestsTaczCrawl(0, true));
    }
}
