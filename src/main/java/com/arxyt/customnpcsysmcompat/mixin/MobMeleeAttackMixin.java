package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.animation.MeleeAttackSync;
import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Receives the server's delayed-hit marker on clients. */
@Mixin(Mob.class)
public abstract class MobMeleeAttackMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void customnpcsYsmCompat$receiveSuccessfulMeleeHit(byte event, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (event == MeleeAttackSync.ENTITY_EVENT && self.level().isClientSide
                && self instanceof EntityNPCInterface npc) {
            CustomNpcsYsmCompat.LOGGER.info(
                    "[YSM-ATTACK-TRACE][CLIENT-EVENT] npcId={} tick={} event={} rawSwinging={} rawSwingTime={}",
                    npc.getId(), npc.tickCount, event, npc.swinging, npc.swingTime);
            MeleeAttackSync.markHit(npc);
        }
    }
}
