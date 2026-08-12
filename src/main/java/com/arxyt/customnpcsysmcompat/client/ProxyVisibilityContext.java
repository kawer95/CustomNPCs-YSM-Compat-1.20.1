package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Render-local visibility state consumed by the YSM 2.6.5 render-type mixin. */
public final class ProxyVisibilityContext {
    private static final float GHOST_ALPHA = 0.15F;
    private static final ThreadLocal<Boolean> PARTIAL = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> NPC_ID = ThreadLocal.withInitial(() -> -1);
    private static final Map<Integer, String> LAST_RENDER_STATE = new HashMap<>();

    private ProxyVisibilityContext() {
    }

    public static void begin(boolean partial, int npcId) {
        PARTIAL.set(partial);
        NPC_ID.set(npcId);
    }

    public static void end() {
        PARTIAL.remove();
        NPC_ID.remove();
    }

    public static boolean partial() {
        return PARTIAL.get();
    }

    public static MultiBufferSource applyAlpha(MultiBufferSource source) {
        if (!partial()) {
            return source;
        }
        return renderType -> new AlphaVertexConsumer(source.getBuffer(renderType), GHOST_ALPHA);
    }

    public static void traceRenderType(ResourceLocation texture, boolean ysmVisible,
                                       boolean glowing, boolean customLayer, RenderType result) {
        int npcId = NPC_ID.get();
        if (npcId < 0) {
            return;
        }
        String state = "partial=" + partial() + ",ysmVisible=" + ysmVisible
                + ",glowing=" + glowing + ",customLayer=" + customLayer
                + ",result=" + (result == null ? "null" : result.toString());
        synchronized (LAST_RENDER_STATE) {
            if (state.equals(LAST_RENDER_STATE.put(npcId, state))) {
                return;
            }
        }
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-VIS-TRACE][RENDER-TYPE] npcId={} texture={} {}", npcId, texture, state);
    }

    public static void clearDebugState() {
        synchronized (LAST_RENDER_STATE) {
            LAST_RENDER_STATE.clear();
        }
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        private AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, Math.round(a * alpha)); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, Math.round(a * alpha)); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
