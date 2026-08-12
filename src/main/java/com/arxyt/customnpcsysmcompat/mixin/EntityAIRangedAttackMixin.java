package com.arxyt.customnpcsysmcompat.mixin;

import com.arxyt.customnpcsysmcompat.GunCompat;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityAIRangedAttack.class, remap = false)
public abstract class EntityAIRangedAttackMixin {
    @Shadow @Final private EntityNPCInterface npc;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = true)
    private void customnpcsYsmCompat$disableVanillaProjectile(CallbackInfoReturnable<Boolean> cir) {
        if (GunCompat.active(npc)) {
            cir.setReturnValue(false);
        }
    }
}
