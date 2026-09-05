package com.arxyt.customnpcsysmcompat.client.render;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoseStackScopeTest {
    @Test
    void removesEveryFrameLeakedByFailedNestedRenderer() {
        AtomicInteger depth = new AtomicInteger(6);
        AtomicInteger pops = new AtomicInteger();

        PoseStackScope.restoreToDepth(2, depth::get, () -> {
            pops.incrementAndGet();
            depth.decrementAndGet();
        });

        assertEquals(2, depth.get());
        assertEquals(4, pops.get());
    }

    @Test
    void detectsRendererPoppingCallerOwnedFrame() {
        AtomicInteger depth = new AtomicInteger(1);
        assertThrows(IllegalStateException.class,
                () -> PoseStackScope.restoreToDepth(2, depth::get, depth::decrementAndGet));
    }
}
