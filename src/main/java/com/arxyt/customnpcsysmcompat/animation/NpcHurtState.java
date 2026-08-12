package com.arxyt.customnpcsysmcompat.animation;

/** Maintains the LivingEntity hurt-clock invariant for a render-only proxy. */
public record NpcHurtState(int time, int duration) {
    public static final NpcHurtState INACTIVE = new NpcHurtState(0, 0);
    private static final int MAX_REASONABLE_HURT_TICKS = 100;

    public static NpcHurtState normalize(int sourceTime, int sourceDuration, boolean dead) {
        if (dead) {
            return INACTIVE;
        }
        int duration = Math.min(MAX_REASONABLE_HURT_TICKS, Math.max(0, sourceDuration));
        // Vanilla LivingEntity decrements only hurtTime. hurtDuration remains at the value
        // established by hurt()/animateHurt(), including after hurtTime reaches zero.
        if (sourceTime <= 0) return new NpcHurtState(0, duration);
        int time = Math.min(MAX_REASONABLE_HURT_TICKS, sourceTime);
        duration = Math.max(time, Math.max(1, duration));
        return new NpcHurtState(time, duration);
    }
}
