package com.arxyt.customnpcsysmcompat.mixin.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Guards CustomNPCs parts whose custom texture and inherited NPC texture are both unset. */
@Mixin(targets = "noppes.npcs.client.parts.MpmPartAbstractClient", remap = false)
public abstract class MpmPartAbstractClientMixin {
    @Redirect(
            method = "render(Lnoppes/npcs/client/parts/MpmPartData;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
                    remap = true
            ),
            remap = false
    )
    private RenderType customnpcsYsmCompat$guardMissingPartTexture(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture == null
                ? MissingTextureAtlasSprite.getLocation() : texture);
    }
}
