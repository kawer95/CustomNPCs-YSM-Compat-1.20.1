package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.ProxyVisibilityContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hooks the YSM 2.6.5 renderer interface used by player, living and generic renderers. */
@Mixin(targets = "com.elfmcys.yesstevemodel.oo0OOoOOOOO0Oo0O0oo0O0OO", remap = false)
public abstract class YsmRenderPipelineMixin {
    @Inject(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(Lnet/minecraft/resources/ResourceLocation;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void customnpcsYsmCompat$useTranslucentType(ResourceLocation texture, boolean visible,
                                                        boolean glowing, boolean customLayer,
                                                        CallbackInfoReturnable<RenderType> cir) {
        if (ProxyVisibilityContext.partial()) {
            RenderType result = glowing ? RenderType.outline(texture) : RenderType.entityTranslucent(texture);
            ProxyVisibilityContext.tracePipeline("type", texture, 1.0F, result);
            cir.setReturnValue(result);
        }
    }

    @ModifyVariable(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;Lcom/elfmcys/yesstevemodel/o0000OoOooO0oo0o0oooo0Oo;FLnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4, remap = false)
    private float customnpcsYsmCompat$applyGhostAlpha(float originalAlpha) {
        return ProxyVisibilityContext.applyModelAlpha(originalAlpha);
    }
}
