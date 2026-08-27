package com.arxyt.customnpcsysmcompat;

import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.dominionsword.api.DominionUnitAdapter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

/** Gives Dominion ownership of YSM-CNPC gun tactics while retaining the native gun engine. */
public final class DominionYsmNpcAdapter implements DominionUnitAdapter {
    @Override
    public int priority() {
        return 500;
    }

    @Override
    public boolean supports(Entity entity) {
        return entity instanceof EntityNPCInterface npc
                && YsmDisplayAccess.get(npc.display).enabled();
    }

    @Override
    public boolean supportsOfflineTasks(Entity entity) {
        return supports(entity);
    }

    @Override
    public boolean supportsWatch(Entity entity) {
        return entity instanceof EntityNPCInterface npc && GunCompat.active(npc);
    }

    @Override
    public boolean beginOfflineTask(ServerPlayer player, Entity entity) {
        return supports(entity);
    }

    @Override
    public boolean attack(ServerPlayer player, Entity entity, LivingEntity target) {
        if (!(entity instanceof EntityNPCInterface npc) || !GunCompat.active(npc)
                || target == null || !target.isAlive()) return false;
        LivingEntity previous = npc.getTarget();
        if (previous != null && previous.isAlive() && !previous.getUUID().equals(target.getUUID())) {
            // Dominion invokes this adapter every tick. Only a genuinely live
            // target replacement is an explicit fresh player order.
            NpcGunTargetReaction.clear(npc);
        }
        npc.setTarget(target);
        return true;
    }

    @Override
    public boolean hold(ServerPlayer player, Entity entity) {
        if (!(entity instanceof EntityNPCInterface npc) || !GunCompat.active(npc)) return false;
        npc.getNavigation().stop();
        npc.getMoveControl().strafe(0.0F, 0.0F);
        // HOLD is also a stationary sentry stance. Stopping the gun here runs after
        // Goal ticks and used to cancel every shot that the sentry goal prepared.
        return true;
    }

    @Override
    public boolean clearAttack(ServerPlayer player, Entity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) return false;
        npc.setTarget(null);
        NpcGunTargetReaction.clear(npc);
        // Dominion clears its persistent queue after adapter callbacks. Force the TaCZ adapter
        // to leave ADS now instead of treating that brief same-tick queue as a target hand-off.
        stopGun(npc, true);
        return true;
    }

    private static void stopGun(EntityNPCInterface npc, boolean forceExitAim) {
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) return;
        try {
            facade.stop(npc, forceExitAim);
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
        }
    }
}
