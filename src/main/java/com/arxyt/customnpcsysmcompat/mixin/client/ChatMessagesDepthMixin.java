package com.arxyt.customnpcsysmcompat.mixin.client;

import com.arxyt.customnpcsysmcompat.client.NpcNameTagRenderContext;
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

    /** A vertex sink keeps CustomNPCs' first bubble pass side-effect-free without changing it globally. */
    private enum DiscardingVertexConsumer implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
