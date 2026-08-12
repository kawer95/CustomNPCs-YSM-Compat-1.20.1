package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.AnimatedNpcRenderBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.elfmcys.yesstevemodel.OOoO0O0OooOO0o00oOoOOoO0", remap = false)
public abstract class YsmGenericRendererMixin {
    @Inject(method = "Oo0Oo0o00O00Oo0OOoOOoooo", at = @At("HEAD"), cancellable = true, remap = false)
    private static void customnpcsYsmCompat$renderStaticNpc(Entity entity, float yaw, float partialTick,
                                                            PoseStack poseStack, MultiBufferSource buffers,
                                                            int packedLight, CallbackInfoReturnable<Boolean> cir) {
        if (AnimatedNpcRenderBridge.tryRender(entity, yaw, partialTick, poseStack, buffers, packedLight)) {
            cir.setReturnValue(false);
        }
    }
}
