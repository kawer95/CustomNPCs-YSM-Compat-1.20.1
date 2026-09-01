package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

/** Fires at a CNPC-native target while Dominion keeps the NPC physically stationary. */
public final class YsmTaczSentryGunGoal extends Goal {
    private final EntityNPCInterface npc;
    private int actionCooldown;

    public YsmTaczSentryGunGoal(EntityNPCInterface npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return validTarget(DominionCommandBridge.snapshot(npc)) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return validTarget(DominionCommandBridge.snapshot(npc)) != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        actionCooldown = 0;
    }

    @Override
    public void tick() {
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        LivingEntity target = validTarget(command);
        GunCompatFacade facade = GunCompat.facade();
        if (target == null || facade == null) return;

        // These are invariants, not steering suggestions: this goal may aim and fire only.
        npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        DominionCombatBalance.Settings settings = DominionCombatBalance.settings();
        if (NpcGunTargetReaction.blocks(npc, target, settings, false)) {
            try {
                facade.stop(npc);
            } catch (Throwable error) {
                GunCompat.reportRuntimeError(error);
            }
            return;
        }
        NpcGunTargetReaction.noteTarget(npc, target, settings);
        npc.getLookControl().setLookAt(target, 90.0F, 90.0F);
        boolean facingReady = NpcGunAimLock.alignForShot(npc, target);

        double range = Math.max(1.0D, npc.stats.ranged.getRange());
        if (!facingReady || npc.distanceTo(target) > range || !npc.getSensing().hasLineOfSight(target)
                || --actionCooldown > 0) return;
        try {
            actionCooldown = facade.operate(npc, target).delayTicks();
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
            actionCooldown = 100;
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) return;
        try {
            facade.stop(npc);
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
        }
    }

    private LivingEntity validTarget(DominionCommandBridge.Snapshot command) {
        // Watch is not this native stationary-sentry mode.  Its target may be up to the
        // Dominion watch range away and must be driven by YsmTaczGunGoal, which owns the
        // 64-block range plus the authoritative multi-point ray test.  Allowing this
        // LOOK-only goal to join watch arbitration reintroduced the CNPC display range.
        if (command.watching()) return null;
        if (!command.stationarySentry() || !GunCompat.active(npc)) return null;
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive() || target == npc) return null;
        return npc.distanceTo(target) <= Math.max(1.0D, npc.stats.ranged.getRange()) ? target : null;
    }
}
