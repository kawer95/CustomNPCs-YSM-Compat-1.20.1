package com.arxyt.customnpcsysmcompat.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcHurtStateTest {
    @Test
    void preservesValidHurtClocks() {
        assertEquals(new NpcHurtState(7, 10), NpcHurtState.normalize(7, 10, false));
    }

    @Test
    void durationCannotEndBeforeTime() {
        assertEquals(new NpcHurtState(8, 8), NpcHurtState.normalize(8, 0, false));
    }

    @Test
    void deadEntitiesDoNotRemainHurt() {
        assertEquals(NpcHurtState.INACTIVE, NpcHurtState.normalize(5, 10, true));
    }

    @Test
    void vanillaDurationSurvivesAfterHurtTimeEnds() {
        assertEquals(new NpcHurtState(0, 10), NpcHurtState.normalize(0, 10, false));
    }
}
