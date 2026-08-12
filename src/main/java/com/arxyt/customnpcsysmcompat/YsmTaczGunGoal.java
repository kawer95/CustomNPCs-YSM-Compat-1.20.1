package com.arxyt.customnpcsysmcompat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

public final class YsmTaczGunGoal extends Goal {
    private final EntityNPCInterface npc;
    private int actionCooldown;
    private int strafeTime = -1;
    private boolean clockwise;
    private boolean backwards;

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
        npc.getLookControl().setLookAt(target, 90.0F, 90.0F);
        double distance = npc.distanceTo(target);
        double desired = effectiveRange();
        boolean canSee = npc.getSensing().hasLineOfSight(target);

        if (npc.isPassenger()) {
            npc.getNavigation().stop();
            npc.getMoveControl().strafe(0.0F, 0.0F);
        } else if (command.active()) {
            CommandGunTactics.Maneuver maneuver = CommandGunTactics.decideControlled(
                    command.commandedAttack(), canSee, distance, desired, command.closeQuarters());
            switch (maneuver) {
                case PURSUE -> {
                    strafeTime = -1;
                    npc.getNavigation().moveTo(target, 1.0D);
                    npc.getMoveControl().strafe(0.0F, 0.0F);
                }
                case RETREAT -> {
                    npc.getNavigation().stop();
                    npc.getMoveControl().strafe(-0.5F, 0.0F);
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
            npc.getMoveControl().strafe(
                    (float) (backwards ? -YsmTaczConfig.FORWARD_SPEED.get() : YsmTaczConfig.FORWARD_SPEED.get()),
                    (float) (clockwise ? YsmTaczConfig.SIDEWAYS_SPEED.get() : -YsmTaczConfig.SIDEWAYS_SPEED.get()));
        }

        npc.setYRot(Mth.rotateIfNecessary(npc.getYRot(), npc.yHeadRot, 30.0F));
        if (canSee && distance <= desired && --actionCooldown <= 0) {
            try {
                GunCompatFacade.Action action = facade.operate(npc, target);
                actionCooldown = action.delayTicks();
            } catch (Throwable error) {
                GunCompat.reportRuntimeError(error);
                actionCooldown = 100;
            }
        }
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
