package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.NpcNameTagRenderContext;
import com.arxyt.customnpcsysmcompat.client.render.DiscardingVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import noppes.npcs.client.ChatMessages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Removes CustomNPCs' explicit through-wall chat-bubble pass while a YSM NPC label is rendered.
 *
 * <p>The first {@code getBuffer} call in {@code ChatMessages#renderMessages} targets the
 * mod's {@code chatbubbledepth} render type, whose depth function is {@code ALWAYS}. Returning
 * a sink avoids changing stock CustomNPC labels and leaves the second, ordinary depth-tested
 * {@code chatbubble} pass responsible for the visible bubble.</p>
 */
@Mixin(value = ChatMessages.class, remap = false)
public abstract class ChatMessagesDepthMixin {
    @Redirect(
            method = "renderMessages",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    ordinal = 0,
                    remap = true),
            remap = false,
            require = 0)
    private VertexConsumer customnpcsYsmCompat$suppressXrayBubblePass(
            MultiBufferSource buffers, RenderType renderType) {
        if (NpcNameTagRenderContext.suppressXrayBubblePass()) {
            return DiscardingVertexConsumer.INSTANCE;
        }
        return buffers.getBuffer(renderType);
    }
}
