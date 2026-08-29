package com.arxyt.customnpcsysmcompat.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that nested label calls cannot leak the depth-safe scope into later renders. */
final class NpcNameTagRenderContextTest {
    @Test
    void scopeIsActiveOnlyUntilTheMatchingEndCall() {
        assertFalse(NpcNameTagRenderContext.suppressXrayBubblePass());

        NpcNameTagRenderContext.begin();
        assertTrue(NpcNameTagRenderContext.suppressXrayBubblePass());
        NpcNameTagRenderContext.begin();
        NpcNameTagRenderContext.end();
        assertTrue(NpcNameTagRenderContext.suppressXrayBubblePass());

        NpcNameTagRenderContext.end();
        assertFalse(NpcNameTagRenderContext.suppressXrayBubblePass());
    }
}
