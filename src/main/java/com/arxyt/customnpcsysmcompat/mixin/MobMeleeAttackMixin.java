package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.animation.MeleeAttackSync;
import com.arxyt.customnpcsysmcompat.data.YsmDisplayAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emits a marker only after vanilla confirms that the melee damage call succeeded. */
@Mixin(Mob.class)
public abstract class MobMeleeAttackMixin {
    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void customnpcsYsmCompat$broadcastSuccessfulMeleeHit(Entity target,
                                                                 CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide && cir.getReturnValueZ()
                && self instanceof EntityNPCInterface npc
                && YsmDisplayAccess.get(npc.display).enabled()) {
            self.level().broadcastEntityEvent(self, MeleeAttackSync.ENTITY_EVENT);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void customnpcsYsmCompat$receiveSuccessfulMeleeHit(byte event, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (event == MeleeAttackSync.ENTITY_EVENT && self.level().isClientSide
                && self instanceof EntityNPCInterface npc) {
            MeleeAttackSync.markHit(npc);
        }
    }
}
