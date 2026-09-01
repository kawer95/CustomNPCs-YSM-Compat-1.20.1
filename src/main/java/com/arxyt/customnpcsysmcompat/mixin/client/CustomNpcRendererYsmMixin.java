package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.AnimatedNpcRenderBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stable entry point for replay-created NPCs. ReplayMod can create an NPC from
 * its recorded Forge spawn packet without taking YSM's generic entity-render
 * path, even though the CustomNPC spawn NBT already contains the YSM model.
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class CustomNpcRendererYsmMixin {
    @Inject(
            method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void customnpcsYsmCompat$renderRecordedYsmNpc(EntityNPCInterface npc, float yaw,
                                                          float partialTick, PoseStack poseStack,
                                                          MultiBufferSource buffers, int packedLight,
                                                          CallbackInfo callback) {
        if (AnimatedNpcRenderBridge.tryRender(npc, yaw, partialTick, poseStack, buffers, packedLight)) {
            callback.cancel();
        }
    }
}
