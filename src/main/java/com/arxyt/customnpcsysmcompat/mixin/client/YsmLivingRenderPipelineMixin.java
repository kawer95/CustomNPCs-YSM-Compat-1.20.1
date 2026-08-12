package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.ProxyVisibilityContext;
import com.arxyt.customnpcsysmcompat.client.Ysm265Adapter;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hooks the concrete YSM 2.6.5 living/player renderer used by the proxy player. */
@Mixin(targets = "com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo", remap = false)
public abstract class YsmLivingRenderPipelineMixin {
    /** Overrides the YSM renderer-interface default for the concrete living renderer. */
    public RenderType Oo0Oo0o00O00Oo0OOoOOoooo(ResourceLocation texture, boolean visible,
                                                boolean glowing, boolean customLayer) {
        return Ysm265Adapter.selectRenderType(texture, visible, glowing, customLayer,
                ProxyVisibilityContext.partial());
    }

    @ModifyArg(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/elfmcys/yesstevemodel/OOoo0o0oO000ooO0Oo00OoOo;Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;Lcom/elfmcys/yesstevemodel/o0000OoOooO0oo0o0oooo0Oo;FLnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                    remap = false),
            index = 13,
            remap = false)
    private float customnpcsYsmCompat$applyGhostAlpha(float originalAlpha) {
        return ProxyVisibilityContext.applyModelAlpha(originalAlpha);
    }
}
