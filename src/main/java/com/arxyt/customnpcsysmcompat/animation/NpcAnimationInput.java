package com.arxyt.customnpcsysmcompat.animation;

public record NpcAnimationInput(int tick, boolean dead, int deathTime, int hurtTime,
                                float attackProgress, boolean walking, float walkSpeed,
                                float bodyYaw, float headYaw) {
}
