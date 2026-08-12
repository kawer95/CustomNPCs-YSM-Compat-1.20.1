package com.arxyt.customnpcsysmcompat.animation;

public final class NpcOrientationTracker {
    private static final float MAX_BODY_TURN_PER_TICK = 30.0F;
    private static final float MAX_HEAD_TURN_PER_TICK = 45.0F;

    private int lastTick = Integer.MIN_VALUE;
    private float bodyYaw;
    private float previousBodyYaw;
    private float headYaw;
    private float previousHeadYaw;

    public Frame sample(int tick, float targetBodyYaw, float targetHeadYaw) {
        if (lastTick == Integer.MIN_VALUE || tick < lastTick || tick - lastTick > 5) {
            lastTick = tick;
            bodyYaw = previousBodyYaw = targetBodyYaw;
            headYaw = previousHeadYaw = targetHeadYaw;
        } else if (tick != lastTick) {
            previousBodyYaw = bodyYaw;
            previousHeadYaw = headYaw;
            bodyYaw = approachDegrees(bodyYaw, targetBodyYaw, MAX_BODY_TURN_PER_TICK);
            headYaw = approachDegrees(headYaw, targetHeadYaw, MAX_HEAD_TURN_PER_TICK);
            lastTick = tick;
        }
        return new Frame(bodyYaw, previousBodyYaw, headYaw, previousHeadYaw);
    }

    public static Frame fixed(float bodyYaw, float headYaw) {
        return new Frame(bodyYaw, bodyYaw, headYaw, headYaw);
    }

    static float approachDegrees(float current, float target, float maximumChange) {
        float difference = wrapDegrees(target - current);
        float change = Math.max(-maximumChange, Math.min(maximumChange, difference));
        return current + change;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public record Frame(float bodyYaw, float previousBodyYaw, float headYaw, float previousHeadYaw) {
        public float interpolatedBodyYaw(float partialTick) {
            float partial = Math.max(0.0F, Math.min(1.0F, partialTick));
            return previousBodyYaw + wrapDegrees(bodyYaw - previousBodyYaw) * partial;
        }
    }
}
