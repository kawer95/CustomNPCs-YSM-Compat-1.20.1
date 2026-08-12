package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.GunCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes compatible NPC tracer geometry visible from both sides. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.entity.EntityBulletRenderer", remap = false)
public abstract class TaczBulletRendererMixin {
    @Redirect(
            method = "renderTracerAmmo",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;energySwirl(Lnet/minecraft/resources/ResourceLocation;FF)Lnet/minecraft/client/renderer/RenderType;",
                    remap = true),
            remap = false,
            require = 0)
    private RenderType customnpcsYsmCompat$twoSidedNpcTracer(
            ResourceLocation texture, float u, float v,
            EntityKineticBullet bullet, float[] tracerColor, float partialTicks,
            PoseStack poseStack, int packedLight) {
        if (bullet.getOwner() instanceof EntityNPCInterface npc && GunCompat.active(npc)) {
            // entityTranslucentEmissive disables back-face culling. TaCZ's tracer model is
            // otherwise visible mainly when viewed from the shooter's rear hemisphere.
            return RenderType.entityTranslucentEmissive(texture, true);
        }
        return RenderType.energySwirl(texture, u, v);
    }
}
