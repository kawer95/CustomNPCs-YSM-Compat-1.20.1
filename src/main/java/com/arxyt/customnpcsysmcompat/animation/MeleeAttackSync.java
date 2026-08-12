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

    public static Sample sample(EntityNPCInterface npc, float partialTick) {
        Integer hitTick = HIT_TICKS.get(npc);
        if (hitTick == null) {
            return Sample.INACTIVE;
        }
        int elapsedTicks = npc.tickCount - hitTick;
        Sample sample = timeline(elapsedTicks, partialTick);
        if (!sample.active()) {
            HIT_TICKS.remove(npc);
        }
        return sample;
    }

    static Sample timeline(int elapsedTicks, float partialTick) {
        if (elapsedTicks < 0 || elapsedTicks >= SWING_TICKS) {
            return Sample.INACTIVE;
        }
        float previous = elapsedTicks / (float) SWING_TICKS;
        float current = (elapsedTicks + 1) / (float) SWING_TICKS;
        float partial = Math.max(0.0F, Math.min(1.0F, partialTick));
        float interpolated = previous + (current - previous) * partial;
        return new Sample(true, elapsedTicks, current, previous, Math.max(0.001F, interpolated));
    }

    public static void clear() {
        HIT_TICKS.clear();
    }

    public record Sample(boolean active, int swingTime, float currentProgress,
                         float previousProgress, float interpolatedProgress) {
        public static final Sample INACTIVE = new Sample(false, 0, 0.0F, 0.0F, 0.0F);
    }
}
