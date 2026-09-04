package com.arxyt.customnpcsysmcompat;

import com.arxyt.customnpcsysmcompat.network.CompatNetwork;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Keeps the server fire gate and the rendered YSM turn on the same stepped orientation. */
public final class NpcGunAimLock {
    static final float BODY_YAW_DEGREES_PER_TICK = 20.0F;
    static final float HEAD_YAW_DEGREES_PER_TICK = 34.0F;
    static final float PITCH_DEGREES_PER_TICK = 26.0F;
    static final float FIRE_YAW_TOLERANCE = 2.0F;
    static final float FIRE_PITCH_TOLERANCE = 2.0F;

    private static final Map<EntityNPCInterface, AimState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcGunAimLock() {
    }

    /** Records a commanded target and reports whether the already rendered turn is fire-ready. */
    public static boolean track(EntityNPCInterface npc, LivingEntity target) {
        return prepare(npc, target, true);
    }

    /** Records an autonomous target without snapping any current or previous rotation field. */
    public static boolean alignForShot(EntityNPCInterface npc, LivingEntity target) {
        return prepare(npc, target, false);
    }

    private static boolean prepare(EntityNPCInterface npc, LivingEntity target, boolean forcedLook) {
        AimSolution solution = solutionFor(npc, target);
        if (!solution.valid()) return false;
        AimState previous = STATES.get(npc);
        boolean newTarget = previous == null || !previous.targetId.equals(target.getUUID());
        if (newTarget && forcedLook && npc.lookAi != null) npc.lookAi.rotate(target);
        AimState state = new AimState(target, target.getUUID(), solution,
                forcedLook || previous != null && previous.forcedLook,
                previous == null ? Integer.MIN_VALUE : previous.lastAdvancedTick);
        STATES.put(npc, state);
        return aligned(npc.yBodyRot, npc.getYHeadRot(), npc.getXRot(), solution.yaw(), solution.pitch());
    }

    /** Exact unspread projectile solution. It is never copied directly into entity rotation. */
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

    /** Prevents retained machine-gun trigger state from firing along the previous target angle. */
    public static boolean mayContinueFire(EntityNPCInterface npc) {
        if (npc == null) return false;
        AimState state = STATES.get(npc);
        LivingEntity current = npc.getTarget();
        return state != null && current != null && current.isAlive()
                && current.getUUID().equals(state.targetId)
                && aligned(npc.yBodyRot, npc.getYHeadRot(), npc.getXRot(),
                state.solution.yaw(), state.solution.pitch());
    }

    static float targetYaw(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || dx * dx + dz * dz < 1.0E-8D) return Float.NaN;
        return (float) -Math.toDegrees(Math.atan2(dx, dz));
    }

    static float stepAngle(float current, float target, float maximumChange) {
        return Mth.approachDegrees(current, target, maximumChange);
    }

    static boolean aligned(float bodyYaw, float headYaw, float pitch, float targetYaw, float targetPitch) {
        return Math.abs(Mth.wrapDegrees(targetYaw - bodyYaw)) <= FIRE_YAW_TOLERANCE
                && Math.abs(Mth.wrapDegrees(targetYaw - headYaw)) <= FIRE_YAW_TOLERANCE
                && Math.abs(Mth.wrapDegrees(targetPitch - pitch)) <= FIRE_PITCH_TOLERANCE;
    }

    /** Runs at entity tick tail, after CNPC AI has finished modifying its orientation. */
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

        LivingEntity target = command.active() ? command.attackTarget() : npc.getTarget();
        if (command.active() && target == null && npc.getTarget() != null && npc.getTarget().isAlive()) {
            target = npc.getTarget();
        }
        AimState state = STATES.get(npc);
        if (target != null && target.isAlive()) {
            if (state == null || !target.getUUID().equals(state.targetId)) {
                prepare(npc, target, command.active());
                state = STATES.get(npc);
            }
            if (state != null) advanceAndSynchronize(npc, state, target);
            return;
        }

        if (command.active() && DominionCommandBridge.hasQueuedAttack(npc) && state != null) {
            LivingEntity retained = state.target;
            if (retained != null && retained.isAlive()) {
                advanceAndSynchronize(npc, state, retained);
                return;
            }
        }
        clear(npc);
    }

    private static void advanceAndSynchronize(EntityNPCInterface npc, AimState state, LivingEntity target) {
        AimSolution solution = solutionFor(npc, target);
        if (!solution.valid()) return;
        state.solution = solution;
        if (state.lastAdvancedTick != npc.tickCount) {
            npc.setYRot(stepAngle(npc.getYRot(), solution.yaw(), BODY_YAW_DEGREES_PER_TICK));
            npc.yBodyRot = stepAngle(npc.yBodyRot, solution.yaw(), BODY_YAW_DEGREES_PER_TICK);
            npc.setYHeadRot(stepAngle(npc.getYHeadRot(), solution.yaw(), HEAD_YAW_DEGREES_PER_TICK));
            npc.setXRot(stepAngle(npc.getXRot(), solution.pitch(), PITCH_DEGREES_PER_TICK));
            state.lastAdvancedTick = npc.tickCount;
        }
        CompatNetwork.sendAimState(npc, npc.getYRot(), npc.yBodyRot, npc.getYHeadRot(), npc.getXRot());
    }

    private static void clear(EntityNPCInterface npc) {
        AimState removed = npc == null ? null : STATES.remove(npc);
        if (removed != null && removed.forcedLook && npc.lookAi != null) npc.lookAi.stop();
    }

    public record AimSolution(float yaw, float pitch, boolean valid) {
        private static final AimSolution INVALID = new AimSolution(Float.NaN, Float.NaN, false);
    }

    private static final class AimState {
        private final LivingEntity target;
        private final UUID targetId;
        private AimSolution solution;
        private final boolean forcedLook;
        private int lastAdvancedTick;

        private AimState(LivingEntity target, UUID targetId, AimSolution solution,
                         boolean forcedLook, int lastAdvancedTick) {
            this.target = target;
            this.targetId = targetId;
            this.solution = solution;
            this.forcedLook = forcedLook;
            this.lastAdvancedTick = lastAdvancedTick;
        }
    }
}
