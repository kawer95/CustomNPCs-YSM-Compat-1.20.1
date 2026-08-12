package com.arxyt.customnpcsysmcompat.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcAnimationControllerTest {
    @Test
    void appliesDeterministicPriority() {
        assertEquals(NpcAnimationState.DEATH, NpcAnimationController.select(input(true, 0, 10, true, true)));
        assertEquals(NpcAnimationState.DEATH, NpcAnimationController.select(input(false, 2, 10, true, true)));
        assertEquals(NpcAnimationState.HURT, NpcAnimationController.select(input(false, 0, 10, true, true)));
        assertEquals(NpcAnimationState.ATTACK, NpcAnimationController.select(input(false, 0, 0, true, true)));
        assertEquals(NpcAnimationState.WALK, NpcAnimationController.select(input(false, 0, 0, false, true)));
        assertEquals(NpcAnimationState.IDLE, NpcAnimationController.select(input(false, 0, 0, false, false)));
    }

    @Test
    void resetsElapsedTimeOnTransition() {
        NpcAnimationController controller = new NpcAnimationController();
        assertEquals(0, controller.update(inputAt(20, false, 0, 0, false, true)).stateTicks());
        assertEquals(3, controller.update(inputAt(23, false, 0, 0, false, true)).stateTicks());
        NpcAnimationFrame attack = controller.update(inputAt(24, false, 0, 0, true, true));
        assertEquals(NpcAnimationState.ATTACK, attack.state());
        assertEquals(0, attack.stateTicks());
    }

    @Test
    void loopsAttackAndAdvancesDeath() {
        NpcAnimationController controller = new NpcAnimationController();
        controller.update(inputAt(100, false, 0, 0, true, false));
        assertEquals(0.5F, controller.update(inputAt(103, false, 0, 0, true, false)).attackProgress(), 0.001F);
        assertEquals(0.0F, controller.update(inputAt(106, false, 0, 0, true, false)).attackProgress(), 0.001F);

        NpcAnimationFrame death = controller.update(inputAt(110, true, 1, 0, false, false));
        assertEquals(1, death.deathTime());
        assertEquals(5, controller.update(inputAt(114, true, 1, 0, false, false)).deathTime());
    }

    private static NpcAnimationInput input(boolean dead, int death, int hurt, boolean attack, boolean walk) {
        return inputAt(0, dead, death, hurt, attack, walk);
    }

    private static NpcAnimationInput inputAt(int tick, boolean dead, int death, int hurt,
                                             boolean attack, boolean walk) {
        return new NpcAnimationInput(tick, dead, death, hurt, attack, walk,
                walk ? 0.6F : 0.0F, 30.0F, 50.0F);
    }
}
