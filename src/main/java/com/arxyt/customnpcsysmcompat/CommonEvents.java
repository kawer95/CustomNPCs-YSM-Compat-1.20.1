package com.arxyt.customnpcsysmcompat;

import com.arxyt.customnpcsysmcompat.animation.DelayedMeleeAttack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

public final class CommonEvents {
    @SubscribeEvent
    public void tickDelayedMeleeAttack(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
            // Forge posts LivingTickEvent at the head of LivingEntity#tick. TaCZ's own
            // crawl validator runs at that tick's tail, so this makes the CNPC-native
            // crawl action available to TaCZ before it updates pose and gun state.
            GunCompat.syncCrawlState(npc);
            DelayedMeleeAttack.tick(npc);
            String model = npc.isDeadOrDying() ? "" : YsmDisplayAccess.get(npc.display).enabled()
                    ? YsmDisplayData.normalizeModelId(YsmDisplayAccess.get(npc.display).modelId()) : "";
            NpcYsmActionState.validate(npc, model);
        }
    }

    @SubscribeEvent public void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof EntityNPCInterface npc) {
            NpcYsmActionState.sendTo(player, npc);
        }
    }
}
