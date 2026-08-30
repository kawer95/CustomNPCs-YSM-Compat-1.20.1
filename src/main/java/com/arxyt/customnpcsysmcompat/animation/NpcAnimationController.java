package com.arxyt.customnpcsysmcompat.animation;

public final class NpcAnimationController {
    private NpcAnimationState state = NpcAnimationState.IDLE;
    private int stateStartedAt;
    private int lastTick = Integer.MIN_VALUE;

    public NpcAnimationFrame update(NpcAnimationInput input) {
        NpcAnimationState next = select(input);
        if (lastTick == Integer.MIN_VALUE || next != state || input.tick() < lastTick) {
            state = next;
            stateStartedAt = input.tick();
        }
        lastTick = input.tick();
        int elapsed = Math.max(0, input.tick() - stateStartedAt);

        float walkSpeed = state == NpcAnimationState.WALK
                ? Math.max(0.1F, Math.min(1.0F, input.walkSpeed())) : 0.0F;
        float attackProgress = state == NpcAnimationState.ATTACK
                ? Math.max(0.0F, Math.min(1.0F, input.attackProgress())) : 0.0F;
        int hurtTime = state == NpcAnimationState.HURT ? Math.max(1, input.hurtTime()) : 0;
        int deathTime = state == NpcAnimationState.DEATH
                ? Math.min(20, Math.max(Math.max(1, input.deathTime()), elapsed + 1)) : 0;

        return new NpcAnimationFrame(state, elapsed, walkSpeed, attackProgress,
                hurtTime, deathTime, input.bodyYaw(), input.headYaw());
    }

    public static NpcAnimationState select(NpcAnimationInput input) {
        if (input.dead() || input.deathTime() > 0) {
            return NpcAnimationState.DEATH;
        }
        if (input.hurtTime() > 0) {
            return NpcAnimationState.HURT;
        }
        if (input.attackProgress() > 0.001F) {
            return NpcAnimationState.ATTACK;
        }
        if (input.walking() || input.walkSpeed() > 0.03F) {
            return NpcAnimationState.WALK;
        }
        return NpcAnimationState.IDLE;
    }
}
