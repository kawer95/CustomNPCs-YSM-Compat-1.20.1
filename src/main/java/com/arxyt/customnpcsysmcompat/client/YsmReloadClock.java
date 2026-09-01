package com.arxyt.customnpcsysmcompat.client;

/** Scales YSM's animation-local elapsed time while TaCZ is reloading. */
final class YsmReloadClock {
    private boolean active;
    private float speed = 1.0F;
    private int activeSyncs;
    private boolean mixinHit;
    private boolean missingMixinReported;

    void target(boolean active, float speed) {
        if (active && !this.active) {
            this.speed = validSpeed(speed);
            activeSyncs = 0;
            mixinHit = false;
            missingMixinReported = false;
        }
        this.active = active;
        if (!active) this.speed = 1.0F;
        if (active) activeSyncs++;
    }

    float scaleElapsed(float elapsed) {
        if (!active) return elapsed;
        mixinHit = true;
        return elapsed * speed;
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

    boolean shouldReportMissingMixin() {
        if (!active || mixinHit || missingMixinReported || activeSyncs < 3) return false;
        missingMixinReported = true;
        return true;
    }

    private static float validSpeed(float speed) {
        return Float.isFinite(speed) && speed > 0.0F ? speed : 1.0F;
    }
}
