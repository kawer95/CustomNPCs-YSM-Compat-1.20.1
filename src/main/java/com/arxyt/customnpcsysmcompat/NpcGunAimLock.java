package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps a commanded gun NPC's replicated body and head facing coherent.
 *
 * <p>CustomNPCs' normal body controller is free to restore an idle heading after a
 * gun goal yields. Dominion intentionally yields between dead queue entries to apply
 * its target-reaction timing, but TaCZ keeps ADS active for that same queue. Remembering
 * the last commanded aim yaw prevents that short hand-off from producing an idle-facing
 * head snap before the next target arrives.</p>
 */
public final class NpcGunAimLock {
    private static final Map<EntityNPCInterface, Float> LAST_COMMAND_YAW =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcGunAimLock() {
    }

    /** Updates the lock from a live commanded target. Safe to call from a gun goal. */
    public static void track(EntityNPCInterface npc, LivingEntity target) {
        if (npc == null || target == null || !target.isAlive()) return;
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        if (dx * dx + dz * dz < 1.0E-8D) return;
        float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        LAST_COMMAND_YAW.put(npc, yaw);
        apply(npc, yaw);
    }

    /**
     * Runs after CustomNPCs' own mob tick. While the queue is between targets it retains
     * the latest aim direction; a new live command target immediately replaces that value.
     */
    public static void maintain(EntityNPCInterface npc) {
        if (npc == null || npc.level().isClientSide || !GunCompat.active(npc)) {
            clear(npc);
            return;
        }
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        if (!command.active() || command.nativeCombatBlocked()) {
            clear(npc);
            return;
        }
        LivingEntity target = command.attackTarget();
        if (target != null && target.isAlive()) {
            track(npc, target);
            return;
        }
        // Dominion deliberately hides the next target during its post-kill reaction.  Keep
        // the prior heading only while its persistent attack queue still exists.
        if (DominionCommandBridge.hasQueuedAttack(npc)) {
            Float yaw = LAST_COMMAND_YAW.get(npc);
            if (yaw != null) apply(npc, yaw);
            return;
        }
        clear(npc);
    }

    private static void clear(EntityNPCInterface npc) {
        if (npc != null) LAST_COMMAND_YAW.remove(npc);
    }

    /** Matches the direct lock used for Dominion's retreating gun maids. */
    private static void apply(EntityNPCInterface npc, float yaw) {
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.yBodyRot = yaw;
    }
}
