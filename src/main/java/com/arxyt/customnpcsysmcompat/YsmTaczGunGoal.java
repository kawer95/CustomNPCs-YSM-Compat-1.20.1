package com.arxyt.customnpcsysmcompat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

/**
 * Server-side TaCZ combat goal for a YSM-enabled CustomNPC.
 *
 * <p>It owns only movement and firing cadence. TaCZ retains ammunition and
 * weapon-state ownership, while the optional Dominion bridge may impose the
 * shared post-kill reaction delay without becoming a runtime requirement.</p>
 */
public final class YsmTaczGunGoal extends Goal {
    private final EntityNPCInterface npc;
    private int actionCooldown;
    private int strafeTime = -1;
    private boolean clockwise;
    private boolean backwards;
    private double lastTraceX;
    private double lastTraceZ;
    private boolean tracePositionReady;
    private boolean wasRetreating;

    public YsmTaczGunGoal(EntityNPCInterface npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = target(command);
        return GunCompat.active(npc) && target != null && target.isAlive()
                && !command.nativeCombatBlocked()
                && (command.commandedAttack() || npc.distanceTo(target) <= effectiveRange());
    }

    @Override
    public boolean canContinueToUse() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = target(command);
        // Do not tear down an in-progress reload merely because strafing briefly moved the
        // NPC across the preferred range boundary. The goal itself can navigate back.
        return GunCompat.active(npc) && target != null && target.isAlive()
                && !command.nativeCombatBlocked();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        actionCooldown = 0;
        strafeTime = -1;
        tracePositionReady = false;
        wasRetreating = false;
    }

    @Override
    public void stop() {
        // A newly-issued Dominion move can become authoritative in the same goal-selector
        // update that stops this gun goal. Never erase that fresh path.
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        if (!command.nativeCombatBlocked()) npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        GunCompatFacade facade = GunCompat.facade();
        if (facade != null) {
            try {
                facade.stop(npc);
            } catch (Throwable error) {
                GunCompat.reportRuntimeError(error);
            }
        }
    }

    @Override
    public void tick() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = target(command);
        GunCompatFacade facade = GunCompat.facade();
        if (target == null || facade == null) {
            return;
        }
        if (command.commandedAttack() && npc.getTarget() != target) npc.setTarget(target);
        DominionCombatBalance.Settings settings = DominionCombatBalance.settings();
        if (NpcGunTargetReaction.blocks(npc, target, settings)) {
            npc.getNavigation().stop();
            npc.getMoveControl().strafe(0.0F, 0.0F);
            try {
                facade.stop(npc);
            } catch (Throwable error) {
                GunCompat.reportRuntimeError(error);
            }
            return;
        }
        NpcGunTargetReaction.noteTarget(npc, target, settings);
        npc.getLookControl().setLookAt(target, 90.0F, 90.0F);
        double distance = npc.distanceTo(target);
        double desired = effectiveRange();
        boolean canSee = npc.getSensing().hasLineOfSight(target);
        boolean retreating = false;
        String maneuverName = npc.isPassenger() ? "PASSENGER" : "UNDECIDED";

        if (npc.isPassenger()) {
            npc.getNavigation().stop();
            npc.getMoveControl().strafe(0.0F, 0.0F);
        } else if (command.active()) {
            CommandGunTactics.Maneuver maneuver = CommandGunTactics.decideControlled(
                    command.commandedAttack(), canSee, distance, desired, command.closeQuarters(), command.prone());
            maneuverName = maneuver.name();
            switch (maneuver) {
                case PURSUE -> {
                    strafeTime = -1;
                    npc.getNavigation().moveTo(target, 1.0D);
                    npc.getMoveControl().strafe(0.0F, 0.0F);
                }
                case RETREAT -> {
                    retreating = true;
                    npc.getNavigation().stop();
                    npc.getMoveControl().strafe(-0.5F, 0.0F);
                    faceTargetWhileRetreating(target);
                }
                case HOLD, SENTRY -> {
                    // SENTRY is a stationary native-target stance; HOLD is an ordered
                    // attack already at a safe firing distance. Neither may move.
                    strafeTime = -1;
                    npc.getNavigation().stop();
                    npc.getMoveControl().strafe(0.0F, 0.0F);
                }
            }
        } else if (!canSee || distance > desired) {
            maneuverName = "NATIVE_PURSUE";
            strafeTime = -1;
            npc.getNavigation().moveTo(target, 1.0D);
        } else {
            npc.getNavigation().stop();
            if (++strafeTime >= 20) {
                if (npc.getRandom().nextFloat() < 0.3F) clockwise = !clockwise;
                if (npc.getRandom().nextFloat() < 0.3F) backwards = !backwards;
                strafeTime = 0;
            }
            if (distance > desired * 0.65D) backwards = false;
            else if (distance < desired * 0.60D) backwards = true;
            retreating = backwards;
            maneuverName = backwards ? "NATIVE_RETREAT" : "NATIVE_STRAFE";
            npc.getMoveControl().strafe(
                    (float) (backwards ? -YsmTaczConfig.FORWARD_SPEED.get() : YsmTaczConfig.FORWARD_SPEED.get()),
                    (float) (clockwise ? YsmTaczConfig.SIDEWAYS_SPEED.get() : -YsmTaczConfig.SIDEWAYS_SPEED.get()));
        }

        npc.setYRot(Mth.rotateIfNecessary(npc.getYRot(), npc.yHeadRot, 30.0F));
        traceRetreat(target, retreating, maneuverName, distance, desired, canSee, command);
        if (CommandGunTactics.canFire(command.prone(), canSee, distance, desired) && --actionCooldown <= 0) {
            try {
                GunCompatFacade.Action action = facade.operate(npc, target);
                actionCooldown = action.delayTicks();
            } catch (Throwable error) {
                GunCompat.reportRuntimeError(error);
                actionCooldown = 100;
            }
        }
    }

    private void traceRetreat(LivingEntity target, boolean retreating, String maneuver,
                              double distance, double desired, boolean canSee,
                              DominionCommandBridge.Snapshot command) {
        double dx = tracePositionReady ? npc.getX() - lastTraceX : 0.0D;
        double dz = tracePositionReady ? npc.getZ() - lastTraceZ : 0.0D;
        double moved = Math.sqrt(dx * dx + dz * dz);
        float movementYaw = moved > 1.0E-5D
                ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : Float.NaN;
        lastTraceX = npc.getX();
        lastTraceZ = npc.getZ();
        tracePositionReady = true;
        if (!retreating && !wasRetreating) return;
        if (retreating && wasRetreating && npc.tickCount % 5 != 0) return;

        double targetDx = target.getX() - npc.getX();
        double targetDz = target.getZ() - npc.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-targetDx, targetDz));
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-RETREAT-TRACE][SERVER-GOAL] npcId={} tick={} retreating={} maneuver={} commandActive={} commandedAttack={} cqb={} distance={} desired={} canSee={} pos=({},{}) moved={} movementYaw={} rotation={} bodyYaw={} headYaw={} targetYaw={} bodyError={} headError={} forwardInput={} sidewaysInput={} delta=({},{},{})",
                npc.getId(), npc.tickCount, retreating, maneuver, command.active(), command.commandedAttack(),
                command.closeQuarters(), decimal(distance), decimal(desired), canSee,
                decimal(npc.getX()), decimal(npc.getZ()), decimal(moved), decimal(movementYaw),
                decimal(npc.getYRot()), decimal(npc.yBodyRot), decimal(npc.yHeadRot), decimal(targetYaw),
                decimal(Mth.wrapDegrees(targetYaw - npc.yBodyRot)),
                decimal(Mth.wrapDegrees(targetYaw - npc.yHeadRot)), decimal(npc.zza), decimal(npc.xxa),
                decimal(npc.getDeltaMovement().x), decimal(npc.getDeltaMovement().y),
                decimal(npc.getDeltaMovement().z));
        wasRetreating = retreating;
    }

    private static String decimal(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.3f", value) : "nan";
    }

    private void faceTargetWhileRetreating(LivingEntity target) {
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        npc.setYRot(targetYaw);
        npc.yBodyRot = targetYaw;
        npc.yHeadRot = targetYaw;
    }

    private LivingEntity target(DominionCommandBridge.Snapshot command) {
        return command.active() ? command.attackTarget() : npc.getTarget();
    }

    private double effectiveRange() {
        if (DominionCommandBridge.snapshot(npc).active()) {
            return Math.max(1.0D, npc.stats.ranged.getRange());
        }
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) return 1.0D;
        int gunRange;
        try {
            gunRange = switch (facade.rangeClass(npc.getMainHandItem())) {
                case NEAR -> YsmTaczConfig.NEAR_DISTANCE.get();
                case MEDIUM -> YsmTaczConfig.MEDIUM_DISTANCE.get();
                case LONG -> YsmTaczConfig.LONG_DISTANCE.get();
            };
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
            gunRange = YsmTaczConfig.MEDIUM_DISTANCE.get();
        }
        return Math.max(1.0D, Math.min(gunRange, npc.stats.aggroRange));
    }
}
