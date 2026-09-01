package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

/**
 * Owns firing for a standing Dominion watch order.
 *
 * <p>CustomNPCs has native MOVE+LOOK goals which can keep the general gun goal from ever
 * being scheduled while a unit is stationary. This goal intentionally owns LOOK only and is
 * installed at watch priority, just like the prone shooter. It therefore cannot create a
 * movement intent, but it always gets a chance to aim and operate a TaCZ weapon.</p>
 */
public final class YsmTaczWatchGunGoal extends Goal {
    private final EntityNPCInterface npc;
    private int actionCooldown;

    public YsmTaczWatchGunGoal(EntityNPCInterface npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        return validTarget(command) != null || continuousSession(command);
    }

    @Override
    public boolean canContinueToUse() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        return validTarget(command) != null || continuousSession(command);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        actionCooldown = 0;
        CustomNpcsYsmCompat.LOGGER.info("[TACZ-WATCH-GOAL] npcId={} entered standing watch shooter", npc.getId());
    }

    @Override
    public void tick() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = validTarget(command);
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) return;

        // Watch owns the unit's position. Aim and weapon state are the only permitted output.
        npc.setSprinting(false);
        npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        if (target == null) {
            facade.continueWatchFire(npc);
            return;
        }
        if (npc.getTarget() != target) npc.setTarget(target);

        DominionCombatBalance.Settings settings = DominionCombatBalance.settings();
        if (NpcGunTargetReaction.blocks(npc, target, settings, false)) {
            trace(target, false, false, false, "TARGET_REACTION");
            if (continuousSession(command)) facade.continueWatchFire(npc); else stopGun(facade);
            return;
        }
        NpcGunTargetReaction.noteTarget(npc, target, settings);
        npc.getLookControl().setLookAt(target, 90.0F, 90.0F);
        boolean facingReady = NpcGunAimLock.track(npc, target);

        boolean vanillaCanSee = npc.getSensing().hasLineOfSight(target);
        boolean watchHasClearShot = DominionCommandBridge.watchHasClearShot(npc, target, vanillaCanSee);
        boolean canFire = facingReady
                && CommandGunTactics.effectiveLineOfSight(true, vanillaCanSee, watchHasClearShot);
        trace(target, vanillaCanSee, watchHasClearShot, canFire,
                canFire ? "READY" : !facingReady ? "AIM_TURNING" : "NO_CLEAR_SHOT");
        if (!canFire) {
            if (continuousSession(command)) facade.continueWatchFire(npc);
            return;
        }
        boolean continuous = continuousSession(command) && facade.isMachineGun(npc.getMainHandItem());
        if (!continuous && --actionCooldown > 0) return;
        try {
            GunCompatFacade.Action action = continuous ? facade.operateWatch(npc, target) : facade.operate(npc, target);
            actionCooldown = continuous ? 1 : action.delayTicks();
            CustomNpcsYsmCompat.LOGGER.info(
                    "[TACZ-WATCH-FIRE] npcId={} targetId={} actionFired={} nextCooldown={}",
                    npc.getId(), target.getId(), action.fired(), actionCooldown);
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
            actionCooldown = 100;
        }
    }

    @Override
    public void stop() {
        npc.setSprinting(false);
        npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        GunCompatFacade facade = GunCompat.facade();
        if (facade != null) stopGun(facade);
        CustomNpcsYsmCompat.LOGGER.info("[TACZ-WATCH-GOAL] npcId={} left standing watch shooter", npc.getId());
    }

    private LivingEntity validTarget(DominionCommandBridge.Snapshot command) {
        if (!command.watching() || command.prone() || !command.commandedAttack() || !GunCompat.active(npc)) {
            return null;
        }
        LivingEntity target = command.attackTarget();
        return target != null && target.isAlive() && target != npc ? target : null;
    }

    private boolean continuousSession(DominionCommandBridge.Snapshot command) {
        return command.watching() && GunCompat.active(npc)
                && DominionCommandBridge.watchContinuousFireRequested(npc);
    }

    private void trace(LivingEntity target, boolean vanillaCanSee, boolean watchHasClearShot,
                       boolean canFire, String gate) {
        if (npc.tickCount % 20 != 0) return;
        double range = DominionCommandBridge.watchRange(npc, 64.0D);
        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-WATCH-GOAL] npcId={} targetId={} range={} distance={} vanillaCanSee={} watchClearShot={} canFire={} cooldown={} gate={}",
                npc.getId(), target.getId(), decimal(range), decimal(npc.distanceTo(target)), vanillaCanSee,
                watchHasClearShot, canFire,
                actionCooldown, gate);
    }

    private void stopGun(GunCompatFacade facade) {
        try {
            facade.stop(npc);
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
        }
    }

    private static String decimal(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.3f", value) : "nan";
    }
}
