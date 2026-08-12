package com.arxyt.customnpcsysmcompat.animation;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Places the real melee damage on the visible hit frame of the YSM swing. */
public final class DelayedMeleeAttack {
    public static final int HIT_DELAY_TICKS = 5;
    private static final Map<EntityNPCInterface, PendingAttack> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> false);

    private DelayedMeleeAttack() {
    }

    public static boolean isExecuting() {
        return EXECUTING.get();
    }

    /** Returns true when the original call must be cancelled. */
    public static boolean intercept(EntityNPCInterface npc, Entity target) {
        if (isExecuting()) {
            return false;
        }
        synchronized (PENDING) {
            if (!PENDING.containsKey(npc)) {
                int executeAt = npc.tickCount + HIT_DELAY_TICKS;
                PENDING.put(npc, new PendingAttack(target, executeAt));
                npc.level().broadcastEntityEvent(npc, MeleeAttackSync.ENTITY_EVENT);
                CustomNpcsYsmCompat.LOGGER.info(
                        "[YSM-ATTACK-TRACE][SERVER-QUEUED] npcId={} tick={} targetId={} executeAt={} delay={}",
                        npc.getId(), npc.tickCount, target.getId(), executeAt, HIT_DELAY_TICKS);
            }
        }
        return true;
    }

    public static void tick(EntityNPCInterface npc) {
        PendingAttack pending;
        synchronized (PENDING) {
            pending = PENDING.get(npc);
            if (pending == null || npc.tickCount < pending.executeAt()) {
                return;
            }
            PENDING.remove(npc);
        }

        Entity target = pending.target();
        if (!npc.isAlive() || !target.isAlive() || target.level() != npc.level()) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ATTACK-TRACE][SERVER-DROPPED] npcId={} tick={} targetId={}",
                    npc.getId(), npc.tickCount, target.getId());
            return;
        }

        boolean success;
        EXECUTING.set(true);
        try {
            success = npc.doHurtTarget(target);
        } finally {
            EXECUTING.remove();
        }
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-ATTACK-TRACE][SERVER-DAMAGE] npcId={} tick={} targetId={} success={}",
                npc.getId(), npc.tickCount, target.getId(), success);
    }

    public static void clear(EntityNPCInterface npc) {
        PENDING.remove(npc);
    }

    private record PendingAttack(Entity target, int executeAt) {
    }
}
