package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** The only class allowed to know YSM 2.6.5's obfuscated binary names. */
public final class Ysm265Adapter {
    private static final String REGISTRY = "com.elfmcys.yesstevemodel.o0OooO00ooo0OO000O0OoOoO";
    private static final String PLAYER_CAP = "com.elfmcys.yesstevemodel.O0OooOo0oOOoOoOoOooO000o";
    private static final String PLAYER_ANIMATABLE = "com.elfmcys.yesstevemodel.o0OOO0o0o0OOo000oO00o00O";
    private static final String PLAYER_SYNC_DATA = "com.elfmcys.yesstevemodel.ooOO000o0O0OOOoO0Oo0o0Oo";
    private static final String YSM_RENDER_TYPE = "com.elfmcys.yesstevemodel.o0oOo0ooO00oOoooOOOoOOo0";
    private static final String OBF = "Oo0Oo0o00O00Oo0OOoOOoooo";
    private static final AtomicBoolean ERROR_REPORTED = new AtomicBoolean();

    private static volatile Bindings bindings;

    private Ysm265Adapter() {
    }

    public static List<YsmModelEntry> models() {
        try {
            Map<?, ?> models = (Map<?, ?>) bindings().modelRegistry.invoke(null);
            List<YsmModelEntry> result = new ArrayList<>(models.size());
            for (Map.Entry<?, ?> entry : models.entrySet()) {
                String id = String.valueOf(entry.getKey());
                result.add(new YsmModelEntry(id, displayName(entry.getValue(), id)));
            }
            result.sort(Comparator.comparing(YsmModelEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(YsmModelEntry::id));
            return List.copyOf(result);
        } catch (Throwable error) {
            report(error);
            return List.of();
        }
    }

    public static boolean hasModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> models = (Map<?, ?>) bindings().modelRegistry.invoke(null);
            return models.containsKey(modelId);
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    public static boolean setPlayerModel(RemotePlayer player, String modelId) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            if (value.isEmpty()) {
                return false;
            }
            b.setPlayerModel.invoke(value.get(), modelId, "");
            return (boolean) b.isPlayerModelValid.invoke(value.get());
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    public static boolean isPlayerModelReady(RemotePlayer player) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            return value.isPresent() && (boolean) b.isPlayerModelValid.invoke(value.get());
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    /** Normalizes YSM's own fake-player sync cache; it otherwise defaults food_level to zero. */
    public static int normalizePlayerState(RemotePlayer player) {
        try {
            Bindings b = bindings();
            Optional<Object> value = playerAnimatable(player, b);
            if (value.isPresent()) {
                Object syncData = b.getPlayerSyncData.invoke(value.get());
                b.foodLevel.setInt(syncData, 20);
                return b.foodLevel.getInt(syncData);
            }
        } catch (Throwable error) {
            report(error);
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> playerAnimatable(RemotePlayer player, Bindings bindings) {
        return (Optional<Object>) player.getCapability((Capability<Object>) bindings.playerCapability).resolve();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean renderPlayer(RemotePlayer player, float yaw, float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffers, int packedLight) {
        try {
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
            renderer.render(player, yaw, partialTick, poseStack, buffers, packedLight);
            return true;
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    public static RenderType selectRenderType(ResourceLocation texture, boolean visible,
                                              boolean glowing, boolean customLayer,
                                              boolean partialVisibility) {
        RenderType result;
        if (partialVisibility && visible && !glowing) {
            result = RenderType.entityTranslucent(texture);
        } else if (visible) {
            if (!customLayer) {
                result = RenderType.entityCutoutNoCull(texture);
            } else {
                try {
                    result = (RenderType) bindings().customRenderType.invoke(null, texture);
                } catch (Throwable error) {
                    report(error);
                    result = RenderType.entityCutoutNoCull(texture);
                }
            }
        } else {
            result = glowing ? RenderType.outline(texture) : null;
        }
        ProxyVisibilityContext.traceRenderType(texture, visible, glowing, customLayer, result);
        return result;
    }

    private static Bindings bindings() throws ReflectiveOperationException {
        Bindings result = bindings;
        if (result != null) {
            return result;
        }
        synchronized (Ysm265Adapter.class) {
            if (bindings == null) {
                Class<?> registry = Class.forName(REGISTRY);
                Method modelRegistry = registry.getMethod("o0OOooo0o0OO00OoOOOo0o0O");

                Class<?> capHolder = Class.forName(PLAYER_CAP);
                Field capField = capHolder.getField(OBF);
                Capability<?> capability = (Capability<?>) capField.get(null);

                Class<?> animatable = Class.forName(PLAYER_ANIMATABLE);
                Method setModel = animatable.getMethod(OBF, String.class, String.class);
                Method isValid = animatable.getMethod("o0ooooOo0o000OOo0oO00OoO");
                Method getPlayerSyncData = animatable.getMethod(OBF);
                Field foodLevel = Class.forName(PLAYER_SYNC_DATA)
                        .getDeclaredField("o0OOO0o0o0OOo000oO00o00O");
                foodLevel.setAccessible(true);
                Method customRenderType = Class.forName(YSM_RENDER_TYPE)
                        .getMethod(OBF, ResourceLocation.class);
                bindings = new Bindings(modelRegistry, capability, setModel, isValid,
                        getPlayerSyncData, foodLevel, customRenderType);
            }
            return bindings;
        }
    }

    private static String displayName(Object resource, String fallback) {
        try {
            Object metadata = resource.getClass().getMethod("Ooooo0oooO0oooOOOoO0000O").invoke(resource);
            Object basic = metadata.getClass().getMethod(OBF).invoke(metadata);
            String name = (String) basic.getClass().getMethod(OBF).invoke(basic);
            return name == null || name.isBlank() ? fallback : name;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void report(Throwable error) {
        if (ERROR_REPORTED.compareAndSet(false, true)) {
            CustomNpcsYsmCompat.LOGGER.error("YSM 2.6.5 adapter failed; affected NPCs will use their CustomNPCs model", error);
        }
    }

    private record Bindings(Method modelRegistry, Capability<?> playerCapability,
                            Method setPlayerModel, Method isPlayerModelValid,
                            Method getPlayerSyncData, Field foodLevel, Method customRenderType) {
    }
}
