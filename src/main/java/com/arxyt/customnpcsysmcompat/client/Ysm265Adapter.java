package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.CustomNpcsYsmCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
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
    private static final String ANIMATABLE_CAP = "com.elfmcys.yesstevemodel.O0o00oO0O0ooOOOo0oo0Oo00";
    private static final String GENERIC_RENDERER = "com.elfmcys.yesstevemodel.OOoO0O0OooOO0o00oOoOOoO0";
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

    public static boolean setModel(Entity entity, String modelId) {
        try {
            Bindings b = bindings();
            @SuppressWarnings("unchecked")
            Optional<Object> value = (Optional<Object>) entity.getCapability((Capability<Object>) b.animatableCapability).resolve();
            if (value.isEmpty()) {
                return false;
            }
            b.setAnimatableModel.invoke(value.get(), modelId);
            return true;
        } catch (Throwable error) {
            report(error);
            return false;
        }
    }

    /** Returns YSM's condition value: false means it rendered and the original renderer must be skipped. */
    public static boolean render(Entity entity, float yaw, PoseStack poseStack,
                                 MultiBufferSource buffers, int packedLight) {
        try {
            return (boolean) bindings().genericRender.invoke(null, entity, yaw, 0.0F, poseStack, buffers, packedLight);
        } catch (Throwable error) {
            report(error);
            return true;
        }
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

                Class<?> capHolder = Class.forName(ANIMATABLE_CAP);
                Field capField = capHolder.getField(OBF);
                Capability<?> capability = (Capability<?>) capField.get(null);

                Class<?> animatable = Class.forName("com.elfmcys.yesstevemodel.oOOo0Ooo0oOoo0O0OOOOo0oo");
                Method setModel = animatable.getMethod(OBF, String.class);

                Class<?> renderer = Class.forName(GENERIC_RENDERER);
                Method render = renderer.getMethod(OBF, Entity.class, float.class, float.class,
                        PoseStack.class, MultiBufferSource.class, int.class);
                bindings = new Bindings(modelRegistry, capability, setModel, render);
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

    private record Bindings(Method modelRegistry, Capability<?> animatableCapability,
                            Method setAnimatableModel, Method genericRender) {
    }
}
