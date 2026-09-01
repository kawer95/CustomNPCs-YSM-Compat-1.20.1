package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

/**
 * Fires an explicitly commanded TaCZ weapon while a CustomNPC is prone.
 *
 * <p>Dominion deliberately disables the NPC's {@link Flag#MOVE} control while prone so
 * native pathfinding cannot make a crawling unit stand up. The regular gun goal owns MOVE
 * and LOOK and therefore cannot be relied upon in that state. This goal owns LOOK only:
 * it keeps the NPC stationary, faces the ordered target, and lets the TaCZ adapter retain
 * its normal draw, ADS, reload, ammunition, and firing cadence.</p>
 */
public final class YsmTaczProneGunGoal extends Goal {
    private final EntityNPCInterface npc;
    private int actionCooldown;

    public YsmTaczProneGunGoal(EntityNPCInterface npc) {
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
        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-PRONE-GOAL] npcId={} entered look-only prone firing goal", npc.getId());
    }

    @Override
    public void tick() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = validTarget(command);
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) return;

        // Hard invariants for a prone command: no navigation or strafing, only aim and fire.
        npc.setSprinting(false);
        npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        if (target == null) {
            facade.continueWatchFire(npc);
            return;
        }
        DominionCombatBalance.Settings settings = DominionCombatBalance.settings();
        if (NpcGunTargetReaction.blocks(npc, target, settings, command.directAttackOrder())) {
            if (continuousSession(command)) facade.continueWatchFire(npc); else stopGun(facade);
            return;
        }
        NpcGunTargetReaction.noteTarget(npc, target, settings);
        npc.getLookControl().setLookAt(target, 90.0F, 90.0F);
        boolean facingReady = NpcGunAimLock.track(npc, target);

        boolean vanillaCanSee = npc.getSensing().hasLineOfSight(target);
        boolean watchHasClearShot = command.watching()
                && DominionCommandBridge.watchHasClearShot(npc, target, vanillaCanSee);
        boolean canSee = CommandGunTactics.effectiveLineOfSight(
                command.watching(), vanillaCanSee, watchHasClearShot);
        if (npc.tickCount % 20 == 0) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[TACZ-PRONE-GOAL] npcId={} targetId={} watching={} vanillaCanSee={} effectiveCanSee={} cooldown={} distance={}",
                    npc.getId(), target.getId(), command.watching(), vanillaCanSee, canSee, actionCooldown,
                    String.format(java.util.Locale.ROOT, "%.2f", npc.distanceTo(target)));
        }
        // A prone ordered attack intentionally ignores the normal ranged-AI distance cap,
        // but still needs a real line of sight so shots cannot pass through terrain.
        if (!facingReady || !canSee) {
            if (continuousSession(command)) facade.continueWatchFire(npc);
            return;
        }
        boolean continuous = continuousSession(command) && facade.isMachineGun(npc.getMainHandItem());
        if (!continuous && --actionCooldown > 0) return;
        try {
            GunCompatFacade.Action action = continuous ? facade.operateWatch(npc, target) : facade.operate(npc, target);
            actionCooldown = continuous ? 1 : action.delayTicks();
            CustomNpcsYsmCompat.LOGGER.info(
                    "[TACZ-PRONE-GOAL] npcId={} targetId={} actionFired={} nextCooldown={}",
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
        CustomNpcsYsmCompat.LOGGER.info(
                "[TACZ-PRONE-GOAL] npcId={} left look-only prone firing goal", npc.getId());
    }

    private LivingEntity validTarget(DominionCommandBridge.Snapshot command) {
        if (!command.prone() || !command.commandedAttack() || !GunCompat.active(npc)) return null;
        LivingEntity target = command.attackTarget();
        return target != null && target.isAlive() && target != npc ? target : null;
    }

    private boolean continuousSession(DominionCommandBridge.Snapshot command) {
        return command.prone() && command.watching() && GunCompat.active(npc)
                && DominionCommandBridge.watchContinuousFireRequested(npc);
    }

    private void stopGun(GunCompatFacade facade) {
        try {
            facade.stop(npc);
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
        }
    }
}
