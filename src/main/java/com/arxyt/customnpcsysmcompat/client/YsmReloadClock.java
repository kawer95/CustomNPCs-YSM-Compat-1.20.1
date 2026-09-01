package com.arxyt.customnpcsysmcompat.client;

/** Produces a continuous virtual YSM clock with scaled deltas while TaCZ is reloading. */
final class YsmReloadClock {
    private boolean active;
    private float speed = 1.0F;
    private boolean mixinHit;
    private boolean initialized;
    private boolean justActivated;
    private float lastRawTime;
    private float virtualTime;

    void target(boolean active, float speed) {
        if (active && !this.active) {
            this.speed = validSpeed(speed);
            mixinHit = false;
            justActivated = true;
        }
        this.active = active;
        if (!active) this.speed = 1.0F;
    }

    float scaleAbsoluteTime(float rawTime) {
        if (!Float.isFinite(rawTime)) return rawTime;
        if (!initialized || rawTime < lastRawTime || rawTime - lastRawTime > 40.0F) {
            initialized = true;
            lastRawTime = rawTime;
            virtualTime = rawTime;
        }
        float delta = Math.max(0.0F, rawTime - lastRawTime);
        lastRawTime = rawTime;
        if (!active) {
            virtualTime = rawTime;
            return rawTime;
        }
        mixinHit = true;
        if (justActivated) {
            justActivated = false;
            virtualTime = rawTime;
        } else {
            virtualTime += delta * speed;
        }
        return virtualTime;
    }

    boolean active() {
        return active;
    }

    float speed() {
        return speed;
    }

    boolean mixinHit() {
        return mixinHit;
    }

    private static float validSpeed(float speed) {
        return Float.isFinite(speed) && speed > 0.0F ? speed : 1.0F;
    }
}
