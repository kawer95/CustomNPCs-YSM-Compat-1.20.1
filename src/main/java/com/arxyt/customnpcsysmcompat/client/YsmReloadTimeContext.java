package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;

/** Render-thread scope consumed only by the optional YSM animation-clock mixin. */
public final class YsmReloadTimeContext {
    private static final ThreadLocal<YsmReloadClock> CLOCK = new ThreadLocal<>();

    private YsmReloadTimeContext() {
    }

    static void begin(YsmReloadClock clock) {
        CLOCK.set(clock);
    }

    static void end() {
        CLOCK.remove();
    }

    public static float scaleElapsed(float elapsed) {
        YsmReloadClock clock = CLOCK.get();
        if (clock == null) return elapsed;
        boolean firstHit = clock.active() && !clock.mixinHit();
        float scaled = clock.scaleElapsed(elapsed);
        if (firstHit) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-RELOAD-ANIM] elapsedMixin=HIT speed={} elapsedBefore={} elapsedAfter={}",
                    clock.speed(), elapsed, scaled);
        }
        return scaled;
    }
}
