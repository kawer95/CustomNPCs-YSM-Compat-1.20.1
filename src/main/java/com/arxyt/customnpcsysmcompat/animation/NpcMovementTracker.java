package com.arxyt.customnpcsysmcompat.animation;

public final class NpcMovementTracker {
    private static final double WALK_START_DISTANCE = 0.01D;
    private static final double WALK_STOP_DISTANCE = 0.003D;
    private static final double TELEPORT_DISTANCE = 1.0D;

    private int lastTick = Integer.MIN_VALUE;
    private double lastX;
    private double lastZ;
    private boolean moving;
    private boolean backpedalling;
    private int movingTicks;
    private int stoppedTicks;
    private float speed;
    private float movementYaw;

    public Sample sample(int tick, double x, double z) {
        if (lastTick == Integer.MIN_VALUE || tick < lastTick || tick - lastTick > 5) {
            reset(tick, x, z);
            return Sample.STOPPED;
        }
        if (tick == lastTick) {
            return new Sample(moving, speed, movementYaw);
        }

        int elapsedTicks = tick - lastTick;
        double dx = x - lastX;
        double dz = z - lastZ;
        double distancePerTick = Math.sqrt(dx * dx + dz * dz) / elapsedTicks;
        lastTick = tick;
        lastX = x;
        lastZ = z;

        if (distancePerTick > TELEPORT_DISTANCE) {
            moving = false;
            speed = 0.0F;
            movingTicks = 0;
            stoppedTicks = 0;
        } else {
            if (distancePerTick > WALK_START_DISTANCE) {
                movingTicks++;
                stoppedTicks = 0;
                if (!moving && movingTicks >= 2) moving = true;
            } else if (distancePerTick <= WALK_STOP_DISTANCE) {
                stoppedTicks++;
                movingTicks = 0;
                if (moving && stoppedTicks >= 3) moving = false;
            } else {
                movingTicks = 0;
                stoppedTicks = 0;
            }
            speed = moving ? clamp((float) distancePerTick * 4.0F, 0.1F, 1.0F) : 0.0F;
            if (moving) {
                movementYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }
        }
        return new Sample(moving, speed, movementYaw);
    }

    private void reset(int tick, double x, double z) {
        lastTick = tick;
        lastX = x;
        lastZ = z;
        moving = false;
        backpedalling = false;
        movingTicks = 0;
        stoppedTicks = 0;
        speed = 0.0F;
        movementYaw = 0.0F;
    }

    /** 20 degree hysteresis prevents packet jitter around the reverse-motion boundary. */
    public boolean backpedalling(Sample sample, float facingYaw) {
        if (!sample.walking()) {
            backpedalling = false;
            return false;
        }
        float difference = Math.abs(wrapDegrees(sample.movementYaw() - facingYaw));
        if (backpedalling) {
            if (difference < 90.0F) backpedalling = false;
        } else if (difference > 110.0F) {
            backpedalling = true;
        }
        return backpedalling;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public record Sample(boolean walking, float speed, float movementYaw) {
        public static final Sample STOPPED = new Sample(false, 0.0F, 0.0F);

        /** Stateless compatibility helper; new render code uses the tracker's hysteresis. */
        public boolean backpedalling(float facingYaw) {
            return walking && Math.abs(wrapDegrees(movementYaw - facingYaw)) > 100.0F;
        }

    }
}
