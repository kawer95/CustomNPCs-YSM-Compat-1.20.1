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
    public boolean beginOfflineTask(ServerPlayer player, Entity entity) {
        return supports(entity);
    }

    @Override
    public boolean attack(ServerPlayer player, Entity entity, LivingEntity target) {
        if (!(entity instanceof EntityNPCInterface npc) || !GunCompat.active(npc)
                || target == null || !target.isAlive()) return false;
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
        stopGun(npc);
        return true;
    }

    private static void stopGun(EntityNPCInterface npc) {
        GunCompatFacade facade = GunCompat.facade();
        if (facade == null) return;
        try {
            facade.stop(npc);
        } catch (Throwable error) {
            GunCompat.reportRuntimeError(error);
        }
    }
}
