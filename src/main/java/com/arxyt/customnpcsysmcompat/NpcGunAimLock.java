package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
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
    private static final float CLIENT_BODY_TURN_PER_TICK = 30.0F;
    private static final float RESYNC_ANGLE_DEGREES = 1.0F;
    private static final int CLIENT_SYNC_GRACE_TICKS = 1;
    private static final Map<EntityNPCInterface, AimState> LAST_COMMAND_AIM =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcGunAimLock() {
    }

    /** Updates the lock from a live commanded target. Safe to call from a gun goal. */
    public static boolean track(EntityNPCInterface npc, LivingEntity target) {
        AimSolution solution = solutionFor(npc, target);
        if (!solution.valid()) return false;
        // This is deliberately the CustomNPCs-native forced look state, not just a
        // one-tick LookControl request. EntityAILook then stops restoring DataAI.orientation
        // between shots, which makes the target-facing body direction authoritative.
        if (npc.lookAi != null) npc.lookAi.rotate(target);
        return lock(npc, target, solution, true);
    }

    /**
     * Aligns every replicated orientation field immediately before a shot without creating a
     * command-history lock. Native CNPC gun targets use this path: TaCZ receives its own exact
     * yaw supplier, so merely rotating the head left the model body and client-side flashlight
     * facing behind the bullet whenever CustomNPCs elected not to turn the body for a small arc.
     */
    public static boolean alignForShot(EntityNPCInterface npc, LivingEntity target) {
        AimSolution solution = solutionFor(npc, target);
        return solution.valid() && lock(npc, target, solution, false);
    }

    /** Exact yaw/pitch pair shared by the replicated model lock and TaCZ's bullet supplier. */
    public static AimSolution solutionFor(EntityNPCInterface npc, LivingEntity target) {
        if (npc == null || target == null || !target.isAlive()) return AimSolution.INVALID;
        double dx = target.getX() - npc.getX();
        double dy = target.getEyeY() - npc.getEyeY();
        double dz = target.getZ() - npc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = targetYaw(dx, dz);
        float pitch = horizontal < 1.0E-8D ? (dy > 0.0D ? -90.0F : 90.0F)
                : (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return Float.isFinite(yaw) && Float.isFinite(pitch)
                ? new AimSolution(yaw, pitch, true) : AimSolution.INVALID;
    }

    /** Pure target-yaw conversion shared by command locks and native firing alignment. */
    static float targetYaw(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || dx * dx + dz * dz < 1.0E-8D) {
            return Float.NaN;
        }
        return (float) -Math.toDegrees(Math.atan2(dx, dz));
    }

    static int visualTurnDelayTicks(float currentYaw, float targetYaw) {
        if (!Float.isFinite(currentYaw) || !Float.isFinite(targetYaw)) return 0;
        return (int) Math.ceil(Math.abs(wrapDegrees(targetYaw - currentYaw)) / CLIENT_BODY_TURN_PER_TICK);
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
        if (command.nativeCombatBlocked()) {
            clear(npc);
            return;
        }
        if (command.active()) {
            LivingEntity target = command.attackTarget();
            if (target != null && target.isAlive()) {
                track(npc, target);
                return;
            }
            // Dominion deliberately hides the next target during its post-kill reaction. Keep
            // the prior heading only while its persistent attack queue still exists.
            if (DominionCommandBridge.hasQueuedAttack(npc)) {
                AimState aim = LAST_COMMAND_AIM.get(npc);
                if (aim != null) {
                    if (npc.lookAi != null) npc.lookAi.rotate(aim.target());
                    apply(npc, aim.solution());
                }
                return;
            }
            clear(npc);
            return;
        }
        LivingEntity nativeTarget = npc.getTarget();
        if (nativeTarget != null && nativeTarget.isAlive()) alignForShot(npc, nativeTarget);
        else clear(npc);
    }

    private static void clear(EntityNPCInterface npc) {
        AimState removed = npc == null ? null : LAST_COMMAND_AIM.remove(npc);
        if (removed != null && removed.forcedLook()) {
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
    private static boolean lock(EntityNPCInterface npc, LivingEntity target, AimSolution solution,
                                boolean forcedLook) {
        AimState previous = LAST_COMMAND_AIM.get(npc);
        boolean newTarget = previous == null || !previous.targetId().equals(target.getUUID());
        float visualFrom = newTarget ? npc.yBodyRot : previous.solution().yaw();
        int readyTick = newTarget
                ? npc.tickCount + visualTurnDelayTicks(visualFrom, solution.yaw()) + CLIENT_SYNC_GRACE_TICKS
                : previous.readyTick();
        float changedYaw = previous == null ? Float.POSITIVE_INFINITY
                : Math.abs(wrapDegrees(solution.yaw() - previous.solution().yaw()));
        if (!newTarget && changedYaw > CLIENT_BODY_TURN_PER_TICK) {
            readyTick = Math.max(readyTick,
                    npc.tickCount + visualTurnDelayTicks(previous.solution().yaw(), solution.yaw()));
        }
        boolean needsImmediateSync = newTarget
                || Math.abs(wrapDegrees(solution.yaw() - npc.yBodyRot)) > RESYNC_ANGLE_DEGREES
                || Math.abs(solution.pitch() - npc.getXRot()) > RESYNC_ANGLE_DEGREES;
        LAST_COMMAND_AIM.put(npc, new AimState(target, target.getUUID(), solution, readyTick,
                forcedLook || previous != null && previous.forcedLook()));
        apply(npc, solution);
        if (needsImmediateSync) npc.updateClient = true;
        return npc.tickCount >= readyTick;
    }

    private static void apply(EntityNPCInterface npc, AimSolution solution) {
        npc.setYRot(solution.yaw());
        npc.yRotO = solution.yaw();
        npc.setYHeadRot(solution.yaw());
        npc.yHeadRotO = solution.yaw();
        npc.yBodyRot = solution.yaw();
        npc.yBodyRotO = solution.yaw();
        npc.setXRot(solution.pitch());
        npc.xRotO = solution.pitch();
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public record AimSolution(float yaw, float pitch, boolean valid) {
        private static final AimSolution INVALID = new AimSolution(Float.NaN, Float.NaN, false);
    }

    private record AimState(LivingEntity target, UUID targetId, AimSolution solution,
                            int readyTick, boolean forcedLook) {
    }
}
