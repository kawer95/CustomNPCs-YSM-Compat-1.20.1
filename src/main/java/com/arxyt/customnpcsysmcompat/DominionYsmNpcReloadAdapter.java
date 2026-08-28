package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionTaczReloadAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Thin optional registration for Dominion Sword's shared TACZ reload service.
 *
 * <p>This module deliberately owns only YSM-CNPC eligibility and CNPC-specific queued-target
 * visibility. Timers, settings, ammunition inspection and TACZ reload calls remain in Dominion
 * Sword so they cannot diverge from the maid implementation.</p>
 */
public final class DominionYsmNpcReloadAdapter implements DominionTaczReloadAdapter {
    @Override
    public int priority() {
        return 500;
    }

    @Override
    public boolean supports(Mob unit) {
        return unit instanceof EntityNPCInterface npc && GunCompat.active(npc);
    }

    @Override
    public boolean hasCombatTarget(Mob unit) {
        if (!(unit instanceof EntityNPCInterface npc)) return false;
        LivingEntity target = npc.getTarget();
        if (target != null && target.isAlive()) return true;
        DominionCommandBridge.Snapshot command = DominionCommandBridge.snapshot(npc);
        return command.attackTarget() != null && command.attackTarget().isAlive()
                || DominionCommandBridge.hasQueuedAttack(npc);
    }

    @Override
    public Profile profile(Mob unit) {
        return Profile.CUSTOM_NPC;
    }
}
