package com.arxyt.customnpcsysmcompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional, read-only bridge to Dominion Sword's public command view. */
public final class DominionCommandBridge {
    private static final Snapshot UNAVAILABLE = new Snapshot(false, false, false, false, false, null);
    private static final AtomicBoolean ERROR_REPORTED = new AtomicBoolean();
    private static volatile Access access;

    private DominionCommandBridge() {
    }

    public static void load() {
        try {
            ClassLoader loader = DominionCommandBridge.class.getClassLoader();
            Class<?> api = Class.forName("com.arxyt.dominionsword.api.DominionControlApi", false, loader);
            Class<?> view = Class.forName("com.arxyt.dominionsword.api.DominionUnitCommandView", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            access = new Access(
                    unreflect(lookup, api.getMethod("commandView", Mob.class)),
                    unreflect(lookup, view.getMethod("active")),
                    unreflect(lookup, view.getMethod("nativeCombatBlocked")),
                    unreflect(lookup, view.getMethod("autonomousMovementBlocked")),
                    unreflect(lookup, view.getMethod("nativeApproachBlocked")),
                    unreflect(lookup, view.getMethod("closeQuarters")),
                    unreflect(lookup, view.getMethod("attackTarget")));
            CustomNpcsYsmCompat.LOGGER.info("Dominion Sword command coordination enabled");
        } catch (ReflectiveOperationException | LinkageError error) {
            report(error);
        }
    }

    public static Snapshot snapshot(Mob unit) {
        Access current = access;
        if (current == null || unit == null || unit.level().isClientSide) return UNAVAILABLE;
        try {
            Object view = current.commandView.invoke(unit);
            if (view == null || !(boolean) current.active.invoke(view)) return UNAVAILABLE;
            Object target = current.attackTarget.invoke(view);
            return new Snapshot(true,
                    (boolean) current.nativeCombatBlocked.invoke(view),
                    (boolean) current.autonomousMovementBlocked.invoke(view),
                    (boolean) current.nativeApproachBlocked.invoke(view),
                    (boolean) current.closeQuarters.invoke(view),
                    target instanceof LivingEntity living ? living : null);
        } catch (Throwable error) {
            report(error);
            access = null;
            return UNAVAILABLE;
        }
    }

    public static boolean allowsAttack(Mob unit, net.minecraft.world.entity.Entity target) {
        Snapshot command = snapshot(unit);
        if (!command.active()) return true;
        return !command.nativeCombatBlocked() && command.attackTarget() != null
                && target != null && command.attackTarget().getUUID().equals(target.getUUID());
    }

    private static MethodHandle unreflect(MethodHandles.Lookup lookup, Method method)
            throws IllegalAccessException {
        return lookup.unreflect(method);
    }

    private static void report(Throwable error) {
        if (ERROR_REPORTED.compareAndSet(false, true)) {
            CustomNpcsYsmCompat.LOGGER.error(
                    "Dominion Sword command bridge failed; standalone YSM-CNPC behavior remains enabled", error);
        }
    }

    public record Snapshot(boolean active, boolean nativeCombatBlocked,
                           boolean autonomousMovementBlocked, boolean nativeApproachBlocked,
                           boolean closeQuarters,
                           LivingEntity attackTarget) {
        public boolean commandedAttack() {
            return active && !nativeCombatBlocked && attackTarget != null;
        }

        /** Idle/HOLD control may use weapons, but it must never create locomotion intent. */
        public boolean stationarySentry() {
            return active && autonomousMovementBlocked && !nativeCombatBlocked && attackTarget == null;
        }
    }

    private record Access(MethodHandle commandView, MethodHandle active,
                          MethodHandle nativeCombatBlocked, MethodHandle autonomousMovementBlocked,
                          MethodHandle nativeApproachBlocked, MethodHandle closeQuarters,
                          MethodHandle attackTarget) {
    }
}
