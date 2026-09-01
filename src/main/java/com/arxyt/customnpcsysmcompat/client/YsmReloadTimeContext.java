package com.arxyt.customnpcsysmcompat.client;

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

    public static float transform(float rawTime) {
        YsmReloadClock clock = CLOCK.get();
        return clock == null ? rawTime : clock.transform(rawTime);
    }
}
