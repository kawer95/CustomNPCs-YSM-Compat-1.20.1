package com.arxyt.customnpcsysmcompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Reads Dominion Sword's shared TaCZ target-reaction settings without making
 * the base compatibility mod link against Dominion Sword at runtime.
 *
 * <p>The bridge is deliberately read-only. If the optional mod or an expected
 * configuration field is unavailable, callers receive {@link Settings#UNAVAILABLE}
 * and retain the previous YSM-CNPC gun behavior.</p>
 */
public final class DominionCombatBalance {
    private static final AtomicBoolean ERROR_REPORTED = new AtomicBoolean();
    private static volatile Access access;

    private DominionCombatBalance() {
    }

    /** Resolves the optional settings after Forge has confirmed Dominion Sword is installed. */
    public static void load() {
        try {
            ClassLoader loader = DominionCombatBalance.class.getClassLoader();
            Class<?> config = Class.forName("com.arxyt.dominionsword.config.ServerConfig", true, loader);
            access = new Access(
                    config.getField("BALANCE_CNPC_TARGET_ACQUISITION"),
                    config.getField("DYNAMIC_CNPC_TARGET_ACQUISITION"),
                    config.getField("STANDING_CNPC_MACHINE_GUN_ACCURACY_PENALTY"));
            CustomNpcsYsmCompat.LOGGER.info("Dominion Sword TaCZ balance enabled for YSM-CNPCs");
        } catch (ReflectiveOperationException | LinkageError error) {
            access = null;
            report(error);
        }
    }

    /** Returns the current authoritative server values, or unavailable on any optional-link failure. */
    public static Settings settings() {
        Access current = access;
        if (current == null) return Settings.UNAVAILABLE;
        try {
            boolean balanceTargetAcquisition = booleanValue(current.balanceTargetAcquisition());
            boolean dynamicTargetAcquisition = booleanValue(current.dynamicTargetAcquisition());
            boolean customNpcStandingMachineGunAccuracyPenalty = booleanValue(current.customNpcStandingMachineGunAccuracyPenalty());
            return new Settings(true, balanceTargetAcquisition, dynamicTargetAcquisition,
                    customNpcStandingMachineGunAccuracyPenalty);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            access = null;
            report(error);
            return Settings.UNAVAILABLE;
        }
    }

    private static boolean booleanValue(Field field) throws ReflectiveOperationException {
        Object value = readConfigValue(field);
        if (value instanceof Boolean enabled) return enabled;
        throw new ReflectiveOperationException("Expected boolean from " + field.getName());
    }

    private static Object readConfigValue(Field field) throws ReflectiveOperationException {
        Object configValue = field.get(null);
        if (configValue instanceof Supplier<?> supplier) return supplier.get();
        if (configValue == null) throw new ReflectiveOperationException("Missing config value " + field.getName());
        Method getter = configValue.getClass().getMethod("get");
        return getter.invoke(configValue);
    }

    private static void report(Throwable error) {
        if (ERROR_REPORTED.compareAndSet(false, true)) {
            CustomNpcsYsmCompat.LOGGER.error(
                    "Dominion Sword TaCZ balance settings are unavailable; YSM-CNPC behavior remains unchanged", error);
        }
    }

    /** Immutable copy of the independent CNPC reaction values and shared TACZ stance penalty. */
    public record Settings(boolean available, boolean targetReactionEnabled,
                           boolean dynamicTargetReaction, boolean customNpcStandingMachineGunAccuracyPenalty) {
        static final Settings UNAVAILABLE = new Settings(false, false, false, false);
    }

    private record Access(Field balanceTargetAcquisition, Field dynamicTargetAcquisition,
                          Field customNpcStandingMachineGunAccuracyPenalty) {
    }
}
