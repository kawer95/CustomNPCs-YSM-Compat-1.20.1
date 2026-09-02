package com.arxyt.customnpcsysmcompat;

import net.minecraftforge.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional bridge to CNPC-TACZ's per-NPC combat page.  Keeping every symbol reflective is
 * intentional: a YSM-only install continues to use CNPC's native ranged fields unchanged.
 */
public final class TaczCombatSettingsBridge {
    private static final String MOD_ID = "customnpcs_tacz_compat";
    private static final String API = "com.arxyt.customnpcstaczcompat.NpcTaczCombatApi";
    private static final AtomicBoolean ERROR_REPORTED = new AtomicBoolean();
    private static volatile Methods methods;
    private static volatile boolean unavailable;

    private TaczCombatSettingsBridge() { }

    public static int range(EntityNPCInterface npc, int fallback) {
        Object value = invoke(npc, "range");
        return value instanceof Number number ? Math.max(1, number.intValue()) : fallback;
    }

    public static int accuracy(EntityNPCInterface npc, int fallback) {
        Object value = invoke(npc, "accuracy");
        return value instanceof Number number ? Math.max(0, Math.min(100, number.intValue())) : fallback;
    }

    public static boolean allowsShot(EntityNPCInterface npc) {
        Object value = invoke(npc, "allowsShot");
        return !(value instanceof Boolean allowed) || allowed;
    }

    public static int recordSuccessfulShot(EntityNPCInterface npc, int fallbackDelay) {
        Object value = invoke(npc, "recordSuccessfulShot");
        return value instanceof Number number ? Math.max(1, number.intValue()) : Math.max(1, fallbackDelay);
    }

    public static void resetPattern(EntityNPCInterface npc) {
        invoke(npc, "resetPattern");
    }

    private static Object invoke(EntityNPCInterface npc, String member) {
        if (npc == null || unavailable || !ModList.get().isLoaded(MOD_ID)) return null;
        try {
            Methods resolved = methods;
            if (resolved == null) {
                Class<?> api = Class.forName(API, false, TaczCombatSettingsBridge.class.getClassLoader());
                resolved = new Methods(api.getMethod("range", EntityNPCInterface.class),
                        api.getMethod("accuracy", EntityNPCInterface.class),
                        api.getMethod("allowsShot", EntityNPCInterface.class),
                        api.getMethod("recordSuccessfulShot", EntityNPCInterface.class),
                        api.getMethod("resetPattern", EntityNPCInterface.class));
                methods = resolved;
            }
            return switch (member) {
                case "range" -> resolved.range.invoke(null, npc);
                case "accuracy" -> resolved.accuracy.invoke(null, npc);
                case "allowsShot" -> resolved.allowsShot.invoke(null, npc);
                case "recordSuccessfulShot" -> resolved.recordSuccessfulShot.invoke(null, npc);
                case "resetPattern" -> { resolved.resetPattern.invoke(null, npc); yield null; }
                default -> null;
            };
        } catch (Throwable error) {
            // A loaded older CNPC-TACZ release cannot acquire this API during the same game
            // session. Cache that negative result so a normal YSM-only fallback does not pay
            // for failed reflective lookup on every AI tick.
            unavailable = true;
            if (ERROR_REPORTED.compareAndSet(false, true)) {
                CustomNpcsYsmCompat.LOGGER.error(
                        "CNPC-TACZ combat settings bridge failed; using native CNPC ranged values", error);
            }
            return null;
        }
    }

    private record Methods(Method range, Method accuracy, Method allowsShot,
                           Method recordSuccessfulShot, Method resetPattern) { }
}
