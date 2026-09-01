package com.arxyt.customnpcsysmcompat.client;

/**
 * Continuous time transform for YSM's fixed-length reload clip.  Transitions preserve the
 * current virtual time so entering or leaving reload cannot jump every other animation layer.
 */
final class YsmReloadClock {
    private boolean targetActive;
    private float targetSpeed = 1.0F;
    private boolean active;
    private boolean initialized;
    private float speed = 1.0F;
    private float rawAnchor;
    private float virtualAnchor;

    void target(boolean active, float speed) {
        targetActive = active;
        if (active && !this.active) targetSpeed = validSpeed(speed);
    }

    float transform(float rawTime) {
        if (!initialized) {
            initialized = true;
            active = targetActive;
            speed = active ? targetSpeed : 1.0F;
            rawAnchor = rawTime;
            virtualAnchor = rawTime;
        } else if (active != targetActive) {
            float current = mapped(rawTime);
            active = targetActive;
            speed = active ? targetSpeed : 1.0F;
            rawAnchor = rawTime;
            virtualAnchor = current;
        }
        return mapped(rawTime);
    }

    private float mapped(float rawTime) {
        return virtualAnchor + (rawTime - rawAnchor) * speed;
    }

    private static float validSpeed(float speed) {
        return Float.isFinite(speed) && speed > 0.0F ? speed : 1.0F;
    }
}
