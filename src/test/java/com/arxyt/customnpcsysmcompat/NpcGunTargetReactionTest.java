package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NpcGunTargetReactionTest {
    @Test
    void staticReactionAlwaysUsesOneSecond() {
        assertEquals(20, NpcGunTargetReaction.reactionDuration(false, 0.0D));
        assertEquals(20, NpcGunTargetReaction.reactionDuration(false, 180.0D));
    }

    @Test
    void dynamicReactionMatchesDominionMaidAngleRange() {
        assertEquals(10, NpcGunTargetReaction.reactionDuration(true, 0.0D));
        assertEquals(25, NpcGunTargetReaction.reactionDuration(true, 90.0D));
        assertEquals(40, NpcGunTargetReaction.reactionDuration(true, 180.0D));
        assertEquals(10, NpcGunTargetReaction.reactionDuration(true, -20.0D));
        assertEquals(40, NpcGunTargetReaction.reactionDuration(true, 999.0D));
    }
}
