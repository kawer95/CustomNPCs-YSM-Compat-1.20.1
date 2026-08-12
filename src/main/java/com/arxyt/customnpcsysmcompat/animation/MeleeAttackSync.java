package com.arxyt.customnpcsysmcompat.animation;

import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-side timestamps sourced from the server's successful melee damage event. */
public final class MeleeAttackSync {
    public static final byte ENTITY_EVENT = 72;
    private static final int SWING_TICKS = 6;
    private static final Map<EntityNPCInterface, Integer> HIT_TICKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MeleeAttackSync() {
    }

    public static void markHit(EntityNPCInterface npc) {
        HIT_TICKS.put(npc, npc.tickCount);
    }

    public static float progress(EntityNPCInterface npc, float partialTick) {
        Integer hitTick = HIT_TICKS.get(npc);
        if (hitTick == null) {
            return 0.0F;
        }
        float elapsed = npc.tickCount - hitTick + partialTick;
        if (elapsed < 0.0F || elapsed >= SWING_TICKS) {
            HIT_TICKS.remove(npc);
            return 0.0F;
        }
        return Math.max(0.001F, elapsed / SWING_TICKS);
    }

    public static void clear() {
        HIT_TICKS.clear();
    }
}
