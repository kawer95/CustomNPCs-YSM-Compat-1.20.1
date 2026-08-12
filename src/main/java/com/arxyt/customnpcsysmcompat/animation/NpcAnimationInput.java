package com.arxyt.customnpcsysmcompat.animation;

public record NpcAnimationInput(int tick, boolean dead, int deathTime, int hurtTime,
                                boolean attacking, boolean walking, float walkSpeed,
                                float bodyYaw, float headYaw) {
}
