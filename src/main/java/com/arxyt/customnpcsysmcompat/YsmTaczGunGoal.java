package com.arxyt.customnpcsysmcompat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
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
    /** Combat return is an urgent redeployment, not a normal command that slows near its goal. */
    private static final double RETURN_NAVIGATION_SPEED = 1.8D;
    private static final double RETURN_ARRIVAL_DISTANCE_SQR = 1.0D;
    private final EntityNPCInterface npc;
    private int actionCooldown;
    private int strafeTime = -1;
    private boolean clockwise;
    private boolean backwards;
    private double lastTraceX;
    private double lastTraceZ;
    private boolean tracePositionReady;
    private boolean wasRetreating;
    private Vec3 autonomousOrigin;
    private LivingEntity autonomousTarget;
    private boolean autonomousEngagement;
    private boolean returningToOrigin;
    private int nextAutonomousScanTick;

    public YsmTaczGunGoal(EntityNPCInterface npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        if (!GunCompat.active(npc) || command.nativeCombatBlocked()
                || command.prone() || command.watching()) return false;
        LivingEntity target = target(command, true);
        // Acquisition range and weapon range are different. A target found within the fixed
        // 16-block alert radius must start this goal even when this NPC's configured gun range
        // is shorter; the movement phase will pursue until the weapon can legally fire.
        return (target != null && target.isAlive()) || (!command.active() && returningToOrigin);
    }

    @Override
    public boolean canContinueToUse() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = target(command, true);
        // Do not tear down an in-progress reload merely because strafing briefly moved the
        // NPC across the preferred range boundary. The goal itself can navigate back.
        return GunCompat.active(npc) && !command.nativeCombatBlocked()
                && !command.prone() && !command.watching()
                && ((target != null && target.isAlive()) || (!command.active() && returningToOrigin));
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
        if (command.active()) npc.setSprinting(false);
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
        LivingEntity target = target(command, true);
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) {
            return;
        }
        // A player explicitly pressed the reload skill. Do not let the next automatic-gun shot
        // reset TaCZ's shoot cooldown before its native reload request can begin.
        if (DominionCommandBridge.isReloadActive(npc)) {
            if (command.active()) npc.setSprinting(false);
            npc.getNavigation().stop();
            npc.getMoveControl().strafe(0.0F, 0.0F);
            return;
        }
        if (target == null) {
            tickReturnToOrigin(command);
            return;
        }
        if (command.commandedAttack() && npc.getTarget() != target) npc.setTarget(target);
        DominionCombatBalance.Settings settings = DominionCombatBalance.settings();
        if (NpcGunTargetReaction.blocks(npc, target, settings,
                command.directAttackOrder() || DominionCommandBridge.bypassesTargetReaction(npc))) {
            traceWatchFireGate(command, target, effectiveRange(), false, false, false, "TARGET_REACTION");
            if (command.active()) npc.setSprinting(false);
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
        boolean facingReady;
        if (command.commandedAttack()) {
            facingReady = NpcGunAimLock.track(npc, target);
        } else {
            // TaCZ computes its bullet yaw directly from this target. Force the native CNPC
            // body to that same yaw even for a small arc, rather than letting CustomNPCs rotate
            // only the head while the gun, YSM model and flashlight remain visually behind.
            facingReady = NpcGunAimLock.alignForShot(npc, target);
        }
        double distance = npc.distanceTo(target);
        double desired = effectiveRange();
        boolean vanillaCanSee = npc.getSensing().hasLineOfSight(target);
        boolean breach = DominionCommandBridge.isBreachAssault(npc);
        boolean watchHasClearShot = command.watching()
                && DominionCommandBridge.watchHasClearShot(npc, target, vanillaCanSee);
        boolean breachHasClearShot = breach
                && DominionCommandBridge.breachAimPoint(npc, target, vanillaCanSee ? target.getEyePosition() : null) != null;
        boolean canSee = breach ? breachHasClearShot : CommandGunTactics.effectiveLineOfSight(
                command.watching(), vanillaCanSee, watchHasClearShot);
        boolean retreating = false;
        String maneuverName = npc.isPassenger() ? "PASSENGER" : "UNDECIDED";

        if (npc.isPassenger()) {
            if (command.active()) npc.setSprinting(false);
            npc.getNavigation().stop();
            npc.getMoveControl().strafe(0.0F, 0.0F);
        } else if (command.active()) {
            boolean breachEntering = DominionCommandBridge.isBreachEntering(npc);
            boolean breachStationary = DominionCommandBridge.isBreachStationary(npc);
            CommandGunTactics.Maneuver maneuver = command.watching() || breachStationary
                    ? CommandGunTactics.Maneuver.SENTRY
                    : CommandGunTactics.decideControlled(
                            command.commandedAttack(), canSee, distance, desired, command.closeQuarters(), command.prone());
            maneuverName = breachEntering ? "BREACH_ENTRY" : maneuver.name();
            if (breachEntering) {
                // Dominion owns the doorway path. Aim and fire without replacing that path with
                // pursue, retreat or strafe movement until the unit has crossed the door.
                strafeTime = -1;
                backwards = false;
            } else switch (maneuver) {
                case PURSUE -> {
                    strafeTime = -1;
                    double navigationSpeed = DominionCommandBridge.commandMovementSpeed(npc, 1.0D);
                    npc.setSprinting(navigationSpeed > 1.0D && !command.prone());
                    npc.getNavigation().moveTo(target, navigationSpeed);
                    npc.getMoveControl().strafe(0.0F, 0.0F);
                }
                case RETREAT -> {
                    retreating = true;
                    npc.setSprinting(false);
                    npc.getNavigation().stop();
                    npc.getMoveControl().strafe(-0.5F, 0.0F);
                    faceTargetWhileRetreating(target);
                }
                case HOLD, SENTRY -> {
                    // SENTRY is a stationary native-target stance; HOLD is an ordered
                    // attack already at a safe firing distance. Neither may move.
                    strafeTime = -1;
                    npc.setSprinting(false);
                    npc.getNavigation().stop();
                    npc.getMoveControl().strafe(0.0F, 0.0F);
                }
            }
        } else if (!canSee || distance > desired) {
            maneuverName = "NATIVE_PURSUE";
            strafeTime = -1;
            npc.getNavigation().moveTo(target, 1.0D);
        } else {
            npc.setSprinting(false);
            npc.getNavigation().stop();
            retreating = distance < 10.0D;
            maneuverName = retreating ? "AUTONOMOUS_RETREAT" : "AUTONOMOUS_HOLD";
            npc.getMoveControl().strafe(retreating ? -0.5F : 0.0F, 0.0F);
            if (retreating) faceTargetWhileRetreating(target);
        }

        traceRetreat(target, retreating, maneuverName, distance, desired, canSee, command);
        boolean canFire = facingReady && (command.watching()
                ? canSee
                : CommandGunTactics.canFire(command.prone(), canSee, distance, desired));
        traceWatchFireGate(command, target, desired, vanillaCanSee, watchHasClearShot, canFire,
                canFire ? "READY" : !facingReady ? "AIM_TURNING" : "NO_CLEAR_SHOT");
        if (canFire && --actionCooldown <= 0) {
            try {
                GunCompatFacade.Action action = facade.operate(npc, target);
                actionCooldown = action.delayTicks();
                if (command.watching()) {
                    CustomNpcsYsmCompat.LOGGER.info(
                            "[TACZ-WATCH-FIRE] npcId={} targetId={} actionFired={} nextCooldown={}",
                            npc.getId(), target.getId(), action.fired(), actionCooldown);
                }
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

    /** Emits one bounded record per second for the only goal allowed to fire during watch. */
    private void traceWatchFireGate(DominionCommandBridge.Snapshot command, LivingEntity target,
                                    double range, boolean vanillaCanSee, boolean watchHasClearShot,
                                    boolean canFire, String gate) {
        if (!command.watching() || npc.tickCount % 20 != 0) return;
        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-WATCH-GOAL] npcId={} targetId={} range={} distance={} vanillaCanSee={} watchClearShot={} canFire={} cooldown={} gate={}",
                npc.getId(), target.getId(), decimal(range), decimal(npc.distanceTo(target)), vanillaCanSee,
                watchHasClearShot, canFire,
                actionCooldown, gate);
    }

    private void faceTargetWhileRetreating(LivingEntity target) {
        NpcGunAimLock.alignForShot(npc, target);
    }

    private LivingEntity target(DominionCommandBridge.Snapshot command, boolean acquire) {
        if (command.active() && command.attackTarget() != null) {
            clearAutonomousState();
            return command.attackTarget();
        }
        boolean stationary = command.stationarySentry();
        if (command.active() && !stationary || npc.isPassenger()) {
            if (npc.getTarget() == autonomousTarget) npc.setTarget(null);
            clearAutonomousState();
            return null;
        }

        LivingEntity current = npc.getTarget();
        if (current != autonomousTarget && !IdleNpcTargeting.valid(npc, current)) {
            if (current != null) npc.setTarget(null);
            current = null;
        }
        // CNPC can acquire a target before this goal's staggered scan. Treat it exactly like a
        // target found here, otherwise TaCZ clearing npc.getTarget() at reload start would lose
        // the entire autonomous engagement and no post-reload shot could ever resume it.
        if (current != null && current != autonomousTarget) {
            if (!stationary && !autonomousEngagement) autonomousOrigin = npc.position();
            autonomousEngagement = !stationary;
            returningToOrigin = false;
            autonomousTarget = current;
        }
        if (!IdleNpcTargeting.engaged(npc, current, effectiveRange())
                && IdleNpcTargeting.engaged(npc, autonomousTarget, effectiveRange())) {
            current = autonomousTarget;
            npc.setTarget(current);
        }
        if (!IdleNpcTargeting.engaged(npc, current, effectiveRange())) {
            if (current != null && autonomousEngagement) npc.setTarget(null);
            current = null;
        }
        if (current == null && acquire && npc.tickCount >= nextAutonomousScanTick) {
            nextAutonomousScanTick = npc.tickCount + 5 + Math.floorMod(npc.getId(), 5);
            current = IdleNpcTargeting.find(npc);
            if (current != null) {
                if (!stationary && !autonomousEngagement) autonomousOrigin = npc.position();
                autonomousEngagement = !stationary;
                returningToOrigin = false;
                autonomousTarget = current;
                npc.setTarget(current);
            }
        }
        if (current == null && autonomousEngagement && !stationary) returningToOrigin = true;
        return current;
    }

    private void tickReturnToOrigin(DominionCommandBridge.Snapshot command) {
        if (command.active() || !returningToOrigin || autonomousOrigin == null || npc.isPassenger()) return;
        if (npc.position().distanceToSqr(autonomousOrigin) <= RETURN_ARRIVAL_DISTANCE_SQR) {
            npc.getNavigation().stop();
            npc.getMoveControl().strafe(0.0F, 0.0F);
            npc.setSprinting(false);
            clearAutonomousState();
            return;
        }
        // Keep full return speed right up to the one-block arrival boundary. Do not pass this
        // through the ordinary command-distance pace selector, which intentionally downgrades
        // nearby movement to walking and makes a displaced sentry take too long to recover.
        npc.setSprinting(true);
        npc.getMoveControl().strafe(0.0F, 0.0F);
        npc.getNavigation().moveTo(autonomousOrigin.x, autonomousOrigin.y, autonomousOrigin.z,
                RETURN_NAVIGATION_SPEED);
    }

    private void clearAutonomousState() {
        autonomousOrigin = null;
        autonomousTarget = null;
        autonomousEngagement = false;
        returningToOrigin = false;
    }

    private double effectiveRange() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        if (DominionCommandBridge.isBreachAssault(npc)) return 64.0D;
        if (command.watching()) {
            return DominionCommandBridge.watchRange(npc,
                    Math.max(2.0D, TaczCombatSettingsBridge.range(npc, npc.stats.ranged.getRange()) * 2.0D));
        }
        if (command.active()) {
            return Math.max(1.0D, TaczCombatSettingsBridge.range(npc, npc.stats.ranged.getRange()));
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
        return Math.max(1.0D, TaczCombatSettingsBridge.range(npc,
                Math.min(gunRange, npc.stats.aggroRange)));
    }
}
