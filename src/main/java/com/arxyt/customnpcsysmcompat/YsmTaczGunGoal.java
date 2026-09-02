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
        LivingEntity target = target(command);
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
        if (target == null) return;
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

        traceRetreat(target, retreating, maneuverName, distance, desired, canSee, command);
        boolean canFire = facingReady && (command.watching()
                ? canSee
                : CommandGunTactics.canFire(command.prone(), canSee, distance, desired));
        traceWatchFireGate(command, target, desired, vanillaCanSee, watchHasClearShot, canFire,
                canFire ? "READY" : !facingReady ? "AIM_TURNING" : "NO_CLEAR_SHOT");
        if (canFire && --actionCooldown <= 0) {
            try {
                // TaCZ rejects a fire request while sprinting.  A command may have just
                // crossed the firing boundary, so clear the replicated sprint state before
                // handing the shot to the weapon facade.
                if (command.active()) npc.setSprinting(false);
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

    private LivingEntity target(DominionCommandBridge.Snapshot command) {
        return command.active() ? command.attackTarget() : npc.getTarget();
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
