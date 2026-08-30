package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.arxyt.customnpcsysmcompat.RenderStabilityConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Render-local visibility state consumed by the YSM 2.6.5 render-type mixin. */
public final class ProxyVisibilityContext {
    private static final float GHOST_ALPHA = 0.15F;
    private static final ThreadLocal<Boolean> PARTIAL = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> NPC_ID = ThreadLocal.withInitial(() -> -1);
    private static final Map<Integer, String> LAST_RENDER_STATE = new HashMap<>();
    private static final Map<Integer, Set<String>> SEEN_BUFFER_STATES = new HashMap<>();

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

    public static float applyModelAlpha(float originalAlpha) {
        if (!partial()) {
            return originalAlpha;
        }
        float result = originalAlpha * GHOST_ALPHA;
        tracePipeline("alpha", null, result, null);
        return result;
    }

    public static void tracePipeline(String stage, ResourceLocation texture,
                                     float alpha, RenderType renderType) {
        if (!RenderStabilityConfig.ENABLED.get()) return;
        int npcId = NPC_ID.get();
        if (npcId < 0) {
            return;
        }
        String state = "stage=" + stage + ",texture=" + texture + ",alpha=" + alpha
                + ",renderType=" + renderType;
        synchronized (SEEN_BUFFER_STATES) {
            if (!SEEN_BUFFER_STATES.computeIfAbsent(npcId, ignored -> new HashSet<>()).add(state)) {
                return;
            }
        }
        CustomNpcsYsmCompat.LOGGER.info(
                "[YSM-VIS-TRACE][PIPELINE] npcId={} {}", npcId, state);
    }

    public static void traceRenderType(ResourceLocation texture, boolean ysmVisible,
                                       boolean glowing, boolean customLayer, RenderType result) {
        if (!RenderStabilityConfig.ENABLED.get()) return;
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
        synchronized (SEEN_BUFFER_STATES) {
            SEEN_BUFFER_STATES.clear();
        }
    }

}
