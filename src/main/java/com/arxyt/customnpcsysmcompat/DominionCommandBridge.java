package com.arxyt.customnpcsysmcompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional, read-only bridge to Dominion Sword's public command view. */
public final class DominionCommandBridge {
    private static final String DOMINION_ORDER = "DominionOrder";
    private static final String DOMINION_ATTACK_QUEUE = "DominionAttackQueue";
    // Dominion writes this only for PlayerControl.attack(single target), not Ctrl area queues.
    private static final String DOMINION_DIRECT_ATTACK = "DominionOfflineAttack";
    private static final String ATTACK_ORDER = "attack";
    private static final Snapshot UNAVAILABLE = new Snapshot(false, false, false, false, false, false, false, null);
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
                    optionalUnreflect(lookup, view, "prone"),
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
            CompoundTag data = unit.getPersistentData();
            return new Snapshot(true,
                    (boolean) current.nativeCombatBlocked.invoke(view),
                    (boolean) current.autonomousMovementBlocked.invoke(view),
                    (boolean) current.nativeApproachBlocked.invoke(view),
                    (boolean) current.closeQuarters.invoke(view),
                    current.prone != null && (boolean) current.prone.invoke(view),
                    isDirectSingleTargetAttack(data.getString(DOMINION_ORDER), data.hasUUID(DOMINION_DIRECT_ATTACK)),
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

    /**
     * Reports whether Dominion still has a real follow-up attack target queued for this unit.
     *
     * <p>The public command view intentionally hides mutable queue data. ADS continuity needs
     * that one server-side fact, so this optional bridge reads the two version-bounded Dominion
     * persistent tags in one place only. {@link #snapshot(Mob)} runs first: no Dominion bridge,
     * client entity, inactive command, malformed tag, or an empty queue all safely mean false.</p>
     */
    public static boolean hasQueuedAttack(Mob unit) {
        if (unit == null || !snapshot(unit).active()) return false;
        CompoundTag data = unit.getPersistentData();
        return hasQueuedAttack(data.getString(DOMINION_ORDER),
                data.getList(DOMINION_ATTACK_QUEUE, Tag.TAG_COMPOUND).size());
    }

    static boolean hasQueuedAttack(String order, int queueEntries) {
        return ATTACK_ORDER.equals(order) && queueEntries > 0;
    }

    /**
     * Dominion uses a persistent fallback UUID only for its direct one-target attack command.
     * Ctrl/area attacks instead write an attack queue and deliberately omit this field.
     */
    static boolean isDirectSingleTargetAttack(String order, boolean hasDirectTarget) {
        return ATTACK_ORDER.equals(order) && hasDirectTarget;
    }

    private static MethodHandle unreflect(MethodHandles.Lookup lookup, Method method)
            throws IllegalAccessException {
        return lookup.unreflect(method);
    }

    /** Dominion 1.32.7 introduced prone; older installed builds simply report false. */
    private static MethodHandle optionalUnreflect(MethodHandles.Lookup lookup, Class<?> owner, String method)
            throws IllegalAccessException {
        try {
            return unreflect(lookup, owner.getMethod(method));
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void report(Throwable error) {
        if (ERROR_REPORTED.compareAndSet(false, true)) {
            CustomNpcsYsmCompat.LOGGER.error(
                    "Dominion Sword command bridge failed; standalone YSM-CNPC behavior remains enabled", error);
        }
    }

    public record Snapshot(boolean active, boolean nativeCombatBlocked,
                            boolean autonomousMovementBlocked, boolean nativeApproachBlocked,
                            boolean closeQuarters, boolean prone,
                            boolean directSingleTargetAttack,
                            LivingEntity attackTarget) {
        public boolean commandedAttack() {
            return active && !nativeCombatBlocked && attackTarget != null;
        }

        /** Only a player-clicked one-target attack may skip automatic target reacquisition. */
        public boolean directAttackOrder() {
            return commandedAttack() && directSingleTargetAttack;
        }

        /** Idle/HOLD control may use weapons, but it must never create locomotion intent. */
        public boolean stationarySentry() {
            return active && autonomousMovementBlocked && !nativeCombatBlocked && attackTarget == null;
        }
    }

    private record Access(MethodHandle commandView, MethodHandle active,
                           MethodHandle nativeCombatBlocked, MethodHandle autonomousMovementBlocked,
                           MethodHandle nativeApproachBlocked, MethodHandle closeQuarters,
                           MethodHandle prone,
                           MethodHandle attackTarget) {
    }
}
