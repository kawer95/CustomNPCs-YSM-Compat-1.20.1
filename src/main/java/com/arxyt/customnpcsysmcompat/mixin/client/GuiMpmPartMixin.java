package com.arxyt.customnpcsysmcompat.mixin.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Guards a CustomNPCs 1.20.1 part-preview bug where an unset NPC texture is passed to RenderType. */
@Mixin(targets = "noppes.npcs.client.gui.custom.GuiCreationNewParts$GuiMpmPart", remap = false)
public abstract class GuiMpmPartMixin {
    @Redirect(
            method = "renderModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
                    remap = true
            ),
            remap = false
    )
    private RenderType customnpcsYsmCompat$guardMissingPartTexture(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture == null
                ? MissingTextureAtlasSprite.getLocation() : texture);
    }
}
