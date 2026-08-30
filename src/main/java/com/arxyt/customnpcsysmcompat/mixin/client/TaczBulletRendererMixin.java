package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.GunCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

/** Makes compatible NPC tracer geometry visible from both sides. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.entity.EntityBulletRenderer", remap = false)
public abstract class TaczBulletRendererMixin {
    private static volatile Method nativeNpcActive;

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
        if (bullet.getOwner() instanceof EntityNPCInterface npc && customnpcsYsmCompat$isCompatibleNpcOwner(npc)) {
            // entityTranslucentEmissive disables back-face culling. TaCZ's tracer model is
            // otherwise visible mainly when viewed from the shooter's rear hemisphere.
            return RenderType.entityTranslucentEmissive(texture, true);
        }
        return RenderType.energySwirl(texture, u, v);
    }

    /**
     * Both CNPC add-ons redirect TaCZ's one RenderType call.  Mixin applies one redirect first,
     * so that redirect must recognise the other add-on too; otherwise its tracers fall back to
     * TaCZ's single-sided energy swirl merely because of config load order.
     */
    private static boolean customnpcsYsmCompat$isCompatibleNpcOwner(EntityNPCInterface npc) {
        return GunCompat.active(npc) || customnpcsYsmCompat$isNativeNpcOwner(npc);
    }

    private static boolean customnpcsYsmCompat$isNativeNpcOwner(EntityNPCInterface npc) {
        if (!ModList.get().isLoaded("customnpcs_tacz_compat")) return false;
        try {
            Method active = nativeNpcActive;
            if (active == null) {
                active = Class.forName("com.arxyt.customnpcstaczcompat.NativeNpcEligibility", false,
                                TaczBulletRendererMixin.class.getClassLoader())
                        .getMethod("active", EntityNPCInterface.class);
                nativeNpcActive = active;
            }
            return Boolean.TRUE.equals(active.invoke(null, npc));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
