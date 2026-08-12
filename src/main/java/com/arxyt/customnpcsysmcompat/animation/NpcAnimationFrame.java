package com.arxyt.customnpcsysmcompat.animation;

public record NpcAnimationFrame(NpcAnimationState state, int stateTicks, float walkSpeed,
                                float attackProgress, int hurtTime, int deathTime,
                                float bodyYaw, float headYaw) {
}
