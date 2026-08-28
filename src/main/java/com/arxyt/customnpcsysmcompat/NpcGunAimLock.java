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
    private static final Map<EntityNPCInterface, AimState> LAST_COMMAND_AIM =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcGunAimLock() {
    }

    /** Updates the lock from a live commanded target. Safe to call from a gun goal. */
    public static void track(EntityNPCInterface npc, LivingEntity target) {
        float yaw = targetYaw(npc, target);
        if (!Float.isFinite(yaw)) return;
        // This is deliberately the CustomNPCs-native forced look state, not just a
        // one-tick LookControl request. EntityAILook then stops restoring DataAI.orientation
        // between shots, which makes the target-facing body direction authoritative.
        if (npc.lookAi != null) npc.lookAi.rotate(target);
        LAST_COMMAND_AIM.put(npc, new AimState(target, yaw));
        apply(npc, yaw);
    }

    /**
     * Aligns every replicated orientation field immediately before a shot without creating a
     * command-history lock. Native CNPC gun targets use this path: TaCZ receives its own exact
     * yaw supplier, so merely rotating the head left the model body and client-side flashlight
     * facing behind the bullet whenever CustomNPCs elected not to turn the body for a small arc.
     */
    public static void alignForShot(EntityNPCInterface npc, LivingEntity target) {
        float yaw = targetYaw(npc, target);
        if (Float.isFinite(yaw)) apply(npc, yaw);
    }

    /** Pure target-yaw conversion shared by command locks and native firing alignment. */
    static float targetYaw(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || dx * dx + dz * dz < 1.0E-8D) {
            return Float.NaN;
        }
        return (float) -Math.toDegrees(Math.atan2(dx, dz));
    }

    private static float targetYaw(EntityNPCInterface npc, LivingEntity target) {
        if (npc == null || target == null || !target.isAlive()) return Float.NaN;
        return targetYaw(target.getX() - npc.getX(), target.getZ() - npc.getZ());
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
            AimState aim = LAST_COMMAND_AIM.get(npc);
            if (aim != null) {
                if (npc.lookAi != null) npc.lookAi.rotate(aim.target());
                apply(npc, aim.yaw());
            }
            return;
        }
        clear(npc);
    }

    private static void clear(EntityNPCInterface npc) {
        if (npc != null && LAST_COMMAND_AIM.remove(npc) != null) {
            // Release only a forced state this compatibility layer created. The NPC's normal
            // look task can then resume naturally once the Dominion gun command has ended.
            if (npc.lookAi != null) npc.lookAi.stop();
        }
    }

    /**
     * Matches the direct lock used for Dominion's retreating gun maids. The historical
     * fields are updated as well, so a newly initialized CNPC does not interpolate back to
     * its configured idle facing while its optional look AI is still null.
     */
    private static void apply(EntityNPCInterface npc, float yaw) {
        npc.setYRot(yaw);
        npc.yRotO = yaw;
        npc.setYHeadRot(yaw);
        npc.yHeadRotO = yaw;
        npc.yBodyRot = yaw;
        npc.yBodyRotO = yaw;
    }

    private record AimState(LivingEntity target, float yaw) {
    }
}
