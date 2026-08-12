package com.arxyt.customnpcsysmcompat;

import com.arxyt.customnpcsysmcompat.animation.DelayedMeleeAttack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

public final class CommonEvents {
    @SubscribeEvent
    public void tickDelayedMeleeAttack(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
            DelayedMeleeAttack.tick(npc);
        }
    }
}
